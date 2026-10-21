package ai.ezfox.cccloud.uplink;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

/**
 * Validates an action from the dashboard against the widget definition before it reaches Lua. Lua scripts can rely on
 * only ever receiving: {@code true} for buttons, a boolean for toggles, and an in-range number snapped to the step for
 * sliders. Gauges and labels never produce actions.
 */
public final class ActionValidator {
    public record Result(boolean ok, @Nullable JsonElement value, @Nullable String error) {
        static Result ok(JsonElement value) {
            return new Result(true, value, null);
        }

        static Result fail(String error) {
            return new Result(false, null, error);
        }
    }

    private ActionValidator() {
    }

    public static Result validate(@Nullable Widget widget, @Nullable JsonElement value) {
        return validate(widget, value, false);
    }

    /**
     * @param pinVerified whether the mainframe PIN was checked for this action. Widgets with {@code opts.pin = true}
     *                    reject actions without it.
     */
    public static Result validate(@Nullable Widget widget, @Nullable JsonElement value, boolean pinVerified) {
        if (widget == null) return Result.fail("unknown_widget");
        if (!widget.type().isInteractive()) return Result.fail("not_interactive");
        if (requiresPin(widget) && !pinVerified) return Result.fail("pin_required");
        if (value == null || !value.isJsonPrimitive()) return Result.fail("invalid_value");
        JsonPrimitive p = value.getAsJsonPrimitive();
        return switch (widget.type()) {
            case BUTTON -> p.isBoolean() && p.getAsBoolean() ? Result.ok(new JsonPrimitive(true)) : Result.fail("invalid_value");
            case TOGGLE -> p.isBoolean() ? Result.ok(new JsonPrimitive(p.getAsBoolean())) : Result.fail("invalid_value");
            case SLIDER -> validateSlider(widget, p);
            default -> Result.fail("not_interactive");
        };
    }

    public static boolean requiresPin(Widget widget) {
        var pin = widget.opts().get("pin");
        return pin != null && pin.isJsonPrimitive() && pin.getAsJsonPrimitive().isBoolean() && pin.getAsBoolean();
    }

    private static Result validateSlider(Widget widget, JsonPrimitive p) {
        if (!p.isNumber()) return Result.fail("invalid_value");
        double v = p.getAsDouble();
        if (!Double.isFinite(v)) return Result.fail("invalid_value");
        var opts = widget.opts();
        double min = opts.has("min") ? opts.get("min").getAsDouble() : 0;
        double max = opts.has("max") ? opts.get("max").getAsDouble() : 100;
        double step = opts.has("step") ? opts.get("step").getAsDouble() : 1;
        if (v < min || v > max) return Result.fail("invalid_value");
        return Result.ok(Json.number(snap(v, min, max, step)));
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
