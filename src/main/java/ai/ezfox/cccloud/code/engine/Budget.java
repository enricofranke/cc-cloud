package ai.ezfox.cccloud.code.engine;

/**
 * Decides, from inside the engine's interrupt hook, whether a script may go on. The runtime makes the engine check
 * often (a watchdog thread interrupts running scripts every millisecond or two); the check itself is a few
 * nanoseconds: elapsed time and allocated bytes of the current slice.
 *
 * <p>An engine that cannot suspend ({@link ScriptEngine.Preemption#ABORT}) never gets {@link Decision#PAUSE}: its
 * per-second CPU share would be kept by the blocking path of the scheduler (holding back the thread's run permit
 * between host calls), and a decision to stop is a thread interrupt.
 */
public interface Budget {
    enum Decision {
        /** Go on. */
        CONTINUE,
        /** Pause here and continue later (the per-second CPU share is used up); the script does not notice. */
        PAUSE,
        /** Stop with {@link #abortMessage()}: an error the script cannot catch. */
        ABORT
    }

    Decision check();

    String abortMessage();

    /** What kind of stop {@link #abortMessage()} is ({@code CPU_LIMIT}, {@code MEMORY_LIMIT}, {@code KILLED}). */
    default ScriptError.Kind abortKind() {
        return ScriptError.Kind.CPU_LIMIT;
    }

    /**
     * The memory cap of this instance in bytes. An engine with its own heap (WebAssembly linear memory) sets it as a
     * hard limit when the instance is created; Lua has no cap of its own, so the runtime estimates the reachable heap
     * against it and limits the allocation rate.
     */
    default long memoryBytes() {
        return Long.MAX_VALUE;
    }

    /** The engine waited this long inside a call (an action from inside a coroutine): not the script's CPU time. */
    void blocked(long nanos);
}
