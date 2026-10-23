package ai.ezfox.cccloud.compat;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.compat.team.TeamSource;
import ai.ezfox.cccloud.compat.team.Teams;
import ai.ezfox.cccloud.web.AuthStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The v0.5 team trust rule through the auth store's ACL, with a fake team source. */
class TeamTrustTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID ally = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID team = UUID.randomUUID();

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
    }

    @AfterEach
    void reset() {
        Teams.setSource(null);
        TeamTrust.setEnabled(() -> true);
    }

    @Test
    void membersOfTheOwnersTeamAreTrusted() {
        var source = new FakeSource();
        Teams.setSource(source);
        var acl = new AuthStore.Acl(owner, Set.of());
        assertTrue(acl.allows(owner));
        assertTrue(acl.allows(officer), "officer");
        assertTrue(acl.allows(member), "member");
        assertFalse(acl.allows(ally), "allies are not trusted in v0.5");
        assertFalse(acl.allows(stranger), "stranger");

        source.ranks.remove(member);
        assertFalse(acl.allows(member), "leaving the team removes the trust immediately");

        TeamTrust.setEnabled(() -> false);
        assertFalse(acl.allows(officer), "config switch ftbTeamsTrust=false");
        assertTrue(acl.allows(owner), "the owner is unaffected");
    }

    @Test
    void noSourceOrNoOwner() {
        assertFalse(TeamTrust.trusts(owner, member), "without a team mod nobody is trusted through teams");
        Teams.setSource(new FakeSource());
        assertFalse(TeamTrust.trusts(null, member), "mainframes without owner");
        assertTrue(new AuthStore.Acl(owner, Set.of(stranger)).allows(stranger), "the explicit trust list still works");
    }

    @Test
    void failingSourceCountsAsNotTrusted() {
        Teams.setSource(new FakeSource() {
            @Override
            public Optional<Team> teamOf(UUID player) {
                throw new IllegalStateException("manager not loaded");
            }
        });
        assertFalse(TeamTrust.trusts(owner, member));
    }
}
