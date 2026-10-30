package ai.ezfox.cccloud.network.graph;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.network.graph.NetworkGraph.Device;
import ai.ezfox.cccloud.network.graph.NetworkGraph.DeviceType;
import ai.ezfox.cccloud.network.graph.NetworkGraph.Node;
import ai.ezfox.cccloud.network.graph.NetworkGraph.NodeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CapacityTest {
    private static final String OW = "minecraft:overworld";

    @Test
    void mostHopsThenMostRecentGoOverCapacity() {
        var nodes = List.of(new Node("mf", NodeType.MAINFRAME, OW, 0, 0, 0, 96, 96, true, null), new Node("r", NodeType.RELAY, OW, 80, 0, 0, 96, 48, true, null));
        var devices = new ArrayList<Device>();
        var order = new HashMap<String, Integer>();
        for (int i = 0; i < 4; i++) {
            devices.add(new Device("near" + i, DeviceType.UPLINK, OW, 10 + i, 0, 0, 48, false));
            order.put("near" + i, i);
        }
        devices.add(new Device("far", DeviceType.UPLINK, OW, 120, 0, 0, 48, false));
        order.put("far", 0);
        var r = NetworkGraph.compute("mf", nodes, devices, true);
        // used = 1 relay + 5 devices = 6; limit 4 → two go: the 2-hop device first, then the most recently linked near one.
        var over = Capacity.overCapacity(r, "mf", 4, 0.5, order);
        assertEquals(Set.of("far", "near3"), over);
        var cut = Capacity.without(r, over);
        assertFalse(cut.route("far").connected());
        assertTrue(cut.route("near0").connected());
        assertTrue(Capacity.overCapacity(r, "mf", 6, 0.5, order).isEmpty());
    }

    @Test
    void fiberMembersWeighLess() {
        var nodes = List.of(new Node("mf", NodeType.MAINFRAME, OW, 0, 0, 0, 96, 96, true, null, Set.of(), Set.of("fiber:0")));
        var devices = List.of(new Device("a", DeviceType.UPLINK, OW, 5000, 0, 0, 48, false, Set.of(), Set.of("fiber:0")),
            new Device("b", DeviceType.UPLINK, OW, 5001, 0, 0, 48, false, Set.of(), Set.of("fiber:0")));
        var r = NetworkGraph.compute("mf", nodes, devices, true);
        assertTrue(Capacity.overCapacity(r, "mf", 1, 0.5, java.util.Map.of()).isEmpty(), "2 × 0.5 fits into 1");
        assertEquals(1, Capacity.overCapacity(r, "mf", 1, 1, java.util.Map.of()).size());
    }

    @Test
    void cloudProbesCountMoreAndCostMore() {
        var nodes = List.of(new Node("mf", NodeType.MAINFRAME, OW, 0, 0, 0, 96, 96, true, null));
        var devices = List.of(new Device("p", DeviceType.PROBE, OW, 10, 0, 0, 128, false), new Device("u", DeviceType.UPLINK, OW, 12, 0, 0, 48, false));
        var r = NetworkGraph.compute("mf", nodes, devices, true);
        assertTrue(r.route("p").connected(), "a probe links like an advanced uplink");
        assertTrue(Capacity.overCapacity(r, "mf", 3, 0.5, 0.25, 2, java.util.Map.of()).isEmpty(), "probe 2 + uplink 1 fit into 3");
        assertEquals(Set.of("p"), Capacity.overCapacity(r, "mf", 2, 0.5, 0.25, 2, java.util.Map.of("p", 1, "u", 0)), "the probe does not fit into 2");
        var counts = NetworkPower.counts(r, 0.5, 0.25, 2);
        assertEquals(1, counts.probes());
        assertEquals(2, counts.devices());
        assertEquals(3, counts.capacityUsed(), 1e-9);
        var p = NetworkPower.breakdown(new NetworkPower.Rates(40, 4, 8, 10, 2, 1, 1, 24), counts, 0, 0.5);
        assertEquals(24, p.get("probes"));
        assertEquals(40 + 4 + 24, p.get("total"));
        // networks without probes keep their breakdown keys
        var plain = NetworkGraph.compute("mf", nodes, List.of(devices.get(1)), true);
        assertFalse(NetworkPower.breakdown(new NetworkPower.Rates(40, 4, 8, 10, 2, 1, 1, 24), NetworkPower.counts(plain, 0.5, 0.25, 2), 0, 0.5).containsKey("probes"));
    }

    @Test
    void efficiencyAndAnchorInTheBreakdown() {
        var r = NetworkGraph.compute("mf", List.of(new Node("mf", NodeType.MAINFRAME, OW, 0, 0, 0, 96, 96, true, null)),
            List.of(new Device("u", DeviceType.UPLINK, OW, 10, 0, 0, 48, false)), true);
        var p = NetworkPower.breakdown(new NetworkPower.Rates(40, 4, 8, 10, 2, 1), NetworkPower.counts(r, 0.5), 0, 0.5, 0.5, 240);
        assertEquals(240, p.get("anchor"));
        assertEquals(-22, p.get("efficiency"), "50 % of 44");
        assertEquals(44 - 22 + 240, p.get("total"));
        long sum = 0;
        for (var e : p.entrySet()) if (!e.getKey().equals("total")) sum += e.getValue();
        assertEquals(p.get("total"), sum, "items sum to the usage");
    }
}
