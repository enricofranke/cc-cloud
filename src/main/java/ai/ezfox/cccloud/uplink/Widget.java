package ai.ezfox.cccloud.uplink;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/**
 * One dashboard widget of an uplink. Values and options are kept as JSON since they go straight to the browser.
 */
public final class Widget {
    private final String id;
    private final WidgetType type;
    private final @Nullable String label;
    private final JsonObject opts;
    private JsonElement value;

    public Widget(String id, WidgetType type, @Nullable String label, JsonObject opts, @Nullable JsonElement value) {
        this.id = id;
        this.type = type;
        this.label = label;
        this.opts = opts;
        this.value = value == null ? JsonNull.INSTANCE : value;
    }

    public String id() {
        return id;
    }

    public WidgetType type() {
        return type;
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

    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("type", type.id());
        if (label != null) json.addProperty("label", label);
        json.add("opts", opts.deepCopy());
        if (!value.isJsonNull()) json.add("value", value);
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
            return new Widget(id, type, label, opts, json.get("value"));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
