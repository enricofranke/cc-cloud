package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.uplink.Op;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientMessagesV2Test {
    private static final String UPLINK = UUID.randomUUID().toString();
    private static final String LAYOUT = "{\"v\":1,\"pages\":[{\"id\":\"main\",\"title\":\"Overview\"}],\"cards\":{},\"widgets\":{}}";

    @Test
    void parsesQueries() {
        var q = assertInstanceOf(ClientMessages.Query.class,
            ClientMessages.parse("{\"t\":\"query\",\"reqId\":\"q1\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"search\",\"q\":\"  iron \"}"));
        assertEquals("iron", q.q());
        assertEquals("q1", q.reqId());
        assertInstanceOf(ClientMessages.Query.class, ClientMessages.parse("{\"t\":\"query\",\"reqId\":\"q\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"s\",\"q\":\"\"}"));
    }

    @Test
    void rejectsBadQueries() {
        String base = "\"uplinkId\":\"" + UPLINK + "\",\"id\":\"search\"";
        for (var bad : new String[]{
            "{\"t\":\"query\"," + base + ",\"q\":\"iron\"}",
            "{\"t\":\"query\",\"reqId\":\"<x>\"," + base + ",\"q\":\"iron\"}",
            "{\"t\":\"query\",\"reqId\":\"q\"," + base + "}",
            "{\"t\":\"query\",\"reqId\":\"q\"," + base + ",\"q\":5}",
            "{\"t\":\"query\",\"reqId\":\"q\"," + base + ",\"q\":\"" + "x".repeat(65) + "\"}",
            "{\"t\":\"query\",\"reqId\":\"q\",\"uplinkId\":\"nope\",\"id\":\"search\",\"q\":\"\"}",
            "{\"t\":\"query\",\"reqId\":\"q\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"Search\",\"q\":\"\"}",
        }) {
            assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse(bad), bad);
        }
    }

    @Test
    void parsesOperationActions() {
        var action = assertInstanceOf(ClientMessages.Action.class, ClientMessages.parse(
            "{\"t\":\"action\",\"reqId\":\"a\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"search\",\"value\":{\"op\":\"craft\",\"name\":\"minecraft:glass\",\"count\":64},\"pin\":\"1234\"}"));
        assertEquals(new Op.Craft("minecraft:glass", 64), action.op());
        assertEquals("1234", action.pin());
        var watch = assertInstanceOf(ClientMessages.Action.class, ClientMessages.parse(
            "{\"t\":\"action\",\"reqId\":\"a\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"watch\",\"value\":{\"op\":\"set_min\",\"name\":\"a:b\",\"min\":3}}"));
        assertEquals(new Op.WatchSetMin("a:b", 3), watch.op());
        var scalar = assertInstanceOf(ClientMessages.Action.class, ClientMessages.parse(
            "{\"t\":\"action\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"mode\",\"value\":\"high\"}"));
        assertNull(scalar.op());
        var invalid = assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse(
            "{\"t\":\"action\",\"reqId\":\"a\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"search\",\"value\":{\"op\":\"craft\",\"name\":\"x\",\"count\":1}}"));
        assertEquals("invalid_value", invalid.reason());
        assertEquals("a", invalid.reqId());
    }

    @Test
    void parsesLayoutSet() {
        var set = assertInstanceOf(ClientMessages.LayoutSet.class, ClientMessages.parse("{\"t\":\"layout_set\",\"reqId\":\"l1\",\"layout\":" + LAYOUT + "}"));
        assertNull(set.error());
        assertNotNull(set.layout());
        var bad = assertInstanceOf(ClientMessages.LayoutSet.class, ClientMessages.parse("{\"t\":\"layout_set\",\"reqId\":\"l1\",\"layout\":{\"v\":3}}"));
        assertEquals("invalid_layout", bad.error());
        var missing = assertInstanceOf(ClientMessages.LayoutSet.class, ClientMessages.parse("{\"t\":\"layout_set\",\"reqId\":\"l1\"}"));
        assertEquals("invalid_layout", missing.error());
        assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse("{\"t\":\"layout_set\",\"layout\":" + LAYOUT + "}"), "reqId required");
    }

    @Test
    void onlyLayoutSetMayExceedEightKilobytes() {
        // A valid layout of ~20 KB.
        var widgets = new StringBuilder();
        for (int i = 0; i < 400; i++) widgets.append(i == 0 ? "" : ",").append("\"").append(UPLINK).append("/w").append(i).append("\":{\"order\":").append(i).append("}");
        String big = "{\"v\":1,\"pages\":[],\"cards\":{},\"widgets\":{" + widgets + "}}";
        assertTrue(big.length() > ClientMessages.MAX_FRAME);
        var set = assertInstanceOf(ClientMessages.LayoutSet.class, ClientMessages.parse("{\"t\":\"layout_set\",\"reqId\":\"l\",\"layout\":" + big + "}"));
        assertNull(set.error());

        String padded = "{\"t\":\"query\",\"reqId\":\"q\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"s\",\"q\":\"\"," + " ".repeat(ClientMessages.MAX_FRAME) + "}";
        assertEquals("too_large", assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse(padded)).reason());
        assertEquals("too_large", assertInstanceOf(ClientMessages.Invalid.class,
            ClientMessages.parse("{\"t\":\"layout_set\",\"reqId\":\"l\"," + " ".repeat(ClientMessages.MAX_LAYOUT_FRAME) + "}")).reason());
        assertEquals("too_large", assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse("x".repeat(ClientMessages.MAX_FRAME + 10))).reason());
    }

    @Test
    void newMessagesAreNotPublic() {
        assertFalse(ClientMessages.isPublic(ClientMessages.parse("{\"t\":\"query\",\"reqId\":\"q\",\"uplinkId\":\"" + UPLINK + "\",\"id\":\"s\",\"q\":\"\"}")));
        assertFalse(ClientMessages.isPublic(ClientMessages.parse("{\"t\":\"layout_set\",\"reqId\":\"l\",\"layout\":" + LAYOUT + "}")));
    }
}
