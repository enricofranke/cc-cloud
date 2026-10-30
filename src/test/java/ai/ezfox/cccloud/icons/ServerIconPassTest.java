package ai.ezfox.cccloud.icons;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A server render pass over a small item list: what it draws, what it leaves alone, and stopping. */
class ServerIconPassTest {
    @TempDir
    Path dir;

    private final Map<String, byte[]> files = new HashMap<>();

    private void json(String path, String json) {
        files.put(path, json.replace('\'', '"').getBytes(StandardCharsets.UTF_8));
    }

    private void items() {
        var px = new int[16 * 16];
        java.util.Arrays.fill(px, 0xFF336699);
        files.put("assets/t/textures/item/flat.png", PngCodec.encode(16, 16, px));
        files.put("assets/t/textures/block/stone.png", PngCodec.encode(16, 16, px));
        json("assets/t/items/flat.json", "{'model':{'type':'minecraft:model','model':'t:item/flat'}}");
        json("assets/t/models/item/flat.json", "{'parent':'minecraft:item/generated','textures':{'layer0':'t:item/flat'}}");
        json("assets/t/items/cube.json", "{'model':{'type':'minecraft:model','model':'t:block/cube'}}");
        json("assets/t/models/block/cube.json", "{'parent':'minecraft:block/cube_all','textures':{'all':'t:block/stone'}}");
        json("assets/t/items/chest.json", "{'model':{'type':'minecraft:special','base':'t:item/flat','model':{'type':'minecraft:chest'}}}");
    }

    private static Map<String, String> wanted(String... ids) {
        var map = new LinkedHashMap<String, String>();
        for (var id : ids) map.put(id, "t@1");
        return map;
    }

    @Test
    void drawsFlatAndCubeIconsAndLeavesTheRest() throws Exception {
        items();
        try (var store = new IconStore(dir)) {
            var pass = new ServerIconPass(wanted("t:flat", "t:cube", "t:chest"), store, files::get, false);
            pass.run();
            var p = pass.progress();
            assertEquals(ServerIconPass.State.DONE, p.state());
            assertEquals(3, p.total());
            assertEquals(1, p.flat());
            assertEquals(1, p.cube());
            assertEquals(1, p.skipped());
            assertEquals(IconStore.Source.SERVER, store.source("t:flat"));
            assertEquals(IconStore.Source.SERVER, store.source("t:cube"));
            assertNull(store.get("t:chest"), "special renderers wait for a game client");
            assertEquals("special renderer", pass.topReasons(1).getFirst().getKey());
            assertTrue(PngCodec.isValid(store.get("t:cube"), 32, 32));

            var again = new ServerIconPass(wanted("t:flat", "t:cube", "t:chest"), store, files::get, false);
            again.run();
            assertEquals(1, again.progress().total(), "only what is still missing is tried again");
        }
    }

    @Test
    void neverTouchesClientRendersButRedrawsOutdatedServerRenders() throws Exception {
        items();
        var client = new int[32 * 32];
        java.util.Arrays.fill(client, 0xFFFF00FF);
        byte[] clientPng = PngCodec.encode(32, 32, client);
        try (var store = new IconStore(dir)) {
            store.put("t:flat", "t@0", clientPng); // an outdated client render
            store.putServer("t:cube", "t@0", clientPng); // an outdated server render
            new ServerIconPass(wanted("t:flat", "t:cube"), store, files::get, false).run();
            assertArrayEquals(clientPng, store.get("t:flat"), "client renders stay, even outdated ones");
            assertEquals(IconStore.Source.CLIENT, store.source("t:flat"));
            assertFalse(java.util.Arrays.equals(clientPng, store.get("t:cube")), "the outdated server render was redrawn");

            var force = new ServerIconPass(wanted("t:flat", "t:cube"), store, files::get, true);
            force.run();
            assertEquals(1, force.progress().total(), "rebuild redraws current server renders, never client ones");
        }
    }

    @Test
    void stopsWhenCancelled() throws Exception {
        items();
        try (var store = new IconStore(dir)) {
            var pass = new ServerIconPass(wanted("t:flat", "t:cube"), store, files::get, false);
            pass.cancel();
            pass.run();
            assertEquals(ServerIconPass.State.CANCELLED, pass.progress().state());
            assertEquals(0, pass.progress().done());
            assertNull(store.get("t:flat"));
        }
    }
}
