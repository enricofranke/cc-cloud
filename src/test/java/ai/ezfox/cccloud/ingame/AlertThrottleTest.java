package ai.ezfox.cccloud.ingame;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AlertThrottleTest {
    @Test
    void burstThenHeldBackThenMentioned() {
        var clock = new AtomicLong();
        var throttle = new AlertThrottle(clock::get);
        for (int i = 0; i < AlertThrottle.BURST; i++) assertEquals(0, throttle.offer());
        assertEquals(-1, throttle.offer());
        assertEquals(-1, throttle.offer());
        assertEquals(0, throttle.summary(), "no token yet");
        assertFalse(throttle.idle());
        clock.addAndGet(4_000_000_000L);
        assertEquals(2, throttle.offer(), "the next alert mentions the two held back");
        assertTrue(throttle.idle());
        assertEquals(-1, throttle.offer());
        clock.addAndGet(4_000_000_000L);
        assertEquals(1, throttle.summary());
        assertEquals(0, throttle.summary());
    }
}
