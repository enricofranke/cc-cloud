package ai.ezfox.cccloud.uplink;

import ai.ezfox.cccloud.uplink.WidgetFactory.WidgetException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * One frame of a {@link WidgetType#CANVAS} widget: a list of draw commands checked here, on the server, before anything
 * reaches a browser (docs/SPEC.md "Canvas widget"). Nothing a script sends is passed on as markup: operations come from a
 * fixed list, colours are names of the palette or {@code #rrggbb[aa]}, icons are bundled names, items are registry ids,
 * text is plain text and every number is finite and bounded. The dashboard checks all of it again (cloud/web/src/lib/canvas.ts).
 * <p>
 * Commands (as Lua lists, first entry the operation):
 * <pre>
 * {"c", color}                      fill the surface
 * {"r", x, y, w, h, color}          filled rectangle
 * {"R", x, y, w, h, color, [line]}  rectangle outline
 * {"l", x1, y1, x2, y2, color, [line]}
 * {"o", x, y, r, color}             filled circle
 * {"O", x, y, r, color, [line]}     circle outline
 * {"p", color, x1, y1, x2, y2, ...} pixels
 * {"t", x, y, text, color, [size], ["l"|"c"|"r"]}
 * {"g", x, y, w, h, from, to, ["h"|"v"]}  gradient
 * {"i", x, y, icon, [size], [color]}      bundled pixel icon
 * {"s", x, y, item, [size]}         item icon
 * {"b", x, y, w, data, [scale]}     bitmap: rows of paint colours 0-f (space = transparent), w per row
 * </pre>
 * The JSON value is {@code {"canvas":1,"c":[[...],...]}} with every Lua list as an array (the marker lets in-game clients
 * leave frames out, see {@link #withoutFrames}).
 */
public final class CanvasFrame {
    /** The theme-aware colour names; the dashboard resolves them with the current theme. */
    public static final Set<String> THEME_COLORS = Set.of("bg", "panel", "text", "dim", "line", "accent", "gold", "cyan", "green", "yellow", "red", "purple", "orange", "gray");
    /** The sixteen colours of CC's {@code colors} API, in the order of their numbers (1, 2, 4, ...). */
    private static final String[] CC_COLORS = {"white", "orange", "magenta", "lightBlue", "yellow", "lime", "pink", "gray", "lightGray", "cyan", "purple", "blue", "brown",
        "green", "red", "black"};
    private static final Set<String> NAMES;
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?");
    /** What marks a message or section with frames in it. */
    private static final String MARKER = "\"canvas\":1";

    static {
        var names = new java.util.HashSet<>(THEME_COLORS);
        names.addAll(java.util.List.of(CC_COLORS));
        NAMES = Set.copyOf(names);
    }

    /** @param bytes the frame as JSON in UTF-8 bytes (what the rate limit counts) */
    public record Parsed(JsonObject value, int bytes) {
    }

    private CanvasFrame() {
    }

    /** A checked frame from a Lua list of commands. Stops at the first problem (and early when a frame gets too big). */
    public static Parsed parse(@Nullable Object raw) throws WidgetException {
        if (!(raw instanceof Map<?, ?> list)) throw new WidgetException("canvas commands must be a list of commands, got " + WidgetFactory.describe(raw));
        if (list.size() > Limits.CANVAS_COMMANDS) throw new WidgetException("too many canvas commands (" + list.size() + " > " + Limits.CANVAS_COMMANDS + ")");
        var commands = new JsonArray(list.size());
        var state = new State();
        for (int i = 1; i <= list.size(); i++) {
            Object entry = item(list, i);
            if (!(entry instanceof Map<?, ?> command)) throw new WidgetException("canvas command " + i + " must be a list like {\"r\", 0, 0, 10, 10, \"red\"}");
            try {
                commands.add(command(command, state));
            } catch (WidgetException e) {
                throw new WidgetException("canvas command " + i + ": " + e.getMessage());
            }
            if (state.size > Limits.CANVAS_FRAME_BYTES) throw tooBig(state.size);
        }
        var value = new JsonObject();
        value.addProperty("canvas", 1);
        value.add("c", commands);
        int bytes = Json.GSON.toJson(value).getBytes(StandardCharsets.UTF_8).length;
        if (bytes > Limits.CANVAS_FRAME_BYTES) throw tooBig(bytes);
        return new Parsed(value, bytes);
    }

    private static WidgetException tooBig(int bytes) {
        return new WidgetException("canvas frame is too large (at least " + bytes + " > " + Limits.CANVAS_FRAME_BYTES + " bytes)");
    }

    /** A colour: a palette name, {@code #rrggbb[aa]} or a number of CC's {@code colors} API. */
    public static String color(@Nullable Object value) throws WidgetException {
        if (value instanceof Number n) {
            double v = n.doubleValue();
            for (int i = 0; i < CC_COLORS.length; i++) if (v == 1 << i) return CC_COLORS[i];
        } else if (value instanceof String s && (NAMES.contains(s) || HEX.matcher(s).matches())) {
            return s;
        }
        throw new WidgetException("bad color " + WidgetFactory.describe(value) + " (expected a color name, #rrggbb or a colors.* value)");
    }

    private static final class State {
        int size;
        int items;
    }

    private static JsonArray command(Map<?, ?> c, State state) throws WidgetException {
        String op = item(c, 1) instanceof String s ? s : "";
        var out = new JsonArray(c.size());
        out.add(op);
        state.size += 4 + 3 * c.size();
        switch (op) {
            case "c" -> {
                arity(c, 2, 2);
                out.add(color(item(c, 2)));
            }
            case "r", "R" -> {
                arity(c, 6, op.equals("R") ? 7 : 6);
                coord(out, c, 2, 3);
                size(out, c, 4, 5);
                out.add(color(item(c, 6)));
                if (c.size() > 6) out.add(line(item(c, 7)));
            }
            case "l" -> {
                arity(c, 6, 7);
                coord(out, c, 2, 3, 4, 5);
                out.add(color(item(c, 6)));
                if (c.size() > 6) out.add(line(item(c, 7)));
            }
            case "o", "O" -> {
                arity(c, 5, op.equals("O") ? 6 : 5);
                coord(out, c, 2, 3);
                size(out, c, 4);
                out.add(color(item(c, 5)));
                if (c.size() > 5) out.add(line(item(c, 6)));
            }
            case "p" -> {
                if (c.size() < 4 || c.size() % 2 != 0) throw new WidgetException("pixels need a color and x, y pairs");
                if ((c.size() - 2) / 2 > Limits.CANVAS_PIXELS) throw new WidgetException("too many pixels (max " + Limits.CANVAS_PIXELS + " per command)");
                out.add(color(item(c, 2)));
                for (int i = 3; i <= c.size(); i++) coord(out, c, i);
            }
            case "t" -> {
                arity(c, 5, 7);
                coord(out, c, 2, 3);
                String text = WidgetFactory.checkText("text", item(c, 4), Limits.CANVAS_TEXT);
                if (!WidgetFactory.isPlainText(text)) throw new WidgetException("text must not contain control characters");
                state.size += text.length() * 2;
                out.add(text);
                out.add(color(item(c, 5)));
                if (c.size() > 5) {
                    Object size = item(c, 6);
                    out.add(size == null ? Json.number(8) : Json.number(whole("font size", size, Limits.CANVAS_FONT_MIN, Limits.CANVAS_FONT_MAX)));
                }
                if (c.size() > 6) out.add(oneOf("align", item(c, 7), Set.of("l", "c", "r")));
            }
            case "g" -> {
                arity(c, 7, 8);
                coord(out, c, 2, 3);
                size(out, c, 4, 5);
                out.add(color(item(c, 6)));
                out.add(color(item(c, 7)));
                if (c.size() > 7) out.add(oneOf("direction", item(c, 8), Set.of("h", "v")));
            }
            case "i" -> {
                arity(c, 4, 6);
                coord(out, c, 2, 3);
                if (!Icons.isValid(item(c, 4))) throw new WidgetException("bad icon " + WidgetFactory.describe(item(c, 4)));
                out.add((String) item(c, 4));
                if (c.size() > 4) {
                    Object size = item(c, 5);
                    out.add(size == null ? Json.number(16) : Json.number(whole("icon size", size, Limits.CANVAS_ICON_MIN, Limits.CANVAS_ICON_MAX)));
                }
                if (c.size() > 5) out.add(color(item(c, 6)));
            }
            case "s" -> {
                arity(c, 4, 5);
                if (++state.items > Limits.CANVAS_ITEMS) throw new WidgetException("too many item icons (max " + Limits.CANVAS_ITEMS + " per frame)");
                coord(out, c, 2, 3);
                if (!(item(c, 4) instanceof String name) || !Op.isItemName(name)) throw new WidgetException("bad item id " + WidgetFactory.describe(item(c, 4)));
                state.size += name.length();
                out.add(name);
                if (c.size() > 4) out.add(Json.number(whole("icon size", item(c, 5), Limits.CANVAS_ICON_MIN, Limits.CANVAS_ICON_MAX)));
            }
            case "b" -> {
                arity(c, 5, 6);
                coord(out, c, 2, 3);
                int w = whole("bitmap width", item(c, 4), 1, Limits.CANVAS_MAX_WIDTH);
                out.add(w);
                if (!(item(c, 5) instanceof String data) || data.isEmpty()) throw new WidgetException("bitmap data must be a string of colours 0-f");
                if (data.length() > Limits.CANVAS_BITMAP) throw new WidgetException("bitmap is too large (max " + Limits.CANVAS_BITMAP + " pixels)");
                if (data.length() % w != 0) throw new WidgetException("bitmap data must hold whole rows of " + w + " pixels");
                for (int i = 0; i < data.length(); i++) {
                    char ch = data.charAt(i);
                    if (ch != ' ' && (ch < '0' || ch > '9') && (ch < 'a' || ch > 'f')) throw new WidgetException("bitmap data may only hold 0-9, a-f and spaces");
                }
                state.size += data.length();
                out.add(data);
                if (c.size() > 5) out.add(Json.number(whole("bitmap scale", item(c, 6), 1, 16)));
            }
            default -> throw new WidgetException("unknown operation " + WidgetFactory.describe(item(c, 1)) + " (expected c, r, R, l, o, O, p, t, g, i, s or b)");
        }
        return out;
    }

    private static void arity(Map<?, ?> c, int min, int max) throws WidgetException {
        // holes in the middle of a list count as missing arguments: Lua's nil for "default" only works at the end
        if (c.size() < min || c.size() > max) throw new WidgetException("expected " + (min == max ? min : min + " to " + max) + " entries, got " + c.size());
    }

    private static void coord(JsonArray out, Map<?, ?> c, int... indexes) throws WidgetException {
        for (int i : indexes) out.add(round(number(item(c, i), -Limits.CANVAS_COORD)));
    }

    private static void size(JsonArray out, Map<?, ?> c, int... indexes) throws WidgetException {
        for (int i : indexes) out.add(round(number(item(c, i), 0)));
    }

    private static double number(@Nullable Object value, double min) throws WidgetException {
        if (value instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= min && n.doubleValue() <= Limits.CANVAS_COORD) return n.doubleValue();
        throw new WidgetException("bad number " + WidgetFactory.describe(value) + " (expected " + WidgetFactory.fmt(min) + " to " + Limits.CANVAS_COORD + ")");
    }

    /** One decimal is plenty on a surface of at most 480 pixels and keeps a frame small. */
    private static JsonPrimitive round(double v) {
        return Json.number(Math.round(v * 10) / 10d);
    }

    private static JsonPrimitive line(@Nullable Object value) throws WidgetException {
        if (value instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() > 0 && n.doubleValue() <= Limits.CANVAS_LINE_MAX) return round(Math.max(0.1, n.doubleValue()));
        throw new WidgetException("bad line width " + WidgetFactory.describe(value) + " (expected above 0, at most " + WidgetFactory.fmt(Limits.CANVAS_LINE_MAX) + ")");
    }

    private static int whole(String what, @Nullable Object value, int min, int max) throws WidgetException {
        if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && n.doubleValue() >= min && n.doubleValue() <= max) return n.intValue();
        throw new WidgetException(what + " must be a whole number between " + min + " and " + max + ", got " + WidgetFactory.describe(value));
    }

    private static String oneOf(String what, @Nullable Object value, Set<String> allowed) throws WidgetException {
        if (value instanceof String s && allowed.contains(s)) return s;
        throw new WidgetException("bad " + what + " " + WidgetFactory.describe(value));
    }

    /** An entry of a Lua list (CC hands numbers over as doubles; integers come from tests and other callers). */
    private static @Nullable Object item(Map<?, ?> list, int index) {
        Object entry = list.get((double) index);
        return entry != null ? entry : list.get(index);
    }

    // ---- in-game clients --------------------------------------------------------------------------------------------

    /**
     * A hub message or section as JSON text without canvas frames, or null when nothing is left of a {@code values}
     * message. Tablets and Cloud Displays do not draw canvases (they show a hint): frames stay off the game's network.
     * Text without frames comes back as it is, without being parsed.
     */
    public static @Nullable String withoutFrames(String json) {
        if (!json.contains(MARKER)) return json;
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return json;
        }
        if (!(root instanceof JsonObject message)) return json;
        if (message.get("s") instanceof JsonObject sections) {
            for (var section : sections.entrySet()) {
                if (section.getValue() instanceof JsonObject values) values.entrySet().removeIf(e -> isFrame(e.getValue()));
            }
            sections.entrySet().removeIf(e -> e.getValue() instanceof JsonObject values && values.size() == 0);
            if (sections.size() == 0) return null;
        }
        if (message.get("uplinks") instanceof JsonArray uplinks) for (var uplink : uplinks) if (uplink instanceof JsonObject u) stripWidgets(u);
        stripWidgets(message);
        return Json.GSON.toJson(message);
    }

    private static void stripWidgets(JsonObject section) {
        if (!(section.get("widgets") instanceof JsonArray widgets)) return;
        for (var widget : widgets) if (widget instanceof JsonObject w && isFrame(w.get("value"))) w.remove("value");
    }

    private static boolean isFrame(@Nullable JsonElement value) {
        return value instanceof JsonObject o && o.has("canvas") && o.has("c");
    }

    /** A section snapshot's canvases without their frames (a copy; the snapshot stays as it is). */
    public static JsonObject snapshotWithoutFrames(JsonObject snapshot) {
        var copy = snapshot.deepCopy();
        stripWidgets(copy);
        return copy;
    }
}
