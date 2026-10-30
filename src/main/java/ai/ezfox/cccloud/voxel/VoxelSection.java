package ai.ezfox.cccloud.voxel;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One 16³ section for the voxel view (docs/SPEC.md "Crazy 3D", stage 1): only <b>exposed</b> blocks (at least one face
 * touching air or a non-occluding block) are kept, so enclosed ores never leave the server. Encoding: a palette and
 * run-length pairs {@code (run, index)} as unsigned varints; index 0 = nothing. Order: {@code x + 16·z + 256·y}.
 * Pure Java, unit tested.
 */
public final class VoxelSection {
    public static final int SIZE = 16;
    public static final int VOLUME = SIZE * SIZE * SIZE;
    /** Palette flags. */
    public static final int TRANSPARENT = 1, FLUID = 2, EMISSIVE = 4;
    /** Side of the grid with a one-block border around the section. */
    public static final int GRID = SIZE + 2;
    /** Grid value of a neighbor in an unloaded chunk: counts as solid, so nothing is exposed towards it. */
    public static final int UNKNOWN = -1;

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

    /** The encoded section: its own palette, the runs (raw varints), the exposed block count and a content hash. */
    public record Encoded(List<Kind> palette, byte[] runs, int exposed, long hash) {
        public String runsBase64() {
            return Base64.getEncoder().encodeToString(runs);
        }

        /** The hash as 16 hex digits (what the dashboard sends back as "have"). */
        public String hashHex() {
            return hex(hash);
        }
    }

    private VoxelSection() {
    }

    public static int index(int x, int y, int z) {
        return x + SIZE * z + SIZE * SIZE * y;
    }

    /** Index into the bordered grid, coordinates −1..16. */
    public static int gridIndex(int x, int y, int z) {
        return (x + 1) + GRID * (z + 1) + GRID * GRID * (y + 1);
    }

    /** Keeps exposed blocks only and encodes them. */
    public static Encoded encode(Lookup lookup) {
        var kinds = new ArrayList<Kind>();
        var ids = new HashMap<Kind, Integer>();
        var grid = new int[GRID * GRID * GRID];
        for (int y = -1; y <= SIZE; y++) {
            for (int z = -1; z <= SIZE; z++) {
                for (int x = -1; x <= SIZE; x++) {
                    int i = gridIndex(x, y, z);
                    if (!lookup.known(x, y, z)) {
                        grid[i] = UNKNOWN;
                        continue;
                    }
                    var kind = lookup.at(x, y, z);
                    if (kind == null) continue;
                    Integer id = ids.get(kind);
                    if (id == null) {
                        kinds.add(kind);
                        id = kinds.size();
                        ids.put(kind, id);
                    }
                    grid[i] = id;
                }
            }
        }
        return encodeGrid(grid, kinds);
    }

    /**
     * Keeps exposed blocks only and encodes them. {@code grid} holds the section and its one-block border
     * ({@link #gridIndex}): 0 = air, {@link #UNKNOWN} = not loaded, k = {@code kinds.get(k - 1)}.
     */
    public static Encoded encodeGrid(int[] grid, List<Kind> kinds) {
        var occludes = new boolean[kinds.size() + 1];
        for (int k = 0; k < kinds.size(); k++) occludes[k + 1] = kinds.get(k).occludes();
        var local = new int[kinds.size() + 1]; // grid kind → section palette index (0 = not used yet)
        var palette = new ArrayList<Kind>();
        var indices = new int[VOLUME];
        int exposed = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    int g = gridIndex(x, y, z);
                    int k = grid[g];
                    if (k <= 0 || !exposed(grid, occludes, g)) continue;
                    if (local[k] == 0) {
                        palette.add(kinds.get(k - 1));
                        local[k] = palette.size();
                    }
                    indices[index(x, y, z)] = local[k];
                    exposed++;
                }
            }
        }
        byte[] runs = runs(indices);
        return new Encoded(List.copyOf(palette), runs, exposed, hash(palette, runs));
    }

    private static final int[] STEPS = {1, -1, GRID, -GRID, GRID * GRID, -GRID * GRID};

    /** A face touches air or a non-occluding block; unknown neighbors count as solid (no X-ray at chunk borders). */
    private static boolean exposed(int[] grid, boolean[] occludes, int g) {
        for (int step : STEPS) {
            int n = grid[g + step];
            if (n == 0 || (n > 0 && !occludes[n])) return true;
        }
        return false;
    }

    static boolean isExposed(Lookup lookup, int x, int y, int z) {
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (var d : dirs) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            if (!lookup.known(nx, ny, nz)) continue;
            var neighbor = lookup.at(nx, ny, nz);
            if (neighbor == null || !neighbor.occludes()) return true;
        }
        return false;
    }

    /** RLE: unsigned varint pairs (run length, palette index). */
    static byte[] runs(int[] indices) {
        var out = new ByteArrayOutputStream(256);
        int i = 0;
        while (i < indices.length) {
            int value = indices[i];
            int run = 1;
            while (i + run < indices.length && indices[i + run] == value) run++;
            varint(out, run);
            varint(out, value);
            i += run;
        }
        return out.toByteArray();
    }

    static void varint(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    /** 64-bit content hash of the palette and the runs (first 8 bytes of SHA-256). */
    static long hash(List<Kind> palette, byte[] runs) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            for (var k : palette) {
                md.update(k.id().getBytes(StandardCharsets.UTF_8));
                md.update(new byte[]{0, (byte) (k.color() >>> 24), (byte) (k.color() >>> 16), (byte) (k.color() >>> 8), (byte) k.color(), (byte) k.flags()});
            }
            md.update((byte) 0xFF);
            md.update(runs);
            byte[] d = md.digest();
            long h = 0;
            for (int i = 0; i < 8; i++) h = (h << 8) | (d[i] & 0xFF);
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hex(long hash) {
        String s = Long.toHexString(hash);
        return "0".repeat(16 - s.length()) + s;
    }

    /** Decodes base64 runs back to palette indices (for tests and documentation of the format). */
    public static int[] decode(String runs) {
        return decode(Base64.getDecoder().decode(runs));
    }

    /** Decodes raw runs back to palette indices. */
    public static int[] decode(byte[] data) {
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

    static int readVarint(byte[] data, int[] cursor) {
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
