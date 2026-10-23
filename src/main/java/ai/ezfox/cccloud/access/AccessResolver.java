package ai.ezfox.cccloud.access;

import ai.ezfox.cccloud.web.AuthStore;
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

    public record Resolution(Role role, Source source, @Nullable String teamRank) {
        public static final Resolution NONE = new Resolution(Role.NONE, Source.NONE, null);
    }

    private final AccessStore store;
    private final ServerPermissions permissions;
    private final Function<String, @Nullable TeamProvider> teams;

    public AccessResolver(AccessStore store, ServerPermissions permissions, Function<String, @Nullable TeamProvider> teams) {
        this.store = store;
        this.permissions = permissions;
        this.teams = teams;
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

    /** Full resolution (role and the source it came from, for the Access page and the audit log). */
    public Resolution resolve(UUID player, UUID mainframeId) {
        if (permissions.banned(player) || !permissions.has(player, ServerPermissions.Node.USE)) return Resolution.NONE;
        var best = Resolution.NONE;
        var access = store.access(mainframeId);
        if (access != null) {
            if (player.equals(access.owner())) return new Resolution(Role.OWNER, Source.OWNER, null);
            var grant = access.grants().get(player);
            if (grant != null) best = higher(best, new Resolution(grant.role(), Source.DIRECT, null));
            var team = access.team();
            if (team != null) {
                var provider = teams.apply(team.provider());
                String rank = provider == null ? null : provider.rankOf(team.teamId(), player);
                if (rank != null) {
                    Role mapped = team.mapping().get(rank);
                    if (mapped == null) mapped = provider.defaultMapping().getOrDefault(rank, Role.NONE);
                    best = higher(best, new Resolution(mapped, Source.TEAM, rank));
                }
            }
        }
        // Server admins (moderation), visible in the owner's audit log.
        if (best.role().rank < Role.ADMIN.rank && permissions.has(player, ServerPermissions.Node.ADMIN_MANAGE)) {
            best = higher(best, new Resolution(Role.ADMIN, Source.SERVER_ADMIN, null));
        } else if (best.role().rank < Role.OPERATOR.rank && permissions.has(player, ServerPermissions.Node.ADMIN_OPERATE)) {
            best = higher(best, new Resolution(Role.OPERATOR, Source.SERVER_ADMIN, null));
        } else if (best.role().rank < Role.VIEWER.rank && permissions.has(player, ServerPermissions.Node.ADMIN_VIEW)) {
            best = higher(best, new Resolution(Role.VIEWER, Source.SERVER_ADMIN, null));
        }
        return best;
    }

    /**
     * Role of a browser on a mainframe: the role of the player it belongs to, limited to the device's mainframe (v0.3
     * device tokens and mainframe pairing codes) and capped at viewer for read-only devices.
     */
    public Resolution resolveDevice(AuthStore.Device device, UUID mainframeId) {
        if (device.mainframeId() != null && !device.mainframeId().equals(mainframeId)) return Resolution.NONE;
        var r = resolve(device.player(), mainframeId);
        if (!device.canControl() && r.role().rank > Role.VIEWER.rank) return new Resolution(Role.VIEWER, r.source(), r.teamRank());
        return r;
    }

    public Role deviceRole(AuthStore.Device device, UUID mainframeId) {
        return resolveDevice(device, mainframeId).role();
    }

    private static Resolution higher(Resolution a, Resolution b) {
        return b.role().rank > a.role().rank ? b : a;
    }
}
