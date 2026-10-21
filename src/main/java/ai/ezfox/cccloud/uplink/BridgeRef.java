package ai.ezfox.cccloud.uplink;

import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/**
 * Where a Lua-defined bridge widget (item_search, watchlist, jobs) gets its data from: the position of the bridge block
 * that was found under the given peripheral name when the widget was created. Server-internal, never sent to browsers.
 */
public record BridgeRef(String dimension, int x, int y, int z, String peripheral) {
    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("dim", dimension);
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        json.addProperty("name", peripheral);
        return json;
    }

    public static @Nullable BridgeRef fromJson(@Nullable JsonObject json) {
        if (json == null) return null;
        try {
            return new BridgeRef(json.get("dim").getAsString(), json.get("x").getAsInt(), json.get("y").getAsInt(),
                json.get("z").getAsInt(), json.has("name") ? json.get("name").getAsString() : "");
        } catch (RuntimeException e) {
            return null;
        }
    }
}
