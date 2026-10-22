package ai.ezfox.cccloud.uplink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Builds widgets from Lua arguments. Plain Java: options arrive as a {@code Map<?, ?>} (what CC gives us for a table)
 * and errors are reported as {@link WidgetException} with a message meant for the Lua programmer.
 * <p>
 * Every widget accepts the layout options {@code page}, {@code group}, {@code span} (1-4), {@code order},
 * {@code color} and {@code icon}. Everything else is type specific; unknown keys are rejected.
 */
public final class WidgetFactory {
    public static final Set<String> COLORS = Set.of("cyan", "green", "yellow", "red", "gray");
    public static final Set<String> ALERT_LEVELS = Set.of("info", "warn", "crit");
    /** Layout options every widget accepts. */
    static final List<String> COMMON = List.of("page", "group", "span", "order", "color", "icon");
    /** Default upper bound for craft amounts when no config is available (unit tests). */
    public static final int DEFAULT_MAX_CRAFT = 4096;

    public static final class WidgetException extends Exception {
        public WidgetException(String message) {
            super(message);
        }
    }

    private WidgetFactory() {
    }

    public static String checkId(@Nullable Object id) throws WidgetException {
        return checkId("widget id", id);
    }

    public static String checkId(String what, @Nullable Object id) throws WidgetException {
        if (!(id instanceof String s) || !Json.isValidId(s)) {
            throw new WidgetException("bad " + what + " " + describe(id) + " (expected 1-32 characters of a-z, 0-9 or _)");
        }
        return s;
    }

    public static String checkText(String what, @Nullable Object text, int max) throws WidgetException {
        if (!(text instanceof String s)) throw new WidgetException(what + " must be a string, got " + describe(text));
        if (s.length() > max) throw new WidgetException(what + " is too long (" + s.length() + " > " + max + " characters)");
        return s;
    }

    public static Widget create(WidgetType type, String id, @Nullable Object label, @Nullable Map<?, ?> rawOpts) throws WidgetException {
        return create(type, id, label, rawOpts, DEFAULT_MAX_CRAFT);
    }

    /**
     * @param maxCraft upper bound for item_search {@code max} (the {@code maxCraftAmount} config).
     */
    public static Widget create(WidgetType type, String id, @Nullable Object label, @Nullable Map<?, ?> rawOpts, int maxCraft) throws WidgetException {
        checkId(id);
        String labelText = type == WidgetType.LABEL ? null : checkText("label", label, Limits.LABEL);
        JsonObject opts = new JsonObject();
        JsonElement value = null;
        Map<?, ?> o = rawOpts == null ? Map.of() : rawOpts;

        switch (type) {
            case BUTTON -> {
                allowKeys(type, o, "confirm", "pin");
                pin(opts, o);
                confirm(opts, o);
            }
            case TOGGLE -> {
                allowKeys(type, o, "value", "pin", "confirm");
                pin(opts, o);
                confirm(opts, o);
                value = new JsonPrimitive(o.get("value") != null && bool("value", o.get("value")));
            }
            case SLIDER -> {
                allowKeys(type, o, "min", "max", "step", "value", "unit", "pin");
                pin(opts, o);
                double min = num("min", o.get("min"), 0);
                double max = num("max", o.get("max"), 100);
                double step = num("step", o.get("step"), 1);
                if (!(min < max)) throw new WidgetException("slider min must be smaller than max");
                if (!(step > 0)) throw new WidgetException("slider step must be greater than 0");
                double v = num("value", o.get("value"), min);
                if (v < min || v > max) throw new WidgetException("slider value " + fmt(v) + " is outside " + fmt(min) + ".." + fmt(max));
                opts.add("min", Json.number(min));
                opts.add("max", Json.number(max));
                opts.add("step", Json.number(step));
                unit(opts, o);
                value = Json.number(ActionValidator.snap(v, min, max, step));
            }
            case GAUGE -> {
                allowKeys(type, o, "min", "max", "unit", "warn", "crit", "value");
                range(type, opts, o);
                unit(opts, o);
                if (o.get("warn") != null) opts.add("warn", Json.number(num("warn", o.get("warn"), 0)));
                if (o.get("crit") != null) opts.add("crit", Json.number(num("crit", o.get("crit"), 0)));
                if (o.get("value") != null) value = Json.number(num("value", o.get("value"), 0));
            }
            case LABEL -> {
                allowKeys(type, o);
                value = new JsonPrimitive(checkText("text", label, Limits.STRING_VALUE));
            }
            case CHART -> {
                allowKeys(type, o, "min", "max", "unit", "points", "interval", "style", "value");
                if (o.get("min") != null || o.get("max") != null) range(type, opts, o);
                unit(opts, o);
                int points = intIn("points", o.get("points"), Limits.CHART_POINTS_DEFAULT, Limits.CHART_POINTS_MIN, Limits.CHART_POINTS_MAX);
                double interval = num("interval", o.get("interval"), 1);
                if (interval < Limits.CHART_INTERVAL_MIN || interval > Limits.CHART_INTERVAL_MAX) {
                    throw new WidgetException("chart interval must be between " + fmt(Limits.CHART_INTERVAL_MIN) + " and " + fmt(Limits.CHART_INTERVAL_MAX) + " seconds");
                }
                opts.addProperty("points", points);
                opts.add("interval", Json.number(interval));
                if (o.get("style") != null) opts.addProperty("style", oneOf("style", o.get("style"), Set.of("line", "sparkline")));
                if (o.get("value") != null) value = Json.number(num("value", o.get("value"), 0));
            }
            case PROGRESS -> {
                allowKeys(type, o, "min", "max", "unit", "value");
                range(type, opts, o);
                unit(opts, o);
                if (o.get("value") != null) value = Json.number(num("value", o.get("value"), 0));
            }
            case INDICATOR -> {
                allowKeys(type, o, "value", "onColor", "offColor");
                value = new JsonPrimitive(o.get("value") != null && bool("value", o.get("value")));
                if (o.get("onColor") != null) opts.addProperty("onColor", color(o.get("onColor")));
                if (o.get("offColor") != null) opts.addProperty("offColor", color(o.get("offColor")));
            }
            case SELECT -> {
                allowKeys(type, o, "options", "value", "pin", "confirm");
                pin(opts, o);
                confirm(opts, o);
                var options = options(type, o.get("options"));
                opts.add("options", toArray(options));
                String v = o.get("value") == null ? options.getFirst() : checkText("value", o.get("value"), Limits.OPTION);
                if (!options.contains(v)) throw new WidgetException("select value " + describe(v) + " is not one of the options");
                value = new JsonPrimitive(v);
            }
            case NUMBER -> {
                allowKeys(type, o, "min", "max", "step", "value", "unit", "pin", "confirm");
                pin(opts, o);
                confirm(opts, o);
                double min = num("min", o.get("min"), Limits.NUMBER_DEFAULT_MIN);
                double max = num("max", o.get("max"), Limits.NUMBER_DEFAULT_MAX);
                if (!(min < max)) throw new WidgetException("number min must be smaller than max");
                opts.add("min", Json.number(min));
                opts.add("max", Json.number(max));
                if (o.get("step") != null) {
                    double step = num("step", o.get("step"), 1);
                    if (!(step > 0)) throw new WidgetException("number step must be greater than 0");
                    opts.add("step", Json.number(step));
                }
                unit(opts, o);
                double v = num("value", o.get("value"), Math.max(min, Math.min(max, 0)));
                if (v < min || v > max) throw new WidgetException("number value " + fmt(v) + " is outside " + fmt(min) + ".." + fmt(max));
                value = Json.number(opts.has("step") ? ActionValidator.snap(v, min, max, opts.get("step").getAsDouble()) : v);
            }
            case TEXT -> {
                allowKeys(type, o, "maxLength", "value", "placeholder", "pin", "confirm");
                pin(opts, o);
                confirm(opts, o);
                int maxLength = intIn("maxLength", o.get("maxLength"), 64, 1, Limits.STRING_VALUE);
                opts.addProperty("maxLength", maxLength);
                if (o.get("placeholder") != null) opts.addProperty("placeholder", checkText("placeholder", o.get("placeholder"), Limits.LABEL));
                String v = o.get("value") == null ? "" : checkText("value", o.get("value"), maxLength);
                if (!isPlainText(v)) throw new WidgetException("text value must not contain control characters");
                value = new JsonPrimitive(v);
            }
            case BUTTONS -> {
                allowKeys(type, o, "options", "pin", "confirm");
                pin(opts, o);
                confirm(opts, o);
                opts.add("options", toArray(options(type, o.get("options"))));
            }
            case ITEM_SEARCH -> {
                allowKeys(type, o, "bridge", "max", "pin", "confirm");
                pin(opts, o);
                confirm(opts, o);
                opts.addProperty("max", intIn("max", o.get("max"), maxCraft, 1, maxCraft));
            }
            case WATCHLIST, JOBS -> allowKeys(type, o, "bridge");
            case REDSTONE_SIDES -> throw new WidgetException("redstone_sides is only used by the automatic Computer card");
        }
        common(opts, o);

        if (opts.toString().getBytes(StandardCharsets.UTF_8).length > Limits.OPTS_BYTES) {
            throw new WidgetException("options are too large (max " + Limits.OPTS_BYTES + " bytes)");
        }
        return new Widget(id, type, labelText, opts, value);
    }

    /** Validates a value passed to {@code set(id, value)} for the given widget and returns it as JSON. */
    public static JsonElement valueFor(Widget widget, @Nullable Object value) throws WidgetException {
        var o = widget.opts();
        return switch (widget.type()) {
            case BUTTON, BUTTONS, ITEM_SEARCH -> throw new WidgetException(widget.type().id() + " '" + widget.id() + "' has no value");
            case WATCHLIST, JOBS -> throw new WidgetException(widget.type().id() + " '" + widget.id() + "' gets its value from the bridge");
            case REDSTONE_SIDES -> throw new WidgetException("redstone_sides '" + widget.id() + "' is managed by the Computer card");
            case TOGGLE, INDICATOR -> {
                if (!(value instanceof Boolean b)) throw new WidgetException(widget.type().id() + " '" + widget.id() + "' expects a boolean, got " + describe(value));
                yield new JsonPrimitive(b);
            }
            case SLIDER -> {
                double v = finite("value", value);
                double min = o.get("min").getAsDouble(), max = o.get("max").getAsDouble(), step = o.get("step").getAsDouble();
                if (v < min || v > max) throw new WidgetException("slider value " + fmt(v) + " is outside " + fmt(min) + ".." + fmt(max));
                yield Json.number(ActionValidator.snap(v, min, max, step));
            }
            case NUMBER -> {
                double v = finite("value", value);
                double min = o.get("min").getAsDouble(), max = o.get("max").getAsDouble();
                if (v < min || v > max) throw new WidgetException("number value " + fmt(v) + " is outside " + fmt(min) + ".." + fmt(max));
                yield Json.number(o.has("step") ? ActionValidator.snap(v, min, max, o.get("step").getAsDouble()) : v);
            }
            case GAUGE, CHART, PROGRESS -> Json.number(finite("value", value));
            case LABEL -> {
                if (value instanceof Number n) yield new JsonPrimitive(fmt(n.doubleValue()));
                yield new JsonPrimitive(checkText("text", value, Limits.STRING_VALUE));
            }
            case SELECT -> {
                String s = checkText("value", value, Limits.OPTION);
                if (!ActionValidator.hasOption(widget, s)) throw new WidgetException("select value " + describe(s) + " is not one of the options");
                yield new JsonPrimitive(s);
            }
            case TEXT -> {
                String s = checkText("value", value, o.get("maxLength").getAsInt());
                if (!isPlainText(s)) throw new WidgetException("text value must not contain control characters");
                yield new JsonPrimitive(s);
            }
        };
    }

    /** {@code cloud.page(id, title, {icon=})} */
    public static JsonObject page(Object id, @Nullable Object title, @Nullable Map<?, ?> rawOpts) throws WidgetException {
        var json = new JsonObject();
        json.addProperty("id", checkId("page id", id));
        json.addProperty("title", checkText("page title", title, Limits.TITLE));
        Map<?, ?> o = rawOpts == null ? Map.of() : rawOpts;
        allowKeys("page", o, List.of("icon"));
        if (o.get("icon") != null) json.addProperty("icon", icon(o.get("icon")));
        return json;
    }

    /** {@code cloud.group(id, title, {page=, cols=1..4})} */
    public static JsonObject group(Object id, @Nullable Object title, @Nullable Map<?, ?> rawOpts) throws WidgetException {
        var json = new JsonObject();
        json.addProperty("id", checkId("group id", id));
        json.addProperty("title", checkText("group title", title, Limits.TITLE));
        Map<?, ?> o = rawOpts == null ? Map.of() : rawOpts;
        allowKeys("group", o, List.of("page", "cols"));
        if (o.get("page") != null) json.addProperty("page", checkId("page id", o.get("page")));
        if (o.get("cols") != null) json.addProperty("cols", intIn("cols", o.get("cols"), 1, 1, 4));
        return json;
    }

    /** No control characters (the dashboard shows text as plain text, but newlines/escapes are never useful). */
    public static boolean isPlainText(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isISOControl(s.charAt(i))) return false;
        }
        return true;
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    private static void common(JsonObject opts, Map<?, ?> o) throws WidgetException {
        if (o.get("page") != null) opts.addProperty("page", checkId("page id", o.get("page")));
        if (o.get("group") != null) opts.addProperty("group", checkId("group id", o.get("group")));
        if (o.get("span") != null) opts.addProperty("span", intIn("span", o.get("span"), 1, 1, 4));
        if (o.get("order") != null) opts.addProperty("order", intIn("order", o.get("order"), 0, 0, Limits.ORDER_MAX));
        if (o.get("color") != null) opts.addProperty("color", color(o.get("color")));
        if (o.get("icon") != null) opts.addProperty("icon", icon(o.get("icon")));
    }

    private static void range(WidgetType type, JsonObject opts, Map<?, ?> o) throws WidgetException {
        double min = num("min", o.get("min"), 0);
        double max = num("max", o.get("max"), 100);
        if (!(min < max)) throw new WidgetException(type.id() + " min must be smaller than max");
        opts.add("min", Json.number(min));
        opts.add("max", Json.number(max));
    }

    private static void unit(JsonObject opts, Map<?, ?> o) throws WidgetException {
        if (o.get("unit") != null) opts.addProperty("unit", checkText("unit", o.get("unit"), Limits.UNIT));
    }

    private static void pin(JsonObject opts, Map<?, ?> o) throws WidgetException {
        if (o.get("pin") != null && bool("pin", o.get("pin"))) opts.addProperty("pin", true);
    }

    private static void confirm(JsonObject opts, Map<?, ?> o) throws WidgetException {
        if (o.get("confirm") != null) opts.addProperty("confirm", bool("confirm", o.get("confirm")));
    }

    /** A Lua list ({@code {"a","b"}} arrives as {1.0="a", 2.0="b"}) of 1..16 unique, non-empty options. */
    private static List<String> options(WidgetType type, @Nullable Object raw) throws WidgetException {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) throw new WidgetException(type.id() + " needs options={...} with at least one entry");
        if (map.size() > Limits.MAX_OPTIONS) throw new WidgetException("too many options (max " + Limits.MAX_OPTIONS + ")");
        var result = new LinkedHashSet<String>();
        for (int i = 1; i <= map.size(); i++) {
            Object entry = map.get((double) i);
            if (entry == null) entry = map.get(i);
            if (entry == null) throw new WidgetException("options must be a list like {\"low\", \"high\"}");
            String s = checkText("option", entry, Limits.OPTION);
            if (s.isEmpty() || !isPlainText(s)) throw new WidgetException("options must be non-empty plain text");
            if (!result.add(s)) throw new WidgetException("duplicate option " + describe(s));
        }
        return new ArrayList<>(result);
    }

    private static JsonArray toArray(List<String> list) {
        var array = new JsonArray(list.size());
        for (var s : list) array.add(s);
        return array;
    }

    private static void allowKeys(WidgetType type, Map<?, ?> opts, String... specific) throws WidgetException {
        var allowed = new ArrayList<String>(COMMON.size() + specific.length);
        allowed.addAll(List.of(specific));
        allowed.addAll(COMMON);
        allowKeys(type.id(), opts, allowed);
    }

    private static void allowKeys(String what, Map<?, ?> opts, List<String> allowed) throws WidgetException {
        var allowedSet = new HashSet<>(allowed);
        for (var key : opts.keySet()) {
            if (!(key instanceof String k) || !allowedSet.contains(k)) {
                throw new WidgetException("unknown option " + describe(key) + " for " + what + " (allowed: " + String.join(", ", allowed) + ")");
            }
        }
    }

    private static String color(Object value) throws WidgetException {
        if (value instanceof String s && COLORS.contains(s)) return s;
        throw new WidgetException("bad color " + describe(value) + " (expected cyan, green, yellow, red or gray)");
    }

    private static String icon(Object value) throws WidgetException {
        if (Icons.isValid(value)) return (String) value;
        throw new WidgetException("bad icon " + describe(value) + " (expected one of " + String.join(", ", Icons.ALL.stream().sorted().toList()) + ")");
    }

    private static String oneOf(String name, Object value, Set<String> allowed) throws WidgetException {
        if (value instanceof String s && allowed.contains(s)) return s;
        throw new WidgetException("bad " + name + " " + describe(value) + " (expected " + String.join(" or ", allowed.stream().sorted().toList()) + ")");
    }

    private static boolean bool(String name, Object value) throws WidgetException {
        if (value instanceof Boolean b) return b;
        throw new WidgetException(name + " must be a boolean, got " + describe(value));
    }

    private static double num(String name, @Nullable Object value, double def) throws WidgetException {
        return value == null ? def : finite(name, value);
    }

    private static int intIn(String name, @Nullable Object value, int def, int min, int max) throws WidgetException {
        if (value == null) return def;
        double v = finite(name, value);
        if (v != Math.rint(v) || v < min || v > max) throw new WidgetException(name + " must be a whole number between " + min + " and " + max + ", got " + describe(value));
        return (int) v;
    }

    private static double finite(String name, @Nullable Object value) throws WidgetException {
        if (value instanceof Number n && Double.isFinite(n.doubleValue())) return n.doubleValue();
        throw new WidgetException(name + " must be a finite number, got " + describe(value));
    }

    static String fmt(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? Long.toString((long) v) : Double.toString(v);
    }

    static String describe(@Nullable Object value) {
        if (value == null) return "nil";
        if (value instanceof String s) return "'" + (s.length() > 40 ? s.substring(0, 40) + "…" : s) + "'";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) return "table";
        return value.getClass().getSimpleName();
    }
}
