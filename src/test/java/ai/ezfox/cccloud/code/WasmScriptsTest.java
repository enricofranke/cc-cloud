package ai.ezfox.cccloud.code;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.engine.ScriptError;
import ai.ezfox.cccloud.code.runtime.Limits;
import ai.ezfox.cccloud.code.runtime.ScriptRuntime;
import ai.ezfox.cccloud.code.runtime.ScriptScheduler;
import ai.ezfox.cccloud.code.wasm.WasmEngine;
import ai.ezfox.cccloud.code.wasm.WasmSource;
import ai.ezfox.cccloud.code.world.WorldAction;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * WebAssembly scripts on the runtime's thread-per-script path, with modules compiled from {@code wasm/assemblyscript/tests}
 * (rebuild with {@code npm run build} there): limits (endless loop, memory bomb, recursion, too many host calls), the
 * cloud API against the fake world, handlers inside waits, stopping, and modules the server refuses.
 */
class WasmScriptsTest {
    private final TestScripts scripts = new TestScripts();

    @AfterEach
    void close() {
        scripts.close();
    }

    static byte[] module(String name) {
        try (InputStream in = WasmScriptsTest.class.getResourceAsStream("/wasm/" + name + ".wasm")) {
            assertNotNull(in, "missing test module " + name + " (npm run build in wasm/assemblyscript)");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static String stored(String name) {
        return new WasmSource(module(name), "// " + name + ".ts", "assemblyscript").encode();
    }

    private TestScripts.Run run(String name) {
        return run(name, TestScripts.FAST);
    }

    private TestScripts.Run run(String name, Limits limits) {
        return scripts.run(stored(name), limits, TestScripts.snapshot(1, 20, true), new WasmEngine(() -> limits));
    }

    // ---- limits ------------------------------------------------------------------------------------------------------

    @Test
    void endlessLoopIsStoppedByTheCpuLimit() throws InterruptedException {
        long start = System.nanoTime();
        var run = run("loop");
        assertTrue(run.await(10_000), "the loop was not stopped: " + run.logText());
        assertNotNull(run.error());
        assertEquals(ScriptError.Kind.CPU_LIMIT, run.error().kind(), run.error().getMessage());
        assertTrue(run.error().getMessage().contains("ran for more than 120 ms without pausing"), run.error().getMessage());
        assertTrue(run.logText().contains("looping"), run.logText());
        // 120 ms at 50 ms per second: blocked twice in between for the share, so about two seconds
        assertTrue(System.nanoTime() - start > 1_500_000_000L, "the per-second share held it back");
        assertTrue(run.runtime().cpuTotalNanos() < 250_000_000L, "used " + run.runtime().cpuTotalNanos() / 1e6 + " ms");
    }

    @Test
    void busyScriptGetsItsShareOnlyAndStopsPromptly() throws InterruptedException {
        var limits = new Limits(16, 8, 32 * 1024, 30, 100_000, 20, 32, 64, 1024, 20, 128 * 1024, 32 * 1024, 200, 1L << 40, 4L << 20, 20, 32, 6, 5, 0.2,
            100_000, 64 * 1024);
        var run = run("busy", limits);
        Thread.sleep(2500);
        long used = run.runtime().cpuTotalNanos();
        assertTrue(used < 250_000_000L, "about 30 ms per started second, used " + used / 1e6 + " ms");
        assertTrue(used > 30_000_000L, "but it does run, used " + used / 1e6 + " ms");
        long stop = System.nanoTime();
        run.runtime().stop("stopped");
        assertTrue(run.await(2000), "stop ends a busy script promptly");
        assertTrue(System.nanoTime() - stop < 1_500_000_000L);
        assertNull(run.error(), "a person stopping it is not an error");
        assertEquals("stopped", run.runtime().endReason());
    }

    @Test
    void memoryBombHitsTheHardCap() throws InterruptedException {
        var run = run("membomb");
        assertTrue(run.await(20_000), run.logText());
        assertNotNull(run.error());
        assertEquals(ScriptError.Kind.MEMORY_LIMIT, run.error().kind(), run.error().getMessage());
        assertTrue(run.error().getMessage().contains("more than 4 MB"), run.error().getMessage());
        assertTrue(run.runtime().heapEstimate() <= TestScripts.FAST.memoryBytes(), "linear memory stayed within the cap: " + run.runtime().heapEstimate());
    }

    @Test
    void recursionEndsWithAStackOverflowError() throws InterruptedException {
        var run = run("recursion");
        assertTrue(run.await(20_000), run.logText());
        assertNotNull(run.error());
        assertEquals(ScriptError.Kind.STACK_OVERFLOW, run.error().kind(), run.error().getMessage());
    }

    @Test
    void tooManyHostCallsAreStopped() throws InterruptedException {
        var limits = new Limits(16, 8, 32 * 1024, 500, 5000, 20, 32, 64, 1024, 20, 128 * 1024, 32 * 1024, 200, 1L << 40, 4L << 20, 20, 32, 6, 5, 0.2,
            300, 64 * 1024);
        var run = run("hostcalls", limits);
        assertTrue(run.await(10_000), run.logText());
        assertNotNull(run.error());
        assertTrue(run.error().getMessage().contains("more than 300 host calls within a second"), run.error().getMessage());
    }

    @Test
    void threadsAreBoundedOnTheServer() throws InterruptedException {
        var runs = new ArrayList<TestScripts.Run>();
        for (int i = 0; i < ScriptScheduler.MAX_THREADS + 2; i++) runs.add(run("stop"));
        TestScripts.until(() -> runs.stream().filter(r -> r.runtime().isEnded()).count() >= 2, 5000, "refusals");
        long refused = runs.stream().filter(r -> r.error() != null && r.error().getMessage().contains("too many wasm scripts")).count();
        assertEquals(2, refused, "at most " + ScriptScheduler.MAX_THREADS + " threads");
        assertTrue(scripts.scheduler.threadCount() <= ScriptScheduler.MAX_THREADS);
        for (var r : runs) r.runtime().stop("test");
        TestScripts.until(() -> scripts.scheduler.threadCount() == 0, 5000, "threads ended");
    }

    // ---- the API -----------------------------------------------------------------------------------------------------

    @Test
    void cloudApiWorksLikeInLua() throws InterruptedException {
        var run = run("api");
        TestScripts.until(() -> run.logText().contains("store "), 5000, "the script ran: " + run.logText());
        // a change of the heat widget while main sleeps runs the handler inside the sleep
        run.runtime().offer(TestScripts.snapshot(2, 55, true));
        assertTrue(run.await(10_000), run.logText());
        assertNull(run.error(), () -> run.error().getMessage());
        String log = run.logText();
        assertTrue(log.contains("info: card Reactor true 4 heat °C"), log);
        assertTrue(log.contains("info: value 20 null null"), log);
        assertTrue(log.contains("info: energy 5000 10000 50"), log);
        assertTrue(log.contains("info: script test wasm"), log);
        assertTrue(log.contains("info: set true"), log);
        assertTrue(log.contains("info: press false pin_required"), log);
        assertTrue(log.contains("info: items 42"), log);
        assertTrue(log.contains("info: store 7 [\"count\"]"), log);
        assertTrue(log.contains("info: obj {\"a\":\"x\\\"y\\n\",\"n\":1.5,\"list\":[1,2,3]}"), log);
        assertTrue(log.contains("info: tick"), log);
        assertTrue(log.contains("info: change heat 55"), log);
        assertTrue(log.contains("info: slept 1"), log);
        assertTrue(scripts.world.actions.stream().anyMatch(a -> a instanceof WorldAction.Set s && s.widgetId().equals("active") && Boolean.FALSE.equals(s.value())),
            "the action reached the world: " + scripts.world.actions);
    }

    @Test
    void errorsHaveTheScriptsLine() throws InterruptedException {
        var run = run("errors");
        assertTrue(run.await(5000), run.logText());
        assertNotNull(run.error());
        assertEquals(ScriptError.Kind.RUNTIME, run.error().kind());
        assertEquals("test:7: boom", run.error().getMessage());
        assertEquals(7, run.error().line());
        assertTrue(run.logText().contains("before"));
        var bad = run("badarg");
        assertTrue(bad.await(5000), bad.logText());
        assertEquals(ScriptError.Kind.RUNTIME, bad.error().kind());
        assertTrue(bad.error().getMessage().startsWith("test: ") && bad.error().getMessage().contains("nonsense"), bad.error().getMessage());
    }

    @Test
    void waitsTimersAndStop() throws InterruptedException {
        var run = run("stop");
        TestScripts.until(() -> run.logText().contains("every"), 3000, "the timer fired while main waits: " + run.logText());
        run.runtime().offer(TestScripts.snapshot(2, 30, true));
        TestScripts.until(() -> run.logText().contains("woke false"), 3000, "cloud.wait got the change: " + run.logText());
        assertEquals(ScriptRuntime.Status.RUNNING, run.runtime().status());
        assertEquals(1, run.runtime().timerCount());
        run.runtime().stop("stopped");
        assertTrue(run.await(2000), "a stop ends a script that waits");
        assertNull(run.error());
    }

    // ---- refused modules ---------------------------------------------------------------------------------------------

    @Test
    void wasiAndForeignImportsAreRefusedAtSaveAndStart() throws InterruptedException {
        var engine = new WasmEngine(() -> TestScripts.FAST);
        var diagnostics = engine.check("test", stored("wasi"));
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.getFirst().message().contains("wasi_snapshot_preview1.fd_write") && diagnostics.getFirst().message().contains("WASI"),
            diagnostics.getFirst().message());
        var run = run("wasi");
        assertTrue(run.await(5000));
        assertEquals(ScriptError.Kind.SYNTAX, run.error().kind());
        assertTrue(run.error().getMessage().contains("WASI"), run.error().getMessage());
        assertTrue(engine.check("test", stored("api")).isEmpty(), "a normal module passes");
    }

    @Test
    void storedFormIsChecked() {
        var engine = new WasmEngine(() -> TestScripts.FAST);
        assertTrue(engine.check("t", "print('lua')").getFirst().message().contains("not a WebAssembly script"));
        assertTrue(engine.check("t", "{\"format\":\"cccloud-wasm\",\"wasm\":\"!!\"}").getFirst().message().contains("base64"));
        assertTrue(engine.check("t", new WasmSource(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, null, null).encode()).getFirst().message().contains("\\0asm"));
        var big = new byte[TestScripts.FAST.maxWasmBytes() + 1];
        big[1] = 'a';
        big[2] = 's';
        big[3] = 'm';
        assertTrue(engine.check("t", new WasmSource(big, null, null).encode()).getFirst().message().contains("larger than"));
        var source = "x".repeat(TestScripts.FAST.maxWasmSource() + 1);
        assertTrue(engine.check("t", new WasmSource(module("api"), source, "assemblyscript").encode()).getFirst().message().contains("source text"));
        // truncated module
        var api = module("api");
        assertTrue(engine.check("t", new WasmSource(java.util.Arrays.copyOf(api, api.length / 2), null, null).encode()).getFirst().message()
            .contains("not a valid WebAssembly module"));
        var back = assertDoesNotThrow(() -> WasmSource.decode(new WasmSource(api, "src", "assemblyscript").encode(), 1 << 20, 1 << 20));
        assertArrayEquals(api, back.module());
        assertEquals("src", back.source());
    }

    @Test
    void handBuiltModulesThatCouldHurtTheServerAreRefused() {
        var engine = new WasmEngine(() -> TestScripts.FAST);
        // (memory 1000 pages): 64 MB at start, over the 4 MB cap
        assertRefused(engine, WasmBytes.memory(1000, false, false), "of memory at start");
        // shared memory (threads)
        assertRefused(engine, WasmBytes.memory(1, true, true), "shared memory");
        // a table without a maximum
        assertRefused(engine, WasmBytes.table(1, -1), "table");
        // an imported memory
        assertRefused(engine, WasmBytes.importedMemory(), "only host functions");
        // a function with 5000 locals
        assertRefused(engine, WasmBytes.locals(5000), "locals");
        // a struct type (garbage collection)
        assertRefused(engine, WasmBytes.structType(), "garbage-collected");
        // nothing to run
        assertRefused(engine, WasmBytes.empty(), "nothing to run");
        // the smallest module that runs
        assertTrue(engine.check("t", new WasmSource(WasmBytes.mainOnly(), null, null).encode()).isEmpty());
    }

    private static void assertRefused(WasmEngine engine, byte[] module, String expected) {
        var d = engine.check("t", new WasmSource(module, null, null).encode());
        assertEquals(1, d.size(), "refused");
        assertTrue(d.getFirst().message().contains(expected), d.getFirst().message());
    }
}
