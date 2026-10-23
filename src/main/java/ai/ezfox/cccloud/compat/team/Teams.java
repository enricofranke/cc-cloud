package ai.ezfox.cccloud.compat.team;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Holds the active {@link TeamSource} (if a team mod is installed) and fans out its change events. Pure Java.
 */
public final class Teams {
    private static volatile @Nullable TeamSource source;
    private static final List<Consumer<UUID>> LISTENERS = new CopyOnWriteArrayList<>();

    private Teams() {
    }

    public static void setSource(@Nullable TeamSource teamSource) {
        source = teamSource;
    }

    public static @Nullable TeamSource source() {
        return source;
    }

    /** Called with the id of a player whose team membership or rank changed (joined, left, kicked, team deleted, ...). */
    public static void onChange(Consumer<UUID> listener) {
        LISTENERS.add(listener);
    }

    /** For team adapters: a player's membership changed. Listener failures are ignored. */
    public static void fireChanged(UUID player) {
        for (var listener : LISTENERS) {
            try {
                listener.accept(player);
            } catch (RuntimeException ignored) {
            }
        }
    }
}
