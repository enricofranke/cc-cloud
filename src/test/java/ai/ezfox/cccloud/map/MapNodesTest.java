package ai.ezfox.cccloud.map;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.network.graph.NetworkGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapNodesTest {
    private static MapNodes.Network network(UUID id, int devices) {
        var nodes = List.of(new NetworkGraph.Node(id.toString(), NetworkGraph.NodeType.MAINFRAME, "minecraft:overworld", 10.5, 64, -3.2, 64, 16, true, null),
            new NetworkGraph.Node("relay-1", NetworkGraph.NodeType.RELAY, "minecraft:overworld", 40, 70, 0, 64, 16, true, null));
        var list = new ArrayList<NetworkGraph.Device>();
        for (int i = 0; i < devices; i++) list.add(new NetworkGraph.Device("dev-" + i, NetworkGraph.DeviceType.UPLINK, "minecraft:the_nether", i, 30, -i, 16, false));
        var result = new NetworkGraph.Result(nodes, list, List.of(), Map.of(), Map.of());
        return new MapNodes.Network(id, "Base", result, Map.of("dev-0", "Reactor"));
    }

    @Test
    void onlyNetworksWithViewPositions() {
        var mine = UUID.randomUUID();
        var theirs = UUID.randomUUID();
        var json = MapNodes.build(List.of(network(mine, 1), network(theirs, 1)), id -> id.equals(mine));
        var nodes = json.getAsJsonArray("nodes");
        assertEquals(3, nodes.size());
        for (var n : nodes) assertEquals(mine.toString(), n.getAsJsonObject().get("mf").getAsString());
        var mainframe = nodes.get(0).getAsJsonObject();
        assertEquals("mainframe", mainframe.get("type").getAsString());
        assertEquals("Base", mainframe.get("name").getAsString());
        assertEquals(10, mainframe.get("x").getAsInt());
        assertEquals(-4, mainframe.get("z").getAsInt());
        assertEquals("Reactor", nodes.get(2).getAsJsonObject().get("name").getAsString());
        assertEquals("minecraft:the_nether", nodes.get(2).getAsJsonObject().get("dim").getAsString());
        assertFalse(json.get("truncated").getAsBoolean());
        assertEquals(0, MapNodes.build(List.of(network(mine, 1)), id -> false).getAsJsonArray("nodes").size());
    }

    @Test
    void capped() {
        var json = MapNodes.build(List.of(network(UUID.randomUUID(), MapNodes.MAX_NODES + 5)), id -> true);
        assertEquals(MapNodes.MAX_NODES, json.getAsJsonArray("nodes").size());
        assertTrue(json.get("truncated").getAsBoolean());
    }
}
