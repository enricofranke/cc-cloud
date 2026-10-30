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

    @Test
    void serverRendersNeverReplaceClientRenders() throws Exception {
        var client = icon(0xFF0000FF);
        var server = icon(0xFF00FF00);
        try (var store = new IconStore(dir)) {
            assertTrue(store.wantsServerRender("ae2:a", "ae2@1"), "nothing stored yet");
            assertTrue(store.putServer("ae2:a", "ae2@1", server));
            assertEquals(IconStore.Source.SERVER, store.source("ae2:a"));
            assertFalse(store.wantsServerRender("ae2:a", "ae2@1"), "current server render");
            assertTrue(store.wantsServerRender("ae2:a", "ae2@2"), "a mod update re-renders it");
            assertFalse(store.isCurrent("ae2:a", "ae2@1"), "server renders stay queued for game clients");

            store.put("ae2:a", "ae2@1", client);
            assertEquals(IconStore.Source.CLIENT, store.source("ae2:a"), "a client render replaces the server render");
            assertArrayEquals(client, store.get("ae2:a"));
            assertFalse(store.putServer("ae2:a", "ae2@1", server), "never the other way round");
            assertFalse(store.wantsServerRender("ae2:a", "ae2@2"), "not even when the client render is outdated");
            assertFalse(store.putServer("ae2:a", "ae2@2", server));
            assertArrayEquals(client, store.get("ae2:a"));

            store.markFailed("ae2:b", "ae2@1");
            assertFalse(store.wantsServerRender("ae2:b", "ae2@1"), "a client could not render this version: leave it");
            assertFalse(store.putServer("ae2:b", "ae2@1", server));
            assertTrue(store.wantsServerRender("ae2:b", "ae2@2"), "an old failure does not block a new version");
            assertTrue(store.putServer("ae2:b", "ae2@2", server));

            store.putServer("ae2:c", "ae2@1", server);
            store.markFailed("ae2:c", "ae2@1");
            assertEquals(IconStore.Source.SERVER, store.source("ae2:c"), "a failing client does not wipe a server render");
            assertArrayEquals(server, store.get("ae2:c"));

            var status = store.status();
            assertEquals(1, status.client());
            assertEquals(2, status.server());
            assertEquals(3, status.stored());
            store.flush();
        }
        try (var store = new IconStore(dir)) {
            assertEquals(IconStore.Source.CLIENT, store.source("ae2:a"), "sources survive a restart");
            assertEquals(IconStore.Source.SERVER, store.source("ae2:b"));
            assertTrue(store.isCurrent("ae2:a", "ae2@1"));
            assertFalse(store.wantsServerRender("ae2:b", "ae2@2"));
            var manifest = Files.readString(dir.resolve("manifest.json"));
            assertTrue(manifest.contains("\"version\":2"), manifest);
            assertTrue(manifest.contains("\"ae2:b\":{\"version\":\"ae2@2\",\"source\":\"server\"}"), manifest);
            assertTrue(manifest.contains("\"ae2:a\":{\"version\":\"ae2@1\",\"source\":\"client\"}"), manifest);
        }
    }

    @Test
    void readsTheFirstManifestFormatAsClientRenders() throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("manifest.json"), "{\"version\":1,\"items\":{\"minecraft:a\":\"minecraft@26.1.2\",\"weird:b\":\"!weird@1\"}}");
        try (var store = new IconStore(dir)) {
            assertEquals(IconStore.Source.CLIENT, store.source("minecraft:a"));
            assertTrue(store.isCurrent("minecraft:a", "minecraft@26.1.2"));
            assertTrue(store.isCurrent("weird:b", "weird@1"), "the failure marker");
            assertFalse(store.hasIcon("weird:b"));
            assertFalse(store.wantsServerRender("minecraft:a", "minecraft@26.1.2"));
            assertEquals(1, store.status().failed());
        }
    }

    /** A hosting panel kills the server: no flush, no close. What was checkpointed is all there after the restart. */
    @Test
    void survivesAKillWithoutCleanShutdown() throws Exception {
        var a = icon(0xFFAA0000);
        var b = icon(0xFF00AA00);
        var lost = icon(0xFF0000AA);
        var store = new IconStore(dir); // never flushed or closed: the process "dies"
        store.put("minecraft:stone", "minecraft@26.1.2", a);
        store.putServer("ae2:fluix", "ae2@1", b);
        store.markFailed("weird:thing", "weird@1");
        assertEquals(3, store.checkpoint(), "one journal segment with all three");
        store.put("ae2:fluix", "ae2@1", a); // a client render replaces the server render: second segment
        assertEquals(1, store.checkpoint());
        store.put("minecraft:dirt", "minecraft@26.1.2", lost); // after the last checkpoint: lost with the kill
        assertEquals(2, store.journalSegments());
        assertFalse(Files.exists(dir.resolve("manifest.json")), "never compacted");
        // The kill hit a compaction and a checkpoint halfway: their temp files stay behind.
        Files.writeString(dir.resolve("manifest.json.tmp"), "{\"version\":2,\"ite");
        Files.write(dir.resolve("journal-000000000099.zip.tmp"), new byte[]{1, 2, 3});

        try (var restarted = new IconStore(dir)) {
            var wanted = Map.of("minecraft:stone", "minecraft@26.1.2", "ae2:fluix", "ae2@1", "weird:thing", "weird@1");
            assertEquals(java.util.Set.of(), restarted.outdated(wanted), "nothing is rendered again");
            assertArrayEquals(a, restarted.get("minecraft:stone"));
            assertArrayEquals(a, restarted.get("ae2:fluix"), "segments apply in order: the client render wins");
            assertEquals(IconStore.Source.CLIENT, restarted.source("ae2:fluix"));
            assertTrue(restarted.isCurrent("weird:thing", "weird@1"));
            assertNull(restarted.get("minecraft:dirt"), "only what came after the last checkpoint is lost");
            assertFalse(Files.exists(dir.resolve("manifest.json.tmp")), "half-written files are removed, never read");
            assertFalse(Files.exists(dir.resolve("journal-000000000099.zip.tmp")));

            restarted.flush(); // compaction: namespace zips + manifest, then the segments go
            assertEquals(0, restarted.journalSegments());
            assertTrue(Files.isRegularFile(dir.resolve("ae2.zip")));
        }
        try (var again = new IconStore(dir)) {
            assertArrayEquals(a, again.get("ae2:fluix"), "and from the compacted files");
            assertTrue(again.isCurrent("minecraft:stone", "minecraft@26.1.2"));
            again.put("minecraft:dirt", "minecraft@26.1.2", lost);
            again.checkpoint(); // numbering goes on after the deleted segments
        }
        try (var third = new IconStore(dir)) {
            assertArrayEquals(lost, third.get("minecraft:dirt"));
            assertArrayEquals(a, third.get("minecraft:stone"));
        }
    }

    @Test
    void aCheckpointAfterACompactionOnlyWritesWhatIsNew() throws Exception {
        try (var store = new IconStore(dir)) {
            store.put("minecraft:a", "v1", icon(0xFF111111));
            store.flush();
            assertEquals(0, store.checkpoint(), "everything is compacted already");
            assertFalse(store.hasUnjournaled());
            store.put("minecraft:b", "v1", icon(0xFF222222));
            assertTrue(store.hasUnjournaled());
            assertEquals(1, store.checkpoint());
            assertEquals(0, store.checkpoint(), "nothing twice");
        }
    }
}
