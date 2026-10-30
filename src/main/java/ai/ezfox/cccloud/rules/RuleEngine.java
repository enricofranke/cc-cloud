package ai.ezfox.cccloud.rules;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates the rules of one mainframe (docs/SPEC.md v0.5 C). Pure Java with an injected {@link World}, so triggers,
 * cooldowns and the loop protection are unit tested. The game calls {@link #tick} on the server thread a few times per
 * second; every check is a map lookup (widget values come from the dashboard hub's index), so the step is cheap.
 * <p>
 * Triggers are edge-triggered: a comparison fires when it <i>becomes</i> true, not while it stays true. The first
 * evaluation after loading only records the current state (no firing on server start). Unknown values (card not
 * loaded) keep the previous state.
 * <p>
 * Schedules (v0.10: {@code interval}, {@code schedule}, {@code time}) are evaluated even while the mainframe is not
 * {@link World#available() available}: an occurrence that comes due then is <i>skipped</i> (one log entry per outage,
 * nothing runs, nothing is caught up). The last occurrence of real-time triggers is kept across restarts
 * ({@link #fired()}); on the engine's first evaluation at most one missed occurrence is caught up, within
 * {@link Settings#missedWindowMs()}, as soon as the mainframe is available.
 */
public final class RuleEngine {
    /** Max rule runs per second per mainframe (loop protection across rules). */
    public static final int MAX_RUNS_PER_SECOND = 20;

    /** What the engine reads and does. All methods run on the server thread. */
    public interface World {
        /** Numeric value of a widget (booleans 0/1), or null if unknown. */
        @Nullable Double widget(String uplinkId, String widgetId);

        /** Item count in the storage behind a card, or null if unknown. */
        @Nullable Long items(String uplinkId, String item);

        /** Mainframe FE buffer in percent, or null if the mainframe has no buffer. */
        @Nullable Double powerPercent();

        boolean online(String uplinkId);

        /** In-game time of day (0–23999) of the mainframe's level, or -1. */
        long dayTime();

        long now();

        /** Runs one action. Returns null on success, else an error code. */
        @Nullable String run(Rule rule, Rule.Action action);

        /** The mainframe is loaded and online (rules can act). Only schedules are evaluated otherwise. */
        default boolean available() {
            return true;
        }

        /** Why the mainframe is not available: {@code unloaded} or {@code offline}. */
        default String unavailable() {
            return "offline";
        }
    }

    /**
     * Server config of the schedules (v0.10).
     *
     * @param zone            time zone of {@code schedule} triggers
     * @param runMissed       catch up one missed occurrence after a restart
     * @param missedWindowMs  only occurrences younger than this are caught up
     * @param minIntervalMinutes shortest real-time interval (shorter stored intervals run at this period)
     */
    public record Settings(ZoneId zone, boolean runMissed, long missedWindowMs, int minIntervalMinutes) {
        public static final Settings DEFAULT = new Settings(ZoneId.systemDefault(), true, 24 * 3_600_000L, 1);
    }

    /**
     * One rule run for the log, the dashboard and push.
     *
     * @param skipped a schedule came due while the mainframe was unavailable ({@code error} = skipped_unloaded|offline)
     */
    public record Run(String ruleId, String name, long time, boolean manual, @Nullable String error, int actions, boolean push, boolean skipped) {
        public Run(String ruleId, String name, long time, boolean manual, @Nullable String error, int actions, boolean push) {
            this(ruleId, name, time, manual, error, actions, push, false);
        }

        public boolean ok() {
            return error == null;
        }
    }

    private enum Fire { NONE, DUE, CATCH_UP }

    /** Live state of a rule (memory only, except {@link #lastFire}, see {@link #fired()}). */
    public static final class State {
        @Nullable Boolean truth;
        long lastRun;
        /** Next occurrence of an interval or schedule trigger (ms), 0 = not computed yet. */
        long nextAt;
        /** The last time a real-time trigger came due (run, held back by cooldown/conditions, or skipped). */
        long lastFire;
        /** A missed occurrence from before the restart that runs once the mainframe is available (ms), 0 = none. */
        long catchUp;
        /** A skipped occurrence was logged in the current outage. */
        boolean skipping;
        long runs;
        @Nullable String lastResult;

        public long lastRun() {
            return lastRun;
        }

        /** Next occurrence of an interval/schedule trigger, or 0. */
        public long nextAt() {
            return nextAt;
        }

        public long lastFire() {
            return lastFire;
        }

        public long runs() {
            return runs;
        }

        public @Nullable String lastResult() {
            return lastResult;
        }
    }

    private final Map<String, State> states = new HashMap<>();
    private long lastDayTime = -1;
    private long windowStart;
    private int windowRuns;
    private boolean throttled;
    private boolean started;
    private boolean firedDirty;

    public RuleEngine() {
    }

    /** An engine after a (re)start: {@code fired} = the stored last occurrence per rule id (for missed runs). */
    public RuleEngine(Map<String, Long> fired) {
        for (var e : fired.entrySet()) state(e.getKey()).lastFire = e.getValue();
    }

    /** Last occurrence of every real-time trigger (rule id → ms), to be stored. */
    public Map<String, Long> fired() {
        var map = new HashMap<String, Long>();
        for (var e : states.entrySet()) if (e.getValue().lastFire > 0) map.put(e.getKey(), e.getValue().lastFire);
        return map;
    }

    /** True once if {@link #fired()} changed since the last call. */
    public boolean takeFiredDirty() {
        boolean dirty = firedDirty;
        firedDirty = false;
        return dirty;
    }

    public State state(String ruleId) {
        return states.computeIfAbsent(ruleId, k -> new State());
    }

    /** Forget state of rules that no longer exist. */
    public void retain(java.util.Collection<String> ruleIds) {
        states.keySet().retainAll(ruleIds);
    }

    /** Evaluates every enabled rule once and runs those that fire. */
    public List<Run> tick(List<Rule> rules, World world) {
        return tick(rules, world, Settings.DEFAULT);
    }

    /**
     * Evaluates every rule once and runs the enabled ones that fire. While the mainframe is not available only
     * schedules are evaluated; an occurrence due then is skipped (reported once per outage as a skipped run).
     */
    public List<Run> tick(List<Rule> rules, World world, Settings settings) {
        var runs = new ArrayList<Run>();
        long now = world.now();
        long day = world.dayTime();
        boolean available = world.available();
        boolean startup = !started;
        started = true;
        for (var rule : rules) {
            var state = state(rule.id());
            if (available) state.skipping = false;
            else if (!rule.isSchedule()) continue; // edge triggers need live values
            var fire = fires(rule, state, world, now, day, startup, available, settings);
            if (fire == Fire.NONE) continue;
            if (!available) {
                if (state.skipping || !rule.enabled()) continue;
                state.skipping = true;
                runs.add(new Run(rule.id(), rule.name(), now, false, "skipped_" + world.unavailable(), 0, false, true));
                continue;
            }
            if (!rule.enabled()) continue;
            if (state.lastRun != 0 && now - state.lastRun < rule.cooldown() * 1000L) continue;
            if (!conditions(rule, world)) continue;
            var run = run(rule, world, false);
            if (run != null) runs.add(run);
        }
        lastDayTime = day;
        return runs;
    }

    /** Runs a rule now ("Run now" in the dashboard, Lua): ignores trigger, conditions and cooldown, not the rate limit. */
    public @Nullable Run runNow(Rule rule, World world) {
        return run(rule, world, true);
    }

    private @Nullable Run run(Rule rule, World world, boolean manual) {
        long now = world.now();
        if (now - windowStart >= 1000) {
            windowStart = now;
            windowRuns = 0;
            throttled = false;
        }
        var state = state(rule.id());
        if (++windowRuns > MAX_RUNS_PER_SECOND) {
            state.lastResult = "rate_limited";
            if (throttled) return null;
            throttled = true; // report the first throttled run of this second, drop the rest silently
            return new Run(rule.id(), rule.name(), now, manual, "rate_limited", 0, false);
        }
        String error = null;
        int done = 0;
        for (var action : rule.actions()) {
            if (done >= Rule.MAX_ACTIONS) break;
            String result;
            try {
                result = world.run(rule, action);
            } catch (RuntimeException e) {
                result = "internal_error";
            }
            done++;
            if (result != null && error == null) error = result;
        }
        state.lastRun = now;
        state.runs++;
        state.lastResult = error == null ? "ok" : error;
        return new Run(rule.id(), rule.name(), now, manual, error, done, rule.push());
    }

    private Fire fires(Rule rule, State state, World world, long now, long day, boolean startup, boolean available, Settings settings) {
        if (state.catchUp != 0) {
            // A missed occurrence from before the restart: once, as soon as the mainframe can act, while still recent.
            if (now - state.catchUp > settings.missedWindowMs()) state.catchUp = 0;
            else if (available) {
                state.catchUp = 0;
                fired(state, now);
                return Fire.CATCH_UP;
            }
        }
        return switch (rule.when()) {
            case Rule.CheckTrigger t -> edge(state, check(t.check(), world)) ? Fire.DUE : Fire.NONE;
            case Rule.OfflineTrigger t -> edge(state, !world.online(t.uplinkId())) ? Fire.DUE : Fire.NONE;
            case Rule.IntervalTrigger t -> {
                long period = Math.max(t.minutes(), settings.minIntervalMinutes()) * 60_000L;
                if (state.nextAt == 0) {
                    state.nextAt = now + period;
                    if (startup && state.lastFire > 0) {
                        long due = state.lastFire + period;
                        long latest = due + (now - due) / period * period; // the most recent missed occurrence
                        if (due > now) state.nextAt = due; // keep the rhythm across a restart
                        else if (settings.runMissed() && now - latest <= settings.missedWindowMs()) state.catchUp = latest;
                    }
                    yield Fire.NONE;
                }
                if (now < state.nextAt) yield Fire.NONE;
                state.nextAt = now + period;
                fired(state, now);
                yield Fire.DUE;
            }
            case Rule.ScheduleTrigger t -> {
                if (state.nextAt == 0) {
                    state.nextAt = ScheduleMath.next(t, now, settings.zone());
                    if (startup && state.lastFire > 0) {
                        long last = ScheduleMath.last(t, now, settings.zone());
                        if (last > state.lastFire && settings.runMissed() && now - last <= settings.missedWindowMs()) state.catchUp = last;
                    }
                    yield Fire.NONE;
                }
                if (now < state.nextAt) yield Fire.NONE;
                state.nextAt = ScheduleMath.next(t, now, settings.zone()); // several passed occurrences fire once
                fired(state, now);
                yield Fire.DUE;
            }
            case Rule.TimeTrigger t -> {
                if (day < 0 || lastDayTime < 0 || day == lastDayTime) yield Fire.NONE;
                boolean passed = lastDayTime < day ? lastDayTime < t.time() && t.time() <= day : t.time() > lastDayTime || t.time() <= day;
                yield passed ? Fire.DUE : Fire.NONE;
            }
        };
    }

    private void fired(State state, long now) {
        state.lastFire = now;
        firedDirty = true;
    }

    /** True on a false → true transition. Unknown (null) keeps the state; the first known value only initialises it. */
    private static boolean edge(State state, @Nullable Boolean now) {
        if (now == null) return false;
        Boolean before = state.truth;
        state.truth = now;
        return before != null && !before && now;
    }

    private static boolean conditions(Rule rule, World world) {
        for (var condition : rule.conditions()) {
            if (!Boolean.TRUE.equals(check(condition.check(), world))) return false;
        }
        return true;
    }

    static @Nullable Boolean check(Rule.Check check, World world) {
        return switch (check) {
            case Rule.WidgetCheck w -> {
                var v = world.widget(w.uplinkId(), w.widgetId());
                yield v == null ? null : w.cmp().test(v, w.value());
            }
            case Rule.ItemsCheck i -> {
                var v = world.items(i.uplinkId(), i.item());
                yield v == null ? null : i.cmp().test(v, i.value());
            }
            case Rule.PowerCheck p -> {
                var v = world.powerPercent();
                yield v == null ? null : p.cmp().test(v, p.value());
            }
            case Rule.OnlineCheck o -> world.online(o.uplinkId());
        };
    }
}
