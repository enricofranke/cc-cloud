package ai.ezfox.cccloud.uplink;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The automatic Computer card (docs/SPEC.md v0.3 "Auto mode"): shown by an uplink on a CC computer as long as no Lua
 * program defined widgets. Pure Java; the uplink fills the values and executes the actions.
 */
public final class ComputerCard {
    public static final String POWER = "power";
    public static final String TURN_ON = "turn_on";
    public static final String REBOOT = "reboot";
    public static final String SHUTDOWN = "shutdown";
    public static final String[] SIDES = {"top", "bottom", "left", "right", "front", "back"};
    public static final String GROUP_REDSTONE = "redstone";
    public static final String GROUP_ADVANCED = "advanced";

    /** An action on the card. */
    public sealed interface Action {
    }

    public record Power(String what) implements Action {
    }

    public record SetOutput(String side, int level) implements Action {
    }

    private ComputerCard() {
    }

    public static String inputId(String side) {
        return side + "_in";
    }

    public static String outputId(String side) {
        return side + "_out";
    }

    public static String levelId(String side) {
        return side + "_level";
    }

    private static String cap(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    public static List<Widget> widgets() {
        var list = new ArrayList<Widget>();
        list.add(new Widget(POWER, WidgetType.INDICATOR, "Running", opts("onColor", "green", "offColor", "gray"), new JsonPrimitive(false)));
        list.add(new Widget(TURN_ON, WidgetType.BUTTON, "Turn on", opts("color", "green"), null));
        list.add(new Widget(REBOOT, WidgetType.BUTTON, "Reboot", confirm("yellow"), null));
        list.add(new Widget(SHUTDOWN, WidgetType.BUTTON, "Shut down", confirm("red"), null));
        for (var side : SIDES) {
            list.add(new Widget(inputId(side), WidgetType.INDICATOR, cap(side) + " in", opts("group", GROUP_REDSTONE, "onColor", "red", "offColor", "gray"), new JsonPrimitive(false)));
            list.add(new Widget(outputId(side), WidgetType.TOGGLE, cap(side) + " out", opts("group", GROUP_REDSTONE), new JsonPrimitive(false)));
            var level = opts("group", GROUP_ADVANCED);
            level.addProperty("min", 0);
            level.addProperty("max", 15);
            level.addProperty("step", 1);
            list.add(new Widget(levelId(side), WidgetType.SLIDER, cap(side) + " level", level, new JsonPrimitive(0)));
        }
        return list;
    }

    public static List<JsonObject> groups() {
        var redstone = new JsonObject();
        redstone.addProperty("id", GROUP_REDSTONE);
        redstone.addProperty("title", "Redstone");
        redstone.addProperty("cols", 2);
        var advanced = new JsonObject();
        advanced.addProperty("id", GROUP_ADVANCED);
        advanced.addProperty("title", "Advanced");
        advanced.addProperty("cols", 2);
        return List.of(redstone, advanced);
    }

    /**
     * Maps a validated action on the card to what the computer should do; null for read-only widgets.
     *
     * @param value the validated value (true for buttons, boolean for toggles, 0..15 for sliders)
     */
    public static Action action(String widgetId, com.google.gson.JsonElement value) {
        switch (widgetId) {
            case TURN_ON, REBOOT, SHUTDOWN -> {
                return new Power(widgetId);
            }
            default -> {
                for (var side : SIDES) {
                    if (widgetId.equals(outputId(side))) return new SetOutput(side, value.getAsBoolean() ? 15 : 0);
                    if (widgetId.equals(levelId(side))) return new SetOutput(side, value.getAsInt());
                }
                return null;
            }
        }
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
