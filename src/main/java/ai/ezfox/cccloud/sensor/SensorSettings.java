package ai.ezfox.cccloud.sensor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The settings of one Advanced Cloud Sensor (docs/SPEC.md "Advanced Cloud Sensor"): the zone (size in blocks along
 * X, Y and Z, in front of the sensor or around it), the filter, armed (events, alerts and rules; the redstone output
 * works either way), the redstone output and the alert level of its in-app notifications and push messages. Immutable;
 * saved with the block. Pure Java, unit tested.
 */
public record SensorSettings(int sizeX, int sizeY, int sizeZ, Placement placement, SensorFilter filter, boolean armed, boolean redstone, Alert alert) {
    public static final SensorSettings DEFAULT = new SensorSettings(3, 3, 3, Placement.FRONT, SensorFilter.DEFAULT, true, true, Alert.WARN);
    /** Hard limits of the server config's maximum zone. */
    public static final int HARD_MAX_XZ = 32;
    public static final int HARD_MAX_Y = 16;

    public enum Placement {
        /** Starts at the sensor and reaches out from the face it looks out of, centred on the other two axes. */
        FRONT("front", "In front"),
        /** Centred on the sensor. */
        AROUND("around", "Around");

        public final String id;
        public final String label;

        Placement(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public static @Nullable Placement byId(@Nullable String id) {
            for (var p : values()) if (p.id.equals(id)) return p;
            return null;
        }

        public static @Nullable Placement byLabel(@Nullable String label) {
            for (var p : values()) if (p.label.equals(label)) return p;
            return null;
        }
    }

    /** In-app notification and push level of the sensor's alerts; {@code off} = none (events and rules still work). */
    public enum Alert {
        OFF("off", "Off"), INFO("info", "Info"), WARN("warn", "Warning"), CRIT("crit", "Critical");

        public final String id;
        public final String label;

        Alert(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public static @Nullable Alert byId(@Nullable String id) {
            for (var a : values()) if (a.id.equals(id)) return a;
            return null;
        }

        public static @Nullable Alert byLabel(@Nullable String label) {
            for (var a : values()) if (a.label.equals(label)) return a;
            return null;
        }
    }

    /** The zone size clamped to 1 … the server's maximum. */
    public SensorSettings clamped(int maxX, int maxY, int maxZ) {
        return new SensorSettings(clamp(sizeX, maxX), clamp(sizeY, maxY), clamp(sizeZ, maxZ), placement, filter, armed, redstone, alert);
    }

    private static int clamp(int v, int max) {
        return Math.max(1, Math.min(Math.max(1, max), v));
    }

    /** The zone as the dashboard and the screen show it ("3×3×3 in front"). */
    public String zoneText() {
        return sizeX + "×" + sizeY + "×" + sizeZ + (placement == Placement.FRONT ? " in front" : " around");
    }

    public SensorSettings withZone(int x, int y, int z, Placement p) {
        return new SensorSettings(x, y, z, p, filter, armed, redstone, alert);
    }

    public SensorSettings withFilter(SensorFilter value) {
        return new SensorSettings(sizeX, sizeY, sizeZ, placement, value, armed, redstone, alert);
    }

    public SensorSettings withArmed(boolean value) {
        return new SensorSettings(sizeX, sizeY, sizeZ, placement, filter, value, redstone, alert);
    }

    public SensorSettings withRedstone(boolean value) {
        return new SensorSettings(sizeX, sizeY, sizeZ, placement, filter, armed, value, alert);
    }

    public SensorSettings withAlert(Alert value) {
        return new SensorSettings(sizeX, sizeY, sizeZ, placement, filter, armed, redstone, value);
    }

    /** Whether it has to look at its zone at all (disarmed without redstone output: no). */
    public boolean scans() {
        return armed || redstone;
    }

    // ---- persistence ---------------------------------------------------------------------------------------------------

    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("x", sizeX);
        json.addProperty("y", sizeY);
        json.addProperty("z", sizeZ);
        json.addProperty("place", placement.id);
        json.addProperty("mode", filter.mode().id);
        json.addProperty("team", filter.ignoreTeam());
        json.add("allow", names(filter.allow()));
        json.add("deny", names(filter.deny()));
        json.addProperty("armed", armed);
        json.addProperty("redstone", redstone);
        json.addProperty("alert", alert.id);
        return json;
    }

    private static JsonArray names(List<String> list) {
        var array = new JsonArray();
        for (var n : list) array.add(n);
        return array;
    }

    /** Reads saved settings; anything missing or invalid falls back to the default. */
    public static SensorSettings fromJson(@Nullable JsonObject json) {
        if (json == null) return DEFAULT;
        var d = DEFAULT;
        var mode = SensorFilter.Mode.byId(str(json, "mode"));
        var place = Placement.byId(str(json, "place"));
        var alert = Alert.byId(str(json, "alert"));
        var filter = new SensorFilter(mode == null ? d.filter().mode() : mode, bool(json, "team", d.filter().ignoreTeam()), names(json, "allow"),
            names(json, "deny"));
        return new SensorSettings(size(json, "x", HARD_MAX_XZ, d.sizeX()), size(json, "y", HARD_MAX_Y, d.sizeY()), size(json, "z", HARD_MAX_XZ, d.sizeZ()),
            place == null ? d.placement() : place, filter, bool(json, "armed", d.armed()), bool(json, "redstone", d.redstone()), alert == null ? d.alert() : alert);
    }

    private static int size(JsonObject o, String key, int max, int def) {
        return o.get(key) instanceof JsonPrimitive p && p.isNumber() ? Math.max(1, Math.min(max, p.getAsInt())) : def;
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        return o.get(key) instanceof JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() : def;
    }

    private static @Nullable String str(JsonObject o, String key) {
        return o.get(key) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    private static List<String> names(JsonObject o, String key) {
        var list = new ArrayList<String>();
        if (!(o.get(key) instanceof JsonArray array)) return list;
        for (var e : array) {
            if (list.size() >= SensorFilter.MAX_NAMES) break;
            if (e instanceof JsonPrimitive p && p.isString() && SensorFilter.NAME.matcher(p.getAsString()).matches()) list.add(p.getAsString());
        }
        return list;
    }
}
