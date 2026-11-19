package ai.ezfox.cccloud.code.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

/** One player cannot take every WebAssembly thread of the server; the share comes back when threads end. */
class QaThreadShareTest {
    @Test
    void ownerShareIsBoundedAndReleased() throws Exception {
        try (var scheduler = new ScriptScheduler(1)) {
            var release = new CountDownLatch(1);
            Runnable hold = () -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            var player = UUID.randomUUID();
            for (int i = 0; i < ScriptScheduler.MAX_THREADS_PER_OWNER; i++) assertNull(scheduler.startThread(hold, player), "thread " + i);
            assertEquals(ScriptScheduler.Refusal.OWNER_FULL, scheduler.startThread(hold, player));
            assertEquals(ScriptScheduler.Refusal.OWNER_FULL, scheduler.startThread(hold, UUID.fromString(player.toString())), "an equal UUID is the same player");
            assertNull(scheduler.startThread(hold, UUID.randomUUID()), "another player still gets one");
            release.countDown();
            long end = System.nanoTime() + 5_000_000_000L;
            while (scheduler.threadCount() > 0 && System.nanoTime() < end) Thread.sleep(10);
            assertEquals(0, scheduler.threadCount(player));
            assertNull(scheduler.startThread(() -> {
            }, player), "the share came back");
        }
    }
}
