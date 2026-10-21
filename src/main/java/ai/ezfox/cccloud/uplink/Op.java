package ai.ezfox.cccloud.uplink;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Structured action values of the bridge widgets: {@code {"op":"craft","name","count"}} on an item_search widget and
 * {@code {"op":"add"|"remove"|"set_min","name","min"}} on a watchlist widget. Parsing is strict: unknown keys, wrong
 * types, fractions or out-of-range numbers are rejected.
 */
public sealed interface Op {
    /** Registry id of an item: {@code namespace:path}. */
    Pattern ITEM_NAME = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");
    long MAX_INT = Integer.MAX_VALUE;

    String name();

    record Craft(String name, long count) implements Op {
    }

    record WatchAdd(String name, long min) implements Op {
    }

    record WatchRemove(String name) implements Op {
    }

    record WatchSetMin(String name, long min) implements Op {
    }

    static boolean isItemName(@Nullable String name) {
        return name != null && ITEM_NAME.matcher(name).matches();
    }

    /** Returns null if the object is not a valid operation. */
    static @Nullable Op parse(JsonObject obj) {
        String op = string(obj, "op");
        String name = string(obj, "name");
        if (op == null || !isItemName(name)) return null;
        return switch (op) {
            case "craft" -> {
                if (!onlyKeys(obj, Set.of("op", "name", "count"))) yield null;
                Long count = integer(obj.get("count"), 1, MAX_INT);
                yield count == null ? null : new Craft(name, count);
            }
            case "add" -> {
                if (!onlyKeys(obj, Set.of("op", "name", "min"))) yield null;
                Long min = obj.has("min") ? integer(obj.get("min"), 0, MAX_INT) : Long.valueOf(0);
                yield min == null ? null : new WatchAdd(name, min);
            }
            case "remove" -> onlyKeys(obj, Set.of("op", "name")) ? new WatchRemove(name) : null;
            case "set_min" -> {
                if (!onlyKeys(obj, Set.of("op", "name", "min"))) yield null;
                Long min = integer(obj.get("min"), 0, MAX_INT);
                yield min == null ? null : new WatchSetMin(name, min);
            }
            default -> null;
        };
    }

    private static boolean onlyKeys(JsonObject obj, Set<String> allowed) {
        for (var key : obj.keySet()) if (!allowed.contains(key)) return false;
        return true;
    }

    private static @Nullable String string(JsonObject obj, String key) {
        var e = obj.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    private static @Nullable Long integer(@Nullable JsonElement e, long min, long max) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        double v = e.getAsDouble();
        if (!Double.isFinite(v) || v != Math.rint(v) || v < min || v > max) return null;
        return (long) v;
    }
}
