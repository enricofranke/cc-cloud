package ai.ezfox.cccloud.voxel;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

/** The binary batch format: layout, shared palette, statuses, the stage-2 hook, gzip and refusal of malformed data. */
class VoxelBatchTest {
    static final VoxelSection.Kind STONE = VoxelSectionTest.STONE;
    static final VoxelSection.Kind GLASS = VoxelSectionTest.GLASS;
    static final VoxelSection.Kind LAMP = new VoxelSection.Kind("minecraft:sea_lantern", 0xFFDCF5FF, VoxelSection.EMISSIVE);

    /** A section with a stone floor (y=0), glass at (3,1,3) and a lamp at (8,1,8); everything above is air. */
    static VoxelSection.Encoded room(boolean lamp) {
        return VoxelSection.encode(new VoxelSection.Lookup() {
            @Override
            public VoxelSection.Kind at(int x, int y, int z) {
                if (y < 0) return STONE;
                if (y == 0) return STONE;
                if (y == 1 && x == 3 && z == 3) return GLASS;
                if (lamp && y == 1 && x == 8 && z == 8) return LAMP;
                return null;
            }

            @Override
            public boolean known(int x, int y, int z) {
                return true;
            }
        });
    }

    @Test
    void layoutAndRoundTrip() {
        var a = room(true);
        var b = room(false);
        byte[] bytes = VoxelBatch.write("minecraft:overworld", List.of(
            new VoxelBatch.Section(1, -4, 2, VoxelBatch.Status.OK, a),
            new VoxelBatch.Section(2, -4, 2, VoxelBatch.Status.SAME, b),
            new VoxelBatch.Section(3, -4, 2, VoxelBatch.Status.EMPTY, null),
            new VoxelBatch.Section(9, -4, 2, VoxelBatch.Status.OUT_OF_RANGE, null),
            new VoxelBatch.Section(4, -4, 2, VoxelBatch.Status.CLAIMED, null),
            new VoxelBatch.Section(5, -4, 2, VoxelBatch.Status.OK, b)), null);
        // magic, version, u32 BE header length, header, runs
        assertArrayEquals(new byte[]{'C', 'C', 'V', 'B', 1}, java.util.Arrays.copyOf(bytes, 5));
        int len = ((bytes[5] & 0xFF) << 24) | ((bytes[6] & 0xFF) << 16) | ((bytes[7] & 0xFF) << 8) | (bytes[8] & 0xFF);
        var header = JsonParser.parseString(new String(bytes, 9, len, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(9 + len + a.runs().length + b.runs().length, bytes.length, "runs of the two ok sections follow the header");
        JsonArray palette = header.getAsJsonArray("palette");
        assertEquals(3, palette.size(), "stone and glass are shared between the sections: " + palette);
        var same = header.getAsJsonArray("sections").get(1).getAsJsonObject();
        assertFalse(same.has("l") || same.has("n"), "same sections carry no runs: " + same);
        assertEquals(b.hashHex(), same.get("h").getAsString());

        var read = VoxelBatch.read(bytes);
        assertEquals(6, read.size());
        var first = read.getFirst();
        assertEquals(VoxelBatch.Status.OK, first.status());
        assertEquals(a.hashHex(), first.hash());
        assertEquals(a.exposed(), first.exposed());
        int lampIndex = first.cells()[VoxelSection.index(8, 1, 8)];
        assertEquals("minecraft:sea_lantern", first.palette().get(lampIndex - 1).get("id").getAsString());
        assertEquals(VoxelBatch.Status.SAME, read.get(1).status());
        assertEquals(2, read.get(1).palette().size(), "same sections keep their palette (fresh colours/textures)");
        assertNull(read.get(1).cells());
        assertEquals(List.of(VoxelBatch.Status.EMPTY, VoxelBatch.Status.OUT_OF_RANGE, VoxelBatch.Status.CLAIMED),
            read.subList(2, 5).stream().map(VoxelBatch.Decoded::status).toList());
        assertEquals(9, read.get(3).cx());
        assertArrayEquals(VoxelSection.decode(b.runs()), read.get(5).cells(), "the last section decodes to its own cells");
    }

    @Test
    void decorateHookSeesThePalette() {
        byte[] bytes = VoxelBatch.write("minecraft:overworld", List.of(new VoxelBatch.Section(0, 0, 0, VoxelBatch.Status.OK, room(true))), header -> {
            for (var p : header.getAsJsonArray("palette")) {
                var e = p.getAsJsonObject();
                if (e.get("id").getAsString().equals("minecraft:stone")) {
                    var tex = new JsonArray();
                    for (int i = 0; i < 6; i++) tex.add(7);
                    e.add("tex", tex);
                }
            }
            header.addProperty("atlas", "x");
        });
        var read = VoxelBatch.read(bytes);
        assertTrue(read.getFirst().palette().stream().anyMatch(p -> p.has("tex")), "stage-2 fields travel in the palette");
    }

    @Test
    void gzipRoundTrip() throws Exception {
        byte[] raw = VoxelBatch.write("minecraft:overworld", List.of(new VoxelBatch.Section(0, 0, 0, VoxelBatch.Status.OK, room(true))), null);
        byte[] gz = VoxelBatch.gzip(raw);
        try (var in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            assertArrayEquals(raw, in.readAllBytes());
        }
    }

    @Test
    void malformedBatchesAreRefused() {
        byte[] ok = VoxelBatch.write("minecraft:overworld", List.of(new VoxelBatch.Section(0, 0, 0, VoxelBatch.Status.OK, room(true))), null);
        assertThrows(IllegalArgumentException.class, () -> VoxelBatch.read(java.util.Arrays.copyOf(ok, ok.length - 1)), "truncated runs");
        var trailing = java.util.Arrays.copyOf(ok, ok.length + 1);
        assertThrows(IllegalArgumentException.class, () -> VoxelBatch.read(trailing), "trailing bytes");
        var badMagic = ok.clone();
        badMagic[0] = 'X';
        assertThrows(IllegalArgumentException.class, () -> VoxelBatch.read(badMagic));
        var badVersion = ok.clone();
        badVersion[4] = 2;
        assertThrows(IllegalArgumentException.class, () -> VoxelBatch.read(badVersion));
    }

    @Test
    void hashFollowsTheContent() {
        assertEquals(room(true).hash(), room(true).hash(), "deterministic");
        assertNotEquals(room(true).hash(), room(false).hash(), "a changed block changes the hash");
        assertEquals(16, room(true).hashHex().length());
    }
}
