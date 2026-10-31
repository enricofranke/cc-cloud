package ai.ezfox.cccloud.voxel;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The last known view areas: the lookup of an unloading chunk never misses a chunk in an area and costs one map lookup
 * everywhere else, the exact section range is the rule of the batch route, and what is saved comes back.
 */
class VoxelAreasTest {
    private static final String DIM = "minecraft:overworld";

    @Test
    void aChunkFarFromEveryAreaIsOneLookup() {
        UUID mf = UUID.randomUUID();
        var index = new VoxelAreas.Index<>(Map.of(DIM, Map.of(mf, new double[][]{{100.5, 64, -20.5}})), 64);
        assertNotNull(index.at(DIM, 6, -2), "the node's own chunk");
        assertNull(index.at(DIM, 200, 200), "another part of the world");
        assertNull(index.at("minecraft:the_nether", 6, -2), "another dimension");
        assertEquals(mf, index.at(DIM, 6, -2)[0].mainframe());
    }

    @Test
    void theLookupNeverMissesAChunkInAnArea() {
        var random = new Random(42);
        for (int run = 0; run < 200; run++) {
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            int radius = 16 + random.nextInt(113);
            double[][] pa = points(random, 1 + random.nextInt(3)), pb = points(random, 1 + random.nextInt(3));
            var index = new VoxelAreas.Index<>(Map.of(DIM, Map.of(a, pa, b, pb)), radius);
            for (int cx = -40; cx < 40; cx++) {
                for (int cz = -40; cz < 40; cz++) {
                    assertFound(index, a, pa, cx, cz, radius);
                    assertFound(index, b, pb, cx, cz, radius);
                }
            }
        }
    }

    private static void assertFound(VoxelAreas.Index<String> index, UUID mf, double[][] points, int cx, int cz, int radius) {
        for (var p : points) {
            if (VoxelService.distance(cx, (int) Math.floor(p[1] / 16), cz, p[0], p[1], p[2]) > radius) continue;
            var hits = index.at(DIM, cx, cz);
            assertNotNull(hits, "chunk " + cx + " " + cz + " is within " + radius + " of " + java.util.Arrays.toString(p));
            boolean found = false;
            for (var hit : hits) {
                if (!hit.mainframe().equals(mf)) continue;
                for (var q : hit.points()) if (java.util.Arrays.equals(p, q)) found = true;
            }
            assertTrue(found, "the point that reaches the chunk comes with the hit");
        }
    }

    private static double[][] points(Random random, int n) {
        var out = new double[n][];
        for (int i = 0; i < n; i++) out[i] = new double[]{random.nextInt(600) - 300 + .5, random.nextInt(300) - 60 + .5, random.nextInt(600) - 300 + .5};
        return out;
    }

    @Test
    void theSectionRangeIsTheRuleOfTheRoute() {
        var random = new Random(7);
        for (int run = 0; run < 2000; run++) {
            int radius = 16 + random.nextInt(113);
            var points = points(random, 1 + random.nextInt(4));
            int cx = (int) Math.floor(points[0][0] / 16) + random.nextInt(21) - 10, cz = (int) Math.floor(points[0][2] / 16) + random.nextInt(21) - 10;
            var sections = VoxelService.sectionsInRange(points, cx, cz, radius, -4, 19);
            for (int cy = -4; cy <= 19; cy++) {
                boolean allowed = false;
                for (var p : points) allowed |= VoxelService.distance(cx, cy, cz, p[0], p[1], p[2]) <= radius;
                assertEquals(allowed, sections.get(cy + 4), "section " + cx + " " + cy + " " + cz + " radius " + radius);
            }
        }
    }

    @Test
    void pointsAreSortedWithoutDuplicates() {
        var a = VoxelAreas.sorted(new ArrayList<>(List.of(new double[]{2, 0, 0}, new double[]{1, 5, 0}, new double[]{2, 0, 0}, new double[]{1, 4, 9})));
        assertEquals(3, a.length);
        assertArrayEquals(new double[]{1, 4, 9}, a[0]);
        var b = VoxelAreas.sorted(new ArrayList<>(List.of(new double[]{1, 5, 0}, new double[]{1, 4, 9}, new double[]{2, 0, 0})));
        assertTrue(VoxelAreas.same(Map.of(DIM, a), Map.of(DIM, b)), "the order does not matter");
        assertFalse(VoxelAreas.same(Map.of(DIM, a), Map.of("minecraft:the_nether", b)));
        assertFalse(VoxelAreas.same(Map.of(DIM, a), Map.of(DIM, new double[][]{{1, 4, 9}})), "a device left");
    }

    private static com.google.gson.JsonObject saved(byte[] gz) throws java.io.IOException {
        try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
            return JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void anAreaAndItsMapRoundTrip() throws java.io.IOException {
        var full = JsonParser.parseString("{\"nodes\":[{\"id\":\"a\",\"type\":\"mainframe\",\"status\":\"online\",\"pos\":{\"x\":1}},"
            + "{\"id\":\"b\",\"type\":\"uplink\",\"status\":\"online\",\"lastSeen\":5}],\"links\":[]}").getAsJsonObject();
        var json = saved(VoxelAreas.encode(Map.of(DIM, new double[][]{{1.5, 2, 3}}), full, full, 99));
        assertEquals(99, json.get("savedAt").getAsLong());
        assertArrayEquals(new double[]{1.5, 2, 3}, VoxelAreas.decodePoints(json).get(DIM)[0]);
        assertEquals(full, json.getAsJsonObject("full"));
        assertEquals(full, json.getAsJsonObject("stripped"));
    }

    @Test
    void aHugeMapStaysOutOfTheFileButTheAreaDoesNot() throws java.io.IOException {
        var huge = new com.google.gson.JsonObject();
        huge.addProperty("x", "y".repeat(3 * 1024 * 1024));
        var json = saved(VoxelAreas.encode(Map.of(DIM, new double[][]{{1, 2, 3}}), huge, huge, 1));
        assertFalse(json.has("full"));
        assertEquals(1, VoxelAreas.decodePoints(json).get(DIM).length);
    }

    @Test
    void aSavedMapComesBackOffline() {
        var full = JsonParser.parseString("{\"nodes\":[{\"id\":\"a\",\"type\":\"mainframe\",\"status\":\"online\"},"
            + "{\"id\":\"b\",\"type\":\"uplink\",\"status\":\"online\"},{\"id\":\"c\",\"type\":\"relay\",\"status\":\"offline\",\"lastSeen\":5}],\"links\":[]}").getAsJsonObject();
        var nodes = VoxelAreas.offline(full, 77).getAsJsonArray("nodes");
        assertEquals("offline", nodes.get(0).getAsJsonObject().get("status").getAsString());
        assertEquals("unloaded", nodes.get(1).getAsJsonObject().get("status").getAsString());
        assertEquals(77, nodes.get(1).getAsJsonObject().get("lastSeen").getAsLong());
        assertEquals(5, nodes.get(2).getAsJsonObject().get("lastSeen").getAsLong(), "an older time stays");
        assertEquals("online", full.getAsJsonArray("nodes").get(0).getAsJsonObject().get("status").getAsString(), "the saved map itself is not changed");
    }
}
