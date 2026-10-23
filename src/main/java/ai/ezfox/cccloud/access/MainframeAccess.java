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

    /** Extra permissions on top of a role preset ({@code deny} wins). */
    public record Overrides(java.util.Set<Perm> allow, java.util.Set<Perm> deny) {
        public static final Overrides NONE = new Overrides(java.util.Set.of(), java.util.Set.of());

        public Overrides {
            allow = java.util.Set.copyOf(allow);
            deny = java.util.Set.copyOf(deny);
        }

        public boolean isEmpty() {
            return allow.isEmpty() && deny.isEmpty();
        }
    }

    public record Grant(String name, Role role, Overrides overrides) {
        public Grant(String name, Role role) {
            this(name, role, Overrides.NONE);
        }
    }

    /**
     * @param mapping   rank → role ({@link Role#NONE} = this rank gets no access)
     * @param overrides rank → extra allow/deny on top of the rank's role
     */
    public record TeamBinding(String provider, String teamId, Map<String, Role> mapping, Map<String, Overrides> overrides) {
        public TeamBinding(String provider, String teamId, Map<String, Role> mapping) {
            this(provider, teamId, mapping, Map.of());
        }

        public TeamBinding {
            mapping = Collections.unmodifiableMap(new LinkedHashMap<>(mapping));
            overrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
        }

        public Overrides overridesFor(String rank) {
            return overrides.getOrDefault(rank, Overrides.NONE);
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

    /** Sets a direct role (keeping custom permissions); {@link Role#NONE} removes the grant. */
    public MainframeAccess withGrant(UUID player, String name, Role role) {
        var old = grants.get(player);
        return withGrant(player, name, role, old == null ? Overrides.NONE : old.overrides());
    }

    public MainframeAccess withGrant(UUID player, String name, Role role, Overrides overrides) {
        var g = new LinkedHashMap<>(grants);
        if (role == Role.NONE) g.remove(player);
        else g.put(player, new Grant(name, role, overrides));
        return new MainframeAccess(owner, ownerName, g, team);
    }

    public MainframeAccess withTeam(@Nullable TeamBinding binding) {
        return new MainframeAccess(owner, ownerName, grants, binding);
    }
}
