package ai.ezfox.cccloud.network.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Device capacity of a mainframe (docs/SPEC.md "Mainframe upgrades"): relays on device routes count 1, routed devices 1 (by
 * fiber {@code fiberWeight}, Cloud Redstone devices {@code redstoneWeight}, Cloud Probes {@code probeUnits} times their
 * route weight). Above the limit the devices with the most
 * hops, then the most recently linked, go {@code over_capacity} and stop until capacity returns. Pure Java, unit tested.
 */
public final class Capacity {
    private Capacity() {
    }

    /**
     * Relays that carry this network's device traffic (on the route of a connected device). Relays are shared
     * infrastructure, so a neighbour's relay that merely is in range does not use up this mainframe's capacity.
     */
    public static int relays(NetworkGraph.Result result, String mainframeId) {
        var used = new java.util.HashSet<String>();
        for (var route : result.routes().values()) {
            if (!route.connected()) continue;
            for (var hop : route.path()) if (!hop.equals(mainframeId)) used.add(hop);
        }
        return used.size();
    }

    /**
     * @param linkOrder device id → link order (higher = linked later)
     * @return ids of the devices that are over capacity (empty when everything fits)
     */
    public static Set<String> overCapacity(NetworkGraph.Result result, String mainframeId, int limit, double fiberWeight, Map<String, Integer> linkOrder) {
        return overCapacity(result, mainframeId, limit, fiberWeight, 1, linkOrder);
    }

    /** @param redstoneWeight what a Cloud Redstone device counts (config {@code redstoneDeviceCapacityWeight}) */
    public static Set<String> overCapacity(NetworkGraph.Result result, String mainframeId, int limit, double fiberWeight, double redstoneWeight,
                                           Map<String, Integer> linkOrder) {
        return overCapacity(result, mainframeId, limit, fiberWeight, redstoneWeight, 1, linkOrder);
    }

    /** @param probeUnits how many devices a Cloud Probe counts as (config {@code probeCapacityUnits}) */
    public static Set<String> overCapacity(NetworkGraph.Result result, String mainframeId, int limit, double fiberWeight, double redstoneWeight,
                                           double probeUnits, Map<String, Integer> linkOrder) {
        double used = relays(result, mainframeId);
        var connected = new ArrayList<NetworkGraph.Device>();
        for (var device : result.devices()) {
            var route = result.route(device.id());
            if (route == null || !route.connected()) continue;
            connected.add(device);
            used += weight(device, route, fiberWeight, redstoneWeight, probeUnits);
        }
        var over = new LinkedHashSet<String>();
        if (used <= limit + 1e-9) return over;
        connected.sort(Comparator.<NetworkGraph.Device>comparingInt(d -> result.route(d.id()).hops()).reversed()
            .thenComparing(Comparator.<NetworkGraph.Device>comparingInt(d -> linkOrder.getOrDefault(d.id(), Integer.MAX_VALUE)).reversed()));
        for (var device : connected) {
            if (used <= limit + 1e-9) break;
            over.add(device.id());
            used -= weight(device, result.route(device.id()), fiberWeight, redstoneWeight, probeUnits);
        }
        return over;
    }

    static double weight(NetworkGraph.Device device, NetworkGraph.Route route, double fiberWeight, double redstoneWeight) {
        return weight(device, route, fiberWeight, redstoneWeight, 1);
    }

    /** Capacity units of one routed device: 1 (fiber: {@code fiberWeight}), a redstone device {@code redstoneWeight}, a Cloud Probe {@code probeUnits} times its route weight. */
    public static double weight(NetworkGraph.Device device, NetworkGraph.Route route, double fiberWeight, double redstoneWeight, double probeUnits) {
        if (device.type() == NetworkGraph.DeviceType.REDSTONE) return Math.max(0, redstoneWeight);
        double w = route.fiber() ? Math.max(0, fiberWeight) : 1;
        return device.type() == NetworkGraph.DeviceType.PROBE ? w * Math.max(1, probeUnits) : w;
    }

    /** The result with the over-capacity devices cut off (no route). */
    public static NetworkGraph.Result without(NetworkGraph.Result result, Set<String> over) {
        if (over.isEmpty()) return result;
        var routes = new LinkedHashMap<String, NetworkGraph.Route>(result.routes());
        for (var id : over) routes.put(id, new NetworkGraph.Route(java.util.List.of(), 0, -1, false));
        return new NetworkGraph.Result(result.nodes(), result.devices(), result.links(), routes, result.nodePaths());
    }
}
