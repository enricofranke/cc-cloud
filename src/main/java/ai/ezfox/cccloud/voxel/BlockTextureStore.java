package ai.ezfox.cccloud.voxel;

import ai.ezfox.cccloud.icons.PngCodec;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Block textures for the voxel view, stage 2 (docs/SPEC.md "Voxel view – stage 2"): per block id the sprite of each of
 * the six faces (down, up, north, south, west, east) and which faces are tinted, plus the sprites as validated 16×16
 * PNGs. Sprites get a stable tile index in the atlas ({@link #atlas}, {@link #COLS} per row, at most
 * {@link #MAX_SPRITES}). Entries are versioned per mod version (re-rendered after updates). Pure Java, unit tested;
 * thread-safe.
 */
public final class BlockTextureStore {
    public static final int TILE = 16;
    public static final int COLS = 64;
    public static final int MAX_SPRITES = 4096;
    public static final int MAX_PNG = 4096;
    public static final String[] FACES = {"down", "up", "north", "south", "west", "east"};
    public static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");

    /** faces: sprite name per face (null = no texture), tint: bit i = face i is tinted (multiply with the palette colour). */
    public record Block(String version, @Nullable String[] faces, int tint) {
    }

    private final Map<String, byte[]> sprites = new LinkedHashMap<>();
    private final Map<String, Integer> tiles = new LinkedHashMap<>();
    private final Map<String, Block> blocks = new LinkedHashMap<>();
    private final Map<String, String> failed = new LinkedHashMap<>();
    private int rev;
    private byte @Nullable [] atlasPng;
    private int atlasRev = -1;
    private boolean dirty;

    public synchronized int rev() {
        return rev;
    }

    public synchronized boolean isCurrent(String blockId, String version) {
        var b = blocks.get(blockId);
        return (b != null && b.version().equals(version)) || version.equals(failed.get(blockId));
    }

    public synchronized @Nullable Block block(String blockId) {
        return blocks.get(blockId);
    }

    public synchronized boolean hasSprite(String name) {
        return sprites.containsKey(name);
    }

    /** Tile index of a sprite in the atlas, or -1. */
    public synchronized int tile(@Nullable String sprite) {
        if (sprite == null) return -1;
        var t = tiles.get(sprite);
        return t == null ? -1 : t;
    }

    /** Adds a sprite (16×16 PNG, validated); false if invalid or the atlas is full. A known sprite keeps its tile. */
    public synchronized boolean putSprite(String name, byte[] png) {
        if (!ID.matcher(name).matches() || png.length > MAX_PNG || !PngCodec.isValid(png, TILE, TILE)) return false;
        if (!sprites.containsKey(name) && sprites.size() >= MAX_SPRITES) return false;
        var old = sprites.put(name, png);
        if (old == null) tiles.put(name, tiles.size());
        if (old == null || !java.util.Arrays.equals(old, png)) {
            rev++;
            dirty = true;
        }
        return true;
    }

    /** Stores a block; every named sprite must be known. False if invalid. */
    public synchronized boolean putBlock(String blockId, String version, @Nullable String[] faces, int tint) {
        if (!ID.matcher(blockId).matches() || faces.length != 6 || tint < 0 || tint > 63) return false;
        for (var f : faces) if (f != null && !sprites.containsKey(f)) return false;
        blocks.put(blockId, new Block(version, faces.clone(), tint));
        failed.remove(blockId);
        rev++;
        dirty = true;
        return true;
    }

    public synchronized void markFailed(String blockId, String version) {
        failed.put(blockId, version);
        dirty = true;
    }

    /** Ids of {@code wanted} (id → version) that need rendering. */
    public synchronized Set<String> outdated(Map<String, String> wanted) {
        var out = new LinkedHashSet<String>();
        for (var e : wanted.entrySet()) if (!isCurrent(e.getKey(), e.getValue())) out.add(e.getKey());
        return out;
    }

    /** The atlas: {@link #COLS} tiles per row, one 16×16 tile per sprite in tile order (cached per revision). */
    public synchronized byte[] atlas() {
        if (atlasPng != null && atlasRev == rev) return atlasPng;
        int n = Math.max(1, sprites.size());
        int cols = Math.min(COLS, n), rows = (n + COLS - 1) / COLS;
        int w = cols * TILE, h = rows * TILE;
        var pixels = new int[w * h];
        for (var e : sprites.entrySet()) {
            int t = tiles.get(e.getKey());
            var px = PngCodec.decode(e.getValue(), TILE, TILE);
            if (px == null) continue;
            int ox = (t % COLS) * TILE, oy = (t / COLS) * TILE;
            for (int y = 0; y < TILE; y++) System.arraycopy(px, y * TILE, pixels, (oy + y) * w + ox, TILE);
        }
        atlasPng = PngCodec.encode(w, h, pixels);
        atlasRev = rev;
        return atlasPng;
    }

    public synchronized int[] atlasSize() {
        int n = Math.max(1, sprites.size());
        return new int[]{Math.min(COLS, n) * TILE, ((n + COLS - 1) / COLS) * TILE};
    }

    /**
     * Adds {@code tex} (tile per face, -1 = none) and {@code tint} to every palette entry with a stored block, and the
     * {@code atlas} description when at least one entry has textures. Returns the ids without stored textures.
     */
    public synchronized List<String> decorate(JsonObject voxels) {
        var missing = new ArrayList<String>();
        if (!(voxels.get("palette") instanceof JsonArray palette)) return missing;
        boolean any = false;
        for (JsonElement e : palette) {
            if (!(e instanceof JsonObject p) || !(p.get("id") instanceof com.google.gson.JsonPrimitive idp)) continue;
            var block = blocks.get(idp.getAsString());
            if (block == null) {
                if (!failed.containsKey(idp.getAsString())) missing.add(idp.getAsString());
                continue;
            }
            var tex = new JsonArray();
            boolean has = false;
            for (var f : block.faces()) {
                int t = tile(f);
                tex.add(t);
                has |= t >= 0;
            }
            if (!has) continue;
            p.add("tex", tex);
            p.addProperty("tint", block.tint());
            any = true;
        }
        if (any) {
            var atlas = new JsonObject();
            atlas.addProperty("rev", rev);
            atlas.addProperty("tile", TILE);
            atlas.addProperty("cols", COLS);
            var size = atlasSize();
            atlas.addProperty("width", size[0]);
            atlas.addProperty("height", size[1]);
            atlas.addProperty("url", "/api/voxel-atlas/" + rev + ".png");
            voxels.add("atlas", atlas);
        }
        return missing;
    }

    // ---- persistence -------------------------------------------------------------------------------------------------

    public synchronized void save(Path file) throws java.io.IOException {
        if (!dirty) return;
        var json = new JsonObject();
        var s = new JsonObject();
        for (var e : sprites.entrySet()) s.addProperty(e.getKey(), Base64.getEncoder().encodeToString(e.getValue()));
        json.add("sprites", s);
        var b = new JsonObject();
        for (var e : blocks.entrySet()) {
            var o = new JsonObject();
            o.addProperty("version", e.getValue().version());
            var faces = new JsonArray();
            for (var f : e.getValue().faces()) faces.add(f);
            o.add("faces", faces);
            o.addProperty("tint", e.getValue().tint());
            b.add(e.getKey(), o);
        }
        json.add("blocks", b);
        var f = new JsonObject();
        failed.forEach(f::addProperty);
        json.add("failed", f);
        Files.createDirectories(file.getParent());
        var tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, json.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        dirty = false;
    }

    /** Loads a saved store; invalid entries are skipped. */
    public static BlockTextureStore load(Path file) {
        var store = new BlockTextureStore();
        if (!Files.isRegularFile(file)) return store;
        try {
            var json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : json.getAsJsonObject("sprites").entrySet()) store.putSprite(e.getKey(), Base64.getDecoder().decode(e.getValue().getAsString()));
            for (var e : json.getAsJsonObject("blocks").entrySet()) {
                var o = e.getValue().getAsJsonObject();
                var faces = new String[6];
                var array = o.getAsJsonArray("faces");
                for (int i = 0; i < 6 && i < array.size(); i++) faces[i] = array.get(i).isJsonNull() ? null : array.get(i).getAsString();
                store.putBlock(e.getKey(), o.get("version").getAsString(), faces, o.get("tint").getAsInt());
            }
            if (json.get("failed") instanceof JsonObject f) for (var e : f.entrySet()) store.failed.put(e.getKey(), e.getValue().getAsString());
        } catch (RuntimeException | java.io.IOException e) {
            // unreadable: start empty, clients render again
        }
        store.dirty = false;
        return store;
    }
}
