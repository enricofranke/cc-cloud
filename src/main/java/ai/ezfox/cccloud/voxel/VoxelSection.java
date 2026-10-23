package ai.ezfox.cccloud.voxel;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One 16³ section for the voxel view (docs/SPEC.md "Crazy 3D", stage 1): only <b>exposed</b> blocks (at least one face
 * touching air or a non-occluding block) are kept, so enclosed ores never leave the server. Encoding: a palette and
 * run-length pairs {@code (run, index)} as unsigned varints, base64; index 0 = nothing. Order: {@code x + 16·z + 256·y}.
 * Pure Java, unit tested.
 */
public final class VoxelSection {
    public static final int SIZE = 16;
    public static final int VOLUME = SIZE * SIZE * SIZE;
    /** Palette flags. */
    public static final int TRANSPARENT = 1, FLUID = 2, EMISSIVE = 4;

    /** A block kind as the web sees it. */
    public record Kind(String id, int color, int flags) {
        public boolean occludes() {
            return (flags & (TRANSPARENT | FLUID)) == 0;
        }
    }

    /** Blocks around and in the section; coordinates −1..16 (neighbors), null = air or unknown. */
    public interface Lookup {
        @Nullable Kind at(int x, int y, int z);

        /** False if the neighbor area is not loaded: treated as solid, so nothing is exposed towards it. */
        boolean known(int x, int y, int z);
    }

    public record Encoded(List<Kind> palette, String runs, int exposed) {
    }

    private VoxelSection() {
    }

    public static int index(int x, int y, int z) {
        return x + SIZE * z + SIZE * SIZE * y;
    }

    /** Keeps exposed blocks only and encodes them. */
    public static Encoded encode(Lookup lookup) {
        var paletteIndex = new LinkedHashMap<Kind, Integer>();
        var indices = new int[VOLUME];
        int exposed = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    var kind = lookup.at(x, y, z);
                    if (kind == null || !isExposed(lookup, x, y, z)) continue;
                    Integer id = paletteIndex.get(kind);
                    if (id == null) {
                        id = paletteIndex.size() + 1;
                        paletteIndex.put(kind, id);
                    }
                    indices[index(x, y, z)] = id;
                    exposed++;
                }
            }
        }
        return new Encoded(List.copyOf(paletteIndex.keySet()), runs(indices), exposed);
    }

    static boolean isExposed(Lookup lookup, int x, int y, int z) {
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (var d : dirs) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            if (!lookup.known(nx, ny, nz)) continue; // unknown neighbors count as solid (no X-ray at chunk borders)
            var neighbor = lookup.at(nx, ny, nz);
            if (neighbor == null || !neighbor.occludes()) return true;
        }
        return false;
    }

    /** RLE: unsigned varint pairs (run length, palette index), base64. */
    static String runs(int[] indices) {
        var out = new ByteArrayOutputStream();
        int i = 0;
        while (i < indices.length) {
            int value = indices[i];
            int run = 1;
            while (i + run < indices.length && indices[i + run] == value) run++;
            varint(out, run);
            varint(out, value);
            i += run;
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static void varint(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    /** Decodes the runs back to palette indices (for tests and documentation of the format). */
    public static int[] decode(String runs) {
        byte[] data = Base64.getDecoder().decode(runs);
        var indices = new int[VOLUME];
        int at = 0;
        var cursor = new int[]{0};
        while (cursor[0] < data.length) {
            int run = readVarint(data, cursor);
            int value = readVarint(data, cursor);
            if (run <= 0 || at + run > VOLUME) throw new IllegalArgumentException("bad run");
            java.util.Arrays.fill(indices, at, at + run, value);
            at += run;
        }
        if (at != VOLUME) throw new IllegalArgumentException("short data");
        return indices;
    }

    private static int readVarint(byte[] data, int[] cursor) {
        int value = 0, shift = 0;
        while (true) {
            if (cursor[0] >= data.length || shift > 28) throw new IllegalArgumentException("bad varint");
            int b = data[cursor[0]++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
        }
    }
}
