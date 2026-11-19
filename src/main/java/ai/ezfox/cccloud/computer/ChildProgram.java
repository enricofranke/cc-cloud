package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.HostModule;
import ai.ezfox.cccloud.code.engine.Budget;
import ai.ezfox.cccloud.code.engine.EngineInstance;
import ai.ezfox.cccloud.code.engine.RunResult;
import ai.ezfox.cccloud.code.engine.ScriptEngine;
import ai.ezfox.cccloud.code.engine.ScriptError;
import ai.ezfox.cccloud.code.runtime.BlockingRunner;
import ai.ezfox.cccloud.code.runtime.CpuBudget;
import ai.ezfox.cccloud.code.runtime.ScriptScheduler;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * A WebAssembly program started from the shell of a Cloud Computer ({@code foo} or {@code foo.wasm}): a second engine
 * instance with the computer's own modules on a thread of its own ({@link BlockingRunner}) while the Lua shell waits.
 * While it runs, the computer's events go to it ({@code __native.pull} is its own queue); {@code terminate} (Ctrl+T),
 * shutdown and reboot end it at once. Its CPU share and longest run between waits are the computer's limits (the
 * shell uses no CPU meanwhile), its memory cap what the computer's Lua does not use. At most one per computer.
 */
final class ChildProgram implements Budget {
    /** Told once when the program ended: null = it finished, else the error (or {@code Terminated}). */
    interface Done {
        void ended(ChildProgram child, @Nullable String error);
    }

    final int id;
    private final String name;
    private final ScriptEngine engine;
    private final String source;
    private final List<HostModule> modules;
    private final ComputerLimits limits;
    private final long memoryCap;
    private final Done done;
    private final Object owner;
    private final long startNanos = System.nanoTime();
    private final BlockingRunner runner;

    private final Object lock = new Object();
    // ---- guarded by lock ----
    private final ArrayDeque<Object[]> events = new ArrayDeque<>();
    private @Nullable CompletableFuture<@Nullable Object> waiter;
    private boolean ended;

    private volatile @Nullable EngineInstance instance;
    private volatile boolean stopRequested;
    private volatile String stopMessage = "Terminated";
    private volatile String abortMessage = "Terminated";
    private volatile ScriptError.Kind abortKind = ScriptError.Kind.KILLED;

    /**
     * @param modules the computer's modules; {@code __native.pull} is replaced by this program's own queue and
     *                {@code __native.args} added (the program's name and arguments)
     */
    ChildProgram(int id, String name, ScriptEngine engine, String source, List<Object> args, List<HostModule> modules, ScriptScheduler scheduler,
                 ComputerLimits limits, long memoryCap, Object owner, Done done) {
        this.owner = owner;
        this.id = id;
        this.name = name;
        this.engine = engine;
        this.source = source;
        this.limits = limits;
        this.memoryCap = memoryCap;
        this.done = done;
        var budget = new CpuBudget(scheduler.clock(), limits.cpuMsPerSecond(), limits.cpuMsPerResume(), CpuBudget.UNLIMITED, CpuBudget.UNLIMITED);
        this.runner = new BlockingRunner(scheduler, budget, () -> stopRequested, lock, (used, seconds) -> {
        });
        this.modules = modules.stream().map(m -> m.name().equals("__native") ? natives(m, List.copyOf(args)) : m).toList();
    }

    private HostModule natives(HostModule parent, List<Object> args) {
        var functions = new LinkedHashMap<>(parent.functions());
        functions.put("pull", a -> pull());
        functions.put("args", a -> args);
        functions.remove("runWasm"); // no programs inside programs
        return new HostModule(parent.name(), functions, parent.fields());
    }

    /** Starts the program's thread; false (and {@link Done} told) if the server has no thread left. */
    boolean start() {
        var refusal = runner.start(this::main, owner); // the computer owner's share of the threads
        if (refusal == null) return true;
        finish(BlockingRunner.refusalText(refusal, "WebAssembly", "programs"));
        return false;
    }

    private void main() {
        String error = null;
        try {
            if (stopRequested) return;
            var outer = runner.enter();
            RunResult result;
            try {
                var i = engine.createProgram(name, modules, this, this::clock);
                instance = i;
                result = i.run(i.load(source));
            } catch (ScriptError e) {
                result = new RunResult.Failed(e, false);
            } finally {
                runner.leave(outer);
            }
            if (result instanceof RunResult.Failed f) error = f.error().getMessage();
        } catch (RuntimeException | StackOverflowError e) {
            ai.ezfox.cccloud.CloudLog.LOGGER.error("[cccloud] WebAssembly program '{}' crashed the runtime", name, e);
            error = name + ": internal error";
        } finally {
            var i = instance;
            instance = null;
            if (i != null) i.close();
            finish(stopRequested ? stopMessage : error);
        }
    }

    private void finish(@Nullable String error) {
        synchronized (lock) {
            if (ended) return;
            ended = true;
            events.clear();
            if (waiter != null) waiter.cancel(false);
            waiter = null;
        }
        try {
            done.ended(this, error);
        } catch (RuntimeException e) {
            ai.ezfox.cccloud.CloudLog.LOGGER.warn("[cccloud] Cloud Computer: WebAssembly program end failed", e);
        }
    }

    /** Ends it at once (Ctrl+T: {@code Terminated}; shutdown, reboot). Any thread. */
    void stop(String message) {
        stopMessage = message;
        abortMessage = message;
        abortKind = ScriptError.Kind.KILLED;
        stopRequested = true;
        var i = instance;
        if (i != null) i.interrupt();
        runner.interrupt();
    }

    double clock() {
        return (System.nanoTime() - startNanos) / 1e9;
    }

    // ---- events ------------------------------------------------------------------------------------------------------

    /** An event for the program (any thread); false once it has ended (the computer's shell gets it then). */
    boolean queue(Object[] event) {
        CompletableFuture<@Nullable Object> wake;
        synchronized (lock) {
            if (ended) return false;
            wake = waiter;
            if (wake == null) {
                if (events.size() < limits.maxEvents()) events.add(event);
                return true;
            }
            waiter = null;
        }
        wake.complete(event);
        return true;
    }

    private Object pull() {
        synchronized (lock) {
            var event = events.poll();
            if (event != null) return event;
            if (waiter != null) throw new ApiError("already waiting for an event");
            var future = new CompletableFuture<@Nullable Object>();
            waiter = future;
            return future;
        }
    }

    // ---- budget ------------------------------------------------------------------------------------------------------

    @Override
    public Decision check() {
        return switch (runner.check()) {
            case CONTINUE -> Decision.CONTINUE;
            case STOPPED -> Decision.ABORT;
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
        return memoryCap;
    }

    @Override
    public void blocked(long nanos) {
        runner.blocked(nanos);
    }

    @Override
    public <T> T await(CompletableFuture<T> future) throws InterruptedException, java.util.concurrent.ExecutionException {
        return runner.await(future, null);
    }
}
