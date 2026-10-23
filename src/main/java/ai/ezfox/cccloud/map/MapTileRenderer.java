package ai.ezfox.cccloud.map;

import java.util.function.IntBinaryOperator;

/**
 * Turns column samples of a 128×128 tile into ARGB pixels (docs/SPEC.md "Map tiles"), like a vanilla map at 1:1:
 * map color of the top block, brightness from the height difference to the column north of it, water tinted by depth.
 * The slice layer shades by depth below the cut. Pure Java (the map color palette is injected), unit tested.
 */
public final class MapTileRenderer {
    public static final int SIZE = 128;
    /** How far the slice layer scans down from the cut. */
    public static final int SLICE_DEPTH = 16;

    /** Vanilla brightness ids (MapColor.Brightness): LOW, NORMAL, HIGH, LOWEST. */
    public static final int LOW = 0, NORMAL = 1, HIGH = 2, LOWEST = 3;

    /**
     * Column samples of one tile, row-major (z rows, x columns). {@code color[i] < 0} = unknown (chunk never seen),
     * {@code color[i] == 0} = nothing (air / no block within the slice).
     */
    public static final class Samples {
        public final int[] color = new int[SIZE * SIZE];
        public final int[] height = new int[SIZE * SIZE];
        /** Surface: water depth; slice: depth below the cut (0 = solid at the cut). */
        public final int[] depth = new int[SIZE * SIZE];
        public final boolean[] water = new boolean[SIZE * SIZE];

        public Samples() {
            java.util.Arrays.fill(color, -1);
        }

        public boolean any() {
            for (int c : color) if (c >= 0) return true;
            return false;
        }
    }

    private MapTileRenderer() {
    }

    /**
     * @param palette (mapColorId, brightness) → ARGB, i.e. {@code MapColor.byId(id).calculateARGBColor(brightness)}
     * @param northRow heights of the row north of the tile (for the first row's shading), or null
     */
    public static int[] surface(Samples s, IntBinaryOperator palette, int[] northRow) {
        var argb = new int[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int i = z * SIZE + x;
                int color = s.color[i];
                if (color <= 0) continue; // unknown or nothing: transparent
                int brightness;
                if (s.water[i]) {
                    double diff = s.depth[i] * 0.1 + ((x + z) & 1) * 0.2;
                    brightness = diff < 0.5 ? HIGH : diff > 0.9 ? LOW : NORMAL;
                } else {
                    int north = z > 0 ? s.height[i - SIZE] : northRow != null ? northRow[x] : s.height[i];
                    if (z > 0 && s.color[i - SIZE] < 0) north = s.height[i];
                    double diff = (s.height[i] - north) * 4.0 / 5 + (((x + z) & 1) - 0.5) * 0.4;
                    brightness = diff > 0.6 ? HIGH : diff < -0.6 ? LOW : NORMAL;
                }
                argb[i] = palette.applyAsInt(color, brightness);
            }
        }
        return argb;
    }

    /** Slice: the first block at or below the cut; deeper floors are darker, solid rock at the cut is darkest. */
    public static int[] slice(Samples s, IntBinaryOperator palette) {
        var argb = new int[SIZE * SIZE];
        for (int i = 0; i < SIZE * SIZE; i++) {
            int color = s.color[i];
            if (color <= 0) continue;
            int d = s.depth[i];
            int brightness = d == 0 ? LOWEST : d <= 3 ? HIGH : d <= 7 ? NORMAL : d <= 11 ? LOW : LOWEST;
            argb[i] = palette.applyAsInt(color, brightness);
        }
        return argb;
    }

    /** Keeps old pixels where the new samples know nothing (chunks that are not loaded right now). */
    public static int[] merge(int[] fresh, Samples s, int[] previous) {
        if (previous == null || previous.length != fresh.length) return fresh;
        for (int i = 0; i < fresh.length; i++) if (s.color[i] < 0) fresh[i] = previous[i];
        return fresh;
    }
}
