package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.web.internet.InternetSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker13;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The {@code pin} message over a real {@link WebSocketSession}: the PIN dialog shows exactly as many slots as the
 * mainframe PIN has digits. Sent after the login, right away when the PIN changes, and again when an old PIN (saved
 * without its length) learns its length on a correct entry. Sessions that cannot control get no length.
 */
class PinMessageTest {
    private static final String PUBLIC = "203.0.113.9";
    private static final String LAN = "192.168.1.20";
    private final UUID mainframe = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
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
        instance.storeExecutor.shutdownNow();
    }

    @Test
    void theLoginTellsTheLength() {
        WebHub.INSTANCE.publishMainframe(mainframe, "Base", true, true);
        auth.setPin(mainframe, auth.hashPin("246810"));
        var pin = pinMessages(login(LAN, token(true)));
        assertEquals(1, pin.size(), pin.toString());
        assertTrue(pin.getFirst().get("set").getAsBoolean());
        assertEquals(6, pin.getFirst().get("length").getAsInt());
    }

    @Test
    void noPinNoMessageUntilOneIsSet() {
        WebHub.INSTANCE.publishMainframe(mainframe, "Base", true, true);
        var phone = login(LAN, token(true));
        assertTrue(pinMessages(phone).isEmpty(), "nothing to say");
        auth.setPin(mainframe, auth.hashPin("4711"));
        phone.runPendingTasks();
        var set = pinMessages(phone);
        assertEquals(1, set.size(), "right away, not only at the next re-check");
        assertEquals(4, set.getFirst().get("length").getAsInt());
        CloudWebServer.recheckForTest(instance, mainframe);
        assertTrue(pinMessages(phone).isEmpty(), "unchanged: the re-check stays quiet");
        auth.setPin(mainframe, null);
        phone.runPendingTasks();
        var cleared = pinMessages(phone);
        assertFalse(cleared.getFirst().get("set").getAsBoolean());
        assertEquals(0, cleared.getFirst().get("length").getAsInt());
    }

    @Test
    void anOldPinLearnsItsLengthAndTheDashboardHearsIt() throws Exception {
        instance.internet.update(InternetSettings.resolve(new InternetSettings.Inputs("http", "", 8787, false, "", false, 24, true)));
        WebHub.INSTANCE.publishMainframe(mainframe, "Base", true, true);
        auth.load(Map.of(), List.of(), Map.of(mainframe, auth.hashPin("97531").withLength(0)));
        var phone = login(PUBLIC, token(true));
        var first = pinMessages(phone);
        assertTrue(first.getFirst().get("set").getAsBoolean());
        assertEquals(0, first.getFirst().get("length").getAsInt(), "not known yet");
        var other = login(LAN, token(true));
        pinMessages(other);
        // a correct entry (here the internet unlock) stores the length
        phone.writeInbound(new TextWebSocketFrame("{\"t\":\"unlock\",\"reqId\":\"u1\",\"pin\":\"97531\"}"));
        List<JsonObject> learnt = List.of();
        for (int i = 0; i < 300 && learnt.isEmpty(); i++) {
            phone.runPendingTasks();
            learnt = pinMessages(phone);
            if (learnt.isEmpty()) Thread.sleep(10);
        }
        assertEquals(5, learnt.getFirst().get("length").getAsInt());
        assertEquals(5, auth.pinLength(mainframe));
        other.runPendingTasks();
        assertEquals(5, pinMessages(other).getFirst().get("length").getAsInt(), "every open dashboard of the mainframe");
    }

    @Test
    void readOnlyDevicesGetNoLength() {
        WebHub.INSTANCE.publishMainframe(mainframe, "Base", true, true);
        auth.setPin(mainframe, auth.hashPin("123456"));
        var viewer = pinMessages(login(LAN, token(false)));
        assertTrue(viewer.getFirst().get("set").getAsBoolean());
        assertEquals(0, viewer.getFirst().get("length").getAsInt(), "a viewer never enters the PIN");
    }

    private String token(boolean control) {
        var pending = auth.pair(auth.currentCode(mainframe, owner).code(), "Phone", "127.0.0.1");
        return auth.approve(pending.id(), owner, "Steve", control).token();
    }

    private EmbeddedChannel login(String ip, String token) {
        var handshaker = new WebSocketServerHandshaker13("ws://localhost/ws/client", null, WebSocketDecoderConfig.newBuilder().build());
        var channel = new EmbeddedChannel(new WebSocketSession(instance, handshaker, ip, null));
        channel.writeInbound(new TextWebSocketFrame("{\"t\":\"auth\",\"token\":\"" + token + "\"}"));
        channel.runPendingTasks();
        return channel;
    }

    /** The {@code pin} messages sent since the last call (everything else is read and dropped). */
    private static List<JsonObject> pinMessages(EmbeddedChannel channel) {
        var list = new ArrayList<JsonObject>();
        Object out;
        while ((out = channel.readOutbound()) != null) {
            if (!(out instanceof TextWebSocketFrame text)) continue;
            var json = JsonParser.parseString(text.text()).getAsJsonObject();
            if (json.get("t").getAsString().equals("pin")) list.add(json);
        }
        return list;
    }
}
