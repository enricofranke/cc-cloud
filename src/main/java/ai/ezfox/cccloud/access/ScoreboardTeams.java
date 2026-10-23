package ai.ezfox.cccloud.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

/** Vanilla scoreboard teams ({@code /team}) as a {@link TeamProvider}: one rank, {@code member}. The fallback provider. */
public final class ScoreboardTeams implements TeamProvider {
    public static final String ID = "scoreboard";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return ServerLifecycleHooks.getCurrentServer() != null;
    }

    @Override
    public List<String> ranks() {
        return List.of("member");
    }

    @Override
    public Map<String, Role> defaultMapping() {
        return Map.of("member", Role.OPERATOR);
    }

    @Override
    public @Nullable String rankOf(String teamId, UUID player) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        String name = CloudPermissions.nameOf(server, player);
        if (name.isEmpty()) return null;
        var team = server.getScoreboard().getPlayersTeam(name);
        return team != null && team.getName().equals(teamId) ? "member" : null;
    }

    @Override
    public @Nullable Team team(String teamId) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        var team = server.getScoreboard().getPlayerTeam(teamId);
        return team == null ? null : new Team(team.getName(), team.getDisplayName().getString());
    }

    @Override
    public List<Team> teamsOf(UUID player) {
        var server = ServerLifecycleHooks.getCurrentServer();
        var list = new ArrayList<Team>();
        if (server == null) return list;
        String name = CloudPermissions.nameOf(server, player);
        var team = name.isEmpty() ? null : server.getScoreboard().getPlayersTeam(name);
        if (team != null) list.add(new Team(team.getName(), team.getDisplayName().getString()));
        return list;
    }

    /** Online members only: scoreboard teams store names, and offline names are not resolved without network lookups. */
    @Override
    public List<Member> members(String teamId) {
        var server = ServerLifecycleHooks.getCurrentServer();
        var list = new ArrayList<Member>();
        if (server == null) return list;
        var team = server.getScoreboard().getPlayerTeam(teamId);
        if (team == null) return list;
        for (var player : server.getPlayerList().getPlayers()) {
            if (team.getPlayers().contains(player.getGameProfile().name())) list.add(new Member(player.getUUID(), player.getGameProfile().name(), "member"));
        }
        return list;
    }

    /** All scoreboard teams (offered to server admins). */
    public static List<Team> all(MinecraftServer server) {
        var list = new ArrayList<Team>();
        for (var team : server.getScoreboard().getPlayerTeams()) list.add(new Team(team.getName(), team.getDisplayName().getString()));
        return list;
    }
}
