package ai.ezfox.cccloud.icons;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Limits for client renders (item icons, block textures; security review L10): a per-player quota per hour, and
 * replacing something that is already stored for the current mod version only by players with a role on a mainframe
 * (first writer wins for everyone else). Pure Java, unit tested.
 */
public final class UploadGuard {
    public static final long WINDOW_MS = 60 * 60 * 1000L;
    private final int perHour;
    private final LongSupplier clock;
    private volatile Predicate<UUID> trusted;
    private final Map<UUID, long[]> counts = new ConcurrentHashMap<>();

    public UploadGuard(int perHour, LongSupplier clock, Predicate<UUID> trusted) {
        this.perHour = perHour;
        this.clock = clock;
        this.trusted = trusted;
    }

    public void setTrusted(Predicate<UUID> trusted) {
        this.trusted = trusted;
    }

    /** Takes one upload from the player's quota; false when it is used up. */
    public boolean take(UUID player) {
        long now = clock.getAsLong();
        var c = counts.computeIfAbsent(player, k -> new long[]{now, 0});
        synchronized (c) {
            if (now - c[0] > WINDOW_MS) {
                c[0] = now;
                c[1] = 0;
            }
            if (c[1] >= perHour) return false;
            c[1]++;
            return true;
        }
    }

    /** May the player replace an entry that already exists for the current version? */
    public boolean mayReplace(UUID player) {
        return trusted.test(player);
    }
}
