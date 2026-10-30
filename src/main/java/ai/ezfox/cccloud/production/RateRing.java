package ai.ezfox.cccloud.production;

import com.google.gson.JsonArray;

/**
 * A fixed-size ring of numbers, one per sample (v0.10 production statistics): oldest values are overwritten, so the
 * memory of a series never grows. Pure Java, not thread-safe.
 */
public final class RateRing {
    private final double[] values;
    private int count;
    private int head; // index of the oldest value

    public RateRing(int capacity) {
        this.values = new double[Math.max(1, capacity)];
    }

    public void add(double value) {
        if (count < values.length) {
            values[(head + count++) % values.length] = value;
        } else {
            values[head] = value;
            head = (head + 1) % values.length;
        }
    }

    public int size() {
        return count;
    }

    public int capacity() {
        return values.length;
    }

    /** Oldest first. */
    public double get(int i) {
        return values[(head + i) % values.length];
    }

    public double sum() {
        double sum = 0;
        for (int i = 0; i < count; i++) sum += get(i);
        return sum;
    }

    /** Sum of the positive values and of the negative values (as a positive number). */
    public double positive() {
        double sum = 0;
        for (int i = 0; i < count; i++) sum += Math.max(0, get(i));
        return sum;
    }

    public double negative() {
        double sum = 0;
        for (int i = 0; i < count; i++) sum += Math.max(0, -get(i));
        return sum;
    }

    /** Sum of the absolute values (how much this series moves). */
    public double activity() {
        double sum = 0;
        for (int i = 0; i < count; i++) sum += Math.abs(get(i));
        return sum;
    }

    /** The values (oldest first), divided by the matching entry of {@code minutes} (per-minute rates), rounded. */
    public JsonArray perMinute(RateRing minutes) {
        var array = new JsonArray();
        int offset = minutes.size() - count; // both rings get one value per sample; a younger series is shorter
        for (int i = 0; i < count; i++) {
            double m = offset + i >= 0 && offset + i < minutes.size() ? minutes.get(offset + i) : 0;
            array.add(m > 0 ? round(get(i) / m) : 0);
        }
        return array;
    }

    static double round(double v) {
        return Math.abs(v) >= 1e9 ? Math.rint(v) : Math.round(v * 100) / 100.0;
    }
}
