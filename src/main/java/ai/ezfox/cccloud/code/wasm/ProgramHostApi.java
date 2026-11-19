package ai.ezfox.cccloud.code.wasm;

import ai.ezfox.cccloud.code.api.HostModule;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The modules of a Cloud Computer program ({@code term}, {@code os}, {@code fs}, …, see
 * {@link ai.ezfox.cccloud.code.engine.ScriptEngine#createProgram}) for a WebAssembly program: {@code module.function}
 * calls the module's function with the JSON arguments, {@code module.field} returns a field. Strings travel as
 * ISO-8859-1, one byte per character, like the computer's own Lua.
 */
final class ProgramHostApi implements WasmHostApi {
    private final Map<String, Function> functions = new HashMap<>();

    ProgramHostApi(List<HostModule> modules) {
        for (var module : modules) {
            for (var e : module.functions().entrySet()) {
                String full = module.name() + "." + e.getKey();
                var fn = e.getValue();
                functions.put(full, args -> fn.call(new HostModule.Arguments(full, args.toArray())));
            }
            for (var e : module.fields().entrySet()) {
                Object value = e.getValue();
                functions.putIfAbsent(module.name() + "." + e.getKey(), args -> value);
            }
        }
    }

    @Override
    public @Nullable Function find(String name) {
        return functions.get(name);
    }

    @Override
    public Charset charset() {
        return StandardCharsets.ISO_8859_1;
    }

    @Override
    public void trace(String text) {
        // a computer program prints to its terminal; AssemblyScript's trace has nowhere to go
    }
}
