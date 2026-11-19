package ai.ezfox.cccloud.code.wasm;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.ApiSpec;
import ai.ezfox.cccloud.code.api.Args;
import ai.ezfox.cccloud.code.api.CloudApi;
import ai.ezfox.cccloud.code.api.ScriptFunction;
import ai.ezfox.cccloud.code.api.ScriptHost;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The {@code cloud} API for a WebAssembly script, generated from the same descriptor as the Lua binding: every
 * descriptor function under {@code cloud.<name>} (with the same Java handlers, rights and actions), every descriptor
 * field as a function without arguments ({@code cloud.script}), and the arguments checked against the descriptor's
 * parameter types with the same wording ({@code bad argument #2 to 'cloud.set' (string expected, got nil)}).
 */
final class CloudHostApi implements WasmHostApi {
    static final String PREFIX = "cloud.";

    private final ScriptHost host;
    private final ApiSpec spec;
    private final Map<String, Function> functions = new HashMap<>();

    CloudHostApi(ScriptHost host, ApiSpec spec, CloudApi api) {
        this.host = host;
        this.spec = spec;
        for (var f : spec.functions()) {
            var handler = api.handler(f);
            functions.put(PREFIX + f.name(), new Function() {
                @Override
                public @Nullable Object call(List<@Nullable Object> args) {
                    return handler.call(host, new Args(f.name(), arguments(f, args)));
                }

                @Override
                public boolean returnsSomething() {
                    return !f.returns().isEmpty();
                }
            });
        }
        for (var field : spec.fields()) {
            if (field.name().equals("script")) functions.put(PREFIX + field.name(), args -> host.info());
        }
    }

    @Override
    public @Nullable Function find(String name) {
        return functions.get(name);
    }

    @Override
    public void trace(String text) {
        host.log("info", text);
    }

    /** JSON arguments → the descriptor's parameters (positional; null = left out). */
    private @Nullable Object[] arguments(ApiSpec.Function f, List<@Nullable Object> args) {
        var params = f.params();
        var out = new Object[params.size()];
        for (int p = 0; p < params.size(); p++) {
            var param = params.get(p);
            if (param.variadic()) {
                int n = Math.max(0, args.size() - p);
                out = java.util.Arrays.copyOf(out, p + n);
                for (int i = 0; i < n; i++) out[p + i] = text(args.get(p + i));
                return out;
            }
            Object v = p < args.size() ? args.get(p) : null;
            String type = resolve(param.type());
            if (v == null) {
                if (!param.optional()) throw bad(f, p, type + " expected, got " + (p >= args.size() ? "no value" : "nil"));
                continue;
            }
            out[p] = switch (type) {
                case "string" -> {
                    if (v instanceof String s) yield s;
                    if (v instanceof Number) yield text(v);
                    throw bad(f, p, "string expected, got " + typeName(v));
                }
                case "number" -> {
                    if (v instanceof Number n) yield n.doubleValue();
                    throw bad(f, p, "number expected, got " + typeName(v));
                }
                case "integer" -> {
                    if (!(v instanceof Number n)) throw bad(f, p, "number expected, got " + typeName(v));
                    double d = n.doubleValue();
                    if (d != Math.rint(d) || Math.abs(d) > 0x1p53) throw bad(f, p, "number has no integer representation");
                    yield n instanceof Long l ? l : (long) d;
                }
                case "boolean" -> {
                    if (v instanceof Boolean b) yield b;
                    throw bad(f, p, "boolean expected, got " + typeName(v));
                }
                case "table" -> {
                    if (v instanceof Map<?, ?> || v instanceof List<?>) yield v;
                    throw bad(f, p, "table expected, got " + typeName(v));
                }
                case "function" -> {
                    if (v instanceof ScriptFunction) yield v;
                    throw bad(f, p, "function expected, got " + typeName(v));
                }
                default -> v; // any
            };
        }
        return out;
    }

    private String resolve(String type) {
        var t = spec.types().get(type);
        if (t == null) return type;
        if (t.kind().equals("alias") && t.of() != null) return t.of();
        return "table";
    }

    private static ApiError bad(ApiSpec.Function f, int index, String message) {
        return new ApiError("bad argument #" + (index + 1) + " to 'cloud." + f.name() + "' (" + message + ")");
    }

    static String typeName(@Nullable Object v) {
        return switch (v) {
            case null -> "nil";
            case Boolean b -> "boolean";
            case Number n -> "number";
            case String s -> "string";
            case Map<?, ?> m -> "table";
            case List<?> l -> "table";
            case ScriptFunction s -> "function";
            default -> "userdata";
        };
    }

    /** A value as {@code cloud.log} prints it. */
    static String text(@Nullable Object v) {
        return switch (v) {
            case null -> "nil";
            case String s -> s;
            case Long l -> Long.toString(l);
            case Double d -> d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) (double) d) : Double.toString(d);
            case Boolean b -> b.toString();
            case ScriptFunction f -> "function";
            default -> WasmJson.write(v);
        };
    }
}
