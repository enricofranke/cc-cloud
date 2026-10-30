package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.publicapi.ApiKeys;
import ai.ezfox.cccloud.publicapi.ApiScope;
import ai.ezfox.cccloud.publicapi.ApiSettings;
import ai.ezfox.cccloud.publicapi.ApiV1;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * The read-only event stream of the public API ({@code GET /api/v1/mainframes/<id>/events}, docs/SPEC.md v0.10):
 * Server-Sent Events of the hub's section messages, translated into the API's section form. It is a {@link HubSession}
 * without a device, so it gets exactly what a dashboard with the same permissions would get (restricted sections only
 * with {@code view.positions}) but never counts as a browser. The key owner's rights are re-checked every 5 seconds;
 * lost rights end the stream. Slow readers are disconnected, at most {@code apiStreamsPerKey} streams per key and
 * {@link ApiSettings#MAX_STREAMS} in total.
 */
final class ApiEventStream implements HubSession {
    static final int HEARTBEAT_S = 20;
    static final int RECHECK_S = 5;

    private final WebInstance instance;
    private final Channel channel;
    private final ApiKeys.Key key;
    private final String ip;
    private final AtomicInteger perKey;
    private volatile Set<Perm> perms;
    private volatile boolean closed;
    private @Nullable ScheduledFuture<?> heartbeat;
    private @Nullable ScheduledFuture<?> recheck;

    private ApiEventStream(WebInstance instance, Channel channel, ApiKeys.Key key, String ip, Set<Perm> perms, AtomicInteger perKey) {
        this.instance = instance;
        this.channel = channel;
        this.key = key;
        this.ip = ip;
        this.perms = perms;
        this.perKey = perKey;
    }

    /** Checks the limits and the owner's rights (server thread), then turns the connection into an event stream. */
    static void open(HttpHandler http, WebInstance instance, ChannelHandlerContext ctx, FullHttpRequest req, ApiKeys.Key key, String ip, @Nullable String origin) {
        var settings = instance.api;
        if (!settings.events()) {
            ApiHandler.respond(http, ctx, req, 409, ApiHandler.error("disabled"), origin, 0);
            return;
        }
        if (!key.scopes().contains(ApiScope.READ_SECTIONS)) {
            ApiHandler.respond(http, ctx, req, 403, ApiHandler.error("missing_scope"), origin, 0);
            return;
        }
        String path = new io.netty.handler.codec.http.QueryStringDecoder(req.uri()).path();
        UUID mainframeId = ClientMessages.parseUuid(path.split("/")[4]);
        if (!key.mainframeId().equals(mainframeId)) {
            ApiHandler.respond(http, ctx, req, 404, ApiHandler.error("not_found"), origin, 0);
            return;
        }
        CompletableFuture.supplyAsync(() -> instance.bridge.apiStreamAccess(key, ip), instance.bridge.serverExecutor())
            .orTimeout(5, TimeUnit.SECONDS)
            .whenComplete((perms, err) -> ctx.executor().execute(() -> {
                if (err != null || perms == null) {
                    ApiHandler.respond(http, ctx, req, err != null ? 503 : 403, ApiHandler.error(err != null ? "unavailable" : "no_access"), origin, 0);
                    return;
                }
                var perKey = instance.apiStreams.computeIfAbsent(key.hash(), k -> new AtomicInteger());
                if (instance.apiStreamTotal.incrementAndGet() > ApiSettings.MAX_STREAMS | perKey.incrementAndGet() > settings.streamsPerKey()) {
                    instance.apiStreamTotal.decrementAndGet();
                    perKey.decrementAndGet();
                    ApiHandler.respond(http, ctx, req, 429, ApiHandler.error("too_many_streams"), origin, 30);
                    return;
                }
                new ApiEventStream(instance, ctx.channel(), key, ip, perms, perKey).start(http, ctx, req, origin);
            }));
    }

    private void start(HttpHandler http, ChannelHandlerContext ctx, FullHttpRequest req, @Nullable String origin) {
        http.streaming = true;
        var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream; charset=utf-8");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        response.headers().set("X-Accel-Buffering", "no");
        HttpUtil.setTransferEncodingChunked(response, true);
        if (origin != null) ApiHandler.cors(response, origin);
        HttpHandler.addSecurityHeaders(response, req.headers().get(HttpHeaderNames.HOST), instance.ssl != null);
        ctx.writeAndFlush(response);
        // The connection now only carries events: no read timeout (the client sends nothing), heartbeats instead.
        if (ctx.pipeline().get("idle") != null) ctx.pipeline().remove("idle");
        channel.closeFuture().addListener(f -> cleanup());
        heartbeat = ctx.executor().scheduleAtFixedRate(() -> write(": keep-alive\n\n"), HEARTBEAT_S, HEARTBEAT_S, TimeUnit.SECONDS);
        recheck = ctx.executor().scheduleAtFixedRate(this::recheck, RECHECK_S, RECHECK_S, TimeUnit.SECONDS);
        CloudLog.LOGGER.info("[cccloud] API key '{}' ({}) opened an event stream from {}", key.name(), key.playerName(), ip);
        WebHub.INSTANCE.tryAddSession(this);
    }

    /** The owner's rights, the key itself (revoked, expired) and its IP list, every 5 seconds. */
    private void recheck() {
        if (closed) return;
        var current = instance.apiKeys.byHash(key.hash());
        if (current == null || current.expired(System.currentTimeMillis()) || !instance.api.enabled() || !instance.api.events()) {
            close(4401, current == null ? "revoked" : "expired");
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                Set<Perm> now;
                try {
                    now = instance.bridge.apiStreamAccess(key, ip);
                } catch (RuntimeException e) {
                    now = null;
                }
                var granted = now;
                channel.eventLoop().execute(() -> {
                    if (granted == null) close(4403, "no_access");
                    else if (!granted.equals(perms)) WebHub.INSTANCE.pushRole(this, Role.VIEWER, granted);
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            close(1001, "server stopping");
        }
    }

    private void cleanup() {
        if (heartbeat != null) heartbeat.cancel(false);
        if (recheck != null) recheck.cancel(false);
        WebHub.INSTANCE.removeSession(this);
        if (!closed) closed = true;
        perKey.decrementAndGet();
        instance.apiStreamTotal.decrementAndGet();
    }

    // ---- HubSession --------------------------------------------------------------------------------------------------

    @Override
    public UUID mainframeId() {
        return key.mainframeId();
    }

    @Override
    public AuthStore.@Nullable Device device() {
        return null;
    }

    @Override
    public Role role() {
        return Role.VIEWER;
    }

    @Override
    public Set<Perm> perms() {
        return perms;
    }

    @Override
    public void setAccess(Role role, Set<Perm> perms) {
        this.perms = perms;
    }

    @Override
    public boolean countsAsBrowser() {
        return false;
    }

    /** Hub messages → events ({@code snapshot}, {@code section}, {@code value}, {@code removed}, {@code state}, {@code alert}). */
    @Override
    public void send(List<String> messages) {
        if (closed) return;
        var out = new StringBuilder();
        for (String text : messages) {
            JsonObject message;
            try {
                if (!(JsonParser.parseString(text) instanceof JsonObject m)) continue;
                message = m;
            } catch (RuntimeException e) {
                continue;
            }
            String t = message.get("t") instanceof JsonElement e && e.isJsonPrimitive() ? e.getAsString() : "";
            var data = switch (t) {
                case "snapshot" -> {
                    var sections = new JsonArray();
                    if (message.get("uplinks") instanceof JsonArray uplinks) {
                        for (var u : uplinks) {
                            var section = u instanceof JsonObject o ? ApiV1.apiSection(key.mainframeId(), o, perms) : null;
                            if (section != null) sections.add(section);
                        }
                    }
                    var json = new JsonObject();
                    json.add("sections", sections);
                    yield json;
                }
                case "uplink" -> ApiV1.apiSection(key.mainframeId(), message, perms);
                case "value" -> {
                    var json = new JsonObject();
                    json.add("sectionId", message.get("uplinkId"));
                    json.add("id", message.get("id"));
                    json.add("value", message.get("value"));
                    yield json;
                }
                case "uplink_removed" -> {
                    var json = new JsonObject();
                    json.add("sectionId", message.get("uplinkId"));
                    yield json;
                }
                case "state" -> {
                    var json = new JsonObject();
                    json.add("online", message.get("online"));
                    json.add("enabled", message.get("enabled"));
                    yield json;
                }
                case "alert" -> {
                    var json = new JsonObject();
                    if (message.has("uplinkId")) json.add("sectionId", message.get("uplinkId"));
                    json.add("text", message.get("text"));
                    json.add("level", message.get("level"));
                    yield json;
                }
                default -> null;
            };
            if (data == null) continue;
            String event = switch (t) {
                case "uplink" -> "section";
                case "uplink_removed" -> "removed";
                default -> t;
            };
            out.append("event: ").append(event).append('\n').append("data: ").append(Json.GSON.toJson(data)).append("\n\n");
        }
        if (!out.isEmpty()) write(out.toString());
    }

    private void write(String text) {
        if (closed || !channel.isActive()) return;
        if (!channel.isWritable()) {
            CloudLog.LOGGER.info("[cccloud] Closing the event stream of API key '{}' ({}): the reader is too slow", key.name(), ip);
            close(1013, "too_slow");
            return;
        }
        channel.writeAndFlush(new DefaultHttpContent(Unpooled.copiedBuffer(text, StandardCharsets.UTF_8)));
    }

    /** Ends the stream with {@code event: error}. */
    @Override
    public void close(int code, String reason) {
        if (closed) return;
        var json = new JsonObject();
        json.addProperty("error", reason);
        write("event: error\ndata: " + Json.GSON.toJson(json) + "\n\n");
        closed = true;
        if (channel.isActive()) channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
    }
}
