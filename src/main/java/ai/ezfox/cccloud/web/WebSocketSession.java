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
    private @Nullable Channel channel;
    private volatile State state = State.NEW;
    private volatile AuthStore.@Nullable Device device;
    private volatile boolean closed;
    private int invalidMessages;
    private @Nullable ScheduledFuture<?> authDeadline;
    private @Nullable ScheduledFuture<?> pingTask;

    WebSocketSession(WebInstance instance, WebSocketServerHandshaker handshaker, String ip) {
        this.instance = instance;
        this.handshaker = handshaker;
        this.ip = ip;
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
                if (invalid.reqId() != null) sendResult(invalid.reqId(), invalid.reason());
                else send(List.of(errorJson(invalid.reason())));
            }
            case ClientMessages.Action action -> action(current, action);
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
            instance.bridge.serverExecutor().execute(() -> sendResult(action.reqId(), instance.bridge.dispatchAction(current, action, pinVerified, ip)));
        } catch (RejectedExecutionException e) {
            sendResult(action.reqId(), "offline");
        }
    }

    private void sendResult(@Nullable String reqId, @Nullable String error) {
        sendResult(reqId, error, null, 0);
    }

    private void sendResult(@Nullable String reqId, @Nullable String error, @Nullable String extraKey, long extraValue) {
        var json = new JsonObject();
        json.addProperty("t", "action_result");
        if (reqId != null) json.addProperty("reqId", reqId);
        json.addProperty("ok", error == null);
        if (error != null) json.addProperty("error", error);
        if (extraKey != null) json.addProperty(extraKey, extraValue);
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
