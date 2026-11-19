package ai.ezfox.cccloud.code;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.engine.ScriptError;
import ai.ezfox.cccloud.code.runtime.CpuClock;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * The CPU budget counts the CPU time of the thread that runs the script, not wall time: a worker that does not get a
 * core (a lagging server, a GC pause) must not stop a script with "ran for more than … ms", computing must.
 */
class CpuBudgetTest {
    /** Longer than the 120 ms per resume of {@link TestScripts#FAST}. */
    private static final long STALL_MS = 400;
    private static final long MS = 1_000_000L;

    /** A clock the test moves by hand. */
    private static final class HandClock implements CpuClock {
        final AtomicLong nanos = new AtomicLong();

        @Override
        public long now() {
            return nanos.get();
        }

        @Override
        public long of(long threadId) {
            return nanos.get();
        }

        @Override
        public boolean wall() {
            return false;
        }
    }

    private static TestScripts.Run runStalled(TestScripts scripts, Runnable insideNotify) throws InterruptedException {
        scripts.world.onNotify = insideNotify;
        // after the call the script runs on for 50 ms (wall), so the watchdog makes it look at its budget at least once
        var run = scripts.runFast("""
            cloud.notify('porch light')
            local t = cloud.clock()
            while cloud.clock() - t < 0.05 do end
            print('done')""");
        assertTrue(run.await(15_000), "script did not end: " + run.logText());
        return run;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Computes until the calling thread used {@code ms} of CPU time (or 20 s passed). */
    private static long spin(long ms) {
        var clock = CpuClock.system();
        long start = clock.now();
        long deadline = System.nanoTime() + 20_000 * MS;
        long x = 0;
        while (clock.now() - start < ms * MS && System.nanoTime() < deadline) {
            for (int k = 0; k < 10_000; k++) x += k ^ x;
        }
        return x;
    }

    // ---- the clock -----------------------------------------------------------------------------------------------------

    @Test
    void thisJvmMeasuresThreadCpuTime() {
        assertFalse(CpuClock.system().wall(), "expected thread CPU time on a HotSpot JVM, got " + CpuClock.system());
        assertNotNull(CpuClock.threadCpu(ManagementFactory.getThreadMXBean()));
    }

    @Test
    void sleepingUsesNoCpuTime() {
        var clock = CpuClock.system();
        long cpu = clock.now();
        long wall = System.nanoTime();
        sleep(STALL_MS);
        long usedCpu = clock.now() - cpu;
        assertTrue(System.nanoTime() - wall >= STALL_MS * MS);
        assertTrue(usedCpu < 50 * MS, "sleeping used " + usedCpu / MS + " ms of CPU time");
    }

    @Test
    void computingUsesCpuTime() {
        var clock = CpuClock.system();
        long cpu = clock.now();
        spin(150);
        assertTrue(clock.now() - cpu >= 150 * MS);
    }

    @Test
    void anotherThreadIsMeasuredByItsId() throws InterruptedException {
        var clock = CpuClock.system();
        var release = new CountDownLatch(1);
        var computed = new CountDownLatch(1);
        var waiting = new Thread(() -> {
            try {
                release.await();
            } catch (InterruptedException ignored) {
                // ends the thread
            }
        });
        var computing = new Thread(() -> {
            spin(150);
            computed.countDown();
            try {
                release.await();
            } catch (InterruptedException ignored) {
                // ends the thread
            }
        });
        waiting.start();
        computing.start();
        try {
            long waitingStart = clock.of(waiting.threadId());
            long computingStart = clock.of(computing.threadId());
            assertTrue(computed.await(30, TimeUnit.SECONDS));
            long waited = clock.of(waiting.threadId()) - waitingStart;
            long worked = clock.of(computing.threadId()) - computingStart;
            assertTrue(waitingStart >= 0 && computingStart >= 0);
            assertTrue(waited < 50 * MS, "a waiting thread used " + waited / MS + " ms of CPU time");
            assertTrue(worked >= 100 * MS, "a computing thread was seen with " + worked / MS + " ms of CPU time");
        } finally {
            release.countDown();
            waiting.join(5_000);
            computing.join(5_000);
        }
    }

    /** About one read per millisecond (the watchdog's rate): the read must cost far less than a millisecond. */
    @Test
    void readingTheClockIsCheap() {
        var clock = CpuClock.system();
        long sink = 0;
        for (int k = 0; k < 20_000; k++) sink += clock.now();
        int n = 200_000;
        long start = System.nanoTime();
        for (int k = 0; k < n; k++) sink += clock.now();
        double perRead = (System.nanoTime() - start) / (double) n;
        System.out.printf("CpuClock.now(): %.0f ns per read (%.3f %% of a 1 ms check interval), %s%n", perRead, perRead / 1e4, clock);
        assertTrue(perRead < 20_000, "one read took " + perRead + " ns");
        assertNotEquals(42, sink);
    }

    // ---- the runtime with a clock moved by hand ------------------------------------------------------------------------

    @Test
    void waitingWithoutCpuDoesNotUseTheBudget() throws InterruptedException {
        var clock = new HandClock();
        try (var scripts = new TestScripts(clock)) {
            var run = runStalled(scripts, () -> sleep(STALL_MS));
            assertNull(run.error(), run.logText());
            assertEquals("finished", run.runtime().endReason());
            assertTrue(run.logText().contains("done"), run.logText());
        }
    }

    @Test
    void cpuTimeUsesTheBudget() throws InterruptedException {
        var clock = new HandClock();
        try (var scripts = new TestScripts(clock)) {
            var run = runStalled(scripts, () -> clock.nanos.addAndGet(STALL_MS * MS));
            assertNotNull(run.error(), run.logText());
            assertEquals(ScriptError.Kind.CPU_LIMIT, run.error().kind());
            assertTrue(run.error().getMessage().contains("ran for more than 120 ms without pausing"), run.error().getMessage());
            assertTrue(run.runtime().cpuTotalNanos() >= STALL_MS * MS);
        }
    }

    // ---- the runtime with the real clocks ------------------------------------------------------------------------------

    /** The bug: a three-line script was stopped when its worker stalled (load 40-60, GC). */
    @Test
    void aStalledWorkerDoesNotStopTheScript() throws InterruptedException {
        try (var scripts = new TestScripts()) {
            var run = runStalled(scripts, () -> sleep(STALL_MS));
            assertNull(run.error(), run.logText());
            assertTrue(run.logText().contains("done"), run.logText());
            assertTrue(run.runtime().cpuTotalNanos() < STALL_MS / 2 * MS, "charged " + run.runtime().cpuTotalNanos() / MS + " ms");
        }
    }

    /** The same stall with wall time stops the script: the test above sees the difference. */
    @Test
    void wallTimeWouldHaveStoppedIt() throws InterruptedException {
        try (var scripts = new TestScripts(CpuClock.WALL)) {
            var run = runStalled(scripts, () -> sleep(STALL_MS));
            assertNotNull(run.error(), run.logText());
            assertEquals(ScriptError.Kind.CPU_LIMIT, run.error().kind());
        }
    }

    @Test
    void computingOnTheWorkerStopsTheScript() throws InterruptedException {
        try (var scripts = new TestScripts()) {
            var run = runStalled(scripts, () -> spin(STALL_MS));
            assertNotNull(run.error(), run.logText());
            assertEquals(ScriptError.Kind.CPU_LIMIT, run.error().kind());
            assertTrue(run.error().getMessage().contains("without pausing"), run.error().getMessage());
        }
    }

    @Test
    void anEndlessLoopIsStillStoppedInTime() throws InterruptedException {
        try (var scripts = new TestScripts()) {
            long start = System.nanoTime();
            var run = scripts.runFast("while true do end");
            assertTrue(run.await(TimeUnit.SECONDS.toMillis(15)));
            assertEquals(ScriptError.Kind.CPU_LIMIT, run.error().kind());
            assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(10));
        }
    }
}
