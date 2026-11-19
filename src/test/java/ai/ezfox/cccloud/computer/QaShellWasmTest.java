package ai.ezfox.cccloud.computer;

import static ai.ezfox.cccloud.computer.TestComputers.*;
import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.runtime.Limits;
import ai.ezfox.cccloud.code.wasm.WasmEngine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Independent attacks on WebAssembly programs started from a computer's shell. */
class QaShellWasmTest {
    @TempDir
    Path tmp;
    private TestComputers computers;

    @BeforeEach
    void setUp() {
        computers = new TestComputers(tmp);
        computers.wasm = new WasmEngine(() -> Limits.DEFAULT);
    }

    @AfterEach
    void close() {
        computers.close();
    }

    private static byte[] module(String name) {
        try (var in = QaShellWasmTest.class.getResourceAsStream("/wasm/" + name + ".wasm")) {
            assertNotNull(in, name);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private String run(Computer c, String program, long millis) {
        file(c, "startup.lua", "local function report(v) local f = fs.open('result', 'w') f.write(tostring(v)) f.close() end\n" + program
            + "\nfs.open('done', 'w').close() while true do os.pullEvent() end");
        c.turnOn();
        until(c, millis, "the probe", () -> c.fs().exists("done") || c.lastError() != null);
        String out = c.fs().exists("result") ? read(c, "result") : "-";
        System.out.println("QA shell wasm: " + out + " | error=" + c.lastError() + "\n" + screen(c).strip());
        return out;
    }

    /**
     * The shell's os.run waits for wasm_done with os.pullEventRaw, which is a coroutine.yield: a program could run os.run
     * in a coroutine of its own and keep computing while the WebAssembly program runs (N9). The Lua side is held instead
     * until the program ended: no Lua runs beside it, and afterwards a second program can start again.
     */
    @Test
    void luaWaitsWhileTheProgramRuns() throws InterruptedException {
        var c = computers.create();
        c.fs().writeFile("greet.wasm", module("computer-args"));
        file(c, "startup.lua", """
            local co = coroutine.create(function() return shell.run("greet") end)
            coroutine.resume(co)
            local start, n = os.clock(), 0
            while os.clock() - start < 0.3 do n = n + 1 end
            local second = { pcall(shell.run, "greet") }
            local f = fs.open("result", "w") f.write("loops=" .. n .. " second=" .. tostring(second[2])) f.close()
            fs.open("done", "w").close() while true do os.pullEvent() end""");
        c.turnOn();
        until(c, 30000, "the program", () -> c.runtimeForTest() != null && c.runtimeForTest().wasmRunning());
        var rt = c.runtimeForTest();
        long cpu = rt.cpuTotalNanos();
        long end = System.nanoTime() + 700_000_000L;
        while (System.nanoTime() < end) {
            c.tick();
            Thread.sleep(5);
        }
        assertTrue(rt.luaHeld(), "the Lua side is held while the program runs");
        assertFalse(c.fs().exists("result"), "no Lua ran beside the program");
        assertTrue(rt.cpuTotalNanos() - cpu < 20_000_000L, "Lua used no CPU meanwhile: " + (rt.cpuTotalNanos() - cpu) / 1e6 + " ms");
        c.input(InputEvent.character('a'), "p"); // ends the first program
        until(c, 30000, "the second program", () -> TestComputers.shows(c, "got a") && rt.wasmRunning());
        c.input(InputEvent.character('b'), "p");
        until(c, 30000, "the result", () -> c.fs().exists("done"));
        String out = read(c, "result");
        System.out.println("QA shell wasm: " + out);
        assertTrue(out.endsWith("second=true"), "a second program starts once the first ended: " + out);
    }

    /** A program writes a hostile module onto its own drive (no upload check) and runs it: the engine's check refuses it. */
    @Test
    void selfWrittenHostileModulesAreRefused() {
        var c = computers.create();
        // the 25-byte module of the first critical finding: a data segment that claims 2^31 bytes
        var data = new ByteArrayOutputStream();
        data.writeBytes(new byte[]{0, 'a', 's', 'm', 1, 0, 0, 0});
        data.writeBytes(new byte[]{5, 3, 1, 0, 1});
        data.writeBytes(new byte[]{11, 9, 1, 0, 0x41, 0, 0x0b, (byte) 0xf0, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x07});
        c.fs().writeFile("bomb.wasm", data.toByteArray());
        var big = new byte[200 * 1024];
        System.arraycopy(module("computer-hello"), 0, big, 0, 8);
        c.fs().writeFile("big.wasm", big);
        String out = run(c, """
            local a = { pcall(shell.run, "bomb") }
            local b = { pcall(shell.run, "big") }
            report(tostring(a[2]) .. " " .. tostring(b[2]))""", 30000);
        assertEquals("false false", out);
        assertNull(c.lastError());
    }

    /** Starting and ending programs many times gives every thread and the open-file allowance back. */
    @Test
    void manyShortProgramsLeakNothing() {
        var c = computers.create();
        c.fs().writeFile("args.wasm", module("computer-args"));
        file(c, "startup.lua", """
            local ok = 0
            for i = 1, 60 do if shell.run("args") then ok = ok + 1 end end
            local f = fs.open("big", "w")
            local wrote = pcall(f.write, string.rep("x", 1000000))
            f.close()
            local r = fs.open("result", "w") r.write(ok .. " " .. tostring(wrote)) r.close()
            fs.open("done", "w").close() while true do os.pullEvent() end""");
        c.turnOn();
        long end = System.nanoTime() + 120_000_000_000L;
        while (!c.fs().exists("done") && System.nanoTime() < end) {
            c.tick();
            var rt = c.runtimeForTest();
            if (rt != null && rt.wasmRunning()) c.input(InputEvent.character('k'), "p" + (System.nanoTime() / 1_000_000_000L));
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        String out = c.fs().exists("result") ? read(c, "result") : "-";
        System.out.println("QA shell wasm: 60 runs -> " + out + " threads=" + computers.scheduler.threadCount());
        assertEquals("60 true", out, screen(c));
        assertEquals(0, computers.scheduler.threadCount(), "threads left running");
    }

    /** Ctrl+T ends a busy program, and the shell is usable again at once. */
    @Test
    void terminateEndsABusyProgram() {
        var c = computers.create();
        c.fs().writeFile("loop.wasm", module("computer-loop"));
        file(c, "startup.lua", "shell.run('loop') fs.open('done', 'w').close() while true do os.pullEvent() end");
        c.turnOn();
        until(c, 30000, "the program", () -> c.runtimeForTest() != null && c.runtimeForTest().wasmRunning());
        long start = System.nanoTime();
        c.input(InputEvent.control("terminate"), "p");
        until(c, 10000, "the end", () -> c.fs().exists("done"));
        System.out.println("QA shell wasm: terminate took " + (System.nanoTime() - start) / 1_000_000 + " ms");
        assertTrue(c.isOn());
    }

    /** N9 bypass attempts: whatever Lua does after starting the program in its own coroutine, it uses no CPU meanwhile. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "while true do pcall(function() for i = 1, 1e6 do end end) end",
        "local c2 = coroutine.wrap(function() while true do end end) c2()",
        "os.queueEvent('self') while true do os.pullEventRaw() local x = 0 for i = 1, 1e6 do x = x + i end end",
        "os.startTimer(0.05) while true do os.pullEventRaw('timer') for i = 1, 1e6 do end end",
        "while true do local s = string.rep('a', 1000000) .. 'b' end",
        "while true do pcall(error, 'x') end",
        "local t = {} for i = 1, 1e7 do t[i] = i end",
    })
    void luaStaysHeld(String after) throws InterruptedException {
        var c = computers.create();
        c.fs().writeFile("forever.wasm", module("computer-forever"));
        file(c, "startup.lua", "local co = coroutine.create(function() return shell.run('forever') end)\ncoroutine.resume(co)\n" + after
            + "\nfs.open('done', 'w').close()");
        c.turnOn();
        until(c, 30000, "the program", () -> c.runtimeForTest() != null && c.runtimeForTest().wasmRunning());
        var rt = c.runtimeForTest();
        Thread.sleep(100);
        long cpu = rt.cpuTotalNanos();
        long end = System.nanoTime() + 1_000_000_000L;
        while (System.nanoTime() < end) {
            c.tick();
            Thread.sleep(5);
        }
        long used = rt.cpuTotalNanos() - cpu;
        System.out.println("QA N9 bypass [" + after.substring(0, Math.min(45, after.length())) + "] Lua CPU in 1 s: " + used / 1_000_000 + " ms, held="
            + rt.luaHeld() + " wasm=" + rt.wasmRunning() + " error=" + c.lastError());
        assertTrue(rt.wasmRunning() && rt.luaHeld(), "program runs and Lua is held");
        assertTrue(used < 20_000_000L, "Lua used " + used / 1_000_000 + " ms beside the program");
        // Ctrl+T ends the program; Lua goes on (and may then run into its own limits), the computer must not hang
        c.input(InputEvent.control("terminate"), "p");
        until(c, 30000, "Lua going on", () -> !rt.wasmRunning() && !rt.luaHeld());
    }
}
