package ai.ezfox.cccloud.compat.ftbteams;

import ai.ezfox.cccloud.compat.team.TeamSource;
import ai.ezfox.cccloud.compat.team.Teams;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.neoforged.neoforge.common.NeoForge;

/**
 * FTB Teams as a {@link TeamSource}: a thin adapter over FTB Teams' public API ({@code dev.ftb.mods.ftbteams.api}) that
 * reports team membership and ranks and forwards FTB's team change events. No permission decisions here.
 * Only loaded when {@code ftbteams} is present.
 */
public final class FtbTeamsSource implements TeamSource {
    private FtbTeamsSource() {
    }

    public static void init() {
        Teams.setSource(new FtbTeamsSource());
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(FTBTeamsEvent.PlayerChangedTeam.class, e -> Teams.fireChanged(e.getEventData().playerId()));
        bus.addListener(FTBTeamsEvent.PlayerJoinedPartyTeam.class, e -> Teams.fireChanged(e.getEventData().player().getUUID()));
        bus.addListener(FTBTeamsEvent.PlayerLeftPartyTeam.class, e -> Teams.fireChanged(e.getEventData().playerId()));
        bus.addListener(FTBTeamsEvent.TeamDeleted.class, e -> fireAll(e.getEventData().team()));
        bus.addListener(FTBTeamsEvent.PlayerTransferredOwnership.class, e -> fireAll(e.getEventData().team()));
        bus.addListener(FTBTeamsEvent.TeamAlly.class, e -> e.getEventData().players().forEach(p -> Teams.fireChanged(p.id())));
    }

    private static void fireAll(dev.ftb.mods.ftbteams.api.Team team) {
        for (var member : new HashSet<>(team.getMembers())) Teams.fireChanged(member);
    }

    private static boolean loaded() {
        return FTBTeamsAPI.api().isManagerLoaded();
    }

    @Override
    public String id() {
        return "ftbteams";
    }

    @Override
    public Optional<TeamSource.Team> teamOf(UUID player) {
        if (!loaded()) return Optional.empty();
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(player).map(FtbTeamsSource::convert);
    }

    static TeamSource.Team convert(dev.ftb.mods.ftbteams.api.Team team) {
        int color = 0xFFFFFF;
        try {
            color = team.getProperty(TeamProperties.COLOR).rgb() & 0xFFFFFF;
        } catch (RuntimeException ignored) {
        }
        return new TeamSource.Team(team.getId(), team.getShortName(), color);
    }

    @Override
    public Rank rank(UUID teamId, UUID player) {
        if (!loaded()) return Rank.NONE;
        var team = FTBTeamsAPI.api().getManager().getTeamByID(teamId);
        return team.map(t -> convert(t.getRankForPlayer(player))).orElse(Rank.NONE);
    }

    static Rank convert(TeamRank rank) {
        return switch (rank) {
            case OWNER -> Rank.OWNER;
            case OFFICER -> Rank.OFFICER;
            case MEMBER -> Rank.MEMBER;
            case ALLY -> Rank.ALLY;
            default -> Rank.NONE;
        };
    }

    @Override
    public Optional<TeamSource.Team> team(UUID teamId) {
        if (!loaded()) return Optional.empty();
        return FTBTeamsAPI.api().getManager().getTeamByID(teamId).map(FtbTeamsSource::convert);
    }

    @Override
    public Set<UUID> members(UUID teamId) {
        if (!loaded()) return Set.of();
        return FTBTeamsAPI.api().getManager().getTeamByID(teamId).map(t -> Set.copyOf(t.getMembers())).orElse(Set.of());
    }
}
