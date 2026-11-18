package ai.ezfox.cccloud.code.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * A table of host functions a program sees as a global ({@code term}, {@code fs}, {@code os}, …), engine-neutral: the
 * Lua binding makes it a table, a WebAssembly engine binds it as imports. Used by Cloud Computer programs
 * ({@link ai.ezfox.cccloud.code.engine.ScriptEngine#createProgram}); Cloud Scripts use the {@code cloud} descriptor.
 *
 * <p>Arguments arrive as plain Java values ({@code null}, Boolean, Long or Double, String, {@code Map<String,Object>},
 * {@code List<Object>}); a string is the program's bytes as ISO-8859-1 characters (one char per byte, so binary data
 * and the terminal's 8-bit characters survive). A function returns nothing ({@code null}), one value, several
 * ({@code Object[]}) or a {@link CompletableFuture} the calling task waits for. A wrong call throws {@link ApiError}.
 * Functions run on the program's worker thread and must not block.
 */
public record HostModule(String name, Map<String, Function> functions, Map<String, Object> fields) {
    @FunctionalInterface
    public interface Function {
        @Nullable Object call(Arguments args);
    }

    public HostModule {
        functions = Map.copyOf(functions);
        fields = Map.copyOf(fields);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private final Map<String, Function> functions = new LinkedHashMap<>();
        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder fn(String fn, Function function) {
            if (functions.put(fn, function) != null) throw new IllegalArgumentException("duplicate " + name + "." + fn);
            return this;
        }

        public Builder field(String key, Object value) {
            fields.put(key, value);
            return this;
        }

        public HostModule build() {
            return new HostModule(name, functions, fields);
        }
    }

    /** The arguments of one call with checks worded like Lua's own ({@code bad argument #1 to 'fs.open' (…)}). */
    public static final class Arguments {
        private final String function;
        private final @Nullable Object[] values;

        public Arguments(String function, @Nullable Object[] values) {
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
            var v = get(i);
            if (v instanceof String s) return s;
            if (v instanceof Long || v instanceof Double) return number(v);
            throw bad(i, "string expected, got " + type(v));
        }

        public @Nullable String optString(int i) {
            return has(i) ? string(i) : null;
        }

        public double number(int i) {
            if (get(i) instanceof Number n) return n.doubleValue();
            throw bad(i, "number expected, got " + type(get(i)));
        }

        public double optNumber(int i, double fallback) {
            return has(i) ? number(i) : fallback;
        }

        /** An integer argument within {@code [min, max]}. */
        public long integer(int i, long min, long max) {
            var v = get(i);
            if (!(v instanceof Number n)) throw bad(i, "number expected, got " + type(v));
            double d = n.doubleValue();
            if (d != Math.rint(d)) throw bad(i, "number has no integer representation");
            if (d < min || d > max) throw bad(i, "out of range");
            return (long) d;
        }

        public long optInteger(int i, long min, long max, long fallback) {
            return has(i) ? integer(i, min, max) : fallback;
        }

        public boolean bool(int i) {
            if (get(i) instanceof Boolean b) return b;
            throw bad(i, "boolean expected, got " + type(get(i)));
        }

        public boolean optBool(int i, boolean fallback) {
            return has(i) ? bool(i) : fallback;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> table(int i) {
            var v = get(i);
            if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
            if (v instanceof java.util.List<?> l) {
                var out = new LinkedHashMap<String, Object>();
                for (int k = 0; k < l.size(); k++) out.put(Integer.toString(k + 1), l.get(k));
                return out;
            }
            throw bad(i, "table expected, got " + type(v));
        }

        public ApiError bad(int i, String message) {
            return new ApiError("bad argument #" + (i + 1) + " to '" + function + "' (" + message + ")");
        }

        private static String number(Object v) {
            if (v instanceof Long l) return Long.toString(l);
            double d = (Double) v;
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
        }

        public static String type(@Nullable Object v) {
            return switch (v) {
                case null -> "nil";
                case Boolean b -> "boolean";
                case Number n -> "number";
                case String s -> "string";
                case Map<?, ?> m -> "table";
                case java.util.List<?> l -> "table";
                case ScriptFunction f -> "function";
                default -> "userdata";
            };
        }
    }
}
