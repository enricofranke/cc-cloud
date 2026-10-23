package ai.ezfox.cccloud.command;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Which mainframe a command means when the player does not look at one (pure, unit tested): the one selected with
 * {@code /cccloud select}, else the only one within {@link #NEAR} blocks, else the only one the player has a role on.
 * Otherwise null: the command lists the candidates (clickable).
 */
public final class TargetPicker {
    public static final double NEAR = 32;

    /** A loaded mainframe the player has (at least the needed) role on; distance -1 = other dimension. */
    public record Candidate(UUID id, double distance) {
    }

    private TargetPicker() {
    }

    public static @Nullable UUID pick(List<Candidate> candidates, @Nullable UUID selected) {
        if (selected != null && candidates.stream().anyMatch(c -> c.id().equals(selected))) return selected;
        var near = candidates.stream().filter(c -> c.distance() >= 0 && c.distance() <= NEAR).toList();
        if (near.size() == 1) return near.getFirst().id();
        if (candidates.size() == 1) return candidates.getFirst().id();
        return null;
    }

    /** Candidates sorted for the list: same dimension by distance first, then the others. */
    public static List<Candidate> sorted(List<Candidate> candidates) {
        return candidates.stream()
            .sorted(Comparator.comparingDouble((Candidate c) -> c.distance() < 0 ? Double.MAX_VALUE : c.distance()))
            .toList();
    }
}
