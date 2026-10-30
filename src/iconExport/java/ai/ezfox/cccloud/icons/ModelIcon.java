package ai.ezfox.cccloud.icons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;

/**
 * Dev tool ({@link IconExport}): draws an item's inventory icon from its model JSON without a game client, 32×32.
 * <ul>
 *   <li>{@code item/generated} models: the layers stacked (with the item definition's constant/default tints).</li>
 *   <li>Models with elements (blocks, most machine items): every face drawn with the model's {@code display.gui}
 *       transform (blocks: 30°/225°, scale 0.625), back faces culled, a depth buffer, nearest-neighbour texels and
 *       inventory-style lighting (top brightest, left face lighter than the right one).</li>
 *   <li>Special renderers (chests, beds, banners, heads), entity models and custom model loaders: not drawn (null);
 *       the caller falls back to the flat texture or the letter tile.</li>
 * </ul>
 */
final class ModelIcon {
    static final int SIZE = 32;
    private static final int MAX_DEPTH = 12;
    /** Vertex order per face as in the game's FaceInfo: top-left, bottom-left, bottom-right, top-right seen from outside. */
    private static final Map<String, int[][]> CORNERS = Map.of(
        "down", new int[][]{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
        "up", new int[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
        "north", new int[][]{{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
        "south", new int[][]{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
        "west", new int[][]{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
        "east", new int[][]{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}});
    /** Light directions in view space (x right, y up, z towards the viewer), checked against client-rendered icons. */
    private static final double[] LIGHT_0 = norm(-0.55, 0.85, 0.45);
    private static final double[] LIGHT_1 = norm(0.25, 0.55, 0.8);
    private static final double AMBIENT = 0.4;
    private static final double DIFFUSE = 0.6;

    private final Function<String, byte @Nullable []> read;
    private final Map<String, Texture> textures = new HashMap<>();

    record Texture(int w, int h, int[] argb) {
        int at(double u, double v) {
            int x = Math.min(w - 1, Math.max(0, (int) Math.floor(u / 16.0 * w)));
            int y = Math.min(h - 1, Math.max(0, (int) Math.floor(v / 16.0 * h)));
            return argb[y * w + x];
        }
    }

    /** What the item definition points to: a plain model with its tints, or a special renderer. */
    record Pick(@Nullable String model, int[] tints, boolean special) {
    }

    /** A loaded model chain: merged textures (child wins), the first elements and gui transform found. */
    record Model(Map<String, String> textures, @Nullable JsonArray elements, double[] rotation, double[] translation,
                 double[] scale, boolean generated, boolean frontLight) {
    }

    ModelIcon(Function<String, byte @Nullable []> read) {
        this.read = read;
    }

    /** True when the item is drawn by a special renderer (chest, bed, banner…), so a flat texture would be wrong. */
    boolean isSpecial(String namespace, String path) {
        var pick = pick(namespace, path);
        return pick != null && pick.special();
    }

    /** ARGB pixels (32×32), or null when the item cannot be drawn from its model. */
    int @Nullable [] render(String namespace, String path) {
        var pick = pick(namespace, path);
        if (pick != null && pick.special()) return null;
        String modelId = pick != null && pick.model() != null ? pick.model() : namespace + ":item/" + path;
        int[] tints = pick != null ? pick.tints() : new int[0];
        var model = load(modelId);
        if (model == null) return null;
        if (model.generated()) return layers(model, tints);
        if (model.elements() != null && !model.elements().isEmpty()) return elements(model, tints);
        return null;
    }

    // ------------------------------------------------------------------------------------------ item definition

    private @Nullable Pick pick(String namespace, String path) {
        var def = json(read.apply("assets/" + namespace + "/items/" + path + ".json"));
        return def == null ? null : pick(def.get("model"), 0);
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
        String type = obj.has("type") ? obj.get("type").getAsString().replace("minecraft:", "") : "model";
        switch (type) {
            case "model" -> {
                if (obj.get("model") instanceof JsonElement m && m.isJsonPrimitive()) return new Pick(m.getAsString(), tints(obj.get("tints")), false);
                return null;
            }
            case "special" -> {
                return new Pick(null, new int[0], true);
            }
            default -> {
                // condition / select / range_dispatch / composite: the fallback branch first, then anything else
                for (String key : List.of("fallback", "on_false", "on_true", "models", "cases", "entries", "model")) {
                    var found = pick(obj.get(key), depth + 1);
                    if (found != null) return found;
                }
                return null;
            }
        }
    }

    /** Tint colours per tint index: constants and the sources' default colours (grass/foliage approximated). */
    private static int[] tints(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) return new int[0];
        var array = element.getAsJsonArray();
        var out = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            out[i] = 0xFFFFFFFF;
            if (!(array.get(i) instanceof JsonObject t)) continue;
            String type = t.has("type") ? t.get("type").getAsString().replace("minecraft:", "") : "";
            JsonElement value = t.has("value") ? t.get("value") : t.get("default");
            Integer rgb = color(value);
            if (rgb != null) out[i] = 0xFF000000 | rgb;
            else if (type.equals("grass")) out[i] = 0xFF7CBD6B;
            else if (type.equals("foliage")) out[i] = 0xFF48B518;
        }
        return out;
    }

    private static @Nullable Integer color(@Nullable JsonElement value) {
        if (value == null) return null;
        try {
            if (value.isJsonPrimitive()) return value.getAsInt() & 0xFFFFFF;
            if (value.isJsonArray() && value.getAsJsonArray().size() >= 3) {
                var a = value.getAsJsonArray();
                return (int) (a.get(0).getAsDouble() * 255) << 16 | (int) (a.get(1).getAsDouble() * 255) << 8 | (int) (a.get(2).getAsDouble() * 255);
            }
        } catch (RuntimeException ignored) {
            // not a colour
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------ model chain

    @Nullable Model load(String modelId) {
        var textureMap = new HashMap<String, String>();
        JsonArray elements = null;
        double[] rotation = null, translation = null, scale = null;
        Boolean frontLight = null;
        boolean generated = false;
        String current = modelId;
        boolean found = false;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            var id = IconFallback.split(current);
            if (id[1].equals("builtin/generated")) {
                generated = true;
                break;
            }
            if (id[1].startsWith("builtin/")) return null; // entity renderers
            var json = json(read.apply("assets/" + id[0] + "/models/" + id[1] + ".json"));
            if (json == null) break;
            found = true;
            if (json.has("loader")) return null; // custom model loaders (OBJ, composite, …)
            if (json.get("textures") instanceof JsonObject t) {
                for (var e : t.entrySet()) {
                    String ref = IconFallback.textureRef(e.getValue());
                    if (ref != null) textureMap.putIfAbsent(e.getKey(), ref);
                }
            }
            if (elements == null && json.get("elements") instanceof JsonArray a) elements = a;
            if (rotation == null && json.get("display") instanceof JsonObject d && d.get("gui") instanceof JsonObject gui) {
                rotation = vec(gui.get("rotation"), 0);
                translation = vec(gui.get("translation"), 0);
                scale = vec(gui.get("scale"), 1);
            }
            if (frontLight == null && json.has("gui_light")) frontLight = json.get("gui_light").getAsString().equals("front");
            current = json.get("parent") != null && json.get("parent").isJsonPrimitive() ? json.get("parent").getAsString() : null;
        }
        if (!found) return null;
        if (generated) elements = null; // generated items are their layers, whatever a child declares
        return new Model(textureMap, elements, rotation != null ? rotation : new double[3], translation != null ? translation : new double[3],
            scale != null ? scale : new double[]{1, 1, 1}, generated, frontLight != null && frontLight);
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

    private @Nullable Texture texture(@Nullable String id) {
        if (id == null) return null;
        if (textures.containsKey(id)) return textures.get(id);
        Texture t = null;
        var split = IconFallback.split(id);
        byte[] png = read.apply("assets/" + split[0] + "/textures/" + split[1] + ".png");
        if (png != null) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
                if (image != null) {
                    int w = image.getWidth();
                    int h = image.getHeight() > w && image.getHeight() % w == 0 ? w : image.getHeight(); // animation strip: first frame
                    t = new Texture(w, h, image.getRGB(0, 0, w, h, null, 0, w));
                }
            } catch (Exception e) {
                t = null;
            }
        }
        textures.put(id, t);
        return t;
    }

    // ------------------------------------------------------------------------------------------ generated items

    private int @Nullable [] layers(Model model, int[] tints) {
        var out = new int[SIZE * SIZE];
        boolean any = false;
        for (int layer = 0; layer < 8; layer++) {
            var tex = texture(deref(model.textures(), model.textures().get("layer" + layer)));
            if (tex == null) {
                if (layer == 0) return null;
                break;
            }
            int tint = layer < tints.length ? tints[layer] : 0xFFFFFFFF;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int c = tex.at((x + 0.5) / SIZE * 16, (y + 0.5) / SIZE * 16);
                    if (c >>> 24 == 0) continue;
                    out[y * SIZE + x] = over(multiply(c, tint, 1.0), out[y * SIZE + x]);
                    any = true;
                }
            }
        }
        return any ? out : null;
    }

    // ------------------------------------------------------------------------------------------ elements

    private record Quad(double[][] screen, double[][] uv, Texture tex, double shade, int tint, double depth) {
    }

    private int @Nullable [] elements(Model model, int[] tints) {
        var quads = new ArrayList<Quad>();
        for (var e : model.elements()) {
            if (!(e instanceof JsonObject el)) continue;
            double[] from = vec(el.get("from"), 0), to = vec(el.get("to"), 16);
            boolean shade = !el.has("shade") || el.get("shade").getAsBoolean();
            if (!(el.get("faces") instanceof JsonObject faces)) continue;
            for (var f : faces.entrySet()) {
                int[][] corners = CORNERS.get(f.getKey());
                if (corners == null || !(f.getValue() instanceof JsonObject face)) continue;
                var tex = texture(deref(model.textures(), face.has("texture") ? face.get("texture").getAsString() : null));
                if (tex == null) continue;
                double[] uv = face.get("uv") instanceof JsonArray a && a.size() == 4
                    ? new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble()}
                    : defaultUv(f.getKey(), from, to);
                int rot = face.has("rotation") ? Math.floorMod(face.get("rotation").getAsInt() / 90, 4) : 0;
                var pts = new double[4][];
                var uvs = new double[4][];
                for (int i = 0; i < 4; i++) {
                    double[] p = {corners[i][0] == 1 ? to[0] : from[0], corners[i][1] == 1 ? to[1] : from[1], corners[i][2] == 1 ? to[2] : from[2]};
                    p = elementRotation(el, p);
                    pts[i] = toScreen(model, p);
                    int c = (i + rot) % 4;
                    uvs[i] = new double[]{c == 0 || c == 1 ? uv[0] : uv[2], c == 0 || c == 3 ? uv[1] : uv[3]};
                }
                double[] n = normal(pts);
                if (n == null || n[2] <= 1e-6) continue; // back face (or degenerate)
                double light = !shade ? 1 : model.frontLight() ? 1 : lighting(n);
                int tintIndex = face.has("tintindex") ? face.get("tintindex").getAsInt() : -1;
                int tint = tintIndex >= 0 && tintIndex < tints.length ? tints[tintIndex] : 0xFFFFFFFF;
                double depth = (pts[0][4] + pts[1][4] + pts[2][4] + pts[3][4]) / 4;
                quads.add(new Quad(pts, uvs, tex, light, tint, depth));
            }
        }
        if (quads.isEmpty()) return null;
        quads.sort(Comparator.comparingDouble(Quad::depth)); // back to front, so translucent faces blend correctly
        var out = new int[SIZE * SIZE];
        var zbuf = new double[SIZE * SIZE];
        Arrays.fill(zbuf, Double.NEGATIVE_INFINITY);
        for (var q : quads) raster(q, out, zbuf);
        for (int c : out) if (c >>> 24 != 0) return out;
        return null;
    }

    static double[] defaultUv(String face, double[] f, double[] t) {
        return switch (face) {
            case "down" -> new double[]{f[0], 16 - t[2], t[0], 16 - f[2]};
            case "up" -> new double[]{f[0], f[2], t[0], t[2]};
            case "north" -> new double[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
            case "south" -> new double[]{f[0], 16 - t[1], t[0], 16 - f[1]};
            case "west" -> new double[]{f[2], 16 - t[1], t[2], 16 - f[1]};
            default -> new double[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]}; // east
        };
    }

    private static double[] elementRotation(JsonObject el, double[] p) {
        if (!(el.get("rotation") instanceof JsonObject r) || !r.has("axis") || !r.has("angle")) return p;
        double[] o = vec(r.get("origin"), 8);
        double a = Math.toRadians(r.get("angle").getAsDouble());
        String axis = r.get("axis").getAsString();
        double x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
        double c = Math.cos(a), s = Math.sin(a);
        double[] q = switch (axis) {
            case "x" -> new double[]{x, y * c - z * s, y * s + z * c};
            case "y" -> new double[]{x * c + z * s, y, -x * s + z * c};
            default -> new double[]{x * c - y * s, x * s + y * c, z};
        };
        if (r.has("rescale") && r.get("rescale").getAsBoolean() && Math.abs(c) > 1e-6) {
            double k = 1 / Math.abs(c);
            if (!axis.equals("x")) q[0] *= k;
            if (!axis.equals("y")) q[1] *= k;
            if (!axis.equals("z")) q[2] *= k;
        }
        return new double[]{q[0] + o[0], q[1] + o[1], q[2] + o[2]};
    }

    /**
     * Model space (0–16) → {screen x, screen y, view x, view y, view z}: centred on the block, gui transform
     * (translate · rotate XYZ · scale), 16 gui pixels per block; view space is right-handed with z towards the viewer.
     */
    private static double[] toScreen(Model m, double[] p) {
        double[] v = {(p[0] / 16 - 0.5) * m.scale()[0], (p[1] / 16 - 0.5) * m.scale()[1], (p[2] / 16 - 0.5) * m.scale()[2]};
        v = rot(v, 2, m.rotation()[2]);
        v = rot(v, 1, m.rotation()[1]);
        v = rot(v, 0, m.rotation()[0]);
        for (int i = 0; i < 3; i++) v[i] += clamp(m.translation()[i], -80, 80) / 16;
        double k = SIZE / 16.0;
        return new double[]{(8 + 16 * v[0]) * k, (8 - 16 * v[1]) * k, v[0], v[1], v[2]};
    }

    /** Right-handed rotation about axis 0 = x, 1 = y, 2 = z. */
    private static double[] rot(double[] v, int axis, double deg) {
        if (deg == 0) return v;
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return switch (axis) {
            case 0 -> new double[]{v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c};
            case 1 -> new double[]{v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c};
            default -> new double[]{v[0] * c - v[1] * s, v[0] * s + v[1] * c, v[2]};
        };
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Outward face normal in view space from the corners (top-left, bottom-left, …, top-right seen from outside). */
    private static double @Nullable [] normal(double[][] s) {
        double ax = s[1][2] - s[0][2], ay = s[1][3] - s[0][3], az = s[1][4] - s[0][4];
        double bx = s[3][2] - s[0][2], by = s[3][3] - s[0][3], bz = s[3][4] - s[0][4];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return len < 1e-12 ? null : new double[]{nx / len, ny / len, nz / len};
    }

    private static double lighting(double[] n) {
        double d = Math.max(0, dot(n, LIGHT_0)) + Math.max(0, dot(n, LIGHT_1));
        return Math.min(1, AMBIENT + DIFFUSE * d);
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] norm(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    private static void raster(Quad q, int[] out, double[] zbuf) {
        double minX = SIZE, minY = SIZE, maxX = 0, maxY = 0;
        for (var p : q.screen()) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        int x0 = Math.max(0, (int) Math.floor(minX)), x1 = Math.min(SIZE - 1, (int) Math.ceil(maxX));
        int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(SIZE - 1, (int) Math.ceil(maxY));
        double[][] s = q.screen();
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double px = x + 0.5, py = y + 0.5;
                double[] w = bary(s[0], s[1], s[2], px, py);
                int a = 0, b = 1, c = 2;
                if (w == null) {
                    w = bary(s[0], s[2], s[3], px, py);
                    a = 0;
                    b = 2;
                    c = 3;
                }
                if (w == null) continue;
                double z = w[0] * s[a][4] + w[1] * s[b][4] + w[2] * s[c][4];
                int i = y * SIZE + x;
                if (z < zbuf[i] - 1e-6) continue;
                double u = w[0] * q.uv()[a][0] + w[1] * q.uv()[b][0] + w[2] * q.uv()[c][0];
                double v = w[0] * q.uv()[a][1] + w[1] * q.uv()[b][1] + w[2] * q.uv()[c][1];
                int color = q.tex().at(u, v);
                int alpha = color >>> 24;
                if (alpha < 26) continue; // cutout threshold (0.1)
                out[i] = over(multiply(color, q.tint(), q.shade()), out[i]);
                zbuf[i] = z;
            }
        }
    }

    /** Barycentric weights of (px, py) in the triangle, or null if outside (edges inclusive). */
    private static double @Nullable [] bary(double[] a, double[] b, double[] c, double px, double py) {
        double d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
        if (Math.abs(d) < 1e-12) return null;
        double wa = ((b[1] - c[1]) * (px - c[0]) + (c[0] - b[0]) * (py - c[1])) / d;
        double wb = ((c[1] - a[1]) * (px - c[0]) + (a[0] - c[0]) * (py - c[1])) / d;
        double wc = 1 - wa - wb;
        double eps = -1e-9;
        return wa >= eps && wb >= eps && wc >= eps ? new double[]{wa, wb, wc} : null;
    }

    private static int multiply(int argb, int tint, double shade) {
        int a = argb >>> 24;
        int r = (int) Math.round(((argb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255.0 * shade);
        int g = (int) Math.round(((argb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255.0 * shade);
        int b = (int) Math.round((argb & 0xFF) * (tint & 0xFF) / 255.0 * shade);
        return a << 24 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    /** Source-over blending of straight-alpha ARGB colours. */
    static int over(int src, int dst) {
        int sa = src >>> 24;
        if (sa == 255) return src;
        if (sa == 0) return dst;
        int da = dst >>> 24;
        double as = sa / 255.0, ad = da / 255.0 * (1 - as), ao = as + ad;
        if (ao <= 0) return 0;
        int r = (int) Math.round((((src >> 16) & 0xFF) * as + ((dst >> 16) & 0xFF) * ad) / ao);
        int g = (int) Math.round((((src >> 8) & 0xFF) * as + ((dst >> 8) & 0xFF) * ad) / ao);
        int b = (int) Math.round(((src & 0xFF) * as + (dst & 0xFF) * ad) / ao);
        return (int) Math.round(ao * 255) << 24 | r << 16 | g << 8 | b;
    }

    private static @Nullable JsonObject json(byte @Nullable [] bytes) {
        if (bytes == null) return null;
        try {
            var e = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
