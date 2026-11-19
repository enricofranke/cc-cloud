package ai.ezfox.cccloud.code.runtime;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.ScriptFunction;
import ai.ezfox.cccloud.code.api.ScriptHost;
import ai.ezfox.cccloud.code.engine.Budget;
import ai.ezfox.cccloud.code.engine.EngineInstance;
import ai.ezfox.cccloud.code.engine.RunResult;
import ai.ezfox.cccloud.code.engine.ScriptEngine;
import ai.ezfox.cccloud.code.engine.ScriptError;
import ai.ezfox.cccloud.code.engine.Task;
import ai.ezfox.cccloud.code.world.ScriptIdentity;
import ai.ezfox.cccloud.code.world.ScriptWorld;
import ai.ezfox.cccloud.code.world.WorldAction;
import ai.ezfox.cccloud.code.world.WorldEvents;
import ai.ezfox.cccloud.code.world.WorldSnapshot;
import ai.ezfox.cccloud.web.TokenBucket;
import java.lang.management.ManagementFactory;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * One running script: its engine instance, its tasks (the main body, handler and timer calls), handlers, timers and
 * waits, its CPU and memory budget. Scripts run on the {@link ScriptScheduler}'s workers in slices, one slice of a
 * script at a time; snapshots, timer fires and completed actions arrive from other threads and only queue work.
 *
 * <h2>Budget</h2>
 * Each script gets {@code cpuMsPerSecond} of worker time per second (then it is paused, invisibly, until the next
 * second) and a task may run {@code cpuMsPerResume} in total without waiting for anything (a sleep, an event, an action)
 * before it is stopped with an error the script cannot catch. Allocation (when the JVM can measure it per thread) is
 * capped per second, and the reachable heap is estimated every few seconds. An uncaught error ends the whole script.
 *
 * <h2>Engines that cannot suspend</h2>
 * A {@link ScriptEngine.Preemption#ABORT} engine (WebAssembly) runs on a thread of its own
 * ({@link ScriptScheduler#startThread}) instead of the workers: the thread runs one task at a time to its end. The
 * engine calls {@link #check()} from inside the guest; when the per-second share is used up, the check blocks the thread
 * until the next second. Host calls that wait (sleep, events, actions) block in {@link #await}, which meanwhile runs the
 * script's handlers and timers on the same thread (nested at most {@link #MAX_NESTING} deep), so a script that loops
 * with {@code cloud.sleep} still reacts to events. The same limits apply (CPU per second and per stretch without
 * waiting, timers, handlers, tasks); memory is the engine's hard cap. Stopping it sets the flag its next check sees and
 * interrupts its thread.
 */
public final class ScriptRuntime implements ScriptHost, Budget {
    public enum Status { STARTING, RUNNING, STOPPED, ERROR }

    public interface Listener {
        /** Called once when the script ended: {@code reason} (finished, stopped, error, kill_switch, …) and the error. */
        void ended(ScriptRuntime runtime, String reason, @Nullable ScriptError error);

        /** Handlers or timers changed. */
        default void changed(ScriptRuntime runtime) {
        }
    }

    static final long SECOND = 1_000_000_000L;
    /** A worker gives other scripts a turn after this long. */
    static final long SLICE_NANOS = 20_000_000L;
    static final double MIN_SLEEP = 0.05;
    /** How deep handlers may run inside waits of other tasks (ABORT engines). */
    static final int MAX_NESTING = 4;
    private static final com.sun.management.@Nullable ThreadMXBean ALLOC = allocationBean();

    private final ScriptIdentity who;
    private final int version;
    private final String source;
    private final ScriptEngine engine;
    /** The engine cannot suspend: the script runs on its own thread (see the class comment). */
    private final boolean blocking;
    private final ScriptScheduler scheduler;
    private final ScriptWorld world;
    private final Limits limits;
    private final ScriptLog log;
    private final ScriptStore store;
    private final Listener listener;
    private final Supplier<ZoneId> zone;
    private final long startedAt = System.currentTimeMillis();
    private final long startNanos = System.nanoTime();
    private final TokenBucket actions;
    private final TokenBucket notifications;
    private final AuditThrottle audit;

    private final Object lock = new Object();
    // ---- guarded by lock ----
    private final ArrayDeque<Job> ready = new ArrayDeque<>();
    private final LinkedHashMap<Integer, Handler> handlers = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Timer> timers = new LinkedHashMap<>();
    private final List<Waiter> waiters = new ArrayList<>();
    private final Set<CompletableFuture<?>> waiting = new HashSet<>();
    private int nextId = 1;
    private int liveTasks;
    private int droppedEvents;
    private boolean scheduled;
    private boolean inSlice;
    private boolean ended;
    private Status status = Status.STARTING;
    private @Nullable String endReason;
    private @Nullable ScriptError endError;
    private @Nullable WorldSnapshot pendingSnapshot;
    private @Nullable WorldSnapshot eventBase;
    private long pausedUntil;

    private volatile WorldSnapshot snapshot;
    private volatile @Nullable EngineInstance instance;
    private volatile boolean jobActive;
    /** Stopped from outside (a person, the kill switch): the running task is aborted, the error is not the script's. */
    private volatile boolean stopRequested;
    private volatile String abortMessage = "stopped";
    private volatile ScriptError.Kind abortKind = ScriptError.Kind.KILLED;

    // ---- worker thread (one slice at a time) ----
    private long windowStart;
    private long windowUsed;
    private long windowAlloc;
    private final long[] lastSeconds = new long[10];
    private int secondsSeen;
    private volatile long lastSecondNanos;
    private volatile long averageNanos;
    private long jobStart;
    private long jobBlocked;
    private long jobAllocStart;
    private @Nullable Run currentRun;
    private long lastHeapCheck;
    private long allocSinceHeap;
    /** Allocation after which the next heap estimate is due: max(memory / 4, twice the last estimate). */
    private volatile long heapThreshold;
    /** The budget check paused a task so the heap can be measured (not because the CPU share is used up). */
    private volatile boolean heapRequested;
    private volatile long heapEstimate;
    private volatile long totalCpuNanos;
    // ---- own thread (ABORT engines) ----
    private volatile @Nullable Thread thread;
    private int nesting;

    private static final class Run {
        final Task task;
        final @Nullable Timer timer;
        long cpu;

        Run(Task task, @Nullable Timer timer) {
            this.task = task;
            this.timer = timer;
        }
    }

    private sealed interface Job permits Call, Resume, Again {
    }

    /** Start a task: the main body ({@code main} set) or a handler/timer call. */
    private record Call(@Nullable Task main, @Nullable ScriptFunction function, String label, @Nullable Object arg, @Nullable Timer timer) implements Job {
    }

    private record Resume(Run run, @Nullable Object value, @Nullable Throwable failure) implements Job {
    }

    private record Again(Run run) implements Job {
    }

    private record Handler(WorldEvents.Filter filter, ScriptFunction function) {
    }

    private record Waiter(WorldEvents.Filter filter, CompletableFuture<@Nullable Object> future) {
    }

    private static final class Timer {
        final int id;
        final String label;
        final ScriptFunction function;
        final boolean repeat;
        final @Nullable TimeSpec spec;
        @Nullable ScheduledFuture<?> future;
        boolean busy;

        Timer(int id, String label, ScriptFunction function, boolean repeat, @Nullable TimeSpec spec) {
            this.id = id;
            this.label = label;
            this.function = function;
            this.repeat = repeat;
            this.spec = spec;
        }
    }

    public ScriptRuntime(ScriptIdentity who, int version, String source, ScriptEngine engine, ScriptScheduler scheduler, ScriptWorld world, Limits limits,
                         ScriptLog log, ScriptStore store, Listener listener, Supplier<ZoneId> zone, WorldSnapshot snapshot) {
        this.who = who;
        this.version = version;
        this.source = source;
        this.engine = engine;
        this.blocking = engine.preemption() == ScriptEngine.Preemption.ABORT;
        this.scheduler = scheduler;
        this.world = world;
        this.limits = limits;
        this.log = log;
        this.store = store;
        this.listener = listener;
        this.zone = zone;
        this.snapshot = snapshot;
        this.eventBase = snapshot.seq() == 0 ? null : snapshot;
        this.actions = new TokenBucket(limits.actionsPerSecond(), limits.actionsPerSecond());
        this.notifications = new TokenBucket(limits.notifyBurst(), limits.notifyPerSecond());
        this.audit = new AuditThrottle(limits.auditPerMinute(), System::currentTimeMillis);
        this.heapThreshold = Math.max(1, limits.memoryBytes() / 4);
    }

    // ---- life cycle ----------------------------------------------------------------------------------------------------

    /** Loads the script on a worker and runs its body. */
    public void start() {
        synchronized (lock) {
            if (ended) return;
            if (blocking) inSlice = true; // the thread closes the instance when it ends
        }
        if (!blocking) {
            schedule();
            return;
        }
        var refusal = scheduler.startThread(this::threadMain);
        if (refusal != null) {
            synchronized (lock) {
                inSlice = false;
            }
            if (refusal == ScriptScheduler.Refusal.CLOSED) end("server_stopping", null);
            else end("error", new ScriptError(ScriptError.Kind.ENGINE, who.scriptName() + ": too many " + engine.language()
                + " scripts are running on this server (at most " + ScriptScheduler.MAX_THREADS + ")", null, null));
        }
    }

    /** Stops the script ({@code reason}: stopped, kill_switch, offline, command, …); a running slice ends at its next check. */
    public void stop(String reason) {
        abortMessage = "stopped";
        stopRequested = true;
        var i = instance;
        if (i != null) i.interrupt();
        var t = thread;
        if (t != null && t != Thread.currentThread()) t.interrupt();
        end(reason, null);
    }

    private void end(String reason, @Nullable ScriptError error) {
        var cancel = new ArrayList<java.util.concurrent.Future<?>>();
        boolean close;
        synchronized (lock) {
            if (ended) return;
            ended = true;
            endReason = reason;
            endError = error;
            status = error != null ? Status.ERROR : Status.STOPPED;
            ready.clear();
            for (var t : timers.values()) if (t.future != null) cancel.add(t.future);
            for (var w : waiters) cancel.add(w.future());
            cancel.addAll(waiting);
            waiting.clear();
            waiters.clear();
            timers.clear();
            handlers.clear();
            liveTasks = 0;
            close = !inSlice;
            lock.notifyAll();
        }
        stopRequested = true;
        for (var f : cancel) f.cancel(false);
        if (close) closeInstance();
        String flushed = audit.flush();
        if (flushed != null) world.audit(who, who.label() + ": " + flushed);
        if (error != null) {
            log.system("error", error.getMessage());
            String trace = error.traceback();
            if (trace != null && trace.contains("\n")) log.system("error", trace.substring(trace.indexOf('\n') + 1).lines().limit(6).reduce((a, b) -> a + "\n" + b).orElse(""));
        } else {
            log.system("system", "stopped (" + reason + ")");
        }
        try {
            listener.ended(this, reason, error);
        } catch (RuntimeException e) {
            ai.ezfox.cccloud.CloudLog.LOGGER.warn("[cccloud] Script listener failed", e);
        }
    }

    private void closeInstance() {
        var i = instance;
        instance = null;
        if (i != null) i.close();
    }

    private void schedule() {
        if (blocking) {
            // the script's own thread picks the work up
            synchronized (lock) {
                lock.notifyAll();
            }
            return;
        }
        synchronized (lock) {
            if (ended || scheduled || pausedUntil != 0) return;
            scheduled = true;
        }
        if (!scheduler.submit(this::slice)) {
            synchronized (lock) {
                scheduled = false;
            }
            end("server_stopping", null);
        }
    }

    /** The watchdog: make the engine look at the budget if a task runs right now. */
    void poke() {
        var i = instance;
        if (jobActive && i != null) i.interrupt();
    }

    // ---- slices --------------------------------------------------------------------------------------------------------

    private void slice() {
        synchronized (lock) {
            if (ended) {
                scheduled = false;
                return;
            }
            inSlice = true;
        }
        boolean again = false;
        try {
            long sliceStart = System.nanoTime();
            if (instance == null && !load()) return;
            events();
            while (!stopRequested) {
                long now = System.nanoTime();
                roll(now);
                if (windowUsed >= limits.cpuMsPerSecond() * 1_000_000L) {
                    pause(windowStart + SECOND);
                    break;
                }
                Job job;
                synchronized (lock) {
                    if (ended) break;
                    job = ready.poll();
                }
                if (job == null) break;
                execute(job);
                if (System.nanoTime() - sliceStart > SLICE_NANOS) {
                    again = true;
                    break;
                }
            }
        } catch (RuntimeException | StackOverflowError e) {
            ai.ezfox.cccloud.CloudLog.LOGGER.error("[cccloud] Cloud Script '{}' crashed the runtime", who.scriptName(), e);
            end("error", new ScriptError(ScriptError.Kind.ENGINE, who.scriptName() + ": internal error (" + e + ")", null, null));
        } finally {
            boolean close;
            boolean resubmit;
            synchronized (lock) {
                inSlice = false;
                close = ended;
                resubmit = !ended && pausedUntil == 0 && (again || !ready.isEmpty() || pendingSnapshot != null);
                scheduled = resubmit;
            }
            if (close) closeInstance();
            else if (resubmit && !scheduler.submit(this::slice)) {
                synchronized (lock) {
                    scheduled = false;
                }
                end("server_stopping", null);
            }
        }
    }

    private boolean load() {
        jobStart = System.nanoTime();
        if (limits.memoryBytes() < engine.minimumMemory()) {
            end("error", new ScriptError(ScriptError.Kind.ENGINE, who.scriptName() + ": " + engine.language() + " needs at least "
                + (engine.minimumMemory() >> 20) + " MB of memory, the server allows " + (limits.memoryBytes() >> 20) + " MB", null, null));
            return false;
        }
        try {
            var i = engine.create(who.scriptName(), this, this);
            instance = i;
            var main = i.load(source);
            synchronized (lock) {
                if (ended) return false;
                status = Status.RUNNING;
                liveTasks++;
                ready.addFirst(new Call(main, null, main.label(), null, null));
            }
            log.system("system", "started (version " + version + ") by " + who.starterName() + ", acting with the rights of " + who.authorName());
            windowUsed += System.nanoTime() - jobStart;
            return true;
        } catch (ScriptError e) {
            end("error", e);
            return false;
        }
    }

    private void execute(Job job) {
        var i = instance;
        if (i == null) return;
        Run run = switch (job) {
            case Call c -> new Run(c.main() != null ? c.main() : i.call(c.function(), c.label(), c.arg() == null ? new Object[0] : new Object[]{c.arg()}), c.timer());
            case Resume r -> r.run();
            case Again a -> a.run();
        };
        currentRun = run;
        jobStart = System.nanoTime();
        jobBlocked = 0;
        jobAllocStart = allocated();
        RunResult result;
        jobActive = true;
        scheduler.enter(this);
        try {
            result = switch (job) {
                case Resume r -> i.resume(run.task, r.value(), r.failure());
                default -> i.run(run.task);
            };
        } finally {
            jobActive = false;
            scheduler.exit(this);
        }
        long used = Math.max(0, System.nanoTime() - jobStart - jobBlocked);
        long alloc = allocated() - jobAllocStart;
        windowUsed += used;
        windowAlloc += alloc;
        allocSinceHeap += alloc;
        run.cpu += used;
        totalCpuNanos += used;
        currentRun = null;
        switch (result) {
            case RunResult.Done d -> finished(run);
            case RunResult.Waiting w -> {
                run.cpu = 0;
                await(run, w.future());
            }
            case RunResult.Paused p -> {
                synchronized (lock) {
                    if (!ended) ready.addFirst(new Again(run));
                }
                // paused for a heap measurement only: go on right after it, within the CPU share
                boolean shareLeft = heapRequested && windowUsed < limits.cpuMsPerSecond() * 1_000_000L;
                if (!stopRequested && !shareLeft) pause(windowStart + SECOND);
            }
            case RunResult.Yielded y -> {
                run.cpu = 0;
                synchronized (lock) {
                    if (!ended) ready.addLast(new Again(run));
                }
            }
            case RunResult.Failed f -> {
                if (!(stopRequested && f.aborted())) end("error", f.error());
                finished(run);
            }
        }
        heap();
    }

    private void finished(Run run) {
        boolean done;
        synchronized (lock) {
            liveTasks = Math.max(0, liveTasks - 1);
            if (run.timer != null) run.timer.busy = false;
            done = !ended && liveTasks == 0 && handlers.isEmpty() && timers.isEmpty() && waiters.isEmpty() && ready.isEmpty();
        }
        if (done) end("finished", null);
    }

    private void await(Run run, CompletableFuture<?> future) {
        synchronized (lock) {
            if (ended) {
                future.cancel(false);
                return;
            }
            waiting.add(future);
        }
        future.whenComplete((value, failure) -> {
            synchronized (lock) {
                waiting.remove(future);
                if (ended) return;
                ready.addLast(new Resume(run, value, failure));
            }
            schedule();
        });
    }

    /** Waits for the next CPU window: no slice is submitted before {@code until}. */
    private void pause(long until) {
        long delay = until - System.nanoTime();
        synchronized (lock) {
            if (ended) return;
            pausedUntil = Math.max(1, until);
        }
        var wake = scheduler.schedule(() -> {
            synchronized (lock) {
                pausedUntil = 0;
            }
            schedule();
        }, delay);
        if (wake == null) end("server_stopping", null);
    }

    /** Starts a new CPU window when a second has passed; keeps the last ten seconds for the dashboard. */
    private void roll(long now) {
        if (windowStart == 0) windowStart = now;
        if (now - windowStart < SECOND) return;
        long seconds = (now - windowStart) / SECOND;
        lastSecondNanos = windowUsed;
        lastSeconds[secondsSeen++ % lastSeconds.length] = windowUsed;
        for (long s = 1; s < Math.min(seconds, lastSeconds.length); s++) lastSeconds[secondsSeen++ % lastSeconds.length] = 0;
        long sum = 0;
        int n = Math.min(secondsSeen, lastSeconds.length);
        for (int k = 0; k < n; k++) sum += lastSeconds[k];
        averageNanos = n == 0 ? 0 : sum / n;
        windowStart += seconds * SECOND;
        windowUsed = 0;
        windowAlloc = 0;
    }

    private void heap() {
        var i = instance;
        if (i == null || ended()) return;
        long now = System.nanoTime();
        long since = now - lastHeapCheck;
        // With per-thread allocation counts: whenever the script allocated max(memory / 4, 2 × its last size) since the last
        // estimate (so the walk costs at most about as much as the allocation it follows), else every 10 seconds.
        boolean due = heapRequested || (ALLOC != null ? allocSinceHeap > heapThreshold || allocSinceHeap > 0 && since > 10 * SECOND : since > 10 * SECOND);
        heapRequested = false;
        if (!due) return;
        List<ScriptFunction> roots;
        synchronized (lock) {
            roots = new ArrayList<>();
            for (var h : handlers.values()) roots.add(h.function());
            for (var t : timers.values()) roots.add(t.function);
        }
        long estimate = i.heapEstimate(limits.memoryBytes(), roots);
        long end = System.nanoTime();
        windowUsed += end - now;
        lastHeapCheck = end;
        allocSinceHeap = 0;
        heapEstimate = estimate;
        heapThreshold = Math.max(limits.memoryBytes() / 4, 2 * Math.min(estimate, limits.memoryBytes()));
        if (estimate > limits.memoryBytes()) {
            end("error", new ScriptError(ScriptError.Kind.MEMORY_LIMIT, who.scriptName() + ": uses more than " + (limits.memoryBytes() >> 20) + " MB of memory",
                null, null));
        }
    }

    private boolean ended() {
        synchronized (lock) {
            return ended;
        }
    }

    // ---- budget (engine interrupt hook) --------------------------------------------------------------------------------

    @Override
    public Decision check() {
        if (stopRequested) {
            abortKind = ScriptError.Kind.KILLED;
            return Decision.ABORT;
        }
        if (blocking) return checkBlocking();
        long used = System.nanoTime() - jobStart - jobBlocked;
        long jobAlloc = ALLOC == null ? 0 : allocated() - jobAllocStart;
        if (ALLOC != null && allocSinceHeap + jobAlloc > heapThreshold) {
            heapRequested = true;
            return Decision.PAUSE;
        }
        if (ALLOC != null && windowAlloc + jobAlloc > limits.allocBytesPerSecond()) {
            abortMessage = "allocated more than " + (limits.allocBytesPerSecond() >> 20) + " MB within a second";
            abortKind = ScriptError.Kind.MEMORY_LIMIT;
            return Decision.ABORT;
        }
        var run = currentRun;
        if (run != null && run.cpu + used > limits.cpuMsPerResume() * 1_000_000L) {
            abortMessage = "ran for more than " + limits.cpuMsPerResume() + " ms without pausing (wait with cloud.sleep or react to events)";
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
        jobBlocked += nanos;
    }

    // ---- own thread (ABORT engines) -------------------------------------------------------------------------------------

    /** The script's thread: loads it and runs its tasks until it ends. */
    private void threadMain() {
        thread = Thread.currentThread();
        try {
            if (!stopRequested && load()) pump(null);
        } catch (InterruptedException e) {
            // stopped
        } catch (RuntimeException | StackOverflowError e) {
            ai.ezfox.cccloud.CloudLog.LOGGER.error("[cccloud] Cloud Script '{}' crashed the runtime", who.scriptName(), e);
            end("error", new ScriptError(ScriptError.Kind.ENGINE, who.scriptName() + ": internal error (" + e + ")", null, null));
        } finally {
            thread = null;
            synchronized (lock) {
                inSlice = false;
            }
            if (!ended()) end("finished", null);
            closeInstance();
            Thread.interrupted();
        }
    }

    /**
     * Runs the script's ready tasks on this thread until {@code until} completes (null: until the script ends). New
     * events, timers and completed futures wake it ({@link #schedule}).
     */
    private void pump(@Nullable CompletableFuture<?> until) throws InterruptedException {
        if (until != null) until.whenComplete((v, f) -> schedule());
        while (!stopRequested) {
            if (until != null && until.isDone()) return;
            events();
            Job job;
            synchronized (lock) {
                if (ended) return;
                job = ready.poll();
                if (job == null) {
                    if (until != null && until.isDone()) return;
                    if (pendingSnapshot == null) lock.wait(1000);
                    continue;
                }
            }
            runOnThread(job);
        }
    }

    /** Runs one task to its end (the engine cannot pause it) and books its CPU time. */
    private void runOnThread(Job job) {
        var i = instance;
        if (i == null || !(job instanceof Call c)) return;
        holdForShare();
        if (stopRequested) return;
        var outerRun = currentRun;
        long outerStart = jobStart;
        long outerBlocked = jobBlocked;
        var run = new Run(c.main() != null ? c.main() : i.call(c.function(), c.label(), c.arg() == null ? new Object[0] : new Object[]{c.arg()}), c.timer());
        currentRun = run;
        jobStart = System.nanoTime();
        jobBlocked = 0;
        RunResult result;
        try {
            result = i.run(run.task);
        } finally {
            long used = Math.max(0, System.nanoTime() - jobStart - jobBlocked);
            windowUsed += used;
            run.cpu += used;
            totalCpuNanos += used;
            currentRun = outerRun;
            jobStart = outerStart;
            jobBlocked = outerBlocked;
        }
        heapEstimate = i.heapEstimate(limits.memoryBytes(), List.of());
        switch (result) {
            case RunResult.Done d -> finished(run);
            case RunResult.Failed f -> {
                if (!(stopRequested && f.aborted())) end("error", f.error());
                finished(run);
            }
            default -> end("error", new ScriptError(ScriptError.Kind.ENGINE, who.scriptName() + ": the " + engine.language()
                + " engine paused a task it cannot continue", null, null));
        }
    }

    /**
     * Waits for a host call's future on the script's thread; meanwhile runs the script's other tasks (handlers, timers).
     * The waiting is not CPU time, and the task counts as having waited (its CPU per stretch starts again).
     */
    @Override
    public <T> T await(CompletableFuture<T> future) throws InterruptedException, java.util.concurrent.ExecutionException {
        if (!blocking) return Budget.super.await(future);
        var run = currentRun;
        long now = System.nanoTime();
        long used = Math.max(0, now - jobStart - jobBlocked);
        windowUsed += used;
        totalCpuNanos += used;
        if (run != null) run.cpu = 0;
        synchronized (lock) {
            if (ended) throw new InterruptedException();
            waiting.add(future);
        }
        try {
            if (nesting < MAX_NESTING) {
                nesting++;
                try {
                    pump(future);
                } finally {
                    nesting--;
                }
            } else {
                while (!future.isDone() && !stopRequested) {
                    synchronized (lock) {
                        if (ended) break;
                        if (!future.isDone()) lock.wait(1000);
                    }
                }
            }
            if (stopRequested || Thread.currentThread().isInterrupted()) throw new InterruptedException();
            holdForShare();
            if (stopRequested) throw new InterruptedException();
            return future.get();
        } finally {
            synchronized (lock) {
                waiting.remove(future);
            }
            currentRun = run;
            jobStart = System.nanoTime();
            jobBlocked = 0;
        }
    }

    /** Blocks the script's thread while its CPU share of this second is used up. */
    private void holdForShare() {
        long share = limits.cpuMsPerSecond() * 1_000_000L;
        while (!stopRequested) {
            long now = System.nanoTime();
            roll(now);
            if (windowUsed < share) return;
            long waitMs = Math.max(1, (windowStart + SECOND - now) / 1_000_000L);
            synchronized (lock) {
                if (ended) return;
                try {
                    lock.wait(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** {@link #check()} on the script's own thread: abort, or block here for the CPU share, then go on. */
    private Decision checkBlocking() {
        var run = currentRun;
        long now = System.nanoTime();
        long used = Math.max(0, now - jobStart - jobBlocked);
        if (run != null && run.cpu + used > limits.cpuMsPerResume() * 1_000_000L) {
            abortMessage = "ran for more than " + limits.cpuMsPerResume() + " ms without pausing (wait with cloud.sleep or react to events)";
            abortKind = ScriptError.Kind.CPU_LIMIT;
            return Decision.ABORT;
        }
        roll(now);
        if (windowUsed + used >= limits.cpuMsPerSecond() * 1_000_000L) {
            // the share is used up: book what ran, wait for the next second right here
            windowUsed += used;
            totalCpuNanos += used;
            if (run != null) run.cpu += used;
            jobStart = now;
            jobBlocked = 0;
            holdForShare();
            jobStart = System.nanoTime();
            jobBlocked = 0;
            if (stopRequested || Thread.currentThread().isInterrupted()) {
                if (stopRequested) abortKind = ScriptError.Kind.KILLED;
                return Decision.ABORT;
            }
        }
        return Decision.CONTINUE;
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
        } catch (RuntimeException | LinkageError ignored) {
            // not a HotSpot-like JVM: memory is only bounded by the heap estimate
        }
        return null;
    }

    /** Whether allocation per second can be limited on this JVM. */
    public static boolean allocationLimited() {
        return ALLOC != null;
    }

    // ---- events --------------------------------------------------------------------------------------------------------

    /** A new snapshot (any thread). */
    public void offer(WorldSnapshot next) {
        snapshot = next;
        synchronized (lock) {
            if (ended) return;
            if (handlers.isEmpty() && waiters.isEmpty() && !hasGameTimers()) {
                if (pendingSnapshot == null) eventBase = next;
                return;
            }
            pendingSnapshot = next;
        }
        schedule();
    }

    private boolean hasGameTimers() {
        for (var t : timers.values()) if (t.spec instanceof TimeSpec.Game) return true;
        return false;
    }

    /** Turns the newest snapshot into handler calls, finished waits and in-game timers (worker thread). */
    private void events() {
        WorldSnapshot next;
        WorldSnapshot base;
        synchronized (lock) {
            next = pendingSnapshot;
            if (next == null) return;
            pendingSnapshot = null;
            base = eventBase;
            eventBase = next;
        }
        var list = WorldEvents.diff(base, next);
        var done = new ArrayList<Map.Entry<CompletableFuture<@Nullable Object>, Object>>();
        int dropped = 0;
        synchronized (lock) {
            if (ended) return;
            for (var e : list) {
                for (var h : handlers.values()) {
                    var matched = h.filter().match(e, next);
                    if (matched == null) continue;
                    if (liveTasks >= limits.maxTasks()) {
                        dropped++;
                        continue;
                    }
                    liveTasks++;
                    ready.addLast(new Call(null, h.function(), "on " + h.filter().event(), matched.toMap(), null));
                }
                for (var it = waiters.iterator(); it.hasNext(); ) {
                    var w = it.next();
                    var matched = w.filter().match(e, next);
                    if (matched == null) continue;
                    it.remove();
                    done.add(Map.entry(w.future(), matched.toMap()));
                }
            }
            if (base != null) {
                for (var t : timers.values()) {
                    if (t.spec instanceof TimeSpec.Game g && crossed(base.gameTime(), next.gameTime(), g.time())) dropped += fireLocked(t) ? 0 : 1;
                }
            }
            if (dropped > 0) droppedEvents += dropped;
        }
        for (var d : done) d.getKey().complete(d.getValue());
        if (dropped > 0 && droppedEvents == dropped) log.system("warn", "events dropped: more than " + limits.maxTasks() + " handler calls at once");
    }

    /** Did the in-game time pass {@code target} going from {@code from} to {@code to} (0–23999, wrapping at midnight)? */
    static boolean crossed(long from, long to, long target) {
        from = Math.floorMod(from, 24000);
        to = Math.floorMod(to, 24000);
        if (from == to) return false;
        if (from < to) return target > from && target <= to;
        return target > from || target <= to;
    }

    // ---- ScriptHost ----------------------------------------------------------------------------------------------------

    @Override
    public WorldSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public CompletableFuture<@Nullable Object[]> act(WorldAction action) {
        if (!actions.tryAcquire()) return CompletableFuture.completedFuture(new Object[]{false, "rate_limited"});
        boolean changes = !(action instanceof WorldAction.ItemCount);
        return world.act(who, action).whenComplete((result, failure) -> {
            if (!changes) return;
            boolean ok = failure == null && result != null && result.length > 0 && Boolean.TRUE.equals(result[0]);
            String error = failure != null ? "internal_error" : ok || result == null || result.length < 2 ? null : String.valueOf(result[1]);
            if (!ok) log.script("warn", action.describe() + " refused: " + (error == null ? "failed" : error));
            for (var line : audit.record(action.describe() + (ok ? "" : " (refused: " + error + ")"))) world.audit(who, who.label() + ": " + line);
        });
    }

    @Override
    public int on(WorldEvents.Filter filter, ScriptFunction handler) {
        int id;
        synchronized (lock) {
            if (handlers.size() >= limits.maxHandlers()) throw new ApiError("too many event handlers (at most " + limits.maxHandlers() + ")");
            id = nextId++;
            handlers.put(id, new Handler(filter, handler));
            if (eventBase == null) eventBase = snapshot.seq() == 0 ? null : snapshot;
        }
        listener.changed(this);
        return id;
    }

    @Override
    public CompletableFuture<@Nullable Object> waitEvent(WorldEvents.Filter filter, double timeout) {
        var future = new CompletableFuture<@Nullable Object>();
        var waiter = new Waiter(filter, future);
        synchronized (lock) {
            if (waiters.size() >= limits.maxHandlers()) throw new ApiError("too many cloud.wait calls at once (at most " + limits.maxHandlers() + ")");
            waiters.add(waiter);
            if (eventBase == null) eventBase = snapshot.seq() == 0 ? null : snapshot;
        }
        if (timeout >= 0) {
            scheduler.schedule(() -> {
                synchronized (lock) {
                    waiters.remove(waiter);
                }
                future.complete(null);
            }, (long) (timeout * SECOND));
        }
        return future;
    }

    @Override
    public boolean off(int id) {
        Timer timer;
        synchronized (lock) {
            if (handlers.remove(id) != null) {
                timer = null;
            } else {
                timer = timers.remove(id);
                if (timer == null) return false;
            }
        }
        if (timer != null && timer.future != null) timer.future.cancel(false);
        listener.changed(this);
        return true;
    }

    @Override
    public CompletableFuture<@Nullable Object> sleep(double seconds) {
        var future = new CompletableFuture<@Nullable Object>();
        if (scheduler.schedule(() -> future.complete(null), (long) (Math.max(seconds, MIN_SLEEP) * SECOND)) == null) future.cancel(false);
        return future;
    }

    @Override
    public int every(double seconds, ScriptFunction handler) {
        var timer = timer("every " + format(seconds) + "s", handler, true, null);
        timer.future = scheduler.repeat(() -> fire(timer), (long) (seconds * SECOND));
        return timer.id;
    }

    @Override
    public int after(double seconds, ScriptFunction handler) {
        var timer = timer("after " + format(seconds) + "s", handler, false, null);
        timer.future = scheduler.schedule(() -> fire(timer), (long) (seconds * SECOND));
        return timer.id;
    }

    @Override
    public int at(TimeSpec time, ScriptFunction handler) {
        String label = switch (time) {
            case TimeSpec.Daily d -> String.format(java.util.Locale.ROOT, "at %02d:%02d", d.minute() / 60, d.minute() % 60);
            case TimeSpec.Game g -> "at gameTime " + g.time();
        };
        var timer = timer(label, handler, true, time);
        if (time instanceof TimeSpec.Daily daily) scheduleDaily(timer, daily);
        return timer.id;
    }

    private Timer timer(String label, ScriptFunction handler, boolean repeat, @Nullable TimeSpec spec) {
        Timer timer;
        synchronized (lock) {
            if (timers.size() >= limits.maxTimers()) throw new ApiError("too many timers (at most " + limits.maxTimers() + ")");
            timer = new Timer(nextId++, label, handler, repeat, spec);
            timers.put(timer.id, timer);
            if (spec instanceof TimeSpec.Game && eventBase == null) eventBase = snapshot.seq() == 0 ? null : snapshot;
        }
        listener.changed(this);
        return timer;
    }

    private void scheduleDaily(Timer timer, TimeSpec.Daily daily) {
        long delay = nextDaily(ZonedDateTime.now(zone.get()), daily) * 1_000_000L;
        timer.future = scheduler.schedule(() -> {
            fire(timer);
            synchronized (lock) {
                if (ended || !timers.containsKey(timer.id)) return;
            }
            scheduleDaily(timer, daily);
        }, delay);
    }

    /** Milliseconds from {@code now} to the next matching minute (at least one minute ahead when it is that minute now). */
    static long nextDaily(ZonedDateTime now, TimeSpec.Daily daily) {
        var candidate = now.withSecond(0).withNano(0).withHour(daily.minute() / 60).withMinute(daily.minute() % 60);
        for (int i = 0; i < 9; i++) {
            if (candidate.isAfter(now) && (daily.days() & (1 << (candidate.getDayOfWeek().getValue() - 1))) != 0) break;
            candidate = candidate.plusDays(1).withHour(daily.minute() / 60).withMinute(daily.minute() % 60);
        }
        return Math.max(1000, java.time.Duration.between(now, candidate).toMillis());
    }

    /** A timer fired (timer thread, or the worker for in-game timers). */
    private void fire(Timer timer) {
        boolean queued;
        synchronized (lock) {
            queued = fireLocked(timer);
        }
        if (queued) schedule();
    }

    private boolean fireLocked(Timer timer) {
        if (ended || !timers.containsKey(timer.id)) return true;
        if (timer.busy) return true; // still running from last time: not twice
        if (liveTasks >= limits.maxTasks()) return false;
        if (!timer.repeat) timers.remove(timer.id);
        timer.busy = true;
        liveTasks++;
        ready.addLast(new Call(null, timer.function, timer.label, null, timer));
        return true;
    }

    @Override
    public boolean notify(ScriptWorld.Channel channel, String text, String level, @Nullable String title) {
        if (!notifications.tryAcquire()) {
            log.script("warn", "notification dropped (at most " + limits.notifyBurst() + " in a row, then one per " + Math.round(1 / limits.notifyPerSecond()) + " s): " + text);
            return false;
        }
        log.script(level.equals("info") ? "info" : "warn", "→ " + channel.name().toLowerCase(java.util.Locale.ROOT) + " (" + level + "): " + text);
        return world.notify(who, channel, text, level, title);
    }

    @Override
    public @Nullable Object storeGet(String key) {
        return store.get(key);
    }

    @Override
    public boolean storeSet(String key, @Nullable Object value) {
        return store.set(key, value);
    }

    @Override
    public List<String> storeKeys() {
        return store.keys();
    }

    @Override
    public boolean log(String level, String text) {
        return log.script(level, text);
    }

    @Override
    public double clock() {
        return (System.nanoTime() - startNanos) / 1e9;
    }

    @Override
    public Map<String, Object> info() {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", who.scriptId());
        map.put("name", who.scriptName());
        map.put("author", who.authorName());
        map.put("version", (long) version);
        map.put("language", engine.language());
        return map;
    }

    // ---- status --------------------------------------------------------------------------------------------------------

    public ScriptIdentity identity() {
        return who;
    }

    public int version() {
        return version;
    }

    public long startedAt() {
        return startedAt;
    }

    public Status status() {
        synchronized (lock) {
            return status;
        }
    }

    public boolean isEnded() {
        return ended();
    }

    public @Nullable String endReason() {
        synchronized (lock) {
            return endReason;
        }
    }

    public @Nullable ScriptError endError() {
        synchronized (lock) {
            return endError;
        }
    }

    public int handlerCount() {
        synchronized (lock) {
            return handlers.size() + waiters.size();
        }
    }

    public int timerCount() {
        synchronized (lock) {
            return timers.size();
        }
    }

    /** CPU ms of the last full second. */
    public double cpuLastSecond() {
        return lastSecondNanos / 1e6;
    }

    /** Average CPU ms per second over the last ten seconds. */
    public double cpuAverage() {
        return averageNanos / 1e6;
    }

    public long cpuTotalNanos() {
        return totalCpuNanos;
    }

    public long heapEstimate() {
        return heapEstimate;
    }

    private static String format(double seconds) {
        return seconds == Math.rint(seconds) ? Long.toString((long) seconds) : Double.toString(seconds);
    }
}
