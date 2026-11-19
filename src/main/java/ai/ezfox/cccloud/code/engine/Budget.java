package ai.ezfox.cccloud.code.engine;

/**
 * Decides, from inside the engine's interrupt hook, whether a script may go on. The runtime makes the engine check
 * often (a watchdog thread interrupts running scripts every millisecond or two); the check itself is a few
 * nanoseconds: elapsed time and allocated bytes of the current slice.
 *
 * <p>An engine that cannot suspend ({@link ScriptEngine.Preemption#ABORT}) runs on a thread of its own and calls
 * {@link #check()} itself every few thousand instructions. It never gets {@link Decision#PAUSE}: when the per-second
 * CPU share is used up, {@code check()} blocks the script's thread until the next second and then answers
 * {@link Decision#CONTINUE}. It waits for a host call's future with {@link #await}, which lets the runtime run the
 * script's handlers meanwhile; a stop is {@link Decision#ABORT} at the next check or an interrupt of the waiting thread.
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

    /**
     * For engines that cannot suspend: waits on the calling thread for a host call's future and returns its value, or
     * throws its failure ({@link java.util.concurrent.ExecutionException}). The time waiting is not CPU time. The runtime
     * may run other tasks of the same script meanwhile (handlers and timers calling back into the engine on this thread);
     * the engine must allow that re-entry. {@link InterruptedException}: the script was stopped.
     */
    default <T> T await(java.util.concurrent.CompletableFuture<T> future) throws InterruptedException, java.util.concurrent.ExecutionException {
        long start = System.nanoTime();
        try {
            return future.get();
        } finally {
            blocked(System.nanoTime() - start);
        }
    }
}
