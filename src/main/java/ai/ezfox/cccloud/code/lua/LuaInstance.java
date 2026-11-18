package ai.ezfox.cccloud.code.lua;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.ApiSpec;
import ai.ezfox.cccloud.code.api.CloudApi;
import ai.ezfox.cccloud.code.api.ScriptFunction;
import ai.ezfox.cccloud.code.api.ScriptHost;
import ai.ezfox.cccloud.code.engine.Budget;
import ai.ezfox.cccloud.code.engine.EngineInstance;
import ai.ezfox.cccloud.code.engine.RunResult;
import ai.ezfox.cccloud.code.engine.ScriptError;
import ai.ezfox.cccloud.code.engine.Task;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.DoubleSupplier;
import ai.ezfox.cccloud.code.api.HostModule;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.UnwindThrowable;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.interrupt.InterruptAction;

/**
 * One script in its own Cobalt {@link LuaState}. Every task is a Lua coroutine; a {@code cloud.*} call that waits
 * yields that coroutine with its future, a used-up CPU share suspends it through Cobalt's interrupt hook
 * ({@link InterruptAction#SUSPEND}), and an abort (time or memory limit) raises an error in the hook again and again
 * until it reaches the top, so {@code pcall} cannot swallow it.
 */
final class LuaInstance implements EngineInstance {
    /** Longest wait of an action called from inside a coroutine, where the task cannot pause. */
    static final long BLOCKING_WAIT_MS = 5_000;

    private final String chunk;
    private final @Nullable ScriptHost host;
    private final Budget budget;
    private final LuaState state;
    private final Set<LuaTask> live = Collections.newSetFromMap(new IdentityHashMap<>());

    private volatile boolean running;
    private volatile boolean aborting;
    private volatile String abortMessage = "";
    private @Nullable LuaTask current;
    private @Nullable CompletableFuture<?> pending;
    private @Nullable LuaError resumeFailure;
    private boolean closed;

    static final class LuaTask implements Task {
        final LuaThread thread;
        final String label;
        final Varargs args;
        boolean started;
        /** The coroutine that was running when the task was suspended (the task's own, or one it resumed). */
        @Nullable LuaThread suspended;

        LuaTask(LuaThread thread, String label, Varargs args) {
            this.thread = thread;
            this.label = label;
            this.args = args;
        }

        @Override
        public String label() {
            return label;
        }
    }

    LuaInstance(String name, ScriptHost host, Budget budget, ApiSpec spec, CloudApi api, ZoneId zone) {
        this.chunk = name;
        this.host = host;
        this.budget = budget;
        this.state = LuaState.builder().interruptHandler(this::interrupted).build();
        try {
            LuaSandbox.install(state, spec, zone, host::clock);
            LuaBinding.install(state, spec, api, this);
            var prelude = LoadState.load(state, stream(LuaSandbox.PRELUDE), "=cloud", state.globals());
            LuaThread.runMain(state, prelude);
        } catch (LuaError | CompileException e) {
            throw new IllegalStateException("cannot set up the Lua sandbox: " + e.getMessage(), e);
        }
    }

    /** A computer program: the same sandbox, the modules as globals instead of {@code cloud}, strings as bytes. */
    LuaInstance(String name, List<HostModule> modules, Budget budget, ApiSpec spec, ZoneId zone, DoubleSupplier clock) {
        this.chunk = name;
        this.host = null;
        this.budget = budget;
        this.state = LuaState.builder().interruptHandler(this::interrupted).build();
        try {
            LuaSandbox.install(state, spec, zone, clock);
            ModuleBinding.install(state, modules, this);
        } catch (LuaError e) {
            throw new IllegalStateException("cannot set up the Lua sandbox: " + e.getMessage(), e);
        }
    }

    ScriptHost host() {
        if (host == null) throw new IllegalStateException("a program has no cloud host");
        return host;
    }

    LuaState state() {
        return state;
    }

    // ---- loading and tasks -------------------------------------------------------------------------------------------

    @Override
    public Task load(String source) throws ScriptError {
        LuaFunction main;
        try {
            main = LoadState.load(state, stream(source), "=" + chunk, state.globals());
        } catch (CompileException e) {
            String message = e.getMessage() == null ? "syntax error" : e.getMessage();
            throw new ScriptError(ScriptError.Kind.SYNTAX, message, ScriptError.lineOf(message, chunk), null);
        } catch (LuaError e) {
            throw error(e);
        }
        return task(main, "main", Constants.NONE);
    }

    @Override
    public Task call(ScriptFunction function, String label, @Nullable Object... args) {
        if (!(function instanceof LuaConvert.Function f)) throw new IllegalArgumentException("not a Lua function");
        var values = new LuaValue[args.length];
        var charset = host == null ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8;
        for (int i = 0; i < args.length; i++) values[i] = LuaConvert.toLua(args[i], charset);
        return task(f.function(), label, ValueFactory.varargsOf(values));
    }

    private LuaTask task(LuaFunction function, String label, Varargs args) {
        var task = new LuaTask(new LuaThread(state, function), label, args);
        live.add(task);
        return task;
    }

    @Override
    public RunResult run(Task task) {
        var t = (LuaTask) task;
        if (t.suspended != null) {
            var thread = t.suspended;
            t.suspended = null;
            return go(t, thread, Constants.NONE);
        }
        if (!t.started) {
            t.started = true;
            return go(t, t.thread, t.args);
        }
        return go(t, t.thread, Constants.NONE); // after a bare coroutine.yield()
    }

    @Override
    public RunResult resume(Task task, @Nullable Object value, @Nullable Throwable failure) {
        var t = (LuaTask) task;
        if (failure != null) {
            var cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            resumeFailure = new LuaError(cause instanceof ApiError || cause instanceof TimeoutException ? cause.getMessage() : "internal error: " + cause);
            return go(t, t.thread, Constants.NONE);
        }
        return go(t, t.thread, LuaConvert.results(value, true, host == null ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8));
    }

    private RunResult go(LuaTask task, LuaThread thread, Varargs args) {
        if (closed) return new RunResult.Failed(new ScriptError(ScriptError.Kind.KILLED, "stopped", null, null), true);
        current = task;
        pending = null;
        running = true;
        try {
            Varargs result = LuaThread.run(thread, args);
            if (result == null) {
                task.suspended = state.getCurrentThread();
                return new RunResult.Paused();
            }
            if (task.thread.getStatus() == LuaThread.Status.DEAD) {
                live.remove(task);
                return new RunResult.Done();
            }
            var future = pending;
            pending = null;
            return future != null ? new RunResult.Waiting(future) : new RunResult.Yielded();
        } catch (LuaError e) {
            live.remove(task);
            return new RunResult.Failed(error(e), aborting);
        } catch (VirtualMachineError e) {
            // OutOfMemoryError/StackOverflowError in the VM itself (Cobalt wraps most of them into LuaErrors)
            live.remove(task);
            aborting = true;
            boolean memory = e instanceof OutOfMemoryError;
            return new RunResult.Failed(new ScriptError(memory ? ScriptError.Kind.MEMORY_LIMIT : ScriptError.Kind.STACK_OVERFLOW,
                chunk + ": " + (memory ? "out of memory" : "stack overflow"), null, null), true);
        } finally {
            running = false;
            current = null;
            resumeFailure = null;
        }
    }

    // ---- waiting inside a cloud.* call -------------------------------------------------------------------------------

    /** A call returned a future: pause the task until it completes (or, inside a coroutine, wait for an action). */
    Varargs await(LuaState state, ApiSpec.Function function, CompletableFuture<?> future) throws LuaError, UnwindThrowable {
        return await(state, "cloud." + function.name(), !function.returns().isEmpty(), function.kind() == ApiSpec.Kind.AWAIT, future, StandardCharsets.UTF_8);
    }

    /**
     * A call named {@code name} returned a future: on the task's own coroutine the task waits for it; inside another
     * coroutine it blocks for at most {@link #BLOCKING_WAIT_MS} when {@code mayBlock}, else it is an error.
     */
    Varargs await(LuaState state, String name, boolean declared, boolean mayBlock, CompletableFuture<?> future, Charset charset) throws LuaError, UnwindThrowable {
        if (future.isDone()) return now(future, declared, charset);
        var task = current;
        if (task != null && state.getCurrentThread() == task.thread) {
            pending = future;
            return LuaThread.yield(state, Constants.NONE);
        }
        if (mayBlock) {
            long start = System.nanoTime();
            try {
                future.get(BLOCKING_WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new LuaError(name + " timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LuaError(name + " was interrupted");
            } catch (ExecutionException ignored) {
                // reported by now()
            } finally {
                budget.blocked(System.nanoTime() - start);
            }
            return now(future, declared, charset);
        }
        future.cancel(false);
        throw new LuaError(name + " cannot pause inside a coroutine: call it from the script itself or from a handler");
    }

    private static Varargs now(CompletableFuture<?> future, boolean declared, Charset charset) throws LuaError {
        try {
            return LuaConvert.results(future.getNow(null), declared, charset);
        } catch (CompletionException e) {
            var cause = e.getCause() == null ? e : e.getCause();
            throw new LuaError(cause instanceof ApiError ? cause.getMessage() : "internal error: " + cause);
        } catch (java.util.concurrent.CancellationException e) {
            throw new LuaError("cancelled");
        }
    }

    /** The value a paused call returns when its task continues (or the error it raises). */
    Varargs resumed(Varargs value) throws LuaError {
        var failure = resumeFailure;
        if (failure != null) {
            resumeFailure = null;
            throw failure;
        }
        return value;
    }

    // ---- limits --------------------------------------------------------------------------------------------------------

    private InterruptAction interrupted() throws LuaError {
        if (aborting) {
            state.interrupt(); // again at the next instruction, until the error leaves every pcall
            throw new LuaError(abortMessage);
        }
        if (!running) return InterruptAction.CONTINUE;
        return switch (budget.check()) {
            case CONTINUE -> InterruptAction.CONTINUE;
            case PAUSE -> InterruptAction.SUSPEND;
            case ABORT -> {
                aborting = true;
                abortMessage = budget.abortMessage();
                state.interrupt();
                throw new LuaError(abortMessage);
            }
        };
    }

    @Override
    public void interrupt() {
        if (running) state.interrupt();
    }

    @Override
    public long heapEstimate(long limit, java.util.Collection<ScriptFunction> handlers) {
        var roots = new ArrayList<Object>(live.size() + handlers.size());
        for (var task : live) roots.add(task.thread);
        roots.addAll(handlers);
        return LuaHeap.estimate(state, roots, limit);
    }

    @Override
    public void close() {
        closed = true;
        live.clear();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    private ScriptError error(LuaError e) {
        LuaValue value = e.getValue();
        String message = value instanceof org.squiddev.cobalt.LuaString s ? LuaConvert.text(s)
            : value.type() == Constants.TNUMBER ? value.toString()
            : value.isNil() ? "nil" : "(error object is a " + value.typeName() + " value)";
        if (message.length() > 1000) message = message.substring(0, 1000) + "…";
        var kind = aborting ? budget.abortKind() : message.contains("stack overflow") ? ScriptError.Kind.STACK_OVERFLOW : ScriptError.Kind.RUNTIME;
        return new ScriptError(kind, message, ScriptError.lineOf(message, chunk), e.getMessage());
    }

    private ByteArrayInputStream stream(String source) {
        return new ByteArrayInputStream(source.getBytes(host == null ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8));
    }
}
