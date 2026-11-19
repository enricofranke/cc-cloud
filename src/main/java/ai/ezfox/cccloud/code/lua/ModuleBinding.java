package ai.ezfox.cccloud.code.lua;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.HostModule;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.UnwindThrowable;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.function.ResumableVarArgFunction;

/**
 * The {@link HostModule}s of a computer program as global tables ({@code term.write}). An existing global table of
 * the same name (the sandbox's {@code os}) gets the module's functions added. Strings cross as bytes (ISO-8859-1).
 */
final class ModuleBinding {
    static final Charset BYTES = StandardCharsets.ISO_8859_1;

    private ModuleBinding() {
    }

    static void install(LuaState state, List<HostModule> modules, LuaInstance instance) throws LuaError {
        var g = state.globals();
        for (var module : modules) {
            LuaTable table = g.rawget(module.name()) instanceof LuaTable t ? t : new LuaTable();
            // hidden modules ("__native") are called by the rom's own wrappers, which raise their errors at the program's line
            boolean hidden = module.name().startsWith("__");
            for (var e : module.functions().entrySet()) {
                table.rawset(e.getKey(), new Bound(module.name() + "." + e.getKey(), e.getValue(), instance, hidden));
            }
            for (var e : module.fields().entrySet()) table.rawset(e.getKey(), LuaConvert.toLua(e.getValue(), BYTES));
            g.rawset(module.name(), table);
        }
    }

    static final class Bound extends ResumableVarArgFunction<Void> {
        private final String name;
        private final HostModule.Function function;
        private final LuaInstance instance;
        /** Errors without a place: the caller (a rom wrapper) adds the program's. */
        private final boolean bare;

        Bound(String name, HostModule.Function function, LuaInstance instance, boolean bare) {
            this.name = name;
            this.function = function;
            this.instance = instance;
            this.bare = bare;
        }

        @Override
        protected Varargs invoke(LuaState state, DebugFrame di, Varargs args) throws LuaError, UnwindThrowable {
            var values = new @Nullable Object[args.count()];
            for (int i = 0; i < values.length; i++) {
                try {
                    values[i] = LuaConvert.toJava(args.arg(i + 1), BYTES);
                } catch (ApiError e) {
                    String message = "bad argument #" + (i + 1) + " to '" + name + "' (" + e.getMessage() + ")";
                    throw bare ? new LuaError(message, 0) : new LuaError(message);
                }
            }
            Object result;
            try {
                result = function.call(new HostModule.Arguments(name, values));
            } catch (ApiError e) {
                throw bare ? new LuaError(e.getMessage(), 0) : new LuaError(e.getMessage());
            }
            if (result instanceof CompletableFuture<?> future) return instance.await(state, name, true, false, future, BYTES);
            // nothing comes back as nil, so tostring(f()) and select('#', f()) behave like a function that returns nil
            return LuaConvert.results(result, true, BYTES);
        }

        @Override
        public Varargs resume(LuaState state, Void object, Varargs value) throws LuaError {
            return instance.resumed(value);
        }

        @Override
        public String debugName() {
            return name;
        }
    }
}
