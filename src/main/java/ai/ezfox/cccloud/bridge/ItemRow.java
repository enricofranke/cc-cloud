package ai.ezfox.cccloud.bridge;

import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** One row of a bridge search: {name, displayName, count, craftable}. */
public record ItemRow(String name, String displayName, long count, boolean craftable) {
    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("displayName", displayName);
        json.add("count", Json.number(count));
        json.addProperty("craftable", craftable);
        return json;
    }

    public Map<String, Object> toLua() {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", name);
        map.put("displayName", displayName);
        map.put("count", count);
        map.put("craftable", craftable);
        return map;
    }
}
