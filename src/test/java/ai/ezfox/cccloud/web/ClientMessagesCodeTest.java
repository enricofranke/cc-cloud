package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Cloud Scripts requests ({@code {"t":"code"}}): strict fields, errors that keep their reqId, reads vs writes. */
class ClientMessagesCodeTest {
    private static ClientMessages.CodeCommand code(String json) {
        return assertInstanceOf(ClientMessages.CodeCommand.class, ClientMessages.parse(json), json);
    }

    @Test
    void wellFormedRequests() {
        var list = code("{\"t\":\"code\",\"reqId\":\"r1\",\"op\":\"list\"}");
        assertNull(list.error());
        assertFalse(ClientMessages.isWrite(list), "listing only reads");
        var save = code("{\"t\":\"code\",\"reqId\":\"r2\",\"op\":\"save\",\"name\":\"Night\",\"source\":\"print(1)\"}");
        assertNull(save.error());
        assertNull(save.id(), "no id: a new script");
        assertTrue(ClientMessages.isWrite(save));
        var update = code("{\"t\":\"code\",\"reqId\":\"r3\",\"op\":\"save\",\"id\":\"0a1b2c3d\",\"name\":\"Night\",\"source\":\"x\",\"baseVersion\":4}");
        assertEquals(4, update.baseVersion());
        assertNull(code("{\"t\":\"code\",\"reqId\":\"r3\",\"op\":\"save\",\"id\":\"0a1b2c3d\",\"name\":\"N\",\"source\":\"x\",\"baseVersion\":null}").error());
        var start = code("{\"t\":\"code\",\"reqId\":\"r4\",\"op\":\"start\",\"id\":\"0a1b2c3d\"}");
        assertTrue(ClientMessages.isWrite(start));
        var check = code("{\"t\":\"code\",\"reqId\":\"r5\",\"op\":\"check\",\"source\":\"x\"}");
        assertFalse(ClientMessages.isWrite(check), "a check compiles but changes nothing");
        assertEquals(true, code("{\"t\":\"code\",\"reqId\":\"r6\",\"op\":\"autostart\",\"id\":\"0a1b2c3d\",\"enabled\":true}").enabled());
        assertEquals(2, code("{\"t\":\"code\",\"reqId\":\"r7\",\"op\":\"revert\",\"id\":\"0a1b2c3d\",\"version\":2}").version());
        for (var op : ClientMessages.CODE_OPS) {
            boolean read = ClientMessages.CodeCommand.READ_OPS.contains(op);
            assertEquals(!read, new ClientMessages.CodeCommand("r", op, "0a1b2c3d", null, null, null, null, null, null).writes(), op);
        }
    }

    @Test
    void malformedRequestsKeepTheirReqId() {
        String[][] cases = {
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"explode\"}", "invalid"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"save\",\"source\":\"x\"}", "invalid"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"check\"}", "invalid"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"start\"}", "invalid"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"rename\",\"id\":\"0a1b2c3d\"}", "invalid"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"autostart\",\"id\":\"0a1b2c3d\",\"enabled\":\"yes\"}", "invalid"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"version\",\"id\":\"0a1b2c3d\",\"version\":0}", "invalid"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"version\",\"id\":\"0a1b2c3d\",\"version\":\"2\"}", "invalid"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"list\",\"extra\":1}", "invalid"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"save\",\"name\":5,\"source\":\"x\"}", "invalid"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"save\",\"name\":\"" + "x".repeat(65) + "\",\"source\":\"x\"}", "invalid"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"start\",\"id\":\"../../x\"}", "unknown_script"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"start\",\"id\":\"0A1B2C3D\"}", "unknown_script"},
            {"{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"check\",\"source\":\"" + "x".repeat(ClientMessages.MAX_PROGRAM + 1) + "\"}", "too_large"},
        };
        for (var c : cases) {
            var m = code(c[0]);
            assertEquals("r", m.reqId(), c[0]);
            assertEquals(c[1], m.error(), c[0].length() > 200 ? c[0].substring(0, 200) : c[0]);
            assertFalse(m.writes(), "a malformed request never counts as a write");
        }
        assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse("{\"t\":\"code\",\"op\":\"list\"}"), "no reqId: nothing to answer to");
        var frame = ClientMessages.parse("{\"t\":\"code\",\"reqId\":\"r\",\"op\":\"check\",\"source\":\"" + "x".repeat(ClientMessages.MAX_CODE_FRAME) + "\"}");
        assertEquals("too_large", assertInstanceOf(ClientMessages.Invalid.class, frame).reason());
    }
}
