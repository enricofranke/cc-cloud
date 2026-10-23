package ai.ezfox.cccloud.rules;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One automation rule of a mainframe (docs/SPEC.md v0.5 C): <b>when</b> a trigger fires, <b>if</b> all conditions hold,
 * <b>then</b> run up to {@link #MAX_ACTIONS} actions. Immutable; parsed and validated by {@link RuleCodec}.
 *
 * @param cooldown seconds between two runs (at least {@link #MIN_COOLDOWN})
 * @param push     ("notify" in JSON) also send the run as a push notification (v0.5 D)
 */
public record Rule(String id, String name, boolean enabled, int cooldown, Trigger when, List<Condition> conditions, List<Action> actions, boolean push) {
    public static final int MIN_COOLDOWN = 5;
    public static final int MAX_COOLDOWN = 86_400;
    public static final int MAX_ACTIONS = 10;
    public static final int MAX_CONDITIONS = 8;

    public Rule {
        conditions = List.copyOf(conditions);
        actions = List.copyOf(actions);
    }

    public Rule withEnabled(boolean value) {
        return new Rule(id, name, value, cooldown, when, conditions, actions, push);
    }

    public Rule withId(String value) {
        return new Rule(value, name, enabled, cooldown, when, conditions, actions, push);
    }

    public enum Cmp {
        LT("<"), LE("<="), GT(">"), GE(">="), EQ("=="), NE("!=");

        public final String symbol;

        Cmp(String symbol) {
            this.symbol = symbol;
        }

        public boolean test(double a, double b) {
            return switch (this) {
                case LT -> a < b;
                case LE -> a <= b;
                case GT -> a > b;
                case GE -> a >= b;
                case EQ -> a == b;
                case NE -> a != b;
            };
        }

        public static @Nullable Cmp of(@Nullable String symbol) {
            for (var c : values()) if (c.symbol.equals(symbol)) return c;
            return null;
        }
    }

    /** A comparison that is true or false at any moment (used as edge trigger and as condition). */
    public sealed interface Check permits WidgetCheck, ItemsCheck, PowerCheck, OnlineCheck {
    }

    /** A numeric widget (booleans count as 0/1) of any card, including template cards. */
    public record WidgetCheck(String uplinkId, String widgetId, Cmp cmp, double value) implements Check {
    }

    /** Item count in the storage behind a bridge card. */
    public record ItemsCheck(String uplinkId, String item, Cmp cmp, double value) implements Check {
    }

    /** The mainframe's FE buffer in percent. */
    public record PowerCheck(Cmp cmp, double value) implements Check {
    }

    /** A device (uplink/bridge card) is online. */
    public record OnlineCheck(String uplinkId) implements Check {
    }

    public sealed interface Trigger permits CheckTrigger, OfflineTrigger, IntervalTrigger, TimeTrigger {
    }

    /** Fires when the check becomes true (edge). */
    public record CheckTrigger(Check check) implements Trigger {
    }

    /** Fires when the device goes offline (edge). */
    public record OfflineTrigger(String uplinkId) implements Trigger {
    }

    /** Fires every N minutes (1–1440). */
    public record IntervalTrigger(int minutes) implements Trigger {
    }

    /** Fires when the in-game time of day passes {@code time} (0–23999 ticks; 0 = 6:00, 6000 = noon, 13000 = dusk). */
    public record TimeTrigger(int time) implements Trigger {
    }

    public record Condition(Check check) {
    }

    public sealed interface Action permits RedstoneAction, WidgetAction, CraftAction, AlertAction, DashboardAction {
    }

    /** Set a redstone output on an auto-mode computer card (widget {@code redstone}). */
    public record RedstoneAction(String uplinkId, String side, int value) implements Action {
    }

    /** Press a button / set a toggle, slider, select or number (same validation as a human action, never PIN widgets). */
    public record WidgetAction(String uplinkId, String widgetId, com.google.gson.JsonPrimitive value) implements Action {
    }

    /** Request a craft on a storage card (bridge), bounded by the bridges' maxCraftAmount. */
    public record CraftAction(String uplinkId, String item, long amount) implements Action {
    }

    /** Send an alert to the dashboard (and push targets, v0.5 D). */
    public record AlertAction(String text, String level) implements Action {
    }

    /** Switch the mainframe's dashboard on or off. */
    public record DashboardAction(boolean enabled) implements Action {
    }
}
