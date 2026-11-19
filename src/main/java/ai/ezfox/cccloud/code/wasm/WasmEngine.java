package ai.ezfox.cccloud.code.wasm;

import ai.ezfox.cccloud.code.api.ApiSpec;
import ai.ezfox.cccloud.code.api.CloudApi;
import ai.ezfox.cccloud.code.api.ScriptHost;
import ai.ezfox.cccloud.code.engine.Budget;
import ai.ezfox.cccloud.code.engine.Diagnostic;
import ai.ezfox.cccloud.code.engine.EngineInstance;
import ai.ezfox.cccloud.code.engine.ScriptEngine;
import ai.ezfox.cccloud.code.runtime.Limits;
import java.util.List;
import java.util.function.Supplier;

/**
 * Cloud Scripts compiled to WebAssembly (first language: AssemblyScript, SDK in {@code wasm/assemblyscript}), run by
 * Endive, a WebAssembly interpreter in plain Java (docs/BUILD.md "Endive"). Every script gets a fresh instance with a
 * linear memory capped at the script's memory budget, only the host imports of {@link WasmInstance}, the {@code cloud}
 * API from the same descriptor and handlers as Lua, and the budget checked from inside the interpreter. It cannot
 * suspend a running module ({@link Preemption#ABORT}): the runtime gives each script its own thread.
 */
public final class WasmEngine implements ScriptEngine {
    public static final String LANGUAGE = "wasm";

    private final Supplier<Limits> limits;

    public WasmEngine(Supplier<Limits> limits) {
        this.limits = limits;
    }

    /** The host imports a module may have (for checks outside the engine, e.g. a .wasm upload). */
    public static java.util.Map<String, java.util.Map<String, run.endive.wasm.types.FunctionType>> imports() {
        return WasmInstance.IMPORTS;
    }

    @Override
    public String language() {
        return LANGUAGE;
    }

    @Override
    public Preemption preemption() {
        return Preemption.ABORT;
    }

    @Override
    public List<Diagnostic> check(String name, String source) {
        var l = limits.get();
        WasmSource stored;
        try {
            stored = WasmSource.decode(source, l.maxWasmBytes(), l.maxWasmSource());
        } catch (WasmSource.Invalid e) {
            return List.of(Diagnostic.error(1, e.getMessage()));
        }
        try {
            WasmCheck.check(stored.module(), WasmInstance.IMPORTS, l.memoryBytes());
            return List.of();
        } catch (WasmCheck.Refused e) {
            return List.of(Diagnostic.error(1, e.getMessage()));
        }
    }

    /**
     * A Cloud Computer program in WebAssembly: the boot code is the stored module ({@link WasmSource}), the program's
     * modules are reached through {@code cccloud.call} as {@code term.write}, {@code os.startTimer}, …
     */
    @Override
    public EngineInstance createProgram(String name, List<ai.ezfox.cccloud.code.api.HostModule> modules, Budget budget, java.util.function.DoubleSupplier clock) {
        var l = limits.get();
        return new WasmInstance(name, new ProgramHostApi(modules), budget, l.maxWasmBytes(), l.hostCallBytes(), l.hostCallsPerSecond());
    }

    @Override
    public EngineInstance create(String name, ScriptHost host, Budget budget) {
        var l = limits.get();
        return new WasmInstance(name, new CloudHostApi(host, ApiSpec.get(), CloudApi.standard()), budget, l.maxWasmBytes(), l.hostCallBytes(),
            l.hostCallsPerSecond());
    }
}
