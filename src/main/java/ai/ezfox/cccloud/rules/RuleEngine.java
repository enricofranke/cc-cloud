package ai.ezfox.cccloud.rules;

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
    }

    /** One rule run for the log, the dashboard and push. */
    public record Run(String ruleId, String name, long time, boolean manual, @Nullable String error, int actions, boolean push) {
        public boolean ok() {
            return error == null;
        }
    }

    /** Live state of a rule (memory only). */
    public static final class State {
        @Nullable Boolean truth;
        long lastRun;
        long nextInterval;
        long runs;
        @Nullable String lastResult;

        public long lastRun() {
            return lastRun;
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

    public State state(String ruleId) {
        return states.computeIfAbsent(ruleId, k -> new State());
    }

    /** Forget state of rules that no longer exist. */
    public void retain(java.util.Collection<String> ruleIds) {
        states.keySet().retainAll(ruleIds);
    }

    /** Evaluates every enabled rule once and runs those that fire. */
    public List<Run> tick(List<Rule> rules, World world) {
        var runs = new ArrayList<Run>();
        long now = world.now();
        long day = world.dayTime();
        for (var rule : rules) {
            var state = state(rule.id());
            boolean fired = fires(rule, state, world, now, day);
            if (!rule.enabled() || !fired) continue;
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

    private boolean fires(Rule rule, State state, World world, long now, long day) {
        return switch (rule.when()) {
            case Rule.CheckTrigger t -> edge(state, check(t.check(), world));
            case Rule.OfflineTrigger t -> edge(state, !world.online(t.uplinkId()));
            case Rule.IntervalTrigger t -> {
                long period = t.minutes() * 60_000L;
                if (state.nextInterval == 0) {
                    state.nextInterval = now + period;
                    yield false;
                }
                if (now < state.nextInterval) yield false;
                state.nextInterval = now + period;
                yield true;
            }
            case Rule.TimeTrigger t -> {
                if (day < 0 || lastDayTime < 0 || day == lastDayTime) yield false;
                yield lastDayTime < day ? lastDayTime < t.time() && t.time() <= day : t.time() > lastDayTime || t.time() <= day;
            }
        };
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
