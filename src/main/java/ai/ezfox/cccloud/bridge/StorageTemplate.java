package ai.ezfox.cccloud.bridge;

import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.uplink.WidgetType;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;

/**
 * The ready-made Storage section an uplink publishes when it is attached directly to a Cloud Bridge (direct mode,
 * zero Lua). Same widget ids as the dashboard's dev mock: storage, energy, items, types, usage, search, watch, jobs.
 */
public final class StorageTemplate {
    public static final String SEARCH = "search";
    public static final String WATCH = "watch";
    public static final String JOBS = "jobs";

    private StorageTemplate() {
    }

    public static String title(String kind) {
        return kind.equals("rs") ? "RS Network" : "ME Network";
    }

    public static List<Widget> widgets(int maxCraft) {
        return List.of(
            gauge("storage", "Storage used", 85, 95, null),
            gauge("energy", "Energy", null, null, "green"),
            label("items", "Total items"),
            label("types", "Item types"),
            label("usage", "Energy usage"),
            new Widget(SEARCH, WidgetType.ITEM_SEARCH, "Search network", opts("max", maxCraft), null),
            new Widget(WATCH, WidgetType.WATCHLIST, "Watchlist", new JsonObject(), new com.google.gson.JsonArray()),
            new Widget(JOBS, WidgetType.JOBS, "Crafting jobs", new JsonObject(), new com.google.gson.JsonArray())
        );
    }

    private static Widget gauge(String id, String label, Integer warn, Integer crit, String color) {
        var opts = new JsonObject();
        opts.addProperty("min", 0);
        opts.addProperty("max", 100);
        opts.addProperty("unit", "%");
        if (warn != null) opts.addProperty("warn", warn);
        if (crit != null) opts.addProperty("crit", crit);
        if (color != null) opts.addProperty("color", color);
        return new Widget(id, WidgetType.GAUGE, label, opts, null);
    }

    private static Widget label(String id, String label) {
        return new Widget(id, WidgetType.LABEL, label, new JsonObject(), new JsonPrimitive("–"));
    }

    private static JsonObject opts(String key, int value) {
        var json = new JsonObject();
        json.add(key, Json.number(value));
        return json;
    }
}
