package ai.ezfox.cccloud.code.engine;

import java.util.concurrent.CompletableFuture;

/** How a {@link EngineInstance#run} slice ended. */
public sealed interface RunResult {
    /** The task returned (or its function ended): it is gone. */
    record Done() implements RunResult {
    }

    /** The task waits for this future (an action, a sleep, an event); continue it with the result. */
    record Waiting(CompletableFuture<?> future) implements RunResult {
    }

    /** The CPU share is used up: continue the same task later, nothing to pass in. */
    record Paused() implements RunResult {
    }

    /** The task gave way without waiting for anything (a bare {@code coroutine.yield()}): continue it soon. */
    record Yielded() implements RunResult {
    }

    /** An error the script did not catch; {@code aborted}: stopped by a limit (time, memory). */
    record Failed(ScriptError error, boolean aborted) implements RunResult {
    }
}
