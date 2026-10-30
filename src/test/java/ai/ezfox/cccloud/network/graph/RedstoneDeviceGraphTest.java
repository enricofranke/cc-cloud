package ai.ezfox.cccloud.network.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.ezfox.cccloud.network.graph.NetworkGraph.Device;
import ai.ezfox.cccloud.network.graph.NetworkGraph.DeviceType;
import ai.ezfox.cccloud.network.graph.NetworkGraph.Node;
import ai.ezfox.cccloud.network.graph.NetworkGraph.NodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Cloud Redstone devices in the network: a fraction of a device in capacity and FE (docs/SPEC.md "Cloud Redstone devices"). */
class RedstoneDeviceGraphTest {
    static final String OW = "minecraft:overworld";

    static Node mf() {
        return new Node("mf", NodeType.MAINFRAME, OW, 0, 0, 0, 96, 96, true, null);
    }

    @Test
    void redstoneDevicesWeighLessInTheCapacity() {
        var devices = new ArrayList<Device>();
        for (int i = 0; i < 8; i++) devices.add(new Device("rs" + i, DeviceType.REDSTONE, OW, 10 + i, 0, 0, 32, false));
        devices.add(new Device("u", DeviceType.UPLINK, OW, 5, 0, 0, 48, false));
        var r = NetworkGraph.compute("mf", List.of(mf()), devices, true);
        var counts = NetworkPower.counts(r, 0.5, 0.25);
        assertEquals(8, counts.redstone());
        assertEquals(9, counts.devices());
        assertEquals(1 + 8 * 0.25, counts.capacityUsed(), 1e-9);
        assertTrue(Capacity.overCapacity(r, "mf", 3, 0.5, 0.25, Map.of()).isEmpty(), "1 uplink + 8 × 0.25 = 3 fits into 3");
        var over = Capacity.overCapacity(r, "mf", 2, 0.5, 0.25, Map.of());
        assertEquals(4, over.size(), "4 redstone devices (1 capacity) have to go for a limit of 2: " + over);
        assertEquals(7, Capacity.overCapacity(r, "mf", 2, 0.5, 1, Map.of()).size(), "with weight 1 they would count fully");
    }

    @Test
    void redstoneDevicesHaveTheirOwnFeRate() {
        var devices = List.of(new Device("a", DeviceType.REDSTONE, OW, 10, 0, 0, 32, false), new Device("b", DeviceType.REDSTONE, OW, 20, 0, 0, 32, false),
            new Device("lost", DeviceType.REDSTONE, OW, 5000, 0, 0, 32, false));
        var r = NetworkGraph.compute("mf", List.of(mf()), devices, true);
        var p = NetworkPower.breakdown(new NetworkPower.Rates(40, 4, 8, 10, 2, 1, 1), NetworkPower.counts(r, 0.5, 0.25), 0, 0.5);
        assertEquals(2, p.get("redstone"), "two routed devices × 1 FE/t, the lost one costs nothing");
        assertEquals(42, p.get("total"));
        assertEquals(0, NetworkPower.breakdown(new NetworkPower.Rates(40, 4, 8, 10, 2, 1), NetworkPower.counts(r, 0.5), 0, 0.5).get("redstone"),
            "old rates without a redstone rate: free");
    }
}
