package ai.ezfox.cccloud.code.engine;

import ai.ezfox.cccloud.code.api.ScriptHost;
import java.util.List;

/**
 * A script language. Lua (on Cobalt) is the first; the boundary is made for more (WASM languages later): an engine
 * only binds the descriptor's functions to {@link ai.ezfox.cccloud.code.api.CloudApi}, runs tasks in slices and
 * reports to a {@link Budget}. Everything else (timers, events, rights, storage, logs) is the runtime's.
 */
public interface ScriptEngine {
    /**
     * How a running script can be held. {@link #SUSPEND} engines (Lua on Cobalt) pause anywhere and continue later, so
     * scripts share a few worker threads in slices and a wait is a suspended task ({@link EngineInstance#resume}).
     * {@link #ABORT} engines (WebAssembly: QuickJS on Endive, see docs/WASM_SPIKE.md on branch wasm-spike) cannot
     * unwind a running stack: each script runs on a thread of its own, blocks inside host calls (sleep, events,
     * actions) and can only be stopped (thread interrupt), never paused. {@link ai.ezfox.cccloud.code.runtime.ScriptRuntime}
     * runs SUSPEND engines; the blocking path for ABORT engines is not built yet, and such an engine is refused with an
     * {@link ScriptError.Kind#ENGINE} error.
     */
    enum Preemption { SUSPEND, ABORT }

    /** The language id stored with a script ({@code lua}). */
    String language();

    default Preemption preemption() {
        return Preemption.SUSPEND;
    }

    /**
     * The smallest memory cap ({@link Budget#memoryBytes()}) an instance can start with, e.g. the initial linear memory of
     * a WebAssembly language runtime (QuickJS about 16 MiB with headroom). 0 = no minimum.
     */
    default long minimumMemory() {
        return 0;
    }

    /** Compile problems of a source, without running anything. */
    List<Diagnostic> check(String name, String source);

    /** A fresh instance for one script run; {@code budget.memoryBytes()} is its memory cap. */
    EngineInstance create(String name, ScriptHost host, Budget budget);

    /**
     * A fresh instance for a Cloud Computer program: the language's sandbox plus these modules as globals (no
     * {@code cloud} table). {@code clock} is the seconds since the computer booted. The program's {@link EngineInstance#load}
     * source is its boot code; it waits for events through a module function that returns a future.
     */
    default EngineInstance createProgram(String name, java.util.List<ai.ezfox.cccloud.code.api.HostModule> modules, Budget budget,
                                         java.util.function.DoubleSupplier clock) {
        throw new UnsupportedOperationException(language() + " cannot run computer programs");
    }
}
