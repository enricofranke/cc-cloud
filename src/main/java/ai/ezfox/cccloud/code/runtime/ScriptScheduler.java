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
 * <p>The workers run engines that can suspend ({@link ai.ezfox.cccloud.code.engine.ScriptEngine.Preemption#SUSPEND}).
 * An engine that cannot ({@code ABORT}, WebAssembly) gets a thread of its own per running script
 * ({@link #startThread}, at most {@link #MAX_THREADS} on the server, each with a fixed stack of {@link #THREAD_STACK}
 * bytes so recursion in the guest ends in a stack overflow error, not a deep JVM stack). Such a script checks its budget
 * itself (from the engine's instruction hook), waits for its share of CPU by blocking its own thread, and blocks in host
 * calls; stopping it is the budget's abort or an interrupt of its thread.
 */
public final class ScriptScheduler implements AutoCloseable {
    /** Something running on the workers that the watchdog pokes while it runs (a Cloud Script, a Cloud Computer). */
    public interface Watched {
        /** Watchdog thread: make the engine check its budget soon. */
        void poke();
    }

    /** How often the watchdog makes running scripts check their budget. */
    static final long WATCHDOG_NANOS = 1_000_000;
    /** Threads of scripts that cannot suspend (WebAssembly), on the whole server. */
    public static final int MAX_THREADS = 32;
    /** Stack size of such a thread. */
    public static final long THREAD_STACK = 1L << 20;

    private final ExecutorService workers;
    private final ScheduledThreadPoolExecutor timers;
    private final Set<Watched> running = ConcurrentHashMap.newKeySet();
    private final Thread watchdog;
    private final CpuClock clock;
    private volatile boolean closed;
    private final Set<Thread> threads = ConcurrentHashMap.newKeySet();
    private final AtomicInteger threadCount = new AtomicInteger();
    private final AtomicInteger threadNames = new AtomicInteger();

    public ScriptScheduler(int threads) {
        this(threads, CpuClock.system());
    }

    /** {@code clock}: what the scripts' CPU budget is measured with (tests pass their own). */
    public ScriptScheduler(int threads, CpuClock clock) {
        this.clock = clock;
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

    public @Nullable ScheduledFuture<?> schedule(Runnable task, long delayNanos) {
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

    /** The clock of the scripts' CPU budget. */
    public CpuClock clock() {
        return clock;
    }

    ScheduledExecutorService timers() {
        return timers;
    }

    /** Why {@link #startThread} did not start a thread. */
    public enum Refusal { CLOSED, FULL }

    /** Starts the thread of a script that cannot suspend; null if it started. */
    @Nullable Refusal startThread(Runnable body) {
        if (closed) return Refusal.CLOSED;
        if (threadCount.incrementAndGet() > MAX_THREADS) {
            threadCount.decrementAndGet();
            return Refusal.FULL;
        }
        var holder = new Thread[1];
        var t = new Thread(null, () -> {
            try {
                body.run();
            } finally {
                threads.remove(holder[0]);
                threadCount.decrementAndGet();
            }
        }, "cccloud-script-wasm-" + threadNames.incrementAndGet(), THREAD_STACK);
        holder[0] = t;
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        threads.add(t);
        try {
            t.start();
        } catch (OutOfMemoryError | IllegalThreadStateException e) {
            threads.remove(t);
            threadCount.decrementAndGet();
            return Refusal.FULL;
        }
        return null;
    }

    /** Threads of scripts that cannot suspend running now. */
    public int threadCount() {
        return threadCount.get();
    }

    public void enter(Watched runtime) {
        running.add(runtime);
        LockSupport.unpark(watchdog);
    }

    public void exit(Watched runtime) {
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
        for (var t : threads) t.interrupt();
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
