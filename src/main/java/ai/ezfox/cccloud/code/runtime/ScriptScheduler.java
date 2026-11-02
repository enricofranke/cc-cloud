package ai.ezfox.cccloud.code.runtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.jspecify.annotations.Nullable;

/**
 * The threads of Cloud Scripts, shared by all scripts of the server and never the server thread: a few workers that
 * run script slices, one timer thread (sleeps, {@code every}, {@code at}, wait timeouts, CPU windows) and a watchdog
 * that interrupts every running script about once a millisecond so its engine checks its budget.
 *
 * <p>This is the path for engines that can suspend ({@link ai.ezfox.cccloud.code.engine.ScriptEngine.Preemption#SUSPEND}).
 * An engine that cannot ({@code ABORT}, WebAssembly) needs a second path, not built yet: one thread per running script
 * (with its own stack size, compiled guest code recurses on the JVM stack), a fair run permit shared with the workers'
 * budget, host calls that block on their futures and hand the permit back while they wait, and the watchdog arming a
 * deadline per stretch of running that interrupts the thread, which ends the run (the instance is not reused).
 */
public final class ScriptScheduler implements AutoCloseable {
    /** How often the watchdog makes running scripts check their budget. */
    static final long WATCHDOG_NANOS = 1_000_000;

    private final ExecutorService workers;
    private final ScheduledThreadPoolExecutor timers;
    private final Set<ScriptRuntime> running = ConcurrentHashMap.newKeySet();
    private final Thread watchdog;
    private volatile boolean closed;

    public ScriptScheduler(int threads) {
        workers = Executors.newFixedThreadPool(Math.max(1, threads), factory("cccloud-script-", Thread.NORM_PRIORITY - 1));
        timers = new ScheduledThreadPoolExecutor(1, factory("cccloud-script-timer-", Thread.NORM_PRIORITY));
        timers.setRemoveOnCancelPolicy(true);
        timers.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        watchdog = factory("cccloud-script-watchdog-", Thread.MAX_PRIORITY - 1).newThread(this::watch);
        watchdog.start();
    }

    private static ThreadFactory factory(String prefix, int priority) {
        var count = new AtomicInteger();
        return r -> {
            var t = new Thread(r, prefix + count.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(priority);
            return t;
        };
    }

    public boolean submit(Runnable slice) {
        if (closed) return false;
        try {
            workers.execute(slice);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    @Nullable ScheduledFuture<?> schedule(Runnable task, long delayNanos) {
        if (closed) return null;
        try {
            return timers.schedule(task, Math.max(0, delayNanos), TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException e) {
            return null;
        }
    }

    @Nullable ScheduledFuture<?> repeat(Runnable task, long periodNanos) {
        if (closed) return null;
        try {
            return timers.scheduleAtFixedRate(task, periodNanos, periodNanos, TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException e) {
            return null;
        }
    }

    ScheduledExecutorService timers() {
        return timers;
    }

    void enter(ScriptRuntime runtime) {
        running.add(runtime);
        LockSupport.unpark(watchdog);
    }

    void exit(ScriptRuntime runtime) {
        running.remove(runtime);
    }

    private void watch() {
        while (!closed) {
            if (running.isEmpty()) {
                LockSupport.parkNanos(this, 50_000_000);
                continue;
            }
            for (var runtime : running) runtime.poke();
            LockSupport.parkNanos(this, WATCHDOG_NANOS);
        }
    }

    public boolean closed() {
        return closed;
    }

    /** Stops the threads; running scripts must have been stopped before (their slices end at the next check). */
    @Override
    public void close() {
        closed = true;
        LockSupport.unpark(watchdog);
        timers.shutdownNow();
        workers.shutdownNow();
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
