package ai.ezfox.cccloud.computer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The input rate limit of one computer, per source (a player or a browser session): a token bucket of
 * {@link #BURST} inputs refilled at {@link #PER_SECOND} a second. Remembers at most {@link #MAX_SOURCES} sources (the
 * least recently used goes). Thread-safe.
 */
final class InputLimiter {
    static final int PER_SECOND = 60;
    static final int BURST = 120;
    static final int MAX_SOURCES = 32;

    private final LongSupplier nanos;
    private final LinkedHashMap<String, Bucket> buckets = new LinkedHashMap<>(16, 0.75f, true);

    InputLimiter(LongSupplier nanos) {
        this.nanos = nanos;
    }

    synchronized boolean allow(String source) {
        long now = nanos.getAsLong();
        Bucket b = buckets.get(source);
        if (b == null) {
            if (buckets.size() >= MAX_SOURCES) {
                Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
                it.next();
                it.remove();
            }
            b = new Bucket();
            b.tokens = BURST;
            b.at = now;
            buckets.put(source, b);
        }
        b.tokens = Math.min(BURST, b.tokens + Math.max(0, now - b.at) / 1e9 * PER_SECOND);
        b.at = now;
        if (b.tokens < 1) return false;
        b.tokens -= 1;
        return true;
    }

    private static final class Bucket {
        double tokens;
        long at;
    }
}
