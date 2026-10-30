package ai.ezfox.cccloud.sensor;

import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The dashboard card of an Advanced Cloud Sensor (docs/SPEC.md "Advanced Cloud Sensor"): its activity (who is inside,
 * the last events, today's count), "Occupied", the arm switch and the settings (what it detects, notifications, ignore
 * my team, redstone output, allow and deny lists, the zone). Player names are <i>private values</i>: the card carries a
 * public value next to them ({@code "a player"}, empty lists) that the dashboard hub sends to everyone who may not see
 * names (viewers, share links, Cloud Displays). Widgets are built through {@link WidgetFactory} like Lua widgets. Pure
 * Java, unit tested.
 */
public final class SensorCards {
    public static final String TEMPLATE = "sensor";
    public static final String ACTIVITY = "activity";
    public static final String OCCUPIED = "occupied";
    public static final String ARMED = "armed";
    public static final String DETECT = "detect";
    public static final String NOTIFY = "notify";
    public static final String TEAM = "team";
    public static final String OUTPUT = "output";
    public static final String ALLOW = "allow";
    public static final String DENY = "deny";
    public static final String ZONE_X = "zone_x";
    public static final String ZONE_Y = "zone_y";
    public static final String ZONE_Z = "zone_z";
    public static final String PLACE = "place";
    /** Rows of the "inside" list (more are counted in {@code more}). */
    public static final int MAX_INSIDE_ROWS = 16;

    /** What the card shows. {@code names} = player names are reported at all (server config). */
    public record State(SensorSettings settings, List<SensorEntry> inside, List<SensorLog.Event> events, int today, @Nullable String problem,
                        boolean names, int maxX, int maxY, int maxZ, boolean pin, boolean confirm) {
        public State(SensorSettings settings, List<SensorEntry> inside, List<SensorLog.Event> events, int today, @Nullable String problem,
                     boolean names, int maxX, int maxY, int maxZ) {
            this(settings, inside, events, today, problem, names, maxX, maxY, maxZ, false, false);
        }
    }

    /** The widgets (full values) and, for widgets that carry player names, the value everyone else gets. */
    public record Card(List<Widget> widgets, Map<String, JsonElement> publicValues) {
    }

    private SensorCards() {
    }

    public static Card build(State s) {
        var widgets = new ArrayList<Widget>();
        var pub = new LinkedHashMap<String, JsonElement>();
        var settings = s.settings();
        var full = activity(s, true);
        var open = activity(s, false);
        var activityOpts = new JsonObject();
        activityOpts.addProperty("order", 0);
        activityOpts.addProperty("span", 4);
        activityOpts.addProperty("icon", "eye");
        widgets.add(new Widget(ACTIVITY, WidgetType.SENSOR_LOG, "Activity", activityOpts, full));
        if (!full.equals(open)) pub.put(ACTIVITY, open);
        widgets.add(widget(WidgetType.INDICATOR, OCCUPIED, "Occupied", opts("value", !s.inside().isEmpty(), "onColor", "red", "history", "24h",
            "order", 1, "span", 2)));
        // the device's PIN / confirmation (its screen) protect arming and disarming
        widgets.add(widget(WidgetType.TOGGLE, ARMED, "Armed", opts("value", settings.armed(), "pin", s.pin() ? true : null, "confirm", s.confirm() ? true : null,
            "order", 2, "span", 2)));
        widgets.add(widget(WidgetType.SELECT, DETECT, "Detect", opts("options", options(modeLabels()), "value", settings.filter().mode().label,
            "order", 3, "span", 2)));
        widgets.add(widget(WidgetType.SELECT, NOTIFY, "Notify", opts("options", options(alertLabels()), "value", settings.alert().label, "order", 4,
            "span", 2)));
        widgets.add(widget(WidgetType.TOGGLE, TEAM, "Ignore my team", opts("value", settings.filter().ignoreTeam(), "order", 5, "span", 2)));
        widgets.add(widget(WidgetType.TOGGLE, OUTPUT, "Redstone output", opts("value", settings.redstone(), "order", 6, "span", 2)));
        String allow = SensorFilter.join(settings.filter().allow());
        String deny = SensorFilter.join(settings.filter().deny());
        widgets.add(widget(WidgetType.TEXT, ALLOW, "Only these players", opts("value", allow, "maxLength", 256, "placeholder", "every player", "order", 7,
            "span", 2)));
        widgets.add(widget(WidgetType.TEXT, DENY, "Never these players", opts("value", deny, "maxLength", 256, "placeholder", "nobody", "order", 8,
            "span", 2)));
        if (!allow.isEmpty()) pub.put(ALLOW, new JsonPrimitive(""));
        if (!deny.isEmpty()) pub.put(DENY, new JsonPrimitive(""));
        zone(widgets, ZONE_X, "Zone width (X)", settings.sizeX(), s.maxX(), 9);
        zone(widgets, ZONE_Y, "Zone height (Y)", settings.sizeY(), s.maxY(), 10);
        zone(widgets, ZONE_Z, "Zone depth (Z)", settings.sizeZ(), s.maxZ(), 11);
        widgets.add(widget(WidgetType.SELECT, PLACE, "Zone", opts("options", options(placeLabels()), "value", settings.placement().label, "order", 12,
            "span", 1)));
        return new Card(List.copyOf(widgets), Map.copyOf(pub));
    }

    private static void zone(List<Widget> widgets, String id, String label, int value, int max, int order) {
        if (max <= 1) return; // a fixed size of 1
        widgets.add(widget(WidgetType.NUMBER, id, label, opts("min", 1, "max", max, "step", 1, "value", Math.min(max, value), "order", order, "span", 1)));
    }

    /**
     * The activity value: {@code {armed, today, zone, problem?, summary, inside:[{k,n?,id?,c?}], more?, events:[{t,in,k,n?,id}]}}
     * (events newest first). {@code names} = with player names.
     */
    public static JsonObject activity(State s, boolean names) {
        boolean show = names && s.names();
        var json = new JsonObject();
        json.addProperty("armed", s.settings().armed());
        json.addProperty("today", s.today());
        json.addProperty("zone", s.settings().clamped(s.maxX(), s.maxY(), s.maxZ()).zoneText());
        if (s.problem() != null) json.addProperty("problem", s.problem());
        json.addProperty("summary", summary(s, show));
        // inside: players by name (or counted), the others grouped by name
        var groups = new LinkedHashMap<String, JsonObject>();
        for (var e : s.inside()) {
            boolean named = show && e.named();
            String key = e.kind().id + "|" + (named ? e.name() : e.kind() == SensorKind.PLAYER ? "" : e.label());
            var row = groups.get(key);
            if (row == null) {
                row = new JsonObject();
                row.addProperty("k", e.kind().id);
                if (named || e.kind() != SensorKind.PLAYER) row.addProperty("n", named ? e.name() : e.label());
                if (e.kind() != SensorKind.PLAYER) row.addProperty("id", e.type());
                row.addProperty("c", 0);
                groups.put(key, row);
            }
            row.addProperty("c", row.get("c").getAsInt() + 1);
        }
        var inside = new JsonArray();
        int more = 0;
        for (var row : groups.values()) {
            if (inside.size() < MAX_INSIDE_ROWS) inside.add(row);
            else more += row.get("c").getAsInt();
        }
        json.add("inside", inside);
        if (more > 0) json.addProperty("more", more);
        var events = new JsonArray();
        for (int i = s.events().size() - 1; i >= 0; i--) {
            var e = s.events().get(i);
            var o = new JsonObject();
            o.addProperty("t", e.time());
            o.addProperty("in", e.entered());
            o.addProperty("k", e.entry().kind().id);
            if (e.entry().kind() != SensorKind.PLAYER) o.addProperty("n", e.entry().label());
            else if (show && e.entry().named()) o.addProperty("n", e.entry().name());
            o.addProperty("id", e.entry().type());
            events.add(o);
        }
        json.add("events", events);
        return json;
    }

    /** One line for in-game screens and displays: "Steve and 2 Zombies inside · 5 today". */
    static String summary(State s, boolean names) {
        String today = s.today() + " today";
        if (s.problem() != null) return "Zone in another team's claim – not watching";
        String inside = s.inside().isEmpty() ? "Nobody inside" : SensorText.sentence(s.inside(), names, SensorText.who(s.inside(), names)) + " inside";
        return (s.settings().armed() ? "" : "Disarmed · ") + inside + " · " + today;
    }

    static List<String> modeLabels() {
        var list = new ArrayList<String>();
        for (var m : SensorFilter.Mode.values()) list.add(m.label);
        return list;
    }

    static List<String> alertLabels() {
        var list = new ArrayList<String>();
        for (var a : SensorSettings.Alert.values()) list.add(a.label);
        return list;
    }

    static List<String> placeLabels() {
        var list = new ArrayList<String>();
        for (var p : SensorSettings.Placement.values()) list.add(p.label);
        return list;
    }

    /** A Lua-style option list ({@code {1 = "a", 2 = "b"}}) for {@link WidgetFactory}. */
    private static Map<Integer, String> options(List<String> labels) {
        var map = new LinkedHashMap<Integer, String>();
        for (int i = 0; i < labels.size(); i++) map.put(i + 1, labels.get(i));
        return map;
    }

    private static Widget widget(WidgetType type, String id, String label, Map<String, Object> opts) {
        try {
            return WidgetFactory.create(type, id, label, opts);
        } catch (WidgetFactory.WidgetException e) {
            throw new IllegalStateException("invalid sensor widget " + id + ": " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> opts(Object... pairs) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < pairs.length; i += 2) if (pairs[i + 1] != null) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
