package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker13;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The web layer's guard in front of every card action (v0.5 MFFS field toggle, ME Requester amounts, ...): read-only
 * devices get {@code read_only} and PIN widgets without a PIN get {@code pin_required}; neither reaches the game.
 */
class ActionGuardTest {
    private final UUID mainframe = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final String section = UUID.randomUUID().toString();

    private final TestBridge bridge = new TestBridge();
    private final List<String> dispatched = bridge.dispatched;

    @AfterEach
    void cleanup() {
        WebHub.INSTANCE.removeMainframe(mainframe);
    }

    private String token(AuthStore store, String name, boolean canControl) {
        var code = store.currentCode(mainframe, owner);
        var pending = store.pair(AuthStore.formatCode(code.code()), name, "127.0.0.1");
        return store.approve(pending.id(), owner, "Owner", canControl).token();
    }

    private void publishCard() {
        var hub = WebHub.INSTANCE;
        hub.publishMainframe(mainframe, "MF", true, true);
        var json = new JsonObject();
        json.addProperty("uplinkId", section);
        json.addProperty("title", "Force Field Projector");
        json.addProperty("online", true);
        var widgets = new JsonArray();
        var field = new JsonObject();
        field.addProperty("id", "field");
        field.addProperty("type", "toggle");
        var opts = new JsonObject();
        opts.addProperty("pin", true);
        opts.addProperty("confirm", true);
        field.add("opts", opts);
        widgets.add(field);
        var mode = new JsonObject();
        mode.addProperty("id", "mode");
        mode.addProperty("type", "select");
        mode.add("opts", new JsonObject());
        widgets.add(mode);
        json.add("widgets", widgets);
        hub.publishUplink(mainframe, json);
    }

    private static @Nullable String result(EmbeddedChannel channel, String reqId) {
        Object out;
        while ((out = channel.readOutbound()) != null) {
            if (!(out instanceof TextWebSocketFrame frame)) continue;
            var json = JsonParser.parseString(frame.text()).getAsJsonObject();
            if ("action_result".equals(json.get("t").getAsString()) && reqId.equals(json.get("reqId").getAsString())) {
                return json.has("error") && !json.get("error").isJsonNull() ? json.get("error").getAsString() : "ok";
            }
        }
        return null;
    }

    private EmbeddedChannel session(WebInstance instance, String token) {
        var handshaker = new WebSocketServerHandshaker13("ws://localhost/ws/client", null, WebSocketDecoderConfig.newBuilder().build());
        var channel = new EmbeddedChannel(new WebSocketSession(instance, handshaker, "127.0.0.1", null));
        channel.writeInbound(new TextWebSocketFrame("{\"t\":\"auth\",\"token\":\"" + token + "\"}"));
        channel.runPendingTasks();
        return channel;
    }

    private static TextWebSocketFrame action(String reqId, String id, String value) {
        return new TextWebSocketFrame("{\"t\":\"action\",\"reqId\":\"" + reqId + "\",\"uplinkId\":\"" + "%s" + "\",\"id\":\"" + id + "\",\"value\":" + value + "}");
    }

    @Test
    void readOnlyDevicesAndMissingPinsNeverReachTheGame() {
        var store = new AuthStore(System::currentTimeMillis, () -> {
        });
        var instance = new WebInstance(bridge, store, null, List.of(), true, "http://localhost:8787", 8787);
        publishCard();

        var viewer = session(instance, token(store, "Viewer", false));
        viewer.writeInbound(new TextWebSocketFrame(action("r1", "field", "true").text().formatted(section)));
        viewer.runPendingTasks();
        assertEquals("read_only", result(viewer, "r1"));
        viewer.writeInbound(new TextWebSocketFrame(action("r2", "mode", "\"Consume\"").text().formatted(section)));
        viewer.runPendingTasks();
        assertEquals("read_only", result(viewer, "r2"), "read-only for every card widget");

        var control = session(instance, token(store, "Control", true));
        control.writeInbound(new TextWebSocketFrame(action("c1", "field", "true").text().formatted(section)));
        control.runPendingTasks();
        assertEquals("pin_required", result(control, "c1"), "PIN widget without a PIN");
        control.writeInbound(new TextWebSocketFrame(action("c2", "mode", "\"Consume\"").text().formatted(section)));
        control.runPendingTasks();
        assertEquals("ok", result(control, "c2"), "a canControl device reaches the game for normal widgets");

        assertEquals(List.of("action:mode"), dispatched, "only the allowed action was dispatched");
        instance.pinExecutor.shutdownNow();
    }
}
