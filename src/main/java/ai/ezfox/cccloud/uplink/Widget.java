package ai.ezfox.cccloud.uplink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import org.jspecify.annotations.Nullable;

/**
 * One dashboard widget of an uplink. Values and options are kept as JSON since they go straight to the browser.
 * <p>
 * Charts additionally keep a capped ring buffer of sampled values ({@code history}); bridge widgets created from Lua
 * remember which bridge they are bound to ({@link BridgeRef}, server-internal).
 */
public final class Widget {
    private final String id;
    private final WidgetType type;
    private final @Nullable String label;
    private final JsonObject opts;
    private JsonElement value;
    private final @Nullable ArrayDeque<Double> history;
    private @Nullable BridgeRef bridge;
    /** Game tick of the last chart sample (transient). */
    private long lastSample = Long.MIN_VALUE;

    public Widget(String id, WidgetType type, @Nullable String label, JsonObject opts, @Nullable JsonElement value) {
        this.id = id;
        this.type = type;
        this.label = label;
        this.opts = opts;
        this.value = value == null ? JsonNull.INSTANCE : value;
        this.history = type == WidgetType.CHART ? new ArrayDeque<>() : null;
    }

    public String id() {
        return id;
    }

    public WidgetType type() {
        return type;
    }

    public @Nullable String label() {
        return label;
    }

    public JsonObject opts() {
        return opts;
    }

    public JsonElement value() {
        return value;
    }

    public void setValue(JsonElement value) {
        this.value = value;
    }

    public @Nullable BridgeRef bridge() {
        return bridge;
    }

    public Widget withBridge(@Nullable BridgeRef bridge) {
        this.bridge = bridge;
        return this;
    }

    // ---- charts ------------------------------------------------------------------------------------------------------

    /** Chart options read once (charts are sampled every tick; their options do not change after creation). */
    private int points = -1;
    private long intervalTicks = -1;

    public int chartPoints() {
        if (points < 0) {
            var p = opts.get("points");
            points = p == null ? Limits.CHART_POINTS_DEFAULT : p.getAsInt();
        }
        return points;
    }

    /** Sample interval in game ticks. */
    public long chartIntervalTicks() {
        if (intervalTicks < 0) {
            var i = opts.get("interval");
            double seconds = i == null ? 1 : i.getAsDouble();
            intervalTicks = Math.max(1, Math.round(seconds * 20));
        }
        return intervalTicks;
    }

    /**
     * Appends the current value to the chart history if the sample interval elapsed.
     *
     * @return true if a point was appended.
     */
    public boolean sample(long gameTick) {
        if (history == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
        if (lastSample != Long.MIN_VALUE && gameTick - lastSample < chartIntervalTicks()) return false;
        lastSample = gameTick;
        push(value.getAsDouble());
        return true;
    }

    public void push(double point) {
        if (history == null) return;
        history.addLast(point);
        while (history.size() > chartPoints()) history.removeFirst();
    }

    public int historySize() {
        return history == null ? 0 : history.size();
    }

    // ---- JSON ------------------------------------------------------------------------------------------------------

    /** The widget as sent to browsers. */
    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("type", type.id());
        if (label != null) json.addProperty("label", label);
        json.add("opts", opts.deepCopy());
        if (!value.isJsonNull()) json.add("value", value);
        if (history != null) {
            var array = new JsonArray(history.size());
            for (double point : history) array.add(Json.number(point));
            json.add("history", array);
        }
        return json;
    }

    /** The widget as saved in the block entity (includes the server-internal bridge binding). */
    public JsonObject toSaveJson() {
        var json = toJson();
        if (bridge != null) json.add("bridge", bridge.toJson());
        return json;
    }

    public static @Nullable Widget fromJson(JsonObject json) {
        try {
            var type = WidgetType.byId(json.get("type").getAsString());
            if (type == null) return null;
            String id = json.get("id").getAsString();
            if (!Json.isValidId(id)) return null;
            String label = json.has("label") ? json.get("label").getAsString() : null;
            JsonObject opts = json.has("opts") && json.get("opts").isJsonObject() ? json.getAsJsonObject("opts") : new JsonObject();
            var widget = new Widget(id, type, label, opts, json.get("value"));
            if (json.has("bridge") && json.get("bridge").isJsonObject()) widget.bridge = BridgeRef.fromJson(json.getAsJsonObject("bridge"));
            if (widget.history != null && json.has("history") && json.get("history").isJsonArray()) {
                for (var point : json.getAsJsonArray("history")) {
                    if (point.isJsonPrimitive() && point.getAsJsonPrimitive().isNumber() && Double.isFinite(point.getAsDouble())) {
                        widget.push(point.getAsDouble());
                    }
                }
            }
            return widget;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
