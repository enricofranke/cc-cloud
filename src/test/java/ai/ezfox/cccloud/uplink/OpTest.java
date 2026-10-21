package ai.ezfox.cccloud.uplink;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class OpTest {
    private static Op op(String json) {
        return Op.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void parsesAllOperations() {
        assertEquals(new Op.Craft("ae2:logic_processor", 64), op("{\"op\":\"craft\",\"name\":\"ae2:logic_processor\",\"count\":64}"));
        assertEquals(new Op.WatchAdd("minecraft:iron_ingot", 0), op("{\"op\":\"add\",\"name\":\"minecraft:iron_ingot\"}"));
        assertEquals(new Op.WatchAdd("minecraft:iron_ingot", 5), op("{\"op\":\"add\",\"name\":\"minecraft:iron_ingot\",\"min\":5}"));
        assertEquals(new Op.WatchRemove("minecraft:iron_ingot"), op("{\"op\":\"remove\",\"name\":\"minecraft:iron_ingot\"}"));
        assertEquals(new Op.WatchSetMin("mekanism:ingot_osmium", 10), op("{\"op\":\"set_min\",\"name\":\"mekanism:ingot_osmium\",\"min\":10}"));
    }

    @Test
    void rejectsBadOperations() {
        for (var bad : new String[]{
            "{}",
            "{\"op\":\"explode\",\"name\":\"a:b\"}",
            "{\"op\":\"craft\",\"name\":\"a:b\"}",
            "{\"op\":\"craft\",\"name\":\"a:b\",\"count\":0}",
            "{\"op\":\"craft\",\"name\":\"a:b\",\"count\":1.5}",
            "{\"op\":\"craft\",\"name\":\"a:b\",\"count\":\"5\"}",
            "{\"op\":\"craft\",\"name\":\"a:b\",\"count\":3000000000}",
            "{\"op\":\"craft\",\"name\":\"a:b\",\"count\":1,\"extra\":true}",
            "{\"op\":\"craft\",\"name\":\"no_namespace\",\"count\":1}",
            "{\"op\":\"craft\",\"name\":\"A:B\",\"count\":1}",
            "{\"op\":\"craft\",\"name\":\"<script>:x\",\"count\":1}",
            "{\"op\":\"add\",\"name\":\"a:b\",\"min\":-1}",
            "{\"op\":\"set_min\",\"name\":\"a:b\"}",
            "{\"op\":\"remove\",\"name\":\"a:b\",\"min\":1}",
            "{\"op\":\"add\",\"name\":\"" + "a".repeat(65) + ":b\"}",
        }) {
            assertNull(op(bad), bad);
        }
    }
}
