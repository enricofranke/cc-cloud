package ai.ezfox.cccloud.claims;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

/**
 * The claim guard's rule (docs/SECURITY.md "Claims"): a device is blocked while the block it reads is in a chunk
 * claimed by a team none of its owners belongs to or is allied with. Plus the pass budget and the team-name filter.
 */
class ClaimRuleTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final UUID OPERATOR = UUID.randomUUID();
    private static final ClaimProvider.Team RIVALS = new ClaimProvider.Team(UUID.randomUUID(), "Rivals");
    private static final ClaimProvider.Team HOME = new ClaimProvider.Team(UUID.randomUUID(), "Home");

    /** Home: Alice is a member, Bob an ally. Rivals: Carol is a member. Nobody else is in either. */
    private static final Map<UUID, Set<UUID>> TEAMS = Map.of(HOME.id(), Set.of(ALICE, BOB), RIVALS.id(), Set.of(CAROL));
    private static final BiPredicate<ClaimProvider.Team, UUID> TRUSTED = (team, player) -> TEAMS.getOrDefault(team.id(), Set.of()).contains(player);

    @Test
    void unclaimedLandIsFine() {
        assertNull(ClaimGuard.rule(null, List.of(ALICE), TRUSTED));
        assertNull(ClaimGuard.rule(null, List.of(), TRUSTED), "even without a known owner");
    }

    @Test
    void ownTeamsClaimIsFine() {
        assertNull(ClaimGuard.rule(HOME, List.of(ALICE), TRUSTED));
    }

    @Test
    void anAllysClaimIsFine() {
        assertNull(ClaimGuard.rule(HOME, List.of(BOB), TRUSTED), "allies count as the same team");
    }

    @Test
    void aForeignTeamsClaimBlocks() {
        var block = ClaimGuard.rule(RIVALS, List.of(ALICE), TRUSTED);
        assertNotNull(block);
        assertEquals(RIVALS.id(), block.teamId());
        assertEquals("Rivals", block.team());
    }

    @Test
    void anyOwnerOfTheTeamIsEnough() {
        // who linked it is a stranger, the mainframe's owner is a member (or the other way round)
        assertNull(ClaimGuard.rule(RIVALS, List.of(ALICE, CAROL), TRUSTED));
        assertNull(ClaimGuard.rule(RIVALS, List.of(CAROL, ALICE), TRUSTED));
        assertNotNull(ClaimGuard.rule(RIVALS, List.of(ALICE, BOB), TRUSTED), "neither owner: blocked");
    }

    @Test
    void noKnownOwnerInAClaimIsBlocked() {
        assertNotNull(ClaimGuard.rule(HOME, List.of(), TRUSTED), "fails closed");
    }

    @Test
    void operatorsGetNoBypass() {
        // like the probe's pick and the map: the rule only asks the claim mod, server operators are players like others
        assertNotNull(ClaimGuard.rule(RIVALS, List.of(OPERATOR), TRUSTED));
    }

    @Test
    void ownersAreTheLinkerAndTheMainframeOwner() {
        assertEquals(List.of(), ClaimGuard.owners(null, null), "unlinked and nobody placed it");
        assertEquals(List.of(ALICE), ClaimGuard.owners(ALICE, null), "unlinked: who placed it (or set the probe's target)");
    }

    @Test
    void thePassChecksEveryDeviceOncePerPeriod() {
        assertEquals(0, ClaimGuard.passSlice(0));
        assertEquals(1, ClaimGuard.passSlice(1));
        assertEquals(1, ClaimGuard.passSlice(ClaimGuard.PASS_TICKS));
        assertEquals(2, ClaimGuard.passSlice(ClaimGuard.PASS_TICKS + 1));
        assertEquals(50, ClaimGuard.passSlice(10_000), "10 000 devices: 50 checks per tick");
        assertEquals(ClaimGuard.MAX_CHECKS_PER_TICK, ClaimGuard.passSlice(10_000_000), "capped");
        for (int n : new int[]{1, 7, 199, 200, 201, 5000, 99_999}) {
            assertTrue((long) ClaimGuard.passSlice(n) * ClaimGuard.PASS_TICKS >= n, "covers all " + n + " devices within the period");
        }
    }

    @Test
    void teamNamesAreOnlyForViewersWithPositions() {
        var head = JsonParser.parseString("""
            {"uplinkId":"u","title":"Furnace","blocked":{"reason":"claim","team":"Rivals"},
             "blockedWidgets":{"d_1":{"reason":"claim","team":"Rivals"},"d_2":{"reason":"claim"}}}""").getAsJsonObject();
        var copy = head.deepCopy();
        var publicHead = ClaimGuard.withoutTeams(head);
        assertNotNull(publicHead);
        assertEquals(copy, head, "the full head is not changed");
        assertFalse(publicHead.getAsJsonObject("blocked").has("team"));
        assertEquals("claim", publicHead.getAsJsonObject("blocked").get("reason").getAsString());
        assertFalse(publicHead.getAsJsonObject("blockedWidgets").getAsJsonObject("d_1").has("team"));
        assertEquals("Furnace", publicHead.get("title").getAsString());
        assertNull(ClaimGuard.withoutTeams(JsonParser.parseString("{\"uplinkId\":\"u\"}").getAsJsonObject()), "nothing to hide");
        assertNull(ClaimGuard.withoutTeams(JsonParser.parseString("{\"blocked\":{\"reason\":\"claim\"}}").getAsJsonObject()), "no team named");
    }

    @Test
    void jsonOfABlock() {
        JsonObject json = ClaimGuard.json(new ClaimGuard.Block(RIVALS.id(), "Rivals"));
        assertEquals("claim", json.get("reason").getAsString());
        assertEquals("Rivals", json.get("team").getAsString());
        assertFalse(ClaimGuard.json(new ClaimGuard.Block(RIVALS.id(), "")).has("team"), "unknown team (failed closed): no name");
        assertEquals("", ClaimGuard.screenJson(new ClaimGuard.Block(RIVALS.id(), "Rivals"), false).get("team").getAsString());
        assertEquals("Rivals", ClaimGuard.screenJson(new ClaimGuard.Block(RIVALS.id(), "Rivals"), true).get("team").getAsString());
    }
}
