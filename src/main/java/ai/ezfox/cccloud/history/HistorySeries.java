package ai.ezfox.cccloud.history;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Long-term statistics of one value (docs/SPEC.md v0.5 F): samples are averaged into buckets of 1 min (kept 24 h),
 * 5 min (7 days) and 30 min (30 days). Only the tiers the series' range needs are allocated, and each tier keeps at most
 * the range: {@code 1h} = 60 one-minute points; {@code 24h} = 1440; {@code 7d} adds 2016 five-minute points;
 * {@code 30d} adds 1440 half-hour points. Gaps (no samples) are NaN. Pure Java, unit tested; not thread-safe.
 */
public final class HistorySeries {
    public static final long[] STEP = {60_000L, 300_000L, 1_800_000L};
    static final int[] FULL = {1440, 2016, 1440};

    public enum Range {
        H1("1h", 3_600_000L), H24("24h", 86_400_000L), D7("7d", 7 * 86_400_000L), D30("30d", 30 * 86_400_000L);

        public final String id;
        public final long ms;

        Range(String id, long ms) {
            this.id = id;
            this.ms = ms;
        }

        public static @Nullable Range byId(@Nullable String id) {
            for (var r : values()) if (r.id.equals(id)) return r;
            return null;
        }

        /** The longest range that fits in {@code hours} of retention (at least 1 h). */
        public static Range limit(Range wanted, int hours) {
            Range best = H1;
            for (var r : values()) if (r.ms <= hours * 3_600_000L && r.ordinal() <= wanted.ordinal()) best = r;
            return best;
        }

        /** The tier a chart of this range reads (1-min for up to 24 h). */
        int tier() {
            return switch (this) {
                case H1, H24 -> 0;
                case D7 -> 1;
                case D30 -> 2;
            };
        }

        /** Points kept per tier for this range. */
        int capacity(int tier) {
            if (tier > tier()) return 0;
            if (this == H1) return 60;
            return FULL[tier];
        }
    }

    /** One tier: a ring of bucket averages ending at {@code lastBucket}, plus the running bucket. */
    static final class Tier {
        final int capacity;
        final float[] values;
        int count;
        int head; // index of the oldest value
        long lastBucket = Long.MIN_VALUE; // bucket index of the newest stored value
        long accBucket = Long.MIN_VALUE;
        double accSum;
        int accN;

        Tier(int capacity) {
            this.capacity = capacity;
            this.values = new float[capacity];
        }

        void add(long bucket, double v) {
            if (bucket != accBucket) {
                flush();
                accBucket = bucket;
                accSum = 0;
                accN = 0;
            }
            accSum += v;
            accN++;
        }

        void flush() {
            if (accN == 0 || accBucket == Long.MIN_VALUE) return;
            push(accBucket, (float) (accSum / accN));
            accN = 0;
        }

        void push(long bucket, float value) {
            if (lastBucket != Long.MIN_VALUE && bucket <= lastBucket) return; // clock went back: ignore
            if (lastBucket != Long.MIN_VALUE) {
                long gap = bucket - lastBucket - 1;
                if (gap >= capacity) {
                    count = 0;
                    head = 0;
                } else {
                    for (long i = 0; i < gap; i++) append(Float.NaN);
                }
            }
            append(value);
            lastBucket = bucket;
        }

        private void append(float value) {
            if (count < capacity) {
                values[(head + count) % capacity] = value;
                count++;
            } else {
                values[head] = value;
                head = (head + 1) % capacity;
            }
        }

        float get(int i) {
            return values[(head + i) % capacity];
        }
    }

    private final Range range;
    private final Tier[] tiers;
    private long lastSample;

    public HistorySeries(Range range) {
        this.range = range;
        this.tiers = new Tier[3];
        for (int t = 0; t < 3; t++) {
            int cap = range.capacity(t);
            if (cap > 0) tiers[t] = new Tier(cap);
        }
    }

    public Range range() {
        return range;
    }

    public long lastSample() {
        return lastSample;
    }

    /** Bytes this series takes (for the per-mainframe cap). */
    public int bytes() {
        int b = 32;
        for (var t : tiers) if (t != null) b += t.capacity * 4 + 32;
        return b;
    }

    public static int bytes(Range range) {
        return new HistorySeries(range).bytes();
    }

    public void add(long timeMs, double value) {
        if (!Double.isFinite(value)) return;
        lastSample = timeMs;
        for (int t = 0; t < 3; t++) if (tiers[t] != null) tiers[t].add(Math.floorDiv(timeMs, STEP[t]), value);
    }

    /** A chart point: bucket start (ms) and average, NaN for gaps. */
    public record Point(long time, double value) {
    }

    /**
     * Points of {@code range} (or less) up to {@code now}, oldest first, including the running bucket. Uses the tier of
     * the requested range, limited to what this series keeps.
     */
    public List<Point> points(Range wanted, long now) {
        Range r = wanted.ordinal() <= range.ordinal() ? wanted : range;
        int t = r.tier();
        var tier = tiers[t];
        var out = new ArrayList<Point>();
        if (tier == null) return out;
        long step = STEP[t];
        long from = Math.floorDiv(now - r.ms, step) + 1;
        long first = tier.lastBucket == Long.MIN_VALUE ? Long.MAX_VALUE : tier.lastBucket - tier.count + 1;
        for (int i = 0; i < tier.count; i++) {
            long bucket = first + i;
            if (bucket >= from) out.add(new Point(bucket * step, tier.get(i)));
        }
        if (tier.accN > 0 && tier.accBucket >= from) {
            // buckets between the last stored one and the running one had no samples
            long gapFrom = tier.lastBucket == Long.MIN_VALUE ? tier.accBucket : Math.max(tier.lastBucket + 1, Math.max(from, tier.accBucket - tier.capacity));
            for (long b = gapFrom; b < tier.accBucket; b++) out.add(new Point(b * step, Double.NaN));
            out.add(new Point(tier.accBucket * step, tier.accSum / tier.accN));
        }
        return out;
    }

    public long step(Range wanted) {
        Range r = wanted.ordinal() <= range.ordinal() ? wanted : range;
        return STEP[r.tier()];
    }

    // ---- storage -------------------------------------------------------------------------------------------------------

    /** Compact storage form (base64), including the running buckets. */
    public String encode() {
        int size = 1 + 8;
        for (var t : tiers) if (t != null) size += 1 + 8 + 4 + t.count * 4 + 8 + 8 + 4;
        var buffer = ByteBuffer.allocate(size + 1);
        buffer.put((byte) range.ordinal());
        buffer.putLong(lastSample);
        for (int i = 0; i < 3; i++) {
            var t = tiers[i];
            if (t == null) continue;
            buffer.put((byte) i);
            buffer.putLong(t.lastBucket);
            buffer.putInt(t.count);
            for (int j = 0; j < t.count; j++) buffer.putFloat(t.get(j));
            buffer.putLong(t.accBucket);
            buffer.putDouble(t.accSum);
            buffer.putInt(t.accN);
        }
        buffer.put((byte) -1);
        return Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(buffer.array(), buffer.position()));
    }

    /** Null if the data is unreadable. */
    public static @Nullable HistorySeries decode(String data) {
        try {
            var buffer = ByteBuffer.wrap(Base64.getDecoder().decode(data));
            int r = buffer.get();
            if (r < 0 || r >= Range.values().length) return null;
            var series = new HistorySeries(Range.values()[r]);
            series.lastSample = buffer.getLong();
            while (true) {
                int i = buffer.get();
                if (i == -1) break;
                if (i < 0 || i > 2) return null;
                long last = buffer.getLong();
                int count = buffer.getInt();
                var t = series.tiers[i];
                if (count < 0 || count > 100_000) return null;
                var values = new float[count];
                for (int j = 0; j < count; j++) values[j] = buffer.getFloat();
                long accBucket = buffer.getLong();
                double accSum = buffer.getDouble();
                int accN = buffer.getInt();
                if (t == null) continue; // range shrank: drop the tier
                int keep = Math.min(count, t.capacity);
                for (int j = count - keep; j < count; j++) t.append(values[j]);
                t.lastBucket = last;
                t.accBucket = accBucket;
                t.accSum = accSum;
                t.accN = Math.max(0, accN);
            }
            return series;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
