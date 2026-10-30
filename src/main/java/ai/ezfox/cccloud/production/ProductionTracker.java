package ai.ezfox.cccloud.production;

import ai.ezfox.cccloud.bridge.ItemIndex;
import ai.ezfox.cccloud.bridge.ItemRow;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
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
 * items that moved most in the window win, items without movement for a whole window are dropped. New items are picked
 * with a heap of at most {@code maxTracked} entries, so a sample costs memory for that many whatever the number of
 * changed items. A delta is the net change between two samples: what was gained and taken in the same interval cancels
 * out.
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
    private int created;

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
        // One pass over the changes: the totals, and the items not tracked yet that moved most in a heap of at most
        // maxTracked entries. Nothing is allocated per changed item (a stock change of 50 000 types used to build an
        // entry with two rings for each before trimming).
        var candidates = new TopCandidates(maxTracked);
        double plus = 0, minus = 0, made = 0;
        for (var row : index.rows()) {
            var old = before.get(row.name());
            if (old == null && partial) continue;
            long d = row.count() - (old == null ? 0 : old.count());
            if (d == 0) continue;
            if (d > 0) plus += d;
            else minus -= d;
            if (!items.containsKey(row.name())) candidates.offer(row.name(), row.displayName(), Math.abs(d) + craftedNow.getOrDefault(row.name(), 0L));
        }
        if (!partial) {
            for (var row : before.rows()) {
                if (index.get(row.name()) != null || row.count() == 0) continue;
                minus += row.count();
                if (!items.containsKey(row.name())) candidates.offer(row.name(), row.displayName(), row.count() + craftedNow.getOrDefault(row.name(), 0L));
            }
        }
        for (var e : craftedNow.entrySet()) {
            made += e.getValue();
            // crafted without a stock change (the items went straight out again): tracked for what was crafted
            if (items.containsKey(e.getKey()) || delta(before, index, e.getKey(), partial) != 0) continue;
            var row = index.get(e.getKey());
            candidates.offer(e.getKey(), row == null ? e.getKey() : row.displayName(), e.getValue());
        }
        minutes.add((now - last) / 60_000.0);
        gained.add(plus);
        consumed.add(minus);
        crafted.add(made);
        track(before, index, partial, craftedNow, candidates);
        return true;
    }

    /** The stock change of one item between two samples (0 where a cut-off index cannot tell). */
    private static double delta(ItemIndex before, ItemIndex index, String name, boolean partial) {
        var row = index.get(name);
        var old = before.get(name);
        if (row != null) return old == null && partial ? 0 : row.count() - (old == null ? 0 : old.count());
        return partial || old == null ? 0 : -old.count();
    }

    /**
     * Pushes this sample into the tracked items, drops the idle ones and adds the new items that moved most, keeping at
     * most {@code maxTracked} (the ones with the most movement in the window; on a tie the tracked item stays).
     */
    private void track(ItemIndex before, ItemIndex index, boolean partial, Map<String, Long> craftedNow, TopCandidates candidates) {
        for (var e : items.entrySet()) {
            e.getValue().deltas.add(delta(before, index, e.getKey(), partial));
            e.getValue().crafted.add(craftedNow.getOrDefault(e.getKey(), 0L));
        }
        items.values().removeIf(t -> t.score() == 0);
        var fresh = candidates.best();
        if (fresh.isEmpty()) return;
        if (items.size() + fresh.size() <= maxTracked) {
            for (var c : fresh) add(c, before, index, partial, craftedNow);
            return;
        }
        // rank the tracked items and the candidates together (at most twice maxTracked entries)
        var ranked = new ArrayList<Object[]>(items.size() + fresh.size());
        for (var e : items.entrySet()) ranked.add(new Object[]{e.getValue().score(), e.getKey(), e.getValue()});
        for (var c : fresh) ranked.add(new Object[]{c.score(), c.name(), c});
        ranked.sort(Comparator.comparingDouble((Object[] r) -> (double) r[0]).reversed()); // stable: tracked items first on a tie
        var kept = new LinkedHashMap<String, Tracked>();
        for (int i = 0; i < maxTracked; i++) {
            var r = ranked.get(i);
            kept.put((String) r[1], r[2] instanceof Tracked t ? t : create((Candidate) r[2], before, index, partial, craftedNow));
        }
        items.clear();
        items.putAll(kept);
    }

    private void add(Candidate c, ItemIndex before, ItemIndex index, boolean partial, Map<String, Long> craftedNow) {
        items.put(c.name(), create(c, before, index, partial, craftedNow));
    }

    private Tracked create(Candidate c, ItemIndex before, ItemIndex index, boolean partial, Map<String, Long> craftedNow) {
        created++;
        var t = new Tracked(window, c.displayName());
        t.deltas.add(delta(before, index, c.name(), partial));
        t.crafted.add(craftedNow.getOrDefault(c.name(), 0L));
        return t;
    }

    /** How many tracked entries were ever created (tests: a big change creates at most {@code maxTracked}). */
    int createdForTest() {
        return created;
    }

    private record Candidate(String name, String displayName, double score, long order) {
    }

    /**
     * The {@code limit} best new items of one sample: a min-heap of at most {@code limit} entries (the weakest on top, on
     * a tie the later one), so a sample with any number of changed items needs memory for {@code limit} only.
     */
    private static final class TopCandidates {
        private final int limit;
        private final java.util.PriorityQueue<Candidate> heap;
        private long order;

        TopCandidates(int limit) {
            this.limit = Math.max(0, limit);
            this.heap = new java.util.PriorityQueue<>(Math.max(1, Math.min(this.limit, 1024)),
                Comparator.comparingDouble(Candidate::score).thenComparing(Comparator.comparingLong(Candidate::order).reversed()));
        }

        void offer(String name, String displayName, double score) {
            if (score <= 0 || limit == 0) return;
            if (heap.size() < limit) {
                heap.add(new Candidate(name, displayName, score, order++));
            } else if (score > heap.peek().score()) {
                heap.poll();
                heap.add(new Candidate(name, displayName, score, order++));
            }
        }

        /** Best first (on a tie the one offered first). */
        List<Candidate> best() {
            var list = new ArrayList<>(heap);
            list.sort(Comparator.comparingDouble(Candidate::score).reversed().thenComparingLong(Candidate::order));
            return list;
        }
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
