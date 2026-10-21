package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudLog;
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
 * One dashboard WebSocket. The first message must be {@code {"t":"auth","token":"..."}} within 5 seconds. Every other
 * message type is denied until authentication (token + live rights of the owning player) succeeded.
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
                authenticate(auth.token());
            } else if (!ClientMessages.isPublic(message) || state == State.NEW) {
                close(4401, "auth required");
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
        }
    }

    private void authenticate(String token) {
        var candidate = instance.auth.authenticate(token);
        if (candidate == null) {
            CloudLog.LOGGER.info("[cccloud] Rejected WebSocket login with an invalid or expired token from {}", ip);
            send(List.of(errorJson("unauthorized")));
            close(4401, "unauthorized");
            return;
        }
        state = State.AUTHENTICATING;
        try {
            instance.bridge.serverExecutor().execute(() -> {
                String error = instance.bridge.checkRights(candidate);
                var ch = channel;
                if (ch == null) return;
                ch.eventLoop().execute(() -> {
                    if (error != null) {
                        CloudLog.LOGGER.info("[cccloud] Rejected WebSocket login of device '{}' ({}): {}", candidate.name(), ip, error);
                        send(List.of(errorJson(error)));
                        close(4403, error);
                        return;
                    }
                    if (closed) return;
                    device = candidate;
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

    private void action(AuthStore.Device current, ClientMessages.Action action) {
        if (!current.canControl()) {
            sendResult(action.reqId(), "read_only");
            return;
        }
        if (!actionLimit.tryAcquire()) {
            sendResult(action.reqId(), "rate_limited");
            return;
        }
        boolean needsPin = WebHub.INSTANCE.widgetNeedsPin(current.mainframeId(), action.uplinkId().toString(), action.id());
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
                var check = instance.auth.checkPin(current.mainframeId(), current.tokenHash(), action.pin());
                switch (check.result()) {
                    case OK -> runOnServer(current, action, true);
                    case NOT_SET -> sendResult(action.reqId(), "pin_not_set");
                    case WRONG -> sendResult(action.reqId(), "wrong_pin", "attemptsLeft", check.attemptsLeft());
                    case LOCKED -> {
                        sendResult(action.reqId(), "pin_locked", "retryAfter", check.retryAfterSeconds());
                        instance.bridge.serverExecutor().execute(() -> instance.bridge.onPinLockout(current, ip));
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
                    result = instance.bridge.dispatchAction(current, action, pinVerified, ip);
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

    /** Item search. Read-only, so devices without canControl may search too. */
    private void query(AuthStore.Device current, ClientMessages.Query query) {
        if (!queryLimit.tryAcquire()) {
            sendQueryResult(query.reqId(), QueryResult.fail("rate_limited"));
            return;
        }
        if (!"item_search".equals(WebHub.INSTANCE.widgetType(current.mainframeId(), query.uplinkId().toString(), query.id()))) {
            sendQueryResult(query.reqId(), QueryResult.fail("unknown_widget"));
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                QueryResult result;
                try {
                    result = instance.bridge.dispatchQuery(current, query);
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
     * Saves the shared dashboard layout (validated during parsing). Needs canControl; at most 5 saves per 10 s per
     * mainframe. Answered with layout_result; on success every client of the mainframe gets {"t":"layout"}.
     */
    private void layout(AuthStore.Device current, ClientMessages.LayoutSet layout) {
        if (!current.canControl()) {
            sendLayoutResult(layout.reqId(), "read_only");
            return;
        }
        if (!WebHub.INSTANCE.tryLayoutSave(current.mainframeId())) {
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
                    error = instance.bridge.setLayout(current, layout.layout());
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
        var d = device;
        return d == null ? new UUID(0, 0) : d.mainframeId();
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
