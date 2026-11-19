package ai.ezfox.cccloud.computer;

import static ai.ezfox.cccloud.computer.TestComputers.*;
import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.runtime.CpuClock;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cloud Computers count the CPU time of the thread that runs the program: a worker that stalls (a lagging server, a GC
 * pause) does not turn a computer off with "too long without yielding", computing does.
 */
class ComputerCpuTimeTest {
    /** Longer than the 150 ms per run of {@link #TIGHT}. */
    private static final long STALL_MS = 400;
    private static final ComputerLimits TIGHT = new ComputerLimits(50, 150, 16L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);
    /** Reads the input, runs on for 50 ms (so the budget is looked at), says so and waits for events. */
    private static final String PROGRAM = """
        redstone.getInput("top")
        local t = os.clock()
        while os.clock() - t < 0.05 do end
        print("lamp checked")
        while true do os.pullEvent() end
        """;

    @TempDir
    Path tmp;

    /** Redstone whose input read runs {@code inside} on the program's thread first. */
    private static RedstoneIo reading(Runnable inside) {
        var plain = new RedstoneIo.Memory();
        return new RedstoneIo() {
            @Override
            public int input(ComputerSide side) {
                inside.run();
                return plain.input(side);
            }

            @Override
            public int output(ComputerSide side) {
                return plain.output(side);
            }

            @Override
            public void setOutput(ComputerSide side, int level) {
                plain.setOutput(side, level);
            }

            @Override
            public long outputs() {
                return plain.outputs();
            }
        };
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void spin(long ms) {
        var clock = CpuClock.system();
        long start = clock.now();
        long deadline = System.nanoTime() + 20_000_000_000L;
        long x = 0;
        while (clock.now() - start < ms * 1_000_000L && System.nanoTime() < deadline) {
            for (int k = 0; k < 10_000; k++) x += k ^ x;
        }
        if (x == 42) System.out.println(x);
    }

    private Computer boot(TestComputers computers, Runnable insideInput) {
        computers.limits = TIGHT;
        var c = computers.create(reading(insideInput));
        file(c, "startup.lua", PROGRAM);
        c.turnOn();
        return c;
    }

    @Test
    void aStalledWorkerDoesNotTurnTheComputerOff() {
        try (var computers = new TestComputers(tmp)) {
            var c = boot(computers, () -> sleep(STALL_MS));
            until(c, 15_000, "the program", () -> shows(c, "lamp checked"));
            assertTrue(c.isOn(), "turned off: " + c.lastError());
            assertNull(c.lastError());
        }
    }

    /** The same stall measured in wall time stops it: the test above sees the difference. */
    @Test
    void wallTimeWouldHaveTurnedItOff() {
        try (var computers = new TestComputers(tmp, CpuClock.WALL)) {
            var c = boot(computers, () -> sleep(STALL_MS));
            until(c, 15_000, "the stop", () -> !c.isOn());
            assertNotNull(c.lastError());
            assertTrue(c.lastError().contains("too long without yielding"), c.lastError());
        }
    }

    @Test
    void computingOnTheWorkerTurnsItOff() {
        try (var computers = new TestComputers(tmp)) {
            var c = boot(computers, () -> spin(STALL_MS));
            until(c, 15_000, "the stop", () -> !c.isOn());
            assertNotNull(c.lastError());
            assertTrue(c.lastError().contains("too long without yielding"), c.lastError());
        }
    }
}
