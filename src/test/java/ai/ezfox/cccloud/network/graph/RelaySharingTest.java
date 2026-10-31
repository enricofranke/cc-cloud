package ai.ezfox.cccloud.network.graph;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.FakeTeams;
import ai.ezfox.cccloud.access.TeamProvider;
import ai.ezfox.cccloud.access.TeamProviders;
import ai.ezfox.cccloud.network.graph.NetworkGraph.Device;
import ai.ezfox.cccloud.network.graph.NetworkGraph.DeviceType;
import ai.ezfox.cccloud.network.graph.NetworkGraph.Node;
import ai.ezfox.cccloud.network.graph.NetworkGraph.NodeType;
import ai.ezfox.cccloud.network.graph.RelaySharing.Share;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Relays belong to a team (docs/SPEC.md "Relays belong to a team"): the rule, the routing it leads to (a relay that does
 * not carry a network is not one of its nodes, as NetworkService builds them), the team lookup over the team providers
 * and the cache.
 */
class RelaySharingTest {
    private static final String OW = "minecraft:overworld";
    private static final UUID ENRICO = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final UUID MATE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALLY = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID STEINI = UUID.fromString("00000000-0000-0000-0000-000000000005");

    /** Enrico's FTB-like party with a member and an ally; Steini plays in his own team. */
    private static FakeTeams teams() {
        return new FakeTeams().put("red", ENRICO, "owner").put("red", MATE, "member").put("red", ALLY, "ally").put("blue", STEINI, "owner");
    }

    private static RelaySharing.Trust trust(FakeTeams teams) {
        return (relayOwner, player) -> TeamProviders.sameTeamOrAlly(List.of(teams), relayOwner, player);
    }

    // ---- the rule ----------------------------------------------------------------------------------------------------

    @Test
    void teamRelayCarriesOwnerTeamAndAllies() {
        var t = trust(teams());
        assertTrue(RelaySharing.carries(ENRICO, Share.TEAM, ENRICO, t), "same owner");
        assertTrue(RelaySharing.carries(ENRICO, Share.TEAM, MATE, t), "team member");
        assertTrue(RelaySharing.carries(ENRICO, Share.TEAM, ALLY, t), "ally of the owner's team");
        assertFalse(RelaySharing.carries(ENRICO, Share.TEAM, STEINI, t), "foreign team");
        assertFalse(RelaySharing.carries(ENRICO, Share.TEAM, null, t), "a mainframe without a known owner");
    }

    @Test
    void allyIsOneWay() {
        // Enrico's team lists ALLY as an ally; that does not make ALLY's own relays carry Enrico.
        var teams = teams().put("green", ALLY, "owner");
        assertTrue(RelaySharing.carries(ENRICO, Share.TEAM, ALLY, trust(teams)));
        assertFalse(RelaySharing.carries(ALLY, Share.TEAM, ENRICO, trust(teams)), "Enrico is no member or ally of the ally's team");
    }

    @Test
    void everyoneRelayCarriesEveryNetwork() {
        var t = trust(teams());
        assertTrue(RelaySharing.carries(ENRICO, Share.EVERYONE, STEINI, t));
        assertTrue(RelaySharing.carries(ENRICO, Share.EVERYONE, null, t));
    }

    @Test
    void legacyRelayWithoutOwnerCarriesEveryNetwork() {
        RelaySharing.Trust never = (a, b) -> fail("no team lookup for a relay without an owner");
        assertTrue(RelaySharing.carries(null, Share.TEAM, STEINI, never));
        assertTrue(RelaySharing.carries(null, Share.TEAM, null, never));
    }

    @Test
    void withoutTeamModOnlyTheSamePlayer() {
        RelaySharing.Trust none = (a, b) -> TeamProviders.sameTeamOrAlly(List.of(), a, b);
        assertTrue(RelaySharing.carries(ENRICO, Share.TEAM, ENRICO, none));
        assertFalse(RelaySharing.carries(ENRICO, Share.TEAM, MATE, none));
    }

    @Test
    void shareIds() {
        assertEquals(Share.TEAM, Share.byId("team"));
        assertEquals(Share.EVERYONE, Share.byId("everyone"));
        assertNull(Share.byId("Team"));
        assertNull(Share.byId(""));
        assertNull(Share.byId(null));
    }

    // ---- team lookup -------------------------------------------------------------------------------------------------

    @Test
    void sameTeamOrAllyAcrossProviders() {
        var scoreboard = new FakeTeams().put("builders", ENRICO, "member").put("builders", STEINI, "member");
        assertTrue(TeamProviders.sameTeamOrAlly(List.of(teams(), scoreboard), ENRICO, STEINI), "the second provider has them in one team");
        assertFalse(TeamProviders.sameTeamOrAlly(List.of(teams()), ENRICO, STEINI));
    }

    @Test
    void aFailingProviderCountsAsNotInTheTeam() {
        TeamProvider broken = new TeamProvider() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public List<String> ranks() {
                return List.of("member");
            }

            @Override
            public java.util.Map<String, ai.ezfox.cccloud.access.Role> defaultMapping() {
                return java.util.Map.of();
            }

            @Override
            public @Nullable String rankOf(String teamId, UUID player) {
                return null;
            }

            @Override
            public TeamProvider.@Nullable Team team(String teamId) {
                return null;
            }

            @Override
            public List<TeamProvider.Team> teamsOf(UUID player) {
                throw new IllegalStateException("team data not loaded");
            }
        };
        assertFalse(TeamProviders.sameTeamOrAlly(List.of(broken), ENRICO, MATE));
        assertTrue(TeamProviders.sameTeamOrAlly(List.of(broken, teams()), ENRICO, MATE), "the next provider still answers");
    }

    // ---- routing: what NetworkService feeds into NetworkGraph ----------------------------------------------------------

    private record Relay(Node node, @Nullable UUID owner, Share share) {
    }

    private static Node mainframe(String id, double x) {
        return new Node(id, NodeType.MAINFRAME, OW, x, 0, 0, 96, 96, true, null);
    }

    private static Relay relay(String id, double x, @Nullable UUID owner, Share share) {
        return new Relay(new Node(id, NodeType.RELAY, OW, x, 0, 0, 96, 48, true, null), owner, share);
    }

    /** The nodes of one mainframe's network, as NetworkService.nodes builds them. */
    private static List<Node> nodes(Node mainframe, @Nullable UUID mainframeOwner, List<Relay> relays, RelaySharing.Trust trust) {
        var list = new ArrayList<Node>();
        list.add(mainframe);
        for (var r : relays) if (RelaySharing.carries(r.owner(), r.share(), mainframeOwner, trust)) list.add(r.node());
        return list;
    }

    /** Mainframe at 0, relay at 80, uplink at 120: only reachable over the relay. */
    private static NetworkGraph.Result network(@Nullable UUID mainframeOwner, @Nullable UUID relayOwner, Share share) {
        var mf = mainframe("mf", 0);
        var r = relay("r", 80, relayOwner, share);
        var device = new Device("u", DeviceType.UPLINK, OW, 120, 0, 0, 48, false);
        return NetworkGraph.compute("mf", nodes(mf, mainframeOwner, List.of(r), trust(teams())), List.of(device), true);
    }

    @Test
    void routesOverOwnAndTeamRelays() {
        for (var owner : new UUID[]{ENRICO, MATE, ALLY}) {
            var result = network(owner, ENRICO, Share.TEAM);
            assertTrue(result.route("u").connected(), "routed over Enrico's relay for " + owner);
            assertEquals(List.of("mf", "r"), result.route("u").path());
        }
    }

    @Test
    void foreignTeamGetsNoRouteAndNeverSeesTheRelay() {
        var result = network(STEINI, ENRICO, Share.TEAM);
        assertFalse(result.route("u").connected(), "no route over another team's relay");
        assertTrue(result.nodes().stream().noneMatch(n -> n.id().equals("r")), "the relay is not part of Steini's network (map, 3D, screen)");
        assertTrue(result.links().stream().noneMatch(l -> l.a().equals("r") || l.b().equals("r")), "no link to it either");
        var nearest = NetworkGraph.nearestNode(result, OW, 120, 0, 0);
        assertTrue(nearest != null && nearest.getKey().id().equals("mf"), "link errors name Steini's own node, never the relay: " + nearest);
    }

    @Test
    void everyoneAndLegacyRelaysRouteForeignNetworks() {
        assertTrue(network(STEINI, ENRICO, Share.EVERYONE).route("u").connected(), "shared with everyone");
        assertTrue(network(STEINI, null, Share.TEAM).route("u").connected(), "legacy relay without an owner");
    }

    @Test
    void twoTeamsSideBySideKeepTheirOwnRelays() {
        // Enrico's relay at 80 and Steini's at 82: each network routes over its own team's relay only.
        var relays = List.of(relay("re", 80, ENRICO, Share.TEAM), relay("rs", 82, STEINI, Share.TEAM));
        var device = List.of(new Device("u", DeviceType.UPLINK, OW, 120, 0, 0, 48, false));
        var t = trust(teams());
        var enrico = NetworkGraph.compute("mf", nodes(mainframe("mf", 0), ENRICO, relays, t), device, true);
        var steini = NetworkGraph.compute("mf", nodes(mainframe("mf", 0), STEINI, relays, t), device, true);
        assertEquals(List.of("mf", "re"), enrico.route("u").path());
        assertEquals(List.of("mf", "rs"), steini.route("u").path());
        assertFalse(enrico.reachable("rs"));
        assertFalse(steini.reachable("re"));
    }

    // ---- cache -------------------------------------------------------------------------------------------------------

    @Test
    void cacheAsksOncePerPairUntilClearedOrExpired() {
        var teams = teams();
        var now = new AtomicLong(1_000);
        var cache = new RelaySharing.Cache(trust(teams), now::get, RelaySharing.TTL_MS);
        // A full recompute of 50 networks over 20 relays of one owner: one lookup per owner pair, not per relay.
        for (int net = 0; net < 50; net++) {
            for (int relay = 0; relay < 20; relay++) {
                assertTrue(RelaySharing.carries(ENRICO, Share.TEAM, MATE, cache));
                assertFalse(RelaySharing.carries(ENRICO, Share.TEAM, STEINI, cache));
                assertTrue(RelaySharing.carries(ENRICO, Share.TEAM, ENRICO, cache), "same owner needs no lookup");
            }
        }
        assertEquals(2, cache.lookups(), "two owner pairs");

        // Steini joins Enrico's team: the cached answer stays until a team event clears it ...
        teams.put("red", STEINI, "member");
        assertFalse(RelaySharing.carries(ENRICO, Share.TEAM, STEINI, cache));
        cache.clear();
        assertTrue(RelaySharing.carries(ENRICO, Share.TEAM, STEINI, cache), "after the team change event");
        assertEquals(3, cache.lookups());

        // ... or until the TTL ran out (team mods without events, e.g. scoreboard teams).
        teams.teams.get("red").remove(STEINI);
        assertTrue(RelaySharing.carries(ENRICO, Share.TEAM, STEINI, cache));
        now.addAndGet(RelaySharing.TTL_MS);
        assertFalse(RelaySharing.carries(ENRICO, Share.TEAM, STEINI, cache), "re-asked after the TTL");
    }

    @Test
    void cacheTreatsAFailingLookupAsForeign() {
        var cache = new RelaySharing.Cache((a, b) -> {
            throw new NoClassDefFoundError("team mod gone");
        }, () -> 0, RelaySharing.TTL_MS);
        assertFalse(RelaySharing.carries(ENRICO, Share.TEAM, MATE, cache));
        assertTrue(RelaySharing.carries(ENRICO, Share.EVERYONE, MATE, cache));
    }

    @Test
    void cheapPathsNeedNoTeamLookup() {
        // The rule never asks the team mods for its own owner or for shared relays (cheap paths first).
        var asked = new AtomicLong();
        RelaySharing.Trust counting = (a, b) -> {
            asked.incrementAndGet();
            return false;
        };
        RelaySharing.carries(ENRICO, Share.TEAM, ENRICO, counting);
        RelaySharing.carries(ENRICO, Share.EVERYONE, STEINI, counting);
        RelaySharing.carries(null, Share.TEAM, STEINI, counting);
        assertEquals(0, asked.get());
    }
}
