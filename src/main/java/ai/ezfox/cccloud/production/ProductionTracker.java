package ai.ezfox.cccloud.production;

import ai.ezfox.cccloud.bridge.ItemIndex;
import ai.ezfox.cccloud.bridge.ItemRow;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Production statistics of one storage network (docs/SPEC.md v0.10): rates from the stock deltas between two samples
 * of the bridge's item index, plus the items of finished crafting jobs. Pure Java, unit tested.
 * <p>
 * Bounded memory: the previous index snapshot (an immutable object the bridge built anyway; capped at 50 000 types),
 * rings of {@code window} values for the storage totals, and at most {@code maxTracked} items with their own rings – the
 * items that moved most in the window win, items without movement for a whole window are dropped. A delta is the net
 * change between two samples: what was gained and taken in the same interval cancels out.
 */
public final class ProductionTracker {
    public static final int TOP = 10;

    private final int window;
    private final int maxTracked;
    private @Nullable ItemIndex previous;
    private long lastSample;
    private long since;
    /** Minutes covered by each sample (the time since the one before). */
    private final RateRing minutes;
    private final RateRing gained;
    private final RateRing consumed;
    private final RateRing crafted;
    private final Map<String, Tracked> items = new LinkedHashMap<>();

    private static final class Tracked {
        final RateRing deltas;
        final RateRing crafted;
        String displayName;

        Tracked(int window, String displayName) {
            this.deltas = new RateRing(window);
            this.crafted = new RateRing(window);
            this.displayName = displayName;
        }

        double score() {
            return deltas.activity() + crafted.sum();
        }
    }

    public ProductionTracker(int window, int maxTracked) {
        this.window = window;
        this.maxTracked = maxTracked;
        this.minutes = new RateRing(window);
        this.gained = new RateRing(window);
        this.consumed = new RateRing(window);
        this.crafted = new RateRing(window);
    }

    /** The storage went offline: the next sample only records (no fake deltas from a network that is still booting). */
    public void pause() {
        previous = null;
    }

    public int samples() {
        return minutes.size();
    }

    /** Time of the first sample with a rate (ms), or 0. */
    public long since() {
        return since;
    }

    public int tracked() {
        return items.size();
    }

    /**
     * Takes one sample.
     *
     * @param craftedNow items of crafting jobs that finished since the last sample (name → count)
     * @return false if this was the first sample (it only records the stock)
     */
    public boolean sample(ItemIndex index, Map<String, Long> craftedNow, long now) {
        var before = previous;
        long last = lastSample;
        if (before != null && now <= last) return false; // the clock went backwards: keep the older sample
        if (index.size() == 0) {
            // An empty index looks like a network that is still booting (cells not mounted): never a baseline.
            previous = null;
            return false;
        }
        previous = index;
        lastSample = now;
        if (before == null) return false;
        if (since == 0) since = now;
        // A cut-off index (more than 50 000 types) does not say anything about the items it left out.
        boolean partial = before.truncated() || index.truncated();
        var deltas = new HashMap<String, Double>();
        var names = new HashMap<String, String>();
        for (var row : index.rows()) {
            var old = before.get(row.name());
            if (old == null && partial) continue;
            long d = row.count() - (old == null ? 0 : old.count());
            if (d != 0) deltas.put(row.name(), (double) d);
            names.put(row.name(), row.displayName());
        }
        if (!partial) {
            for (var row : before.rows()) {
                if (index.get(row.name()) != null || row.count() == 0) continue;
                deltas.put(row.name(), (double) -row.count());
                names.put(row.name(), row.displayName());
            }
        }
        double plus = 0, minus = 0, made = 0;
        for (double d : deltas.values()) {
            if (d > 0) plus += d;
            else minus -= d;
        }
        for (long c : craftedNow.values()) made += c;
        minutes.add((now - last) / 60_000.0);
        gained.add(plus);
        consumed.add(minus);
        crafted.add(made);
        track(deltas, craftedNow, names);
        return true;
    }

    /** Pushes this sample into the tracked items and picks the ones that move most. */
    private void track(Map<String, Double> deltas, Map<String, Long> craftedNow, Map<String, String> names) {
        for (var e : items.entrySet()) {
            e.getValue().deltas.add(deltas.getOrDefault(e.getKey(), 0.0));
            e.getValue().crafted.add(craftedNow.getOrDefault(e.getKey(), 0L));
        }
        var candidates = new ArrayList<String>();
        for (var name : deltas.keySet()) if (!items.containsKey(name)) candidates.add(name);
        for (var name : craftedNow.keySet()) if (!items.containsKey(name) && !deltas.containsKey(name)) candidates.add(name);
        for (var name : candidates) {
            var t = new Tracked(window, names.getOrDefault(name, name));
            t.deltas.add(deltas.getOrDefault(name, 0.0));
            t.crafted.add(craftedNow.getOrDefault(name, 0L));
            items.put(name, t);
        }
        items.values().removeIf(t -> t.score() == 0);
        if (items.size() <= maxTracked) return;
        var ranked = new ArrayList<>(items.entrySet());
        ranked.sort(Comparator.comparingDouble((Map.Entry<String, Tracked> e) -> e.getValue().score()).reversed());
        items.clear();
        for (int i = 0; i < maxTracked; i++) items.put(ranked.get(i).getKey(), ranked.get(i).getValue());
    }

    /** Minutes covered by the last {@code n} samples. */
    private double lastMinutes(int n) {
        double m = 0;
        for (int i = Math.max(0, minutes.size() - n); i < minutes.size(); i++) m += minutes.get(i);
        return m;
    }

    private static double perMinute(double amount, double minutes) {
        return minutes <= 0 ? 0 : RateRing.round(amount / minutes);
    }

    /** Storage totals: {@code gained, consumed, net, crafted} per minute, {@code samples}, {@code series}, top lists. */
    public JsonObject overview() {
        var json = new JsonObject();
        double m = lastMinutes(minutes.size());
        json.addProperty("samples", minutes.size());
        json.addProperty("gained", perMinute(gained.sum(), m));
        json.addProperty("consumed", perMinute(consumed.sum(), m));
        json.addProperty("net", perMinute(gained.sum() - consumed.sum(), m));
        json.addProperty("crafted", perMinute(crafted.sum(), m));
        var series = new JsonObject();
        series.add("gained", gained.perMinute(minutes));
        series.add("consumed", consumed.perMinute(minutes));
        series.add("crafted", crafted.perMinute(minutes));
        json.add("series", series);
        json.add("producers", top(true));
        json.add("consumers", top(false));
        return json;
    }

    private JsonArray top(boolean producers) {
        var list = new ArrayList<Map.Entry<String, Tracked>>();
        for (var e : items.entrySet()) if ((producers ? e.getValue().deltas.positive() : e.getValue().deltas.negative()) > 0) list.add(e);
        list.sort(Comparator.comparingDouble((Map.Entry<String, Tracked> e) -> producers ? e.getValue().deltas.positive() : e.getValue().deltas.negative())
            .reversed().thenComparing(Map.Entry::getKey));
        var array = new JsonArray();
        for (var e : list.subList(0, Math.min(TOP, list.size()))) {
            var t = e.getValue();
            var row = new JsonObject();
            row.addProperty("name", e.getKey());
            row.addProperty("displayName", t.displayName);
            row.addProperty("rate", perMinute(producers ? t.deltas.positive() : t.deltas.negative(), lastMinutes(t.deltas.size())));
            array.add(row);
        }
        return array;
    }

    /**
     * One item ("what is eating my iron"): {@code gained, consumed, net, crafted} per minute and the per-sample series.
     * An item without movement in the window answers zeros.
     */
    public JsonObject item(String name) {
        var json = new JsonObject();
        var t = items.get(name);
        double m = t == null ? 0 : lastMinutes(t.deltas.size());
        json.addProperty("gained", t == null ? 0 : perMinute(t.deltas.positive(), m));
        json.addProperty("consumed", t == null ? 0 : perMinute(t.deltas.negative(), m));
        json.addProperty("net", t == null ? 0 : perMinute(t.deltas.sum(), m));
        json.addProperty("crafted", t == null ? 0 : perMinute(t.crafted.sum(), m));
        var series = new JsonObject();
        var net = t == null ? new JsonArray() : t.deltas.perMinute(minutes);
        series.add("net", net);
        var plus = new JsonArray();
        var minus = new JsonArray();
        for (var v : net) {
            double d = v.getAsDouble();
            plus.add(Math.max(0, d));
            minus.add(Math.max(0, -d));
        }
        series.add("gained", plus);
        series.add("consumed", minus);
        json.add("series", series);
        return json;
    }

    /** Net rate per minute of an item over the window (pinned charts). */
    public double itemRate(String name) {
        var t = items.get(name);
        return t == null ? 0 : perMinute(t.deltas.sum(), lastMinutes(t.deltas.size()));
    }

    /** Net rate per minute of the whole storage over the last sample (pinned charts). */
    public double lastNetRate() {
        if (minutes.size() == 0) return 0;
        int i = minutes.size() - 1;
        return perMinute(gained.get(i) - consumed.get(i), minutes.get(i));
    }

    /** Net rate per minute of an item in the last sample (pinned charts). */
    public double lastItemRate(String name) {
        var t = items.get(name);
        if (t == null || t.deltas.size() == 0 || minutes.size() == 0) return 0;
        return perMinute(t.deltas.get(t.deltas.size() - 1), minutes.get(minutes.size() - 1));
    }

    /** The current stock of an item (from the last sample), or null. */
    public @Nullable ItemRow row(String name) {
        return previous == null ? null : previous.get(name);
    }

    /** Names of the tracked items (tests). */
    public List<String> trackedNames() {
        return List.copyOf(items.keySet());
    }
}
