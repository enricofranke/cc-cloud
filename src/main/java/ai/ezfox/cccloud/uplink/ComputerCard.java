package ai.ezfox.cccloud.uplink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The automatic Computer card (docs/SPEC.md v0.3 "Auto mode" and "Web protocol for the auto Computer card"): shown by
 * an uplink on a CC computer as long as no Lua program defined widgets. Pure Java; the uplink fills the values and
 * executes the actions.
 */
public final class ComputerCard {
    public static final String POWER = "power";
    public static final String TURN_ON = "turn_on";
    public static final String REBOOT = "reboot";
    public static final String SHUTDOWN = "shutdown";
    public static final String REDSTONE = "redstone";
    /** Order of the sides in the redstone_sides value. */
    public static final String[] SIDES = {"top", "bottom", "left", "right", "front", "back"};

    /** An action on the card. */
    public sealed interface Action {
    }

    public record Power(String what) implements Action {
    }

    public record SetOutput(String side, int level) implements Action {
    }

    private ComputerCard() {
    }

    public static List<Widget> widgets() {
        var power = opts("onColor", "green", "offColor", "gray");
        power.addProperty("span", 2);
        return List.of(
            new Widget(POWER, WidgetType.INDICATOR, "Computer on", power, new JsonPrimitive(false)),
            new Widget(TURN_ON, WidgetType.BUTTON, "Turn on", opts("color", "green"), null),
            new Widget(REBOOT, WidgetType.BUTTON, "Reboot", confirm("yellow"), null),
            new Widget(SHUTDOWN, WidgetType.BUTTON, "Shut down", confirm("red"), null),
            new Widget(REDSTONE, WidgetType.REDSTONE_SIDES, "Redstone", new JsonObject(), sides(new int[6], new int[6]))
        );
    }

    /** The redstone_sides value: [{side, out, in}] for the six sides in {@link #SIDES} order. */
    public static JsonArray sides(int[] out, int[] in) {
        var array = new JsonArray(6);
        for (int i = 0; i < SIDES.length; i++) {
            var row = new JsonObject();
            row.addProperty("side", SIDES[i]);
            row.addProperty("out", out[i]);
            row.addProperty("in", in[i]);
            array.add(row);
        }
        return array;
    }

    /**
     * Maps a validated action on the card to what the computer should do; null for read-only widgets.
     *
     * @param op the validated operation (set_output on the redstone widget), else null
     */
    public static @Nullable Action action(String widgetId, @Nullable JsonElement value, @Nullable Op op) {
        return switch (widgetId) {
            case TURN_ON, REBOOT, SHUTDOWN -> new Power(widgetId);
            case REDSTONE -> op instanceof Op.SetOutput set ? new SetOutput(set.side(), set.value()) : null;
            default -> null;
        };
    }

    private static JsonObject opts(String... kv) {
        var json = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) json.addProperty(kv[i], kv[i + 1]);
        return json;
    }

    private static JsonObject confirm(String color) {
        var json = opts("color", color);
        json.addProperty("confirm", true);
        return json;
    }
}
