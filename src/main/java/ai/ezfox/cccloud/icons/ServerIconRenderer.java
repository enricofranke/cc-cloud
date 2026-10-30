package ai.ezfox.cccloud.icons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Draws item icons on the server, without a game client (docs/SPEC.md "Item icons (as implemented)"): 32×32 RGBA
 * like the client renders, from the models and textures in the mod jars (and vanilla's, when a client jar is
 * available). Two kinds of items are drawn, everything else is left to game clients:
 * <ul>
 *   <li><b>flat</b>: {@code item/generated} and {@code item/handheld} models – the {@code layerN} textures stacked, each
 *       scaled to the slot with nearest neighbour, tinted with the item definition's constant or default tint
 *       (leather brown, water blue, …); animated textures show their first frame;</li>
 *   <li><b>cube</b>: block models whose elements are all full cubes ({@code block/cube_all}, {@code cube_column},
 *       {@code orientable}, leaves, overlays like the grass block's): the faces the inventory shows, projected with the
 *       model's {@code display.gui} transform (blocks: 30°/225°, 0.625) and lit with the game's inventory lights, so
 *       they match client renders (top 100 %, left 65 %, right 40 %).</li>
 * </ul>
 * Skipped: special renderers (chests, banners, heads), composite and custom-loader models (OBJ, …), models with other
 * shapes (slabs, stairs, machines), builtin entity models, and anything whose model or texture cannot be read.
 * <p>
 * Pure Java over a resource reader (paths like {@code assets/ae2/models/item/foo.json}), unit tested. Not thread-safe:
 * one renderer per render pass; it caches parent models and recently used textures.
 */
public final class ServerIconRenderer {
    public static final int SIZE = PngCodec.ICON_SIZE;
    private static final int MAX_DEPTH = 16;
    private static final int MAX_JSON = 256 * 1024;
    /** The item shaders discard texels with alpha below 0.1. */
    private static final int CUTOUT = 26;
    private static final double EPS = 1e-9;
    private static final List<String> FACES = List.of("down", "up", "north", "south", "west", "east");
    /** Corners per face as the game orders them (top-left, bottom-left, bottom-right, top-right seen from outside). */
    private static final Map<String, int[][]> CORNERS = Map.of(
        "down", new int[][]{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
        "up", new int[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
        "north", new int[][]{{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
        "south", new int[][]{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
        "west", new int[][]{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
        "east", new int[][]{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}});
    private static final Map<String, double[]> NORMALS = Map.of(
        "down", new double[]{0, -1, 0}, "up", new double[]{0, 1, 0}, "north", new double[]{0, 0, -1},
        "south", new double[]{0, 0, 1}, "west", new double[]{-1, 0, 0}, "east", new double[]{1, 0, 0});
    /** The game's GUI item lights (Lighting.ITEMS_3D / ITEMS_FLAT), in GUI space (x right, y down, z to the viewer). */
    private static final double[][] SIDE_LIGHTS = guiLights(true);
    private static final double[][] FRONT_LIGHTS = guiLights(false);
    private static final double[] BLOCK_ROTATION = {30, 225, 0};

    public enum Kind { FLAT, CUBE, SKIPPED }

    /** What was drawn: ARGB pixels (row-major, 32×32) for flat and cube icons, else the reason it was skipped. */
    public record Result(Kind kind, int @Nullable [] argb, String detail) {
        static Result skipped(String why) {
            return new Result(Kind.SKIPPED, null, why);
        }

        /** The icon as a PNG (as {@link PngCodec} validates uploads), or null when skipped. */
        public byte @Nullable [] png() {
            return argb == null ? null : PngCodec.encode(SIZE, SIZE, argb);
        }

        /** {@link #png()} with a reused deflater (bulk rendering). */
        public byte @Nullable [] png(java.util.zip.Deflater deflater) {
            return argb == null ? null : PngCodec.encode(SIZE, SIZE, argb, deflater);
        }
    }

    /** What the item definition points to: one plain model and its tints, or why it cannot be drawn. */
    private record Pick(@Nullable String model, int[] tints, @Nullable String skip) {
    }

    /** A model with its parents merged: textures (child wins), the first elements, the gui transform. */
    private record Chain(Map<String, String> textures, @Nullable JsonArray elements, boolean generated, double[] rotation,
                         double[] translation, double[] scale, boolean frontLight) {
    }

    private record Frame(int w, int h, int[] argb) {
        /** Texel at model uv (0–16), nearest neighbour like the game's texture sampling. */
        int at(double u, double v) {
            int x = Math.min(w - 1, Math.max(0, (int) Math.floor(u / 16.0 * w)));
            int y = Math.min(h - 1, Math.max(0, (int) Math.floor(v / 16.0 * h)));
            return argb[y * w + x];
        }
    }

    /** Recently used textures are kept up to this many pixels (16 MB), HD packs included. */
    private static final long CACHED_PIXELS = 4L * 1024 * 1024;

    private final Function<String, byte @Nullable []> read;
    private final Map<String, Optional<JsonObject>> parents = new HashMap<>();
    private final LinkedHashMap<String, Optional<Frame>> textures = new LinkedHashMap<>(256, 0.75f, true);
    private long cachedPixels;

    /** @param read reads a resource path like {@code assets/ae2/textures/item/foo.png}; null if missing */
    public ServerIconRenderer(Function<String, byte @Nullable []> read) {
        this.read = read;
    }

    /** Draws the icon of item {@code namespace:path}. Never throws for bad assets: those come back as skipped. */
    public Result render(String namespace, String path) {
        try {
            var pick = pick(namespace, path);
            if (pick.skip() != null) return Result.skipped(pick.skip());
            String modelId = pick.model() != null ? pick.model() : namespace + ":item/" + path;
            var loaded = load(modelId);
            if (loaded instanceof String why) return Result.skipped(why);
            var chain = (Chain) loaded;
            if (chain.generated()) return flat(chain, pick.tints());
            if (chain.elements() != null) return cube(chain, pick.tints());
            return Result.skipped("no elements in " + modelId);
        } catch (RuntimeException e) {
            return Result.skipped("error: " + e);
        }
    }

    // ---- item definition ---------------------------------------------------------------------------------------------

    private Pick pick(String namespace, String path) {
        var def = json(read.apply("assets/" + namespace + "/items/" + path + ".json"));
        if (def == null) return new Pick(null, new int[0], null); // no definition: the classic item/<path> model
        var pick = pick(def.get("model"), 0);
        return pick != null ? pick : new Pick(null, new int[0], "no model in the item definition");
    }

    private @Nullable Pick pick(@Nullable JsonElement element, int depth) {
        if (depth > MAX_DEPTH || element == null) return null;
        if (element.isJsonArray()) {
            for (var e : element.getAsJsonArray()) {
                var found = pick(e, depth + 1);
                if (found != null) return found;
            }
            return null;
        }
        if (!(element instanceof JsonObject obj)) return null;
        String type = string(obj.get("type"), "minecraft:model");
        if (!type.contains(":")) type = "minecraft:" + type;
        switch (type) {
            case "minecraft:model" -> {
                return obj.get("model") instanceof JsonElement m && m.isJsonPrimitive() ? new Pick(m.getAsString(), tints(obj.get("tints")), null) : null;
            }
            case "minecraft:condition" -> {
                // The plain state (not in use, not broken, no component).
                var found = pick(obj.get("on_false"), depth + 1);
                return found != null ? found : pick(obj.get("on_true"), depth + 1);
            }
            case "minecraft:select" -> {
                if (string(obj.get("property"), "").replace("minecraft:", "").equals("display_context") && obj.get("cases") instanceof JsonArray cases) {
                    for (var c : cases) {
                        if (c instanceof JsonObject co && whenMatches(co.get("when"), "gui")) return pick(co.get("model"), depth + 1);
                    }
                }
                var found = pick(obj.get("fallback"), depth + 1);
                if (found == null && obj.get("cases") instanceof JsonArray cases && !cases.isEmpty() && cases.get(0) instanceof JsonObject first) {
                    found = pick(first.get("model"), depth + 1);
                }
                return found;
            }
            case "minecraft:range_dispatch" -> {
                // The entry the property's resting value 0 selects (highest threshold ≤ 0), else the fallback.
                JsonElement best = null;
                double bestThreshold = Double.NEGATIVE_INFINITY;
                if (obj.get("entries") instanceof JsonArray entries) {
                    for (var e : entries) {
                        if (!(e instanceof JsonObject eo)) continue;
                        double t = eo.get("threshold") != null && eo.get("threshold").isJsonPrimitive() ? eo.get("threshold").getAsDouble() : 0;
                        if (t <= 0 && t >= bestThreshold) {
                            best = eo.get("model");
                            bestThreshold = t;
                        }
                    }
                }
                var found = best != null ? pick(best, depth + 1) : null;
                if (found == null) found = pick(obj.get("fallback"), depth + 1);
                if (found == null && obj.get("entries") instanceof JsonArray entries && !entries.isEmpty() && entries.get(0) instanceof JsonObject first) {
                    found = pick(first.get("model"), depth + 1);
                }
                return found;
            }
            case "minecraft:special" -> {
                return new Pick(null, new int[0], "special renderer");
            }
            case "minecraft:composite" -> {
                return new Pick(null, new int[0], "composite model");
            }
            case "minecraft:empty" -> {
                return new Pick(null, new int[0], "empty model");
            }
            default -> {
                return new Pick(null, new int[0], "item model type " + type);
            }
        }
    }

    private static boolean whenMatches(@Nullable JsonElement when, String value) {
        if (when == null) return false;
        if (when.isJsonPrimitive()) return when.getAsString().equals(value);
        if (when.isJsonArray()) for (var e : when.getAsJsonArray()) if (e.isJsonPrimitive() && e.getAsString().equals(value)) return true;
        return false;
    }

    /**
     * Tint colours per tint index: constants and the sources' default colours ({@code dye}, {@code potion},
     * {@code firework}, {@code map_color}, …); grass in its default biome colour; unknown sources stay white.
     */
    static int[] tints(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) return new int[0];
        var array = element.getAsJsonArray();
        var out = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            out[i] = 0xFFFFFFFF;
            if (!(array.get(i) instanceof JsonObject t)) continue;
            String type = string(t.get("type"), "").replace("minecraft:", "");
            Integer rgb = color(t.has("value") ? t.get("value") : t.get("default"));
            if (rgb != null) out[i] = 0xFF000000 | rgb;
            else if (type.equals("grass")) out[i] = 0xFF7CBD6B;
            else if (type.equals("foliage")) out[i] = 0xFF48B518;
        }
        return out;
    }

    private static @Nullable Integer color(@Nullable JsonElement value) {
        if (value == null) return null;
        try {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsInt() & 0xFFFFFF;
            if (value.isJsonArray() && value.getAsJsonArray().size() >= 3) {
                var a = value.getAsJsonArray();
                int r = (int) Math.round(Math.clamp(a.get(0).getAsDouble(), 0, 1) * 255);
                int g = (int) Math.round(Math.clamp(a.get(1).getAsDouble(), 0, 1) * 255);
                int b = (int) Math.round(Math.clamp(a.get(2).getAsDouble(), 0, 1) * 255);
                return r << 16 | g << 8 | b;
            }
        } catch (RuntimeException ignored) {
            // not a colour
        }
        return null;
    }

    // ---- models ------------------------------------------------------------------------------------------------------

    /** The merged model chain, or a String with the reason it cannot be drawn. */
    private Object load(String modelId) {
        var textureMap = new HashMap<String, String>();
        JsonArray elements = null;
        double[] rotation = null, translation = null, scale = null;
        Boolean frontLight = null;
        String current = modelId;
        for (int depth = 0; ; depth++) {
            if (current == null) break;
            if (depth >= MAX_DEPTH) return "model parents nested too deep";
            var id = IconFallback.split(current);
            if (id[0].equals("minecraft") && id[1].equals("builtin/generated")) {
                return new Chain(textureMap, null, true, new double[3], new double[3], new double[]{1, 1, 1}, true);
            }
            if (id[1].startsWith("builtin/")) return "builtin model " + current;
            var json = model(id[0], id[1], depth > 0);
            if (json == null) return "missing model " + id[0] + ":" + id[1];
            if (json.has("loader")) return "model loader " + string(json.get("loader"), "?");
            if (json.get("textures") instanceof JsonObject t) {
                for (var e : t.entrySet()) {
                    String ref = IconFallback.textureRef(e.getValue());
                    if (ref != null) textureMap.putIfAbsent(e.getKey(), ref); // the child wins
                }
            }
            if (elements == null && json.get("elements") instanceof JsonArray a) elements = a;
            if (rotation == null && json.get("display") instanceof JsonObject d && d.get("gui") instanceof JsonObject gui) {
                rotation = vec(gui.get("rotation"), 0);
                translation = vec(gui.get("translation"), 0);
                scale = vec(gui.get("scale"), 1);
            }
            if (frontLight == null && json.get("gui_light") != null && json.get("gui_light").isJsonPrimitive()) {
                frontLight = json.get("gui_light").getAsString().equals("front");
            }
            current = json.get("parent") != null && json.get("parent").isJsonPrimitive() ? json.get("parent").getAsString() : null;
        }
        // No display.gui anywhere in the chain: the game draws the model untransformed (front view, whole slot).
        return new Chain(textureMap, elements, false, rotation != null ? rotation : new double[3], translation != null ? translation : new double[3],
            scale != null ? scale : new double[]{1, 1, 1}, frontLight != null && frontLight);
    }

    /** A model's JSON from the sources, else the vanilla stand-in; parents are cached for the whole pass. */
    private @Nullable JsonObject model(String namespace, String path, boolean parent) {
        String key = namespace + ":" + path;
        var cached = parents.get(key);
        if (cached != null) return cached.orElse(null);
        byte[] bytes = read.apply("assets/" + namespace + "/models/" + path + ".json");
        if (bytes == null && namespace.equals("minecraft")) bytes = BuiltinModels.get(path);
        var json = json(bytes);
        if (parent || (bytes != null && namespace.equals("minecraft") && BuiltinModels.has(path))) parents.put(key, Optional.ofNullable(json));
        return json;
    }

    private static double[] vec(@Nullable JsonElement e, double fallback) {
        var out = new double[]{fallback, fallback, fallback};
        if (e != null && e.isJsonArray()) {
            var a = e.getAsJsonArray();
            for (int i = 0; i < 3 && i < a.size(); i++) out[i] = a.get(i).getAsDouble();
        }
        return out;
    }

    private static @Nullable String deref(Map<String, String> textures, @Nullable String value) {
        for (int i = 0; value != null && value.startsWith("#") && i < MAX_DEPTH; i++) value = textures.get(value.substring(1));
        return value == null || value.startsWith("#") ? null : value;
    }

    // ---- textures ----------------------------------------------------------------------------------------------------

    /** The texture's first animation frame (the whole texture if it is not animated); null if unreadable. */
    private @Nullable Frame texture(String id) {
        var cached = textures.get(id);
        if (cached != null) return cached.orElse(null);
        var frame = loadTexture(id);
        textures.put(id, Optional.ofNullable(frame));
        if (frame != null) cachedPixels += frame.argb().length;
        for (var it = textures.entrySet().iterator(); cachedPixels > CACHED_PIXELS && textures.size() > 1 && it.hasNext(); ) {
            var eldest = it.next();
            if (eldest.getKey().equals(id)) continue;
            cachedPixels -= eldest.getValue().map(f -> f.argb().length).orElse(0);
            it.remove();
        }
        return frame;
    }

    private @Nullable Frame loadTexture(String id) {
        var split = IconFallback.split(id);
        String file = "assets/" + split[0] + "/textures/" + split[1] + ".png";
        byte[] png = read.apply(file);
        if (png == null) return null;
        int[] size = PngReader.size(png);
        if (size == null || size[0] <= 0 || size[1] <= 0) return null;
        int w = size[0], h = size[1];
        int fw = w, fh = h, fx = 0, fy = 0;
        if (w != h) {
            // Animated textures: the frame size from the .mcmeta (square by default) and the first frame's index.
            var meta = json(read.apply(file + ".mcmeta"));
            if (meta != null && meta.get("animation") instanceof JsonObject anim) {
                Integer mw = integer(anim.get("width")), mh = integer(anim.get("height"));
                if (mw != null) {
                    fw = mw;
                    fh = mh != null ? mh : h;
                } else if (mh != null) {
                    fh = mh;
                } else {
                    fw = fh = Math.min(w, h);
                }
                int index = 0;
                if (anim.get("frames") instanceof JsonArray frames && !frames.isEmpty()) {
                    var first = frames.get(0);
                    Integer i = first instanceof JsonObject fo ? integer(fo.get("index")) : integer(first);
                    if (i != null) index = i;
                }
                if (fw <= 0 || fh <= 0 || fw > w || fh > h) return null;
                int cols = w / fw;
                fx = (index % cols) * fw;
                fy = (index / cols) * fh;
                if (fy + fh > h) {
                    fx = 0;
                    fy = 0;
                }
            }
        }
        var image = PngReader.read(png, fy + fh);
        if (image == null || image.height() < fy + fh) return null;
        var argb = new int[fw * fh];
        for (int y = 0; y < fh; y++) System.arraycopy(image.argb(), (fy + y) * image.width() + fx, argb, y * fw, fw);
        return new Frame(fw, fh, argb);
    }

    // ---- flat items --------------------------------------------------------------------------------------------------

    private Result flat(Chain chain, int[] tints) {
        var out = new int[SIZE * SIZE];
        for (int layer = 0; layer < 16; layer++) {
            String ref = chain.textures().get("layer" + layer);
            if (ref == null) {
                if (layer == 0) return Result.skipped("no layer0");
                break;
            }
            String id = deref(chain.textures(), ref);
            var tex = id == null ? null : texture(id);
            if (tex == null) return Result.skipped("missing texture " + (id != null ? id : ref));
            int tint = layer < tints.length ? tints[layer] : 0xFFFFFFFF;
            for (int y = 0; y < SIZE; y++) {
                int ty = (int) ((y + 0.5) * tex.h() / SIZE);
                for (int x = 0; x < SIZE; x++) {
                    int c = tex.argb()[ty * tex.w() + (int) ((x + 0.5) * tex.w() / SIZE)];
                    if (c >>> 24 < CUTOUT) continue;
                    out[y * SIZE + x] = blend(shade(c, tint, 1.0), out[y * SIZE + x]);
                }
            }
        }
        return nonEmpty(out) ? new Result(Kind.FLAT, out, "") : Result.skipped("empty layers");
    }

    // ---- cubes -------------------------------------------------------------------------------------------------------

    private Result cube(Chain chain, int[] tints) {
        var elements = chain.elements();
        if (elements == null || elements.isEmpty()) return Result.skipped("no elements");
        // Only full cubes: inverted copies (inner faces, only seen from inside) are left out, anything else is a shape.
        var cubes = new java.util.ArrayList<JsonObject>();
        for (var e : elements) {
            if (!(e instanceof JsonObject el)) return Result.skipped("bad element");
            double[] from = vec(el.get("from"), 0), to = vec(el.get("to"), 16);
            if (el.get("rotation") instanceof JsonObject r && r.get("angle") != null && r.get("angle").getAsDouble() != 0) {
                return Result.skipped("rotated element");
            }
            boolean inverted = false, full = true;
            for (int i = 0; i < 3; i++) {
                if (from[i] > to[i]) inverted = true;
                if (Math.min(from[i], to[i]) > 0.01 || Math.max(from[i], to[i]) < 15.99) full = false;
            }
            if (!full) return Result.skipped("not a plain cube (" + elements.size() + " element" + (elements.size() == 1 ? ")" : "s)"));
            if (!inverted) cubes.add(el);
        }
        if (cubes.isEmpty()) return Result.skipped("no outer faces");

        var rotation = rotationMatrix(chain.rotation());
        double[] scale = new double[3], translation = new double[3];
        for (int i = 0; i < 3; i++) {
            scale[i] = Math.clamp(Math.abs(chain.scale()[i]), 0.01, 4);
            translation[i] = Math.clamp(chain.translation()[i], -80, 80) / 16.0;
        }
        var lights = chain.frontLight() ? FRONT_LIGHTS : SIDE_LIGHTS;
        var out = new int[SIZE * SIZE];
        for (var el : cubes) {
            if (!(el.get("faces") instanceof JsonObject faces)) continue;
            for (String dir : FACES) {
                if (!(faces.get(dir) instanceof JsonObject face)) continue;
                // Outward normal in view space (inverse-transpose of the scale), culled when it points away.
                double[] n = NORMALS.get(dir);
                double[] vn = mul(rotation, new double[]{n[0] / scale[0], n[1] / scale[1], n[2] / scale[2]});
                double len = Math.sqrt(vn[0] * vn[0] + vn[1] * vn[1] + vn[2] * vn[2]);
                if (len < EPS || vn[2] / len <= 1e-6) continue;
                String ref = face.get("texture") != null && face.get("texture").isJsonPrimitive() ? face.get("texture").getAsString() : null;
                String id = deref(chain.textures(), ref);
                var tex = id == null ? null : texture(id);
                if (tex == null) return Result.skipped("missing texture " + (id != null ? id : String.valueOf(ref)));
                double[] uv = face.get("uv") instanceof JsonArray a && a.size() == 4
                    ? new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble()}
                    : new double[]{0, 0, 16, 16};
                int rot = face.get("rotation") != null ? Math.floorMod(face.get("rotation").getAsInt() / 90, 4) : 0;
                int tintIndex = face.get("tintindex") != null ? face.get("tintindex").getAsInt() : -1;
                int tint = tintIndex >= 0 && tintIndex < tints.length ? tints[tintIndex] : 0xFFFFFFFF;
                double light = light(lights, vn[0] / len, vn[1] / len, vn[2] / len);
                var screen = new double[4][];
                var uvs = new double[4][];
                for (int i = 0; i < 4; i++) {
                    int[] c = CORNERS.get(dir)[i];
                    double[] v = {(c[0] - 0.5) * scale[0], (c[1] - 0.5) * scale[1], (c[2] - 0.5) * scale[2]};
                    v = mul(rotation, v);
                    screen[i] = new double[]{SIZE / 2.0 + SIZE * (v[0] + translation[0]), SIZE / 2.0 - SIZE * (v[1] + translation[1])};
                    int k = (i + rot) % 4; // face rotation turns the uv corners
                    uvs[i] = new double[]{k == 0 || k == 1 ? uv[0] : uv[2], k == 0 || k == 3 ? uv[1] : uv[3]};
                }
                raster(screen, uvs, tex, tint, light, out);
            }
        }
        return nonEmpty(out) ? new Result(Kind.CUBE, out, "") : Result.skipped("no visible faces");
    }

    /** Fills the parallelogram face (corners top-left, bottom-left, bottom-right, top-right) at pixel centres. */
    private static void raster(double[][] s, double[][] uv, Frame tex, int tint, double light, int[] out) {
        double ax = s[3][0] - s[0][0], ay = s[3][1] - s[0][1]; // top-left → top-right
        double bx = s[1][0] - s[0][0], by = s[1][1] - s[0][1]; // top-left → bottom-left
        double det = ax * by - ay * bx;
        if (Math.abs(det) < EPS) return;
        double minX = SIZE, minY = SIZE, maxX = 0, maxY = 0;
        for (var p : s) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        int x0 = Math.max(0, (int) Math.floor(minX)), x1 = Math.min(SIZE - 1, (int) Math.ceil(maxX));
        int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(SIZE - 1, (int) Math.ceil(maxY));
        double du1 = uv[3][0] - uv[0][0], dv1 = uv[3][1] - uv[0][1];
        double du2 = uv[1][0] - uv[0][0], dv2 = uv[1][1] - uv[0][1];
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double dx = x + 0.5 - s[0][0], dy = y + 0.5 - s[0][1];
                double p = (dx * by - dy * bx) / det, q = (ax * dy - ay * dx) / det;
                if (p < -EPS || p > 1 + EPS || q < -EPS || q > 1 + EPS) continue;
                int c = tex.at(uv[0][0] + p * du1 + q * du2, uv[0][1] + p * dv1 + q * dv2);
                if (c >>> 24 < CUTOUT) continue;
                out[y * SIZE + x] = blend(shade(c, tint, light), out[y * SIZE + x]);
            }
        }
    }

    // ---- maths -------------------------------------------------------------------------------------------------------

    /** Rx · Ry · Rz for the gui rotation in degrees (the game's rotationXYZ: z first, then y, then x). */
    private static double[][] rotationMatrix(double[] deg) {
        return mul(mul(rot(0, deg[0]), rot(1, deg[1])), rot(2, deg[2]));
    }

    private static double[][] rot(int axis, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return switch (axis) {
            case 0 -> new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}};
            case 1 -> new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
            default -> new double[][]{{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
        };
    }

    private static double[][] mul(double[][] a, double[][] b) {
        var out = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) out[i][j] += a[i][k] * b[k][j];
        return out;
    }

    private static double[] mul(double[][] m, double[] v) {
        return new double[]{m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2], m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }

    /**
     * The two light directions the game uses for items in the GUI, computed like its {@code Lighting}: the world
     * lights (0.2, 1, −0.7) and (−0.2, 1, 0.7) turned by the GUI's fixed poses (and, for 3D items, flipped in y).
     */
    private static double[][] guiLights(boolean side) {
        double[][] pose = side
            ? mul(mul(mul(mul(new double[][]{{1, 0, 0}, {0, -1, 0}, {0, 0, 1}}, rotRad(1, 1.0821041)), rotRad(0, 3.2375858)), rotRad(1, -Math.PI / 8)),
                rotRad(0, Math.PI * 3 / 4))
            : mul(rotRad(1, -Math.PI / 8), rotRad(0, Math.PI * 3 / 4));
        return new double[][]{normalize(mul(pose, normalize(new double[]{0.2, 1, -0.7}))), normalize(mul(pose, normalize(new double[]{-0.2, 1, 0.7})))};
    }

    private static double[][] rotRad(int axis, double rad) {
        return rot(axis, Math.toDegrees(rad));
    }

    private static double[] normalize(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    /** The game's item lighting: 40 % ambient + 60 % of both directional lights, capped at 1. Normal in view space. */
    private static double light(double[][] lights, double nx, double ny, double nz) {
        double gy = -ny; // GUI space is y-down
        double d0 = lights[0][0] * nx + lights[0][1] * gy + lights[0][2] * nz;
        double d1 = lights[1][0] * nx + lights[1][1] * gy + lights[1][2] * nz;
        return Math.min(1.0, 0.4 + 0.6 * (Math.max(0, d0) + Math.max(0, d1)));
    }

    /** Texel × tint × light, rounded like an 8-bit render target. */
    static int shade(int argb, int tint, double light) {
        int a = argb >>> 24;
        int r = (int) Math.round(((argb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255.0 * light);
        int g = (int) Math.round(((argb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255.0 * light);
        int b = (int) Math.round((argb & 0xFF) * (tint & 0xFF) / 255.0 * light);
        return a << 24 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    /**
     * Blends like the game's translucent item rendering into the icon atlas (colour: src·α + dst·(1−α), alpha:
     * α + dst·(1−α)), so semi-transparent texels come out as in client renders.
     */
    static int blend(int src, int dst) {
        int sa = src >>> 24;
        if (sa == 255) return src;
        double a = sa / 255.0, k = 1 - a;
        int da = dst >>> 24;
        int r = (int) Math.round(((src >> 16) & 0xFF) * a + ((dst >> 16) & 0xFF) * k);
        int g = (int) Math.round(((src >> 8) & 0xFF) * a + ((dst >> 8) & 0xFF) * k);
        int b = (int) Math.round((src & 0xFF) * a + (dst & 0xFF) * k);
        int outA = (int) Math.round(sa + da * k);
        return Math.min(255, outA) << 24 | r << 16 | g << 8 | b;
    }

    private static boolean nonEmpty(int[] argb) {
        for (int c : argb) if (c >>> 24 != 0) return true;
        return false;
    }

    // ---- json --------------------------------------------------------------------------------------------------------

    private static @Nullable JsonObject json(byte @Nullable [] bytes) {
        if (bytes == null || bytes.length > MAX_JSON) return null;
        try {
            var e = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String string(@Nullable JsonElement e, String fallback) {
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static @Nullable Integer integer(@Nullable JsonElement e) {
        try {
            return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
