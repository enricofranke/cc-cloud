package ai.ezfox.cccloud.web;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Counts failures per key (e.g. IP) in a sliding window and blocks a key once it reached the limit. */
public final class FailureLimiter {
    private final int maxFailures;
    private final long windowMs;
    private final LongSupplier clock;
    private final Map<String, ArrayDeque<Long>> failures = new ConcurrentHashMap<>();

    public FailureLimiter(int maxFailures, long windowMs, LongSupplier clock) {
        this.maxFailures = maxFailures;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        var deque = failures.get(key);
        if (deque == null) return false;
        synchronized (deque) {
            prune(deque);
            return deque.size() >= maxFailures;
        }
    }

    public void recordFailure(String key) {
        if (failures.size() > 10_000) cleanup();
        var deque = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque);
            deque.addLast(clock.getAsLong());
        }
    }

    public void cleanup() {
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue());
                return e.getValue().isEmpty();
            }
        });
    }

    private void prune(ArrayDeque<Long> deque) {
        long cutoff = clock.getAsLong() - windowMs;
        while (!deque.isEmpty() && deque.peekFirst() <= cutoff) deque.pollFirst();
    }
}
