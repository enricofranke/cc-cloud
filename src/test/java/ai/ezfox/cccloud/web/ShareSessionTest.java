package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.Role;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker13;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Share-link sessions over a real {@link WebSocketSession}: login errors, the viewer limit, the filtered snapshot and
 * the hard deny on every client message type (nothing reaches the game).
 */
class ShareSessionTest {
    private final UUID mainframe = UUID.randomUUID();
    private final String uplink = UUID.randomUUID().toString();
    private final TestBridge bridge = new TestBridge();
    private final AuthStore auth = new AuthStore(System::currentTimeMillis, () -> {
    });
    private final AccessStore access = new AccessStore(System::currentTimeMillis, () -> {
    });
    private final WebInstance instance = new WebInstance(bridge, auth, access, null, List.of(), true, "http://localhost", 8787);

    @AfterEach
    void cleanup() {
        WebHub.INSTANCE.removeMainframe(mainframe);
        instance.pinExecutor.shutdownNow();
    }

    private void publish() {
        WebHub.INSTANCE.publishMainframe(mainframe, "Base", true, true);
        var json = new JsonObject();
        json.addProperty("uplinkId", uplink);
        json.addProperty("title", "Reactor");
        json.addProperty("online", true);
        json.addProperty("mode", "lua");
        var widgets = new JsonArray();
        widgets.add(widget("temp", "gauge"));
        widgets.add(widget("search", "item_search"));
        widgets.add(widget("note", "label"));
        json.add("widgets", widgets);
        WebHub.INSTANCE.publishUplink(mainframe, json);
    }

    private static JsonObject widget(String id, String type) {
        var w = new JsonObject();
        w.addProperty("id", id);
        w.addProperty("type", type);
        return w;
    }

    private EmbeddedChannel connect(String first) {
        var handshaker = new WebSocketServerHandshaker13("ws://localhost/ws/client", null, WebSocketDecoderConfig.newBuilder().build());
        var channel = new EmbeddedChannel(new WebSocketSession(instance, handshaker, "127.0.0.1", null));
        channel.writeInbound(new TextWebSocketFrame(first));
        channel.runPendingTasks();
        return channel;
    }

    private static String authShare(String id, String secret) {
        return "{\"t\":\"auth_share\",\"id\":\"" + id + "\",\"secret\":\"" + secret + "\"}";
    }

    /** Text frames and the close code (or -1) the session sent so far. */
    private static List<JsonObject> drain(EmbeddedChannel channel, int[] close) {
        var list = new ArrayList<JsonObject>();
        Object out;
        while ((out = channel.readOutbound()) != null) {
            if (out instanceof TextWebSocketFrame text) list.add(JsonParser.parseString(text.text()).getAsJsonObject());
            if (out instanceof CloseWebSocketFrame c && close != null) close[0] = c.statusCode();
        }
        return list;
    }

    @Test
    void loginErrors() {
        publish();
        var share = access.createShare(mainframe, "Base", "Alex", 3600L, 1, List.of(), List.of(uplink), false);
        int[] close = {-1};
        var wrong = drain(connect(authShare(share.share().id(), "wrong-secret")), close);
        assertEquals("share_invalid", wrong.getFirst().get("error").getAsString());
        assertEquals(4401, close[0]);

        var ok = connect(authShare(share.share().id(), share.secret()));
        var snapshot = drain(ok, null).getFirst();
        assertEquals("share", snapshot.get("role").getAsString());

        close[0] = -1;
        var full = drain(connect(authShare(share.share().id(), share.secret())), close);
        assertEquals("share_full", full.getFirst().get("error").getAsString(), "maxViewers = 1");
        assertEquals(4429, close[0]);

        access.revokeShare(mainframe, share.share().id());
        WebHub.INSTANCE.closeShare(share.share().id(), "share_revoked");
        close[0] = -1;
        var revoked = drain(ok, close);
        assertEquals("share_revoked", revoked.getFirst().get("error").getAsString());
        assertEquals(4401, close[0]);
    }

    @Test
    void snapshotIsFiltered() {
        publish();
        var share = access.createShare(mainframe, "Base", "Alex", null, 5, List.of("main"), List.of(), true);
        var snapshot = drain(connect(authShare(share.share().id(), share.secret())), null).getFirst();
        assertEquals("snapshot", snapshot.get("t").getAsString());
        assertFalse(snapshot.has("network"), "no network map");
        assertFalse(snapshot.get("canControl").getAsBoolean());
        assertEquals("Alex", snapshot.getAsJsonObject("share").get("sharedBy").getAsString());
        var widgets = snapshot.getAsJsonArray("uplinks").get(0).getAsJsonObject().getAsJsonArray("widgets");
        assertEquals(1, widgets.size(), "no item_search, and with hideNumbers no label");
        assertEquals("temp", widgets.get(0).getAsJsonObject().get("id").getAsString());
        assertFalse(snapshot.getAsJsonArray("uplinks").get(0).getAsJsonObject().has("mode"), "script details are not shared");
    }

    @Test
    void everyMessageTypeIsHardDenied() {
        publish();
        var share = access.createShare(mainframe, "Base", "Alex", null, 5, List.of("main"), List.of(), false);
        var channel = connect(authShare(share.share().id(), share.secret()));
        drain(channel, null);

        // One sample per client message type the parser knows (checked below against the sealed interface).
        Map<Class<?>, String[]> samples = new LinkedHashMap<>();
        samples.put(ClientMessages.Auth.class, new String[]{"{\"t\":\"auth\",\"token\":\"abc\"}", "error"});
        samples.put(ClientMessages.AuthShare.class, new String[]{authShare(share.share().id(), share.secret()), "error"});
        samples.put(ClientMessages.Action.class, new String[]{"{\"t\":\"action\",\"reqId\":\"a1\",\"uplinkId\":\"" + uplink + "\",\"id\":\"temp\",\"value\":1}", "action_result"});
        samples.put(ClientMessages.Query.class, new String[]{"{\"t\":\"query\",\"reqId\":\"q1\",\"uplinkId\":\"" + uplink + "\",\"id\":\"search\",\"q\":\"iron\"}", "query_result"});
        samples.put(ClientMessages.LayoutSet.class, new String[]{"{\"t\":\"layout_set\",\"reqId\":\"l1\",\"layout\":{\"v\":1,\"pages\":[],\"cards\":{},\"widgets\":{}}}", "layout_result"});
        samples.put(ClientMessages.NetUnlink.class, new String[]{"{\"t\":\"net_unlink\",\"reqId\":\"n1\",\"nodeId\":\"" + uplink + "\"}", "net_result"});
        samples.put(ClientMessages.RedstoneOverride.class, new String[]{"{\"t\":\"rs_override\",\"reqId\":\"o1\",\"channel\":\"door\",\"level\":1}", "rs_result"});
        samples.put(ClientMessages.RuleCommand.class, new String[]{"{\"t\":\"rule_set\",\"reqId\":\"r1\",\"rule\":{}}", "rule_result"});
        samples.put(ClientMessages.Invalid.class, new String[]{"{\"t\":\"something_else\",\"reqId\":\"x1\"}", "error"});
        samples.put(ClientMessages.Pong.class, new String[]{"{\"t\":\"pong\"}", null});
        for (var type : ClientMessages.Message.class.getPermittedSubclasses()) {
            assertTrue(samples.containsKey(type), "the share test covers " + type.getSimpleName());
        }
        for (var e : samples.entrySet()) {
            channel.writeInbound(new TextWebSocketFrame(e.getValue()[0]));
            channel.runPendingTasks();
            var answers = drain(channel, null);
            String expected = e.getValue()[1];
            if (expected == null) {
                assertTrue(answers.isEmpty(), "pong is the only message without answer");
                continue;
            }
            assertEquals(1, answers.size(), e.getKey().getSimpleName());
            var answer = answers.getFirst();
            assertEquals(expected, answer.get("t").getAsString(), e.getKey().getSimpleName());
            assertEquals("forbidden", answer.get("error").getAsString(), e.getKey().getSimpleName());
            if (!expected.equals("error")) assertFalse(answer.get("ok").getAsBoolean());
        }
        // Without a reqId even requests get the plain error.
        channel.writeInbound(new TextWebSocketFrame("{\"t\":\"action\",\"uplinkId\":\"" + uplink + "\",\"id\":\"temp\",\"value\":1}"));
        assertEquals("error", drain(channel, null).getFirst().get("t").getAsString());
        channel.writeInbound(new TextWebSocketFrame("not json"));
        assertEquals("forbidden", drain(channel, null).getFirst().get("error").getAsString());
        assertTrue(bridge.dispatched.isEmpty(), "nothing reached the game: " + bridge.dispatched);
    }

}
