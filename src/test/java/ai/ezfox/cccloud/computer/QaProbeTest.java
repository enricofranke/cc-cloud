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
}
