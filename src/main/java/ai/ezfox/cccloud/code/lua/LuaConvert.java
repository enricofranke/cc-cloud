package ai.ezfox.cccloud.code.lua;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.ScriptFunction;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaInteger;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.LuaFunction;

/**
 * Lua values ⇄ the plain Java values of the API ({@code null}, Boolean, Long/Double, String, Map, List,
 * {@link ScriptFunction}). Strings are UTF-8 both ways (a script's source is UTF-8, so {@code "ä"} stays {@code "ä"}).
 * Tables from scripts are checked: string or number keys only, at most {@link #MAX_DEPTH} levels and
 * {@link #MAX_ITEMS} values in total.
 */
final class LuaConvert {
    static final int MAX_DEPTH = 16;
    static final int MAX_ITEMS = 10_000;

    private LuaConvert() {
    }

    /** A handler of the script, kept by the runtime and called again through {@link LuaInstance#call}. */
    record Function(LuaFunction function) implements ScriptFunction {
    }

    static LuaString str(String s) {
        return str(s, StandardCharsets.UTF_8);
    }

    static LuaString str(String s, Charset charset) {
        return ValueFactory.valueOf(s.getBytes(charset));
    }

    static String text(LuaString s) {
        return text(s, StandardCharsets.UTF_8);
    }

    static String text(LuaString s, Charset charset) {
        byte[] bytes = new byte[s.length()];
        s.copyTo(bytes, 0);
        return new String(bytes, charset);
    }

    // ---- Java → Lua --------------------------------------------------------------------------------------------------

    static LuaValue toLua(@Nullable Object value) {
        return toLua(value, 0, StandardCharsets.UTF_8);
    }

    /** Like {@link #toLua(Object)} with strings encoded in {@code charset} (ISO-8859-1 for computer programs: one byte per char). */
    static LuaValue toLua(@Nullable Object value, Charset charset) {
        return toLua(value, 0, charset);
    }

    private static LuaValue toLua(@Nullable Object value, int depth, Charset charset) {
        return switch (value) {
            case null -> Constants.NIL;
            case Boolean b -> ValueFactory.valueOf(b);
            case Integer i -> ValueFactory.valueOf(i);
            case Long l -> LuaInteger.valueOf(l);
            case Number n -> ValueFactory.valueOf(n.doubleValue());
            case String s -> str(s, charset);
            case Function f -> f.function();
            case LuaValue v -> v;
            case Map<?, ?> map -> {
                if (depth > MAX_DEPTH) yield Constants.NIL;
                var table = new LuaTable(0, map.size());
                for (var e : map.entrySet()) {
                    if (e.getValue() != null) table.rawset(String.valueOf(e.getKey()), toLua(e.getValue(), depth + 1, charset));
                }
                yield table;
            }
            case List<?> list -> {
                if (depth > MAX_DEPTH) yield Constants.NIL;
                var table = new LuaTable(list.size(), 0);
                int i = 1;
                for (var item : list) table.rawset(i++, toLua(item, depth + 1, charset));
                yield table;
            }
            default -> str(value.toString(), charset);
        };
    }

    /** A handler's result: nothing, one value or several ({@code Object[]}). */
    static Varargs results(@Nullable Object result, boolean declared) {
        return results(result, declared, StandardCharsets.UTF_8);
    }

    static Varargs results(@Nullable Object result, boolean declared, Charset charset) {
        if (result instanceof Object[] array) {
            var values = new LuaValue[array.length];
            for (int i = 0; i < array.length; i++) values[i] = toLua(array[i], charset);
            return ValueFactory.varargsOf(values);
        }
        if (result == null) return declared ? Constants.NIL : Constants.NONE;
        return toLua(result, charset);
    }

    // ---- Lua → Java --------------------------------------------------------------------------------------------------

    /** Any script value as plain Java; functions become {@link Function}. */
    static @Nullable Object toJava(LuaValue value) throws LuaError {
        return toJava(value, StandardCharsets.UTF_8);
    }

    static @Nullable Object toJava(LuaValue value, Charset charset) throws LuaError {
        return toJava(value, 0, new int[]{0}, new IdentityHashMap<>(), charset);
    }

    private static @Nullable Object toJava(LuaValue value, int depth, int[] items, IdentityHashMap<LuaTable, Boolean> seen, Charset charset) throws LuaError {
        switch (value.type()) {
            case Constants.TNIL:
                return null;
            case Constants.TBOOLEAN:
                return value.toBoolean();
            case Constants.TNUMBER: {
                if (value instanceof LuaInteger) return (long) value.toInteger();
                double d = value.toDouble();
                return d == Math.rint(d) && Math.abs(d) < 0x1p53 ? (Object) (long) d : (Object) d;
            }
            case Constants.TSTRING:
                return text((LuaString) value, charset);
            case Constants.TFUNCTION:
                return new Function((LuaFunction) value);
            case Constants.TTABLE:
                return table((LuaTable) value, depth, items, seen, charset);
            default:
                throw new ApiError("cannot pass a " + value.typeName());
        }
    }

    private static Object table(LuaTable table, int depth, int[] items, IdentityHashMap<LuaTable, Boolean> seen, Charset charset) throws LuaError {
        if (depth >= MAX_DEPTH) throw new ApiError("table nested more than " + MAX_DEPTH + " levels deep");
        if (seen.put(table, Boolean.TRUE) != null) throw new ApiError("table contains itself");
        int length = table.length();
        int count = 0;
        var keys = new ArrayList<LuaValue>();
        var values = new ArrayList<LuaValue>();
        LuaValue k = Constants.NIL;
        while (true) {
            Varargs next = table.next(k);
            k = next.first();
            if (k.isNil()) break;
            if (++items[0] > MAX_ITEMS) throw new ApiError("table has more than " + MAX_ITEMS + " values");
            keys.add(k);
            values.add(next.arg(2));
            count++;
        }
        Object out;
        if (count > 0 && count == length) {
            var list = new ArrayList<Object>(length);
            for (int i = 1; i <= length; i++) list.add(toJava(table.rawget(i), depth + 1, items, seen, charset));
            out = list;
        } else {
            var map = new LinkedHashMap<String, Object>();
            for (int i = 0; i < keys.size(); i++) {
                LuaValue key = keys.get(i);
                String name = switch (key.type()) {
                    case Constants.TSTRING -> text((LuaString) key, charset);
                    case Constants.TNUMBER -> key.toString();
                    default -> throw new ApiError("table keys must be strings or numbers, not " + key.typeName());
                };
                map.put(name, toJava(values.get(i), depth + 1, items, seen, charset));
            }
            out = map;
        }
        seen.remove(table);
        return out;
    }
}
