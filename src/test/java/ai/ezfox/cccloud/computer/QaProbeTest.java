package ai.ezfox.cccloud.computer;

import static ai.ezfox.cccloud.computer.TestComputers.*;
import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.api.ApiError;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Independent checks of the Cloud Computer limits: each test states the expected behaviour. */
class QaProbeTest {
    @TempDir
    Path tmp;
    private TestComputers computers;

    @BeforeEach
    void setUp() {
        computers = new TestComputers(tmp);
    }

    @AfterEach
    void tearDown() {
        computers.close();
    }

    /**
     * Open write handles buffer their content on the Java heap, outside the program's memory estimate. A program must
     * not be able to keep more than its memory cap (here 4 MB) in such buffers.
     */
    @Test
    void writeBuffersStayWithinTheMemoryCap() {
        computers.limits = new ComputerLimits(500, 2000, 4L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);
        var c = computers.create();
        file(c, "startup.lua", """
            local chunk = string.rep("x", 1000000)
            local handles, ok = {}, 0
            for i = 1, 30 do
              local f = fs.open("f" .. i, "w")
              if not f then break end
              handles[i] = f
              if pcall(f.write, chunk) then ok = ok + 1 end
              chunk = chunk:sub(1, -2) .. "y" -- a new string each time
            end
            local r = fs.open("result", "w") r.write(tostring(ok)) r.close()
            fs.open("done", "w").close()
            while true do os.pullEvent() end
            """);
        c.turnOn();
        until(c, 30000, "the program", () -> c.fs().exists("done") || c.lastError() != null);
        long buffered = c.fs().exists("result") ? Long.parseLong(read(c, "result")) * 1_000_000L : 0;
        assertTrue(buffered <= computers.limits.memoryBytes(),
            "program holds " + (buffered >> 20) + " MB in open write handles with a " + (computers.limits.memoryBytes() >> 20) + " MB cap");
    }

    /** Copying the root into one of its own folders must be refused like any copy into itself. */
    @Test
    void copyRootIntoItselfIsRefused() {
        var c = computers.create();
        file(c, "a.txt", "hello");
        c.fs().makeDir("sub");
        assertThrows(ApiError.class, () -> c.fs().copy("/", "sub/copy"));
        assertFalse(c.fs().exists("sub/copy/sub"), "the copy went into itself");
    }

    /** A program's term.write must not hold the terminal lock (which the server thread takes) for long. */
    @Test
    void writeFarLeftOfTheScreenIsCheap() {
        var t = new ComputerTerminal();
        t.setCursor(-1_000_000_000, 0);
        String s = "a".repeat(1 << 20);
        long start = System.nanoTime();
        for (int i = 0; i < 50; i++) t.write(s);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("QA: 50 writes of 1 MB left of the screen: " + ms + " ms");
    }

    /** Programs that try to break the VM end with an error; the computer is then off and can boot again. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "local function f() return f() + 1 end f()",
        "local t = {} while true do t[#t + 1] = coroutine.create(function() coroutine.yield() end) coroutine.resume(t[#t]) end",
        "local t = {} local s = string.rep('a', 1000000) local i = 0 while true do i = i + 1 t[i] = s .. i end",
        "local t = {} while true do t = {t} end",
        "while true do pcall(function() while true do end end) end",
        "local co co = coroutine.wrap(function() while true do co() end end) co()",
        "string.rep('a', 2^31)",
        "local s = ('x'):rep(1000000) for i = 1, 100 do s = s:gsub('x', 'xx') end",
    })
    void runawayProgramsEndCleanly(String program) {
        computers.limits = new ComputerLimits(200, 500, 8L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);
        var c = computers.create();
        file(c, "startup.lua", program + "\nfs.open('done', 'w').close()");
        c.turnOn();
        long start = System.nanoTime();
        until(c, 60000, "the program to fail", () -> c.lastError() != null || !c.isOn() || shows(c, "startup.lua:") || shows(c, "rror"));
        System.out.println("QA runaway [" + program.substring(0, Math.min(40, program.length())) + "] after "
            + (System.nanoTime() - start) / 1_000_000 + " ms: error=" + c.lastError() + " on=" + c.isOn() + "\n" + screen(c).strip());
        assertFalse(c.fs().exists("done"));
        c.shutdown();
        file(c, "startup.lua", "fs.open('done', 'w').close()");
        c.turnOn();
        until(c, 30000, "a normal boot afterwards", () -> c.fs().exists("done"));
    }

    /** The Lua compiler's own allocations: big sources, long literals, a reader function that never ends. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "local s = string.rep('a=1;', 250000) local f = load(s) report(f ~= nil)",
        "local s = 'return \\'' .. string.rep('x', 1000000 - 20) .. '\\'' local f = load(s) report(f ~= nil)",
        "local s = 'return {' .. string.rep('1,', 450000) .. '}' local f = load(s) report(f ~= nil)",
        "local piece = string.rep('a=1;', 250000) local f = load(function() return piece end) report(f ~= nil)",
        "local piece = string.rep('-', 1000000) local n = 0 local f = load(function() n = n + 1 if n == 1 then return '--[[' end return piece end) report(f ~= nil)",
        "local s = 'return ' .. string.rep('(', 100000) .. '1' .. string.rep(')', 100000) local f, e = load(s) report(tostring(e))",
        "local s = 'local x = ' .. string.rep('1+', 400000) .. '1' local f = load(s) report(f ~= nil)",
    })
    void luaCompilerAllocations(String program) {
        computers.limits = new ComputerLimits(200, 1000, 16L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);
        var c = computers.create();
        file(c, "startup.lua", "local function report(v) local f = fs.open('result', 'w') f.write(tostring(v)) f.close() end\n" + program
            + "\nfs.open('done', 'w').close() while true do os.pullEvent() end");
        var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long before = 0;
        for (long id : bean.getAllThreadIds()) before += Math.max(0, bean.getThreadAllocatedBytes(id));
        long start = System.nanoTime();
        c.turnOn();
        until(c, 60000, "the program", () -> c.fs().exists("done") || c.lastError() != null || shows(c, "startup.lua:"));
        long after = 0;
        for (long id : bean.getAllThreadIds()) after += Math.max(0, bean.getThreadAllocatedBytes(id));
        System.out.println("QA lua compile [" + program.substring(0, Math.min(50, program.length())) + "] "
            + (System.nanoTime() - start) / 1_000_000 + " ms, allocated ~" + ((after - before) >> 20) + " MB, result="
            + (c.fs().exists("result") ? read(c, "result") : "-") + " error=" + c.lastError() + "\n" + screen(c).strip());
    }

    /**
     * load() with a reader function that keeps returning code: the compiler keeps what it read, inside one Java call the
     * memory estimate cannot pause. The live heap must stay near the program's 16 MB cap.
     */
    @Test
    void endlessLoadReaderStaysWithinTheMemoryCap() throws Exception {
        computers.limits = ComputerLimits.DEFAULT;
        var c = computers.create();
        file(c, "startup.lua", "local piece = string.rep('a=1;', 250000) local f = load(function() return piece end)\nfs.open('done', 'w').close()");
        System.gc();
        var rt = Runtime.getRuntime();
        long base = rt.totalMemory() - rt.freeMemory();
        long peak = 0;
        c.turnOn();
        long end = System.nanoTime() + 60_000_000_000L;
        while (System.nanoTime() < end && c.isOn() && c.lastError() == null) {
            c.tick();
            Thread.sleep(250);
            System.gc();
            peak = Math.max(peak, rt.totalMemory() - rt.freeMemory() - base);
        }
        System.out.println("QA lua endless reader: live heap peak +" + (peak >> 20) + " MB, error=" + c.lastError());
        assertTrue(peak < 64L << 20, "live heap grew by " + (peak >> 20) + " MB for one computer with a 16 MB cap");
    }

    // ---- attempts to get around the fixes of 6550e807 ------------------------------------------------------------------

    /** Runs a startup program that writes its findings to "result" and creates "done"; returns the result. */
    private String probe(String program) {
        var c = computers.create();
        file(c, "startup.lua", "local function report(v) local f = fs.open('result', 'w') f.write(tostring(v)) f.close() end\n" + program
            + "\nfs.open('done', 'w').close() while true do os.pullEvent() end");
        c.turnOn();
        until(c, 60000, "the probe", () -> c.fs().exists("done") || c.lastError() != null || shows(c, "startup.lua:"));
        String out = c.fs().exists("result") ? read(c, "result") : "-";
        System.out.println("QA fix probe: " + out + " | error=" + c.lastError() + "\n" + screen(c).strip());
        return out;
    }

    @Test
    void loadLimitCannotBeBypassed() {
        // pieces just under the limit, many times
        assertEquals("nil chunk too large (more than 256 KB)", probe("""
            local piece = string.rep('a=1;', 60000)
            local f, e = load(function() return piece end)
            report(tostring(f) .. ' ' .. tostring(e))"""));
        // one piece over the limit, and a string over the limit
        assertEquals("nil nil", probe("""
            local f1 = load(string.rep(' ', 300000) .. 'return 1')
            local big = string.rep(' ', 300000)
            local f2 = load(function() local b = big big = nil return b end)
            report(tostring(f1) .. ' ' .. tostring(f2))"""));
        // the original load must not be reachable from a program
        assertEquals("ok", probe("""
            local found = {}
            if debug then found[#found + 1] = 'debug' end
            if getfenv or setfenv then found[#found + 1] = 'fenv' end
            if string.dump then found[#found + 1] = 'dump' end
            if loadstring then found[#found + 1] = 'loadstring' end
            if _G.__native then found[#found + 1] = 'native' end
            -- a reader that changes the global load on the way must not change the limit
            local piece = string.rep('a=1;', 60000)
            local n = 0
            local f = load(function() n = n + 1 load = nil if n > 10 then return nil end return piece end)
            if f then found[#found + 1] = 'load accepted ' .. (n * #piece) end
            report(#found == 0 and 'ok' or table.concat(found, ','))"""));
        // a 200 KB program file on the drive still runs
        var c = computers.create();
        file(c, "big.lua", "local x = 1\n" + "-- filler\n".repeat(20_000) + "fs.open('ran', 'w').close()");
        file(c, "startup.lua", "shell.run('big') fs.open('done', 'w').close() while true do os.pullEvent() end");
        c.turnOn();
        until(c, 60000, "the big program", () -> c.fs().exists("done"));
        assertTrue(c.fs().exists("ran"), "a 200 KB program does not run any more:\n" + screen(c));
    }

    @Test
    void openFilesTogetherStayWithinTheDrive() {
        computers.limits = new ComputerLimits(500, 2000, 16L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);
        // read handles: a 700 KB file opened twice
        assertEquals("1", probe("""
            local f = fs.open('a', 'w') f.write(string.rep('x', 700000)) f.close()
            local ok = 0
            local h = {}
            for i = 1, 4 do local r = fs.open('a', 'r') if r then ok = ok + 1 h[i] = r end end
            report(ok)"""));
        // close gives the allowance back
        assertEquals("10", probe("""
            local ok = 0
            for i = 1, 10 do
              local f = fs.open('w', 'w')
              if f and pcall(f.write, string.rep('y', 600000)) then ok = ok + 1 end
              if f then f.close() end
            end
            report(ok)"""));
        // append mode counts the old content
        assertEquals("false", probe("""
            local f = fs.open('b', 'w') f.write(string.rep('z', 600000)) f.close()
            local a = fs.open('b', 'a')
            local w = fs.open('c', 'w')
            report(pcall(w.write, string.rep('q', 500000)))"""));
    }
}
