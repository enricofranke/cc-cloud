package ai.ezfox.cccloud.code.api;

import ai.ezfox.cccloud.code.world.ScriptWorld;
import ai.ezfox.cccloud.code.world.WorldAction;
import ai.ezfox.cccloud.code.world.WorldEvents;
import ai.ezfox.cccloud.code.world.WorldSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The Java side of every {@code cloud.*} function in the descriptor ({@link ApiSpec}), shared by all script languages.
 * A handler gets checked {@link Args} and returns nothing ({@code null}), one value, several ({@code Object[]}), or a
 * {@link CompletableFuture} the engine waits for without blocking (the script pauses). Values are plain Java
 * ({@code Map}, {@code List}, {@code String}, numbers, booleans) and are turned into the language's values by the
 * binding.
 *
 * <p>Wrong calls (bad arguments, an unknown event) raise an {@link ApiError}; things that can fail in the world
 * (unknown card, no right, PIN, offline) return {@code false, error} like the dashboard would answer.
 */
public final class CloudApi {
    @FunctionalInterface
    public interface Handler {
        @Nullable Object call(ScriptHost host, Args args);
    }

    public static final Set<String> LEVELS = Set.of("info", "warn", "crit");
    public static final Set<String> SIDES = Set.of("top", "bottom", "left", "right", "front", "back");
    /** Shortest {@code cloud.every} interval. */
    public static final double MIN_EVERY = 0.25;
    /** Longest sleep, timer or wait timeout: a day. */
    public static final double MAX_DELAY = 86_400;
    public static final int MAX_TEXT = 500;
    private static final Pattern TIME = Pattern.compile("([01]?\\d|2[0-3]):([0-5]\\d)");
    private static final List<String> DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");

    private static final CloudApi STANDARD = new CloudApi();

    private final Map<String, Handler> handlers = new HashMap<>();

    public static CloudApi standard() {
        return STANDARD;
    }

    private CloudApi() {
        // ---- read ----
        add("getCards", (h, a) -> {
            var list = new ArrayList<Object>();
            for (var c : h.snapshot().cards()) {
                var row = head(c);
                row.put("widgets", (long) c.widgets().size());
                list.add(row);
            }
            return list;
        });
        add("getCard", (h, a) -> {
            var c = h.snapshot().card(a.string(0));
            return c == null ? null : card(c);
        });
        add("getValue", (h, a) -> {
            var c = h.snapshot().card(a.string(0));
            var w = c == null ? null : c.widget(a.string(1));
            return w == null ? null : w.value();
        });
        add("getWidget", (h, a) -> {
            var c = h.snapshot().card(a.string(0));
            var w = c == null ? null : c.widget(a.string(1));
            return w == null ? null : widget(w);
        });
        add("isOnline", (h, a) -> {
            var s = h.snapshot();
            if (!a.has(0)) return s.mainframe().online();
            var c = s.card(a.string(0));
            if (c != null) return c.online();
            var d = s.device(a.string(0));
            return d != null && d.online();
        });
        add("getDevices", (h, a) -> {
            var list = new ArrayList<Object>();
            for (var d : h.snapshot().devices()) {
                var row = new LinkedHashMap<String, Object>();
                row.put("id", d.id());
                row.put("type", d.type());
                row.put("name", d.name());
                row.put("status", d.status());
                row.put("online", d.online());
                list.add(row);
            }
            return list;
        });
        add("getMainframe", (h, a) -> {
            var m = h.snapshot().mainframe();
            var row = new LinkedHashMap<String, Object>();
            row.put("id", m.id());
            row.put("name", m.name());
            row.put("online", m.online());
            row.put("enabled", m.enabled());
            row.put("energy", m.energy());
            row.put("capacity", m.capacity());
            row.put("percent", m.percent());
            row.put("usage", m.usage());
            return row;
        });
        add("isEnabled", (h, a) -> h.snapshot().mainframe().enabled());
        add("getEnergy", (h, a) -> h.snapshot().mainframe().energy());
        add("getEnergyCapacity", (h, a) -> h.snapshot().mainframe().capacity());
        add("getEnergyUsage", (h, a) -> h.snapshot().mainframe().usage());
        add("getItemCount", (h, a) -> {
            var c = h.snapshot().card(a.string(0));
            String item = item(a, 1);
            if (c == null) return null;
            return h.act(new WorldAction.ItemCount(c.id(), c.title(), item)).thenApply(r -> r == null || r.length == 0 ? null : r[0]);
        });
        add("getRedstone", (h, a) -> {
            Integer level = h.snapshot().redstone().get(a.string(0));
            return level == null ? null : (long) level;
        });
        add("getRules", (h, a) -> {
            var list = new ArrayList<Object>();
            for (var r : h.snapshot().rules()) {
                var row = new LinkedHashMap<String, Object>();
                row.put("id", r.id());
                row.put("name", r.name());
                row.put("enabled", r.enabled());
                row.put("trigger", r.trigger());
                row.put("runs", r.runs());
                if (r.lastResult() != null) row.put("lastResult", r.lastResult());
                list.add(row);
            }
            return list;
        });
        add("getScenes", (h, a) -> {
            var list = new ArrayList<Object>();
            for (var s : h.snapshot().scenes()) {
                var row = new LinkedHashMap<String, Object>();
                row.put("id", s.id());
                row.put("name", s.name());
                row.put("needsPin", s.needsPin());
                row.put("confirm", s.confirm());
                row.put("running", s.running());
                list.add(row);
            }
            return list;
        });

        // ---- act ----
        add("press", (h, a) -> {
            var c = h.snapshot().card(a.string(0));
            if (c == null) return fail("unknown_card");
            var w = c.widget(a.string(1));
            if (w == null) return fail("unknown_widget");
            if (w.pin()) return fail("pin_required");
            boolean confirmed = Args.flag(a.optTable(2), "confirm");
            if (w.confirm() && !confirmed) return fail("confirm_required");
            return h.act(new WorldAction.Press(c.id(), c.title(), w.id(), confirmed));
        });
        add("set", (h, a) -> {
            Object value = a.get(2);
            if (value instanceof Number n) {
                if (!Double.isFinite(n.doubleValue())) throw a.bad(2, "number must be finite");
                value = n.doubleValue();
            } else if (value instanceof String s) {
                if (s.length() > MAX_TEXT) throw a.bad(2, "text longer than " + MAX_TEXT + " characters");
            } else if (!(value instanceof Boolean)) {
                throw a.bad(2, "boolean, number or string expected");
            }
            var c = h.snapshot().card(a.string(0));
            if (c == null) return fail("unknown_card");
            var w = c.widget(a.string(1));
            if (w == null) return fail("unknown_widget");
            if (w.pin()) return fail("pin_required");
            boolean confirmed = Args.flag(a.optTable(3), "confirm");
            if (w.confirm() && !confirmed) return fail("confirm_required");
            return h.act(new WorldAction.Set(c.id(), c.title(), w.id(), value, confirmed));
        });
        add("setRedstone", (h, a) -> {
            String side = a.string(1).toLowerCase(Locale.ROOT);
            if (!SIDES.contains(side)) throw a.bad(1, "side expected: top, bottom, left, right, front or back");
            long level = a.integer(2);
            if (level < 0 || level > 15) throw a.bad(2, "level 0-15 expected");
            var c = h.snapshot().card(a.string(0));
            if (c == null) return fail("unknown_card");
            return h.act(new WorldAction.SetRedstone(c.id(), c.title(), side, (int) level));
        });
        add("craft", (h, a) -> {
            String item = item(a, 1);
            long amount = a.integer(2);
            if (amount < 1) throw a.bad(2, "amount must be at least 1");
            var c = h.snapshot().card(a.string(0));
            if (c == null) return fail("unknown_card");
            return h.act(new WorldAction.Craft(c.id(), c.title(), item, amount));
        });
        add("setEnabled", (h, a) -> h.act(new WorldAction.SetEnabled(a.bool(0))));
        add("runScene", (h, a) -> {
            var s = h.snapshot().scene(a.string(0));
            if (s == null) return fail("unknown_scene");
            if (s.needsPin()) return fail("pin_required");
            boolean confirmed = Args.flag(a.optTable(1), "confirm");
            if (s.confirm() && !confirmed) return fail("confirm_required");
            return h.act(new WorldAction.RunScene(s.id(), s.name(), confirmed));
        });
        add("setRuleEnabled", (h, a) -> {
            var r = h.snapshot().rule(a.string(0));
            if (r == null) return fail("unknown_rule");
            return h.act(new WorldAction.SetRuleEnabled(r.id(), r.name(), a.bool(1)));
        });
        add("runRule", (h, a) -> {
            var r = h.snapshot().rule(a.string(0));
            if (r == null) return fail("unknown_rule");
            return h.act(new WorldAction.RunRule(r.id(), r.name()));
        });

        // ---- events ----
        add("on", (h, a) -> (long) h.on(WorldEvents.filter(a.string(0), a.optTable(1), "on"), a.function(2)));
        add("wait", (h, a) -> {
            var filter = WorldEvents.filter(a.string(0), a.optTable(1), "wait");
            double timeout = a.has(2) ? delay(a, 2, 0) : -1;
            return h.waitEvent(filter, timeout);
        });
        add("off", (h, a) -> h.off((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, a.integer(0)))));

        // ---- timers ----
        add("sleep", (h, a) -> h.sleep(delay(a, 0, 0)));
        add("every", (h, a) -> (long) h.every(delay(a, 0, MIN_EVERY), a.function(1)));
        add("after", (h, a) -> (long) h.after(delay(a, 0, 0), a.function(1)));
        add("at", (h, a) -> (long) h.at(timeSpec(a), a.function(1)));

        // ---- notify ----
        add("notify", (h, a) -> h.notify(ScriptWorld.Channel.BOTH, text(a, 0), level(a, 1), null));
        add("toast", (h, a) -> h.notify(ScriptWorld.Channel.TOAST, text(a, 0), level(a, 1), null));
        add("push", (h, a) -> h.notify(ScriptWorld.Channel.PUSH, text(a, 0), level(a, 1), a.has(2) ? clip(a.string(2), 64) : null));

        // ---- store ----
        add("store.get", (h, a) -> h.storeGet(key(a)));
        add("store.set", (h, a) -> h.storeSet(key(a), a.get(1)));
        add("store.delete", (h, a) -> h.storeSet(key(a), null));
        add("store.keys", (h, a) -> new ArrayList<Object>(h.storeKeys()));

        // ---- log and time ----
        add("log", (h, a) -> {
            h.log("info", join(a));
            return null;
        });
        add("warn", (h, a) -> {
            h.log("warn", join(a));
            return null;
        });
        add("time", (h, a) -> System.currentTimeMillis());
        add("clock", (h, a) -> h.clock());
        add("gameTime", (h, a) -> h.snapshot().gameTime());
    }

    private void add(String name, Handler handler) {
        handlers.put(name, handler);
    }

    /** The handler of a descriptor function (aliases run their target's handler). */
    public Handler handler(ApiSpec.Function function) {
        var h = handlers.get(function.target());
        if (h == null) throw new IllegalStateException("no handler for cloud." + function.name());
        return h;
    }

    public Set<String> names() {
        return Set.copyOf(handlers.keySet());
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private static Object[] fail(String error) {
        return new Object[]{false, error};
    }

    private static Map<String, Object> head(WorldSnapshot.Card c) {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", c.id());
        row.put("title", c.title());
        row.put("online", c.online());
        row.put("kind", c.kind());
        if (c.blocked()) row.put("blocked", true);
        return row;
    }

    private static Map<String, Object> card(WorldSnapshot.Card c) {
        var row = head(c);
        var widgets = new ArrayList<Object>();
        for (var w : c.widgets()) widgets.add(widget(w));
        row.put("widgets", widgets);
        return row;
    }

    private static Map<String, Object> widget(WorldSnapshot.Widget w) {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", w.id());
        row.put("type", w.type());
        if (w.label() != null) row.put("label", w.label());
        if (w.value() != null) row.put("value", w.value());
        if (w.unit() != null) row.put("unit", w.unit());
        if (w.min() != null) row.put("min", w.min());
        if (w.max() != null) row.put("max", w.max());
        if (w.pin()) row.put("pin", true);
        if (w.confirm()) row.put("confirm", true);
        return row;
    }

    private static String item(Args a, int i) {
        String item = a.string(i);
        if (item.isEmpty() || item.length() > 128) throw a.bad(i, "item id expected, e.g. minecraft:iron_ingot");
        return item;
    }

    private static double delay(Args a, int i, double min) {
        double s = a.number(i);
        if (!Double.isFinite(s) || s < 0) throw a.bad(i, "seconds must be a positive number");
        if (s < min) throw a.bad(i, "at least " + min + " seconds");
        if (s > MAX_DELAY) throw a.bad(i, "at most " + (long) MAX_DELAY + " seconds (a day)");
        return s;
    }

    private static String text(Args a, int i) {
        String s = a.string(i).strip();
        if (s.isEmpty()) throw a.bad(i, "text must not be empty");
        return clip(s, MAX_TEXT);
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static String level(Args a, int i) {
        if (!a.has(i)) return "info";
        String level = a.string(i).toLowerCase(Locale.ROOT);
        if (!LEVELS.contains(level)) throw a.bad(i, "level must be info, warn or crit");
        return level;
    }

    private static String key(Args a) {
        String key = a.string(0);
        if (key.isEmpty() || key.length() > 64) throw a.bad(0, "key must be 1-64 characters");
        return key;
    }

    /** Variadic {@code log} arguments arrive as their text already ({@code tostring} in the engine). */
    private static String join(Args a) {
        var sb = new StringBuilder();
        for (int i = 0; i < a.count(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(a.get(i));
        }
        return sb.toString();
    }

    /** {@code "HH:MM"}, {@code {time="HH:MM", days={"mon",…}}} or {@code {gameTime=13000}}. */
    static ScriptHost.TimeSpec timeSpec(Args a) {
        Object v = a.get(0);
        if (v instanceof String s) return new ScriptHost.TimeSpec.Daily(minute(a, s), 0x7f);
        if (!(v instanceof Map<?, ?> m)) throw a.bad(0, "\"HH:MM\", {time=\"HH:MM\", days={...}} or {gameTime=...} expected");
        if (m.get("gameTime") instanceof Number n) {
            if (m.size() != 1) throw a.bad(0, "{gameTime=...} takes no other fields");
            double t = n.doubleValue();
            if (!Double.isFinite(t) || t < 0 || t >= 24000) throw a.bad(0, "gameTime must be 0-23999");
            return new ScriptHost.TimeSpec.Game((long) t);
        }
        if (!(m.get("time") instanceof String time)) throw a.bad(0, "time=\"HH:MM\" expected");
        int days = 0x7f;
        Object list = m.get("days");
        if (list != null) {
            if (!(list instanceof List<?> l) || l.isEmpty()) throw a.bad(0, "days must be a list like {\"mon\", \"fri\"}");
            days = 0;
            for (var d : l) {
                int index = d instanceof String s ? DAYS.indexOf(s.toLowerCase(Locale.ROOT)) : -1;
                if (index < 0) throw a.bad(0, "unknown day " + d + " (mon, tue, wed, thu, fri, sat, sun)");
                days |= 1 << index;
            }
        }
        for (var key : m.keySet()) {
            if (!key.equals("time") && !key.equals("days")) throw a.bad(0, "unknown field " + key);
        }
        return new ScriptHost.TimeSpec.Daily(minute(a, time), days);
    }

    private static int minute(Args a, String s) {
        var m = TIME.matcher(s.strip());
        if (!m.matches()) throw a.bad(0, "time must be \"HH:MM\" (24 hours)");
        return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
    }
}
