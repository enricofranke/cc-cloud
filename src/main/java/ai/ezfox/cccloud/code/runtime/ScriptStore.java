package ai.ezfox.cccloud.code.runtime;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.ScriptFunction;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * {@code cloud.store}: a few kilobytes of values per script that survive restarts, new versions and server restarts
 * (saved with the script). Values are JSON: nil, booleans, numbers, strings and tables of those. Thread-safe;
 * {@link #dirty()} tells the owner to save.
 */
public final class ScriptStore {
    private final TreeMap<String, String> values = new TreeMap<>();
    private int limit;
    private int size;
    private boolean dirty;

    public ScriptStore(int limit) {
        this.limit = limit;
    }

    public synchronized void limit(int limit) {
        this.limit = limit;
    }

    public synchronized @Nullable Object get(String key) {
        String json = values.get(key);
        if (json == null) return null;
        try {
            return fromJson(JsonParser.parseString(json));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** False if the value does not fit; null deletes. */
    public synchronized boolean set(String key, @Nullable Object value) {
        if (value == null) {
            String old = values.remove(key);
            if (old != null) {
                size -= bytes(key, old);
                dirty = true;
            }
            return true;
        }
        String json = toJson(value, 0).toString();
        String old = values.get(key);
        int next = size - (old == null ? 0 : bytes(key, old)) + bytes(key, json);
        if (next > limit) return false;
        values.put(key, json);
        size = next;
        dirty = true;
        return true;
    }

    public synchronized List<String> keys() {
        return new ArrayList<>(values.keySet());
    }

    public synchronized int size() {
        return size;
    }

    /** The stored JSON per key (for saving). */
    public synchronized Map<String, String> snapshot() {
        return Map.copyOf(values);
    }

    public synchronized void load(Map<String, String> stored) {
        values.clear();
        size = 0;
        for (var e : stored.entrySet()) {
            values.put(e.getKey(), e.getValue());
            size += bytes(e.getKey(), e.getValue());
        }
    }

    /** True once after a change. */
    public synchronized boolean dirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    private static int bytes(String key, String json) {
        return key.getBytes(StandardCharsets.UTF_8).length + json.getBytes(StandardCharsets.UTF_8).length;
    }

    private static @Nullable Object fromJson(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e instanceof JsonPrimitive p) {
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) {
                double d = p.getAsDouble();
                return d == Math.rint(d) && Math.abs(d) < 0x1p53 ? (Object) (long) d : (Object) d;
            }
            return p.getAsString();
        }
        if (e instanceof JsonArray a) {
            var list = new ArrayList<Object>(a.size());
            for (var item : a) list.add(fromJson(item));
            return list;
        }
        var map = new java.util.LinkedHashMap<String, Object>();
        for (var entry : e.getAsJsonObject().entrySet()) map.put(entry.getKey(), fromJson(entry.getValue()));
        return map;
    }

    private static JsonElement toJson(@Nullable Object value, int depth) {
        if (depth > 16) throw new ApiError("value nested too deep to store");
        return switch (value) {
            case null -> JsonNull.INSTANCE;
            case Boolean b -> new JsonPrimitive(b);
            case Number n -> {
                if (!Double.isFinite(n.doubleValue())) throw new ApiError("cannot store nan or inf");
                yield new JsonPrimitive(n);
            }
            case String s -> new JsonPrimitive(s);
            case List<?> list -> {
                var array = new JsonArray();
                for (var item : list) array.add(toJson(item, depth + 1));
                yield array;
            }
            case Map<?, ?> map -> {
                var object = new JsonObject();
                for (var e : map.entrySet()) object.add(String.valueOf(e.getKey()), toJson(e.getValue(), depth + 1));
                yield object;
            }
            case ScriptFunction f -> throw new ApiError("cannot store a function");
            default -> throw new ApiError("cannot store a " + value.getClass().getSimpleName());
        };
    }
}
