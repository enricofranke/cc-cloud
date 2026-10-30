package ai.ezfox.cccloud.sensor;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Rate limit of one Advanced Cloud Sensor's alerts and push messages (docs/SPEC.md "Advanced Cloud Sensor"): the
 * first entries go out at once ("Steve entered …"); everything that enters during the following cooldown is collected
 * and sent as one summary when the cooldown is over ("3 Zombies and a Skeleton entered …"), which starts the next
 * cooldown. So a sensor sends at most one alert per cooldown, however many entities come and go. The collection is
 * bounded ({@link #MAX_PENDING} entries; more are counted only). Pure Java, unit tested.
 */
public final class SensorAlerts {
    public static final int MAX_PENDING = 64;

    private long windowEnd;
    private final List<SensorEntry> pending = new ArrayList<>();
    private int overflow;

    /** What to send: the entries, plus how many more entered than are listed. */
    public record Alert(List<SensorEntry> entries, int more) {
    }

    /**
     * Entities were reported entering (one scan). Returns the alert to send now, or null when it is collected for the
     * summary at the end of the cooldown.
     */
    public @Nullable Alert entered(List<SensorEntry> entries, long now, long cooldownMs) {
        if (entries.isEmpty()) return null;
        if (now >= windowEnd && pending.isEmpty()) {
            windowEnd = now + cooldownMs;
            return new Alert(List.copyOf(entries), 0);
        }
        for (var e : entries) {
            if (pending.size() < MAX_PENDING) pending.add(e);
            else overflow++;
        }
        return null;
    }

    /** The summary of what entered during the cooldown, once it is over (then the next cooldown starts); else null. */
    public @Nullable Alert due(long now, long cooldownMs) {
        if (pending.isEmpty() || now < windowEnd) return null;
        var alert = new Alert(List.copyOf(pending), overflow);
        pending.clear();
        overflow = 0;
        windowEnd = now + cooldownMs;
        return alert;
    }

    /** Disarmed or reset: nothing collected is sent any more. */
    public void clear() {
        pending.clear();
        overflow = 0;
    }

    public int pending() {
        return pending.size() + overflow;
    }
}
