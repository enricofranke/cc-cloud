package ai.ezfox.cccloud.code.lua;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.ApiSpec;
import ai.ezfox.cccloud.code.api.Args;
import ai.ezfox.cccloud.code.api.CloudApi;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaInteger;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.OperationHelper;
import org.squiddev.cobalt.UnwindThrowable;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.function.ResumableVarArgFunction;

/**
 * The {@code cloud} table of a Lua script, generated from the descriptor: one function per descriptor entry (dotted
 * names become sub-tables: {@code cloud.store.get}), the descriptor's fields ({@code cloud.script}), and argument
 * checks from the parameter types with Lua's own wording ({@code bad argument #2 to 'cloud.set' (string expected, got
 * nil)}). An optional parameter can be left out in the middle when the next argument fits the next parameter
 * ({@code cloud.on("change", handler)}). Calls that wait ({@code await}/{@code yield} in the descriptor) pause the
 * calling task until their future completes.
 */
final class LuaBinding {
    private LuaBinding() {
    }

    static void install(LuaState state, ApiSpec spec, CloudApi api, LuaInstance instance) throws LuaError {
        var cloud = new LuaTable();
        for (var function : spec.functions()) {
            String[] path = function.path();
            LuaTable target = cloud;
            for (int i = 0; i < path.length - 1; i++) {
                LuaTable sub = target.rawget(path[i]) instanceof LuaTable t ? t : null;
                if (sub == null) {
                    sub = new LuaTable();
                    target.rawset(path[i], sub);
                }
                target = sub;
            }
            target.rawset(path[path.length - 1], new Bound(spec, function, api.handler(function), instance));
        }
        cloud.rawset("script", LuaConvert.toLua(instance.host().info()));
        state.globals().rawset("cloud", cloud);
    }

    /** One {@code cloud.*} function. */
    static final class Bound extends ResumableVarArgFunction<Void> {
        private final ApiSpec spec;
        private final ApiSpec.Function function;
        private final CloudApi.Handler handler;
        private final LuaInstance instance;

        Bound(ApiSpec spec, ApiSpec.Function function, CloudApi.Handler handler, LuaInstance instance) {
            this.spec = spec;
            this.function = function;
            this.handler = handler;
            this.instance = instance;
        }

        @Override
        protected Varargs invoke(LuaState state, DebugFrame di, Varargs args) throws LuaError, UnwindThrowable {
            @Nullable Object[] values = arguments(args);
            Object result;
            try {
                result = handler.call(instance.host(), new Args(function.name(), values));
            } catch (ApiError e) {
                throw new LuaError(e.getMessage());
            }
            if (result instanceof CompletableFuture<?> future) return instance.await(state, function, future);
            return LuaConvert.results(result, !function.returns().isEmpty());
        }

        @Override
        public Varargs resume(LuaState state, Void object, Varargs value) throws LuaError {
            return instance.resumed(value);
        }

        @Override
        public String debugName() {
            return "cloud." + function.name();
        }

        /** Lua arguments → Java, checked against the descriptor's parameters. */
        private @Nullable Object[] arguments(Varargs args) throws LuaError {
            var params = function.params();
            var out = new Object[params.size()];
            int arg = 1;
            for (int p = 0; p < params.size(); p++) {
                var param = params.get(p);
                if (param.variadic()) {
                    int n = Math.max(0, args.count() - arg + 1);
                    out = java.util.Arrays.copyOf(out, p + n);
                    for (int i = 0; i < n; i++) out[p + i] = LuaConvert.text(OperationHelper.toStringDirect(args.arg(arg + i)));
                    return out;
                }
                LuaValue v = args.arg(arg);
                String type = resolve(param.type());
                if (v.isNil()) {
                    if (!param.optional()) throw bad(arg, type + " expected, got " + (arg > args.count() ? "no value" : "nil"));
                    out[p] = null;
                    arg++;
                    continue;
                }
                if (!matches(type, v)) {
                    // an optional parameter left out in the middle: cloud.on("change", fn), cloud.wait("change", 5)
                    if (param.optional() && p + 1 < params.size() && matches(resolve(params.get(p + 1).type()), v)) {
                        out[p] = null;
                        continue;
                    }
                    throw bad(arg, type + " expected, got " + v.typeName());
                }
                if (type.equals("integer")) {
                    double d = v.toDouble();
                    if (d != Math.rint(d) || Math.abs(d) > 0x1p53) throw bad(arg, "number has no integer representation");
                    out[p] = (long) d;
                } else if (type.equals("string")) {
                    out[p] = v.type() == Constants.TSTRING ? LuaConvert.text(v.checkLuaString()) : v.toString();
                } else {
                    try {
                        out[p] = LuaConvert.toJava(v);
                    } catch (ApiError e) {
                        throw bad(arg, e.getMessage());
                    }
                }
                arg++;
            }
            return out;
        }

        private String resolve(String type) {
            var t = spec.types().get(type);
            return t != null && t.of() != null && t.kind().equals("alias") ? t.of() : type;
        }

        private static boolean matches(String type, LuaValue v) {
            return switch (type) {
                case "string" -> v.type() == Constants.TSTRING || v.type() == Constants.TNUMBER;
                case "number" -> v.type() == Constants.TNUMBER;
                case "integer" -> v.type() == Constants.TNUMBER || v instanceof LuaInteger;
                case "boolean" -> v.type() == Constants.TBOOLEAN;
                case "table" -> v.type() == Constants.TTABLE;
                case "function" -> v.type() == Constants.TFUNCTION;
                default -> true; // any
            };
        }

        private LuaError bad(int arg, String message) {
            return new LuaError("bad argument #" + arg + " to 'cloud." + function.name() + "' (" + message + ")");
        }
    }
}
