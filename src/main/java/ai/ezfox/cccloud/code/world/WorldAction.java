package ai.ezfox.cccloud.code.world;

import org.jspecify.annotations.Nullable;

/**
 * Something a script asks the game to do. Card, rule and scene references are already resolved to ids against the
 * script's snapshot; the game checks everything again on the server thread with the script's live rights (the same
 * checks as a person on the dashboard) and answers {@code {true}} or {@code {false, error}}.
 */
public sealed interface WorldAction {
    /** The permission id the action needs at least (the game may need a different one, e.g. act.redstone for a lever). */
    String perm();

    /** One line for the audit log and the script log: {@code set Reactor/active = false}. */
    String describe();

    record Press(String cardId, String cardTitle, String widgetId, boolean confirmed) implements WorldAction {
        public String perm() {
            return "act.buttons";
        }

        public String describe() {
            return "pressed " + cardTitle + "/" + widgetId;
        }
    }

    /** {@code value}: Boolean, Double or String. */
    record Set(String cardId, String cardTitle, String widgetId, Object value, boolean confirmed) implements WorldAction {
        public String perm() {
            return "act.buttons";
        }

        public String describe() {
            return "set " + cardTitle + "/" + widgetId + " = " + value;
        }
    }

    record SetRedstone(String cardId, String cardTitle, String side, int level) implements WorldAction {
        public String perm() {
            return "act.redstone";
        }

        public String describe() {
            return "set redstone " + cardTitle + "/" + side + " = " + level;
        }
    }

    record Craft(String cardId, String cardTitle, String item, long amount) implements WorldAction {
        public String perm() {
            return "storage.craft";
        }

        public String describe() {
            return "crafting " + amount + "x " + item + " on " + cardTitle;
        }
    }

    record SetEnabled(boolean enabled) implements WorldAction {
        public String perm() {
            return "power.toggle";
        }

        public String describe() {
            return "switched the dashboard " + (enabled ? "on" : "off");
        }
    }

    record RunScene(String sceneId, String sceneName, boolean confirmed) implements WorldAction {
        public String perm() {
            return "act.buttons";
        }

        public String describe() {
            return "started scene " + sceneName;
        }
    }

    record SetRuleEnabled(String ruleId, String ruleName, boolean enabled) implements WorldAction {
        public String perm() {
            return "rules.toggle";
        }

        public String describe() {
            return "switched rule " + ruleName + (enabled ? " on" : " off");
        }
    }

    record RunRule(String ruleId, String ruleName) implements WorldAction {
        public String perm() {
            return "rules.toggle";
        }

        public String describe() {
            return "ran rule " + ruleName;
        }
    }

    /** A read that needs the server thread: an item count behind a storage card. Not audited. */
    record ItemCount(String cardId, String cardTitle, String item) implements WorldAction {
        public String perm() {
            return "storage.search";
        }

        public String describe() {
            return "counted " + item + " on " + cardTitle;
        }
    }

    /** Whether the action changes something (audited) or only reads. */
    static boolean changes(WorldAction action) {
        return !(action instanceof ItemCount);
    }

    /** {@code true} or {@code false, error}. */
    static Object[] ok() {
        return new Object[]{true};
    }

    static Object[] fail(@Nullable String error) {
        return new Object[]{false, error == null ? "failed" : error};
    }
}
