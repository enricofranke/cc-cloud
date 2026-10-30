package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** v0.10 messages: scene_set / scene_delete and production requests are parsed strictly. */
class ClientMessagesV3Test {
    static final String U = UUID.randomUUID().toString();

    @Test
    void sceneCommands() {
        var set = assertInstanceOf(ClientMessages.SceneCommand.class, ClientMessages.parse("{\"t\":\"scene_set\",\"reqId\":\"r1\",\"scene\":{\"name\":\"x\"}}"));
        assertEquals("scene_set", set.op());
        assertNotNull(set.scene());
        var delete = assertInstanceOf(ClientMessages.SceneCommand.class, ClientMessages.parse("{\"t\":\"scene_delete\",\"reqId\":\"r1\",\"id\":\"0a1b2c3d\"}"));
        assertEquals("0a1b2c3d", delete.id());
        assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse("{\"t\":\"scene_set\",\"reqId\":\"r1\",\"scene\":[]}"));
        assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse("{\"t\":\"scene_set\",\"scene\":{}}"), "reqId required");
        assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse("{\"t\":\"scene_delete\",\"reqId\":\"r1\",\"id\":\"../x\"}"));
    }

    @Test
    void productionRequests() {
        var overview = assertInstanceOf(ClientMessages.Production.class, ClientMessages.parse("{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"overview\"}"));
        assertEquals("overview", overview.op());
        var item = assertInstanceOf(ClientMessages.Production.class,
            ClientMessages.parse("{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"item\",\"uplinkId\":\"" + U + "\",\"name\":\"minecraft:iron_ingot\"}"));
        assertEquals("minecraft:iron_ingot", item.name());
        assertInstanceOf(ClientMessages.Production.class, ClientMessages.parse("{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"pin\",\"kind\":\"energy\"}"));
        assertInstanceOf(ClientMessages.Production.class, ClientMessages.parse("{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"pin\",\"kind\":\"storage\",\"uplinkId\":\"" + U + "\"}"));
        assertInstanceOf(ClientMessages.Production.class, ClientMessages.parse("{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"unpin\",\"id\":\"0a1b2c3d\"}"));
        for (var bad : new String[]{
            "{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"drop\"}",
            "{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"item\",\"uplinkId\":\"" + U + "\"}",
            "{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"item\",\"uplinkId\":\"x\",\"name\":\"minecraft:iron_ingot\"}",
            "{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"item\",\"uplinkId\":\"" + U + "\",\"name\":\"../../etc\"}",
            "{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"pin\",\"kind\":\"item\",\"uplinkId\":\"" + U + "\"}",
            "{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"pin\",\"kind\":\"weather\"}",
            "{\"t\":\"production\",\"reqId\":\"p\",\"op\":\"unpin\",\"id\":\"zz\"}"}) {
            assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse(bad), bad);
        }
        assertInstanceOf(ClientMessages.Invalid.class, ClientMessages.parse("{\"t\":\"production\",\"op\":\"overview\"}"), "reqId required");
    }
}
