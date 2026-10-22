package ai.ezfox.cccloud.uplink;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

/**
 * Validates an action from the dashboard against the widget definition before it reaches Lua. Lua scripts can rely on
 * only ever receiving: {@code true} for buttons, a boolean for toggles, an in-range number snapped to the step for
 * sliders and number inputs, one of the declared options for select/buttons, and plain text within maxLength for text
 * inputs. Bridge widgets only accept their structured {@link Op}s. Read-only widgets never produce actions.
 */
public final class ActionValidator {
    public record Result(boolean ok, @Nullable JsonElement value, @Nullable Op op, @Nullable String error) {
        static Result ok(JsonElement value) {
            return new Result(true, value, null, null);
        }

        static Result ok(Op op) {
            return new Result(true, null, op, null);
        }

        static Result fail(String error) {
            return new Result(false, null, null, error);
        }
    }

    private ActionValidator() {
    }

    public static Result validate(@Nullable Widget widget, @Nullable JsonElement value) {
        return validate(widget, value, null, false);
    }

    public static Result validate(@Nullable Widget widget, @Nullable JsonElement value, boolean pinVerified) {
        return validate(widget, value, null, pinVerified);
    }

    /**
     * @param op          the parsed operation if the action value was an object (see {@link Op#parse}).
     * @param pinVerified whether the mainframe PIN was checked for this action. Widgets with {@code opts.pin = true}
     *                    reject actions without it.
     */
    public static Result validate(@Nullable Widget widget, @Nullable JsonElement value, @Nullable Op op, boolean pinVerified) {
        if (widget == null) return Result.fail("unknown_widget");
        if (!widget.type().isInteractive()) return Result.fail("not_interactive");
        if (requiresPin(widget) && !pinVerified) return Result.fail("pin_required");
        if (widget.type().takesOps()) return validateOp(widget, op);
        if (op != null || value == null || !value.isJsonPrimitive()) return Result.fail("invalid_value");
        JsonPrimitive p = value.getAsJsonPrimitive();
        return switch (widget.type()) {
            case BUTTON -> p.isBoolean() && p.getAsBoolean() ? Result.ok(new JsonPrimitive(true)) : Result.fail("invalid_value");
            case TOGGLE -> p.isBoolean() ? Result.ok(new JsonPrimitive(p.getAsBoolean())) : Result.fail("invalid_value");
            case SLIDER -> validateRange(widget, p, true);
            case NUMBER -> validateRange(widget, p, widget.opts().has("step"));
            case SELECT, BUTTONS -> p.isString() && hasOption(widget, p.getAsString()) ? Result.ok(new JsonPrimitive(p.getAsString())) : Result.fail("invalid_value");
            case TEXT -> validateText(widget, p);
            default -> Result.fail("not_interactive");
        };
    }

    private static Result validateOp(Widget widget, @Nullable Op op) {
        if (op == null) return Result.fail("invalid_value");
        return switch (widget.type()) {
            case ITEM_SEARCH -> {
                if (!(op instanceof Op.Craft craft)) yield Result.fail("invalid_value");
                var max = widget.opts().get("max");
                if (max != null && craft.count() > max.getAsLong()) yield Result.fail("too_many");
                yield Result.ok(op);
            }
            case WATCHLIST -> op instanceof Op.WatchAdd || op instanceof Op.WatchRemove || op instanceof Op.WatchSetMin ? Result.ok(op) : Result.fail("invalid_value");
            case REDSTONE_SIDES -> op instanceof Op.SetOutput ? Result.ok(op) : Result.fail("invalid_value");
            default -> Result.fail("invalid_value");
        };
    }

    public static boolean requiresPin(Widget widget) {
        var pin = widget.opts().get("pin");
        return pin != null && pin.isJsonPrimitive() && pin.getAsJsonPrimitive().isBoolean() && pin.getAsBoolean();
    }

    public static boolean hasOption(Widget widget, String option) {
        var options = widget.opts().get("options");
        if (options == null || !options.isJsonArray()) return false;
        for (var o : options.getAsJsonArray()) if (o.getAsString().equals(option)) return true;
        return false;
    }

    private static Result validateRange(Widget widget, JsonPrimitive p, boolean snap) {
        if (!p.isNumber()) return Result.fail("invalid_value");
        double v = p.getAsDouble();
        if (!Double.isFinite(v)) return Result.fail("invalid_value");
        var opts = widget.opts();
        double min = opts.has("min") ? opts.get("min").getAsDouble() : 0;
        double max = opts.has("max") ? opts.get("max").getAsDouble() : 100;
        double step = opts.has("step") ? opts.get("step").getAsDouble() : 1;
        if (v < min || v > max) return Result.fail("invalid_value");
        return Result.ok(Json.number(snap ? snap(v, min, max, step) : v));
    }

    private static Result validateText(Widget widget, JsonPrimitive p) {
        if (!p.isString()) return Result.fail("invalid_value");
        String s = stripControl(p.getAsString());
        int maxLength = widget.opts().has("maxLength") ? widget.opts().get("maxLength").getAsInt() : Limits.STRING_VALUE;
        if (s.length() > maxLength) return Result.fail("invalid_value");
        return Result.ok(new JsonPrimitive(s));
    }

    /** Removes control characters (text inputs are plain text). */
    public static String stripControl(String s) {
        if (WidgetFactory.isPlainText(s)) return s;
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) if (!Character.isISOControl(s.charAt(i))) sb.append(s.charAt(i));
        return sb.toString();
    }

    /** Snaps a value onto the step grid starting at min and keeps it within min..max. */
    public static double snap(double v, double min, double max, double step) {
        if (step <= 0) return Math.min(max, Math.max(min, v));
        double snapped = min + Math.round((v - min) / step) * step;
        if (snapped > max) snapped -= step;
        if (snapped < min) snapped = min;
        // Remove floating point noise such as 0.30000000000000004.
        return Math.round(snapped * 1e9) / 1e9;
    }
}
