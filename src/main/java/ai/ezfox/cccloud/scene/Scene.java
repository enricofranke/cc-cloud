package ai.ezfox.cccloud.scene;

import ai.ezfox.cccloud.rules.Rule;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One scene of a mainframe (docs/SPEC.md v0.10 "Scenes"): a named button that runs up to {@link #MAX_STEPS} steps in
 * order – the rule action types plus delays. Immutable; parsed and validated by {@link SceneCodec}.
 *
 * @param owner     the player who saved it last (scheduled runs use their live rights); null before the first save
 * @param ownerName that player's name when it was saved (display only)
 */
public record Scene(String id, String name, @Nullable String icon, @Nullable String color, boolean confirm, boolean pin, List<Step> steps,
                    @Nullable UUID owner, String ownerName) {
    public static final int MAX_STEPS = 16;
    public static final int MAX_NAME = 32;
    public static final double MIN_DELAY = 0.05;
    public static final double MAX_DELAY = 300;
    /** All delays of a scene together, in seconds. */
    public static final double MAX_TOTAL_DELAY = 600;

    public Scene {
        steps = List.copyOf(steps);
    }

    public Scene withId(String value) {
        return new Scene(value, name, icon, color, confirm, pin, steps, owner, ownerName);
    }

    public Scene withOwner(UUID player, String playerName) {
        return new Scene(id, name, icon, color, confirm, pin, steps, player, playerName);
    }

    /** The id of the scene's button in the Scenes section. */
    public String widgetId() {
        return SceneService.WIDGET_PREFIX + id;
    }

    /**
     * One step: an action (the rule action types, never a scene) or a delay.
     *
     * @param delayTicks server ticks to wait (delays only)
     * @param seconds    the delay as entered (delays only)
     */
    public record Step(Rule.@Nullable Action action, int delayTicks, double seconds) {
        public static Step of(Rule.Action action) {
            return new Step(action, 0, 0);
        }

        public static Step delay(double seconds) {
            return new Step(null, (int) Math.ceil(seconds * 20 - 1e-9), seconds);
        }

        public boolean isDelay() {
            return action == null;
        }
    }
}
