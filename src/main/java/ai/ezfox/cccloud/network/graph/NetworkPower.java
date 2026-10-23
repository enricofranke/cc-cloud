package ai.ezfox.cccloud.network.graph;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mainframe's FE usage for its network (docs/SPEC.md "v0.4 Power scaling", "Cables"), as a breakdown for the
 * screen and the dashboard: base + per uplink / advanced uplink / bridge + per dashboard client + per air hop beyond
 * the first for every routed device. Cable hops cost nothing; devices attached by fiber pay a reduced device share
 * ({@code fiberPowerFactor}) and count with {@code fiberCapacityWeight} against the device capacity. Pure Java, unit
 * tested.
 */
public final class NetworkPower {
    public record Rates(long base, long uplink, long advancedUplink, long bridge, long client, long extraHop) {
    }

    /**
     * Routed devices of one network. {@code fiber*} are the subset attached by fiber; {@code capacityUsed} is the
     * device count with fiber members weighted.
     */
    public record Counts(int uplinks, int advancedUplinks, int bridges, int extraHops, int fiberUplinks, int fiberAdvancedUplinks,
                         int fiberBridges, int wired, double capacityUsed) {
        public static final Counts EMPTY = new Counts(0, 0, 0, 0, 0, 0, 0, 0, 0);

        public int devices() {
            return uplinks + advancedUplinks + bridges;
        }

        public int fiber() {
            return fiberUplinks + fiberAdvancedUplinks + fiberBridges;
        }
    }

    private NetworkPower() {
    }

    /** Counts the routed devices of {@code network}; fiber members weigh {@code fiberCapacityWeight} for the capacity. */
    public static Counts counts(NetworkGraph.Result network, double fiberCapacityWeight) {
        int uplinks = 0, advanced = 0, bridges = 0, extraHops = 0, fu = 0, fa = 0, fb = 0, wired = 0;
        double capacity = 0;
        for (var device : network.devices()) {
            var route = network.route(device.id());
            if (route == null || !route.connected()) continue;
            boolean fiber = route.fiber();
            switch (device.type()) {
                case UPLINK -> {
                    uplinks++;
                    if (fiber) fu++;
                }
                case ADVANCED_UPLINK -> {
                    advanced++;
                    if (fiber) fa++;
                }
                case BRIDGE -> {
                    bridges++;
                    if (fiber) fb++;
                }
            }
            if (route.wired()) wired++;
            capacity += fiber ? Math.max(0, fiberCapacityWeight) : 1;
            extraHops += route.extraHops();
        }
        return new Counts(uplinks, advanced, bridges, extraHops, fu, fa, fb, wired, capacity);
    }

    /** FE of {@code count} devices of which {@code fiber} pay {@code factor} of the rate (rounded up per category). */
    static long share(long rate, int count, int fiber, double factor) {
        rate = Math.max(0, rate);
        long full = rate * (count - fiber);
        long reduced = (long) Math.ceil(rate * fiber * Math.max(0, Math.min(1, factor)) - 1e-9);
        return full + reduced;
    }

    /** Ordered breakdown (key → FE/t), plus "total". */
    public static Map<String, Long> breakdown(Rates rates, Counts counts, int clients, double fiberPowerFactor) {
        var map = new LinkedHashMap<String, Long>();
        map.put("base", Math.max(0, rates.base()));
        map.put("uplinks", share(rates.uplink(), counts.uplinks(), counts.fiberUplinks(), fiberPowerFactor));
        map.put("advanced_uplinks", share(rates.advancedUplink(), counts.advancedUplinks(), counts.fiberAdvancedUplinks(), fiberPowerFactor));
        map.put("bridges", share(rates.bridge(), counts.bridges(), counts.fiberBridges(), fiberPowerFactor));
        map.put("clients", Math.max(0, rates.client()) * Math.max(0, clients));
        map.put("hops", Math.max(0, rates.extraHop()) * counts.extraHops());
        long total = 0;
        for (var v : map.values()) total += v;
        map.put("total", total);
        return map;
    }

    /** Breakdown without fiber reductions (v0.4 behaviour for networks without fiber). */
    public static Map<String, Long> breakdown(Rates rates, NetworkGraph.Result network, int clients) {
        return breakdown(rates, counts(network, 1), clients, 1);
    }
}
