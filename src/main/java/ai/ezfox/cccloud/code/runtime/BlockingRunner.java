package ai.ezfox.cccloud.code.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The run of an engine that cannot suspend ({@link ai.ezfox.cccloud.code.engine.ScriptEngine.Preemption#ABORT},
 * WebAssembly) on a thread of its own, shared by Cloud Scripts ({@link ScriptRuntime}) and Cloud Computers: the thread
 * ({@link ScriptScheduler#startThread}), the CPU book of the running task (a stretch from one wait to the next, over
 * nested tasks), the per-second share (the thread blocks itself until the next second, {@link #holdForShare}) and the
 * wait for a host call's future ({@link #await}), during which the owner may run its other tasks. The accounting is the
 * owner's {@link CpuBudget} (thread CPU time: waiting, a GC pause or a loaded server is not charged; the windows of a
 * second are wall time), the same the owner uses for engines that suspend.
 *
 * <p>All methods but {@link #start}, {@link #interrupt} and the readers are called on the run's own thread.
 */
public final class BlockingRunner {
    /** Runs the owner's other ready work while a task waits; returns when {@code future} is done or nothing is left to do. */
    @FunctionalInterface
    public interface Pump {
        void runUntil(CompletableFuture<?> future) throws InterruptedException;
    }

    /** Told when a CPU window of a second closes: the CPU it used and how many seconds passed. */
    @FunctionalInterface
    public interface Seconds {
        void closed(long usedNanos, long seconds);
    }

    /** What {@link #check} found. */
    public enum Verdict { CONTINUE, STOPPED, TOO_LONG }

    /** The book of an interrupted stretch, restored by {@link #leave}. */
    public record Stretch(CpuBudget.Job job, long cpu) {
    }

    private final ScriptScheduler scheduler;
    private final CpuBudget budget;
    private final BooleanSupplier stopping;
    private final Object lock;
    private final Seconds seconds;
    private volatile @Nullable Thread thread;
    // ---- the run's thread ----
    /** CPU of the current stretch (from the last wait, over share pauses). */
    private long stretchCpu;

    /**
     * @param budget   the owner's budget: share per second, longest stretch (its run limit), without memory limits (the
     *                 engine has a hard cap of its own)
     * @param stopping whether the run is being stopped (any thread)
     * @param lock     the owner's lock; notified when work arrives, waited on while the share is used up
     */
    public BlockingRunner(ScriptScheduler scheduler, CpuBudget budget, BooleanSupplier stopping, Object lock, Seconds seconds) {
        this.scheduler = scheduler;
        this.budget = budget;
        this.stopping = stopping;
        this.lock = lock;
        this.seconds = seconds;
    }

    /** Starts the run's thread; null if it started. */
    public ScriptScheduler.@Nullable Refusal start(Runnable body) {
        return scheduler.startThread(() -> {
            thread = Thread.currentThread();
            try {
                body.run();
            } finally {
                thread = null;
                Thread.interrupted();
            }
        });
    }

    /** Any thread: wakes the run's thread out of a wait (a stop). */
    public void interrupt() {
        var t = thread;
        if (t != null && t != Thread.currentThread()) t.interrupt();
    }

    /** CPU time of the whole run so far. */
    public long total() {
        return budget.totalCpuNanos();
    }

    // ---- the book ------------------------------------------------------------------------------------------------------

    /** A task starts (possibly inside another task's wait): a fresh stretch; returns the book to {@link #leave} with. */
    public Stretch enter() {
        var outer = new Stretch(budget.job(), stretchCpu);
        budget.startJob();
        stretchCpu = 0;
        return outer;
    }

    /** The task ended: books its CPU time and returns to the interrupted stretch. Returns the CPU time it used. */
    public long leave(Stretch outer) {
        long used = book();
        budget.resume(outer.job());
        stretchCpu = outer.cpu();
        return used;
    }

    /** Books the CPU of the stretch so far and goes on measuring from now. */
    private long book() {
        long used = budget.endJob();
        stretchCpu += used;
        budget.startJob();
        return used;
    }

    /** Time inside a call that was not CPU for the script (only counted out with the wall-time fallback clock). */
    public void blocked(long nanos) {
        budget.blocked(nanos);
    }

    /**
     * From inside the engine: stop, too long without waiting, or go on; blocks here first while the share of this
     * second is used up (the engine cannot pause, so the thread waits instead).
     */
    public Verdict check() {
        if (stopping.getAsBoolean()) return Verdict.STOPPED;
        roll(System.nanoTime());
        switch (budget.check(stretchCpu)) {
            case TOO_LONG -> {
                return Verdict.TOO_LONG;
            }
            case PAUSE -> {
                book();
                holdForShare();
                budget.startJob();
                if (stopping.getAsBoolean() || Thread.currentThread().isInterrupted()) return Verdict.STOPPED;
            }
            default -> {
                // CONTINUE; no memory verdicts, the budget has no memory limits here
            }
        }
        return Verdict.CONTINUE;
    }

    /** Blocks the run's thread while the CPU share of this second is used up. */
    public void holdForShare() {
        boolean counted = false;
        while (!stopping.getAsBoolean()) {
            long now = System.nanoTime();
            roll(now);
            if (!budget.shareUsed()) return;
            if (!counted) {
                budget.pauseForShare();
                counted = true;
            }
            long waitMs = Math.max(1, (budget.windowEnd() - now) / 1_000_000L);
            synchronized (lock) {
                try {
                    lock.wait(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void roll(long now) {
        long passed = budget.roll(now);
        if (passed > 0) seconds.closed(budget.lastWindowUsed(), passed);
    }

    // ---- waiting -------------------------------------------------------------------------------------------------------

    /**
     * Waits on the run's thread for a host call's future. The CPU so far is booked and the task counts as having waited
     * (its stretch starts again afterwards); {@code pump} (may be null) runs the owner's other tasks meanwhile.
     */
    public <T> T await(CompletableFuture<T> future, @Nullable Pump pump) throws InterruptedException, ExecutionException {
        book();
        stretchCpu = 0;
        try {
            if (pump != null) pump.runUntil(future);
            while (!future.isDone() && !stopping.getAsBoolean()) {
                try {
                    future.get(200, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    // look at the stop flag again
                }
            }
            if (stopping.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
            holdForShare();
            if (stopping.getAsBoolean()) throw new InterruptedException();
            return future.get();
        } finally {
            budget.startJob();
        }
    }
}
