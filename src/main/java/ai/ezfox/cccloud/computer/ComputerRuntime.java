package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.HostModule;
import ai.ezfox.cccloud.code.engine.Budget;
import ai.ezfox.cccloud.code.engine.EngineInstance;
import ai.ezfox.cccloud.code.engine.RunResult;
import ai.ezfox.cccloud.code.engine.ScriptEngine;
import ai.ezfox.cccloud.code.engine.ScriptError;
import ai.ezfox.cccloud.code.engine.Task;
import ai.ezfox.cccloud.code.runtime.BlockingRunner;
import ai.ezfox.cccloud.code.runtime.ScriptScheduler;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Runs one boot of a Cloud Computer: its program (the boot code from the ROM, which starts the shell) as one task of a
 * {@link ScriptEngine#createProgram program instance} on the shared Cloud Script workers, never on the server thread.
 *
 * <p>Event model: the boot code waits for the next event through {@link #pull()}, which returns an event at once or a
 * future the task waits on; {@link #queue} (any thread) adds events, {@link #startTimer} fires {@code timer} events
 * from the timer thread. Programs below the boot code wait with {@code coroutine.yield}, so nested coroutines work.
 *
 * <p>Budget like Cloud Scripts ({@link ComputerLimits}): a CPU share per second (then the task pauses), a longest run
 * between two waits (then the program fails), a memory estimate cap measured after allocation and an allocation
 * backstop. A program that ends or fails ends the run; the {@link Owner} decides what the computer does.
 *
 * <p>An engine that cannot suspend ({@link ScriptEngine.Preemption#ABORT}, WebAssembly) runs the program on a thread of
 * its own instead ({@link BlockingRunner}): the same CPU share and longest run between waits, {@link #pull}'s future
 * blocks that thread, the memory cap is the engine's own.
 */
public final class ComputerRuntime implements Budget, ScriptScheduler.Watched {
    static final long SECOND = 1_000_000_000L;
    static final long SLICE_NANOS = 20_000_000L;
    /** Shortest timer: one server tick. */
    static final double MIN_TIMER = 0.05;
    static final double MAX_TIMER = 86_400 * 7;
    private static final com.sun.management.@Nullable ThreadMXBean ALLOC = allocationBean();

    /** Told once when the run ended. {@code error} null: the program ended or was stopped. */
    public interface Owner {
        void ended(ComputerRuntime runtime, @Nullable ScriptError error);
    }

    private final String name;
    private final ScriptEngine engine;
    private final ScriptScheduler scheduler;
    private final ComputerLimits limits;
    private final String boot;
    private final Owner owner;
    private final List<HostModule> modules;
    /** The program's own thread, for engines that cannot suspend; null on the workers. */
    private final @Nullable BlockingRunner runner;
    private final long startNanos = System.nanoTime();

    private final Object lock = new Object();
    // ---- guarded by lock ----
    private final ArrayDeque<Object[]> events = new ArrayDeque<>();
    private final Map<Integer, ScheduledFuture<?>> timers = new HashMap<>();
    private @Nullable CompletableFuture<@Nullable Object> waiter;
    private int nextTimer = 1;
    private int dropped;
    private boolean scheduled;
    private boolean ended;
    private long pausedUntil;
    private @Nullable Job job;

    private volatile @Nullable EngineInstance instance;
    private volatile boolean jobActive;
    private volatile boolean stopRequested;
    private volatile String abortMessage = "stopped";
    private volatile ScriptError.Kind abortKind = ScriptError.Kind.KILLED;

    // ---- worker thread ----
    private @Nullable Task main;
    private long windowStart;
    private long windowUsed;
    private long windowAlloc;
    private long jobStart;
    private long jobBlocked;
    private long jobAllocStart;
    /** CPU since the last wait (over pauses). */
    private long runCpu;
    private long lastHeapCheck;
    private long allocSinceHeap;
    private volatile long heapThreshold;
    private volatile boolean heapRequested;
    private volatile long heapEstimate;

    private sealed interface Job permits Start, Resume, Again {
    }

    private record Start() implements Job {
    }

    private record Resume(@Nullable Object value, @Nullable Throwable failure) implements Job {
    }

    private record Again() implements Job {
    }

    /**
     * @param modules the APIs of the program, made for this runtime (they may call {@link #pull}, {@link #startTimer})
     * @param boot    the boot code (Lua source)
     */
    public ComputerRuntime(String name, ScriptEngine engine, ScriptScheduler scheduler, ComputerLimits limits, String boot,
                           Function<ComputerRuntime, List<HostModule>> modules, Owner owner) {
        this.name = name;
        this.engine = engine;
        this.scheduler = scheduler;
        this.limits = limits;
        this.boot = boot;
        this.owner = owner;
        this.heapThreshold = Math.max(1, limits.memoryBytes() / 4);
        this.runner = engine.preemption() == ScriptEngine.Preemption.ABORT
            ? new BlockingRunner(scheduler, limits.cpuMsPerSecond(), limits.cpuMsPerResume(), () -> stopRequested, lock, (used, seconds) -> {
            })
            : null;
        this.modules = List.copyOf(modules.apply(this));
    }

    public void start() {
        var r = runner;
        synchronized (lock) {
            if (ended || job != null || scheduled) return;
            if (r != null) scheduled = true; // the thread closes the instance when it ends
            else job = new Start();
        }
        if (r == null) {
            schedule();
            return;
        }
        var refusal = r.start(this::threadMain);
        if (refusal != null) {
            synchronized (lock) {
                scheduled = false;
            }
            end(refusal == ScriptScheduler.Refusal.CLOSED ? null : new ScriptError(ScriptError.Kind.ENGINE, name + ": too many " + engine.language()
                + " programs are running on this server (at most " + ScriptScheduler.MAX_THREADS + ")", null, null), refusal == ScriptScheduler.Refusal.CLOSED);
        }
    }

    /** The program's own thread (engines that cannot suspend): load the boot code and run it to its end. */
    private void threadMain() {
        var r = runner;
        try {
            if (r == null || stopRequested) return;
            EngineInstance i;
            Task task;
            var outer = r.enter();
            try {
                i = engine.createProgram(name, modules, this, this::clock);
                instance = i;
                task = i.load(boot);
            } catch (ScriptError e) {
                r.leave(outer);
                end(e, false);
                return;
            }
            RunResult result;
            try {
                result = i.run(task);
            } finally {
                r.leave(outer);
            }
            heapEstimate = i.heapEstimate(limits.memoryBytes(), List.of());
            switch (result) {
                case RunResult.Failed f -> {
                    if (!(stopRequested && f.aborted())) end(f.error(), false);
                }
                default -> end(null, false);
            }
        } catch (RuntimeException | StackOverflowError e) {
            ai.ezfox.cccloud.CloudLog.LOGGER.error("[cccloud] Cloud Computer '{}' crashed the runtime", name, e);
            end(new ScriptError(ScriptError.Kind.ENGINE, name + ": internal error (" + e + ")", null, null), false);
        } finally {
            if (stopRequested) end(null, true);
            synchronized (lock) {
                scheduled = false;
            }
            closeInstance();
        }
    }

    /** Ends the run (shutdown, reboot, unload): a running slice stops at its next check. Any thread. */
    public void stop() {
        stopRequested = true;
        var i = instance;
        if (i != null) i.interrupt();
        if (runner != null) runner.interrupt();
        end(null, true);
    }

    public boolean running() {
        synchronized (lock) {
            return !ended;
        }
    }

    /** Seconds since boot ({@code os.clock}). */
    public double clock() {
        return (System.nanoTime() - startNanos) / 1e9;
    }

    public long heapEstimate() {
        return heapEstimate;
    }

    // ---- events ------------------------------------------------------------------------------------------------------

    /**
     * Adds an event (any thread): {@code name} and plain Java arguments. False if the queue is full (the event is
     * dropped) or the run has ended.
     */
    public boolean queue(String name, @Nullable Object... args) {
        var event = new Object[(args == null ? 0 : args.length) + 1];
        event[0] = name;
        if (args != null) System.arraycopy(args, 0, event, 1, args.length);
        CompletableFuture<@Nullable Object> wake;
        synchronized (lock) {
            if (ended) return false;
            wake = waiter;
            if (wake != null) {
                waiter = null;
            } else {
                if (events.size() >= limits.maxEvents()) {
                    dropped++;
                    return false;
                }
                events.add(event);
                return true;
            }
        }
        wake.complete(event);
        return true;
    }

    /** Events dropped because the queue was full (since boot). */
    public int dropped() {
        synchronized (lock) {
            return dropped;
        }
    }

    /** Worker thread (from the boot code): the next event as values, or a future that completes with it. */
    public Object pull() {
        synchronized (lock) {
            var event = events.poll();
            if (event != null) return event;
            if (waiter != null) throw new ApiError("already waiting for an event");
            var future = new CompletableFuture<@Nullable Object>();
            waiter = future;
            return future;
        }
    }

    /** A timer that queues {@code timer, id} after {@code seconds} (at least one tick). */
    public int startTimer(double seconds) {
        if (Double.isNaN(seconds)) throw new ApiError("bad argument #1 to 'os.startTimer' (number expected, got nan)");
        double delay = Math.max(MIN_TIMER, Math.min(MAX_TIMER, seconds));
        int id;
        synchronized (lock) {
            if (ended) return 0;
            if (timers.size() >= limits.maxTimers()) throw new ApiError("too many timers (at most " + limits.maxTimers() + ")");
            id = nextTimer++;
            if (nextTimer == Integer.MAX_VALUE) nextTimer = 1;
            var future = scheduler.schedule(() -> fire(id), (long) (delay * SECOND));
            if (future == null) return 0;
            timers.put(id, future);
        }
        return id;
    }

    public void cancelTimer(int id) {
        ScheduledFuture<?> future;
        synchronized (lock) {
            future = timers.remove(id);
        }
        if (future != null) future.cancel(false);
    }

    private void fire(int id) {
        synchronized (lock) {
            if (timers.remove(id) == null) return;
        }
        queue("timer", (long) id);
    }

    // ---- scheduling --------------------------------------------------------------------------------------------------

    private void schedule() {
        synchronized (lock) {
            if (ended || scheduled || pausedUntil != 0 || job == null) return;
            scheduled = true;
        }
        if (!scheduler.submit(this::slice)) {
            synchronized (lock) {
                scheduled = false;
            }
            end(null, true);
        }
    }

    @Override
    public void poke() {
        var i = instance;
        if (jobActive && i != null) i.interrupt();
    }

    /** One worker turn; {@code scheduled} stays set while it runs, so there is never a second slice at once. */
    private void slice() {
        try {
            long sliceStart = System.nanoTime();
            while (!stopRequested) {
                Job next;
                synchronized (lock) {
                    if (ended) break;
                    roll(System.nanoTime());
                    if (windowUsed >= limits.cpuMsPerSecond() * 1_000_000L) {
                        if (job != null) pauseLocked(windowStart + SECOND);
                        break;
                    }
                    next = job;
                    job = null;
                }
                if (next == null) break;
                execute(next);
                heap();
                if (System.nanoTime() - sliceStart > SLICE_NANOS) break;
            }
        } catch (RuntimeException | StackOverflowError e) {
            ai.ezfox.cccloud.CloudLog.LOGGER.error("[cccloud] Cloud Computer '{}' crashed the runtime", name, e);
            end(new ScriptError(ScriptError.Kind.ENGINE, name + ": internal error (" + e + ")", null, null), false);
        } finally {
            if (stopRequested) end(null, true);
            boolean resubmit;
            boolean close;
            synchronized (lock) {
                scheduled = false;
                close = ended;
                resubmit = !ended && job != null && pausedUntil == 0;
            }
            if (close) closeInstance();
            else if (resubmit) schedule();
        }
    }

    private void closeInstance() {
        var i = instance;
        instance = null;
        if (i != null) i.close();
    }

    private void execute(Job next) {
        var i = instance;
        if (next instanceof Start) {
            jobStart = System.nanoTime();
            try {
                i = engine.createProgram(name, modules, this, this::clock);
                instance = i;
                main = i.load(boot);
            } catch (ScriptError e) {
                end(e, false);
                return;
            }
            windowUsed += System.nanoTime() - jobStart;
        }
        if (i == null || main == null) return;
        jobStart = System.nanoTime();
        jobBlocked = 0;
        jobAllocStart = allocated();
        RunResult result;
        jobActive = true;
        scheduler.enter(this);
        try {
            result = next instanceof Resume r ? i.resume(main, r.value(), r.failure()) : i.run(main);
        } finally {
            jobActive = false;
            scheduler.exit(this);
        }
        long used = Math.max(0, System.nanoTime() - jobStart - jobBlocked);
        long alloc = allocated() - jobAllocStart;
        windowUsed += used;
        windowAlloc += alloc;
        allocSinceHeap += alloc;
        runCpu += used;
        switch (result) {
            case RunResult.Done d -> end(null, false);
            case RunResult.Waiting w -> {
                runCpu = 0;
                w.future().whenComplete((value, failure) -> {
                    synchronized (lock) {
                        if (ended) return;
                        job = new Resume(value, failure);
                    }
                    schedule();
                });
            }
            case RunResult.Paused p -> {
                boolean shareLeft = heapRequested && windowUsed < limits.cpuMsPerSecond() * 1_000_000L;
                synchronized (lock) {
                    if (ended) return;
                    job = new Again();
                    if (!stopRequested && !shareLeft) pauseLocked(windowStart + SECOND);
                }
            }
            case RunResult.Yielded y -> {
                runCpu = 0;
                synchronized (lock) {
                    if (!ended) job = new Again();
                }
            }
            case RunResult.Failed f -> {
                if (!(stopRequested && f.aborted())) end(f.error(), false);
            }
        }
    }

    private void pauseLocked(long until) {
        if (ended) return;
        pausedUntil = Math.max(1, until);
        var wake = scheduler.schedule(() -> {
            synchronized (lock) {
                pausedUntil = 0;
            }
            schedule();
        }, until - System.nanoTime());
        if (wake == null) {
            pausedUntil = 0;
            stopRequested = true;
        }
    }

    private void roll(long now) {
        if (windowStart == 0) windowStart = now;
        if (now - windowStart < SECOND) return;
        windowStart += (now - windowStart) / SECOND * SECOND;
        windowUsed = 0;
        windowAlloc = 0;
    }

    private void heap() {
        var i = instance;
        if (i == null || !running()) return;
        long now = System.nanoTime();
        long since = now - lastHeapCheck;
        boolean due = heapRequested || (ALLOC != null ? allocSinceHeap > heapThreshold || allocSinceHeap > 0 && since > 10 * SECOND : since > 10 * SECOND);
        heapRequested = false;
        if (!due) return;
        long estimate = i.heapEstimate(limits.memoryBytes(), List.of());
        long end = System.nanoTime();
        windowUsed += end - now;
        lastHeapCheck = end;
        allocSinceHeap = 0;
        heapEstimate = estimate;
        heapThreshold = Math.max(limits.memoryBytes() / 4, 2 * Math.min(estimate, limits.memoryBytes()));
        if (estimate > limits.memoryBytes()) {
            end(new ScriptError(ScriptError.Kind.MEMORY_LIMIT, "out of memory (more than " + (limits.memoryBytes() >> 20) + " MB)", null, null), false);
        }
    }

    private void end(@Nullable ScriptError error, boolean stopped) {
        var cancel = new ArrayList<java.util.concurrent.Future<?>>();
        synchronized (lock) {
            if (ended) return;
            ended = true;
            job = null;
            events.clear();
            cancel.addAll(timers.values());
            timers.clear();
            if (waiter != null) cancel.add(waiter);
            waiter = null;
        }
        stopRequested = true;
        for (var f : cancel) f.cancel(false);
        boolean idle;
        synchronized (lock) {
            idle = !scheduled;
        }
        if (idle) closeInstance();
        try {
            owner.ended(this, stopped ? null : error);
        } catch (RuntimeException e) {
            ai.ezfox.cccloud.CloudLog.LOGGER.warn("[cccloud] Cloud Computer owner failed", e);
        }
    }

    // ---- budget ------------------------------------------------------------------------------------------------------

    @Override
    public Decision check() {
        if (stopRequested) {
            abortMessage = "stopped";
            abortKind = ScriptError.Kind.KILLED;
            return Decision.ABORT;
        }
        if (runner != null) {
            return switch (runner.check()) {
                case CONTINUE -> Decision.CONTINUE;
                case STOPPED -> {
                    abortMessage = "stopped";
                    abortKind = ScriptError.Kind.KILLED;
                    yield Decision.ABORT;
                }
                case TOO_LONG -> {
                    abortMessage = "too long without yielding";
                    abortKind = ScriptError.Kind.CPU_LIMIT;
                    yield Decision.ABORT;
                }
            };
        }
        long used = System.nanoTime() - jobStart - jobBlocked;
        long jobAlloc = ALLOC == null ? 0 : allocated() - jobAllocStart;
        if (ALLOC != null && allocSinceHeap + jobAlloc > heapThreshold) {
            heapRequested = true;
            return Decision.PAUSE;
        }
        if (ALLOC != null && windowAlloc + jobAlloc > limits.allocBytesPerSecond()) {
            abortMessage = "out of memory (allocated more than " + (limits.allocBytesPerSecond() >> 20) + " MB within a second)";
            abortKind = ScriptError.Kind.MEMORY_LIMIT;
            return Decision.ABORT;
        }
        if (runCpu + used > limits.cpuMsPerResume() * 1_000_000L) {
            abortMessage = "too long without yielding";
            abortKind = ScriptError.Kind.CPU_LIMIT;
            return Decision.ABORT;
        }
        if (windowUsed + used >= limits.cpuMsPerSecond() * 1_000_000L) return Decision.PAUSE;
        return Decision.CONTINUE;
    }

    @Override
    public String abortMessage() {
        return abortMessage;
    }

    @Override
    public ScriptError.Kind abortKind() {
        return abortKind;
    }

    @Override
    public long memoryBytes() {
        return limits.memoryBytes();
    }

    @Override
    public void blocked(long nanos) {
        if (runner != null) runner.blocked(nanos);
        else jobBlocked += nanos;
    }

    /** Engines that cannot suspend wait here for a host call ({@link #pull}'s event, an action); not CPU time. */
    @Override
    public <T> T await(CompletableFuture<T> future) throws InterruptedException, java.util.concurrent.ExecutionException {
        var r = runner;
        return r == null ? Budget.super.await(future) : r.await(future, null);
    }

    private static long allocated() {
        return ALLOC == null ? 0 : ALLOC.getCurrentThreadAllocatedBytes();
    }

    private static com.sun.management.@Nullable ThreadMXBean allocationBean() {
        try {
            if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()) {
                if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
                return bean.getCurrentThreadAllocatedBytes() >= 0 ? bean : null;
            }
        } catch (RuntimeException | LinkageError e) {
            // not HotSpot: only the 10 s measurement
        }
        return null;
    }
}
