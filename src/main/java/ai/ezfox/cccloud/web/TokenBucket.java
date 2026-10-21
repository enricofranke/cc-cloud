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
        long now = nanoClock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - last) * perNano);
        last = now;
        if (tokens < 1) return false;
        tokens -= 1;
        return true;
    }
}
