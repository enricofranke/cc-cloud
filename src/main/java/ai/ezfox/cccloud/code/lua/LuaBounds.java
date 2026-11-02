package ai.ezfox.cccloud.code.lua;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.OperationHelper;
import org.squiddev.cobalt.UnwindThrowable;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.function.Dispatch;
import org.squiddev.cobalt.function.ResumableVarArgFunction;

/**
 * {@code string.gsub} and {@code string.format} with a bound on what one call builds. Both run their loop in Java
 * without consulting the budget, so {@code s:gsub(".", s)} on a 1 MB string would ask for terabytes in one call. They
 * refuse results over {@link LuaSandbox#MAX_STRING} (or over the source, for a {@code gsub} that does not grow it), like
 * {@code string.rep} and {@code table.concat}. Both stay resumable: a replacement function, an {@code __index} or an
 * {@code __tostring} may wait or be paused as everywhere else (the original runs as a nested call).
 */
final class LuaBounds {
    private LuaBounds() {
    }

    static void install(LuaTable string) throws LuaError {
        string.rawset("gsub", new Gsub(string.rawget("gsub")));
        string.rawset("format", new Format(string.rawget("format")));
    }

    private static LuaError tooLarge(int arg, String function) {
        return new LuaError("bad argument #" + arg + " to '" + function + "' (result would be over 1 MB)");
    }

    /** {@code string.gsub(s, pattern, repl [, n])}. */
    private static final class Gsub extends ResumableVarArgFunction<Object> {
        private final LuaValue original;

        Gsub(LuaValue original) {
            this.original = original;
        }

        @Override
        protected Varargs invoke(LuaState state, DebugFrame di, Varargs args) throws LuaError, UnwindThrowable {
            LuaValue s = args.arg(1), repl = args.arg(3);
            if (!s.isString() || !args.arg(2).isString()) return Dispatch.invoke(state, original, args); // its own errors
            LuaString source = s.checkLuaString();
            long length = source.length();
            long allowed = Math.max(LuaSandbox.MAX_STRING, length);
            if (repl.isString()) {
                LuaString r = repl.checkLuaString();
                long captures = captures(r);
                long most = args.arg(4).isNil() ? length + 1 : Math.max(0, Math.min(args.arg(4).checkLong(), length + 1));
                // unmatched bytes + per match the replacement (a position capture is a number, up to 10 digits) + the
                // captures it repeats, which together are at most the matched bytes once per capture reference
                // (in double: the products can pass a long, and only the comparison with a few MB matters)
                double bound = length + most * (r.length() + 10.0 * captures) + (double) captures * length;
                if (bound > allowed) {
                    // the exact count: the same matches with an empty replacement (makes at most the source's bytes)
                    Varargs counted = Dispatch.invoke(state, original, ValueFactory.varargsOf(source, args.arg(2), Constants.EMPTYSTRING, args.arg(4)));
                    long kept = counted.first().checkLuaString().length();
                    long matches = counted.arg(2).checkLong();
                    bound = kept + matches * (r.length() + 10.0 * captures) + (double) captures * (length - kept);
                    if (bound > allowed) throw tooLarge(3, "gsub");
                }
                return Dispatch.invoke(state, original, args);
            }
            if (repl instanceof LuaTable || repl.type() == Constants.TFUNCTION) {
                var measured = new Measured(repl, allowed);
                return Dispatch.invoke(state, original, ValueFactory.varargsOf(source, args.arg(2), measured, args.arg(4)));
            }
            return Dispatch.invoke(state, original, args);
        }

        @Override
        public Varargs resume(LuaState state, Object unused, Varargs value) {
            return value;
        }

        /** {@code %0}–{@code %9} in a replacement string. */
        private static long captures(LuaString r) {
            long n = 0;
            for (int i = 0; i + 1 < r.length(); i++) {
                if (r.charAt(i) != '%') continue;
                int next = r.charAt(++i);
                if (next >= '0' && next <= '9') n++;
            }
            return n;
        }
    }

    /**
     * The replacement function or table of one {@code gsub} call, counting the bytes it hands back. A table is read with
     * metamethods, as {@code gsub} does; a falsy result keeps the match.
     */
    private static final class Measured extends ResumableVarArgFunction<Object> {
        private final LuaValue replacement;
        private final long allowed;
        private long total;

        Measured(LuaValue replacement, long allowed) {
            this.replacement = replacement;
            this.allowed = allowed;
        }

        @Override
        protected Varargs invoke(LuaState state, DebugFrame di, Varargs args) throws LuaError, UnwindThrowable {
            if (replacement instanceof LuaTable) return count(OperationHelper.getTable(state, replacement, args.first()));
            return count(Dispatch.invoke(state, replacement, args).first());
        }

        @Override
        public Varargs resume(LuaState state, Object unused, Varargs value) throws LuaError {
            return count(value.first());
        }

        private LuaValue count(LuaValue value) throws LuaError {
            if (value.type() == Constants.TSTRING) total += ((LuaString) value).length();
            else if (value.type() == Constants.TNUMBER) total += 32;
            if (total > allowed) throw tooLarge(3, "gsub");
            return value;
        }
    }

    /** {@code string.format(fmt, ...)}. Values formatted through {@code __tostring} are not counted ahead. */
    private static final class Format extends ResumableVarArgFunction<Object> {
        private final LuaValue original;

        Format(LuaValue original) {
            this.original = original;
        }

        @Override
        protected Varargs invoke(LuaState state, DebugFrame di, Varargs args) throws LuaError, UnwindThrowable {
            if (args.arg(1).isString() && bound(args.arg(1).checkLuaString(), args) > LuaSandbox.MAX_STRING) throw tooLarge(1, "format");
            return Dispatch.invoke(state, original, args);
        }

        @Override
        public Varargs resume(LuaState state, Object unused, Varargs value) {
            return value;
        }

        /** The most bytes the format can make: its text, each string argument (four times for {@code %q}), 512 per number. */
        private static long bound(LuaString format, Varargs args) {
            long bound = format.length();
            int arg = 2;
            int n = format.length();
            for (int i = 0; i < n; i++) {
                if (format.charAt(i) != '%') continue;
                if (++i >= n) break;
                if (format.charAt(i) == '%') continue;
                while (i < n && "-+ #0123456789.".indexOf(format.charAt(i)) >= 0) i++;
                if (i >= n) break;
                int conversion = format.charAt(i);
                LuaValue value = args.arg(arg++);
                if (conversion == 's' || conversion == 'q') {
                    if (value.type() == Constants.TSTRING) bound += (conversion == 'q' ? 4L : 1L) * ((LuaString) value).length() + 2;
                    else bound += 512;
                } else {
                    bound += 512;
                }
            }
            return bound;
        }
    }
}
