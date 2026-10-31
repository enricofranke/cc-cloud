package ai.ezfox.cccloud.analyzer;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.network.graph.NetworkGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalyzerGeometryTest {
    private static final String OW = "minecraft:overworld";

    private static NetworkGraph.Result network() {
        var mainframe = new NetworkGraph.Node("m", NetworkGraph.NodeType.MAINFRAME, OW, 0, 64, 0, 96, 96, true, null);
        var relay = new NetworkGraph.Node("r", NetworkGraph.NodeType.RELAY, OW, 80, 64, 0, 96, 48, true, null);
        var far = new NetworkGraph.Node("x", NetworkGraph.NodeType.RELAY, OW, 900, 64, 0, 96, 48, true, null);
        var device = new NetworkGraph.Device("d", NetworkGraph.DeviceType.UPLINK, OW, 120, 64, 0, 48, false);
        return NetworkGraph.compute("m", List.of(mainframe, relay, far), List.of(device), false);
    }

    @Test
    void payloadOnlyContainsWhatIsNear() {
        var net = new AnalyzerGeometry.Network("m", "MF", network(), Map.of("m", "Mainframe"), Map.of());
        var json = AnalyzerGeometry.build(List.of(net), OW, 0, 64, 0, Map.of("uplink", new int[]{48}));
        var ids = new ArrayList<String>();
        for (var n : json.getAsJsonArray("nodes")) ids.add(n.getAsJsonObject().get("id").getAsString());
        assertTrue(ids.contains("m") && ids.contains("r"));
        assertFalse(ids.contains("x"), "a node 900 blocks away is not sent");
        assertEquals(1, json.getAsJsonArray("devices").size());
        var other = AnalyzerGeometry.build(List.of(net), "minecraft:the_nether", 0, 64, 0, Map.of());
        var claimed = AnalyzerGeometry.build(List.of(net), OW, 0, 64, 0, Map.of(), (x, y, z) -> true);
        assertEquals(0, claimed.getAsJsonArray("nodes").size(), "nothing inside a hidden place");
        assertEquals(0, claimed.getAsJsonArray("devices").size());
        assertEquals(0, claimed.getAsJsonArray("links").size());
        assertEquals(0, other.getAsJsonArray("nodes").size(), "other dimensions are not sent");
        assertTrue(json.getAsJsonArray("links").size() >= 1);
    }

    @Test
    void anEnderLinkShowsWhereItGoes() {
        var mainframe = new NetworkGraph.Node("m", NetworkGraph.NodeType.MAINFRAME, OW, 0, 64, 0, 96, 96, true, null);
        var a = new NetworkGraph.Node("a", NetworkGraph.NodeType.ENDER_RELAY, OW, 8, 64, 0, 96, 48, true, "g");
        var b = new NetworkGraph.Node("b", NetworkGraph.NodeType.ENDER_RELAY, "minecraft:the_nether", 8, 64, 0, 96, 48, true, "g");
        var result = NetworkGraph.compute("m", List.of(mainframe, a, b), List.of(), false);
        var net = new AnalyzerGeometry.Network("m", "MF", result, Map.of(), Map.of());
        assertTrue(result.links().stream().anyMatch(l -> l.a().equals("b") || l.b().equals("b")), "ender relays of one network link");
        var here = AnalyzerGeometry.build(List.of(net), OW, 0, 64, 0, Map.of());
        assertEquals("minecraft:the_nether", here.getAsJsonArray("gates").get(0).getAsJsonObject().get("to").getAsString());
        assertEquals("minecraft:the_nether", here.getAsJsonArray("elsewhere").get(0).getAsString());
        var hidden = AnalyzerGeometry.build(List.of(net), OW, 0, 64, 0, Map.of(), (x, y, z) -> x == 8);
        assertEquals(0, hidden.getAsJsonArray("gates").size(), "a hidden end shows no gate");
    }

    @Test
    void elementCountIsCapped() {
        var nodes = new ArrayList<NetworkGraph.Node>();
        nodes.add(new NetworkGraph.Node("m", NetworkGraph.NodeType.MAINFRAME, OW, 0, 64, 0, 96, 96, true, null));
        var devices = new ArrayList<NetworkGraph.Device>();
        for (int i = 0; i < 400; i++) devices.add(new NetworkGraph.Device("d" + i, NetworkGraph.DeviceType.UPLINK, OW, i % 40, 64, i / 40, 48, false));
        var result = NetworkGraph.compute("m", nodes, devices, false);
        var json = AnalyzerGeometry.build(List.of(new AnalyzerGeometry.Network("m", "MF", result, Map.of(), Map.of())), OW, 0, 64, 0, Map.of());
        int total = json.getAsJsonArray("nodes").size() + json.getAsJsonArray("devices").size() + json.getAsJsonArray("links").size();
        assertEquals(AnalyzerGeometry.MAX_ELEMENTS, total);
    }

    @Test
    void previewPrefersFewerHopsThenSignal() {
        var net = new AnalyzerGeometry.Network("m", "MF", network(), Map.of(), Map.of());
        var nodes = AnalyzerGeometry.nodes(AnalyzerGeometry.build(List.of(net), OW, 0, 64, 0, Map.of()));
        // 90 blocks east: in range of the mainframe (96, 1 hop, weak) and the relay (48 device range, 2 hops, strong).
        var p = AnalyzerGeometry.preview(nodes, OW, 90, 64, 0, 48, false);
        assertTrue(p.connects());
        assertEquals("m", p.via().id(), "fewest hops wins");
        assertEquals(1, p.hops());
        assertEquals(1 - 90.0 / 96, p.signal(), 1e-6);
        var viaRelay = AnalyzerGeometry.preview(nodes, OW, 120, 64, 0, 48, false);
        assertEquals("r", viaRelay.via().id());
        assertEquals(2, viaRelay.hops());
        var none = AnalyzerGeometry.preview(nodes, OW, 400, 64, 0, 48, false);
        assertFalse(none.connects());
        assertEquals("r", none.nearest().id(), "nearest reachable node");
        assertEquals(320, none.distance(), 1e-6);
    }
}
