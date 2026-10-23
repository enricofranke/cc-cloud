package ai.ezfox.cccloud.ingame;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.network.graph.NetworkGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

class InGameRangeTest {
    @Test
    void coverageIsTheDeviceRangeOfReachableNodes() {
        var mainframe = new NetworkGraph.Node("m", NetworkGraph.NodeType.MAINFRAME, "minecraft:overworld", 0, 64, 0, 96, 96, true, null);
        var relay = new NetworkGraph.Node("r", NetworkGraph.NodeType.RELAY, "minecraft:overworld", 90, 64, 0, 96, 48, true, null);
        var lonely = new NetworkGraph.Node("x", NetworkGraph.NodeType.RELAY, "minecraft:overworld", 1000, 64, 0, 96, 48, true, null);
        var result = NetworkGraph.compute("m", List.of(mainframe, relay, lonely), List.of(), false);
        assertTrue(InGameRange.covered(result, "minecraft:overworld", 10, 64, 10), "near the mainframe");
        assertTrue(InGameRange.covered(result, "minecraft:overworld", 130, 64, 0), "via the relay (48 blocks device range)");
        assertFalse(InGameRange.covered(result, "minecraft:overworld", 200, 64, 0), "beyond the relay");
        assertFalse(InGameRange.covered(result, "minecraft:overworld", 1000, 64, 0), "a relay that is not part of the network");
        assertFalse(InGameRange.covered(result, "minecraft:the_nether", 0, 64, 0), "other dimension");
    }
}
