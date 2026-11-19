package ai.ezfox.cccloud.code.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Threads for scripts and programs that cannot suspend: 32 on the server, 8 per player, all given back. */
class ScriptSchedulerThreadsTest {
    @Test
    void onePlayerCannotTakeEveryThread() throws InterruptedException {
        try (var scheduler = new ScriptScheduler(1)) {
            var hold = new CountDownLatch(1);
            Runnable waiting = () -> {
                try {
                    hold.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            var alice = UUID.randomUUID();
            for (int i = 0; i < ScriptScheduler.MAX_THREADS_PER_OWNER; i++) assertNull(scheduler.startThread(waiting, alice));
            assertEquals(ScriptScheduler.Refusal.OWNER_FULL, scheduler.startThread(waiting, alice), "the ninth of one player");
            assertEquals(ScriptScheduler.MAX_THREADS_PER_OWNER, scheduler.threadCount(alice));
            // others still get threads, up to the server's limit
            int others = ScriptScheduler.MAX_THREADS - ScriptScheduler.MAX_THREADS_PER_OWNER;
            for (int i = 0; i < others; i++) assertNull(scheduler.startThread(waiting, UUID.randomUUID()), "player " + i);
            assertEquals(ScriptScheduler.Refusal.FULL, scheduler.startThread(waiting, UUID.randomUUID()), "the server is full");
            assertEquals(ScriptScheduler.MAX_THREADS, scheduler.threadCount());
            hold.countDown();
            long end = System.currentTimeMillis() + 5000;
            while (scheduler.threadCount() > 0 && System.currentTimeMillis() < end) Thread.sleep(10);
            assertEquals(0, scheduler.threadCount());
            assertEquals(0, scheduler.threadCount(alice), "given back");
            assertNull(scheduler.startThread(() -> {
            }, alice), "and usable again");
        }
    }
}
