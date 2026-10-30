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
}
