package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.Role;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker13;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Account tokens, mainframe selection, role checks per message and live role changes over a real session. */
class RoleSessionTest {
    private final UUID mainframe = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final TestBridge bridge = new TestBridge();
    private final AuthStore auth = new AuthStore(System::currentTimeMillis, () -> {
    });
    private final WebInstance instance = new WebInstance(bridge, auth, new AccessStore(System::currentTimeMillis, () -> {
    }), null, List.of(), true, "http://localhost", 8787);
    private Role role = Role.VIEWER;

    RoleSessionTest() {
        bridge.roles = (device, mf) -> mf.equals(mainframe) ? role : Role.NONE;
    }

    @AfterEach
    void cleanup() {
        WebHub.INSTANCE.removeMainframe(mainframe);
        instance.pinExecutor.shutdownNow();
    }

    private String accountToken() {
        var pending = auth.pair(auth.accountCode(player).code(), "Laptop", "127.0.0.1");
        return auth.approve(pending.id(), player, "Alex", true).token();
    }

    private EmbeddedChannel connect(String first) {
        var handshaker = new WebSocketServerHandshaker13("ws://localhost/ws/client", null, WebSocketDecoderConfig.newBuilder().build());
        var channel = new EmbeddedChannel(new WebSocketSession(instance, handshaker, "127.0.0.1", null));
        channel.writeInbound(new TextWebSocketFrame(first));
        channel.runPendingTasks();
        return channel;
    }

    private static List<JsonObject> drain(EmbeddedChannel channel, int[] close) {
        var list = new ArrayList<JsonObject>();
        Object out;
        while ((out = channel.readOutbound()) != null) {
            if (out instanceof TextWebSocketFrame text) list.add(JsonParser.parseString(text.text()).getAsJsonObject());
            if (out instanceof CloseWebSocketFrame c && close != null) close[0] = c.statusCode();
        }
        return list;
    }

    private static JsonObject find(List<JsonObject> list, String t) {
        return list.stream().filter(j -> t.equals(j.get("t").getAsString())).findFirst().orElseThrow(() -> new AssertionError("no " + t + " in " + list));
    }

    @Test
    void accountTokensNeedAMainframe() {
        WebHub.INSTANCE.publishMainframe(mainframe, "Base", true, true);
        String token = accountToken();
        int[] close = {-1};
        assertEquals("mainframe_required", drain(connect("{\"t\":\"auth\",\"token\":\"" + token + "\"}"), close).getFirst().get("error").getAsString());
        assertEquals(4401, close[0]);
        close[0] = -1;
        var other = UUID.randomUUID();
        assertEquals("no_access", drain(connect("{\"t\":\"auth\",\"token\":\"" + token + "\",\"mainframeId\":\"" + other + "\"}"), close).getFirst().get("error").getAsString());
        assertEquals(4403, close[0]);

        var ok = drain(connect("{\"t\":\"auth\",\"token\":\"" + token + "\",\"mainframeId\":\"" + mainframe + "\"}"), null);
        var snapshot = find(ok, "snapshot");
        assertEquals("viewer", snapshot.get("role").getAsString());
        assertFalse(snapshot.get("canControl").getAsBoolean());
        assertEquals("Alex", snapshot.getAsJsonObject("account").get("player").getAsString());
    }

    @Test
    void rolesGateMessagesAndChangeLive() {
        WebHub.INSTANCE.publishMainframe(mainframe, "Base", true, true);
        String token = accountToken();
        var channel = connect("{\"t\":\"auth\",\"token\":\"" + token + "\",\"mainframeId\":\"" + mainframe + "\"}");
        drain(channel, null);
        String uplink = UUID.randomUUID().toString();
        String action = "{\"t\":\"action\",\"reqId\":\"a1\",\"uplinkId\":\"" + uplink + "\",\"id\":\"x\",\"value\":1}";
        String layout = "{\"t\":\"layout_set\",\"reqId\":\"l1\",\"layout\":null}";
        String unlink = "{\"t\":\"net_unlink\",\"reqId\":\"n1\",\"nodeId\":\"" + uplink + "\"}";

        channel.writeInbound(new TextWebSocketFrame(action));
        assertEquals("read_only", find(drain(channel, null), "action_result").get("error").getAsString(), "viewer");
        channel.writeInbound(new TextWebSocketFrame(unlink));
        assertEquals("read_only", find(drain(channel, null), "net_result").get("error").getAsString());

        // Promoted to operator: the live re-check pushes {"t":"role"}.
        role = Role.OPERATOR;
        CloudWebServer.recheckForTest(instance, mainframe);
        var push = find(drain(channel, null), "role");
        assertEquals("operator", push.get("role").getAsString());
        assertTrue(push.get("canControl").getAsBoolean());
        channel.writeInbound(new TextWebSocketFrame(action));
        channel.runPendingTasks();
        assertTrue(find(drain(channel, null), "action_result").get("ok").getAsBoolean(), "operators act");
        channel.writeInbound(new TextWebSocketFrame(layout));
        assertEquals("not_allowed", find(drain(channel, null), "layout_result").get("error").getAsString(), "layout needs admin");
        channel.writeInbound(new TextWebSocketFrame(unlink));
        assertEquals("not_allowed", find(drain(channel, null), "net_result").get("error").getAsString());

        role = Role.ADMIN;
        CloudWebServer.recheckForTest(instance, mainframe);
        drain(channel, null);
        channel.writeInbound(new TextWebSocketFrame(unlink));
        channel.runPendingTasks();
        assertTrue(find(drain(channel, null), "net_result").get("ok").getAsBoolean());

        // Role removed (untrusted, left the team, banned): the session is closed.
        role = Role.NONE;
        int[] close = {-1};
        CloudWebServer.recheckForTest(instance, mainframe);
        assertEquals("no_access", find(drain(channel, close), "error").get("error").getAsString());
        assertEquals(4403, close[0]);
        assertEquals(List.of("action:x", "unlink:" + uplink), bridge.dispatched);
    }
}
