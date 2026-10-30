package ai.ezfox.cccloud.icons;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Server-side checks of uploaded icons. */
class IconServiceTest {
    @TempDir
    Path dir;

    private static byte[] icon() {
        var pixels = new int[32 * 32];
        java.util.Arrays.fill(pixels, 0xFF44AA22);
        return PngCodec.encode(32, 32, pixels);
    }

    private IconService service() {
        var items = new LinkedHashMap<String, String>();
        for (var id : List.of("minecraft:a", "minecraft:b", "minecraft:c", "ae2:d")) items.put(id, id.substring(0, id.indexOf(':')) + "@1");
        return IconService.forTest(dir, items);
    }

    @Test
    void acceptsOnlyAssignedValidIcons() {
        var service = service();
        var client = UUID.randomUUID();
        var batch = service.assignForTest(client, 3);
        assertEquals(List.of("minecraft:a", "minecraft:b", "minecraft:c"), batch);
        int ok = service.accept(client, List.of(
            new IconService.Icon("minecraft:a", icon()),
            new IconService.Icon("ae2:d", icon()),                    // not in this client's batch
            new IconService.Icon("minecraft:b", new byte[]{1, 2, 3}), // not a valid PNG
            new IconService.Icon("minecraft:c", new byte[0])));      // "cannot render"
        assertEquals(1, ok);
        assertNotNull(service.store().get("minecraft:a"));
        assertNull(service.store().get("ae2:d"), "unassigned uploads are ignored");
        assertNull(service.store().get("minecraft:b"), "invalid PNGs are rejected");
        assertTrue(service.store().isCurrent("minecraft:b", "minecraft@1"), "rejected icons are not requested again for this version");
        assertTrue(service.store().isCurrent("minecraft:c", "minecraft@1"), "cannot-render is remembered");
        assertEquals(2, service.rejectedCount());
        assertEquals(1, service.status().queued(), "ae2:d is still queued");
    }

    @Test
    void uploadsWithoutAnAssignmentAreIgnored() {
        var service = service();
        assertEquals(0, service.accept(UUID.randomUUID(), List.of(new IconService.Icon("minecraft:a", icon()))));
        assertNull(service.store().get("minecraft:a"));
        var client = UUID.randomUUID();
        service.assignForTest(client, 1);
        assertEquals(0, service.accept(UUID.randomUUID(), List.of(new IconService.Icon("minecraft:a", icon()))),
            "another client cannot fill someone else's batch");
        assertEquals(1, service.accept(client, List.of(new IconService.Icon("minecraft:a", icon()))));
        assertEquals(0, service.accept(client, List.of(new IconService.Icon("minecraft:a", icon()))), "a batch is accepted only once");
    }

    @Test
    void rebuildRequeuesEverything() throws Exception {
        var service = service();
        var client = UUID.randomUUID();
        service.assignForTest(client, 4);
        for (var id : List.of("minecraft:a", "minecraft:b", "minecraft:c", "ae2:d")) service.accept(client, List.of(new IconService.Icon(id, icon())));
        assertEquals(0, service.status().queued());
        assertEquals(4, service.status().done());
        service.rebuild();
        assertEquals(4, service.status().queued());
        assertNotNull(service.store().get("minecraft:a"), "old icons stay available until replaced");
        service.clear();
        assertNull(service.store().get("minecraft:a"));
    }

    @Test
    void serverRendersStayQueuedForClientsAndAnyClientMayReplaceThem() {
        var items = new LinkedHashMap<String, String>();
        items.put("ae2:a", "ae2@1");
        items.put("ae2:b", "ae2@1");
        // The server renderer ran before (last start): both have server icons.
        try (var store = new IconStore(dir)) {
            var server = new int[32 * 32];
            java.util.Arrays.fill(server, 0xFF00FF00);
            store.putServer("ae2:a", "ae2@1", PngCodec.encode(32, 32, server));
            store.putServer("ae2:b", "ae2@1", PngCodec.encode(32, 32, server));
            store.flush();
        } catch (Exception e) {
            fail(e);
        }
        var service = IconService.forTest(dir, items);
        assertEquals(2, service.status().queued(), "server renders are still handed to game clients");
        var client = UUID.randomUUID(); // a player without any role on a mainframe
        assertEquals(List.of("ae2:a", "ae2:b"), service.assignForTest(client, 2));
        assertEquals(2, service.accept(client, List.of(new IconService.Icon("ae2:a", icon()), new IconService.Icon("ae2:b", icon()))),
            "replacing a server render needs no role (first-writer-wins is only for client renders)");
        assertEquals(IconStore.Source.CLIENT, service.store().source("ae2:a"));
        assertArrayEquals(icon(), service.store().get("ae2:a"));
        assertEquals(new IconService.Sources(2, 0, 0), service.sources());
    }

    /**
     * Enrico's hosted server: icons were rendered again after every start. A restart after a kill (no stopping event,
     * no compaction) must hand out nothing that was uploaded before the last checkpoint.
     */
    @Test
    void aRestartAfterAKillRendersNothingAgain() throws Exception {
        var items = new LinkedHashMap<String, String>();
        for (var id : List.of("minecraft:a", "minecraft:b", "minecraft:c", "ae2:d")) items.put(id, id.substring(0, id.indexOf(':')) + "@1");
        var first = IconService.forTest(dir, items);
        var client = UUID.randomUUID();
        assertEquals(4, first.assignForTest(client, 4).size());
        for (var id : items.keySet()) first.accept(client, List.of(new IconService.Icon(id, icon())));
        first.store().checkpoint(); // what the io thread does within a second of the upload
        // killed here: no onStopping, no flush, no close

        var second = IconService.forTest(dir, items);
        assertEquals(0, second.status().queued(), "nothing is queued for rendering again");
        assertEquals(4, second.status().done());
        assertArrayEquals(icon(), second.store().get("ae2:d"));

        // Only a real mod update re-renders, and only that mod's items.
        var updated = new LinkedHashMap<>(items);
        updated.put("ae2:d", "ae2@2");
        assertEquals(1, IconService.forTest(dir, updated).status().queued());
    }

    @Test
    void aUsedUpQuotaPausesTheClientAndLosesNothing() {
        var service = service();
        var client = UUID.randomUUID();
        service.guard.setPerHour(2);
        assertEquals(List.of("minecraft:a", "minecraft:b"), service.assignForTest(client, 4), "never more than the quota left");
        assertEquals(2, service.accept(client, List.of(new IconService.Icon("minecraft:a", icon()), new IconService.Icon("minecraft:b", icon()))));
        assertEquals(List.of(), service.assignForTest(client, 4), "quota used up: no batch, so no wasted renders");
        assertEquals(2, service.status().queued(), "the rest waits for another player or the next hour");

        // The quota shrank while a batch was out (e.g. a config change): what is over goes back to the queue.
        var other = UUID.randomUUID();
        service.guard.setPerHour(3);
        assertEquals(List.of("minecraft:c", "ae2:d"), service.assignForTest(other, 4));
        service.guard.setPerHour(1);
        assertEquals(1, service.accept(other, List.of(new IconService.Icon("minecraft:c", icon()), new IconService.Icon("ae2:d", icon()))));
        assertEquals(1, service.status().queued(), "ae2:d was not dropped");
        assertEquals(3, service.status().done());
    }

    @Test
    void theQuotaFitsOneFullRenderOfThePack() {
        assertEquals(4096, IconService.quotaFor(100));
        assertEquals(45_942, IconService.quotaFor(36_754), "ATM11: all items in one hour, plus retries");
    }

    @Test
    void versionKeysAreStableAndOnlyFollowTheDeclaredModVersion() {
        java.util.function.Function<String, java.util.Optional<String>> mods = ns -> switch (ns) {
            case "ae2" -> java.util.Optional.of("26.1.13-beta");
            case "cccloud" -> java.util.Optional.of("1.0.0-beta.1");
            default -> java.util.Optional.empty();
        };
        assertEquals("minecraft@26.1.2", IconService.versionKey("minecraft", "26.1.2", mods));
        assertEquals("ae2@26.1.13-beta", IconService.versionKey("ae2", "26.1.2", mods));
        assertEquals("c@?", IconService.versionKey("c", "26.1.2", mods), "a namespace that is no mod id");
        for (int i = 0; i < 3; i++) {
            assertEquals(IconService.versionKey("ae2", "26.1.2", mods), IconService.versionKey("ae2", "26.1.2", mods), "the same on every start");
        }
        assertNotEquals(IconService.versionKey("ae2", "26.1.2", mods), IconService.versionKey("ae2", "26.1.2", ns -> java.util.Optional.of("26.1.14")),
            "a mod update changes it");
        assertEquals(IconService.versionKey("ae2", "26.1.2", mods), IconService.versionKey("ae2", "26.1.3", mods), "a game update does not touch mods");
    }
}
