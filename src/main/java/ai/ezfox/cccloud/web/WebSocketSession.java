package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.Perm;
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
    /** Actions per second: 10, from an internet address in plain-HTTP mode 5 ({@link ai.ezfox.cccloud.web.internet.InternetGuard}). */
    private final TokenBucket actionLimit;
    /** PIN unlocks (plain-HTTP internet access): one per second. */
    private final TokenBucket unlockLimit = new TokenBucket(1, 1);
    /** What this session was last told by {@code {"t":"internet"}} ("" = nothing yet). */
    private volatile String internetState = "";
    /** Item searches: 5 per second (docs/SPEC.md). */
    private final TokenBucket queryLimit = new TokenBucket(5, 5);
    /** Terminal input: 20 messages per second (each up to 32 events). */
    private final TokenBucket terminalLimit = new TokenBucket(20, 20);
    /** This session as a viewer of browser terminals (v0.5 E). */
    private final ai.ezfox.cccloud.terminal.TerminalService.Viewer terminalViewer = new ai.ezfox.cccloud.terminal.TerminalService.Viewer() {
        @Override
        public void send(List<String> messages) {
            WebSocketSession.this.send(messages);
        }

        @Override
        public boolean allowed() {
            var d = device;
            return !closed && share == null && d != null && instance.auth.isValid(d.tokenHash())
                && AccessGate.check(role, perms, AccessGate.Kind.TERMINAL) == null;
        }
    };
    private @Nullable Channel channel;
    private volatile State state = State.NEW;
    private volatile AuthStore.@Nullable Device device;
    private volatile AccessStore.@Nullable Share share;
    private volatile Role role = Role.NONE;
    private volatile java.util.Set<ai.ezfox.cccloud.access.Perm> perms = java.util.Set.of();
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
        int actions = instance.internet.actionsPerSecond(ip);
        this.actionLimit = new TokenBucket(actions, actions);
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
        // Before authentication a message is at most 2 KB. With permessage-deflate the raw-frame guard only sees the
        // compressed size (2 KB can inflate to the 257 KB frame limit), so the inflated message is checked here too.
        if (state != State.READY && text.length() > FrameSizeGuard.PRE_AUTH_LIMIT) {
            close(1009, "message too big");
            return;
        }
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
        // Plain-HTTP internet access with internetRequirePin: writes from an internet address need the PIN first (actions
        // and opening a terminal carry the PIN themselves and are checked in their handlers).
        if (instance.internet.pinRequired(ip) && ClientMessages.isWrite(message) && !(message instanceof ClientMessages.Action)
            && !(message instanceof ClientMessages.Terminal t && t.op().equals("term_open"))) {
            String locked = remoteLock(current, mainframeId);
            if (locked != null) {
                String deny = AccessGate.deny(text, locked);
                if (deny != null) send(List.of(deny));
                return;
            }
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
            case ClientMessages.RedstoneOverride override -> redstoneOverride(current, override);
            case ClientMessages.RedstoneDevice device -> redstoneDevice(current, device);
            case ClientMessages.CardSettings settings -> cardSettings(current, settings);
            case ClientMessages.RuleCommand command -> rule(current, command);
            case ClientMessages.SceneCommand command -> scene(current, command);
            case ClientMessages.Production request -> production(current, request);
            case ClientMessages.Storage storage -> storage(current, storage);
            case ClientMessages.CraftPreview preview -> craftPreview(current, preview);
            case ClientMessages.Terminal terminal -> terminal(current, terminal);
            case ClientMessages.Unlock unlock -> unlock(current, unlock);
        }
    }

    /**
     * Plain-HTTP internet access: tells an internet session whether its mainframe can be controlled from outside
     * ({@code {"t":"internet","remote":true,"pin":false}} = read-only, the mainframe has no PIN). Sent after the login
     * and again when it changes (the 5-second re-check), e.g. when the owner sets a PIN.
     */
    @Override
    public void recheckInternet() {
        if (state != State.READY || share != null) return;
        boolean remote = instance.internet.pinRequired(ip);
        boolean pin = instance.auth.hasPin(mainframeId);
        String now = remote ? (pin ? "pin" : "no_pin") : "lan";
        if (now.equals(internetState)) return;
        boolean first = internetState.isEmpty();
        internetState = now;
        if (first && !remote) return; // the LAN never needs the message
        var json = new JsonObject();
        json.addProperty("t", "internet");
        json.addProperty("remote", remote);
        json.addProperty("pin", pin);
        send(List.of(Json.GSON.toJson(json)));
    }

    /**
     * Plain-HTTP internet access: why a write from this (internet) session is refused, or null if it may go ahead.
     * {@code remote_no_pin}: the mainframe has no PIN, so nothing can be controlled from the internet;
     * {@code remote_locked}: enter the PIN first ({@code unlock}, or with the action itself).
     */
    private @Nullable String remoteLock(AuthStore.Device current, UUID mainframe) {
        if (!instance.internet.pinRequired(ip)) return null;
        if (!instance.auth.hasPin(mainframe)) return "remote_no_pin";
        return instance.internet.unlocked(current.tokenHash(), mainframe, ip) ? null : "remote_locked";
    }

    /** A correct PIN from an internet address in plain-HTTP mode: writes are unlocked for a while (logged and audited). */
    private long remoteUnlocked(AuthStore.Device current, UUID mainframe) {
        if (!instance.internet.pinRequired(ip)) return 0;
        boolean was = instance.internet.unlocked(current.tokenHash(), mainframe, ip);
        long seconds = instance.internet.unlock(current.tokenHash(), mainframe, ip);
        if (!was) {
            String text = "Remote control unlocked with the PIN from " + ip + " (plain HTTP) by device '" + current.name() + "' (" + current.playerName() + ")";
            instance.access.audit(mainframe, text);
            CloudLog.LOGGER.info("[cccloud] {} on {}", text, mainframe);
        }
        return seconds;
    }

    /**
     * {@code unlock {reqId, pin}} → {@code unlock_result {ok, seconds}}: plain-HTTP internet access needs the mainframe
     * PIN before writes from an internet address. Same lockouts as every PIN; wrong PINs also count against the address.
     */
    private void unlock(AuthStore.Device current, ClientMessages.Unlock unlock) {
        UUID mainframe = mainframeId;
        if (!instance.internet.pinRequired(ip)) {
            sendUnlockResult(unlock.reqId(), null, "seconds", 0);
            return;
        }
        if (!instance.auth.hasPin(mainframe)) {
            sendUnlockResult(unlock.reqId(), "remote_no_pin", null, 0);
            return;
        }
        if (instance.internet.blocked(ip) || !unlockLimit.tryAcquire()) {
            sendUnlockResult(unlock.reqId(), "rate_limited", null, 0);
            return;
        }
        long lockedFor = instance.auth.pinLockedFor(current.tokenHash());
        if (lockedFor > 0) {
            sendUnlockResult(unlock.reqId(), "pin_locked", "retryAfter", lockedFor);
            return;
        }
        try {
            instance.pinExecutor.execute(() -> {
                var check = instance.auth.checkPin(mainframe, current.tokenHash(), unlock.pin());
                switch (check.result()) {
                    case OK -> sendUnlockResult(unlock.reqId(), null, "seconds", remoteUnlocked(current, mainframe));
                    case NOT_SET -> sendUnlockResult(unlock.reqId(), "remote_no_pin", null, 0);
                    case WRONG -> {
                        instance.internet.failure(ip);
                        sendUnlockResult(unlock.reqId(), "wrong_pin", "attemptsLeft", check.attemptsLeft());
                    }
                    case LOCKED -> {
                        sendUnlockResult(unlock.reqId(), "pin_locked", "retryAfter", check.retryAfterSeconds());
                        instance.bridge.serverExecutor().execute(() -> instance.bridge.onPinLockout(current, mainframe, ip));
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            sendUnlockResult(unlock.reqId(), "busy", null, 0);
        }
    }

    private void sendUnlockResult(String reqId, @Nullable String error, @Nullable String extraKey, long extraValue) {
        var json = new JsonObject();
        json.addProperty("t", "unlock_result");
        json.addProperty("reqId", reqId);
        json.addProperty("ok", error == null);
        if (error != null) json.addProperty("error", error);
        if (extraKey != null) json.addProperty(extraKey, extraValue);
        send(List.of(Json.GSON.toJson(json)));
    }

    private void authenticate(String token, @Nullable UUID requested) {
        if (instance.internet.blocked(ip)) {
            deny("rate_limited", 4429);
            return;
        }
        var candidate = instance.auth.authenticate(token);
        if (candidate == null) {
            instance.internet.failure(ip);
            CloudLog.LOGGER.info("[cccloud] Rejected WebSocket login with an invalid or expired token from {}", ip);
            deny("unauthorized", 4401);
            return;
        }
        if (instance.internet.tooIdle(candidate, ip)) {
            instance.auth.expire(candidate.tokenHash());
            CloudLog.LOGGER.info("[cccloud] Device '{}' of {} logged out: used from internet address {} over plain HTTP after more than {} h without use",
                candidate.name(), candidate.playerName(), ip, instance.internet.settings().publicTokenIdleMs() / 3_600_000L);
            deny("token_expired", 4401);
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
                ai.ezfox.cccloud.access.AccessResolver.Resolution resolved;
                try {
                    resolved = instance.bridge.authorize(candidate, target, ip);
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while authorizing a dashboard login from {}", ip, e);
                    resolved = ai.ezfox.cccloud.access.AccessResolver.Resolution.NONE;
                }
                var ch = channel;
                if (ch == null) return;
                var granted = resolved;
                ch.eventLoop().execute(() -> {
                    if (granted.role() == Role.NONE) {
                        CloudLog.LOGGER.info("[cccloud] Rejected WebSocket login of device '{}' ({}) to {}: no role", candidate.name(), ip, target);
                        deny("no_access", 4403);
                        return;
                    }
                    if (closed) return;
                    device = candidate;
                    mainframeId = target;
                    role = granted.role();
                    perms = granted.perms();
                    state = State.READY;
                    // Only authenticated sessions may send big frames (layout_set, up to 256 KB).
                    if (sizeGuard != null) sizeGuard.raiseLimit(ClientMessages.MAX_LAYOUT_FRAME);
                    if (authDeadline != null) authDeadline.cancel(false);
                    WebHub.INSTANCE.addSession(this);
                    recheckInternet();
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
                instance.internet.failure(ip);
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
        if (!instance.bridge.shareCreatorAllowed(granted)) {
            deny("share_revoked", 4401);
            return;
        }
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
        String denied = AccessGate.check(role, perms, WebHub.INSTANCE.actionPerm(mainframeId, action.uplinkId().toString(), action.id()));
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
        // internetRequirePin: every action from an internet address needs the PIN (once per unlock period; a PIN sent with
        // the action unlocks too). A mainframe without a PIN cannot be controlled from the internet then.
        boolean remote = instance.internet.pinRequired(ip);
        if (remote && !instance.auth.hasPin(mainframe)) {
            sendResult(action.reqId(), "remote_no_pin");
            return;
        }
        boolean remoteLocked = remote && !instance.internet.unlocked(current.tokenHash(), mainframe, ip);
        if (!needsPin && !remoteLocked) {
            runOnServer(current, action, false);
            return;
        }
        if (remote && instance.internet.blocked(ip)) {
            sendResult(action.reqId(), "rate_limited");
            return;
        }
        long lockedFor = instance.auth.pinLockedFor(current.tokenHash());
        if (lockedFor > 0) {
            sendResult(action.reqId(), "pin_locked", "retryAfter", lockedFor);
            return;
        }
        if (action.pin() == null) {
            sendResult(action.reqId(), needsPin ? "pin_required" : "remote_locked");
            return;
        }
        try {
            instance.pinExecutor.execute(() -> {
                var check = instance.auth.checkPin(mainframe, current.tokenHash(), action.pin());
                switch (check.result()) {
                    case OK -> {
                        remoteUnlocked(current, mainframe);
                        runOnServer(current, action, true);
                    }
                    case NOT_SET -> sendResult(action.reqId(), remote ? "remote_no_pin" : "pin_not_set");
                    case WRONG -> {
                        instance.internet.failure(ip);
                        sendResult(action.reqId(), "wrong_pin", "attemptsLeft", check.attemptsLeft());
                    }
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
        String denied = AccessGate.check(role, perms, AccessGate.Kind.QUERY);
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
        String denied = AccessGate.check(role, perms, AccessGate.Kind.LAYOUT);
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
        String denied = AccessGate.check(role, perms, AccessGate.Kind.NET_UNLINK);
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

    /** v0.5 rules: edit (admin) or operate (operator); answered with rule_result. */
    private void rule(AuthStore.Device current, ClientMessages.RuleCommand command) {
        String denied = AccessGate.check(role, perms, command.edits() ? AccessGate.Kind.RULE_EDIT : AccessGate.Kind.RULE_OPERATE);
        if (denied != null) {
            sendRuleResult("rule_result", command.reqId(), new GameBridge.RuleResult(denied, null, null));
            return;
        }
        if (!actionLimit.tryAcquire()) {
            sendRuleResult("rule_result", command.reqId(), new GameBridge.RuleResult("rate_limited", null, null));
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                GameBridge.RuleResult result;
                try {
                    result = instance.bridge.ruleCommand(current, mainframeId, command);
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while handling a rule command from {}", ip, e);
                    result = new GameBridge.RuleResult("internal_error", null, null);
                }
                sendRuleResult("rule_result", command.reqId(), result);
            });
        } catch (RejectedExecutionException e) {
            sendRuleResult("rule_result", command.reqId(), new GameBridge.RuleResult("offline", null, null));
        }
    }

    /** v0.10 scenes: save or delete ({@code rules.edit}); answered with scene_result. Running is a button action. */
    private void scene(AuthStore.Device current, ClientMessages.SceneCommand command) {
        String denied = AccessGate.check(role, perms, AccessGate.Kind.SCENE_EDIT);
        if (denied != null) {
            sendRuleResult("scene_result", command.reqId(), new GameBridge.RuleResult(denied, null, null));
            return;
        }
        if (!actionLimit.tryAcquire()) {
            sendRuleResult("scene_result", command.reqId(), new GameBridge.RuleResult("rate_limited", null, null));
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                GameBridge.RuleResult result;
                try {
                    result = instance.bridge.sceneCommand(current, mainframeId, command);
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while handling a scene command from {}", ip, e);
                    result = new GameBridge.RuleResult("internal_error", null, null);
                }
                sendRuleResult("scene_result", command.reqId(), result);
            });
        } catch (RejectedExecutionException e) {
            sendRuleResult("scene_result", command.reqId(), new GameBridge.RuleResult("offline", null, null));
        }
    }

    /** Production statistics requests: 5 per second per socket. */
    private final TokenBucket productionLimit = new TokenBucket(5, 5);

    /** v0.10 production statistics: overview, item details, pins; answered with production_result. */
    private void production(AuthStore.Device current, ClientMessages.Production request) {
        String denied = AccessGate.check(role, perms, AccessGate.productionPerm(request.op()));
        if (denied != null) {
            sendStorage("production_result", "data", request.reqId(), GameBridge.StorageResult.fail(denied));
            return;
        }
        if (!productionLimit.tryAcquire()) {
            sendStorage("production_result", "data", request.reqId(), GameBridge.StorageResult.fail("rate_limited"));
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                GameBridge.StorageResult result;
                try {
                    result = instance.bridge.production(current, mainframeId, request);
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while handling a production request from {}", ip, e);
                    result = GameBridge.StorageResult.fail("internal_error");
                }
                sendStorage("production_result", "data", request.reqId(), result);
            });
        } catch (RejectedExecutionException e) {
            sendStorage("production_result", "data", request.reqId(), GameBridge.StorageResult.fail("offline"));
        }
    }

    /** Storage requests: 10 per second per socket (browse pages, details, CPU lists). */
    private final TokenBucket storageLimit = new TokenBucket(10, 10);

    private void storage(AuthStore.Device current, ClientMessages.Storage request) {
        String denied = AccessGate.check(role, perms, AccessGate.storagePerm(request.op()));
        if (denied != null) {
            sendStorage("storage_result", "data", request.reqId(), GameBridge.StorageResult.fail(denied));
            return;
        }
        if (!storageLimit.tryAcquire()) {
            sendStorage("storage_result", "data", request.reqId(), GameBridge.StorageResult.fail("rate_limited"));
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                GameBridge.StorageResult result;
                try {
                    result = instance.bridge.storage(current, mainframeId, request);
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while handling a storage request from {}", ip, e);
                    result = GameBridge.StorageResult.fail("internal_error");
                }
                sendStorage("storage_result", "data", request.reqId(), result);
            });
        } catch (RejectedExecutionException e) {
            sendStorage("storage_result", "data", request.reqId(), GameBridge.StorageResult.fail("offline"));
        }
    }

    private void craftPreview(AuthStore.Device current, ClientMessages.CraftPreview request) {
        String denied = AccessGate.check(role, perms, Perm.STORAGE_CRAFT);
        if (denied != null) {
            sendStorage("craft_plan", "plan", request.reqId(), GameBridge.StorageResult.fail(denied));
            return;
        }
        if (!storageLimit.tryAcquire()) {
            sendStorage("craft_plan", "plan", request.reqId(), GameBridge.StorageResult.fail("rate_limited"));
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                try {
                    instance.bridge.craftPreview(current, mainframeId, request, result -> sendStorage("craft_plan", "plan", request.reqId(), result));
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while planning a craft preview from {}", ip, e);
                    sendStorage("craft_plan", "plan", request.reqId(), GameBridge.StorageResult.fail("internal_error"));
                }
            });
        } catch (RejectedExecutionException e) {
            sendStorage("craft_plan", "plan", request.reqId(), GameBridge.StorageResult.fail("offline"));
        }
    }

    private void sendStorage(String type, String field, String reqId, GameBridge.StorageResult result) {
        var json = new JsonObject();
        json.addProperty("t", type);
        json.addProperty("reqId", reqId);
        json.addProperty("ok", result.error() == null);
        if (result.error() != null) json.addProperty("error", result.error());
        if (result.data() != null) json.add(field, result.data());
        send(List.of(Json.GSON.toJson(json)));
    }

    private void sendRuleResult(String type, String reqId, GameBridge.RuleResult result) {
        var json = new JsonObject();
        json.addProperty("t", type);
        json.addProperty("reqId", reqId);
        json.addProperty("ok", result.error() == null);
        if (result.error() != null) json.addProperty("error", result.error());
        if (result.detail() != null) json.addProperty("detail", result.detail());
        if (result.id() != null) json.addProperty("id", result.id());
        send(List.of(Json.GSON.toJson(json)));
    }

    /** Wireless redstone override (act.redstone): {@code rs_result}, then a {@code redstone} delta on success. */
    private void redstoneOverride(AuthStore.Device current, ClientMessages.RedstoneOverride override) {
        String denied = AccessGate.check(role, perms, AccessGate.Kind.REDSTONE);
        if (denied != null) {
            sendTypedResult("rs_result", override.reqId(), denied);
            return;
        }
        if (!actionLimit.tryAcquire()) {
            sendTypedResult("rs_result", override.reqId(), "rate_limited");
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                String error;
                try {
                    error = instance.bridge.redstoneOverride(current, mainframeId, override);
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while overriding a redstone channel from {}", ip, e);
                    error = "internal_error";
                }
                sendTypedResult("rs_result", override.reqId(), error);
            });
        } catch (RejectedExecutionException e) {
            sendTypedResult("rs_result", override.reqId(), "offline");
        }
    }

    /** Cloud Redstone device rename / regroup (network.link): {@code rs_result}; the cards and the map follow. */
    private void redstoneDevice(AuthStore.Device current, ClientMessages.RedstoneDevice message) {
        String denied = AccessGate.check(role, perms, AccessGate.Kind.RS_DEVICE);
        if (denied != null) {
            sendTypedResult("rs_result", message.reqId(), denied);
            return;
        }
        if (!actionLimit.tryAcquire()) {
            sendTypedResult("rs_result", message.reqId(), "rate_limited");
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                String error;
                try {
                    error = instance.bridge.redstoneDevice(current, mainframeId, message);
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while renaming a redstone device from {}", ip, e);
                    error = "internal_error";
                }
                sendTypedResult("rs_result", message.reqId(), error);
            });
        } catch (RejectedExecutionException e) {
            sendTypedResult("rs_result", message.reqId(), "offline");
        }
    }

    /** Wave 4: a device card's settings (the Peripheral card's methods); layout.edit, rate limited like actions. */
    private void cardSettings(AuthStore.Device current, ClientMessages.CardSettings message) {
        String denied = AccessGate.check(role, perms, AccessGate.Kind.CARD_SETTINGS);
        if (denied != null) {
            sendTypedResult("card_settings_result", message.reqId(), denied);
            return;
        }
        if (!actionLimit.tryAcquire()) {
            sendTypedResult("card_settings_result", message.reqId(), "rate_limited");
            return;
        }
        try {
            instance.bridge.serverExecutor().execute(() -> {
                String error;
                try {
                    error = instance.bridge.cardSettings(current, mainframeId, message);
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error while changing card settings from {}", ip, e);
                    error = "internal_error";
                }
                sendTypedResult("card_settings_result", message.reqId(), error);
            });
        } catch (RejectedExecutionException e) {
            sendTypedResult("card_settings_result", message.reqId(), "offline");
        }
    }

    private void sendTypedResult(String type, String reqId, @Nullable String error) {
        var json = new JsonObject();
        json.addProperty("t", type);
        json.addProperty("reqId", reqId);
        json.addProperty("ok", error == null);
        if (error != null) json.addProperty("error", error);
        send(List.of(Json.GSON.toJson(json)));
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

    /**
     * v0.5 E browser terminal: open (permission {@code terminal}, the mainframe PIN when one is set), input (rate
     * limited, validated against the terminal), close. Results as {@code term_result}; frames come from the
     * TerminalService as {@code term} / {@code term_closed}.
     */
    private void terminal(AuthStore.Device current, ClientMessages.Terminal terminal) {
        String denied = AccessGate.check(role, perms, AccessGate.Kind.TERMINAL);
        if (denied != null) {
            if (terminal.reqId() != null) sendTermResult(terminal.reqId(), denied, null, null);
            return;
        }
        UUID mainframe = mainframeId;
        switch (terminal.op()) {
            case "term_close" -> onServer(terminal.reqId(), () -> {
                instance.bridge.terminalClose(mainframe, terminal.uplinkId(), terminalViewer);
                return null;
            });
            case "term_input" -> {
                if (!terminalLimit.tryAcquire()) {
                    if (terminal.reqId() != null) sendTermResult(terminal.reqId(), "rate_limited", null, null);
                    return;
                }
                onServer(terminal.reqId(), () -> instance.bridge.terminalInput(current, mainframe, terminal.uplinkId(), terminalViewer, terminal.events()));
            }
            default -> {
                if (!actionLimit.tryAcquire()) {
                    sendTermResult(terminal.reqId(), "rate_limited", null, null);
                    return;
                }
                boolean remote = instance.internet.pinRequired(ip);
                if (!instance.auth.hasPin(mainframe)) {
                    // plain-HTTP internet access: no terminal from the internet without a PIN
                    if (remote) sendTermResult(terminal.reqId(), "remote_no_pin", null, null);
                    else onServer(terminal.reqId(), () -> instance.bridge.terminalOpen(current, mainframe, terminal.uplinkId(), terminalViewer));
                    return;
                }
                if (remote && instance.internet.blocked(ip)) {
                    sendTermResult(terminal.reqId(), "rate_limited", null, null);
                    return;
                }
                long lockedFor = instance.auth.pinLockedFor(current.tokenHash());
                if (lockedFor > 0) {
                    sendTermResult(terminal.reqId(), "pin_locked", "retryAfter", lockedFor);
                    return;
                }
                if (terminal.pin() == null) {
                    sendTermResult(terminal.reqId(), "pin_required", null, null);
                    return;
                }
                try {
                    instance.pinExecutor.execute(() -> {
                        var check = instance.auth.checkPin(mainframe, current.tokenHash(), terminal.pin());
                        switch (check.result()) {
                            case OK -> {
                                remoteUnlocked(current, mainframe);
                                onServer(terminal.reqId(), () -> instance.bridge.terminalOpen(current, mainframe, terminal.uplinkId(), terminalViewer));
                            }
                            case NOT_SET -> {
                                if (remote) sendTermResult(terminal.reqId(), "remote_no_pin", null, null);
                                else onServer(terminal.reqId(), () -> instance.bridge.terminalOpen(current, mainframe, terminal.uplinkId(), terminalViewer));
                            }
                            case WRONG -> {
                                instance.internet.failure(ip);
                                sendTermResult(terminal.reqId(), "wrong_pin", "attemptsLeft", check.attemptsLeft());
                            }
                            case LOCKED -> {
                                sendTermResult(terminal.reqId(), "pin_locked", "retryAfter", check.retryAfterSeconds());
                                instance.bridge.serverExecutor().execute(() -> instance.bridge.onPinLockout(current, mainframe, ip));
                            }
                        }
                    });
                } catch (RejectedExecutionException e) {
                    sendTermResult(terminal.reqId(), "busy", null, null);
                }
            }
        }
    }

    /** Runs a terminal operation on the server thread and answers with {@code term_result} when there is a reqId. */
    private void onServer(@Nullable String reqId, java.util.function.Supplier<@Nullable String> operation) {
        try {
            instance.bridge.serverExecutor().execute(() -> {
                String error;
                try {
                    error = operation.get();
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.error("[cccloud] Error in a browser terminal of {}", ip, e);
                    error = "internal_error";
                }
                if (reqId != null) sendTermResult(reqId, error, null, null);
            });
        } catch (RejectedExecutionException e) {
            if (reqId != null) sendTermResult(reqId, "offline", null, null);
        }
    }

    private void sendTermResult(@Nullable String reqId, @Nullable String error, @Nullable String extraKey, @Nullable Number extraValue) {
        var json = new JsonObject();
        json.addProperty("t", "term_result");
        if (reqId != null) json.addProperty("reqId", reqId);
        json.addProperty("ok", error == null);
        if (error != null) json.addProperty("error", error);
        if (extraKey != null && extraValue != null) json.addProperty(extraKey, extraValue);
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
    public java.util.Set<ai.ezfox.cccloud.access.Perm> perms() {
        return perms;
    }

    @Override
    public void setAccess(Role role, java.util.Set<ai.ezfox.cccloud.access.Perm> perms) {
        if (share != null) return;
        this.role = role;
        this.perms = perms;
    }

    @Override
    public AuthStore.@Nullable Device device() {
        return device;
    }

    /**
     * A browser may fall behind by this many bytes (beyond the 1 MB write buffer) or for this long before it is
     * disconnected as too slow: a big base's snapshot is over 1 MB, and the deltas right after it must not cost the
     * connection (docs/SPEC.md "Scaling"). Memory per connection stays bounded.
     */
    static final long MAX_BACKLOG_BYTES = 4L * 1024 * 1024;
    static final long MAX_BACKLOG_NANOS = 10_000_000_000L;
    private volatile long slowSince;

    /** Non-blocking; disconnects clients whose outbound backlog stays too big for too long (slow consumers). */
    @Override
    public void send(List<String> messages) {
        var ch = channel;
        if (ch == null || closed || !ch.isActive()) return;
        if (!ch.isWritable()) {
            long now = System.nanoTime();
            if (slowSince == 0) slowSince = now;
            if (ch.bytesBeforeWritable() > MAX_BACKLOG_BYTES || now - slowSince > MAX_BACKLOG_NANOS) {
                var d = device;
                CloudLog.LOGGER.info("[cccloud] Disconnecting slow dashboard client {} ({})", ip, d != null ? d.name() : "?");
                close(1013, "too slow");
                return;
            }
        } else {
            slowSince = 0;
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
        if (state == State.READY) {
            WebHub.INSTANCE.removeSession(this);
            try {
                UUID mf = mainframeId;
                instance.bridge.serverExecutor().execute(() -> instance.bridge.terminalClose(mf, null, terminalViewer));
            } catch (RejectedExecutionException ignored) {
                // server stopping
            }
        }
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
