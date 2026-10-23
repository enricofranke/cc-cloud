package ai.ezfox.cccloud.voxel;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.icons.PngCodec;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Block texture store (validation, stable tiles, atlas, palette decoration, persistence) and upload checks. */
class BlockTextureTest {
    static byte[] png(int argb) {
        var px = new int[256];
        Arrays.fill(px, argb);
        return PngCodec.encode(16, 16, px);
    }

    @Test
    void spritesBlocksAtlasAndDecoration(@TempDir Path dir) {
        var store = new BlockTextureStore();
        assertTrue(store.putSprite("minecraft:block/stone", png(0xFF777777)));
        assertTrue(store.putSprite("minecraft:block/grass_block_top", png(0xFF999999)));
        assertFalse(store.putSprite("minecraft:block/big", PngCodec.encode(32, 32, new int[1024])), "16×16 only");
        assertFalse(store.putSprite("Bad Name", png(0)), "id");
        assertFalse(store.putSprite("minecraft:block/x", new byte[]{1, 2, 3}), "not a PNG");
        assertEquals(0, store.tile("minecraft:block/stone"));
        assertEquals(1, store.tile("minecraft:block/grass_block_top"));
        assertFalse(store.putBlock("minecraft:dirt", "v", new String[]{"minecraft:block/dirt", null, null, null, null, null}, 0), "unknown sprite");
        assertTrue(store.putBlock("minecraft:stone", "minecraft@26.1.2", new String[]{"minecraft:block/stone", "minecraft:block/stone", "minecraft:block/stone",
            "minecraft:block/stone", "minecraft:block/stone", "minecraft:block/stone"}, 0));
        assertTrue(store.putBlock("minecraft:grass_block", "minecraft@26.1.2", new String[]{"minecraft:block/stone", "minecraft:block/grass_block_top", null,
            null, null, null}, 0b10));
        assertEquals(java.util.Set.of("minecraft:dirt"), store.outdated(Map.of("minecraft:stone", "minecraft@26.1.2", "minecraft:dirt", "minecraft@26.1.2")));
        assertEquals(java.util.Set.of("minecraft:stone"), store.outdated(Map.of("minecraft:stone", "minecraft@26.2")), "re-render after updates");

        var atlas = store.atlas();
        assertArrayEquals(new int[]{32, 16}, store.atlasSize());
        var px = PngCodec.decode(atlas, 32, 16);
        assertNotNull(px);
        assertEquals(0xFF777777, px[0]);
        assertEquals(0xFF999999, px[16], "tile 1 next to tile 0");

        var voxels = JsonParser.parseString("{\"palette\":[{\"id\":\"minecraft:stone\",\"color\":1,\"flags\":0},{\"id\":\"minecraft:grass_block\",\"color\":2,\"flags\":0},"
            + "{\"id\":\"minecraft:dirt\",\"color\":3,\"flags\":0}]}").getAsJsonObject();
        var missing = store.decorate(voxels);
        assertEquals(List.of("minecraft:dirt"), missing);
        var grass = voxels.getAsJsonArray("palette").get(1).getAsJsonObject();
        assertEquals("[0,1,-1,-1,-1,-1]", grass.get("tex").toString());
        assertEquals(2, grass.get("tint").getAsInt());
        assertFalse(voxels.getAsJsonArray("palette").get(2).getAsJsonObject().has("tex"), "fallback: map colour");
        assertEquals(store.rev(), voxels.getAsJsonObject("atlas").get("rev").getAsInt());
        assertEquals("/api/voxel-atlas/" + store.rev() + ".png", voxels.getAsJsonObject("atlas").get("url").getAsString());

        var file = dir.resolve("block_textures.json");
        assertDoesNotThrow(() -> store.save(file));
        var loaded = BlockTextureStore.load(file);
        assertEquals(store.tile("minecraft:block/grass_block_top"), loaded.tile("minecraft:block/grass_block_top"), "tiles are stable");
        assertArrayEquals(store.block("minecraft:grass_block").faces(), loaded.block("minecraft:grass_block").faces());
        assertTrue(BlockTextureStore.load(dir.resolve("missing.json")).atlas().length > 0, "empty store still serves an atlas");
    }

    @Test
    void uploadsOnlyForTheAssignedBatch() {
        var store = new BlockTextureStore();
        var client = UUID.randomUUID();
        var service = BlockTextureService.forTest(store, client, List.of("minecraft:stone", "minecraft:poppy"));
        String stone = "minecraft:block/stone";
        var upload = new BlockTextureService.UploadPayload(List.of(
            new BlockTextureService.BlockFaces("minecraft:stone", List.of(stone, stone, stone, stone, stone, stone), 0),
            new BlockTextureService.BlockFaces("minecraft:diamond_ore", List.of("minecraft:block/diamond_ore", "", "", "", "", ""), 0),
            new BlockTextureService.BlockFaces("minecraft:poppy", List.of(), 0)),
            List.of(new BlockTextureService.Sprite(stone, png(0xFF777777)), new BlockTextureService.Sprite("minecraft:block/diamond_ore", png(0xFF00FFFF)),
                new BlockTextureService.Sprite("minecraft:block/unused", png(0xFF000000))));
        assertEquals(1, service.accept(client, upload));
        assertNotNull(store.block("minecraft:stone"));
        assertNull(store.block("minecraft:diamond_ore"), "not requested");
        assertFalse(store.hasSprite("minecraft:block/diamond_ore"), "sprites of refused blocks are not stored");
        assertFalse(store.hasSprite("minecraft:block/unused"), "unreferenced sprites are not stored");
        assertTrue(store.isCurrent("minecraft:poppy", "test@1"), "cannot render → remembered");
        assertEquals(0, service.accept(UUID.randomUUID(), upload), "other clients have no batch");
    }

    @Test
    void storedSpritesAreReplacedOnlyByTrustedPlayers() {
        var store = new BlockTextureStore();
        String stone = "minecraft:block/stone";
        store.putSprite(stone, png(0xFF777777));
        var client = UUID.randomUUID();
        var service = BlockTextureService.forTest(store, client, List.of("minecraft:stone"));
        var upload = new BlockTextureService.UploadPayload(List.of(
            new BlockTextureService.BlockFaces("minecraft:stone", List.of(stone, stone, stone, stone, stone, stone), 0)),
            List.of(new BlockTextureService.Sprite(stone, png(0xFFFF00FF))));
        assertEquals(1, service.accept(client, upload));
        var px = PngCodec.decode(store.atlas(), 16, 16);
        assertEquals(0xFF777777, px[0], "an untrusted client cannot repaint a stored sprite");
    }
}
