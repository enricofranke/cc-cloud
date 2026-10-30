package ai.ezfox.cccloud.network.graph;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mainframe's FE usage for its network (docs/SPEC.md "v0.4 Power scaling", "Cables"), as a breakdown for the
 * screen and the dashboard: base + per uplink / advanced uplink / Cloud Probe / bridge / redstone device + per dashboard client + per
 * air hop beyond the first for every routed device. Cable hops cost nothing; devices attached by fiber pay a reduced device share
 * ({@code fiberPowerFactor}) and count with {@code fiberCapacityWeight} against the device capacity. Pure Java, unit
 * tested.
 */
public final class NetworkPower {
    public record Rates(long base, long uplink, long advancedUplink, long bridge, long client, long extraHop, long redstone, long probe) {
        public Rates(long base, long uplink, long advancedUplink, long bridge, long client, long extraHop) {
            this(base, uplink, advancedUplink, bridge, client, extraHop, 0);
        }

        /** Cloud Probes cost like an advanced uplink here. */
        public Rates(long base, long uplink, long advancedUplink, long bridge, long client, long extraHop, long redstone) {
            this(base, uplink, advancedUplink, bridge, client, extraHop, redstone, advancedUplink);
        }
    }

    /**
     * Routed devices of one network. {@code fiber*} are the subset attached by fiber; {@code capacityUsed} is the
     * device count with fiber members weighted.
     */
    public record Counts(int uplinks, int advancedUplinks, int bridges, int extraHops, int fiberUplinks, int fiberAdvancedUplinks,
                         int fiberBridges, int wired, double capacityUsed, int redstone, int probes, int fiberProbes) {
        public static final Counts EMPTY = new Counts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        public Counts(int uplinks, int advancedUplinks, int bridges, int extraHops, int fiberUplinks, int fiberAdvancedUplinks,
                      int fiberBridges, int wired, double capacityUsed) {
            this(uplinks, advancedUplinks, bridges, extraHops, fiberUplinks, fiberAdvancedUplinks, fiberBridges, wired, capacityUsed, 0);
        }

        public Counts(int uplinks, int advancedUplinks, int bridges, int extraHops, int fiberUplinks, int fiberAdvancedUplinks,
                      int fiberBridges, int wired, double capacityUsed, int redstone) {
            this(uplinks, advancedUplinks, bridges, extraHops, fiberUplinks, fiberAdvancedUplinks, fiberBridges, wired, capacityUsed, redstone, 0, 0);
        }

        public int devices() {
            return uplinks + advancedUplinks + bridges + redstone + probes;
        }

        public int fiber() {
            return fiberUplinks + fiberAdvancedUplinks + fiberBridges + fiberProbes;
        }
    }

    private NetworkPower() {
    }

    /** Counts the routed devices of {@code network}; fiber members weigh {@code fiberCapacityWeight} for the capacity. */
    public static Counts counts(NetworkGraph.Result network, double fiberCapacityWeight) {
        return counts(network, fiberCapacityWeight, 1);
    }

    /** As above; Cloud Redstone devices weigh {@code redstoneCapacityWeight}. */
    public static Counts counts(NetworkGraph.Result network, double fiberCapacityWeight, double redstoneCapacityWeight) {
        return counts(network, fiberCapacityWeight, redstoneCapacityWeight, 1);
    }

    /** As above; a Cloud Probe counts {@code probeUnits} devices (times its fiber weight). */
    public static Counts counts(NetworkGraph.Result network, double fiberCapacityWeight, double redstoneCapacityWeight, double probeUnits) {
        int uplinks = 0, advanced = 0, bridges = 0, redstone = 0, extraHops = 0, fu = 0, fa = 0, fb = 0, wired = 0, probes = 0, fp = 0;
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
                case REDSTONE -> redstone++;
                case PROBE -> {
                    probes++;
                    if (fiber) fp++;
                }
            }
            if (route.wired()) wired++;
            capacity += Capacity.weight(device, route, fiberCapacityWeight, redstoneCapacityWeight, probeUnits);
            extraHops += route.extraHops();
        }
        return new Counts(uplinks, advanced, bridges, extraHops, fu, fa, fb, wired, capacity, redstone, probes, fp);
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
        return breakdown(rates, counts, clients, fiberPowerFactor, 1, 0);
    }

    /**
     * With upgrades: {@code feFactor} (Efficiency card / Quantum Core) scales the network part (base … hops) and shows
     * up as a negative {@code efficiency} item; {@code anchorFe} (Chunk Anchor card) is added as {@code anchor}.
     */
    public static Map<String, Long> breakdown(Rates rates, Counts counts, int clients, double fiberPowerFactor, double feFactor, long anchorFe) {
        var map = breakdownRaw(rates, counts, clients, fiberPowerFactor);
        long network = map.remove("total");
        long total = network;
        if (anchorFe > 0) {
            map.put("anchor", anchorFe);
            total += anchorFe;
        }
        double factor = Math.max(0, Math.min(1, feFactor));
        if (factor < 1) {
            long saved = Math.round(network * (1 - factor));
            map.put("efficiency", -saved);
            total -= saved;
        }
        map.put("total", total);
        return map;
    }

    private static Map<String, Long> breakdownRaw(Rates rates, Counts counts, int clients, double fiberPowerFactor) {
        var map = new LinkedHashMap<String, Long>();
        map.put("base", Math.max(0, rates.base()));
        map.put("uplinks", share(rates.uplink(), counts.uplinks(), counts.fiberUplinks(), fiberPowerFactor));
        map.put("advanced_uplinks", share(rates.advancedUplink(), counts.advancedUplinks(), counts.fiberAdvancedUplinks(), fiberPowerFactor));
        map.put("bridges", share(rates.bridge(), counts.bridges(), counts.fiberBridges(), fiberPowerFactor));
        if (counts.probes() > 0) map.put("probes", share(rates.probe(), counts.probes(), counts.fiberProbes(), fiberPowerFactor));
        map.put("redstone", Math.max(0, rates.redstone()) * counts.redstone());
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
