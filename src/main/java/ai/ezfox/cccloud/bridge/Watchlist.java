package ai.ezfox.cccloud.bridge;

import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.Op;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import org.jspecify.annotations.Nullable;

/**
 * Items with a minimum amount, stored in the bridge block entity and edited from the dashboard. {@link #check} reports
 * every item that dropped below its minimum, once per crossing (it has to rise to the minimum again before it can
 * alert again). With auto-stock ({@link #autoStock}) entries also keep themselves stocked by crafting
 * (level maintainer, docs/SPEC.md "Storage card v2"). Pure Java, unit tested.
 */
public final class Watchlist {
    /** Pause auto-stock after this many failed jobs in a row. */
    public static final int MAX_FAILURES = 3;
    /** Minimum time between two auto-stock requests of an item. */
    public static final long COOLDOWN_MS = 30_000;

    public enum State {
        IDLE("idle"), CRAFTING("crafting"), WAITING_CPU("waiting_cpu"), MISSING("missing"), PAUSED("paused");

        public final String id;

        State(String id) {
            this.id = id;
        }
    }

    private static final class Entry {
        long min;
        long count;
        boolean below;
        long target;
        boolean autoCraft;
        State state = State.IDLE;
        int failures;
        long lastRequest = Long.MIN_VALUE / 2;
        @Nullable String jobId;

        Entry(long min) {
            this.min = min;
            this.target = min;
        }
    }

    /** What auto-stock needs from the bridge. */
    public interface Crafter {
        boolean craftable(String name);

        /** Starts a craft; returns the job id or an immediate error prefixed with "error:". */
        String start(String name, long count);

        /** Status of a job: queued/running/done/failed/cancelled, or null if unknown; plus its error. */
        @Nullable String status(String jobId);

        @Nullable String error(String jobId);
    }

    /** A view of an entry for the dashboard / Lua. */
    public record Info(String name, long min, long count, long target, boolean autoCraft, String state, int failures) {
    }

    public record Crossing(String name, long count, long min) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** Applies a dashboard operation. Returns null on success or an error code. */
    public @Nullable String apply(Op op, java.util.function.Predicate<String> itemExists) {
        return switch (op) {
            case Op.WatchAdd add -> {
                if (entries.containsKey(add.name())) yield "already_watched";
                if (entries.size() >= Limits.WATCHLIST) yield "too_many";
                if (!itemExists.test(add.name())) yield "unknown_item";
                entries.put(add.name(), new Entry(add.min()));
                yield null;
            }
            case Op.WatchRemove remove -> entries.remove(remove.name()) == null ? "unknown_item" : null;
            case Op.WatchSetMin set -> {
                var entry = entries.get(set.name());
                if (entry == null) yield "unknown_item";
                entry.min = set.min();
                entry.below = entry.count < entry.min && entry.below;
                yield null;
            }
            case Op.Craft craft -> "invalid_value";
            case Op.AutoStock set -> setAutoStock(set.name(), set.min(), set.target(), set.autoCraft(), itemExists);
            case Op.SetOutput set -> "invalid_value";
        };
    }

    /**
     * Configures auto-stock of an item (adds it to the watchlist if needed). {@code min 0} with autoCraft off removes
     * the auto-stock settings (the entry stays watched). Editing resets the failure counter and a pause.
     */
    public @Nullable String setAutoStock(String name, long min, long target, boolean autoCraft, java.util.function.Predicate<String> itemExists) {
        if (min < 0 || target < 0 || target > Integer.MAX_VALUE || min > Integer.MAX_VALUE) return "invalid_value";
        if (target < min) return "invalid_value";
        var entry = entries.get(name);
        if (entry == null) {
            if (entries.size() >= Limits.WATCHLIST) return "too_many";
            if (!itemExists.test(name)) return "unknown_item";
            entry = new Entry(min);
            entries.put(name, entry);
        }
        entry.min = min;
        entry.target = Math.max(min, target);
        entry.autoCraft = autoCraft;
        entry.failures = 0;
        if (entry.state == State.PAUSED || entry.state == State.MISSING || !autoCraft) entry.state = entry.jobId != null ? State.CRAFTING : State.IDLE;
        entry.below = entry.count < entry.min && entry.below;
        return null;
    }

    /**
     * One auto-stock step (every second): for each auto-stock entry below its minimum, request
     * {@code min(target − count, batch)} unless a job is running, the cooldown is active or the entry is paused.
     *
     * @return log lines of what happened (requests and state changes), for the server log
     */
    public List<String> autoStock(long nowMs, int batch, Crafter crafter) {
        var log = new ArrayList<String>();
        for (var e : entries.entrySet()) {
            var entry = e.getValue();
            String name = e.getKey();
            if (!entry.autoCraft || entry.min <= 0) continue;
            if (entry.jobId != null) {
                String status = crafter.status(entry.jobId);
                if (status == null || status.equals("done") || status.equals("cancelled")) {
                    if ("done".equals(status)) entry.failures = 0;
                    entry.jobId = null;
                    entry.state = State.IDLE;
                } else if (status.equals("failed")) {
                    String error = crafter.error(entry.jobId);
                    entry.jobId = null;
                    fail(entry, error, name, log);
                    continue; // show the state; the next step may retry
                } else {
                    entry.state = State.CRAFTING;
                    continue;
                }
            }
            if (entry.state == State.PAUSED) continue;
            if (entry.count >= entry.min) {
                if (entry.state != State.MISSING) entry.state = State.IDLE;
                continue;
            }
            if (nowMs - entry.lastRequest < COOLDOWN_MS) continue;
            if (!crafter.craftable(name)) {
                entry.state = State.MISSING;
                continue;
            }
            long amount = Math.min(entry.target - entry.count, batch);
            if (amount <= 0) amount = Math.min(entry.min - entry.count, batch);
            if (amount <= 0) continue;
            entry.lastRequest = nowMs;
            String result = crafter.start(name, amount);
            if (result.startsWith("error:")) {
                fail(entry, result.substring(6), name, log);
            } else {
                entry.jobId = result;
                entry.state = State.CRAFTING;
                log.add("requested " + amount + "x " + name + " (job " + result + ")");
            }
        }
        return log;
    }

    private static void fail(Entry entry, @Nullable String error, String name, List<String> log) {
        if ("no_cpu".equals(error) || "busy".equals(error)) {
            entry.state = State.WAITING_CPU; // retried after the cooldown, not counted as a failure
            return;
        }
        entry.failures++;
        entry.state = entry.failures >= MAX_FAILURES ? State.PAUSED : State.MISSING;
        log.add(name + ": " + (error == null ? "failed" : error) + (entry.state == State.PAUSED ? " – paused after " + MAX_FAILURES + " failures" : ""));
    }

    public @Nullable Info info(String name) {
        var e = entries.get(name);
        return e == null ? null : new Info(name, e.min, e.count, e.target, e.autoCraft, e.state.id, e.failures);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** Updates the counts; returns the items that just dropped below their minimum. */
    public List<Crossing> check(ToLongFunction<String> counter) {
        var crossings = new ArrayList<Crossing>();
        for (var e : entries.entrySet()) {
            var entry = e.getValue();
            entry.count = counter.applyAsLong(e.getKey());
            boolean below = entry.count < entry.min;
            if (below && !entry.below) crossings.add(new Crossing(e.getKey(), entry.count, entry.min));
            entry.below = below;
        }
        return crossings;
    }

    /** Rows for the dashboard: [{name, displayName, count, min}]. */
    public JsonArray rows(java.util.function.Function<String, String> displayName) {
        var array = new JsonArray(entries.size());
        for (var e : entries.entrySet()) {
            var row = new JsonObject();
            row.addProperty("name", e.getKey());
            row.addProperty("displayName", displayName.apply(e.getKey()));
            row.add("count", Json.number(e.getValue().count));
            row.add("min", Json.number(e.getValue().min));
            if (e.getValue().autoCraft) {
                row.add("target", Json.number(e.getValue().target));
                row.addProperty("autoCraft", true);
                row.addProperty("state", e.getValue().state.id);
            }
            array.add(row);
        }
        return array;
    }

    public String save() {
        var array = new JsonArray();
        for (var e : entries.entrySet()) {
            var row = new JsonObject();
            row.addProperty("name", e.getKey());
            row.addProperty("min", e.getValue().min);
            row.addProperty("below", e.getValue().below);
            if (e.getValue().autoCraft || e.getValue().target != e.getValue().min) {
                row.addProperty("target", e.getValue().target);
                row.addProperty("autoCraft", e.getValue().autoCraft);
                row.addProperty("failures", e.getValue().failures);
                row.addProperty("paused", e.getValue().state == State.PAUSED);
            }
            array.add(row);
        }
        return Json.GSON.toJson(array);
    }

    public void load(String json) {
        entries.clear();
        try {
            var parsed = JsonParser.parseString(json);
            // Since storage v2 the bridge stores {"watch":[…],"fav":[…]}; older saves are the plain array.
            var array = parsed.isJsonObject() ? parsed.getAsJsonObject().getAsJsonArray("watch") : parsed.getAsJsonArray();
            for (var element : array) {
                var row = element.getAsJsonObject();
                String name = row.get("name").getAsString();
                long min = row.get("min").getAsLong();
                if (!Op.isItemName(name) || min < 0 || entries.size() >= Limits.WATCHLIST) continue;
                var entry = new Entry(min);
                entry.below = row.has("below") && row.get("below").getAsBoolean();
                if (row.has("target")) entry.target = Math.max(min, Math.min(Integer.MAX_VALUE, row.get("target").getAsLong()));
                entry.autoCraft = row.has("autoCraft") && row.get("autoCraft").getAsBoolean();
                entry.failures = row.has("failures") ? Math.max(0, Math.min(MAX_FAILURES, row.get("failures").getAsInt())) : 0;
                if (row.has("paused") && row.get("paused").getAsBoolean()) entry.state = State.PAUSED;
                entries.put(name, entry);
            }
        } catch (RuntimeException ignored) {
            // Corrupt data: start with an empty watchlist.
        }
    }
}
