package ai.ezfox.cccloud.uplink;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Builds widgets from Lua arguments. Plain Java: options arrive as a {@code Map<?, ?>} (what CC gives us for a table)
 * and errors are reported as {@link WidgetException} with a message meant for the Lua programmer.
 */
public final class WidgetFactory {
    public static final Set<String> COLORS = Set.of("cyan", "green", "yellow", "red", "gray");
    public static final Set<String> ALERT_LEVELS = Set.of("info", "warn", "crit");

    public static final class WidgetException extends Exception {
        public WidgetException(String message) {
            super(message);
        }
    }

    private WidgetFactory() {
    }

    public static String checkId(@Nullable Object id) throws WidgetException {
        if (!(id instanceof String s) || !Json.isValidId(s)) {
            throw new WidgetException("bad widget id " + describe(id) + " (expected 1-32 characters of a-z, 0-9 or _)");
        }
        return s;
    }

    public static String checkText(String what, @Nullable Object text, int max) throws WidgetException {
        if (!(text instanceof String s)) throw new WidgetException(what + " must be a string, got " + describe(text));
        if (s.length() > max) throw new WidgetException(what + " is too long (" + s.length() + " > " + max + " characters)");
        return s;
    }

    public static Widget create(WidgetType type, String id, @Nullable Object label, @Nullable Map<?, ?> rawOpts) throws WidgetException {
        checkId(id);
        String labelText = type == WidgetType.LABEL ? null : checkText("label", label, Limits.LABEL);
        JsonObject opts = new JsonObject();
        JsonElement value = null;
        Map<?, ?> o = rawOpts == null ? Map.of() : rawOpts;

        switch (type) {
            case BUTTON -> {
                allowKeys(type, o, "color", "confirm", "pin");
                pin(opts, o);
                if (o.get("color") != null) opts.addProperty("color", color(o.get("color")));
                if (o.get("confirm") != null) opts.addProperty("confirm", bool("confirm", o.get("confirm")));
            }
            case TOGGLE -> {
                allowKeys(type, o, "value", "color", "pin");
                pin(opts, o);
                if (o.get("color") != null) opts.addProperty("color", color(o.get("color")));
                value = new JsonPrimitive(o.get("value") != null && bool("value", o.get("value")));
            }
            case SLIDER -> {
                allowKeys(type, o, "min", "max", "step", "value", "unit", "color", "pin");
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
                if (o.get("unit") != null) opts.addProperty("unit", checkText("unit", o.get("unit"), Limits.UNIT));
                if (o.get("color") != null) opts.addProperty("color", color(o.get("color")));
                value = Json.number(ActionValidator.snap(v, min, max, step));
            }
            case GAUGE -> {
                allowKeys(type, o, "min", "max", "unit", "warn", "crit", "value", "color");
                double min = num("min", o.get("min"), 0);
                double max = num("max", o.get("max"), 100);
                if (!(min < max)) throw new WidgetException("gauge min must be smaller than max");
                opts.add("min", Json.number(min));
                opts.add("max", Json.number(max));
                if (o.get("unit") != null) opts.addProperty("unit", checkText("unit", o.get("unit"), Limits.UNIT));
                if (o.get("warn") != null) opts.add("warn", Json.number(num("warn", o.get("warn"), 0)));
                if (o.get("crit") != null) opts.add("crit", Json.number(num("crit", o.get("crit"), 0)));
                if (o.get("color") != null) opts.addProperty("color", color(o.get("color")));
                if (o.get("value") != null) value = Json.number(num("value", o.get("value"), 0));
            }
            case LABEL -> value = new JsonPrimitive(checkText("text", label, Limits.STRING_VALUE));
        }

        if (opts.toString().getBytes(StandardCharsets.UTF_8).length > Limits.OPTS_BYTES) {
            throw new WidgetException("options are too large (max " + Limits.OPTS_BYTES + " bytes)");
        }
        return new Widget(id, type, labelText, opts, value);
    }

    /** Validates a value passed to {@code set(id, value)} for the given widget and returns it as JSON. */
    public static JsonElement valueFor(Widget widget, @Nullable Object value) throws WidgetException {
        return switch (widget.type()) {
            case BUTTON -> throw new WidgetException("button '" + widget.id() + "' has no value");
            case TOGGLE -> {
                if (!(value instanceof Boolean b)) throw new WidgetException("toggle '" + widget.id() + "' expects a boolean, got " + describe(value));
                yield new JsonPrimitive(b);
            }
            case SLIDER -> {
                double v = finite("value", value);
                var o = widget.opts();
                double min = o.get("min").getAsDouble(), max = o.get("max").getAsDouble(), step = o.get("step").getAsDouble();
                if (v < min || v > max) throw new WidgetException("slider value " + fmt(v) + " is outside " + fmt(min) + ".." + fmt(max));
                yield Json.number(ActionValidator.snap(v, min, max, step));
            }
            case GAUGE -> Json.number(finite("value", value));
            case LABEL -> {
                if (value instanceof Number n) yield new JsonPrimitive(fmt(n.doubleValue()));
                yield new JsonPrimitive(checkText("text", value, Limits.STRING_VALUE));
            }
        };
    }

    private static void pin(JsonObject opts, Map<?, ?> o) throws WidgetException {
        if (o.get("pin") != null && bool("pin", o.get("pin"))) opts.addProperty("pin", true);
    }

    private static void allowKeys(WidgetType type, Map<?, ?> opts, String... allowed) throws WidgetException {
        var allowedSet = Set.of(allowed);
        for (var key : opts.keySet()) {
            if (!(key instanceof String k) || !allowedSet.contains(k)) {
                throw new WidgetException("unknown option " + describe(key) + " for " + type.id() + " (allowed: " + String.join(", ", allowed) + ")");
            }
        }
    }

    private static String color(Object value) throws WidgetException {
        if (value instanceof String s && COLORS.contains(s)) return s;
        throw new WidgetException("bad color " + describe(value) + " (expected cyan, green, yellow, red or gray)");
    }

    private static boolean bool(String name, Object value) throws WidgetException {
        if (value instanceof Boolean b) return b;
        throw new WidgetException(name + " must be a boolean, got " + describe(value));
    }

    private static double num(String name, @Nullable Object value, double def) throws WidgetException {
        return value == null ? def : finite(name, value);
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
