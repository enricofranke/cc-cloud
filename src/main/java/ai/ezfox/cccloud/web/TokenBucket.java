package ai.ezfox.cccloud.web;

import java.util.function.LongSupplier;

/**
 * A simple thread-safe token bucket: {@code capacity} tokens, refilled at {@code perSecond}. The limits can change while
 * the bucket is in use ({@link #reconfigure}, config reload): what was used stays used.
 */
public final class TokenBucket {
    private double capacity;
    private double perSecond;
    private double perNano;
    private final LongSupplier nanoClock;
    private double tokens;
    private long last;

    public TokenBucket(double capacity, double perSecond, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.perSecond = perSecond;
        this.perNano = perSecond / 1e9;
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.last = nanoClock.getAsLong();
    }

    public TokenBucket(double capacity, double perSecond) {
        this(capacity, perSecond, System::nanoTime);
    }

    public synchronized boolean tryAcquire() {
        return tryAcquire(1);
    }

    /** Takes {@code n} tokens at once, or none. */
    public synchronized boolean tryAcquire(double n) {
        refill();
        if (tokens < n) return false;
        tokens -= n;
        return true;
    }

    /** Seconds until {@code n} tokens are available (0 if they are now; more than capacity never). */
    public synchronized double secondsUntil(double n) {
        refill();
        if (tokens >= n) return 0;
        return (Math.min(n, capacity) - tokens) / (perNano * 1e9);
    }

    public synchronized double capacity() {
        return capacity;
    }

    /** Tokens available right now. */
    public synchronized double available() {
        refill();
        return tokens;
    }

    /**
     * New limits for a bucket in use (config reload). Tokens earned so far count at the old rate; what is left is capped
     * at the new capacity and never topped up, so a client that used up its budget gets nothing back from a reload, and a
     * lowered limit holds at once. A no-op when the limits are unchanged.
     */
    public synchronized void reconfigure(double capacity, double perSecond) {
        if (capacity == this.capacity && perSecond == this.perSecond) return;
        refill();
        this.capacity = capacity;
        this.perSecond = perSecond;
        this.perNano = perSecond / 1e9;
        tokens = Math.min(tokens, capacity);
    }

    /** {@code bucket} with the given limits, or a new full one if there is none yet. */
    public static TokenBucket ensure(@org.jspecify.annotations.Nullable TokenBucket bucket, double capacity, double perSecond) {
        if (bucket == null) return new TokenBucket(capacity, perSecond);
        bucket.reconfigure(capacity, perSecond);
        return bucket;
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - last) * perNano);
        last = now;
    }
}
