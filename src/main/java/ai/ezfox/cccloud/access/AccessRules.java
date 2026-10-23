package ai.ezfox.cccloud.access;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The one rank rule for every way of changing access (web API, mainframe screen, commands; docs/SECURITY.md):
 * <ul>
 *   <li>Moderators (the owner, or the server node {@code cccloud.admin.manage}) may change everyone.</li>
 *   <li>Everybody else needs {@code access.manage}, may only change players below their own role (never themselves,
 *       never the owner), never grants a role above their own, and never grants a permission they do not hold
 *       themselves – checked against the whole result (the role's preset plus the extra allows), not only the allow
 *       list.</li>
 *   <li>The same holds for team rank mappings and for assigning a team (its default mapping counts as granted).</li>
 * </ul>
 * Pure Java, unit tested; every entry point calls it.
 */
public final class AccessRules {
    /** Who is asking: role and effective permissions on the mainframe, and whether they moderate it. */
    public record Caller(Role role, Set<Perm> perms, boolean moderator) {
    }

    private AccessRules() {
    }

    /**
     * May the caller set a player's grant to {@code newRole} (NONE = remove) with these overrides?
     *
     * @param currentRole the player's current grant (NONE if none)
     * @param target      the player is the caller themselves
     * @return null if allowed, else {@code not_allowed}
     */
    public static @Nullable String changeGrant(Caller caller, Role currentRole, boolean targetIsOwner, boolean target, Role newRole,
                                               Set<Perm> allow) {
        if (targetIsOwner || newRole == Role.OWNER) return "not_allowed";
        if (caller.moderator()) return null;
        if (!caller.perms().contains(Perm.ACCESS_MANAGE)) return "not_allowed";
        if (target && newRole != Role.NONE) return "not_allowed"; // nobody raises or reshapes their own grant
        if (!target && currentRole.rank >= caller.role().rank) return "not_allowed";
        if (newRole.rank > caller.role().rank) return "not_allowed";
        return grants(caller, newRole, allow);
    }

    /** May the caller map a team rank to {@code role} (with these extra allows)? */
    public static @Nullable String changeMapping(Caller caller, Role role, Set<Perm> allow) {
        if (role == Role.OWNER) return "not_allowed";
        if (caller.moderator()) return null;
        if (!caller.perms().contains(Perm.ACCESS_MANAGE) || role.rank > caller.role().rank) return "not_allowed";
        return grants(caller, role, allow);
    }

    /** May the caller bind a team whose ranks map to these roles (the provider's default mapping)? */
    public static @Nullable String bindTeam(Caller caller, Map<String, Role> mapping) {
        if (caller.moderator()) return null;
        if (!caller.perms().contains(Perm.ACCESS_MANAGE)) return "not_allowed";
        for (var role : mapping.values()) {
            String error = changeMapping(caller, role, Set.of());
            if (error != null) return error;
        }
        return null;
    }

    /** The caller holds everything the role's preset and the allows would give. */
    private static @Nullable String grants(Caller caller, Role role, Set<Perm> allow) {
        if (role == Role.NONE) return null;
        var given = EnumSet.noneOf(Perm.class);
        given.addAll(Perm.preset(role));
        given.addAll(allow);
        return caller.perms().containsAll(given) ? null : "not_allowed";
    }

    /**
     * The role a {@code trust} grants: {@code max} (operator from the screen, admin from the command), but never more
     * than the caller's own role; moderators get {@code max}.
     */
    public static Role trustRole(Caller caller, Role max) {
        if (caller.moderator()) return max;
        return caller.role().rank >= max.rank ? max : caller.role();
    }
}
