package ai.ezfox.cccloud.compat.team;

import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.access.TeamProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A v0.5 {@link TeamSource} (FTB Teams adapter) as a v0.6 {@link TeamProvider}. Ranks map to roles per docs/SPEC.md:
 * owner/officer → admin, member → operator, ally → viewer (changeable per mainframe on the Access page). Pure Java.
 */
public final class TeamSourceProvider implements TeamProvider {
    private static final List<String> RANKS = List.of("owner", "officer", "member", "ally");
    /** Player names for the Access page (set by the game; tests leave the default). */
    private static volatile Function<UUID, String> names = id -> "";

    private final TeamSource source;

    public TeamSourceProvider(TeamSource source) {
        this.source = source;
    }

    public static void setNames(Function<UUID, String> resolver) {
        names = resolver;
    }

    @Override
    public String id() {
        return source.id();
    }

    @Override
    public List<String> ranks() {
        return RANKS;
    }

    @Override
    public Map<String, Role> defaultMapping() {
        var map = new LinkedHashMap<String, Role>();
        map.put("owner", Role.ADMIN);
        map.put("officer", Role.ADMIN);
        map.put("member", Role.OPERATOR);
        map.put("ally", Role.VIEWER);
        return map;
    }

    private static @Nullable UUID parse(String teamId) {
        try {
            return UUID.fromString(teamId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public @Nullable String rankOf(String teamId, UUID player) {
        var id = parse(teamId);
        if (id == null) return null;
        try {
            var rank = source.rank(id, player);
            return rank == TeamSource.Rank.NONE ? null : rank.name().toLowerCase(Locale.ROOT);
        } catch (RuntimeException | LinkageError e) {
            return null; // failures count as "not in the team"
        }
    }

    @Override
    public @Nullable Team team(String teamId) {
        var id = parse(teamId);
        if (id == null) return null;
        try {
            return source.team(id).map(t -> new Team(t.id().toString(), t.name())).orElse(null);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Override
    public List<Team> teamsOf(UUID player) {
        try {
            return source.teamOf(player).map(t -> List.of(new Team(t.id().toString(), t.name()))).orElse(List.of());
        } catch (RuntimeException | LinkageError e) {
            return List.of();
        }
    }

    @Override
    public List<Member> members(String teamId) {
        var id = parse(teamId);
        var list = new ArrayList<Member>();
        if (id == null) return list;
        try {
            for (var player : source.members(id)) {
                String rank = rankOf(teamId, player);
                if (rank != null) list.add(new Member(player, names.apply(player), rank));
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return list;
    }

    /** The team the owner plays in, as a provider team id (v0.5 "owner's team" default). */
    public @Nullable String ownerTeam(UUID owner) {
        try {
            return source.teamOf(owner).map(t -> t.id().toString()).orElse(null);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
