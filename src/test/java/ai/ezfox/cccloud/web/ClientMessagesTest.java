package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientMessagesTest {
    private static final String UPLINK = UUID.randomUUID().toString();

    @Test
    void parsesAuth() {
        assertInstanceOf(ClientMessages.Auth.class, ClientMessages.parse("{\"t\":\"auth\",\"token\":\"abc\"}"));
        assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse("{\"t\":\"auth\"}"));
        assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse("{\"t\":\"auth\",\"token\":\"" + "a".repeat(200) + "\"}"));
    }

    @Test
    void parsesValidAction() {
        var msg = ClientMessages.parse("{\"t\":\"action\",\"reqId\":\"r1\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"scram\",\"value\":true}");
        var action = assertInstanceOf(ClientMessages.Action.class, msg);
        assertEquals("r1", action.reqId());
        assertEquals("scram", action.id());
        assertNull(action.pin());
    }

    @Test
    void rejectsMalformedActions() {
        assertInvalid("{\"t\":\"action\",\"uplinkId\":\"nope\",\"id\":\"x\",\"value\":1}");
        assertInvalid("{\"t\":\"action\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"Bad-Id\",\"value\":1}");
        assertInvalid("{\"t\":\"action\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"x\",\"value\":{\"a\":1}}");
        assertInvalid("{\"t\":\"action\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"x\",\"value\":[1]}");
        assertInvalid("{\"t\":\"action\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"x\"}");
        assertInvalid("{\"t\":\"action\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"x\",\"value\":\"" + "a".repeat(300) + "\"}");
        assertInvalid("{\"t\":\"action\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"x\",\"value\":true,\"pin\":\"12\"}");
        assertInvalid("not json");
        assertInvalid("[1,2]");
        assertInvalid("{\"t\":\"unknown\"}");
        assertInvalid("{\"t\":\"action\"," + " ".repeat(ClientMessages.MAX_FRAME) + "}");
    }

    @Test
    void reqIdIsSanitized() {
        var action = (ClientMessages.Action) ClientMessages.parse("{\"t\":\"action\",\"reqId\":\"<script>\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"x\",\"value\":true}");
        assertNull(action.reqId());
    }

    @Test
    void onlyAuthAndPongArePublicMessages() {
        assertTrue(ClientMessages.isPublic(new ClientMessages.Auth("t")));
        assertTrue(ClientMessages.isPublic(new ClientMessages.Pong()));
        assertFalse(ClientMessages.isPublic(ClientMessages.parse("{\"t\":\"action\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"x\",\"value\":true}")));
    }

    @Test
    void parsesPairRequests() {
        var request = ClientMessages.parsePair("{\"code\":\"ABCD-EFGH\",\"name\":\"  Enricos iPhone \"}");
        assertNotNull(request);
        assertEquals("Enricos iPhone", request.name());
        assertNull(ClientMessages.parsePair("{\"code\":\"ABCD-EFGH\",\"name\":\"   \"}"));
        assertNull(ClientMessages.parsePair("{\"code\":\"ABCD-EFGH\"}"));
        assertNull(ClientMessages.parsePair("{\"code\":\"" + "A".repeat(40) + "\",\"name\":\"x\"}"));
        assertNull(ClientMessages.parsePair("x".repeat(ClientMessages.MAX_BODY + 1)));
    }

    private static void assertInvalid(String text) {
        assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse(text), text.length() > 80 ? text.substring(0, 80) : text);
    }
}
