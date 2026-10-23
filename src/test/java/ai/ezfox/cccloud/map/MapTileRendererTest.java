package ai.ezfox.cccloud.map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Vanilla-map shading, water depth, slice depth shading, merge with old pixels, the 512-block rule. */
class MapTileRendererTest {
    /** Fake palette: color id in the high byte, brightness in the low byte. */
    static int palette(int id, int brightness) {
        return 0xFF000000 | id << 8 | brightness;
    }

    @Test
    void flatSurfaceIsNormalBrightnessAndSlopesShade() {
        var s = new MapTileRenderer.Samples();
        for (int i = 0; i < s.color.length; i++) {
            s.color[i] = 11; // stone
            s.height[i] = 64;
        }
        s.height[MapTileRenderer.SIZE * 5 + 7] = 66; // a step up: brighter
        s.height[MapTileRenderer.SIZE * 9 + 7] = 62; // a step down: darker
        var argb = MapTileRenderer.surface(s, MapTileRendererTest::palette, null);
        assertEquals(palette(11, MapTileRenderer.NORMAL), argb[MapTileRenderer.SIZE * 2 + 2], "flat ground");
        assertEquals(palette(11, MapTileRenderer.HIGH), argb[MapTileRenderer.SIZE * 5 + 7]);
        assertEquals(palette(11, MapTileRenderer.LOW), argb[MapTileRenderer.SIZE * 9 + 7]);
        var unknown = new MapTileRenderer.Samples();
        assertEquals(0, MapTileRenderer.surface(unknown, MapTileRendererTest::palette, null)[0], "unknown columns stay transparent");
    }

    @Test
    void waterDepthAndSlice() {
        var s = new MapTileRenderer.Samples();
        s.color[0] = 12;
        s.water[0] = true;
        s.depth[0] = 1;
        s.color[2] = 12;
        s.water[2] = true;
        s.depth[2] = 10;
        var argb = MapTileRenderer.surface(s, MapTileRendererTest::palette, null);
        assertEquals(palette(12, MapTileRenderer.HIGH), argb[0], "shallow water is light");
        assertEquals(palette(12, MapTileRenderer.LOW), argb[2], "deep water is dark");

        var slice = new MapTileRenderer.Samples();
        slice.color[0] = 11;
        slice.depth[0] = 0;
        slice.color[1] = 11;
        slice.depth[1] = 2;
        slice.color[2] = 11;
        slice.depth[2] = 9;
        slice.color[3] = 0;
        var cut = MapTileRenderer.slice(slice, MapTileRendererTest::palette);
        assertEquals(palette(11, MapTileRenderer.LOWEST), cut[0], "solid at the cut");
        assertEquals(palette(11, MapTileRenderer.HIGH), cut[1], "cave floor just below");
        assertEquals(palette(11, MapTileRenderer.LOW), cut[2], "deeper floor is darker");
        assertEquals(0, cut[3], "nothing within 16 blocks");
    }

    @Test
    void mergeKeepsOldPixelsOfUnloadedChunks() {
        var s = new MapTileRenderer.Samples();
        s.color[1] = 11;
        var fresh = MapTileRenderer.slice(s, MapTileRendererTest::palette);
        var previous = new int[fresh.length];
        previous[0] = 0xFF123456;
        previous[1] = 0xFF654321;
        var merged = MapTileRenderer.merge(fresh, s, previous);
        assertEquals(0xFF123456, merged[0], "unknown now: old pixel");
        assertNotEquals(0xFF654321, merged[1], "known now: fresh pixel");
    }

    @Test
    void radius() {
        assertEquals(0, MapAccess.distance(0, 0, 10, 10), 1e-9, "inside");
        assertEquals(10, MapAccess.distance(0, 0, 138, 64), 1e-9);
        assertEquals(0, MapAccess.distance(-1, -1, -5, -5), 1e-9, "negative tiles");
    }
}
