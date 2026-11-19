package ai.ezfox.cccloud.code.wasm;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.ScriptFunction;
import ai.ezfox.cccloud.code.engine.Budget;
import ai.ezfox.cccloud.code.engine.EngineInstance;
import ai.ezfox.cccloud.code.engine.RunResult;
import ai.ezfox.cccloud.code.engine.ScriptError;
import ai.ezfox.cccloud.code.engine.Task;
import ai.ezfox.cccloud.web.TokenBucket;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jspecify.annotations.Nullable;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.HostFunction;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.Memory;
import run.endive.runtime.TrapException;
import run.endive.runtime.WasmInterruptedException;
import run.endive.wasm.WasmEngineException;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.MemoryLimits;
import run.endive.wasm.types.ValType;

/**
 * One WebAssembly script: an Endive interpreter instance with a hard memory cap, the host imports, and a hook that
 * consults the {@link Budget} every {@link #CHECK_EVERY} instructions (CPU share, CPU per stretch, stop). It cannot
 * suspend: tasks run to their end on the calling thread, host calls that wait (sleep, events, actions) block in
 * {@link Budget#await}, and the runtime may run handlers inside such a wait (re-entering the module on the same
 * thread).
 *
 * <h2>Imports</h2>
 * <ul>
 *   <li>{@code cccloud.call(name, nameLen, args, argsLen) -> i32}: calls a host function with a JSON array of arguments;
 *   returns the length of the JSON array of results, which waits for {@code cccloud.take}. A wrong call (bad argument,
 *   unknown function, too large) ends the script with that error (a negative result, {@code -1 - length} of an error
 *   message, is reserved for errors a script may handle).</li>
 *   <li>{@code cccloud.take(ptr)}: copies the waiting bytes to {@code ptr}.</li>
 *   <li>{@code env.abort(msg, file, line, column)}, {@code env.trace(msg, n, a0..a4)}, {@code env.seed() -> f64}: what
 *   AssemblyScript's runtime needs.</li>
 * </ul>
 * A handler runs as the export {@code cccloud_handle(id, argLength)} (its argument waits for {@code take}).
 */
final class WasmInstance implements EngineInstance {
    static final int CHECK_EVERY = 4096;
    static final int MAX_NAME = 256;
    static final String HANDLE = "cccloud_handle";
    private static final FunctionType CALL = FunctionType.of(List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32), List.of(ValType.I32));
    private static final FunctionType TAKE = FunctionType.of(List.of(ValType.I32), List.of());
    private static final FunctionType ABORT = FunctionType.of(List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32), List.of());
    private static final FunctionType TRACE = FunctionType.of(List.of(ValType.I32, ValType.I32, ValType.F64, ValType.F64, ValType.F64, ValType.F64, ValType.F64), List.of());
    private static final FunctionType SEED = FunctionType.of(List.of(), List.of(ValType.F64));

    /** The imports a module may have, with their types (everything else is refused by {@link WasmCheck}). */
    static final Map<String, Map<String, FunctionType>> IMPORTS = Map.of(
        "cccloud", Map.of("call", CALL, "take", TAKE),
        "env", Map.of("abort", ABORT, "trace", TRACE, "seed", SEED));

    private final String name;
    private final WasmHostApi api;
    private final Budget budget;
    private final long memoryCap;
    private final int maxModule;
    private final int callBytes;
    private final TokenBucket calls;
    private final int callsPerSecond;
    private @Nullable Instance instance;
    private @Nullable Memory memory;
    private int maxPages;
    private byte @Nullable [] pending;
    private int countdown = CHECK_EVERY;
    private boolean growRefused;
    private volatile boolean poked;

    /** Stops the running module from inside a host call or the instruction hook: not catchable by the script. */
    static final class Stop extends RuntimeException {
        final ScriptError.Kind kind;

        Stop(ScriptError.Kind kind, String message) {
            super(message, null, false, false);
            this.kind = kind;
        }
    }

    /** An error the script raised ({@code abort}) or a failed host call: ends the task with this error. */
    static final class Failure extends RuntimeException {
        final ScriptError error;

        Failure(ScriptError error) {
            super(error.getMessage(), null, false, false);
            this.error = error;
        }
    }

    private record WasmTask(String label, Runnable body) implements Task {
    }

    /** A handler of this script: an index into the module's handler table. */
    record Handler(WasmInstance owner, int id) implements ScriptFunction {
    }

    WasmInstance(String name, WasmHostApi api, Budget budget, int maxModule, int callBytes, int callsPerSecond) {
        this.name = name;
        this.api = api;
        this.budget = budget;
        this.memoryCap = budget.memoryBytes();
        this.maxModule = maxModule;
        this.callBytes = callBytes;
        this.callsPerSecond = callsPerSecond;
        this.calls = new TokenBucket(callsPerSecond, callsPerSecond);
    }

    // ---- loading -------------------------------------------------------------------------------------------------------

    @Override
    public Task load(String source) throws ScriptError {
        WasmSource stored;
        try {
            stored = WasmSource.decode(source, maxModule, Integer.MAX_VALUE);
        } catch (WasmSource.Invalid e) {
            throw new ScriptError(ScriptError.Kind.SYNTAX, name + ": " + e.getMessage(), null, null);
        }
        WasmCheck.Checked checked;
        try {
            checked = WasmCheck.check(stored.module(), IMPORTS, memoryCap);
        } catch (WasmCheck.Refused e) {
            throw new ScriptError(ScriptError.Kind.SYNTAX, name + ": " + e.getMessage(), null, null);
        }
        maxPages = checked.maxPages();
        var imports = ImportValues.builder().addFunction(
            new HostFunction("cccloud", "call", CALL, (inst, args) -> new long[]{call((int) args[0], (int) args[1], (int) args[2], (int) args[3])}),
            new HostFunction("cccloud", "take", TAKE, (inst, args) -> {
                take((int) args[0]);
                return null;
            }),
            new HostFunction("env", "abort", ABORT, (inst, args) -> {
                throw abort((int) args[0], (int) args[1], (int) args[2], (int) args[3]);
            }),
            new HostFunction("env", "trace", TRACE, (inst, args) -> {
                trace((int) args[0], (int) args[1], args);
                return null;
            }),
            new HostFunction("env", "seed", SEED, (inst, args) -> new long[]{Double.doubleToRawLongBits(Math.random() * 0x1p32)})).build();
        try {
            var built = Instance.builder(checked.module())
                .withImportValues(imports)
                .withMemoryLimits(new MemoryLimits(checked.memoryPages(), Math.max(checked.memoryPages(), checked.maxPages())))
                .withMemoryFactory(ByteArrayMemory::new)
                .withUnsafeExecutionListener(this::tick)
                .withStart(false)
                .build();
            instance = built;
            memory = built.memory();
        } catch (Stop e) {
            throw new ScriptError(e.kind, name + ": " + e.getMessage(), null, null);
        } catch (RuntimeException | StackOverflowError e) {
            throw new ScriptError(ScriptError.Kind.SYNTAX, name + ": cannot load the module: " + WasmCheck.message(e), null, null);
        }
        var start = checked.module().startSection();
        boolean hasMain = checked.hasMain();
        return new WasmTask("main", () -> {
            var i = live();
            if (start.isPresent()) i.getMachine().call((int) start.get().startIndex(), new long[0]);
            if (hasMain) i.export(WasmCheck.MAIN).apply();
        });
    }

    private Instance live() {
        var i = instance;
        if (i == null) throw new Stop(ScriptError.Kind.KILLED, "stopped");
        return i;
    }

    // ---- tasks ---------------------------------------------------------------------------------------------------------

    @Override
    public Task call(ScriptFunction function, String label, @Nullable Object... args) {
        if (!(function instanceof Handler h) || h.owner() != this) throw new IllegalArgumentException("not a handler of this script");
        @Nullable Object arg = args.length == 0 ? null : args[0];
        return new WasmTask(label, () -> {
            var i = live();
            int length = -1;
            if (arg != null) {
                byte[] json = WasmJson.write(arg).getBytes(api.charset());
                if (json.length > callBytes) throw new Failure(error(ScriptError.Kind.RUNTIME, "the event for " + label + " is larger than " + callBytes / 1024 + " KB"));
                pending = json;
                length = json.length;
            }
            try {
                i.export(HANDLE).apply(h.id(), length);
            } catch (run.endive.wasm.InvalidException | IllegalArgumentException e) {
                throw new Failure(error(ScriptError.Kind.RUNTIME, "the module does not export " + HANDLE + " (re-export it from the SDK)"));
            }
        });
    }

    @Override
    public RunResult run(Task task) {
        if (!(task instanceof WasmTask t)) throw new IllegalArgumentException("not a task of this instance");
        try {
            t.body().run();
            return new RunResult.Done();
        } catch (Stop e) {
            return new RunResult.Failed(error(e.kind, e.getMessage()), true);
        } catch (WasmInterruptedException e) {
            Thread.interrupted();
            return new RunResult.Failed(error(budget.abortKind(), budget.abortMessage()), true);
        } catch (Failure e) {
            return new RunResult.Failed(e.error, false);
        } catch (StackOverflowError e) {
            return new RunResult.Failed(error(ScriptError.Kind.STACK_OVERFLOW, "stack overflow (recursion too deep)"), false);
        } catch (TrapException e) {
            if (memoryFull()) return new RunResult.Failed(memoryLimit(), true);
            return new RunResult.Failed(error(ScriptError.Kind.RUNTIME, "trap: " + WasmCheck.message(e)), false);
        } catch (WasmEngineException e) {
            if (e.getCause() instanceof StackOverflowError || String.valueOf(e.getMessage()).contains("call stack exhausted")) {
                return new RunResult.Failed(error(ScriptError.Kind.STACK_OVERFLOW, "stack overflow (recursion too deep)"), false);
            }
            if (memoryFull()) return new RunResult.Failed(memoryLimit(), true);
            return new RunResult.Failed(error(ScriptError.Kind.RUNTIME, WasmCheck.message(e)), false);
        } catch (RuntimeException e) {
            if (memoryFull()) return new RunResult.Failed(memoryLimit(), true);
            return new RunResult.Failed(new ScriptError(ScriptError.Kind.ENGINE, name + ": internal error (" + e + ")", null, null), false);
        }
    }

    private void grows(long pages) {
        var m = memory;
        long delta = pages & 0xFFFF_FFFFL; // memory.grow takes an unsigned page count
        if (m != null && delta > 0 && m.pages() + delta > maxPages) growRefused = true;
    }

    /** The module asked for more memory than its cap allows (it then traps or aborts: a memory limit, not its bug). */
    private boolean memoryFull() {
        return growRefused;
    }

    private ScriptError memoryLimit() {
        return error(ScriptError.Kind.MEMORY_LIMIT, "uses more than " + (memoryCap >> 20) + " MB of memory");
    }

    private ScriptError error(ScriptError.Kind kind, String message) {
        return new ScriptError(kind, name + ": " + message, null, null);
    }

    @Override
    public void interrupt() {
        poked = true;
    }

    /**
     * The instruction hook: notes a {@code memory.grow} past the cap (so the trap that follows is a memory limit), and every
     * few thousand instructions (or soon after {@link #interrupt}) asks the budget.
     */
    private void tick(run.endive.wasm.types.Instruction instruction, run.endive.runtime.MStack stack) {
        if (instruction.opcode() == run.endive.wasm.types.OpCode.MEMORY_GROW) grows(stack.peek());
        if (--countdown > 0 && !poked) return;
        countdown = CHECK_EVERY;
        poked = false;
        consult();
    }

    private void consult() {
        if (budget.check() == Budget.Decision.ABORT) throw new Stop(budget.abortKind(), budget.abortMessage());
        if (Thread.currentThread().isInterrupted()) throw new Stop(budget.abortKind(), budget.abortMessage());
    }

    @Override
    public long heapEstimate(long limit, Collection<ScriptFunction> handlers) {
        var m = memory;
        return m == null ? 0 : (long) m.pages() * Memory.PAGE_SIZE;
    }

    @Override
    public void close() {
        instance = null;
        memory = null;
        pending = null;
    }

    // ---- host imports --------------------------------------------------------------------------------------------------

    private int call(int namePtr, int nameLen, int argsPtr, int argsLen) {
        consult();
        if (!calls.tryAcquire()) {
            throw new Failure(error(ScriptError.Kind.CPU_LIMIT, "made more than " + callsPerSecond + " host calls within a second"));
        }
        pending = null;
        String function = "?";
        try {
            if (nameLen < 0 || nameLen > MAX_NAME) throw refuse("bad function name");
            function = new String(bytes(namePtr, nameLen), StandardCharsets.UTF_8);
            if (argsLen < 0 || argsLen > callBytes) throw refuse("arguments of " + function + " are larger than " + callBytes / 1024 + " KB");
            var f = api.find(function);
            if (f == null) throw refuse("unknown function " + function);
            String json = new String(bytes(argsPtr, argsLen), api.charset());
            Object parsed = WasmJson.parse(json, id -> new Handler(this, id));
            if (!(parsed instanceof List<?> list)) throw refuse("arguments of " + function + " must be a JSON array");
            @SuppressWarnings("unchecked") var args = (List<@Nullable Object>) list;
            Object result = f.call(args);
            if (result instanceof CompletableFuture<?> future) result = await(future);
            byte[] out = WasmJson.results(result, f.returnsSomething()).getBytes(api.charset());
            if (out.length > callBytes) throw refuse("the result of " + function + " is larger than " + callBytes / 1024 + " KB");
            pending = out;
            return out.length;
        } catch (ApiError e) {
            // a wrong call ends the script with that error (AssemblyScript cannot catch it anyway); no line: it is the host's
            throw new Failure(error(ScriptError.Kind.RUNTIME, e.getMessage()));
        } catch (Stop | Failure e) {
            throw e;
        } catch (RuntimeException e) {
            ai.ezfox.cccloud.CloudLog.LOGGER.warn("[cccloud] WebAssembly script '{}': host call {} failed", name, function, e);
            throw refuse("internal error in " + function);
        }
    }

    private @Nullable Object await(CompletableFuture<?> future) {
        try {
            return budget.await(future);
        } catch (InterruptedException e) {
            throw new Stop(budget.abortKind(), budget.abortMessage());
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ApiError api) throw api;
            if (e.getCause() instanceof Stop stop) throw stop;
            throw new ApiError("failed");
        } catch (java.util.concurrent.CancellationException e) {
            throw new Stop(budget.abortKind(), budget.abortMessage());
        } finally {
            if (Thread.currentThread().isInterrupted()) {
                // a stop arrived while waiting: unwind (the hook would see it at the next check as well)
                consult();
            }
        }
    }

    /** A wrong host call: ends the script with this error (without a line: the host saw it, not the script). */
    private Failure refuse(String message) {
        return new Failure(error(ScriptError.Kind.RUNTIME, message));
    }

    private void take(int ptr) {
        var data = pending;
        pending = null;
        if (data == null || data.length == 0) return;
        check(ptr, data.length);
        live().memory().write(ptr, data);
    }

    private byte[] bytes(int ptr, int len) {
        check(ptr, len);
        return live().memory().readBytes(ptr, len);
    }

    private void check(int ptr, int len) {
        var m = memory;
        long end = (long) ptr + len;
        if (m == null || ptr < 0 || len < 0 || end > (long) m.pages() * Memory.PAGE_SIZE) {
            throw new Failure(error(ScriptError.Kind.RUNTIME, "host call with memory out of bounds"));
        }
    }

    /** {@code env.abort}: AssemblyScript's {@code throw} and failed assertions; the line is the script's own. */
    private Failure abort(int msgPtr, int filePtr, int line, int column) {
        String message = string(msgPtr);
        String file = string(filePtr);
        if (message.isEmpty()) message = "aborted";
        boolean lib = file.startsWith("~lib/");
        if (lib && message.toLowerCase(java.util.Locale.ROOT).contains("memory") && memoryFull()) return new Failure(memoryLimit());
        String where = lib || line <= 0 ? name + ": " : name + ":" + line + ": ";
        String trace = file.isEmpty() ? null : name + ": " + message + "\n  at " + file + ":" + line + ":" + column;
        return new Failure(new ScriptError(ScriptError.Kind.RUNTIME, where + message, lib || line <= 0 ? null : line, trace));
    }

    private void trace(int msgPtr, int n, long[] args) {
        var sb = new StringBuilder(string(msgPtr));
        for (int i = 0; i < Math.max(0, Math.min(5, n)); i++) sb.append(i == 0 ? " " : ", ").append(Double.longBitsToDouble(args[2 + i]));
        if (calls.tryAcquire()) api.trace(sb.toString());
    }

    /** An AssemblyScript string: UTF-16 code units at {@code ptr}, their byte length in the four bytes before it. */
    private String string(int ptr) {
        var m = memory;
        if (m == null || ptr < 4 || (long) ptr > (long) m.pages() * Memory.PAGE_SIZE) return "";
        int bytes = m.readInt(ptr - 4);
        if (bytes < 0 || (long) ptr + bytes > (long) m.pages() * Memory.PAGE_SIZE) return "";
        bytes = Math.min(bytes, 2000) & ~1;
        return new String(m.readBytes(ptr, bytes), StandardCharsets.UTF_16LE);
    }
}
