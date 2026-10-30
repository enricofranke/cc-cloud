package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Limits that change while buckets are in use (config reload): never a fresh budget, a lowered limit holds at once. */
class TokenBucketReloadTest {
    final long[] nanos = {0};

    @Test
    void raisingTheLimitGivesNothingBack() {
        var bucket = new TokenBucket(5, 1, () -> nanos[0]);
        for (int i = 0; i < 5; i++) assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire(), "used up");
        bucket.reconfigure(100, 100);
        assertFalse(bucket.tryAcquire(), "a reload does not refill a used-up bucket");
        nanos[0] += 50_000_000L; // 50 ms at the new rate of 100/s = 5 tokens
        assertEquals(5, bucket.available(), 1e-6);
        assertEquals(100, bucket.capacity());
    }

    @Test
    void loweringTheLimitHoldsAtOnce() {
        var bucket = new TokenBucket(60, 1, () -> nanos[0]);
        assertEquals(60, bucket.available(), 1e-9);
        bucket.reconfigure(2, 2 / 60.0);
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire(), "capped at the new capacity");
        nanos[0] += 30_000_000_000L; // 30 s at 2/min = 1 token
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
    }

    @Test
    void tokensEarnedBeforeTheChangeCountAtTheOldRate() {
        var bucket = new TokenBucket(10, 1, () -> nanos[0]);
        for (int i = 0; i < 10; i++) bucket.tryAcquire();
        nanos[0] += 3_000_000_000L; // 3 s at 1/s
        bucket.reconfigure(10, 0.1);
        assertEquals(3, bucket.available(), 1e-6, "earned at the old rate up to the change");
    }

    @Test
    void ensureReusesTheBucket() {
        var a = TokenBucket.ensure(null, 8, 4);
        assertEquals(8, a.capacity());
        for (int i = 0; i < 8; i++) a.tryAcquire();
        var b = TokenBucket.ensure(a, 2, 1);
        assertSame(a, b, "the same bucket, so a device keeps what it used");
        assertFalse(b.tryAcquire());
        assertSame(b, TokenBucket.ensure(b, 2, 1), "no-op when unchanged");
    }
}
