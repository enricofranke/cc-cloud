package ai.ezfox.cccloud.icons;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IconStoreTest {
    @TempDir
    Path dir;

    private static byte[] icon(int color) {
        var pixels = new int[32 * 32];
        java.util.Arrays.fill(pixels, color);
        return PngCodec.encode(32, 32, pixels);
    }

    @Test
    void validatesIds() {
        for (var good : new String[]{"minecraft:iron_ingot", "ae2:item_storage_cell_1k", "mod.x:a/b-c_d.e"}) assertTrue(IconStore.isValidId(good), good);
        for (var bad : new String[]{null, "", "iron", ":x", "minecraft:", "Minecraft:iron", "minecraft:../secret", "minecraft:a/../../b",
            "../x:y", "minecraft:a\\b", "minecraft:/abs", "minecraft:a//b", "minecraft:x.png%00", "minecraft:%2e%2e/x", "minecraft:a b"}) {
            assertFalse(IconStore.isValidId(bad), String.valueOf(bad));
        }
    }

    @Test
    void storesFlushesAndReloads() throws Exception {
        var a = icon(0xFFFF0000);
        var b = icon(0xFF00FF00);
        try (var store = new IconStore(dir)) {
            store.put("minecraft:iron_ingot", "minecraft@26.1.2", a);
            store.put("ae2:logic_processor", "ae2@26.1.13-beta", b);
            store.markFailed("weird:thing", "weird@1");
            assertArrayEquals(a, store.get("minecraft:iron_ingot"), "served before flush");
            assertNull(store.get("weird:thing"));
            store.flush();
            assertTrue(Files.isRegularFile(dir.resolve("minecraft.zip")));
            assertTrue(Files.isRegularFile(dir.resolve("ae2.zip")));
            assertEquals(2, store.status().stored());
            assertEquals(1, store.status().failed());
        }
        try (var store = new IconStore(dir)) {
            assertArrayEquals(a, store.get("minecraft:iron_ingot"), "read back from the zip");
            assertEquals(1, store.misses());
            assertArrayEquals(a, store.get("minecraft:iron_ingot"));
            assertEquals(1, store.hits(), "second read is an LRU hit");
            assertTrue(store.isCurrent("ae2:logic_processor", "ae2@26.1.13-beta"));
            assertFalse(store.isCurrent("ae2:logic_processor", "ae2@26.2.0"), "a mod update makes it outdated");
            assertTrue(store.isCurrent("weird:thing", "weird@1"), "failed items are not re-queued for the same version");
            var outdated = store.outdated(Map.of("minecraft:iron_ingot", "minecraft@26.1.2", "ae2:logic_processor", "ae2@27", "new:item", "new@1"));
            assertEquals(java.util.Set.of("ae2:logic_processor", "new:item"), outdated);
            assertNull(store.get("minecraft:../manifest"), "traversal ids never touch the disk");
            assertNull(store.get("minecraft:gold_ingot"), "unknown → null (404)");
        }
    }

    @Test
    void replacingAnIconRewritesOnlyThatEntry() throws Exception {
        try (var store = new IconStore(dir)) {
            store.put("minecraft:a", "v1", icon(0xFF111111));
            store.put("minecraft:b", "v1", icon(0xFF222222));
            store.flush();
            store.put("minecraft:a", "v2", icon(0xFF333333));
            store.flush();
        }
        try (var store = new IconStore(dir); var zip = new java.util.zip.ZipFile(dir.resolve("minecraft.zip").toFile())) {
            assertEquals(2, zip.size(), "no duplicate entries");
            assertArrayEquals(icon(0xFF333333), store.get("minecraft:a"));
            assertArrayEquals(icon(0xFF222222), store.get("minecraft:b"));
        }
    }

    @Test
    void clearRemovesEverything() throws Exception {
        try (var store = new IconStore(dir)) {
            store.put("minecraft:a", "v1", icon(0xFF111111));
            store.flush();
            store.clear();
            assertNull(store.get("minecraft:a"));
            assertEquals(0, store.status().stored());
            assertFalse(Files.exists(dir.resolve("minecraft.zip")));
            assertFalse(Files.exists(dir.resolve("manifest.json")));
        }
    }
}
