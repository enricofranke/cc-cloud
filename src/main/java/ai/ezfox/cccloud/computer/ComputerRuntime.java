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
import ai.ezfox.cccloud.code.runtime.CpuBudget;
import ai.ezfox.cccloud.code.runtime.ScriptScheduler;
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
 * backstop, all kept by a {@link CpuBudget} in the CPU time of the thread that runs the program (a lagging server or a
 * GC pause does not count). A program that ends or fails ends the run; the {@link Owner} decides what the computer does.
 *
 * <p>An engine that cannot suspend ({@link ScriptEngine.Preemption#ABORT}, WebAssembly) runs the program on a thread of
 * its own instead ({@link BlockingRunner}): the same CPU share and longest run between waits, {@link #pull}'s future
 * blocks that thread, the memory cap is the engine's own.
 */
public final class ComputerRuntime implements Budget, ScriptScheduler.Watched {
    static final long SECOND = CpuBudget.SECOND;
    static final long SLICE_NANOS = 20_000_000L;
    /** Shortest timer: one server tick. */
    static final double MIN_TIMER = 0.05;
    static final double MAX_TIMER = 86_400 * 7;

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
    /** CPU share, longest run, allocation and heap timing; thread CPU time, read on the thread that runs the program. */
    private final CpuBudget budget;

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
    /** A WebAssembly program started from the shell ({@link #startWasm}): it gets the events while it runs. */
    private @Nullable ChildProgram child;
    private int nextChild = 1;

    private volatile @Nullable EngineInstance instance;
    private volatile boolean jobActive;
    private volatile boolean stopRequested;
    private volatile String abortMessage = "stopped";
    private volatile ScriptError.Kind abortKind = ScriptError.Kind.KILLED;

    // ---- worker thread ----
    private @Nullable Task main;
    /** CPU since the last wait (over pauses). */
    private long runCpu;

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
        boolean blocking = engine.preemption() == ScriptEngine.Preemption.ABORT;
        // an engine that cannot suspend has a hard memory cap of its own: no allocation or heap limits in the budget
        this.budget = new CpuBudget(scheduler.clock(), limits.cpuMsPerSecond(), limits.cpuMsPerResume(),
            blocking ? CpuBudget.UNLIMITED : limits.allocBytesPerSecond(), blocking ? CpuBudget.UNLIMITED : limits.memoryBytes());
        this.runner = blocking ? new BlockingRunner(scheduler, budget, () -> stopRequested, lock, (used, seconds) -> {
        }) : null;
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
            budget.heapMeasured(i.heapEstimate(limits.memoryBytes(), List.of()));
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
        return budget.heapEstimate();
    }

    /** CPU time the program used so far (any thread). */
    public long cpuTotalNanos() {
        return budget.totalCpuNanos();
    }

    /** How often the program was paused until the next second because its CPU share was used up (any thread). */
    public int sharePauses() {
        return budget.sharePauses();
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
        ChildProgram running;
        synchronized (lock) {
            running = child;
        }
        if (running != null) {
            // a WebAssembly program runs: its events (Ctrl+T ends it; the shell then gets "wasm_done")
            if (name.equals("terminate")) {
                running.stop("Terminated");
                return true;
            }
            if (running.queue(event)) return true;
        }
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

    /**
     * Starts a WebAssembly program from the shell (boot code, {@code __native.runWasm}): it runs on its own thread with
     * this computer's modules and gets the events until it ends; then the shell gets {@code wasm_done, id, ok, error}.
     * Returns the id. At most one at a time.
     */
    public long startWasm(ScriptEngine wasm, String name, String source, List<Object> args) {
        return startWasm(wasm, name, source, args, name, () -> {
        });
    }

    /**
     * {@link #startWasm} with the player whose share of the threads it takes ({@code owner}, the computer's owner) and
     * {@code released} run once when the program ended (or did not start).
     */
    public long startWasm(ScriptEngine wasm, String name, String source, List<Object> args, Object owner, Runnable released) {
        ChildProgram started;
        synchronized (lock) {
            if (ended) throw new ApiError("the computer is shutting down");
            if (child != null) throw new ApiError("a WebAssembly program is already running");
            // the program's memory is what the computer's Lua does not use (it waits meanwhile)
            long memory = limits.memoryBytes() - Math.max(0, budget.heapEstimate());
            if (memory < 1L << 20) throw new ApiError("not enough memory left for a WebAssembly program");
            int id = nextChild++;
            started = new ChildProgram(id, name, wasm, source, args, modules, scheduler, limits, memory, owner, (child, error) -> {
                released.run();
                wasmEnded(child, error);
            });
            child = started;
        }
        started.start();
        return started.id;
    }

    private void wasmEnded(ChildProgram ended, @Nullable String error) {
        synchronized (lock) {
            if (child == ended) child = null;
        }
        queue("wasm_done", (long) ended.id, error == null, error);
    }

    /** Whether a WebAssembly program started from the shell is running (tests). */
    public boolean wasmRunning() {
        synchronized (lock) {
            return child != null;
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
                    budget.roll(System.nanoTime());
                    if (budget.shareUsed()) {
                        if (job != null) pauseLocked(budget.pauseForShare());
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
            long loadStart = budget.mark();
            try {
                i = engine.createProgram(name, modules, this, this::clock);
                instance = i;
                main = i.load(boot);
            } catch (ScriptError e) {
                end(e, false);
                return;
            }
            budget.chargeSince(loadStart);
        }
        if (i == null || main == null) return;
        budget.startJob();
        RunResult result;
        jobActive = true;
        scheduler.enter(this);
        try {
            result = next instanceof Resume r ? i.resume(main, r.value(), r.failure()) : i.run(main);
        } finally {
            jobActive = false;
            scheduler.exit(this);
        }
        runCpu += budget.endJob();
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
                boolean shareLeft = budget.heapPauseOnly();
                synchronized (lock) {
                    if (ended) return;
                    job = new Again();
                    if (!stopRequested && !shareLeft) pauseLocked(budget.pauseForShare());
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

    private void heap() {
        var i = instance;
        if (i == null || !running()) return;
        long estimate = budget.measureHeap(System.nanoTime(), () -> i.heapEstimate(limits.memoryBytes(), List.of()));
        if (estimate > limits.memoryBytes()) {
            end(new ScriptError(ScriptError.Kind.MEMORY_LIMIT, "out of memory (more than " + (limits.memoryBytes() >> 20) + " MB)", null, null), false);
        }
    }

    private void end(@Nullable ScriptError error, boolean stopped) {
        var cancel = new ArrayList<java.util.concurrent.Future<?>>();
        ChildProgram running;
        synchronized (lock) {
            if (ended) return;
            ended = true;
            running = child;
            child = null;
            job = null;
            events.clear();
            cancel.addAll(timers.values());
            timers.clear();
            if (waiter != null) cancel.add(waiter);
            waiter = null;
        }
        stopRequested = true;
        if (running != null) running.stop("stopped");
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
        return switch (budget.check(runCpu)) {
            case CONTINUE -> Decision.CONTINUE;
            case MEASURE_HEAP, PAUSE -> Decision.PAUSE;
            case TOO_MUCH_ALLOCATION -> {
                abortMessage = "out of memory (allocated more than " + (limits.allocBytesPerSecond() >> 20) + " MB within a second)";
                abortKind = ScriptError.Kind.MEMORY_LIMIT;
                yield Decision.ABORT;
            }
            case TOO_LONG -> {
                abortMessage = "too long without yielding";
                abortKind = ScriptError.Kind.CPU_LIMIT;
                yield Decision.ABORT;
            }
        };
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
        // one budget for both paths: thread CPU time does not run while the engine waits, wall time is given back
        budget.blocked(nanos);
    }

    /** Engines that cannot suspend wait here for a host call ({@link #pull}'s event, an action); not CPU time. */
    @Override
    public <T> T await(CompletableFuture<T> future) throws InterruptedException, java.util.concurrent.ExecutionException {
        var r = runner;
        return r == null ? Budget.super.await(future) : r.await(future, null);
    }
}
