package ai.ezfox.cccloud.code.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The log of one script: the last {@code kept} lines, kept across restarts (not across server restarts). Lines from
 * the script itself are limited to {@code perSecond}; what goes over is dropped and counted in one {@code system}
 * line. Thread-safe; every line has a sequence number so the dashboard can fetch what is new.
 */
public final class ScriptLog {
    public static final int MAX_LINE = 1000;

    public record Line(long seq, long ts, String level, String text) {
        public JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("ts", ts);
            json.addProperty("level", level);
            json.addProperty("text", text);
            return json;
        }
    }

    private final ArrayDeque<Line> lines = new ArrayDeque<>();
    private final LongSupplier clock;
    private int kept;
    private int perSecond;
    private long seq;
    private long windowStart;
    private int inWindow;
    private int dropped;

    public ScriptLog(int kept, int perSecond, LongSupplier clock) {
        this.kept = kept;
        this.perSecond = perSecond;
        this.clock = clock;
    }

    public synchronized void limits(int kept, int perSecond) {
        this.kept = kept;
        this.perSecond = perSecond;
    }

    /** A line from the script ({@code info}, {@code warn}); false if dropped by the rate limit. */
    public synchronized boolean script(String level, String text) {
        long now = clock.getAsLong();
        if (now - windowStart >= 1000) {
            if (dropped > 0) add(now, "system", dropped + " log line(s) dropped (more than " + perSecond + " per second)");
            windowStart = now;
            inWindow = 0;
            dropped = 0;
        }
        if (inWindow >= perSecond) {
            dropped++;
            return false;
        }
        inWindow++;
        add(now, level, text);
        return true;
    }

    /** A line from the runtime ({@code system}, {@code error}); never dropped. */
    public synchronized void system(String level, String text) {
        add(clock.getAsLong(), level, text);
    }

    private void add(long now, String level, String text) {
        if (text.length() > MAX_LINE) text = text.substring(0, MAX_LINE - 1) + "…";
        lines.addLast(new Line(++seq, now, level, text));
        while (lines.size() > kept) lines.removeFirst();
    }

    public synchronized long seq() {
        return seq;
    }

    public synchronized List<Line> since(long after) {
        var out = new ArrayList<Line>();
        for (var line : lines) if (line.seq() > after) out.add(line);
        return out;
    }

    public synchronized int size() {
        return lines.size();
    }

    public synchronized void clear() {
        lines.clear();
    }

    public static JsonArray toJson(List<Line> lines) {
        var array = new JsonArray();
        for (var line : lines) array.add(line.toJson());
        return array;
    }
}
