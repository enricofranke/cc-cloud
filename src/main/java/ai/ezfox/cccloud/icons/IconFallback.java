package ai.ezfox.cccloud.icons;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Server-side icon fallback without a game client (docs/SPEC.md "Item icons"): follows the item definition
 * ({@code assets/<ns>/items/<id>.json}) to its model, the model's parents to a texture ({@code layer0}, else the
 * usual block faces), and returns that texture's PNG. Animated strips are cut to their first frame. Pure Java over a
 * resource reader, unit tested.
 */
public final class IconFallback {
    private static final int MAX_DEPTH = 8;
    private static final List<String> TEXTURE_KEYS = List.of("layer0", "all", "front", "side", "top", "texture", "end", "cross", "particle");

    private IconFallback() {
    }

    /**
     * @param read reads a resource path like {@code assets/ae2/models/item/foo.json}; null if missing
     * @return the PNG bytes of the best texture, or null
     */
    public static byte @Nullable [] resolve(String namespace, String path, Function<String, byte @Nullable []> read) {
        String model = itemModel(namespace, path, read);
        if (model == null) model = namespace + ":item/" + path;
        String texture = texture(model, read);
        if (texture == null) return null;
        var id = split(texture);
        byte[] png = read.apply("assets/" + id[0] + "/textures/" + id[1] + ".png");
        return png == null ? null : firstFrame(png);
    }

    /** The model id of an item definition (first plain model found, e.g. the fallback of a condition/select). */
    static @Nullable String itemModel(String namespace, String path, Function<String, byte @Nullable []> read) {
        var json = json(read.apply("assets/" + namespace + "/items/" + path + ".json"));
        if (json == null || !(json.get("model") instanceof JsonObject model)) return null;
        return findModel(model, 0);
    }

    private static @Nullable String findModel(JsonElement element, int depth) {
        if (depth > MAX_DEPTH || element == null) return null;
        if (element instanceof JsonObject obj) {
            var type = obj.get("type");
            boolean plain = type == null || type.getAsString().equals("minecraft:model") || type.getAsString().equals("model");
            if (plain && obj.get("model") != null && obj.get("model").isJsonPrimitive()) return obj.get("model").getAsString();
            // Prefer the "fallback"/"on_false" branches, then anything else.
            for (String key : List.of("fallback", "on_false", "on_true", "model", "cases", "entries", "models")) {
                var found = findModel(obj.get(key), depth + 1);
                if (found != null) return found;
            }
        } else if (element.isJsonArray()) {
            for (var e : element.getAsJsonArray()) {
                var found = findModel(e, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Follows the model's parents and returns the texture id (namespace:path) for the icon. */
    static @Nullable String texture(String modelId, Function<String, byte @Nullable []> read) {
        var textures = new HashMap<String, String>();
        String current = modelId;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            var id = split(current);
            if (id[1].startsWith("builtin/")) break;
            var json = json(read.apply("assets/" + id[0] + "/models/" + id[1] + ".json"));
            if (json == null) break;
            if (json.get("textures") instanceof JsonObject t) {
                for (var e : t.entrySet()) {
                    String ref = textureRef(e.getValue());
                    if (ref != null) textures.putIfAbsent(e.getKey(), ref); // child wins
                }
            }
            current = json.get("parent") != null && json.get("parent").isJsonPrimitive() ? json.get("parent").getAsString() : null;
        }
        for (String key : TEXTURE_KEYS) {
            String value = deref(textures, textures.get(key));
            if (value != null) return value;
        }
        for (var value : textures.values()) {
            String v = deref(textures, value);
            if (v != null) return v;
        }
        return null;
    }

    /** A model's texture value: {@code "ns:path"} / {@code "#key"}, or (26.1) {@code {"sprite": "ns:path", "force_translucent": …}}. */
    static @Nullable String textureRef(@Nullable JsonElement value) {
        if (value == null) return null;
        if (value.isJsonPrimitive()) return value.getAsString();
        if (value instanceof JsonObject o && o.get("sprite") instanceof JsonElement s && s.isJsonPrimitive()) return s.getAsString();
        return null;
    }

    private static @Nullable String deref(Map<String, String> textures, @Nullable String value) {
        for (int i = 0; value != null && value.startsWith("#") && i < MAX_DEPTH; i++) value = textures.get(value.substring(1));
        return value == null || value.startsWith("#") ? null : value;
    }

    /** {@code ns:path} → {ns, path}; no namespace = minecraft. */
    static String[] split(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? new String[]{"minecraft", id} : new String[]{id.substring(0, colon), id.substring(colon + 1)};
    }

    private static @Nullable JsonObject json(byte @Nullable [] bytes) {
        if (bytes == null || bytes.length > 256 * 1024) return null;
        try {
            var e = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Square textures as they are; vertical animation strips cut to the first frame; anything else null. */
    static byte @Nullable [] firstFrame(byte[] png) {
        var size = PngCodec.size(png);
        if (size == null || size[0] <= 0 || size[0] > 512) return null;
        if (size[0] == size[1]) return png.length <= 64 * 1024 ? png : null;
        if (size[1] % size[0] != 0) return null;
        try {
            var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (image == null) return null;
            var frame = image.getSubimage(0, 0, size[0], size[0]);
            var out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(frame, "png", out);
            return out.size() <= 64 * 1024 ? out.toByteArray() : null;
        } catch (Exception | LinkageError e) {
            return null; // no java.desktop on this server: skip animated textures
        }
    }
}
