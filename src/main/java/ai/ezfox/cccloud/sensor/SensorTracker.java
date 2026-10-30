package ai.ezfox.cccloud.sensor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who is inside an Advanced Cloud Sensor's zone, from one scan to the next (docs/SPEC.md "Advanced Cloud Sensor"),
 * and which of the changes are reported as "entered" / "left" events. Pure Java, unit tested.
 * <ul>
 *   <li><b>Leaving</b>: an entity counts as gone after it was missing in {@link #LEAVE_AFTER_MISSED} scans in a row, so
 *       a mob at the edge of the zone does not flicker in and out.</li>
 *   <li><b>Cooldown per entity</b>: an entity that comes back within the cooldown after its last reported "entered" is
 *       inside again but silent: no "entered" and, when it leaves again, no "left". A mob walking in and out produces one
 *       pair of events per cooldown.</li>
 *   <li><b>Baseline</b>: the first scan after loading, arming or changing the zone or the filter only records who is
 *       there (silently), so a restart or a new setting does not report everyone again.</li>
 *   <li><b>Bounded</b>: at most {@link #MAX_INSIDE} entities are tracked (more are ignored until a place is free) and
 *       the cooldown remembers at most {@link #MAX_REMEMBERED} entities (oldest forgotten first).</li>
 * </ul>
 */
public final class SensorTracker {
    public static final int MAX_INSIDE = 64;
    public static final int MAX_REMEMBERED = 256;
    public static final int LEAVE_AFTER_MISSED = 2;

    /** One entity found in the zone by a scan (already filtered). */
    public record Seen(UUID id, SensorEntry entry) {
    }

    /** A reported change: {@code entered} or left. */
    public record Change(boolean entered, SensorEntry entry) {
    }

    private record Presence(SensorEntry entry, boolean silent, int missed) {
    }

    private final LinkedHashMap<UUID, Presence> inside = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Long> lastEntered = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Long> eldest) {
            return size() > MAX_REMEMBERED;
        }
    };
    private boolean baseline = true;

    /** Everything found by the next scan is recorded without events (after loading, arming, a new zone or filter). */
    public void rebase() {
        inside.clear();
        baseline = true;
    }

    /**
     * One scan.
     *
     * @param seen       what is in the zone now and passes the filter (at most what the caller bounded it to)
     * @param now        the current time (ms)
     * @param cooldownMs per-entity cooldown
     * @return the reported changes, in order
     */
    public List<Change> scan(List<Seen> seen, long now, long cooldownMs) {
        var changes = new ArrayList<Change>();
        var present = new HashSet<UUID>();
        boolean quiet = baseline;
        baseline = false;
        for (var s : seen) {
            if (!present.add(s.id())) continue;
            var p = inside.get(s.id());
            if (p != null) {
                // still there (a renamed mob or a player whose name became known keeps its place)
                inside.put(s.id(), new Presence(s.entry(), p.silent(), 0));
                continue;
            }
            if (inside.size() >= MAX_INSIDE) continue;
            Long last = lastEntered.get(s.id());
            boolean silent = quiet || last != null && now - last < cooldownMs;
            inside.put(s.id(), new Presence(s.entry(), silent, 0));
            if (!silent) {
                lastEntered.put(s.id(), now);
                changes.add(new Change(true, s.entry()));
            }
        }
        for (var it = inside.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (present.contains(e.getKey())) continue;
            var p = e.getValue();
            if (p.missed() + 1 < LEAVE_AFTER_MISSED) {
                e.setValue(new Presence(p.entry(), p.silent(), p.missed() + 1));
                continue;
            }
            it.remove();
            if (!p.silent()) changes.add(new Change(false, p.entry()));
        }
        return changes;
    }

    /** Who is inside now (reported or not), in the order they came in. */
    public List<SensorEntry> inside() {
        var list = new ArrayList<SensorEntry>(inside.size());
        for (var p : inside.values()) list.add(p.entry());
        return list;
    }

    public boolean occupied() {
        return !inside.isEmpty();
    }

    public int size() {
        return inside.size();
    }

    /** Entities the per-entity cooldown remembers (tests). */
    int remembered() {
        return lastEntered.size();
    }
}
