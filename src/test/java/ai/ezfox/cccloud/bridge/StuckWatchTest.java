package ai.ezfox.cccloud.bridge;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** A job without progress becomes stuck after the limit, recovers when it moves, and is reported only once. */
class StuckWatchTest {
    @Test
    void stuckAfterTheLimitAndReportedOnce() {
        var watch = new StuckWatch(100);
        assertFalse(watch.update(1299, 0, 1200));
        assertFalse(watch.stuck());
        assertTrue(watch.update(1300, 0, 1200), "the first time over the limit reports");
        assertTrue(watch.stuck());
        assertFalse(watch.update(1320, 0, 1200), "not again");
        assertTrue(watch.stuck(), "still stuck");
    }

    @Test
    void progressResetsTheClockButNeverReportsTwice() {
        var watch = new StuckWatch(0);
        assertFalse(watch.update(1000, 0.5, 1200));
        assertFalse(watch.update(2199, 0.5, 1200), "progress at tick 1000 moved the clock");
        assertTrue(watch.update(2200, 0.5, 1200));
        assertFalse(watch.update(2220, 0.6, 1200));
        assertFalse(watch.stuck(), "moving again");
        assertFalse(watch.update(3420, 0.6, 1200), "second stall of the same job: no second report");
        assertTrue(watch.stuck(), "but it shows");
    }

    @Test
    void fallingProgressIsNoProgress() {
        var watch = new StuckWatch(0);
        watch.update(10, 0.5, 1200);
        assertTrue(watch.update(1210, 0.2, 1200), "a network that drops out reads as 0 progress; that is no movement");
    }

    @Test
    void startingRestartsTheClock() {
        var watch = new StuckWatch(0);
        assertTrue(watch.update(1200, 0, 1200), "never started");
        watch.started(1300);
        assertFalse(watch.stuck());
        assertFalse(watch.update(2499, 0, 1200));
        assertFalse(watch.update(2500, 0, 1200), "stuck, but this job was reported already");
        assertTrue(watch.stuck());
    }

    @Test
    void limitZeroSwitchesItOff() {
        var watch = new StuckWatch(0);
        assertFalse(watch.update(1_000_000, 0, 0));
        assertFalse(watch.stuck());
    }

    @Test
    void reasonsHavePlainWords() {
        assertEquals("no progress", StuckWatch.text(null));
        assertEquals("no free crafting CPU", StuckWatch.text("no_cpu"));
        assertEquals("not enough items", StuckWatch.text("not_enough_items"));
    }
}
