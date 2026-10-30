package ai.ezfox.cccloud.icons;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Security review L10: upload quota per player and hour, replacing only by trusted players. */
class UploadGuardTest {
    @Test
    void quotaAndReplace() {
        var now = new AtomicLong(0);
        var trusted = UUID.randomUUID();
        var guard = new UploadGuard(3, now::get, trusted::equals);
        var p = UUID.randomUUID();
        assertTrue(guard.take(p));
        assertTrue(guard.take(p));
        assertTrue(guard.take(p));
        assertFalse(guard.take(p), "quota used up");
        assertTrue(guard.take(UUID.randomUUID()), "per player");
        now.addAndGet(UploadGuard.WINDOW_MS + 1);
        assertTrue(guard.take(p), "a new hour");
        assertFalse(guard.mayReplace(p));
        assertTrue(guard.mayReplace(trusted));
    }

    @Test
    void remainingFollowsTheQuotaAndTheHour() {
        var now = new AtomicLong(0);
        var guard = new UploadGuard(3, now::get, uuid -> false);
        var p = UUID.randomUUID();
        assertEquals(3, guard.remaining(p));
        guard.take(p);
        assertEquals(2, guard.remaining(p));
        guard.setPerHour(10);
        assertEquals(9, guard.remaining(p), "a bigger quota counts what was used");
        guard.setPerHour(1);
        assertEquals(0, guard.remaining(p), "never negative");
        now.addAndGet(UploadGuard.WINDOW_MS + 1);
        assertEquals(1, guard.remaining(p), "a new hour");
    }
}
