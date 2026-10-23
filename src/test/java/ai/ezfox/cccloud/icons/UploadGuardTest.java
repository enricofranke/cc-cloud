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
}
