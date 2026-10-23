package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonObject;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.timeout.IdleStateEvent;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * One dashboard WebSocket. The first message must be {@code {"t":"auth","token","mainframeId?"}} or (share links)
 * {@code {"t":"auth_share","id","secret"}} within 5 seconds. Every other message type is denied until authentication
 * (token + live role of the owning player) succeeded. Every message of an authenticated session goes through the
 * central {@link AccessGate}; share sessions are hard-denied everything except pong.
 */
final class WebSocketSession extends SimpleChannelInboundHandler<WebSocketFrame> implements HubSession {
    private static final int MAX_INVALID_MESSAGES = 20;

    private enum State { NEW, AUTHENTICATING, READY }

    private final WebInstance instance;
    private final WebSocketServerHandshaker handshaker;
    private final String ip;
    private final TokenBucket actionLimit = new TokenBucket(10, 10);
    /** Item searches: 5 per second (docs/SPEC.md). */
    private final TokenBucket queryLimit = new TokenBucket(5, 5);
    private @Nullable Channel channel;
    private volatile State state = State.NEW;
    private volatile AuthStore.@Nullable Device device;
    private volatile AccessStore.@Nullable Share share;
    private volatile Role role = Role.NONE;
    private volatile UUID mainframeId = new UUID(0, 0);
    private volatile boolean closed;
    private int invalidMessages;
    private @Nullable ScheduledFuture<?> authDeadline;
    private @Nullable ScheduledFuture<?> pingTask;

    private final @Nullable FrameSizeGuard sizeGuard;

    WebSocketSession(WebInstance instance, WebSocketServerHandshaker handshaker, String ip, @Nullable FrameSizeGuard sizeGuard) {
        this.instance = instance;
        this.handshaker = handshaker;
        this.ip = ip;
        this.sizeGuard = sizeGuard;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        channel = ctx.channel();
        authDeadline = ctx.executor().schedule(() -> {
            if (state != State.READY) close(4401, "auth timeout");
        }, CloudWebServer.AUTH_TIMEOUT_S, TimeUnit.SECONDS);
        pingTask = ctx.executor().scheduleAtFixedRate(() -> {
            if (state == State.READY) send(List.of("{\"t\":\"ping\"}"));
        }, CloudWebServer.PING_INTERVAL_S, CloudWebServer.PING_INTERVAL_S, TimeUnit.SECONDS);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        try {
            if (frame instanceof CloseWebSocketFrame close) {
                handshaker.close(ctx.channel(), close.retain());
            } else if (frame instanceof PingWebSocketFrame ping) {
                ctx.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
            } else if (frame instanceof TextWebSocketFrame text) {
                handleText(text.text());
            } else if (frame instanceof BinaryWebSocketFrame) {
                close(1003, "binary frames not supported");
            }
        } catch (Exception e) {
            CloudLog.LOGGER.warn("[cccloud] Error handling WebSocket message from {}", ip, e);
            close(1011, "internal error");
        }
    }

    private void handleText(String text) {
        var message = ClientMessages.parse(text);

        // Deny by default: before READY only the auth message is processed.
        if (state != State.READY) {
            if (state == State.NEW && message instanceof ClientMessages.Auth auth) {
                authenticate(auth.token(), auth.mainframeId());
            } else if (state == State.NEW && message instanceof ClientMessages.AuthShare auth) {
                authenticateShare(auth.id(), auth.secret());
            } else if (!ClientMessages.isPublic(message) || state == State.NEW) {
                close(4401, "auth required");
            }
            return;
        }

        if (share != null) {
            // Share viewers are read-only: hard deny on everything but pong.
            String deny = AccessGate.shareDeny(text);
            if (deny != null) {
                if (++invalidMessages > MAX_INVALID_MESSAGES * 5) {
                    close(1008, "too many messages");
                    return;
                }
                send(List.of(deny));
            }
            return;
        }

        var current = device;
        if (current == null || !instance.auth.isValid(current.tokenHash())) {
            close(4401, "revoked");
            return;
        }
        switch (message) {
            case ClientMessages.Pong pong -> {
            }
            case ClientMessages.Auth auth -> {
            }
            case ClientMessages.AuthShare auth -> {
            }
            case ClientMessages.Invalid invalid -> {
                if (++invalidMessages > MAX_INVALID_MESSAGES) {
                    close(1008, "too many invalid messages");
                    return;
                }
                if (invalid.reqId() != null) sendResult(invalid.reqId(), invalid.reason(), "detail", invalid.detail());
                else send(List.of(errorJson(invalid.reason())));
            }
            case ClientMessages.Action action -> action(current, action);
            case ClientMessages.Query query -> query(current, query);
            case ClientMessages.LayoutSet layout -> layout(current, layout);
            case ClientMessages.NetUnlink unlink -> netUnlink(current, unlink);
        }
    }

    private void authenticate(String token, @Nullable UUID requested) {
        var candidate = instance.auth.authenticate(token);
        if (candidate == null) {
            CloudLog.LOGGER.info("[cccloud] Rejected WebSocket login with an invalid or expired token from {}", ip);
            deny("unauthorized", 4401);
            return;
        }
        // Account tokens must name the mainframe; mainframe-limited tokens may only open their own.
        UUID target = requested != null ? requested : candidate.mainframeId();
        if (target == null) {
            deny("mainframe_required", 4401);
            return;
        }
        if (candidate.mainframeId() != null && !candidate.mainframeId().equals(target)) {
            deny("no_access", 4403);
            return;
        }
        state = State.AUTHENTICATING;
        try {
            instance.bridge.serverExecutor().execute(() -> {
                Role resolved;
                try {
                    resolved = instance.bridge.authorize(candidate, target, ip);
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while authorizing a dashboard login from {}", ip, e);
                    resolved = Role.NONE;
                }
                var ch = channel;
                if (ch == null) return;
                Role granted = resolved;
                ch.eventLoop().execute(() -> {
                    if (granted == Role.NONE) {
                        CloudLog.LOGGER.info("[cccloud] Rejected WebSocket login of device '{}' ({}) to {}: no role", candidate.name(), ip, target);
                        deny("no_access", 4403);
                        return;
                    }
                    if (closed) return;
                    device = candidate;
                    mainframeId = target;
                    role = granted;
                    state = State.READY;
                    // Only authenticated sessions may send big frames (layout_set, up to 64 KB).
                    if (sizeGuard != null) sizeGuard.raiseLimit(ClientMessages.MAX_LAYOUT_FRAME);
                    if (authDeadline != null) authDeadline.cancel(false);
                    WebHub.INSTANCE.addSession(this);
                });
            });
        } catch (RejectedExecutionException e) {
            close(1013, "server busy");
        }
    }

    /** Share-link viewer: id + 128-bit secret (from the URL fragment), expiry and the viewer limit. */
    private void authenticateShare(String id, String secret) {
        if (instance.shareLimiter.isBlocked(ip)) {
            deny("rate_limited", 4429);
            return;
        }
        if (instance.shareLogins.size() > 10_000) instance.shareLogins.clear();
        if (!instance.shareLogins.computeIfAbsent(id.length() > 32 ? id.substring(0, 32) : id, k -> new TokenBucket(30, 0.5)).tryAcquire()) {
            deny("rate_limited", 4429);
            return;
        }
        var check = instance.access.checkShare(id, secret);
        switch (check.status()) {
            case INVALID -> {
                instance.shareLimiter.recordFailure(ip);
                CloudLog.LOGGER.info("[cccloud] Rejected share link login from {}", ip);
                deny("share_invalid", 4401);
                return;
            }
            case EXPIRED -> {
                deny("share_expired", 4401);
                return;
            }
            case OK -> {
            }
        }
        var granted = check.share();
        share = granted;
        mainframeId = granted.mainframeId();
        role = Role.SHARE;
        state = State.READY;
        if (authDeadline != null) authDeadline.cancel(false);
        if (!WebHub.INSTANCE.tryAddSession(this)) {
            state = State.NEW;
            share = null;
            deny("share_full", 4429);
        }
    }

    private void deny(String error, int code) {
        send(List.of(errorJson(error)));
        close(code, error);
    }

    private void action(AuthStore.Device current, ClientMessages.Action action) {
        String denied = AccessGate.check(role, AccessGate.Kind.ACTION);
        if (denied != null) {
            sendResult(action.reqId(), denied);
            return;
        }
        if (!actionLimit.tryAcquire()) {
            sendResult(action.reqId(), "rate_limited");
            return;
        }
        UUID mainframe = mainframeId;
        boolean needsPin = WebHub.INSTANCE.widgetNeedsPin(mainframe, action.uplinkId().toString(), action.id());
        if (!needsPin) {
            runOnServer(current, action, false);
            return;
        }
        long lockedFor = instance.auth.pinLockedFor(current.tokenHash());
        if (lockedFor > 0) {
            sendResult(action.reqId(), "pin_locked", "retryAfter", lockedFor);
            return;
        }
        if (action.pin() == null) {
            sendResult(action.reqId(), "pin_required");
            return;
        }
        try {
            instance.pinExecutor.execute(() -> {
                var check = instance.auth.checkPin(mainframe, current.tokenHash(), action.pin());
                switch (check.result()) {
                    case OK -> runOnServer(current, action, true);
                    case NOT_SET -> sendResult(action.reqId(), "pin_not_set");
                    case WRONG -> sendResult(action.reqId(), "wrong_pin", "attemptsLeft", check.attemptsLeft());
                    case LOCKED -> {
                        sendResult(action.reqId(), "pin_locked", "retryAfter", check.retryAfterSeconds());
                        instance.bridge.serverExecutor().execute(() -> instance.bridge.onPinLockout(current, mainframe, ip));
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            sendResult(action.reqId(), "busy");
        }
    }

    private void runOnServer(AuthStore.Device current, ClientMessages.Action action, boolean pinVerified) {
        try {
            instance.bridge.serverExecutor().execute(() -> {
                java.util.concurrent.CompletableFuture<ActionResult> result;
                try {
                    result = instance.bridge.dispatchAction(current, mainframeId, action, pinVerified, ip);
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while dispatching an action from {}", ip, e);
                    result = ActionResult.done("internal_error");
                }
                result.whenComplete((r, ex) -> {
                    var outcome = ex != null || r == null ? ActionResult.fail("internal_error") : r;
                    sendResult(action.reqId(), outcome.error(), "jobId", outcome.jobId());
                });
            });
        } catch (RejectedExecutionException e) {
            sendResult(action.reqId(), "offline");
        }
    }

    /** Item search. Read-only, so viewers may search too. */
    private void query(AuthStore.Device current, ClientMessages.Query query) {
        String denied = AccessGate.check(role, AccessGate.Kind.QUERY);
        if (denied != null) {
            sendQueryResult(query.reqId(), QueryResult.fail(denied));
            return;
        }
        if (!queryLimit.tryAcquire()) {
            sendQueryResult(query.reqId(), QueryResult.fail("rate_limited"));
            return;
        }
        if (!"item_search".equals(WebHub.INSTANCE.widgetType(mainframeId, query.uplinkId().toString(), query.id()))) {
            sendQueryResult(query.reqId(), QueryResult.fail("unknown_widget"));
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                QueryResult result;
                try {
                    result = instance.bridge.dispatchQuery(current, mainframeId, query);
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while running an item search from {}", ip, e);
                    result = QueryResult.fail("internal_error");
                }
                sendQueryResult(query.reqId(), result);
            });
        } catch (RejectedExecutionException e) {
            sendQueryResult(query.reqId(), QueryResult.fail("offline"));
        }
    }

    /**
     * Saves the shared dashboard layout (validated during parsing). Needs the admin role; at most 5 saves per 10 s per
     * mainframe. Answered with layout_result; on success every client of the mainframe gets {"t":"layout"}.
     */
    private void layout(AuthStore.Device current, ClientMessages.LayoutSet layout) {
        String denied = AccessGate.check(role, AccessGate.Kind.LAYOUT);
        if (denied != null) {
            sendLayoutResult(layout.reqId(), denied);
            return;
        }
        if (!WebHub.INSTANCE.tryLayoutSave(mainframeId)) {
            sendLayoutResult(layout.reqId(), "rate_limited");
            return;
        }
        if (layout.error() != null || layout.layout() == null) {
            sendLayoutResult(layout.reqId(), layout.error() == null ? "invalid_layout" : layout.error());
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                String error;
                try {
                    error = instance.bridge.setLayout(current, mainframeId, layout.layout());
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while saving a dashboard layout from {}", ip, e);
                    error = "internal_error";
                }
                sendLayoutResult(layout.reqId(), error);
            });
        } catch (RejectedExecutionException e) {
            sendLayoutResult(layout.reqId(), "offline");
        }
    }

    /** Unlink a device from the network map (admin): {@code net_result}, then a network delta on success. */
    private void netUnlink(AuthStore.Device current, ClientMessages.NetUnlink unlink) {
        String denied = AccessGate.check(role, AccessGate.Kind.NET_UNLINK);
        if (denied != null) {
            sendNetResult(unlink.reqId(), denied);
            return;
        }
        if (!actionLimit.tryAcquire()) {
            sendNetResult(unlink.reqId(), "rate_limited");
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                String error;
                try {
                    error = instance.bridge.unlinkNode(current, mainframeId, unlink.nodeId());
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while unlinking a node from {}", ip, e);
                    error = "internal_error";
                }
                sendNetResult(unlink.reqId(), error);
            });
        } catch (RejectedExecutionException e) {
            sendNetResult(unlink.reqId(), "offline");
        }
    }

    private void sendNetResult(String reqId, @Nullable String error) {
        var json = new JsonObject();
        json.addProperty("t", "net_result");
        json.addProperty("reqId", reqId);
        json.addProperty("ok", error == null);
        if (error != null) json.addProperty("error", error);
        send(List.of(Json.GSON.toJson(json)));
    }

    private void sendLayoutResult(String reqId, @Nullable String error) {
        var json = new JsonObject();
        json.addProperty("t", "layout_result");
        json.addProperty("reqId", reqId);
        json.addProperty("ok", error == null);
        if (error != null) json.addProperty("error", error);
        send(List.of(Json.GSON.toJson(json)));
    }

    private void sendQueryResult(String reqId, QueryResult result) {
        var json = new JsonObject();
        json.addProperty("t", "query_result");
        json.addProperty("reqId", reqId);
        json.add("rows", result.rows());
        json.addProperty("truncated", result.truncated());
        if (result.error() != null) json.addProperty("error", result.error());
        send(List.of(Json.GSON.toJson(json)));
    }

    private void sendResult(@Nullable String reqId, @Nullable String error) {
        sendResult(reqId, error, null, null);
    }

    private void sendResult(@Nullable String reqId, @Nullable String error, @Nullable String extraKey, long extraValue) {
        sendResult(reqId, error, extraKey, (Object) extraValue);
    }

    private void sendResult(@Nullable String reqId, @Nullable String error, @Nullable String extraKey, @Nullable Object extraValue) {
        var json = new JsonObject();
        json.addProperty("t", "action_result");
        if (reqId != null) json.addProperty("reqId", reqId);
        json.addProperty("ok", error == null);
        if (error != null) json.addProperty("error", error);
        if (extraKey != null && extraValue instanceof Number n) json.addProperty(extraKey, n);
        if (extraKey != null && extraValue instanceof String str) json.addProperty(extraKey, str);
        send(List.of(Json.GSON.toJson(json)));
    }

    private static String errorJson(String code) {
        var json = new JsonObject();
        json.addProperty("t", "error");
        json.addProperty("error", code);
        return Json.GSON.toJson(json);
    }

    // ---- HubSession --------------------------------------------------------------------------------------------------

    @Override
    public UUID mainframeId() {
        return mainframeId;
    }

    @Override
    public AccessStore.@Nullable Share share() {
        return share;
    }

    @Override
    public Role role() {
        return role;
    }

    @Override
    public void setRole(Role role) {
        if (share == null) this.role = role;
    }

    @Override
    public AuthStore.@Nullable Device device() {
        return device;
    }

    /** Non-blocking; disconnects clients whose outbound buffer is full (slow consumers). */
    @Override
    public void send(List<String> messages) {
        var ch = channel;
        if (ch == null || closed || !ch.isActive()) return;
        if (!ch.isWritable()) {
            var d = device;
            CloudLog.LOGGER.info("[cccloud] Disconnecting slow dashboard client {} ({})", ip, d != null ? d.name() : "?");
            close(1013, "too slow");
            return;
        }
        for (String message : messages) ch.write(new TextWebSocketFrame(message));
        ch.flush();
    }

    @Override
    public void close(int code, String reason) {
        var ch = channel;
        if (ch == null || closed) return;
        closed = true;
        if (ch.isActive()) {
            ch.writeAndFlush(new CloseWebSocketFrame(code, reason)).addListener(ChannelFutureListener.CLOSE);
            ch.eventLoop().schedule(() -> {
                ch.close();
            }, 2, TimeUnit.SECONDS);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        closed = true;
        if (authDeadline != null) authDeadline.cancel(false);
        if (pingTask != null) pingTask.cancel(false);
        if (state == State.READY) WebHub.INSTANCE.removeSession(this);
        super.channelInactive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            close(1001, "idle timeout");
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        CloudLog.LOGGER.debug("[cccloud] WebSocket error from {}", ip, cause);
        ctx.close();
    }
}
