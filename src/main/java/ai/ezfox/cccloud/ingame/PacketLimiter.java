package ai.ezfox.cccloud.ingame;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * At most one packet of a kind per player and interval (security review M6: map waypoint subscriptions and opening the
 * tablet build whole snapshots). Pure Java, unit tested.
 */
public final class PacketLimiter {
    private final long intervalMs;
    private final Map<String, Long> last = new ConcurrentHashMap<>();

    public PacketLimiter(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public boolean allow(UUID player, String kind, long nowMs) {
        String key = player + "|" + kind;
        Long before = last.get(key);
        if (before != null && nowMs - before < intervalMs) return false;
        last.put(key, nowMs);
        if (last.size() > 10_000) last.clear();
        return true;
    }

    public void forget(UUID player) {
        last.keySet().removeIf(k -> k.startsWith(player + "|"));
    }

    /** Shared limiter for in-game packets: 1 per second per kind. */
    public static final PacketLimiter IN_GAME = new PacketLimiter(1000);

    /**
     * Screen actions (mainframe, uplink and redstone device screens) per player: bursts of {@code burst}, then
     * {@code perSecond}. Each action re-checks rights, may write a log line and sends the screen's view again, so a
     * modified client must not be able to send them without limit (security review 2026-10-30). Pure Java, unit tested.
     */
    public static final class Budget {
        private final double burst;
        private final double perSecond;
        private final java.util.function.LongSupplier nanoClock;
        private final Map<UUID, ai.ezfox.cccloud.web.TokenBucket> buckets = new ConcurrentHashMap<>();

        public Budget(double burst, double perSecond, java.util.function.LongSupplier nanoClock) {
            this.burst = burst;
            this.perSecond = perSecond;
            this.nanoClock = nanoClock;
        }

        public boolean allow(UUID player) {
            if (buckets.size() > 10_000) buckets.clear();
            return buckets.computeIfAbsent(player, k -> new ai.ezfox.cccloud.web.TokenBucket(burst, perSecond, nanoClock)).tryAcquire();
        }
    }

    /** Screen actions: 20 at once, then 10 per second per player (far more than clicking can produce). */
    public static final Budget SCREEN_ACTIONS = new Budget(20, 10, System::nanoTime);
}
