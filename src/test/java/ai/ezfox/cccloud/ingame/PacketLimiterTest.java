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
}
