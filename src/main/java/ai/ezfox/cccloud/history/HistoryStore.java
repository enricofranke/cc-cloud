package ai.ezfox.cccloud.history;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * All long-term series, per mainframe (docs/SPEC.md v0.5 F). A series' range is what the widget asked for, limited by
 * the mainframe's retention (Memory upgrade: base 1 h, 24 h / 7 d / 30 d). At most {@link #MAX_BYTES} per mainframe:
 * new series beyond the cap are not created. Series without samples for longer than their range are dropped.
 * Pure Java, unit tested; synchronized (the server thread records, the web API reads).
 */
public final class HistoryStore {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    /**
     * All mainframes together (config {@code historyMaxTotalMB}, docs/SPEC.md "Scaling"): a new series is only started
     * while the total stays below this; existing series always keep recording (their memory is fixed at creation).
     */
    private volatile long maxTotalBytes = 128L * 1024 * 1024;
    private long totalBytes;
    public static final int MAX_KEY = 240;

    private final Map<UUID, Map<String, HistorySeries>> series = new HashMap<>();
    private final Runnable onChange;

    public HistoryStore(Runnable onChange) {
        this.onChange = onChange;
    }

    /** Records a sample; false if the series cannot be created (cap reached). */
    public synchronized boolean record(UUID mainframeId, String key, HistorySeries.Range wanted, int retentionHours, long now, double value) {
        if (key.length() > MAX_KEY || !Double.isFinite(value)) return false;
        var map = series.computeIfAbsent(mainframeId, k -> new LinkedHashMap<>());
        var range = HistorySeries.Range.limit(wanted, retentionHours);
        var s = map.get(key);
        if (s != null && s.range() != range && bytes(map) - s.bytes() + HistorySeries.bytes(range) <= MAX_BYTES
            && totalBytes - s.bytes() + HistorySeries.bytes(range) <= maxTotalBytes) {
            // retention or the widget's range changed: keep the data that still fits
            var resized = HistorySeries.decode(withRange(s.encode(), range));
            totalBytes -= s.bytes();
            s = resized != null ? resized : new HistorySeries(range);
            totalBytes += s.bytes();
            map.put(key, s);
        }
        if (s == null) {
            if (bytes(map) + HistorySeries.bytes(range) > MAX_BYTES || totalBytes + HistorySeries.bytes(range) > maxTotalBytes) return false;
            s = new HistorySeries(range);
            totalBytes += s.bytes();
            map.put(key, s);
        }
        s.add(now, value);
        onChange.run();
        return true;
    }

    /** The stored form with another range byte (tiers the new range does not keep are dropped on decode). */
    private static String withRange(String encoded, HistorySeries.Range range) {
        byte[] raw = java.util.Base64.getDecoder().decode(encoded);
        raw[0] = (byte) range.ordinal();
        return java.util.Base64.getEncoder().encodeToString(raw);
    }

    private static int bytes(Map<String, HistorySeries> map) {
        int b = 0;
        for (var s : map.values()) b += s.bytes();
        return b;
    }

    /** Bytes of all series of all mainframes. */
    public synchronized long totalBytes() {
        return totalBytes;
    }

    public void setMaxTotalBytes(long bytes) {
        maxTotalBytes = Math.max(MAX_BYTES, bytes);
    }

    private void recount() {
        long t = 0;
        for (var map : series.values()) t += bytes(map);
        totalBytes = t;
    }

    public synchronized int bytes(UUID mainframeId) {
        var map = series.get(mainframeId);
        return map == null ? 0 : bytes(map);
    }

    public synchronized HistorySeries.@Nullable Range range(UUID mainframeId, String key) {
        var map = series.get(mainframeId);
        var s = map == null ? null : map.get(key);
        return s == null ? null : s.range();
    }

    public synchronized List<HistorySeries.Point> points(UUID mainframeId, String key, HistorySeries.Range range, long now) {
        var map = series.get(mainframeId);
        var s = map == null ? null : map.get(key);
        return s == null ? List.of() : s.points(range, now);
    }

    public synchronized long step(UUID mainframeId, String key, HistorySeries.Range range) {
        var map = series.get(mainframeId);
        var s = map == null ? null : map.get(key);
        return s == null ? HistorySeries.STEP[0] : s.step(range);
    }

    /** Drops series that got no sample for longer than their range. */
    public synchronized void prune(long now) {
        boolean changed = false;
        for (var map : series.values()) changed |= map.values().removeIf(s -> now - s.lastSample() > s.range().ms);
        changed |= series.values().removeIf(Map::isEmpty);
        if (changed) {
            recount();
            onChange.run();
        }
    }

    public synchronized void remove(UUID mainframeId) {
        if (series.remove(mainframeId) != null) {
            recount();
            onChange.run();
        }
    }

    public synchronized Map<UUID, Map<String, String>> save() {
        var out = new HashMap<UUID, Map<String, String>>();
        for (var e : series.entrySet()) {
            var m = new LinkedHashMap<String, String>();
            for (var s : e.getValue().entrySet()) m.put(s.getKey(), s.getValue().encode());
            out.put(e.getKey(), m);
        }
        return out;
    }

    public synchronized void load(Map<UUID, Map<String, String>> stored) {
        series.clear();
        for (var e : stored.entrySet()) {
            var m = new LinkedHashMap<String, HistorySeries>();
            for (var s : e.getValue().entrySet()) {
                var decoded = HistorySeries.decode(s.getValue());
                if (decoded != null && s.getKey().length() <= MAX_KEY) m.put(s.getKey(), decoded);
            }
            if (!m.isEmpty()) series.put(e.getKey(), m);
        }
        recount();
    }
}
