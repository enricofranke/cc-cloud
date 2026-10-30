package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.web.internet.InternetGuard;
import ai.ezfox.cccloud.web.internet.InternetSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker13;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@code internetAccess = http} over a real {@link WebSocketSession} and {@link HttpHandler}. By default control from
 * the internet just works (no PIN); with {@code internetRequirePin} (most tests here) every write from an internet
 * address needs the PIN (unlock or with the action) and a mainframe without a PIN is view-only from there. Idle tokens
 * expire on the internet, share links stay read-only, and the LAN is unaffected either way.
 */
class InternetHttpModeTest {
    private static final String PUBLIC = "203.0.113.9";
    private static final String LAN = "192.168.1.20";
    private final UUID mainframe = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final String uplink = UUID.randomUUID().toString();
    private final TestBridge bridge = new TestBridge();
    private final AuthStore auth = new AuthStore(System::currentTimeMillis, () -> {
    });
    private final AccessStore access = new AccessStore(System::currentTimeMillis, () -> {
    });
    private final WebInstance instance = new WebInstance(bridge, auth, access, null, List.of(), true, "http://localhost", 8787);

    InternetHttpModeTest() {
        instance.internet.update(STRICT);
    }

    private static final InternetSettings STRICT = InternetSettings.resolve(new InternetSettings.Inputs("http", "", 8787, false, "", false, 24, true));
    private static final InternetSettings DEFAULT = InternetSettings.resolve(new InternetSettings.Inputs("http", "", 8787, false, "", false, 24));

    @Test
    void byDefaultControlFromTheInternetJustWorks() throws Exception {
        instance.internet.update(DEFAULT);
        publish();
        var phone = login(PUBLIC, token()); // no PIN on this mainframe at all
        assertNull(error(ask(phone, action(1))), "no PIN needed");
        assertNull(error(ask(phone, "{\"t\":\"rs_override\",\"reqId\":\"r1\",\"channel\":\"door\",\"level\":15}")));
        assertEquals(List.of("action:lamp", "rs_override:door=15"), bridge.dispatched);
        // the other plain-HTTP rules stay: idle tokens and the API
        assertEquals("http", body(http(PUBLIC, HttpMethod.GET, "/api/config", null, "")).getAsJsonObject("internet").get("mode").getAsString());
        assertFalse(body(http(PUBLIC, HttpMethod.GET, "/api/config", null, "")).getAsJsonObject("internet").get("remote").getAsBoolean(),
            "no unlock needed");
        String token = token();
        var device = auth.authenticate(token);
        auth.deviceMap().put(device.tokenHash(), new AuthStore.Device(device.tokenHash(), device.mainframeId(), device.name(), true, device.created(),
            System.currentTimeMillis() - 25 * InternetSettings.HOUR_MS, device.player(), device.playerName(), device.type(), device.lastIp()));
        assertEquals("token_expired", error(drain(session(PUBLIC, "{\"t\":\"auth\",\"token\":\"" + token + "\"}"), null).getFirst()));
    }

    @Test
    void readOnlyFromOutsideIsAnnouncedInStrictMode() throws Exception {
        publish();
        var phone = session(PUBLIC, "{\"t\":\"auth\",\"token\":\"" + token() + "\"}");
        var answers = drain(phone, null);
        var notice = answers.stream().filter(a -> a.get("t").getAsString().equals("internet")).findFirst().orElseThrow();
        assertTrue(notice.get("remote").getAsBoolean());
        assertFalse(notice.get("pin").getAsBoolean(), "the dashboard shows 'read-only from outside'");
        pin();
        CloudWebServer.recheckForTest(instance, mainframe);
        var update = drain(phone, null).stream().filter(a -> a.get("t").getAsString().equals("internet")).findFirst().orElseThrow();
        assertTrue(update.get("pin").getAsBoolean(), "set a PIN: the next re-check says so");
        var lan = session(LAN, "{\"t\":\"auth\",\"token\":\"" + token() + "\"}");
        assertTrue(drain(lan, null).stream().noneMatch(a -> a.get("t").getAsString().equals("internet")), "nothing for the LAN");
    }

    @AfterEach
    void cleanup() {
        WebHub.INSTANCE.removeMainframe(mainframe);
        instance.pinExecutor.shutdownNow();
        instance.storeExecutor.shutdownNow();
    }

    private void publish() {
        WebHub.INSTANCE.publishMainframe(mainframe, "Base", true, true);
        var json = new JsonObject();
        json.addProperty("uplinkId", uplink);
        json.addProperty("title", "Reactor");
        json.addProperty("online", true);
        json.addProperty("mode", "lua");
        var widgets = new JsonArray();
        var lamp = new JsonObject();
        lamp.addProperty("id", "lamp");
        lamp.addProperty("type", "toggle");
        widgets.add(lamp);
        json.add("widgets", widgets);
        WebHub.INSTANCE.publishUplink(mainframe, json);
    }

    private String token() {
        var pending = auth.pair(auth.currentCode(mainframe, owner).code(), "Phone", "127.0.0.1");
        return auth.approve(pending.id(), owner, "Steve", true).token();
    }

    private void pin() {
        auth.setPin(mainframe, auth.hashPin("4711"));
    }

    private EmbeddedChannel session(String ip, String first) {
        var handshaker = new WebSocketServerHandshaker13("ws://localhost/ws/client", null, WebSocketDecoderConfig.newBuilder().build());
        var channel = new EmbeddedChannel(new WebSocketSession(instance, handshaker, ip, null));
        channel.writeInbound(new TextWebSocketFrame(first));
        channel.runPendingTasks();
        return channel;
    }

    private EmbeddedChannel login(String ip, String token) {
        var channel = session(ip, "{\"t\":\"auth\",\"token\":\"" + token + "\"}");
        assertEquals("snapshot", drain(channel, null).getFirst().get("t").getAsString(), "logged in");
        return channel;
    }

    /** Sends a message and waits (the PIN check runs on its own thread) for the first answer. */
    private JsonObject ask(EmbeddedChannel channel, String message) throws InterruptedException {
        channel.writeInbound(new TextWebSocketFrame(message));
        for (int i = 0; i < 300; i++) {
            channel.runPendingTasks();
            var answers = drain(channel, null);
            for (var a : answers) if (!a.get("t").getAsString().equals("value") && !a.get("t").getAsString().equals("values")) return a;
            Thread.sleep(10);
        }
        throw new AssertionError("no answer to " + message);
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

    private String action(int n) {
        return "{\"t\":\"action\",\"reqId\":\"a" + n + "\",\"uplinkId\":\"" + uplink + "\",\"id\":\"lamp\",\"value\":true}";
    }

    private static String error(JsonObject answer) {
        return answer.has("error") ? answer.get("error").getAsString() : null;
    }

    @Test
    void everyWriteFromTheInternetNeedsThePin() throws Exception {
        publish();
        pin();
        var phone = login(PUBLIC, token());
        var locked = ask(phone, action(1));
        assertEquals("action_result", locked.get("t").getAsString());
        assertEquals("remote_locked", error(locked));
        var layout = ask(phone, "{\"t\":\"layout_set\",\"reqId\":\"l1\",\"layout\":{\"v\":1,\"pages\":[]}}");
        assertEquals("layout_result", layout.get("t").getAsString(), "the refusal keeps the result type");
        assertEquals("remote_locked", error(layout));
        var rule = ask(phone, "{\"t\":\"rule_delete\",\"reqId\":\"r1\",\"id\":\"0a1b2c3d\"}");
        assertEquals("remote_locked", error(rule));
        assertTrue(bridge.dispatched.isEmpty(), "nothing reached the game: " + bridge.dispatched);

        var wrong = ask(phone, "{\"t\":\"unlock\",\"reqId\":\"u1\",\"pin\":\"0000\"}");
        assertEquals("unlock_result", wrong.get("t").getAsString());
        assertEquals("wrong_pin", error(wrong));
        Thread.sleep(1100); // one unlock per second
        var ok = ask(phone, "{\"t\":\"unlock\",\"reqId\":\"u2\",\"pin\":\"4711\"}");
        assertTrue(ok.get("ok").getAsBoolean(), ok.toString());
        assertEquals(600, ok.get("seconds").getAsLong());
        assertNull(error(ask(phone, action(2))), "unlocked: the action goes through");
        assertEquals(List.of("action:lamp"), bridge.dispatched);
        assertTrue(access.audit(mainframe).stream().anyMatch(e -> e.text().contains("Remote control unlocked") && e.text().contains(PUBLIC)), "audited");
    }

    @Test
    void aPinSentWithTheActionUnlocksToo() throws Exception {
        publish();
        pin();
        var phone = login(PUBLIC, token());
        String withPin = "{\"t\":\"action\",\"reqId\":\"p1\",\"uplinkId\":\"" + uplink + "\",\"id\":\"lamp\",\"value\":true,\"pin\":\"4711\"}";
        assertNull(error(ask(phone, withPin)));
        assertNull(error(ask(phone, action(3))), "and stays unlocked");
        assertEquals(List.of("action:lamp", "action:lamp"), bridge.dispatched);
    }

    @Test
    void withoutAPinNothingCanBeControlledFromTheInternet() throws Exception {
        publish();
        bridge.roles = (device, mf) -> ai.ezfox.cccloud.access.Role.ADMIN; // admins may open the terminal
        var phone = login(PUBLIC, token());
        assertEquals("remote_no_pin", error(ask(phone, action(1))));
        assertEquals("remote_no_pin", error(ask(phone, "{\"t\":\"unlock\",\"reqId\":\"u1\",\"pin\":\"1234\"}")));
        assertEquals("remote_no_pin", error(ask(phone, "{\"t\":\"rs_override\",\"reqId\":\"r1\",\"channel\":\"door\",\"level\":15}")));
        assertEquals("remote_no_pin", error(ask(phone, "{\"t\":\"term_open\",\"reqId\":\"t1\",\"uplinkId\":\"" + uplink + "\"}")));
        assertTrue(bridge.dispatched.isEmpty(), bridge.dispatched.toString());
    }

    @Test
    void theLanIsUnaffected() throws Exception {
        publish();
        var pc = login(LAN, token());
        assertNull(error(ask(pc, action(1))), "no PIN needed on the LAN");
        var unlock = ask(pc, "{\"t\":\"unlock\",\"reqId\":\"u1\",\"pin\":\"1234\"}");
        assertTrue(unlock.get("ok").getAsBoolean());
        assertEquals(0, unlock.get("seconds").getAsLong(), "nothing to unlock");
        // other modes: the internet is not strict either (e.g. an older setup behind a proxy)
        instance.internet.update(InternetSettings.OFF);
        var other = login(PUBLIC, token());
        assertNull(error(ask(other, action(2))));
        assertEquals(List.of("action:lamp", "action:lamp"), bridge.dispatched);
    }

    @Test
    void idleTokensExpireFromTheInternet() {
        publish();
        String token = token();
        var device = auth.authenticate(token);
        // pretend the browser was last used 25 h ago
        auth.deviceMap().put(device.tokenHash(), new AuthStore.Device(device.tokenHash(), device.mainframeId(), device.name(), true, device.created(),
            System.currentTimeMillis() - 25 * InternetSettings.HOUR_MS, device.player(), device.playerName(), device.type(), device.lastIp()));
        int[] close = {-1};
        var answers = drain(session(PUBLIC, "{\"t\":\"auth\",\"token\":\"" + token + "\"}"), close);
        assertEquals("token_expired", error(answers.getFirst()));
        assertEquals(4401, close[0]);
        assertNull(auth.authenticate(token), "the token is gone (pair again)");
    }

    @Test
    void idleTokensStillWorkOnTheLan() {
        publish();
        String token = token();
        var device = auth.authenticate(token);
        auth.deviceMap().put(device.tokenHash(), new AuthStore.Device(device.tokenHash(), device.mainframeId(), device.name(), true, device.created(),
            System.currentTimeMillis() - 5 * 24 * InternetSettings.HOUR_MS, device.player(), device.playerName(), device.type(), device.lastIp()));
        login(LAN, token);
        assertNotNull(auth.authenticate(token));
    }

    @Test
    void wrongPinsBlockTheAddress() throws Exception {
        publish();
        pin();
        String token = token();
        for (int i = 0; i < InternetGuard.PUBLIC_FAILURES; i++) instance.internet.failure(PUBLIC);
        int[] close = {-1};
        var answers = drain(session(PUBLIC, "{\"t\":\"auth\",\"token\":\"" + token + "\"}"), close);
        assertEquals("rate_limited", error(answers.getFirst()));
        assertEquals(4429, close[0]);
        login(LAN, token);
    }

    @Test
    void stricterActionRate() throws Exception {
        publish();
        pin();
        String token = token();
        var phone = login(PUBLIC, token);
        var device = auth.authenticate(token);
        instance.internet.unlock(device.tokenHash(), mainframe, PUBLIC);
        int limited = 0;
        for (int i = 0; i < 8; i++) if ("rate_limited".equals(error(ask(phone, action(i))))) limited++;
        assertTrue(limited >= 2, "5 actions per second from the internet, got " + limited + " refusals of 8");
    }

    @Test
    void shareLinksStayReadOnly() throws Exception {
        publish();
        pin();
        var share = access.createShare(mainframe, "Base", "Alex", 3600L, 2, List.of(), List.of(uplink), false);
        var viewer = session(PUBLIC, "{\"t\":\"auth_share\",\"id\":\"" + share.share().id() + "\",\"secret\":\"" + share.secret() + "\"}");
        assertEquals("snapshot", drain(viewer, null).getFirst().get("t").getAsString());
        var unlock = ask(viewer, "{\"t\":\"unlock\",\"reqId\":\"u1\",\"pin\":\"4711\"}");
        assertEquals("unlock_result", unlock.get("t").getAsString());
        assertEquals("forbidden", error(unlock), "a share link never unlocks anything");
        assertEquals("forbidden", error(ask(viewer, action(1))));
        assertTrue(bridge.dispatched.isEmpty());
    }

    @Test
    void writeClassification() {
        assertTrue(ClientMessages.isWrite(ClientMessages.parse(action(1))));
        assertTrue(ClientMessages.isWrite(ClientMessages.parse("{\"t\":\"storage\",\"reqId\":\"s\",\"uplinkId\":\"" + uplink + "\",\"op\":\"cancel\"}")));
        assertFalse(ClientMessages.isWrite(ClientMessages.parse("{\"t\":\"storage\",\"reqId\":\"s\",\"uplinkId\":\"" + uplink + "\",\"op\":\"browse\"}")));
        assertTrue(ClientMessages.isWrite(ClientMessages.parse("{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"pin\",\"kind\":\"energy\"}")));
        assertFalse(ClientMessages.isWrite(ClientMessages.parse("{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"overview\"}")));
        assertFalse(ClientMessages.isWrite(ClientMessages.parse("{\"t\":\"query\",\"reqId\":\"q\",\"uplinkId\":\"" + uplink + "\",\"id\":\"s\",\"q\":\"iron\"}")));
        assertFalse(ClientMessages.isWrite(ClientMessages.parse("{\"t\":\"term_close\",\"uplinkId\":\"" + uplink + "\"}")));
        assertTrue(ClientMessages.isWrite(ClientMessages.parse("{\"t\":\"term_input\",\"uplinkId\":\"" + uplink + "\",\"events\":[]}")));
        assertFalse(ClientMessages.isWrite(ClientMessages.parse("{\"t\":\"unlock\",\"reqId\":\"u\",\"pin\":\"1234\"}")));
        assertInstanceOf(ClientMessages.Unlock.class, ClientMessages.parse("{\"t\":\"unlock\",\"reqId\":\"u\",\"pin\":\"1234\"}"));
        assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse("{\"t\":\"unlock\",\"pin\":\"1234\"}"), "needs a reqId");
    }

    // ---- HTTP ---------------------------------------------------------------------------------------------------------

    /** An HTTP connection from {@code ip}. */
    private FullHttpResponse http(String ip, HttpMethod method, String path, String token, String body) throws InterruptedException {
        var channel = new EmbeddedChannel() {
            @Override
            protected SocketAddress remoteAddress0() {
                return new InetSocketAddress(ip, 40000);
            }
        };
        channel.pipeline().addLast(new HttpHandler(instance));
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, path, Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        req.headers().set(HttpHeaderNames.HOST, "203.0.113.1:8787");
        if (token != null) req.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + token);
        channel.writeInbound(req);
        for (int i = 0; i < 200; i++) {
            channel.runPendingTasks();
            FullHttpResponse response = channel.readOutbound();
            if (response != null) return response;
            Thread.sleep(10);
        }
        throw new AssertionError("no response for " + path);
    }

    private static JsonObject body(FullHttpResponse response) {
        return JsonParser.parseString(response.content().toString(StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void settingsChangesOverHttpNeedTheUnlockToo() throws Exception {
        pin();
        String token = token();
        String path = "/api/mainframes/" + mainframe + "/shares";
        var locked = http(PUBLIC, HttpMethod.POST, path, token, "{}");
        assertEquals(403, locked.status().code());
        assertEquals("remote_locked", body(locked).get("error").getAsString());
        // (the test bridge answers every API call with 404: reaching it is what counts)
        assertEquals(404, http(PUBLIC, HttpMethod.GET, path, token, "").status().code(), "reading needs no PIN");
        assertEquals(404, http(LAN, HttpMethod.POST, path, token, "{}").status().code(), "the LAN needs no PIN");
        instance.internet.unlock(auth.authenticate(token).tokenHash(), mainframe, PUBLIC);
        assertEquals(404, http(PUBLIC, HttpMethod.POST, path, token, "{}").status().code(), "after the unlock");
        assertEquals(List.of("api:SHARES_GET", "api:SHARES_POST", "api:SHARES_POST"), bridge.dispatched);
    }

    @Test
    void publicConfigTellsTheDashboard() throws Exception {
        var remote = body(http(PUBLIC, HttpMethod.GET, "/api/config", null, "")).getAsJsonObject("internet");
        assertEquals("http", remote.get("mode").getAsString());
        assertTrue(remote.get("remote").getAsBoolean());
        assertFalse(body(http(LAN, HttpMethod.GET, "/api/config", null, "")).getAsJsonObject("internet").get("remote").getAsBoolean());
    }

    @Test
    void scriptWritesWithoutAPinAreRefusedFromTheInternet() throws Exception {
        String token = token();
        String path = "/api/mainframes/" + mainframe + "/computers/" + uplink + "/files/a.lua";
        var refused = http(PUBLIC, HttpMethod.PUT, path, token, "print(1)");
        assertEquals(403, refused.status().code());
        assertEquals("remote_no_pin", body(refused).get("error").getAsString());
        assertTrue(bridge.dispatched.isEmpty());
    }

    @Test
    void wrongTokensCountAgainstTheAddress() throws Exception {
        for (int i = 0; i < InternetGuard.PUBLIC_FAILURES; i++) {
            assertEquals(401, http(PUBLIC, HttpMethod.GET, "/api/devices", "nope" + i, "").status().code());
        }
        var blocked = http(PUBLIC, HttpMethod.GET, "/api/devices", token(), "");
        assertEquals(429, blocked.status().code(), "blocked for the rest of the window");
        assertEquals(404, http(LAN, HttpMethod.GET, "/api/devices", token(), "").status().code(), "the LAN is not blocked (reaches the bridge)");
    }
}
