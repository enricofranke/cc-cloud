package ai.ezfox.cccloud.code.lua;

import ai.ezfox.cccloud.code.api.ApiSpec;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.lib.BaseLib;
import org.squiddev.cobalt.lib.Bit32Lib;
import org.squiddev.cobalt.lib.CoroutineLib;
import org.squiddev.cobalt.lib.MathLib;
import org.squiddev.cobalt.lib.StringLib;
import org.squiddev.cobalt.lib.TableLib;
import org.squiddev.cobalt.lib.Utf8Lib;
import org.squiddev.cobalt.function.LibFunction;

/**
 * The globals of a Cloud Script: Cobalt's pure libraries (base, string, table, math, utf8, coroutine, bit32) plus a
 * harmless {@code os} ({@link LuaOsLib}), then cut down to exactly the whitelist in the descriptor
 * ({@code lua.globals} and {@code lua.libraries}): no {@code io}, {@code debug}, {@code package}/{@code require},
 * file loading, {@code setfenv}/{@code getfenv}, {@code collectgarbage}, {@code string.dump}, {@code table.create}.
 * {@code load} only takes source text (the state has no bytecode format, so binary chunks are refused).
 *
 * <p>Calls that make big things in one go are bounded: {@code string.rep}, {@code table.concat}, {@code string.gsub}
 * and {@code string.format} refuse results over {@link #MAX_STRING} ({@link LuaBounds}), {@code table.unpack} more
 * than {@link #MAX_UNPACK} values, {@code table.move} more than {@link #MAX_MOVE} elements; {@code string.pack} is not
 * offered ({@code c2000000000} is one call). What grows memory step by step ({@code ..}, tables) is caught by the
 * runtime's memory checks. Not bounded ahead: a string built with {@code ..} is lazy in Cobalt and gets its bytes in one
 * allocation of its full length (at most 2 GB) when they are needed (string functions, table keys, comparisons); the
 * memory check stops the script right after.
 */
final class LuaSandbox {
    static final int MAX_STRING = 1 << 20;
    static final int MAX_UNPACK = 10_000;
    static final int MAX_MOVE = 1_000_000;

    private LuaSandbox() {
    }

    static void install(LuaState state, ApiSpec spec, ZoneId zone, DoubleSupplier clock) throws LuaError {
        BaseLib.add(state);
        TableLib.add(state);
        StringLib.add(state);
        CoroutineLib.add(state);
        MathLib.add(state);
        Utf8Lib.add(state);
        Bit32Lib.add(state);
        var g = state.globals();
        g.rawset("os", LuaOsLib.create(state, zone, clock));

        var string = (LuaTable) g.rawget("string");
        string.rawset("rep", LibFunction.createV(LuaSandbox::rep));
        LuaBounds.install(string);
        var table = (LuaTable) g.rawget("table");
        table.rawset("concat", LibFunction.createV(LuaSandbox::concat));
        var unpack = LibFunction.createV(LuaSandbox::unpack);
        table.rawset("unpack", unpack);
        g.rawset("unpack", unpack);
        table.rawset("move", LibFunction.createV(LuaSandbox::move));

        // The whitelist: whatever the descriptor does not list goes, in the globals and in every library.
        var allowed = new HashSet<>(spec.lua().globals());
        allowed.addAll(spec.lua().libraries().keySet());
        allowed.add("cloud");
        allowed.add("print"); // defined by the prelude (cloud.log)
        strip(g, allowed);
        for (var lib : spec.lua().libraries().entrySet()) {
            if (g.rawget(lib.getKey()) instanceof LuaTable t) strip(t, Set.copyOf(lib.getValue()));
        }
        // the string metatable's __index is the same (now stripped) string table
    }

    private static void strip(LuaTable table, Set<String> allowed) throws LuaError {
        var remove = new ArrayList<LuaValue>();
        LuaValue k = Constants.NIL;
        while (true) {
            Varargs next = table.next(k);
            k = next.first();
            if (k.isNil()) break;
            if (!(k instanceof LuaString s) || !allowed.contains(s.toString())) remove.add(k);
        }
        for (var key : remove) table.rawset(key, Constants.NIL);
    }

    /** {@code string.rep(s, n [, sep])}, refusing results over {@link #MAX_STRING} bytes. */
    private static Varargs rep(LuaState state, Varargs args) throws LuaError {
        LuaString s = args.arg(1).checkLuaString();
        long n = args.arg(2).checkLong();
        LuaString sep = args.arg(3).optLuaString(Constants.EMPTYSTRING);
        if (n <= 0) return Constants.EMPTYSTRING;
        long size = s.length() * n + sep.length() * (n - 1);
        if (size > MAX_STRING) throw new LuaError("bad argument #2 to 'rep' (result would be over 1 MB)");
        if (n == 1) return s;
        byte[] out = new byte[(int) size];
        int pos = 0;
        for (long i = 0; i < n; i++) {
            if (i > 0) pos = sep.copyTo(out, pos);
            pos = s.copyTo(out, pos);
        }
        return ValueFactory.valueOf(out);
    }

    /** {@code table.concat(list [, sep [, i [, j]]])} with raw access (as in Lua 5.2), at most {@link #MAX_STRING} bytes. */
    private static Varargs concat(LuaState state, Varargs args) throws LuaError {
        LuaTable t = args.arg(1).checkTable();
        LuaString sep = args.arg(2).optLuaString(Constants.EMPTYSTRING);
        long i = args.arg(3).optLong(1);
        long j = args.arg(4).isNil() ? t.length() : args.arg(4).checkLong();
        if (i > j) return Constants.EMPTYSTRING;
        var parts = new ArrayList<LuaString>();
        long size = 0;
        for (long k = i; k <= j; k++) {
            LuaValue v = k >= Integer.MIN_VALUE && k <= Integer.MAX_VALUE ? t.rawget((int) k) : Constants.NIL;
            if (!(v.type() == Constants.TSTRING || v.type() == Constants.TNUMBER)) {
                throw new LuaError("invalid value (at index " + k + ") in table for 'concat'");
            }
            LuaString part = v.checkLuaString();
            size += part.length() + (k > i ? sep.length() : 0);
            if (size > MAX_STRING) throw new LuaError("bad argument #1 to 'concat' (result would be over 1 MB)");
            parts.add(part);
        }
        byte[] out = new byte[(int) size];
        int pos = 0;
        for (int k = 0; k < parts.size(); k++) {
            if (k > 0) pos = sep.copyTo(out, pos);
            pos = parts.get(k).copyTo(out, pos);
        }
        return ValueFactory.valueOf(out);
    }

    /** {@code table.unpack(list [, i [, j]])} with raw access, at most {@link #MAX_UNPACK} values. */
    private static Varargs unpack(LuaState state, Varargs args) throws LuaError {
        LuaTable t = args.arg(1).checkTable();
        long i = args.arg(2).optLong(1);
        long j = args.arg(3).isNil() ? t.length() : args.arg(3).checkLong();
        if (i > j) return Constants.NONE;
        if (j - i >= MAX_UNPACK) throw new LuaError("too many results to unpack (at most " + MAX_UNPACK + ")");
        var values = new LuaValue[(int) (j - i + 1)];
        for (int k = 0; k < values.length; k++) {
            long index = i + k;
            values[k] = index >= Integer.MIN_VALUE && index <= Integer.MAX_VALUE ? t.rawget((int) index) : Constants.NIL;
        }
        return ValueFactory.varargsOf(values);
    }

    /** {@code table.move(a1, f, e, t [, a2])} with raw access, at most {@link #MAX_MOVE} elements. */
    private static Varargs move(LuaState state, Varargs args) throws LuaError {
        LuaTable a1 = args.arg(1).checkTable();
        long f = args.arg(2).checkLong(), e = args.arg(3).checkLong(), t = args.arg(4).checkLong();
        LuaTable a2 = args.arg(5).isNil() ? a1 : args.arg(5).checkTable();
        if (e >= f) {
            if (e - f >= MAX_MOVE) throw new LuaError("bad argument #3 to 'move' (too many elements to move)");
            if (Math.max(Math.abs(f), Math.abs(e)) > Integer.MAX_VALUE || Math.abs(t) + (e - f) > Integer.MAX_VALUE) {
                throw new LuaError("bad argument #4 to 'move' (destination wrap around)");
            }
            int n = (int) (e - f + 1);
            if (t > e || t <= f || a1 != a2) {
                for (int k = 0; k < n; k++) a2.rawset((int) (t + k), a1.rawget((int) (f + k)));
            } else {
                for (int k = n - 1; k >= 0; k--) a2.rawset((int) (t + k), a1.rawget((int) (f + k)));
            }
        }
        return a2;
    }

    /** The prelude: {@code print}, {@code cloud.log} and {@code cloud.warn} take any values and call {@code tostring} (in Lua, so it may pause). */
    static final String PRELUDE = """
        local log, warn, tostring, select, concat = cloud.log, cloud.warn, tostring, select, table.concat
        local function join(...)
          local n = select("#", ...)
          local parts = {}
          for i = 1, n do parts[i] = tostring((select(i, ...))) end
          return concat(parts, " ")
        end
        cloud.log = function(...) return log(join(...)) end
        cloud.warn = function(...) return warn(join(...)) end
        print = function(...) return log(join(...)) end
        """;

    static List<String> globals(LuaState state) throws LuaError {
        var out = new ArrayList<String>();
        LuaValue k = Constants.NIL;
        while (true) {
            Varargs next = state.globals().next(k);
            k = next.first();
            if (k.isNil()) break;
            out.add(k.toString());
        }
        return out;
    }
}
