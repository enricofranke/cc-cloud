package ai.ezfox.cccloud.code.engine;

import ai.ezfox.cccloud.code.api.ScriptFunction;
import org.jspecify.annotations.Nullable;

/**
 * One loaded script in one engine: its own globals and heap, nothing shared with other scripts. Not thread-safe: the
 * runtime calls it from one worker thread at a time; only {@link #interrupt()} comes from another thread.
 */
public interface EngineInstance extends AutoCloseable {
    /** Compiles the source and returns the task that runs its body. */
    Task load(String source) throws ScriptError;

    /** A task that calls a handler with these arguments (plain Java values). */
    Task call(ScriptFunction function, String label, @Nullable Object... args);

    /** Starts or continues a task until it ends, waits, pauses or fails. */
    RunResult run(Task task);

    /**
     * Continues a waiting task with the result of its future (or the failure, raised as an error at the call). Only for
     * {@link ScriptEngine.Preemption#SUSPEND} engines: an engine that cannot suspend blocks inside the host call instead,
     * and its {@link #run} only ends with {@link RunResult.Done} or {@link RunResult.Failed}.
     */
    default RunResult resume(Task task, @Nullable Object value, @Nullable Throwable failure) {
        throw new UnsupportedOperationException("this engine cannot suspend a task");
    }

    /** Any thread: makes the engine consult its {@link Budget} soon. */
    void interrupt();

    /**
     * Bytes the script's own values take: an estimate for Lua (strings and table slots reachable from its globals, its
     * tasks and the handlers the runtime keeps; stops counting once over {@code limit}), the size of the linear memory
     * for an engine with a hard cap.
     */
    long heapEstimate(long limit, java.util.Collection<ScriptFunction> handlers);

    /** Forgets everything; the instance cannot be used afterwards. */
    @Override
    void close();
}
