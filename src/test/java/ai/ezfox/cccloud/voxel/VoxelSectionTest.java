package ai.ezfox.cccloud.voxel;

import static org.junit.jupiter.api.Assertions.*;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Exposed-only filtering (no ore X-ray), unknown neighbors count as solid, and the RLE round trip. */
class VoxelSectionTest {
    static final VoxelSection.Kind STONE = new VoxelSection.Kind("minecraft:stone", 0xFF707070, 0);
    static final VoxelSection.Kind ORE = new VoxelSection.Kind("minecraft:diamond_ore", 0xFF5DECF5, 0);
    static final VoxelSection.Kind GLASS = new VoxelSection.Kind("minecraft:glass", 0xFFFFFFFF, VoxelSection.TRANSPARENT);

    /** A solid stone world with an ore at (8,8,8), an ore next to a cave at (2,2,2)/(2,3,2), glass at (12,12,12). */
    static final class World implements VoxelSection.Lookup {
        boolean neighborsKnown = true;

        @Override
        public VoxelSection.@Nullable Kind at(int x, int y, int z) {
            if (x == 8 && y == 8 && z == 8) return ORE;
            if (x == 2 && y == 2 && z == 2) return ORE;
            if (x == 2 && y == 3 && z == 2) return null; // cave air above the second ore
            if (x == 12 && y == 12 && z == 12) return GLASS;
            return STONE;
        }

        @Override
        public boolean known(int x, int y, int z) {
            boolean inside = x >= 0 && x < 16 && y >= 0 && y < 16 && z >= 0 && z < 16;
            return inside || neighborsKnown;
        }
    }

    @Test
    void enclosedOreIsNeverSent() {
        var encoded = VoxelSection.encode(new World());
        var indices = VoxelSection.decode(encoded.runs());
        assertEquals(0, indices[VoxelSection.index(8, 8, 8)], "an ore enclosed in stone is not sent");
        int caveOre = indices[VoxelSection.index(2, 2, 2)];
        assertTrue(caveOre > 0 && encoded.palette().get(caveOre - 1).equals(ORE), "an ore touching a cave is visible");
        assertTrue(indices[VoxelSection.index(12, 11, 12)] > 0, "stone below glass is exposed (glass does not occlude)");
        assertEquals(0, indices[VoxelSection.index(5, 5, 5)], "inner stone is not sent");
    }

    @Test
    void unknownNeighborsDoNotExpose() {
        var world = new World();
        world.neighborsKnown = false;
        var indices = VoxelSection.decode(VoxelSection.encode(world).runs());
        assertEquals(0, indices[VoxelSection.index(0, 5, 5)], "a border block next to an unloaded chunk stays hidden");
        world.neighborsKnown = true;
        // Neighbors known but solid stone: still hidden.
        assertEquals(0, VoxelSection.decode(VoxelSection.encode(world).runs())[VoxelSection.index(0, 5, 5)]);
    }

    @Test
    void roundTrip() {
        var original = new int[VoxelSection.VOLUME];
        for (int i = 0; i < original.length; i++) original[i] = (i % 7 == 0) ? (i % 300) : 0;
        assertArrayEquals(original, VoxelSection.decode(VoxelSection.runs(original)));
        var empty = new int[VoxelSection.VOLUME];
        assertArrayEquals(empty, VoxelSection.decode(VoxelSection.runs(empty)));
        assertThrows(IllegalArgumentException.class, () -> VoxelSection.decode("AQ=="), "short data");
    }

    @Test
    void radiusBox() {
        assertEquals(0, VoxelService.distance(0, 0, 0, 8, 8, 8), 1e-9);
        assertEquals(4, VoxelService.distance(0, 0, 0, 20, 8, 8), 1e-9);
    }
}
