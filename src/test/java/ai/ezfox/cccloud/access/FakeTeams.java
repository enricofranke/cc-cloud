package ai.ezfox.cccloud.access;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A team provider for tests (FTB-like ranks); membership can change at any time. */
public final class FakeTeams implements TeamProvider {
    /** team id → player → rank */
    public final Map<String, Map<UUID, String>> teams = new HashMap<>();

    public FakeTeams put(String team, UUID player, String rank) {
        teams.computeIfAbsent(team, k -> new LinkedHashMap<>()).put(player, rank);
        return this;
    }

    @Override
    public String id() {
        return "fake";
    }

    @Override
    public List<String> ranks() {
        return List.of("owner", "officer", "member", "ally");
    }

    @Override
    public Map<String, Role> defaultMapping() {
        return Map.of("owner", Role.ADMIN, "officer", Role.ADMIN, "member", Role.OPERATOR, "ally", Role.VIEWER);
    }

    @Override
    public @Nullable String rankOf(String teamId, UUID player) {
        var team = teams.get(teamId);
        return team == null ? null : team.get(player);
    }

    @Override
    public @Nullable Team team(String teamId) {
        return teams.containsKey(teamId) ? new Team(teamId, "Team " + teamId) : null;
    }

    @Override
    public List<Team> teamsOf(UUID player) {
        var list = new ArrayList<Team>();
        for (var e : teams.entrySet()) if (e.getValue().containsKey(player)) list.add(new Team(e.getKey(), "Team " + e.getKey()));
        return list;
    }

    @Override
    public List<Member> members(String teamId) {
        var list = new ArrayList<Member>();
        for (var e : teams.getOrDefault(teamId, Map.of()).entrySet()) list.add(new Member(e.getKey(), "p" + e.getKey().toString().substring(0, 4), e.getValue()));
        return list;
    }
}
