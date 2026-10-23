package ai.ezfox.cccloud.access;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Who has which role on one mainframe (immutable). Stored in the world's web SavedData so rights can be checked while
 * the mainframe's chunk is unloaded.
 *
 * @param grants direct roles per player ({@code /cccloud role}, screen, web Access page); never {@link Role#OWNER}
 * @param team   the team this mainframe is assigned to, or null
 */
public record MainframeAccess(@Nullable UUID owner, String ownerName, Map<UUID, Grant> grants, @Nullable TeamBinding team) {
    public static final int MAX_GRANTS = 256;

    public record Grant(String name, Role role) {
    }

    /** @param mapping rank → role ({@link Role#NONE} = this rank gets no access) */
    public record TeamBinding(String provider, String teamId, Map<String, Role> mapping) {
        public TeamBinding {
            mapping = Collections.unmodifiableMap(new LinkedHashMap<>(mapping));
        }
    }

    public MainframeAccess {
        grants = Collections.unmodifiableMap(new LinkedHashMap<>(grants));
    }

    public static MainframeAccess empty() {
        return new MainframeAccess(null, "", Map.of(), null);
    }

    public MainframeAccess withOwner(@Nullable UUID owner, String name) {
        var g = new LinkedHashMap<>(grants);
        if (owner != null) g.remove(owner);
        return new MainframeAccess(owner, name, g, team);
    }

    /** Sets a direct role; {@link Role#NONE} removes the grant. */
    public MainframeAccess withGrant(UUID player, String name, Role role) {
        var g = new LinkedHashMap<>(grants);
        if (role == Role.NONE) g.remove(player);
        else g.put(player, new Grant(name, role));
        return new MainframeAccess(owner, ownerName, g, team);
    }

    public MainframeAccess withTeam(@Nullable TeamBinding binding) {
        return new MainframeAccess(owner, ownerName, grants, binding);
    }
}
