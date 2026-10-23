package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RedstoneOverrideMessageTest {
    @Test
    void parses() {
        var m = assertInstanceOf(ClientMessages.RedstoneOverride.class,
            ClientMessages.parse("{\"t\":\"rs_override\",\"reqId\":\"r1\",\"channel\":\"door\",\"level\":15,\"seconds\":30}"));
        assertEquals("door", m.channel());
        assertEquals(15, m.level());
        assertEquals(30, m.seconds());
        var clear = assertInstanceOf(ClientMessages.RedstoneOverride.class,
            ClientMessages.parse("{\"t\":\"rs_override\",\"reqId\":\"r2\",\"channel\":\"door\",\"level\":null}"));
        assertNull(clear.level());
        assertNull(clear.seconds(), "until reset");
    }

    @Test
    void rejectsBadValues() {
        for (var bad : new String[]{
            "{\"t\":\"rs_override\",\"reqId\":\"r\",\"channel\":\"door\",\"level\":16}",
            "{\"t\":\"rs_override\",\"reqId\":\"r\",\"channel\":\"door\",\"level\":1.5}",
            "{\"t\":\"rs_override\",\"reqId\":\"r\",\"channel\":\"door\",\"level\":\"5\"}",
            "{\"t\":\"rs_override\",\"reqId\":\"r\",\"channel\":\"bad name\",\"level\":5}",
            "{\"t\":\"rs_override\",\"reqId\":\"r\",\"channel\":\"door\",\"level\":5,\"seconds\":0}",
            "{\"t\":\"rs_override\",\"channel\":\"door\",\"level\":5}"}) {
            assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse(bad), bad);
        }
    }
}
