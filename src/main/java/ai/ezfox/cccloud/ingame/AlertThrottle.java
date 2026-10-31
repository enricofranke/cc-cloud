package ai.ezfox.cccloud.ingame;

import ai.ezfox.cccloud.web.TokenBucket;
import java.util.function.LongSupplier;

/**
 * Rate limit of the alert toasts of one player: a burst, then one every few seconds. What is held back is counted and
 * mentioned with the next toast (it stays in the tablet's Alerts tab anyway). Pure Java, unit tested.
 */
public final class AlertThrottle {
    public static final int BURST = 3;
    public static final double PER_SECOND = 0.25;

    private final TokenBucket bucket;
    private int heldBack;

    public AlertThrottle(LongSupplier nanoClock) {
        bucket = new TokenBucket(BURST, PER_SECOND, nanoClock);
    }

    /** An alert: -1 = held back; otherwise show it and mention this many held-back ones. */
    public int offer() {
        if (!bucket.tryAcquire()) {
            heldBack++;
            return -1;
        }
        int n = heldBack;
        heldBack = 0;
        return n;
    }

    /** No alert came: the summary of the held-back ones if a toast is allowed again, else 0. */
    public int summary() {
        if (heldBack == 0 || !bucket.tryAcquire()) return 0;
        int n = heldBack;
        heldBack = 0;
        return n;
    }

    public boolean idle() {
        return heldBack == 0;
    }
}
