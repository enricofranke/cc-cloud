package ai.ezfox.cccloud.access;

import ai.ezfox.cccloud.web.AuthStore;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The one place that answers "which role does this player have on this mainframe" (docs/SPEC.md v0.6). Every
 * permission check (web, in-game screens, commands, Link Cards) goes through here. The highest role from all sources
 * wins: owner, direct grant, team rank (via a {@link TeamProvider}) and the server-wide admin permission nodes. Banned
 * players and players without {@code cccloud.use} get {@link Role#NONE}.
 * <p>
 * Pure Java (unit tested with fake permissions and teams). In the game it is called on the server thread.
 */
public final class AccessResolver {
    public enum Source {
        OWNER("owner"), DIRECT("direct"), TEAM("team"), SERVER_ADMIN("server_admin"), NONE("none");

        public final String id;

        Source(String id) {
            this.id = id;
        }
    }

    /**
     * @param role  the highest role from all sources (for display)
     * @param perms the effective permissions: union over the sources of {@code preset(role) ∪ allow − deny}, minus
     *              features the server switched off
     */
    public record Resolution(Role role, Source source, @Nullable String teamRank, Set<Perm> perms) {
        public static final Resolution NONE = new Resolution(Role.NONE, Source.NONE, null, Set.of());

        public Resolution(Role role, Source source, @Nullable String teamRank) {
            this(role, source, teamRank, Perm.preset(role));
        }

        public boolean has(Perm perm) {
            return perms.contains(perm);
        }
    }

    private final AccessStore store;
    private final ServerPermissions permissions;
    private final Function<String, @Nullable TeamProvider> teams;
    private final Function<UUID, MainframeAccess.@Nullable TeamBinding> ownerTeam;

    public AccessResolver(AccessStore store, ServerPermissions permissions, Function<String, @Nullable TeamProvider> teams) {
        this(store, permissions, teams, owner -> null);
    }

    /**
     * @param ownerTeam the implicit team of a mainframe without an explicit assignment (v0.5 "owner's FTB team"
     *                  trust, config {@code ftbTeamsTrust}); null if none
     */
    public AccessResolver(AccessStore store, ServerPermissions permissions, Function<String, @Nullable TeamProvider> teams,
                          Function<UUID, MainframeAccess.@Nullable TeamBinding> ownerTeam) {
        this.store = store;
        this.permissions = permissions;
        this.teams = teams;
        this.ownerTeam = ownerTeam;
    }

    /** The team whose ranks give roles on this mainframe: the assigned one, else the owner's team (if enabled). */
    public MainframeAccess.@Nullable TeamBinding effectiveTeam(@Nullable MainframeAccess access) {
        if (access == null) return null;
        if (access.team() != null) return access.team();
        return access.owner() == null ? null : ownerTeam.apply(access.owner());
    }

    public AccessStore store() {
        return store;
    }

    public ServerPermissions permissions() {
        return permissions;
    }

    public Role role(UUID player, UUID mainframeId) {
        return resolve(player, mainframeId).role();
    }

    /** Full resolution: role, the source it came from, and the effective permissions. */
    public Resolution resolve(UUID player, UUID mainframeId) {
        if (permissions.banned(player) || !permissions.has(player, ServerPermissions.Node.USE)) return Resolution.NONE;
        var access = store.access(mainframeId);
        if (access != null && player.equals(access.owner())) return withFeatures(player, new Resolution(Role.OWNER, Source.OWNER, null, Perm.all()));
        var best = Resolution.NONE;
        var perms = EnumSet.noneOf(Perm.class);
        // A deny on any source (the player's own grant or their team rank) removes the permission from the result, so a
        // team role cannot bring back what the owner denied directly.
        var denied = EnumSet.noneOf(Perm.class);
        if (access != null) {
            var grant = access.grants().get(player);
            if (grant != null) {
                best = higher(best, new Resolution(grant.role(), Source.DIRECT, null));
                perms.addAll(Perm.effective(grant.role(), grant.overrides().allow(), grant.overrides().deny()));
                denied.addAll(grant.overrides().deny());
            }
            var team = effectiveTeam(access);
            if (team != null) {
                var provider = teams.apply(team.provider());
                String rank = provider == null ? null : provider.rankOf(team.teamId(), player);
                if (rank != null) {
                    Role mapped = team.mapping().get(rank);
                    if (mapped == null) mapped = provider.defaultMapping().getOrDefault(rank, Role.NONE);
                    best = higher(best, new Resolution(mapped, Source.TEAM, rank));
                    var o = team.overridesFor(rank);
                    perms.addAll(Perm.effective(mapped, o.allow(), o.deny()));
                    denied.addAll(o.deny());
                }
            }
        }
        perms.removeAll(denied);
        // Server admins (moderation), visible in the owner's audit log.
        Role admin = permissions.has(player, ServerPermissions.Node.ADMIN_MANAGE) ? Role.ADMIN
            : permissions.has(player, ServerPermissions.Node.ADMIN_OPERATE) ? Role.OPERATOR
            : permissions.has(player, ServerPermissions.Node.ADMIN_VIEW) ? Role.VIEWER : Role.NONE;
        if (admin.rank > best.role().rank) best = new Resolution(admin, Source.SERVER_ADMIN, null);
        perms.addAll(Perm.preset(admin));
        if (best.role() == Role.NONE || !perms.contains(Perm.VIEW)) return Resolution.NONE;
        return withFeatures(player, new Resolution(best.role(), best.source(), best.teamRank(), perms));
    }

    /**
     * Server-level feature switches (PermissionAPI) remove permissions: {@code cccloud.feature.scripts} the script and
     * terminal permissions, {@code cccloud.api.keys} (v0.10) {@code api.keys}.
     */
    private Resolution withFeatures(UUID player, Resolution r) {
        boolean scripts = permissions.has(player, ServerPermissions.Node.FEATURE_SCRIPTS);
        boolean api = permissions.has(player, ServerPermissions.Node.API_KEYS);
        if (scripts && api) return r;
        var perms = EnumSet.noneOf(Perm.class);
        perms.addAll(r.perms());
        if (!scripts) perms.removeAll(EnumSet.of(Perm.SCRIPTS_UPLOAD, Perm.SCRIPTS_RUN, Perm.SCRIPTS_DELETE, Perm.TERMINAL));
        if (!api) perms.remove(Perm.API_KEYS);
        return new Resolution(r.role(), r.source(), r.teamRank(), perms);
    }

    /**
     * Role of a browser on a mainframe: the role of the player it belongs to, limited to the device's mainframe (v0.3
     * device tokens and mainframe pairing codes) and capped at viewer for read-only devices.
     */
    public Resolution resolveDevice(AuthStore.Device device, UUID mainframeId) {
        if (device.mainframeId() != null && !device.mainframeId().equals(mainframeId)) return Resolution.NONE;
        var r = resolve(device.player(), mainframeId);
        if (!device.canControl() && (r.role().rank > Role.VIEWER.rank || !Perm.preset(Role.VIEWER).containsAll(r.perms()))) {
            // Read-only devices: at most the viewer preset (also for viewers with extra allows).
            var perms = EnumSet.noneOf(Perm.class);
            perms.addAll(r.perms());
            perms.retainAll(Perm.preset(Role.VIEWER));
            return new Resolution(r.role().rank > Role.VIEWER.rank ? Role.VIEWER : r.role(), r.source(), r.teamRank(), perms);
        }
        return r;
    }

    public Role deviceRole(AuthStore.Device device, UUID mainframeId) {
        return resolveDevice(device, mainframeId).role();
    }

    private static Resolution higher(Resolution a, Resolution b) {
        return b.role().rank > a.role().rank ? b : a;
    }
}
