package ai.ezfox.cccloud.web;

import java.util.function.LongSupplier;

/** A simple thread-safe token bucket: {@code capacity} tokens, refilled at {@code perSecond}. */
public final class TokenBucket {
    private final double capacity;
    private final double perNano;
    private final LongSupplier nanoClock;
    private double tokens;
    private long last;

    public TokenBucket(double capacity, double perSecond, LongSupplier nanoClock) {
        this.capacity = capacity;
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

    private void refill() {
        long now = nanoClock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - last) * perNano);
        last = now;
    }
}
