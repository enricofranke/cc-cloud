package ai.ezfox.cccloud.network.graph;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mainframe's FE usage for its network (docs/SPEC.md "v0.4 Power scaling"), as a breakdown for the screen and the
 * dashboard: base + per uplink / advanced uplink / bridge + per dashboard client + per hop beyond the first for every
 * routed device. Pure Java, unit tested.
 */
public final class NetworkPower {
    public record Rates(long base, long uplink, long advancedUplink, long bridge, long client, long extraHop) {
    }

    private NetworkPower() {
    }

    /** Ordered breakdown (key → FE/t), plus "total". */
    public static Map<String, Long> breakdown(Rates rates, NetworkGraph.Result network, int clients) {
        long uplinks = 0, advanced = 0, bridges = 0, extraHops = 0;
        for (var device : network.devices()) {
            var route = network.route(device.id());
            if (route == null || !route.connected()) continue;
            switch (device.type()) {
                case UPLINK -> uplinks++;
                case ADVANCED_UPLINK -> advanced++;
                case BRIDGE -> bridges++;
            }
            extraHops += Math.max(0, route.hops() - 1);
        }
        var map = new LinkedHashMap<String, Long>();
        map.put("base", Math.max(0, rates.base()));
        map.put("uplinks", Math.max(0, rates.uplink()) * uplinks);
        map.put("advanced_uplinks", Math.max(0, rates.advancedUplink()) * advanced);
        map.put("bridges", Math.max(0, rates.bridge()) * bridges);
        map.put("clients", Math.max(0, rates.client()) * Math.max(0, clients));
        map.put("hops", Math.max(0, rates.extraHop()) * extraHops);
        long total = 0;
        for (var v : map.values()) total += v;
        map.put("total", total);
        return map;
    }
}
