package ai.ezfox.cccloud.sensor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The event log of one Advanced Cloud Sensor (docs/SPEC.md "Advanced Cloud Sensor"): the last {@link #MAX_EVENTS}
 * "entered" / "left" events (older ones are dropped) with a sequence number (rules read the events after the last one
 * they saw) and the number of entries today (in the server's schedule time zone, reset at midnight). Saved with the
 * block, so its size is bounded. Pure Java, unit tested.
 */
public final class SensorLog {
    public static final int MAX_EVENTS = 20;

    /** One event. {@code name} is null for a player when names were off (or for an unnamed entity). */
    public record Event(long seq, long time, boolean entered, SensorEntry entry) {
    }

    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private long seq;
    private int today;
    /** Epoch day of {@link #today}. */
    private long day = Long.MIN_VALUE;

    public Event add(boolean entered, SensorEntry entry, long time, ZoneId zone) {
        var event = new Event(++seq, time, entered, entry);
        events.addLast(event);
        while (events.size() > MAX_EVENTS) events.removeFirst();
        if (entered) {
            roll(time, zone);
            today++;
        }
        return event;
    }

    /** Entries today (since midnight in {@code zone}). */
    public int today(long now, ZoneId zone) {
        roll(now, zone);
        return today;
    }

    private void roll(long now, ZoneId zone) {
        long d = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay();
        if (d != day) {
            day = d;
            today = 0;
        }
    }

    /** The events, oldest first. */
    public List<Event> events() {
        return List.copyOf(events);
    }

    /** The sequence number of the newest event (0 = none yet). */
    public long lastSeq() {
        return seq;
    }

    /** Events newer than {@code after}, oldest first (at most {@link #MAX_EVENTS}: older ones are gone). */
    public List<Event> after(long after) {
        var list = new ArrayList<Event>();
        for (var e : events) if (e.seq() > after) list.add(e);
        return list;
    }

    public void clear() {
        events.clear();
        today = 0;
    }

    // ---- persistence ---------------------------------------------------------------------------------------------------

    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("seq", seq);
        json.addProperty("today", today);
        json.addProperty("day", day);
        var array = new JsonArray();
        for (var e : events) {
            var o = new JsonObject();
            o.addProperty("s", e.seq());
            o.addProperty("t", e.time());
            o.addProperty("in", e.entered());
            o.addProperty("k", e.entry().kind().id);
            o.addProperty("id", e.entry().type());
            if (e.entry().name() != null) o.addProperty("n", e.entry().name());
            array.add(o);
        }
        json.add("events", array);
        return json;
    }

    /** Reads a saved log; anything malformed is skipped (a broken save never breaks the block). */
    public static SensorLog fromJson(@Nullable JsonObject json) {
        var log = new SensorLog();
        if (json == null) return log;
        log.seq = Math.max(0, num(json, "seq"));
        log.today = (int) Math.max(0, Math.min(1_000_000, num(json, "today")));
        log.day = json.get("day") instanceof JsonPrimitive p && p.isNumber() ? p.getAsLong() : Long.MIN_VALUE;
        if (json.get("events") instanceof JsonArray array) {
            for (var element : array) {
                if (!(element instanceof JsonObject o)) continue;
                var kind = SensorKind.byId(str(o, "k"));
                String type = str(o, "id");
                if (kind == null || type == null || type.length() > 128) continue;
                String name = str(o, "n");
                if (name != null && name.length() > 64) name = name.substring(0, 64);
                long s = num(o, "s");
                if (s <= 0 || s > log.seq) continue;
                log.events.addLast(new Event(s, num(o, "t"), o.get("in") instanceof JsonPrimitive b && b.isBoolean() && b.getAsBoolean(),
                    new SensorEntry(kind, type, name)));
                while (log.events.size() > MAX_EVENTS) log.events.removeFirst();
            }
        }
        return log;
    }

    private static long num(JsonObject o, String key) {
        return o.get(key) instanceof JsonPrimitive p && p.isNumber() ? p.getAsLong() : 0;
    }

    private static @Nullable String str(JsonObject o, String key) {
        return o.get(key) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }
}
