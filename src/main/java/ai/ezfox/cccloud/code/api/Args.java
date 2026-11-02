package ai.ezfox.cccloud.code.api;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The arguments of one {@code cloud.*} call, already converted by the engine's binding to plain Java values
 * ({@code null}, {@link Boolean}, {@link Long} or {@link Double}, {@link String}, {@code Map<String, Object>},
 * {@code List<Object>}, {@link ScriptFunction}) and checked against the descriptor's parameter types, so a handler only
 * casts. Optional parameters the script left out are {@code null}.
 */
public final class Args {
    private final String function;
    private final @Nullable Object[] values;

    public Args(String function, @Nullable Object[] values) {
        this.function = function;
        this.values = values;
    }

    public String function() {
        return function;
    }

    public int count() {
        return values.length;
    }

    public @Nullable Object get(int i) {
        return i < values.length ? values[i] : null;
    }

    public boolean has(int i) {
        return get(i) != null;
    }

    public String string(int i) {
        if (get(i) instanceof String s) return s;
        throw bad(i, "string expected");
    }

    public @Nullable String optString(int i) {
        return get(i) instanceof String s ? s : null;
    }

    public double number(int i) {
        if (get(i) instanceof Number n) return n.doubleValue();
        throw bad(i, "number expected");
    }

    public long integer(int i) {
        if (get(i) instanceof Number n) return n.longValue();
        throw bad(i, "integer expected");
    }

    public boolean bool(int i) {
        if (get(i) instanceof Boolean b) return b;
        throw bad(i, "boolean expected");
    }

    @SuppressWarnings("unchecked")
    public @Nullable Map<String, Object> optTable(int i) {
        var v = get(i);
        if (v == null) return null;
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        if (v instanceof List<?> l && l.isEmpty()) return Map.of();
        throw bad(i, "table with names expected");
    }

    public ScriptFunction function(int i) {
        if (get(i) instanceof ScriptFunction f) return f;
        throw bad(i, "function expected");
    }

    /** {@code bad argument #2 to 'cloud.set' (…)}, like Lua's own argument errors. */
    public ApiError bad(int i, String message) {
        return new ApiError("bad argument #" + (i + 1) + " to 'cloud." + function + "' (" + message + ")");
    }

    /** A boolean field of an options table ({@code {confirm=true}}); false when absent. */
    public static boolean flag(@Nullable Map<String, Object> options, String key) {
        return options != null && Boolean.TRUE.equals(options.get(key));
    }
}
