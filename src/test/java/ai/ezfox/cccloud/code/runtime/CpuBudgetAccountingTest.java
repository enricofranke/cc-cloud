package ai.ezfox.cccloud.code.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.runtime.CpuBudget.Verdict;
import org.junit.jupiter.api.Test;

/** {@link CpuBudget} with a clock moved by hand: what counts against the share and the run limit, and what does not. */
class CpuBudgetAccountingTest {
    private static final long MS = 1_000_000L;
    /** 50 ms per second, 120 ms per run, allocation and memory out of the way. */
    private static CpuBudget budget(CpuClock clock) {
        return new CpuBudget(clock, 50, 120, Long.MAX_VALUE / 4, Long.MAX_VALUE / 4);
    }

    @Test
    void timeWithoutCpuIsNotCharged() {
        var clock = new HandClock();
        var b = budget(clock);
        b.roll(1);
        b.startJob();
        // the worker stalls 400 ms (lag, GC): its CPU clock does not move
        b.blocked(400 * MS);
        assertEquals(Verdict.CONTINUE, b.check(0));
        assertEquals(0, b.endJob());
        assertFalse(b.shareUsed());
        assertEquals(0, b.totalCpuNanos());
    }

    @Test
    void cpuTimeIsChargedAndStopsALongRun() {
        var clock = new HandClock();
        var b = budget(clock);
        b.roll(1);
        b.startJob();
        clock.advanceMs(30);
        assertEquals(Verdict.CONTINUE, b.check(0));
        clock.advanceMs(30);
        assertEquals(Verdict.PAUSE, b.check(0), "60 ms is over the share of 50");
        clock.advanceMs(70);
        assertEquals(Verdict.TOO_LONG, b.check(0), "130 ms is over the 120 ms per run");
        assertEquals(130 * MS, b.endJob());
        assertEquals(130 * MS, b.totalCpuNanos());
    }

    @Test
    void theRunCarriesOverPauses() {
        var clock = new HandClock();
        var b = budget(clock);
        b.roll(1);
        b.startJob();
        clock.advanceMs(30);
        assertEquals(Verdict.CONTINUE, b.check(80 * MS));
        clock.advanceMs(20);
        assertEquals(Verdict.TOO_LONG, b.check(80 * MS), "80 ms before the pause + 50 ms now");
    }

    @Test
    void wallTimeTakesWaitsOff() {
        var clock = new HandClock(true);
        var b = budget(clock);
        b.roll(1);
        b.startJob();
        clock.advanceMs(400);
        b.blocked(400 * MS);
        assertEquals(Verdict.CONTINUE, b.check(0));
        clock.advanceMs(10);
        assertEquals(10 * MS, b.endJob());
    }

    @Test
    void cpuClockDoesNotTakeWaitsOff() {
        var clock = new HandClock();
        var b = budget(clock);
        b.roll(1);
        b.startJob();
        clock.advanceMs(40);
        b.blocked(400 * MS); // reported by the engine, but thread CPU time already left the wait out
        assertEquals(40 * MS, b.endJob());
    }

    @Test
    void windowsAreWallTimeAndPausesAreCounted() {
        var clock = new HandClock();
        var b = budget(clock);
        long start = 5 * CpuBudget.SECOND;
        assertEquals(0, b.roll(start));
        b.startJob();
        clock.advanceMs(60);
        b.endJob();
        assertTrue(b.shareUsed());
        assertEquals(start + CpuBudget.SECOND, b.pauseForShare());
        assertEquals(1, b.sharePauses());
        assertEquals(0, b.roll(start + CpuBudget.SECOND - 1));
        assertTrue(b.shareUsed());
        assertEquals(1, b.roll(start + CpuBudget.SECOND));
        assertEquals(60 * MS, b.lastWindowUsed());
        assertFalse(b.shareUsed());
        assertEquals(3, b.roll(start + 4 * CpuBudget.SECOND + 7), "three idle seconds later");
        assertEquals(start + 5 * CpuBudget.SECOND, b.windowEnd());
    }

    @Test
    void workOutsideAJobCountsAgainstTheShareOnly() {
        var clock = new HandClock();
        var b = budget(clock);
        b.roll(1);
        long mark = b.mark();
        clock.advanceMs(55);
        b.chargeSince(mark);
        assertTrue(b.shareUsed());
        assertEquals(0, b.totalCpuNanos());
    }

    /** A sink so the allocation below is not optimized away. */
    static volatile Object kept;

    @Test
    void heapWalkIsChargedAndThenNotDueAgainAtOnce() {
        var clock = new HandClock();
        var b = new CpuBudget(clock, 50, 120, Long.MAX_VALUE / 4, 16L << 20);
        b.roll(1);
        if (CpuBudget.allocationLimited()) {
            // due after allocating a quarter of the memory cap: the check pauses the task for the measurement
            b.startJob();
            for (int k = 0; k < 8; k++) kept = new byte[1 << 20];
            assertEquals(Verdict.MEASURE_HEAP, b.check(0));
            b.endJob();
            assertTrue(b.heapPauseOnly(), "the share is left, so the task goes on right after the measurement");
        }
        long estimate = b.measureHeap(System.nanoTime() + 20 * CpuBudget.SECOND, () -> {
            clock.advanceMs(20);
            return 3L << 20;
        });
        assertEquals(3L << 20, estimate);
        assertEquals(3L << 20, b.heapEstimate());
        b.startJob();
        assertEquals(Verdict.CONTINUE, b.check(0));
        clock.advanceMs(31);
        assertEquals(Verdict.PAUSE, b.check(0), "20 ms walk + 31 ms run is over the share");
        b.endJob();
        assertEquals(-1, b.measureHeap(System.nanoTime(), () -> fail("not due")));
    }

    @Test
    void theSystemBudgetMeasuresThisThread() {
        var b = new CpuBudget(CpuClock.system(), 1000, 5000, Long.MAX_VALUE / 4, Long.MAX_VALUE / 4);
        b.roll(System.nanoTime());
        b.startJob();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(b.endJob() < 50 * MS, "sleeping was charged");
    }
}
