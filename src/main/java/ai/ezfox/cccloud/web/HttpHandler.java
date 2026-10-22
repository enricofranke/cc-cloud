package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * HTTP part of the dashboard server. Every request goes through {@link #handle}, which (1) resolves the client IP
 * (trusted proxies), (2) enforces the LAN-only and IP-ban rules, (3) matches a {@link Router} route and (4) enforces
 * the route's access level before any route code runs.
 */
final class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Pattern SAFE_HOST = Pattern.compile("[A-Za-z0-9.\\-:\\[\\]]{1,255}");
    private static final int MAX_TOKENS_PER_REQUEST = 16;
    static final String LAN_ONLY_MESSAGE = "This CC: Cloud dashboard only accepts connections from the local network. "
        + "Server admins can allow internet access with webAllowInternet=true in serverconfig/cccloud-server.toml "
        + "(use HTTPS, see docs/HOSTING.md).";

    private final WebInstance instance;

    HttpHandler(WebInstance instance) {
        this.instance = instance;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            handle(ctx, req);
        } catch (Exception e) {
            CloudLog.LOGGER.warn("[cccloud] Error handling {} request", req.method(), e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, error("internal_error"));
        }
    }

    String clientIp(ChannelHandlerContext ctx, FullHttpRequest req) {
        return IpUtil.clientIp(ConnectionLimiter.peerIp(ctx.channel()), instance.trustedProxies,
            req.headers().get("CF-Connecting-IP"), req.headers().getAll("X-Forwarded-For"));
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.decoderResult().isSuccess()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("bad_request"));
            return;
        }
        String ip = clientIp(ctx, req);
        if (instance.bannedIps.contains(ip)) {
            sendText(ctx, req, HttpResponseStatus.FORBIDDEN, "Forbidden");
            return;
        }
        if (!instance.allowInternet && !IpUtil.isLan(ip)) {
            var body = error("lan_only");
            body.addProperty("message", LAN_ONLY_MESSAGE);
            sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, body);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();
        var route = Router.match(req.method(), path);
        if (route == null) {
            if (Router.pathExists(path)) sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, error("method_not_allowed"));
            else sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("not_found"));
            return;
        }

        // Central access check (deny by default).
        switch (route.access()) {
            case PUBLIC -> dispatch(ctx, req, route, ip, List.of());
            case DEVICE -> authenticateDevices(ctx, req, route, ip);
        }
    }

    /** Resolves the Bearer tokens and re-checks the players' live rights on the server thread. */
    private void authenticateDevices(ChannelHandlerContext ctx, FullHttpRequest req, Router.Route route, String ip) {
        var devices = new ArrayList<AuthStore.Device>();
        for (String token : bearerTokens(req)) {
            var device = instance.auth.authenticate(token);
            if (device != null) devices.add(device);
        }
        if (devices.isEmpty()) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, error("unauthorized"));
            return;
        }
        req.retain();
        CompletableFuture.supplyAsync(() -> devices.stream().filter(d -> instance.bridge.checkRights(d) == null).toList(), instance.bridge.serverExecutor())
            .orTimeout(5, TimeUnit.SECONDS)
            .whenComplete((allowed, err) -> ctx.executor().execute(() -> {
                try {
                    if (err != null) sendJson(ctx, req, HttpResponseStatus.SERVICE_UNAVAILABLE, error("unavailable"));
                    else if (allowed.isEmpty()) sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, error("forbidden"));
                    else dispatch(ctx, req, route, ip, allowed);
                } catch (Exception e) {
                    CloudLog.LOGGER.warn("[cccloud] Error handling request", e);
                    sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, error("internal_error"));
                } finally {
                    req.release();
                }
            }));
    }

    private void dispatch(ChannelHandlerContext ctx, FullHttpRequest req, Router.Route route, String ip, List<AuthStore.Device> devices) {
        String path = new QueryStringDecoder(req.uri()).path();
        switch (route.id()) {
            case PAIR -> pair(ctx, req, ip);
            case PAIR_STATUS -> pairStatus(ctx, req, path.substring("/api/pair/".length()));
            case MAINFRAMES -> mainframes(ctx, req, devices);
            case ICON -> icon(ctx, req, ip, path);
            case WEBSOCKET -> upgrade(ctx, req, ip);
            case STATIC -> {
                var file = StaticFiles.resolve(path);
                if (file == null) sendText(ctx, req, HttpResponseStatus.NOT_FOUND, "Not found");
                else send(ctx, req, HttpResponseStatus.OK, file.contentType(), file.data(), file.immutable() ? "public, max-age=31536000, immutable" : "no-cache");
            }
        }
    }

    private static List<String> bearerTokens(FullHttpRequest req) {
        var tokens = new LinkedHashSet<String>();
        for (String header : req.headers().getAll(HttpHeaderNames.AUTHORIZATION)) {
            for (String part : header.split(",")) {
                String t = part.trim();
                if (!t.regionMatches(true, 0, "Bearer ", 0, 7)) continue;
                t = t.substring(7).trim();
                if (!t.isEmpty() && tokens.size() < MAX_TOKENS_PER_REQUEST) tokens.add(t);
            }
        }
        return List.copyOf(tokens);
    }

    // ---- /api/icon ---------------------------------------------------------------------------------------------------

    /** Max icon requests per minute per IP. */
    static final int ICON_RATE = 200;

    private void icon(ChannelHandlerContext ctx, FullHttpRequest req, String ip, String path) {
        String rest = path.substring("/api/icon/".length());
        int slash = rest.indexOf('/');
        String id = rest.substring(0, slash) + ":" + rest.substring(slash + 1, rest.length() - ".png".length());
        if (!ai.ezfox.cccloud.icons.IconStore.isValidId(id)) {
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("not_found"));
            return;
        }
        if (instance.iconLimits.size() > 10_000) instance.iconLimits.clear();
        if (!instance.iconLimits.computeIfAbsent(ip, k -> new TokenBucket(ICON_RATE, ICON_RATE / 60.0)).tryAcquire()) {
            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS, error("rate_limited"));
            return;
        }
        var key = net.minecraft.resources.Identifier.tryParse(id);
        if (key == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)) {
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("unknown_item"));
            return;
        }
        var service = ai.ezfox.cccloud.icons.IconService.current();
        byte[] png = service == null ? null : service.store().get(id);
        if (png != null) {
            var crc = new java.util.zip.CRC32();
            crc.update(png);
            String etag = "\"" + Long.toHexString(crc.getValue()) + "\"";
            if (etag.equals(req.headers().get(HttpHeaderNames.IF_NONE_MATCH))) {
                var notModified = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED, Unpooled.EMPTY_BUFFER);
                notModified.headers().set(HttpHeaderNames.ETAG, etag);
                notModified.headers().set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=86400");
                write(ctx, req, notModified);
                return;
            }
            var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(png));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/png");
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=86400");
            response.headers().set(HttpHeaderNames.ETAG, etag);
            write(ctx, req, response);
            return;
        }
        // Not rendered yet: the plain texture from the mod jar, re-requested later (no-cache) so the real icon replaces it.
        byte[] fallback = ai.ezfox.cccloud.icons.IconService.fallback(rest.substring(0, slash), rest.substring(slash + 1, rest.length() - ".png".length()));
        if (fallback != null) {
            send(ctx, req, HttpResponseStatus.OK, "image/png", fallback, "no-cache");
            return;
        }
        sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("no_icon"));
    }

    // ---- /api/pair ---------------------------------------------------------------------------------------------------

    private void pair(ChannelHandlerContext ctx, FullHttpRequest req, String ip) {
        if (instance.pairLimiter.isBlocked(ip)) {
            var response = json(HttpResponseStatus.TOO_MANY_REQUESTS, error("rate_limited"));
            response.headers().set(HttpHeaderNames.RETRY_AFTER, "60");
            write(ctx, req, response);
            return;
        }
        var request = ClientMessages.parsePair(req.content().toString(StandardCharsets.UTF_8));
        if (request == null) {
            instance.pairLimiter.recordFailure(ip);
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("invalid_request"));
            return;
        }
        req.retain();
        CompletableFuture.supplyAsync(() -> {
                var pending = instance.auth.pair(request.code(), request.name(), ip);
                if (pending != null) instance.bridge.requestApproval(pending);
                else CloudLog.LOGGER.info("[cccloud] Failed pairing attempt from {}", ip);
                return pending;
            }, instance.bridge.serverExecutor())
            .orTimeout(5, TimeUnit.SECONDS)
            .whenComplete((pending, err) -> ctx.executor().execute(() -> {
                try {
                    if (err != null) {
                        sendJson(ctx, req, HttpResponseStatus.SERVICE_UNAVAILABLE, error("unavailable"));
                    } else if (pending == null) {
                        instance.pairLimiter.recordFailure(ip);
                        sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, error("invalid_code"));
                    } else {
                        var json = new JsonObject();
                        json.addProperty("status", "pending");
                        json.addProperty("pairingId", pending.id());
                        json.addProperty("expiresIn", AuthStore.APPROVAL_TTL_MS / 1000);
                        sendJson(ctx, req, HttpResponseStatus.ACCEPTED, json);
                    }
                } finally {
                    req.release();
                }
            }));
    }

    private void pairStatus(ChannelHandlerContext ctx, FullHttpRequest req, String pairingId) {
        var pending = instance.auth.poll(pairingId);
        var json = new JsonObject();
        if (pending == null) {
            json.addProperty("status", "expired");
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, json);
            return;
        }
        switch (pending.state()) {
            case PENDING -> {
                json.addProperty("status", "pending");
                sendJson(ctx, req, HttpResponseStatus.OK, json);
            }
            case DENIED -> {
                json.addProperty("status", "denied");
                sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, json);
            }
            case APPROVED -> {
                var device = pending.device();
                json.addProperty("status", "approved");
                json.addProperty("token", pending.token());
                json.addProperty("mainframeId", pending.mainframeId().toString());
                var info = WebHub.INSTANCE.info(pending.mainframeId());
                json.addProperty("name", info != null ? info.name() : "Mainframe");
                if (device != null) {
                    json.addProperty("device", device.name());
                    json.addProperty("canControl", device.canControl());
                }
                sendJson(ctx, req, HttpResponseStatus.OK, json);
            }
        }
    }

    // ---- /api/mainframes ---------------------------------------------------------------------------------------------

    private void mainframes(ChannelHandlerContext ctx, FullHttpRequest req, List<AuthStore.Device> devices) {
        var list = new JsonArray();
        for (var device : devices) {
            var info = WebHub.INSTANCE.info(device.mainframeId());
            var json = new JsonObject();
            json.addProperty("id", device.mainframeId().toString());
            json.addProperty("name", info != null ? info.name() : "Mainframe");
            json.addProperty("online", info != null && info.online());
            json.addProperty("enabled", info != null && info.enabled());
            json.addProperty("device", device.name());
            json.addProperty("canControl", device.canControl());
            list.add(json);
        }
        var body = new JsonObject();
        body.add("mainframes", list);
        sendJson(ctx, req, HttpResponseStatus.OK, body);
    }

    // ---- WebSocket ---------------------------------------------------------------------------------------------------

    private void upgrade(ChannelHandlerContext ctx, FullHttpRequest req, String ip) {
        if (!"websocket".equalsIgnoreCase(req.headers().get(HttpHeaderNames.UPGRADE))) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("websocket_expected"));
            return;
        }
        if (!originMatchesHost(req.headers().get(HttpHeaderNames.ORIGIN), req.headers().get(HttpHeaderNames.HOST))) {
            CloudLog.LOGGER.warn("[cccloud] Rejected WebSocket from {} with foreign Origin {}", ip, req.headers().get(HttpHeaderNames.ORIGIN));
            sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, error("bad_origin"));
            return;
        }
        String scheme = instance.ssl != null ? "wss://" : "ws://";
        String host = req.headers().get(HttpHeaderNames.HOST, "localhost");
        var config = WebSocketDecoderConfig.newBuilder().maxFramePayloadLength(ClientMessages.MAX_LAYOUT_FRAME).allowExtensions(false).build();
        var handshaker = new WebSocketServerHandshakerFactory(scheme + host + "/ws/client", null, config).newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            return;
        }
        var pipeline = ctx.pipeline();
        // Raw-byte guard in front of the frame decoder: 2 KB before auth, raised by the session after auth. Netty only
        // installs "wsdecoder" once the 101 response is written, so the guard is added in the handshake listener (the
        // client cannot send frames before it has that response). Without a decoder to guard: fail closed.
        var guard = new FrameSizeGuard();
        handshaker.handshake(ctx.channel(), req).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                future.channel().close();
                return;
            }
            var p = future.channel().pipeline();
            if (p.get("wsdecoder") == null) {
                CloudLog.LOGGER.error("[cccloud] WebSocket decoder missing after handshake; closing {}", ip);
                future.channel().close();
                return;
            }
            p.addBefore("wsdecoder", "ws-size-guard", guard);
        });
        pipeline.replace("idle", "idle", new IdleStateHandler(CloudWebServer.IDLE_TIMEOUT_S, 0, 0));
        pipeline.replace(this, "ws", new WebSocketSession(instance, handshaker, ip, guard));
        pipeline.addBefore("ws", "ws-aggregator", new WebSocketFrameAggregator(ClientMessages.MAX_LAYOUT_FRAME));
    }

    /** Blocks cross-site WebSocket hijacking: the Origin (if sent) must match the Host header. */
    static boolean originMatchesHost(@Nullable String origin, @Nullable String host) {
        if (origin == null) return true; // Non-browser clients; browsers always send Origin for WebSockets.
        if (host == null) return false;
        try {
            var uri = new URI(origin);
            if (uri.getHost() == null) return false;
            int port = uri.getPort();
            if (port == -1) port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            var hostUri = new URI("http://" + host);
            if (hostUri.getHost() == null) return false;
            int hostPort = hostUri.getPort();
            if (hostPort == -1) hostPort = port;
            return uri.getHost().equalsIgnoreCase(hostUri.getHost()) && port == hostPort;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- responses ---------------------------------------------------------------------------------------------------

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        CloudLog.LOGGER.debug("[cccloud] HTTP connection error", cause);
        ctx.close();
    }

    private static JsonObject error(String code) {
        var json = new JsonObject();
        json.addProperty("error", code);
        return json;
    }

    private static FullHttpResponse json(HttpResponseStatus status, JsonObject body) {
        byte[] bytes = Json.GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        return response;
    }

    private void sendJson(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, JsonObject body) {
        write(ctx, req, json(status, body));
    }

    private void sendText(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, String text) {
        send(ctx, req, status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8), "no-store");
    }

    private void send(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, String type, byte[] body, String cache) {
        boolean head = req.method().equals(HttpMethod.HEAD);
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, head ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(body));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, type);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, cache);
        if (head) response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        write(ctx, req, response);
    }

    private void write(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse response) {
        addSecurityHeaders(response, req.headers().get(HttpHeaderNames.HOST), instance.ssl != null);
        if (!response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) HttpUtil.setContentLength(response, response.content().readableBytes());
        boolean keepAlive = HttpUtil.isKeepAlive(req) && response.status().code() < 400;
        response.headers().set(HttpHeaderNames.CONNECTION, keepAlive ? HttpHeaderValues.KEEP_ALIVE : HttpHeaderValues.CLOSE);
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    static void addSecurityHeaders(FullHttpResponse response, @Nullable String host, boolean tls) {
        String connect = "'self'";
        if (host != null && SAFE_HOST.matcher(host).matches()) connect += " ws://" + host + " wss://" + host;
        var h = response.headers();
        h.set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; "
            + "connect-src " + connect + "; manifest-src 'self'; worker-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Cross-Origin-Opener-Policy", "same-origin");
        h.set("Cross-Origin-Resource-Policy", "same-origin");
        h.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (tls) h.set("Strict-Transport-Security", "max-age=31536000");
    }
}
