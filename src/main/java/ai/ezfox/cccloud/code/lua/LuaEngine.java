package ai.ezfox.cccloud.code.lua;

import ai.ezfox.cccloud.code.api.ApiSpec;
import ai.ezfox.cccloud.code.api.CloudApi;
import ai.ezfox.cccloud.code.api.ScriptHost;
import ai.ezfox.cccloud.code.engine.Budget;
import ai.ezfox.cccloud.code.engine.Diagnostic;
import ai.ezfox.cccloud.code.engine.EngineInstance;
import ai.ezfox.cccloud.code.engine.ScriptEngine;
import ai.ezfox.cccloud.code.engine.ScriptError;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;

/** Lua 5.2 (with the bits of 5.3 Cobalt has) on Cobalt, the VM CC: Tweaked uses; independent of CC: Tweaked itself. */
public final class LuaEngine implements ScriptEngine {
    private final Supplier<ZoneId> zone;

    public LuaEngine(Supplier<ZoneId> zone) {
        this.zone = zone;
    }

    @Override
    public String language() {
        return "lua";
    }

    @Override
    public List<Diagnostic> check(String name, String source) {
        var state = LuaState.builder().build();
        try {
            LoadState.load(state, new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), "=" + name, state.globals());
            return List.of();
        } catch (CompileException | LuaError e) {
            String message = e.getMessage() == null ? "syntax error" : e.getMessage();
            Integer line = ScriptError.lineOf(message, name);
            String text = line == null ? message : message.substring(name.length() + 2 + Integer.toString(line).length()).strip();
            return List.of(Diagnostic.error(line == null ? 1 : line, text));
        }
    }

    @Override
    public EngineInstance create(String name, ScriptHost host, Budget budget) {
        return new LuaInstance(name, host, budget, ApiSpec.get(), CloudApi.standard(), zone.get());
    }
}
