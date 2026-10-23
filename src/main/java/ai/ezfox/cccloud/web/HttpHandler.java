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
        // Bodies: 4 KB, except script uploads (256 KB, the aggregator's limit).
        if (req.content().readableBytes() > ClientMessages.MAX_BODY && route.id() != Router.Id.SCRIPT_PUT) {
            sendJson(ctx, req, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, error("too_large"));
            return;
        }

        // Central access check (deny by default).
        switch (route.access()) {
            case PUBLIC -> dispatch(ctx, req, route, ip, List.of());
            case DEVICE -> authenticateDevices(ctx, req, route, ip);
        }
    }

    /** Max v0.6 API requests per minute per IP. */
    static final int API_RATE = 120;

    /**
     * Resolves the Bearer tokens, then runs the route on the server thread, where the {@link AccessApi} checks the
     * live role of the device's player on the mainframe the request touches (deny by default).
     */
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
        if (instance.apiLimits.size() > 10_000) instance.apiLimits.clear();
        if (!instance.apiLimits.computeIfAbsent(ip, k -> new TokenBucket(API_RATE, API_RATE / 60.0)).tryAcquire()) {
            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS, error("rate_limited"));
            return;
        }
        if (route.id().isScript()) {
            script(ctx, req, route, ip, devices.getFirst());
            return;
        }
        if (route.id() == Router.Id.MAP_TILE) {
            mapTile(ctx, req, devices.getFirst());
            return;
        }
        if (route.id() == Router.Id.VOXELS) {
            voxels(ctx, req, devices.getFirst());
            return;
        }
        String path = new QueryStringDecoder(req.uri()).path();
        String body = req.content().toString(StandardCharsets.UTF_8);
        CompletableFuture.supplyAsync(() -> instance.bridge.api(route.id(), devices, path, body, ip), instance.bridge.serverExecutor())
            .orTimeout(5, TimeUnit.SECONDS)
            .whenComplete((response, err) -> ctx.executor().execute(() -> {
                try {
                    if (err != null || response == null) {
                        if (err != null && !(err instanceof java.util.concurrent.TimeoutException)) CloudLog.LOGGER.warn("[cccloud] Error handling {}", route.id(), err);
                        sendJson(ctx, req, HttpResponseStatus.SERVICE_UNAVAILABLE, error("unavailable"));
                    } else {
                        sendJson(ctx, req, HttpResponseStatus.valueOf(response.status()), response.body());
                    }
                } catch (Exception e) {
                    CloudLog.LOGGER.warn("[cccloud] Error handling request", e);
                    sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, error("internal_error"));
                }
            }));
    }

    // ---- map tiles ----------------------------------------------------------------------------------------------------

    /** Map tiles per device per minute. */
    static final int MAP_RATE = 60;

    private void mapTile(ChannelHandlerContext ctx, FullHttpRequest req, AuthStore.Device device) {
        String[] parts = new QueryStringDecoder(req.uri()).path().split("/");
        // /api/mainframes/<mf>/map/<dim>/surface/<tx>/<tz>.png or …/slice/<y>/<tx>/<tz>.png
        var mainframeId = ClientMessages.parseUuid(parts[3]);
        String dim = parts[5];
        boolean slice = parts[6].equals("slice");
        int y, tx, tz;
        try {
            y = slice ? Integer.parseInt(parts[7]) : 0;
            tx = Integer.parseInt(parts[slice ? 8 : 7]);
            tz = Integer.parseInt(parts[slice ? 9 : 8].substring(0, parts[slice ? 9 : 8].length() - 4));
        } catch (RuntimeException e) {
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("not_found"));
            return;
        }
        if (instance.mapLimits.size() > 10_000) instance.mapLimits.clear();
        if (!instance.mapLimits.computeIfAbsent(device.tokenHash(), k -> new TokenBucket(MAP_RATE, MAP_RATE / 60.0)).tryAcquire()) {
            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS, error("rate_limited"));
            return;
        }
        String ifNoneMatch = req.headers().get(HttpHeaderNames.IF_NONE_MATCH);
        req.retain();
        CompletableFuture.supplyAsync(() -> instance.bridge.mapTile(device, mainframeId, dim, slice, y, tx, tz), instance.bridge.serverExecutor())
            .thenCompose(f -> f)
            .orTimeout(15, TimeUnit.SECONDS)
            .whenComplete((result, err) -> ctx.executor().execute(() -> {
                try {
                    if (err != null || result == null) {
                        sendJson(ctx, req, HttpResponseStatus.SERVICE_UNAVAILABLE, error("unavailable"));
                    } else if (result.error() != null) {
                        sendJson(ctx, req, HttpResponseStatus.valueOf(result.status()), error(result.error()));
                    } else if (result.png() == null) {
                        var empty = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT, Unpooled.EMPTY_BUFFER);
                        empty.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=60");
                        write(ctx, req, empty);
                    } else if (result.etag() != null && result.etag().equals(ifNoneMatch)) {
                        var notModified = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED, Unpooled.EMPTY_BUFFER);
                        notModified.headers().set(HttpHeaderNames.ETAG, result.etag());
                        notModified.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=60");
                        write(ctx, req, notModified);
                    } else {
                        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(result.png()));
                        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/png");
                        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=60");
                        if (result.etag() != null) response.headers().set(HttpHeaderNames.ETAG, result.etag());
                        write(ctx, req, response);
                    }
                } finally {
                    req.release();
                }
            }));
    }

    /** Voxel sections per device per second. */
    static final int VOXEL_RATE = 40;

    private void voxels(ChannelHandlerContext ctx, FullHttpRequest req, AuthStore.Device device) {
        String[] parts = new QueryStringDecoder(req.uri()).path().split("/");
        // /api/mainframes/<mf>/voxels/<dim>/<cx>/<cy>/<cz>
        var mainframeId = ClientMessages.parseUuid(parts[3]);
        String dim = parts[5];
        int cx, cy, cz;
        try {
            cx = Integer.parseInt(parts[6]);
            cy = Integer.parseInt(parts[7]);
            cz = Integer.parseInt(parts[8]);
        } catch (RuntimeException e) {
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("not_found"));
            return;
        }
        if (instance.voxelLimits.size() > 10_000) instance.voxelLimits.clear();
        if (!instance.voxelLimits.computeIfAbsent(device.tokenHash(), k -> new TokenBucket(VOXEL_RATE, VOXEL_RATE)).tryAcquire()) {
            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS, error("rate_limited"));
            return;
        }
        req.retain();
        CompletableFuture.supplyAsync(() -> instance.bridge.voxels(device, mainframeId, dim, cx, cy, cz), instance.bridge.serverExecutor())
            .thenCompose(f -> f)
            .orTimeout(10, TimeUnit.SECONDS)
            .whenComplete((result, err) -> ctx.executor().execute(() -> {
                try {
                    if (err != null || result == null) {
                        sendJson(ctx, req, HttpResponseStatus.SERVICE_UNAVAILABLE, error("unavailable"));
                    } else if (result.error() != null) {
                        sendJson(ctx, req, HttpResponseStatus.valueOf(result.status()), error(result.error()));
                    } else if (result.json() == null) {
                        var empty = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT, Unpooled.EMPTY_BUFFER);
                        empty.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
                        write(ctx, req, empty);
                    } else {
                        var response = json(HttpResponseStatus.OK, result.json());
                        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=5");
                        write(ctx, req, response);
                    }
                } finally {
                    req.release();
                }
            }));
    }

    // ---- script import ------------------------------------------------------------------------------------------------

    /** Script writes: 10 per minute per device. */
    static final int SCRIPT_RATE = 10;

    /** Script import: rate limit and PIN (PBKDF2 on the PIN executor), then the computer on the server thread. */
    private void script(ChannelHandlerContext ctx, FullHttpRequest req, Router.Route route, String ip, AuthStore.Device device) {
        var decoder = new QueryStringDecoder(req.uri());
        String[] parts = decoder.path().split("/");
        // /api/mainframes/<mf>/computers/<uplink>/files[/<a>[/<b>]] or /run
        var mainframeId = ClientMessages.parseUuid(parts[3]);
        var uplinkId = ClientMessages.parseUuid(parts[5]);
        String file = parts.length > 7 ? String.join("/", java.util.Arrays.copyOfRange(parts, 7, parts.length)) : null;
        var query = new java.util.HashMap<String, String>();
        for (var e : decoder.parameters().entrySet()) if (!e.getValue().isEmpty() && e.getKey().length() <= 16) query.put(e.getKey(), e.getValue().getFirst());
        byte[] body = new byte[req.content().readableBytes()];
        req.content().getBytes(req.content().readerIndex(), body);
        var request = new GameBridge.ScriptRequest(route.id(), device, mainframeId, uplinkId, file, query, body, ip);
        if (!route.id().isScriptWrite()) {
            runScript(ctx, req, request);
            return;
        }
        if (instance.scriptLimits.size() > 10_000) instance.scriptLimits.clear();
        if (!instance.scriptLimits.computeIfAbsent(device.tokenHash(), k -> new TokenBucket(SCRIPT_RATE, SCRIPT_RATE / 60.0)).tryAcquire()) {
            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS, error("rate_limited"));
            return;
        }
        if (!instance.auth.hasPin(mainframeId)) {
            runScript(ctx, req, request);
            return;
        }
        String pin = req.headers().get("X-Cloud-Pin");
        long locked = instance.auth.pinLockedFor(device.tokenHash());
        if (locked > 0) {
            var body2 = error("pin_locked");
            body2.addProperty("retryAfter", locked);
            sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, body2);
            return;
        }
        if (pin == null || pin.isEmpty()) {
            sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, error("pin_required"));
            return;
        }
        req.retain();
        try {
            instance.pinExecutor.execute(() -> {
                var check = instance.auth.checkPin(mainframeId, device.tokenHash(), pin);
                ctx.executor().execute(() -> {
                    try {
                        switch (check.result()) {
                            case OK, NOT_SET -> runScript(ctx, req, request);
                            case WRONG -> {
                                var e = error("wrong_pin");
                                e.addProperty("attemptsLeft", check.attemptsLeft());
                                sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, e);
                            }
                            case LOCKED -> {
                                var e = error("pin_locked");
                                e.addProperty("retryAfter", check.retryAfterSeconds());
                                sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, e);
                                instance.bridge.serverExecutor().execute(() -> instance.bridge.onPinLockout(device, mainframeId, ip));
                            }
                        }
                    } finally {
                        req.release();
                    }
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            req.release();
            sendJson(ctx, req, HttpResponseStatus.SERVICE_UNAVAILABLE, error("busy"));
        }
    }

    private void runScript(ChannelHandlerContext ctx, FullHttpRequest req, GameBridge.ScriptRequest request) {
        req.retain();
        CompletableFuture.supplyAsync(() -> instance.bridge.script(request), instance.bridge.serverExecutor())
            .orTimeout(5, TimeUnit.SECONDS)
            .whenComplete((response, err) -> ctx.executor().execute(() -> {
                try {
                    if (err != null || response == null) {
                        if (err != null && !(err instanceof java.util.concurrent.TimeoutException)) CloudLog.LOGGER.warn("[cccloud] Script import failed", err);
                        sendJson(ctx, req, HttpResponseStatus.SERVICE_UNAVAILABLE, error("unavailable"));
                    } else if (response.raw() != null) {
                        send(ctx, req, HttpResponseStatus.valueOf(response.status()), "text/plain; charset=utf-8", response.raw(), "no-store");
                    } else {
                        sendJson(ctx, req, HttpResponseStatus.valueOf(response.status()), response.body());
                    }
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
            case MAINFRAMES, DEVICES, DEVICE_DELETE, ACCESS_GET, ACCESS_POST, SHARES_GET, SHARES_POST, SHARE_DELETE, PUSH_GET, PUSH_POST,
                 SCRIPT_LIST, SCRIPT_GET, SCRIPT_PUT, SCRIPT_DELETE, SCRIPT_RUN, SCRIPT_MOVE, MAP_TILE, VOXELS ->
                sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("not_found")); // only reachable through authenticateDevices
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
                var pending = instance.auth.pair(request.code(), request.name(), ip, req.headers().get(HttpHeaderNames.USER_AGENT, ""));
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
                // Seconds left for the in-game approval (the browser shows a countdown).
                json.addProperty("expiresIn", Math.max(0, (pending.expiresAt() - System.currentTimeMillis() + 999) / 1000));
                sendJson(ctx, req, HttpResponseStatus.OK, json);
            }
            case DENIED -> {
                json.addProperty("status", "denied");
                sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, json);
            }
            case APPROVED -> {
                var device = pending.device();
                if (device == null) {
                    sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("expired"));
                    return;
                }
                // The token is handed out exactly once (poll removed it); the role comes from the server thread.
                String token = pending.token();
                req.retain();
                CompletableFuture.supplyAsync(() -> instance.bridge.api(Router.Id.PAIR_STATUS, List.of(device), "", "", ""), instance.bridge.serverExecutor())
                    .orTimeout(5, TimeUnit.SECONDS)
                    .whenComplete((info, err) -> ctx.executor().execute(() -> {
                        try {
                            var body = new JsonObject();
                            body.addProperty("status", "approved");
                            body.addProperty("token", token);
                            if (info != null) for (var e : info.body().entrySet()) body.add(e.getKey(), e.getValue());
                            sendJson(ctx, req, HttpResponseStatus.OK, body);
                        } finally {
                            req.release();
                        }
                    }));
            }
        }
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
        // Dashboards and share links (/s/<id>) must never be indexed.
        h.set("X-Robots-Tag", "noindex, nofollow");
        if (tls) h.set("Strict-Transport-Security", "max-age=31536000");
    }
}
