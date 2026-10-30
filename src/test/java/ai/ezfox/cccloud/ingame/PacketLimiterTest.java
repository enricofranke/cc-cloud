package ai.ezfox.cccloud.ingame;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PacketLimiterTest {
    @Test
    void onePerSecondPerKindAndPlayer() {
        var limiter = new PacketLimiter(1000);
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        assertTrue(limiter.allow(a, "tablet_open", 0));
        assertFalse(limiter.allow(a, "tablet_open", 500), "within the second");
        assertTrue(limiter.allow(a, "map_nodes", 500), "other kind");
        assertTrue(limiter.allow(b, "tablet_open", 500), "other player");
        assertTrue(limiter.allow(a, "tablet_open", 1000));
        limiter.forget(a);
        assertTrue(limiter.allow(a, "tablet_open", 1001));
    }

    /** Security review 2026-10-30: screen action packets are limited per player (bursts of 20, then 10 per second). */
    @Test
    void screenActionBudget() {
        long[] now = {0};
        var budget = new PacketLimiter.Budget(20, 10, () -> now[0]);
        var a = UUID.randomUUID();
        for (int i = 0; i < 20; i++) assertTrue(budget.allow(a), "burst " + i);
        assertFalse(budget.allow(a), "a flood is cut off");
        assertTrue(budget.allow(UUID.randomUUID()), "other players are not affected");
        now[0] += 100_000_000L; // 0.1 s: one more
        assertTrue(budget.allow(a));
        assertFalse(budget.allow(a));
    }
}
