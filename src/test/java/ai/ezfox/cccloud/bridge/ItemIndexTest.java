package ai.ezfox.cccloud.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ItemIndexTest {
    private final ItemIndex index = new ItemIndex(List.of(
        new ItemRow("minecraft:iron_ingot", "Iron Ingot", 1234, true),
        new ItemRow("minecraft:gold_ingot", "Gold Ingot", 50, false),
        new ItemRow("mekanism:ingot_osmium", "Osmium Ingot", 900, false),
        new ItemRow("ae2:logic_processor", "Logic Processor", 0, true)
    ), false);

    @Test
    void sortsByCountThenName() {
        var rows = index.search("", 10).rows();
        assertEquals(List.of("minecraft:iron_ingot", "mekanism:ingot_osmium", "minecraft:gold_ingot", "ae2:logic_processor"),
            rows.stream().map(ItemRow::name).toList());
    }

    @Test
    void matchesIdAndDisplayNameCaseInsensitive() {
        assertEquals(3, index.search("INGOT", 10).rows().size());
        assertEquals("mekanism:ingot_osmium", index.search("osmium", 10).rows().getFirst().name());
        assertEquals(1, index.search("logic proc", 10).rows().size(), "all words must match");
        assertEquals(2, index.search("@minecraft", 10).rows().size());
        assertEquals(1, index.search("@minecraft gold", 10).rows().size());
        assertTrue(index.search("diamond", 10).rows().isEmpty());
    }

    @Test
    void limitsAndTruncation() {
        var page = index.search("ingot", 2);
        assertEquals(2, page.rows().size());
        assertTrue(page.truncated());
        assertFalse(index.search("ingot", 3).truncated());
        assertNotNull(index.get("ae2:logic_processor"));
        assertTrue(index.get("ae2:logic_processor").craftable());
        assertNull(index.get("minecraft:dirt"));
    }

    @Test
    void rowsSerialiseForTheBrowser() {
        var json = index.get("minecraft:iron_ingot").toJson();
        assertEquals("{\"name\":\"minecraft:iron_ingot\",\"displayName\":\"Iron Ingot\",\"count\":1234,\"craftable\":true}", json.toString());
    }
}
