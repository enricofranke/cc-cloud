package ai.ezfox.cccloud.ingame;

import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The dashboard state of an in-game client, built from the same hub messages a browser receives ({@code snapshot},
 * {@code uplink}, {@code uplink_removed}, {@code value}, {@code state}, {@code role}, {@code alert}, {@code layout},
 * {@code action_result}, {@code query_result}). Mirrors cloud/web's store. Pure Java (Gson), unit tested.
 */
public final class DashboardModel {
    private static final int MAX_ALERTS = 20;
    private static final int MAX_RESULTS = 32;

    private JsonObject mainframe = new JsonObject();
    private boolean canControl;
    private final LinkedHashMap<String, JsonObject> uplinks = new LinkedHashMap<>();
    private JsonElement layout = JsonNull.INSTANCE;
    private final ArrayDeque<JsonObject> alerts = new ArrayDeque<>();
    /** Latest action/query results by reqId (the screen shows errors and search rows). */
    private final LinkedHashMap<String, JsonObject> results = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, JsonObject> eldest) {
            return size() > MAX_RESULTS;
        }
    };
    private boolean hasSnapshot;

    /** Applies a JSON array of messages; returns true if anything changed. */
    public boolean apply(String messages) {
        JsonArray array;
        try {
            array = JsonParser.parseString(messages).getAsJsonArray();
        } catch (RuntimeException e) {
            return false;
        }
        boolean changed = false;
        for (var element : array) {
            if (element.isJsonObject()) changed |= apply(element.getAsJsonObject());
        }
        return changed;
    }

    public boolean apply(JsonObject m) {
        String t = m.has("t") ? m.get("t").getAsString() : "";
        switch (t) {
            case "snapshot" -> {
                mainframe = m.has("mainframe") && m.get("mainframe").isJsonObject() ? m.getAsJsonObject("mainframe") : new JsonObject();
                canControl = m.has("canControl") && m.get("canControl").getAsBoolean();
                uplinks.clear();
                if (m.has("uplinks")) for (var u : m.getAsJsonArray("uplinks")) put(u.getAsJsonObject());
                layout = m.has("layout") ? m.get("layout") : JsonNull.INSTANCE;
                hasSnapshot = true;
            }
            case "uplink" -> put(m);
            case "uplink_removed" -> uplinks.remove(str(m, "uplinkId"));
            case "value" -> {
                if (!setValue(str(m, "uplinkId"), str(m, "id"), m.get("value"))) return false;
            }
            case "values" -> {
                // {s: {uplinkId: {widgetId: value}}}: all value deltas of one hub flush
                if (!(m.get("s") instanceof JsonObject sections)) return false;
                boolean any = false;
                for (var section : sections.entrySet()) {
                    if (!(section.getValue() instanceof JsonObject values)) continue;
                    for (var value : values.entrySet()) any |= setValue(section.getKey(), value.getKey(), value.getValue());
                }
                return any;
            }
            case "state" -> {
                for (var key : new String[]{"online", "enabled", "name", "power", "reason"}) {
                    if (m.has(key)) mainframe.add(key, m.get(key));
                }
            }
            case "alert" -> {
                alerts.addFirst(m);
                while (alerts.size() > MAX_ALERTS) alerts.removeLast();
            }
            case "role" -> {
                canControl = m.has("canControl") && m.get("canControl").getAsBoolean();
                if (m.has("role")) mainframe.add("role", m.get("role"));
            }
            case "layout" -> layout = m.has("layout") ? m.get("layout") : JsonNull.INSTANCE;
            case "action_result", "query_result" -> {
                if (m.has("reqId")) results.put(str(m, "reqId"), m);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean setValue(String uplinkId, String widgetId, JsonElement value) {
        var uplink = uplinks.get(uplinkId);
        if (uplink == null || !uplink.has("widgets") || value == null) return false;
        for (var w : uplink.getAsJsonArray("widgets")) {
            var widget = w.getAsJsonObject();
            if (!str(widget, "id").equals(widgetId)) continue;
            widget.add("value", value);
            appendHistory(widget, value);
            return true;
        }
        return false;
    }

    private void put(JsonObject uplink) {
        var copy = uplink.deepCopy();
        copy.remove("t");
        uplinks.put(str(copy, "uplinkId"), copy);
    }

    /** Charts: a number appends one point (trimmed to opts.points), an array replaces the history (as the web does). */
    static void appendHistory(JsonObject widget, JsonElement value) {
        if (!"chart".equals(str(widget, "type"))) return;
        var history = widget.has("history") && widget.get("history").isJsonArray() ? widget.getAsJsonArray("history") : new JsonArray();
        if (value.isJsonArray()) {
            widget.add("history", value.deepCopy());
            return;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return;
        history.add(value);
        int points = 60;
        if (widget.has("opts") && widget.getAsJsonObject("opts").has("points")) points = widget.getAsJsonObject("opts").get("points").getAsInt();
        while (history.size() > points) history.remove(0);
        widget.add("history", history);
    }

    static String str(JsonObject o, String key) {
        var e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    public boolean hasSnapshot() {
        return hasSnapshot;
    }

    public boolean canControl() {
        return canControl;
    }

    public JsonObject mainframe() {
        return mainframe;
    }

    public Map<String, JsonObject> uplinks() {
        return uplinks;
    }

    public JsonElement layout() {
        return layout;
    }

    /** The latest result of a request, or null. */
    public JsonObject result(String reqId) {
        return results.get(reqId);
    }

    /** The whole model as one JSON object (the tablet screen's view). */
    public String toJson() {
        var json = new JsonObject();
        json.add("mainframe", mainframe);
        json.addProperty("canControl", canControl);
        json.addProperty("ready", hasSnapshot);
        var array = new JsonArray();
        for (var uplink : uplinks.values()) array.add(uplink);
        json.add("uplinks", array);
        json.add("layout", layout);
        var alertArray = new JsonArray();
        for (var alert : alerts) alertArray.add(alert);
        json.add("alerts", alertArray);
        var resultArray = new JsonObject();
        for (var e : results.entrySet()) resultArray.add(e.getKey(), e.getValue());
        json.add("results", resultArray);
        return Json.GSON.toJson(json);
    }
}
