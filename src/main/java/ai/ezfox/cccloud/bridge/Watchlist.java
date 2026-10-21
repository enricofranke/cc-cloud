package ai.ezfox.cccloud.bridge;

import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.Op;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import org.jspecify.annotations.Nullable;

/**
 * Items with a minimum amount, stored in the bridge block entity and edited from the dashboard. {@link #check} reports
 * every item that dropped below its minimum, once per crossing (it has to rise to the minimum again before it can
 * alert again). Pure Java, unit tested.
 */
public final class Watchlist {
    private static final class Entry {
        long min;
        long count;
        boolean below;

        Entry(long min) {
            this.min = min;
        }
    }

    public record Crossing(String name, long count, long min) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** Applies a dashboard operation. Returns null on success or an error code. */
    public @Nullable String apply(Op op, java.util.function.Predicate<String> itemExists) {
        return switch (op) {
            case Op.WatchAdd add -> {
                if (entries.containsKey(add.name())) yield "already_watched";
                if (entries.size() >= Limits.WATCHLIST) yield "too_many";
                if (!itemExists.test(add.name())) yield "unknown_item";
                entries.put(add.name(), new Entry(add.min()));
                yield null;
            }
            case Op.WatchRemove remove -> entries.remove(remove.name()) == null ? "unknown_item" : null;
            case Op.WatchSetMin set -> {
                var entry = entries.get(set.name());
                if (entry == null) yield "unknown_item";
                entry.min = set.min();
                entry.below = entry.count < entry.min && entry.below;
                yield null;
            }
            case Op.Craft craft -> "invalid_value";
        };
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** Updates the counts; returns the items that just dropped below their minimum. */
    public List<Crossing> check(ToLongFunction<String> counter) {
        var crossings = new ArrayList<Crossing>();
        for (var e : entries.entrySet()) {
            var entry = e.getValue();
            entry.count = counter.applyAsLong(e.getKey());
            boolean below = entry.count < entry.min;
            if (below && !entry.below) crossings.add(new Crossing(e.getKey(), entry.count, entry.min));
            entry.below = below;
        }
        return crossings;
    }

    /** Rows for the dashboard: [{name, displayName, count, min}]. */
    public JsonArray rows(java.util.function.Function<String, String> displayName) {
        var array = new JsonArray(entries.size());
        for (var e : entries.entrySet()) {
            var row = new JsonObject();
            row.addProperty("name", e.getKey());
            row.addProperty("displayName", displayName.apply(e.getKey()));
            row.add("count", Json.number(e.getValue().count));
            row.add("min", Json.number(e.getValue().min));
            array.add(row);
        }
        return array;
    }

    public String save() {
        var array = new JsonArray();
        for (var e : entries.entrySet()) {
            var row = new JsonObject();
            row.addProperty("name", e.getKey());
            row.addProperty("min", e.getValue().min);
            row.addProperty("below", e.getValue().below);
            array.add(row);
        }
        return Json.GSON.toJson(array);
    }

    public void load(String json) {
        entries.clear();
        try {
            for (var element : JsonParser.parseString(json).getAsJsonArray()) {
                var row = element.getAsJsonObject();
                String name = row.get("name").getAsString();
                long min = row.get("min").getAsLong();
                if (!Op.isItemName(name) || min < 0 || entries.size() >= Limits.WATCHLIST) continue;
                var entry = new Entry(min);
                entry.below = row.has("below") && row.get("below").getAsBoolean();
                entries.put(name, entry);
            }
        } catch (RuntimeException ignored) {
            // Corrupt data: start with an empty watchlist.
        }
    }
}
