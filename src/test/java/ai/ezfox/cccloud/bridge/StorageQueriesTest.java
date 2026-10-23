package ai.ezfox.cccloud.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Browse sorting, filters, favorites pinned on top, paging; the craftables list. */
class StorageQueriesTest {
    final ItemIndex index = new ItemIndex(List.of(
        new ItemRow("minecraft:iron_ingot", "Iron Ingot", 500, true),
        new ItemRow("minecraft:cobblestone", "Cobblestone", 9000, false),
        new ItemRow("ae2:certus_quartz_crystal", "Certus Quartz Crystal", 40, false),
        new ItemRow("minecraft:stick", "Stick", 0, true)), false);

    static List<String> names(com.google.gson.JsonObject page) {
        var list = new java.util.ArrayList<String>();
        for (var e : page.getAsJsonArray("rows")) list.add(e.getAsJsonObject().get("name").getAsString());
        return list;
    }

    @Test
    void sortsFiltersAndPins() {
        assertEquals(List.of("minecraft:cobblestone", "minecraft:iron_ingot", "ae2:certus_quartz_crystal", "minecraft:stick"),
            names(StorageQueries.browse(index, Set.of(), StorageQueries.Sort.COUNT, true, StorageQueries.Filter.ALL, null, 0, 100)));
        assertEquals(List.of("ae2:certus_quartz_crystal", "minecraft:cobblestone", "minecraft:iron_ingot", "minecraft:stick"),
            names(StorageQueries.browse(index, Set.of(), StorageQueries.Sort.NAME, false, StorageQueries.Filter.ALL, null, 0, 100)));
        assertEquals("ae2:certus_quartz_crystal",
            names(StorageQueries.browse(index, Set.of(), StorageQueries.Sort.MOD, false, StorageQueries.Filter.ALL, null, 0, 100)).getFirst());
        assertEquals(List.of("minecraft:stick", "minecraft:cobblestone", "minecraft:iron_ingot", "ae2:certus_quartz_crystal"),
            names(StorageQueries.browse(index, Set.of("minecraft:stick"), StorageQueries.Sort.COUNT, true, StorageQueries.Filter.ALL, null, 0, 100)),
            "favorites first");
        assertEquals(List.of("minecraft:iron_ingot", "minecraft:stick"),
            names(StorageQueries.browse(index, Set.of(), StorageQueries.Sort.COUNT, true, StorageQueries.Filter.CRAFTABLE, null, 0, 100)));
        assertEquals(List.of("minecraft:iron_ingot"),
            names(StorageQueries.browse(index, Set.of(), StorageQueries.Sort.COUNT, true, StorageQueries.Filter.ALL, "iron", 0, 100)));
        var page = StorageQueries.browse(index, Set.of(), StorageQueries.Sort.COUNT, true, StorageQueries.Filter.ALL, null, 1, 2);
        assertEquals(List.of("minecraft:iron_ingot", "ae2:certus_quartz_crystal"), names(page));
        assertEquals(4, page.get("total").getAsInt());
        assertEquals(List.of("minecraft:iron_ingot", "minecraft:stick"), names(StorageQueries.craftables(index, Set.of(), null, 0, 100)));
    }
}
