package ai.ezfox.cccloud.network.graph;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.network.graph.NetworkGraph.Device;
import ai.ezfox.cccloud.network.graph.NetworkGraph.DeviceType;
import ai.ezfox.cccloud.network.graph.NetworkGraph.Node;
import ai.ezfox.cccloud.network.graph.NetworkGraph.NodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import ai.ezfox.cccloud.network.graph.NetworkGraph.LinkKind;
import org.junit.jupiter.api.Test;

class NetworkGraphTest {
    private static final String OW = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    static Node mf(double x) {
        return new Node("mf", NodeType.MAINFRAME, OW, x, 0, 0, 96, 96, true, null);
    }

    static Node relay(String id, double x, boolean available) {
        return new Node(id, NodeType.RELAY, OW, x, 0, 0, 96, 48, available, null);
    }

    static Node ender(String id, String dim, double x, String group) {
        return new Node(id, NodeType.ENDER_RELAY, dim, x, 0, 0, 96, 128, true, group);
    }

    static Device uplink(String id, String dim, double x, boolean legacy) {
        return new Device(id, DeviceType.UPLINK, dim, x, 0, 0, 48, legacy);
    }

    @Test
    void directWhenInRange() {
        var r = NetworkGraph.compute("mf", List.of(mf(0)), List.of(uplink("u", OW, 40, false)), true);
        assertEquals(List.of("mf"), r.route("u").path());
        assertEquals(1, r.route("u").hops());
        assertEquals(1 - 40.0 / 96, r.route("u").signal(), 1e-9, "signal against the larger range (mainframe 96)");
    }

    @Test
    void routesOverTwoRelays() {
        var nodes = List.of(mf(0), relay("r1", 80, true), relay("r2", 160, true));
        var r = NetworkGraph.compute("mf", nodes, List.of(uplink("u", OW, 200, false)), true);
        assertEquals(List.of("mf", "r1", "r2"), r.route("u").path());
        assertEquals(3, r.route("u").hops());
        assertTrue(r.links().stream().anyMatch(l -> l.a().equals("r2") && l.b().equals("u")));
    }

    @Test
    void reroutesWhenARelayGoesDownAndNoRouteWithoutAlternative() {
        var nodes = new ArrayList<>(List.of(mf(0), relay("r1", 80, true), relay("r2", 160, true), relay("r2b", 162, true)));
        var device = List.of(uplink("u", OW, 200, false));
        var before = NetworkGraph.compute("mf", nodes, device, true);
        String used = before.route("u").path().getLast();
        assertTrue(used.equals("r2") || used.equals("r2b"));
        String other = used.equals("r2") ? "r2b" : "r2";
        int usedIndex = used.equals("r2") ? 2 : 3;
        nodes.set(usedIndex, relay(used, used.equals("r2") ? 160 : 162, false)); // out of power
        var after = NetworkGraph.compute("mf", nodes, device, true);
        assertEquals(List.of("mf", "r1", other), after.route("u").path(), "re-routed over the other relay");
        nodes.set(usedIndex == 2 ? 3 : 2, relay(other, other.equals("r2") ? 160 : 162, false));
        var none = NetworkGraph.compute("mf", nodes, device, true);
        assertFalse(none.route("u").connected(), "no_route");
        assertFalse(none.reachable("r2") || none.reachable("r2b"));
    }

    @Test
    void enderRelaysLinkAcrossDimensions() {
        var nodes = List.of(mf(0), ender("e1", OW, 10, "mf"), ender("e2", NETHER, 5000, "mf"), ender("x", NETHER, 5010, "other"));
        var r = NetworkGraph.compute("mf", nodes, List.of(uplink("u", NETHER, 5050, false)), true);
        assertEquals(List.of("mf", "e1", "e2"), r.route("u").path());
        assertTrue(r.links().stream().anyMatch(l -> l.kind() == NetworkGraph.LinkKind.ENDER && l.a().equals("e1") && l.b().equals("e2")));
        assertFalse(r.links().stream().anyMatch(l -> l.kind() == NetworkGraph.LinkKind.ENDER && (l.a().equals("x") || l.b().equals("x"))),
            "other groups are not linked");
    }

    @Test
    void hopLimit() {
        var nodes = new ArrayList<Node>();
        nodes.add(mf(0));
        for (int i = 1; i <= 10; i++) nodes.add(relay("r" + i, i * 90, true));
        var r = NetworkGraph.compute("mf", nodes, List.of(uplink("near", OW, 7 * 90 + 20, false), uplink("far", OW, 10 * 90 + 20, false)), true);
        assertTrue(r.route("near").connected());
        assertTrue(r.route("near").hops() <= NetworkGraph.MAX_HOPS);
        assertFalse(r.route("far").connected(), "beyond 8 hops");
    }

    @Test
    void legacyGrace() {
        var nodes = List.of(mf(0));
        var legacy = uplink("old", OW, 120, true);
        var fresh = uplink("new", OW, 120, false);
        var r = NetworkGraph.compute("mf", nodes, List.of(legacy, fresh), true);
        assertTrue(r.route("old").connected() && r.route("old").legacyOutOfRange(), "legacy device stays connected, flagged");
        assertFalse(r.route("new").connected(), "new links follow the new ranges");
        var off = NetworkGraph.compute("mf", nodes, List.of(legacy), false);
        assertFalse(off.route("old").connected(), "legacyRangeGrace=false");
        var otherDim = NetworkGraph.compute("mf", nodes, List.of(uplink("old2", NETHER, 0, true)), true);
        assertTrue(otherDim.route("old2").legacyOutOfRange(), "v0.3 advanced uplinks worked across dimensions");
        var legacyLink = r.links().stream().filter(l -> l.b().equals("old")).findFirst().orElseThrow();
        assertTrue(legacyLink.legacy() && legacyLink.signal() == 0 && legacyLink.a().equals("mf"), "legacy device gets a legacy link to the mainframe");
        assertFalse(legacyLink.weak(), "legacy links are not reported as weak");
    }

    @Test
    void reportsEveryLinkInRange() {
        // The uplink at 100 reaches both relays (48 device range, 96 relay device range... both within reach) but uses one.
        var nodes = List.of(mf(0), relay("r1", 80, true), relay("r2", 120, true));
        var r = NetworkGraph.compute("mf", nodes, List.of(uplink("u", OW, 100, false)), true);
        long deviceLinks = r.links().stream().filter(l -> l.b().equals("u")).count();
        assertEquals(2, deviceLinks, "both relays in range are reported, not only the routed one");
        assertEquals(List.of("mf", "r1"), r.route("u").path(), "fewest hops wins");
    }

    @Test
    void weakLinksAndNearest() {
        var r = NetworkGraph.compute("mf", List.of(mf(0)), List.of(uplink("u", OW, 90, false)), true);
        var link = r.links().stream().filter(l -> l.b().equals("u")).findFirst().orElseThrow();
        assertTrue(link.weak(), "signal " + link.signal());
        var nearest = NetworkGraph.nearestNode(r, OW, 300, 0, 0);
        assertNotNull(nearest);
        assertEquals("mf", nearest.getKey().id());
        assertEquals(300, nearest.getValue(), 1e-9);
        assertNull(NetworkGraph.nearestNode(r, NETHER, 0, 0, 0));
    }

    @Test
    void mainframeWithoutPowerRoutesNothing() {
        var off = new Node("mf", NodeType.MAINFRAME, OW, 0, 0, 0, 96, 96, false, null);
        var r = NetworkGraph.compute("mf", List.of(off), List.of(uplink("u", OW, 10, true)), true);
        assertFalse(r.route("u").connected());
    }

    @Test
    void powerBreakdown() {
        var nodes = List.of(mf(0), relay("r1", 80, true));
        var devices = List.of(uplink("a", OW, 10, false), uplink("b", OW, 120, false),
            new Device("c", DeviceType.ADVANCED_UPLINK, OW, 20, 0, 0, 128, false), new Device("d", DeviceType.BRIDGE, OW, 30, 0, 0, 48, false),
            uplink("lost", OW, 5000, false));
        var r = NetworkGraph.compute("mf", nodes, devices, true);
        var p = NetworkPower.breakdown(new NetworkPower.Rates(40, 4, 8, 10, 2, 1), r, 3);
        assertEquals(40, p.get("base"));
        assertEquals(8, p.get("uplinks"), "two routed uplinks, the lost one costs nothing");
        assertEquals(8, p.get("advanced_uplinks"));
        assertEquals(10, p.get("bridges"));
        assertEquals(6, p.get("clients"));
        assertEquals(1, p.get("hops"), "uplink b is two hops away");
        assertEquals(73, p.get("total"));
    }

    // ---- cables (docs/SPEC.md "Cables") -----------------------------------------------------------------------------

    static Node cabledMf(Set<String> cables, Set<String> fibers) {
        return new Node("mf", NodeType.MAINFRAME, OW, 0, 0, 0, 96, 96, true, null, cables, fibers);
    }

    static Node cabledRelay(String id, double x, boolean available, Set<String> cables) {
        return new Node(id, NodeType.RELAY, OW, x, 0, 0, 96, 48, available, null, cables, Set.of());
    }

    static Device cabledUplink(String id, double x, Set<String> cables, Set<String> fibers) {
        return new Device(id, DeviceType.UPLINK, OW, x, 0, 0, 48, false, cables, fibers);
    }

    @Test
    void cableReachesBeyondRadioRange() {
        var r = NetworkGraph.compute("mf", List.of(cabledMf(Set.of("cc:1"), Set.of())), List.of(cabledUplink("u", 5000, Set.of("cc:1"), Set.of())), true);
        var route = r.route("u");
        assertTrue(route.connected());
        assertEquals(LinkKind.WIRED, route.via());
        assertEquals(1.0, route.signal());
        assertEquals(0, route.airHops());
        assertEquals(0, route.extraHops());
        assertEquals(1, route.hops());
        var links = r.links().stream().filter(l -> l.b().equals("u")).toList();
        assertEquals(1, links.size());
        assertEquals(LinkKind.WIRED, links.getFirst().kind());
        assertFalse(links.getFirst().weak());
    }

    @Test
    void cabledPairSendsNoRadioLink() {
        var r = NetworkGraph.compute("mf", List.of(cabledMf(Set.of("cc:1"), Set.of())), List.of(cabledUplink("u", 10, Set.of("cc:1"), Set.of())), true);
        assertTrue(r.links().stream().noneMatch(l -> l.b().equals("u") && l.kind() == LinkKind.RADIO), "radio link of a cabled pair is not reported");
        assertTrue(r.route("u").wired());
    }

    @Test
    void cablePreferredOverRadio() {
        // In radio range of the mainframe, but cabled to a relay that reaches the mainframe by radio: the cable wins.
        var nodes = List.of(mf(0), cabledRelay("r1", 80, true, Set.of("cc:r1")));
        var r = NetworkGraph.compute("mf", nodes, List.of(cabledUplink("u", 30, Set.of("cc:r1"), Set.of())), true);
        var route = r.route("u");
        assertEquals(List.of("mf", "r1"), route.path());
        assertEquals(LinkKind.WIRED, route.via());
        assertEquals(1, route.airHops(), "only the relay's radio link is an air hop");
        assertEquals(0, route.extraHops(), "no hop FE for the cable");
        assertTrue(r.links().stream().anyMatch(l -> l.a().equals("mf") && l.b().equals("u") && l.kind() == LinkKind.RADIO),
            "the unused radio link to the mainframe is still reported");
    }

    @Test
    void cutCableReroutesOrNoRoute() {
        var cabled = cabledUplink("u", 30, Set.of("cc:1"), Set.of());
        var mfCabled = cabledMf(Set.of("cc:1"), Set.of());
        assertTrue(NetworkGraph.compute("mf", List.of(mfCabled), List.of(cabled), true).route("u").wired());
        // Cable cut: the uplink is in radio range → radio.
        var cut = cabledUplink("u", 30, Set.of(), Set.of());
        var radio = NetworkGraph.compute("mf", List.of(mfCabled), List.of(cut), true).route("u");
        assertTrue(radio.connected());
        assertEquals(LinkKind.RADIO, radio.via());
        // Far away: no route.
        var far = NetworkGraph.compute("mf", List.of(mfCabled), List.of(cabledUplink("u", 5000, Set.of(), Set.of())), true);
        assertFalse(far.route("u").connected());
    }

    @Test
    void cabledNodeWithoutPowerCarriesNothing() {
        var nodes = List.of(cabledMf(Set.of("cc:a"), Set.of()), cabledRelay("r1", 3000, false, Set.of("cc:b")));
        var r = NetworkGraph.compute("mf", nodes, List.of(cabledUplink("u", 3005, Set.of("cc:b"), Set.of())), true);
        assertFalse(r.route("u").connected());
    }

    @Test
    void nodeCablesCostNoAirHop() {
        // A relay 900 blocks away, cabled to the mainframe: reachable, and its devices are one air hop away.
        var nodes = new ArrayList<Node>();
        nodes.add(cabledMf(Set.of("cc:1"), Set.of()));
        nodes.add(cabledRelay("far", 900, true, Set.of("cc:1")));
        var r = NetworkGraph.compute("mf", nodes, List.of(uplink("u", OW, 920, false)), true);
        assertTrue(r.reachable("far"));
        assertTrue(r.links().stream().anyMatch(l -> l.kind() == LinkKind.WIRED && l.b().equals("far")));
        assertEquals(List.of("mf", "far"), r.route("u").path());
        assertEquals(1, r.route("u").airHops());
        assertEquals(LinkKind.RADIO, r.route("u").via());
    }

    @Test
    void cablesBeatRadioBetweenNodes() {
        // Relay in radio range of the mainframe and cabled: the node link is the cable (no radio link reported).
        var nodes = List.of(cabledMf(Set.of("cc:1"), Set.of()), cabledRelay("r1", 50, true, Set.of("cc:1")));
        var r = NetworkGraph.compute("mf", nodes, List.of(), true);
        var links = r.links().stream().filter(l -> l.b().equals("r1") || l.a().equals("r1")).toList();
        assertEquals(1, links.size());
        assertEquals(LinkKind.WIRED, links.getFirst().kind());
    }

    @Test
    void hopLimitCountsAirHopsOnly() {
        // 10 relays, but the 8th..10th are cabled to the mainframe: the far device is reachable again.
        var nodes = new ArrayList<Node>();
        nodes.add(cabledMf(Set.of("cc:1"), Set.of()));
        for (int i = 1; i <= 10; i++) nodes.add(cabledRelay("r" + i, i * 90, true, i == 10 ? Set.of("cc:1") : Set.of()));
        var r = NetworkGraph.compute("mf", nodes, List.of(uplink("far", OW, 10 * 90 + 20, false)), true);
        assertTrue(r.route("far").connected());
        assertEquals(List.of("mf", "r10"), r.route("far").path());
    }

    @Test
    void fiberPreferredOverCcCableAndWeighted() {
        var nodes = List.of(cabledMf(Set.of("cc:1"), Set.of("fiber:0")));
        var devices = List.of(cabledUplink("f", 5000, Set.of("cc:1"), Set.of("fiber:0")), cabledUplink("c", 4000, Set.of("cc:1"), Set.of()),
            uplink("r", OW, 10, false));
        var r = NetworkGraph.compute("mf", nodes, devices, true);
        assertEquals(LinkKind.FIBER, r.route("f").via());
        assertTrue(r.route("f").fiber() && r.route("f").wired());
        assertEquals(LinkKind.WIRED, r.route("c").via());
        var counts = NetworkPower.counts(r, 0.5);
        assertEquals(3, counts.devices());
        assertEquals(1, counts.fiber());
        assertEquals(2, counts.wired());
        assertEquals(2.5, counts.capacityUsed(), 1e-9, "fiber counts half");
        var p = NetworkPower.breakdown(new NetworkPower.Rates(40, 4, 8, 10, 2, 1), counts, 0, 0.5);
        assertEquals(4 + 4 + 2, p.get("uplinks"), "the fiber uplink pays 50 %");
        assertEquals(0, p.get("hops"));
        var full = NetworkPower.breakdown(new NetworkPower.Rates(40, 4, 8, 10, 2, 1), NetworkPower.counts(r, 1), 0, 1);
        assertEquals(12, full.get("uplinks"));
    }

    @Test
    void sharedCable() {
        assertEquals(LinkKind.FIBER, NetworkGraph.sharedCable(Set.of("cc:1"), Set.of("fiber:1"), Set.of("cc:1"), Set.of("fiber:1")));
        assertEquals(LinkKind.WIRED, NetworkGraph.sharedCable(Set.of("cc:1"), Set.of("fiber:1"), Set.of("cc:1"), Set.of("fiber:2")));
        assertNull(NetworkGraph.sharedCable(Set.of("cc:1"), Set.of(), Set.of("cc:2"), Set.of()));
    }
}
