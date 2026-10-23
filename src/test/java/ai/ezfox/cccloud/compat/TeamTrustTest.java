package ai.ezfox.cccloud.compat;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessResolver;
import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.access.ServerPermissions;
import ai.ezfox.cccloud.access.TeamProviders;
import ai.ezfox.cccloud.compat.team.TeamSource;
import ai.ezfox.cccloud.compat.team.Teams;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** v0.5 team trust folded into the v0.6 AccessResolver: the owner's FTB team maps ranks to roles (fake team source). */
class TeamTrustTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID ally = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID team = UUID.randomUUID();
    private final UUID mainframe = UUID.randomUUID();

    private class FakeSource implements TeamSource {
        final Map<UUID, Rank> ranks = new HashMap<>(Map.of(owner, Rank.OWNER, officer, Rank.OFFICER, member, Rank.MEMBER, ally, Rank.ALLY));

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public Optional<Team> teamOf(UUID player) {
            var rank = ranks.get(player);
            return rank != null && rank.isMember() ? Optional.of(new Team(team, "Base", 0xFF0000)) : Optional.empty();
        }

        @Override
        public Rank rank(UUID teamId, UUID player) {
            return teamId.equals(team) ? ranks.getOrDefault(player, Rank.NONE) : Rank.NONE;
        }

        @Override
        public Set<UUID> members(UUID teamId) {
            return ranks.keySet();
        }

        @Override
        public Optional<Team> team(UUID teamId) {
            return teamId.equals(team) ? Optional.of(new Team(team, "Base", 0)) : Optional.empty();
        }
    }

    private static final ServerPermissions PLAIN = new ServerPermissions() {
        @Override
        public boolean banned(UUID player) {
            return false;
        }

        @Override
        public boolean has(UUID player, Node node) {
            return node == Node.USE;
        }

        @Override
        public int mainframeLimit(UUID player) {
            return 3;
        }
    };

    private AccessResolver resolver() {
        var store = new AccessStore(System::currentTimeMillis, () -> {
        });
        store.setOwner(mainframe, owner, "Owner");
        return new AccessResolver(store, PLAIN, TeamProviders::get, TeamTrust::ownerTeam);
    }

    @AfterEach
    void reset() {
        Teams.setSource(null);
        TeamTrust.setEnabled(() -> true);
    }

    @Test
    void ownersTeamRanksMapToRoles() {
        var source = new FakeSource();
        Teams.setSource(source);
        var resolver = resolver();
        assertEquals(Role.OWNER, resolver.role(owner, mainframe));
        assertEquals(Role.ADMIN, resolver.role(officer, mainframe), "officer → admin");
        assertEquals(Role.OPERATOR, resolver.role(member, mainframe), "member → operator");
        assertEquals(Role.VIEWER, resolver.role(ally, mainframe), "ally → viewer");
        assertEquals(Role.NONE, resolver.role(stranger, mainframe));
        assertEquals(AccessResolver.Source.TEAM, resolver.resolve(member, mainframe).source());

        source.ranks.remove(member);
        assertEquals(Role.NONE, resolver.role(member, mainframe), "leaving the team removes the role immediately");

        TeamTrust.setEnabled(() -> false);
        assertEquals(Role.NONE, resolver.role(officer, mainframe), "config switch ftbTeamsTrust=false");
        assertEquals(Role.OWNER, resolver.role(owner, mainframe), "the owner is unaffected");
    }

    @Test
    void explicitTeamAssignmentReplacesTheOwnersTeam() {
        Teams.setSource(new FakeSource());
        var resolver = resolver();
        resolver.store().setTeam(mainframe, new ai.ezfox.cccloud.access.MainframeAccess.TeamBinding("fake", UUID.randomUUID().toString(), Map.of()));
        assertEquals(Role.NONE, resolver.role(member, mainframe), "another team is assigned");
    }

    @Test
    void noSourceOrFailingSource() {
        assertNull(TeamTrust.ownerTeam(owner), "without a team mod nobody gets a role through teams");
        assertEquals(Role.NONE, resolver().role(member, mainframe));
        Teams.setSource(new FakeSource() {
            @Override
            public Optional<Team> teamOf(UUID player) {
                throw new IllegalStateException("manager not loaded");
            }
        });
        assertNull(TeamTrust.ownerTeam(owner));
        assertEquals(Role.NONE, resolver().role(member, mainframe));
    }
}
