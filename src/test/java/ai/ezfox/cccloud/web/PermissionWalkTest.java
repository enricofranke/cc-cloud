package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.access.Role;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker13;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Fine-grained permissions: walks every HTTP route and every WebSocket message (each action kind separately) and
 * asserts which permission it needs – denied with everything except that permission, allowed with only view + it.
 */
class PermissionWalkTest {
    private final UUID mainframe = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final String lua = UUID.randomUUID().toString();
    private final String auto = UUID.randomUUID().toString();
    private final String direct = UUID.randomUUID().toString();
    private final TestBridge bridge = new TestBridge();
    private final AuthStore auth = new AuthStore(System::currentTimeMillis, () -> {
    });
    private final WebInstance instance = new WebInstance(bridge, auth, new AccessStore(System::currentTimeMillis, () -> {
    }), null, List.of(), true, "http://localhost", 8787);
    private Set<Perm> granted = Perm.all();

    PermissionWalkTest() {
        bridge.roles = (d, m) -> Role.OPERATOR;
        bridge.perms = (d, m) -> granted;
    }

    @AfterEach
    void cleanup() {
        WebHub.INSTANCE.removeMainframe(mainframe);
        instance.pinExecutor.shutdownNow();
    }

    @Test
    void everyRouteHasItsPermission() {
        Map<Router.Id, Perm> expected = new java.util.HashMap<>(Map.of(
            Router.Id.ACCESS_GET, Perm.ACCESS_MANAGE, Router.Id.ACCESS_POST, Perm.ACCESS_MANAGE,
            Router.Id.SHARES_GET, Perm.SHARES_CREATE, Router.Id.SHARES_POST, Perm.SHARES_CREATE, Router.Id.SHARE_DELETE, Perm.SHARES_CREATE,
            Router.Id.SCRIPT_LIST, Perm.SCRIPTS_UPLOAD, Router.Id.SCRIPT_GET, Perm.SCRIPTS_UPLOAD, Router.Id.SCRIPT_PUT, Perm.SCRIPTS_UPLOAD,
            Router.Id.SCRIPT_DELETE, Perm.SCRIPTS_DELETE, Router.Id.SCRIPT_RUN, Perm.SCRIPTS_RUN));
        expected.put(Router.Id.MAP_TILE, Perm.VIEW_POSITIONS);
        expected.put(Router.Id.VOXELS, Perm.VIEW_POSITIONS);
        expected.put(Router.Id.PUSH_GET, Perm.PUSH_MANAGE);
        expected.put(Router.Id.SCRIPT_MOVE, Perm.SCRIPTS_UPLOAD);
        expected.put(Router.Id.HISTORY, Perm.VIEW);
        expected.put(Router.Id.PUSH_POST, Perm.PUSH_MANAGE);
        // Account-level routes (no mainframe permission): the player's own data or public.
        var accountLevel = Set.of(Router.Id.PAIR, Router.Id.PAIR_STATUS, Router.Id.MAINFRAMES, Router.Id.DEVICES, Router.Id.DEVICE_DELETE,
            Router.Id.ICON, Router.Id.WEBSOCKET, Router.Id.STATIC, Router.Id.CONFIG);
        for (var id : Router.Id.values()) {
            if (accountLevel.contains(id)) {
                assertNull(AccessGate.routePerm(id), id.name());
                continue;
            }
            assertEquals(expected.get(id), AccessGate.routePerm(id), "route " + id + " is covered by this test");
            var withoutIt = EnumSet.copyOf(Perm.all());
            withoutIt.remove(expected.get(id));
            assertNotNull(AccessGate.check(Role.ADMIN, withoutIt, AccessGate.routePerm(id)), id + " denied without " + expected.get(id).id);
            assertNull(AccessGate.check(Role.VIEWER, EnumSet.of(Perm.VIEW, expected.get(id)), AccessGate.routePerm(id)), id + " allowed with it");
        }
    }

    private static JsonObject widget(String id, String type) {
        var w = new JsonObject();
        w.addProperty("id", id);
        w.addProperty("type", type);
        return w;
    }

    private void publish() {
        WebHub.INSTANCE.publishMainframe(mainframe, "Base", true, true);
        var a = new JsonObject();
        a.addProperty("uplinkId", lua);
        a.addProperty("mode", "lua");
        a.addProperty("online", true);
        var widgets = new JsonArray();
        widgets.add(widget("pump", "toggle"));
        widgets.add(widget("search", "item_search"));
        widgets.add(widget("watch", "watchlist"));
        a.add("widgets", widgets);
        WebHub.INSTANCE.publishUplink(mainframe, a);
        var b = new JsonObject();
        b.addProperty("uplinkId", auto);
        b.addProperty("mode", "auto");
        b.addProperty("online", true);
        var cw = new JsonArray();
        cw.add(widget("power", "toggle"));
        cw.add(widget("reboot", "button"));
        cw.add(widget("redstone", "redstone_sides"));
        cw.add(widget("note", "text"));
        b.add("widgets", cw);
        WebHub.INSTANCE.publishUplink(mainframe, b);
        // A device card (v0.5/v0.8): the machine's own redstone mode needs act.redstone, its other controls act.buttons.
        var c = new JsonObject();
        c.addProperty("uplinkId", direct);
        c.addProperty("mode", "direct");
        c.addProperty("online", true);
        var dw = new JsonArray();
        dw.add(widget("redstone", "select"));
        dw.add(widget("spawner_redstone", "toggle"));
        dw.add(widget("field", "toggle"));
        c.add("widgets", dw);
        WebHub.INSTANCE.publishUplink(mainframe, c);
    }

    private EmbeddedChannel connect() {
        var pending = auth.pair(auth.accountCode(player).code(), "Laptop", "127.0.0.1");
        String token = auth.approve(pending.id(), player, "Alex", true).token();
        var handshaker = new WebSocketServerHandshaker13("ws://localhost/ws/client", null, WebSocketDecoderConfig.newBuilder().build());
        var channel = new EmbeddedChannel(new WebSocketSession(instance, handshaker, "127.0.0.1", null));
        channel.writeInbound(new TextWebSocketFrame("{\"t\":\"auth\",\"token\":\"" + token + "\",\"mainframeId\":\"" + mainframe + "\"}"));
        channel.runPendingTasks();
        drain(channel);
        return channel;
    }

    private static List<JsonObject> drain(EmbeddedChannel channel) {
        var list = new ArrayList<JsonObject>();
        Object out;
        while ((out = channel.readOutbound()) != null) if (out instanceof TextWebSocketFrame t) list.add(JsonParser.parseString(t.text()).getAsJsonObject());
        return list;
    }

    private static @Nullable JsonObject result(List<JsonObject> messages, String reqId) {
        for (var m : messages) if (m.has("reqId") && reqId.equals(m.get("reqId").getAsString())) return m;
        return null;
    }

    private String action(String uplink, String id, String value) {
        return "{\"t\":\"action\",\"reqId\":\"r\",\"uplinkId\":\"" + uplink + "\",\"id\":\"" + id + "\",\"value\":" + value + "}";
    }

    @Test
    void everyMessageNeedsItsPermission() {
        publish();
        Map<String, Perm> cases = new LinkedHashMap<>();
        cases.put(action(lua, "pump", "true"), Perm.ACT_BUTTONS);
        cases.put(action(lua, "search", "{\"op\":\"craft\",\"name\":\"minecraft:stick\",\"count\":1}"), Perm.STORAGE_CRAFT);
        cases.put(action(lua, "watch", "{\"op\":\"add\",\"name\":\"minecraft:stick\",\"min\":5}"), Perm.STORAGE_AUTOSTOCK);
        cases.put(action(auto, "redstone", "{\"op\":\"set_output\",\"side\":\"top\",\"value\":15}"), Perm.ACT_REDSTONE);
        cases.put(action(auto, "power", "true"), Perm.ACT_REDSTONE);
        cases.put(action(auto, "reboot", "true"), Perm.ACT_REDSTONE);
        cases.put(action(auto, "note", "\"hi\""), Perm.ACT_BUTTONS);
        cases.put(action(direct, "redstone", "\"Ignore\""), Perm.ACT_REDSTONE);
        cases.put(action(direct, "spawner_redstone", "true"), Perm.ACT_REDSTONE);
        cases.put(action(direct, "field", "true"), Perm.ACT_BUTTONS);
        cases.put("{\"t\":\"query\",\"reqId\":\"r\",\"uplinkId\":\"" + lua + "\",\"id\":\"search\",\"q\":\"iron\"}", Perm.STORAGE_SEARCH);
        cases.put("{\"t\":\"layout_set\",\"reqId\":\"r\",\"layout\":{\"v\":1,\"pages\":[],\"cards\":{},\"widgets\":{}}}", Perm.LAYOUT_EDIT);
        cases.put("{\"t\":\"net_unlink\",\"reqId\":\"r\",\"nodeId\":\"" + lua + "\"}", Perm.NETWORK_LINK);
        cases.put("{\"t\":\"rule_set\",\"reqId\":\"r\",\"rule\":{}}", Perm.RULES_EDIT);
        cases.put("{\"t\":\"rs_override\",\"reqId\":\"r\",\"channel\":\"door\",\"level\":15,\"seconds\":30}", Perm.ACT_REDSTONE);
        cases.put("{\"t\":\"rule_delete\",\"reqId\":\"r\",\"id\":\"0a1b2c3d\"}", Perm.RULES_EDIT);
        cases.put("{\"t\":\"rule_enable\",\"reqId\":\"r\",\"id\":\"0a1b2c3d\",\"enabled\":true}", Perm.RULES_TOGGLE);
        cases.put("{\"t\":\"rule_run\",\"reqId\":\"r\",\"id\":\"0a1b2c3d\"}", Perm.RULES_TOGGLE);
        for (var op : new String[]{"browse", "craftables", "fluids", "item", "cpus"}) {
            cases.put("{\"t\":\"storage\",\"reqId\":\"r\",\"uplinkId\":\"" + lua + "\",\"op\":\"" + op + "\"}", Perm.STORAGE_SEARCH);
        }
        cases.put("{\"t\":\"storage\",\"reqId\":\"r\",\"uplinkId\":\"" + lua + "\",\"op\":\"cancel\",\"jobId\":\"abc\"}", Perm.STORAGE_CRAFT);
        cases.put("{\"t\":\"storage\",\"reqId\":\"r\",\"uplinkId\":\"" + lua + "\",\"op\":\"favorite\",\"name\":\"minecraft:stick\",\"on\":true}", Perm.ACT_BUTTONS);
        cases.put("{\"t\":\"storage\",\"reqId\":\"r\",\"uplinkId\":\"" + lua + "\",\"op\":\"autostock\",\"name\":\"minecraft:stick\",\"min\":5,\"target\":10,\"autoCraft\":true}", Perm.STORAGE_AUTOSTOCK);
        cases.put("{\"t\":\"term_open\",\"reqId\":\"r\",\"uplinkId\":\"" + auto + "\"}", Perm.TERMINAL);
        cases.put("{\"t\":\"term_input\",\"reqId\":\"r\",\"uplinkId\":\"" + auto + "\",\"events\":[{\"type\":\"char\",\"c\":\"a\"}]}", Perm.TERMINAL);
        cases.put("{\"t\":\"craft_preview\",\"reqId\":\"r\",\"uplinkId\":\"" + lua + "\",\"name\":\"minecraft:stick\",\"count\":4}", Perm.STORAGE_CRAFT);
        // Message types that need no mainframe permission beyond the login itself.
        var free = Set.of(ClientMessages.Auth.class, ClientMessages.AuthShare.class, ClientMessages.Pong.class, ClientMessages.Invalid.class);
        var covered = new java.util.HashSet<Class<?>>(free);
        for (var text : cases.keySet()) covered.add(ClientMessages.parse(text).getClass());
        for (var type : ClientMessages.Message.class.getPermittedSubclasses()) assertTrue(covered.contains(type), "covered: " + type.getSimpleName());

        for (var e : cases.entrySet()) {
            var withoutIt = EnumSet.copyOf(Perm.all());
            withoutIt.remove(e.getValue());
            granted = withoutIt;
            var channel = connect();
            bridge.dispatched.clear();
            channel.writeInbound(new TextWebSocketFrame(e.getKey()));
            channel.runPendingTasks();
            var denied = result(drain(channel), "r");
            assertNotNull(denied, "answer for " + e.getKey());
            assertFalse(denied.get("ok") != null && denied.get("ok").getAsBoolean(), "denied without " + e.getValue().id + ": " + e.getKey());
            String error = denied.get("error").getAsString();
            // read_only when the session cannot use buttons at all, else not_allowed.
            assertTrue(error.equals(e.getValue() == Perm.ACT_BUTTONS ? "read_only" : "not_allowed"), e.getKey() + " → " + error);
            assertTrue(bridge.dispatched.isEmpty(), "never reached the game without " + e.getValue().id);

            granted = EnumSet.of(Perm.VIEW, e.getValue());
            channel = connect();
            bridge.dispatched.clear();
            channel.writeInbound(new TextWebSocketFrame(e.getKey()));
            channel.runPendingTasks();
            var answer = result(drain(channel), "r");
            String allowedError = answer == null || !answer.has("error") ? null : answer.get("error").getAsString();
            assertFalse("not_allowed".equals(allowedError) || "read_only".equals(allowedError) || "forbidden".equals(allowedError),
                "allowed with only " + e.getValue().id + ": " + e.getKey() + " → " + answer);
            assertFalse(bridge.dispatched.isEmpty(), "reached the game with " + e.getValue().id + ": " + e.getKey());
        }
    }
}
