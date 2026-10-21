package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LimitsTest {
    @Test
    void tokenBucketAllowsTenActionsPerSecond() {
        var nanos = new AtomicLong();
        var bucket = new TokenBucket(10, 10, nanos::get);
        for (int i = 0; i < 10; i++) assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire(), "11th action within the same instant is rejected");
        nanos.addAndGet(100_000_000L); // 100 ms -> one token
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
        nanos.addAndGet(10_000_000_000L);
        for (int i = 0; i < 10; i++) assertTrue(bucket.tryAcquire(), "capacity is capped at 10");
        assertFalse(bucket.tryAcquire());
    }

    @Test
    void fiveWrongPairAttemptsPerMinute() {
        var millis = new AtomicLong();
        var limiter = new FailureLimiter(5, 60_000, millis::get);
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("1.2.3.4");
            assertFalse(limiter.isBlocked("1.2.3.4"));
        }
        limiter.recordFailure("1.2.3.4");
        assertTrue(limiter.isBlocked("1.2.3.4"));
        assertFalse(limiter.isBlocked("5.6.7.8"), "per IP");
        millis.addAndGet(60_001);
        assertFalse(limiter.isBlocked("1.2.3.4"), "window slides");
    }

    @Test
    void staticPathsAreSanitized() {
        assertTrue(StaticFiles.isSafe("/assets/index-abc123.js"));
        assertFalse(StaticFiles.isSafe("/../secret"));
        assertFalse(StaticFiles.isSafe("/a\\b"));
        assertFalse(StaticFiles.isSafe("//etc/passwd"));
        assertFalse(StaticFiles.isSafe("/" + "a".repeat(300)));
        assertFalse(StaticFiles.isSafe("/a%2e%2e"));
    }
}
