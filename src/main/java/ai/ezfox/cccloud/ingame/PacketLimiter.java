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
}
