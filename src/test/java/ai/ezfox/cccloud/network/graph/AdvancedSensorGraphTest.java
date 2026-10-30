package ai.ezfox.cccloud.network.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.ezfox.cccloud.network.graph.NetworkGraph.Device;
import ai.ezfox.cccloud.network.graph.NetworkGraph.DeviceType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Advanced Cloud Sensors in the network: 0.5 capacity units, 4 FE/t while armed, the redstone rate while disarmed. */
class AdvancedSensorGraphTest {
    static final String OW = RedstoneDeviceGraphTest.OW;

    @Test
    void capacityAndPowerWhileArmedOrDisarmed() {
        var devices = List.of(new Device("armed", DeviceType.ADVANCED_SENSOR, OW, 10, 0, 0, 32, false),
            new Device("disarmed", DeviceType.ADVANCED_SENSOR, OW, 12, 0, 0, 32, false), new Device("rs", DeviceType.REDSTONE, OW, 14, 0, 0, 32, false));
        var r = NetworkGraph.compute("mf", List.of(RedstoneDeviceGraphTest.mf()), devices, true);
        var counts = NetworkPower.counts(r, 0.5, 0.25, 2, 0.5, id -> id.equals("armed"));
        assertEquals(1, counts.sensors(), "one armed sensor");
        assertEquals(2, counts.redstone(), "the disarmed one costs like a redstone device");
        assertEquals(3, counts.devices());
        assertEquals(0.5 + 0.5 + 0.25, counts.capacityUsed(), 1e-9, "0.5 units each, armed or not");
        var p = NetworkPower.breakdown(new NetworkPower.Rates(40, 4, 8, 10, 2, 1, 1, 24, 4), counts, 0, 0.5);
        assertEquals(4, p.get("sensors"), "4 FE/t while armed");
        assertEquals(2, p.get("redstone"));
        assertEquals(46, p.get("total"));
        assertTrue(Capacity.overCapacity(r, "mf", 1, 0.5, 0.25, 2, 0.5, Map.of()).size() == 1, "1.25 units do not fit into 1");
        assertEquals(0.5, Capacity.weight(devices.getFirst(), r.route("armed"), 0.5, 0.25, 2, 0.5), 1e-9);
    }
}
