package ai.ezfox.cccloud.network.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The cloud network of one mainframe as a graph (docs/SPEC.md "v0.4 Range and topology"). Pure Java and unit tested;
 * the game side ({@code NetworkService}) collects the inputs and only recomputes on topology changes.
 * <p>
 * Nodes are the mainframe, relays and ender relays; devices (uplinks, bridges) attach to the best reachable node.
 * A radio link exists when either side's radio reaches the other (distance ≤ the larger of the two ranges, same
 * dimension); its signal is {@code 1 − distance / range}. Ender relays of the same group are linked across any
 * distance and dimension. Routing: fewest hops, then best signal (the weakest link on the path), max {@link #MAX_HOPS}.
 * <p>
 * Cables (docs/SPEC.md "Cables"): elements that share a CC wired network ({@code cables}) or a fiber network
 * ({@code fibers}) are linked with 100 % signal at any distance. Cable links cost no air hop, so routing minimises air
 * hops first (radio/ender links, capped at {@link #MAX_HOPS}), then all hops, then the weakest link; a device that is
 * cabled to a reachable node always uses the cable. A radio link of a cabled pair is not reported.
 */
public final class NetworkGraph {
    public static final int MAX_HOPS = 8;
    public static final double WEAK = 0.15;

    public enum NodeType {
        MAINFRAME("mainframe"), RELAY("relay"), ADVANCED_RELAY("advanced_relay"), ENDER_RELAY("ender_relay");

        public final String id;

        NodeType(String id) {
            this.id = id;
        }
    }

    public enum DeviceType {
        UPLINK("uplink"), ADVANCED_UPLINK("advanced_uplink"), BRIDGE("bridge"),
        /** Cloud Probe: links like an advanced uplink, counts {@code probeCapacityUnits} devices and costs more FE. */
        PROBE("probe"),
        /** Cloud Switch / Button / Lamp / Sensor: radio only, a fraction of a device in capacity and FE. */
        REDSTONE("redstone");

        public final String id;

        DeviceType(String id) {
            this.id = id;
        }
    }

    /**
     * @param nodeRange   reach to other nodes; {@code deviceRange} reach to devices
     * @param available   loaded and powered (unavailable nodes are shown but carry no traffic)
     * @param enderGroup  ender relays with the same group are linked to each other (null for other nodes)
     * @param cables      ids of the CC wired networks (cables) this node is on
     * @param fibers      ids of the fiber networks this node is on
     */
    public record Node(String id, NodeType type, String dim, double x, double y, double z, double nodeRange, double deviceRange,
                       boolean available, @Nullable String enderGroup, Set<String> cables, Set<String> fibers) {
        public Node(String id, NodeType type, String dim, double x, double y, double z, double nodeRange, double deviceRange,
                    boolean available, @Nullable String enderGroup) {
            this(id, type, dim, x, y, z, nodeRange, deviceRange, available, enderGroup, Set.of(), Set.of());
        }

        public Node withAvailable(boolean value) {
            return new Node(id, type, dim, x, y, z, nodeRange, deviceRange, value, enderGroup, cables, fibers);
        }
    }

    /**
     * @param legacy linked before v0.4: keeps working without a route while {@code legacyGrace} is on
     * @param cables ids of the CC wired networks (cables) this device is on
     * @param fibers ids of the fiber networks this device is on
     */
    public record Device(String id, DeviceType type, String dim, double x, double y, double z, double range, boolean legacy,
                         Set<String> cables, Set<String> fibers) {
        public Device(String id, DeviceType type, String dim, double x, double y, double z, double range, boolean legacy) {
            this(id, type, dim, x, y, z, range, legacy, Set.of(), Set.of());
        }
    }

    /** RADIO and ENDER are "air" links (they count as hops); WIRED (CC cable) and FIBER are cables: 100 %, no range, no hop FE. */
    public enum LinkKind {
        RADIO, ENDER, WIRED, FIBER;

        public boolean cable() {
            return this == WIRED || this == FIBER;
        }
    }

    /**
     * @param legacy the synthetic link of a {@code legacy_out_of_range} device to its mainframe (signal 0)
     */
    public record Link(String a, String b, double signal, double distance, LinkKind kind, boolean legacy) {
        public Link(String a, String b, double signal, double distance, LinkKind kind) {
            this(a, b, signal, distance, kind, false);
        }

        public boolean weak() {
            return kind == LinkKind.RADIO && signal < WEAK && !legacy;
        }
    }

    /**
     * Where a device is attached: path of node ids from the mainframe to its node; empty if it has no route.
     *
     * @param via     kind of the device's own link (cable kinds are preferred over radio)
     * @param airHops radio/ender links on the whole route, the device link included (cables are free)
     */
    public record Route(List<String> path, double signal, double distance, boolean legacyOutOfRange, LinkKind via, int airHops) {
        public Route(List<String> path, double signal, double distance, boolean legacyOutOfRange) {
            this(path, signal, distance, legacyOutOfRange, LinkKind.RADIO, path.size());
        }

        public boolean connected() {
            return !path.isEmpty();
        }

        /** Hops from the mainframe to the device (the device link included). */
        public int hops() {
            return path.size();
        }

        /** Attached by CC cable or fiber. */
        public boolean wired() {
            return via.cable();
        }

        public boolean fiber() {
            return via == LinkKind.FIBER;
        }

        /** Hops that cost FE: air hops beyond the first. */
        public int extraHops() {
            return Math.max(0, airHops - 1);
        }
    }

    public record Result(List<Node> nodes, List<Device> devices, List<Link> links, Map<String, Route> routes, Map<String, List<String>> nodePaths) {
        public @Nullable Route route(String deviceId) {
            return routes.get(deviceId);
        }

        /** Nodes reachable from the mainframe (they carry this network's traffic). */
        public boolean reachable(String nodeId) {
            return nodePaths.containsKey(nodeId);
        }
    }

    private NetworkGraph() {
    }

    static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static @Nullable Link radio(String a, String dimA, double ax, double ay, double az, double rangeA,
                                        String b, String dimB, double bx, double by, double bz, double rangeB) {
        if (!dimA.equals(dimB)) return null;
        double range = Math.max(rangeA, rangeB);
        double d = distance(ax, ay, az, bx, by, bz);
        if (range <= 0 || d > range) return null;
        return new Link(a, b, 1 - d / range, d, LinkKind.RADIO);
    }

    /** The cable two elements share: fiber first (it is the better medium), then CC cable; null if none. */
    static @Nullable LinkKind sharedCable(Set<String> cablesA, Set<String> fibersA, Set<String> cablesB, Set<String> fibersB) {
        for (var f : fibersA) if (fibersB.contains(f)) return LinkKind.FIBER;
        for (var c : cablesA) if (cablesB.contains(c)) return LinkKind.WIRED;
        return null;
    }

    private static double cableDistance(String dimA, double ax, double ay, double az, String dimB, double bx, double by, double bz) {
        return dimA.equals(dimB) ? distance(ax, ay, az, bx, by, bz) : -1;
    }

    /** Lexicographic path cost: air hops, then all hops, then the weakest link (higher is better). */
    private record Cost(String node, int air, int hops, double signal, long seq) implements Comparable<Cost> {
        @Override
        public int compareTo(Cost o) {
            if (air != o.air) return Integer.compare(air, o.air);
            if (hops != o.hops) return Integer.compare(hops, o.hops);
            if (signal != o.signal) return Double.compare(o.signal, signal);
            return Long.compare(seq, o.seq);
        }

        boolean betterThan(Cost o) {
            if (air != o.air) return air < o.air;
            if (hops != o.hops) return hops < o.hops;
            return signal > o.signal;
        }
    }

    /**
     * @param mainframeId id of the mainframe node in {@code nodes}
     * @param legacyGrace config {@code legacyRangeGrace}
     */
    public static Result compute(String mainframeId, List<Node> nodes, List<Device> devices, boolean legacyGrace) {
        var byId = new LinkedHashMap<String, Node>();
        for (var n : nodes) byId.put(n.id(), n);
        var root = byId.get(mainframeId);

        // Node-node links (only between available nodes). A shared cable replaces the radio/ender link of that pair.
        var links = new ArrayList<Link>();
        var adjacency = new HashMap<String, List<Link>>();
        var list = new ArrayList<>(byId.values());
        for (int i = 0; i < list.size(); i++) {
            var a = list.get(i);
            if (!a.available()) continue;
            for (int j = i + 1; j < list.size(); j++) {
                var b = list.get(j);
                if (!b.available()) continue;
                Link link;
                var cable = sharedCable(a.cables(), a.fibers(), b.cables(), b.fibers());
                if (cable != null) {
                    link = new Link(a.id(), b.id(), 1, cableDistance(a.dim(), a.x(), a.y(), a.z(), b.dim(), b.x(), b.y(), b.z()), cable);
                } else if (a.enderGroup() != null && a.enderGroup().equals(b.enderGroup())) {
                    link = new Link(a.id(), b.id(), 1, a.dim().equals(b.dim()) ? distance(a.x(), a.y(), a.z(), b.x(), b.y(), b.z()) : -1, LinkKind.ENDER);
                } else {
                    link = radio(a.id(), a.dim(), a.x(), a.y(), a.z(), a.nodeRange(), b.id(), b.dim(), b.x(), b.y(), b.z(), b.nodeRange());
                }
                if (link == null) continue;
                links.add(link);
                adjacency.computeIfAbsent(a.id(), k -> new ArrayList<>()).add(link);
                adjacency.computeIfAbsent(b.id(), k -> new ArrayList<>()).add(link);
            }
        }

        // Dijkstra over (air hops, hops, weakest link): cables cost no air hop, so a cabled path wins over radio.
        // The order is isotone, so the result is exact; air hops are capped at MAX_HOPS.
        var paths = new HashMap<String, List<String>>();
        var best = new HashMap<String, Cost>();
        if (root != null && root.available()) {
            long seq = 0;
            var queue = new PriorityQueue<Cost>();
            var start = new Cost(root.id(), 0, 0, 1.0, seq++);
            best.put(root.id(), start);
            paths.put(root.id(), List.of(root.id()));
            queue.add(start);
            var settled = new HashSet<String>();
            while (!queue.isEmpty()) {
                var cur = queue.poll();
                if (!settled.add(cur.node())) continue;
                for (var link : adjacency.getOrDefault(cur.node(), List.of())) {
                    String to = link.a().equals(cur.node()) ? link.b() : link.a();
                    if (settled.contains(to)) continue;
                    int air = cur.air() + (link.kind().cable() ? 0 : 1);
                    if (air > MAX_HOPS) continue;
                    var cand = new Cost(to, air, cur.hops() + 1, Math.min(cur.signal(), link.signal()), seq++);
                    var old = best.get(to);
                    if (old != null && !cand.betterThan(old)) continue;
                    best.put(to, cand);
                    var path = new ArrayList<>(paths.get(cur.node()));
                    path.add(to);
                    paths.put(to, List.copyOf(path));
                    queue.add(cand);
                }
            }
        }

        // Devices: a cable attachment is preferred; then fewest air hops, fewest hops, best signal.
        var routes = new LinkedHashMap<String, Route>();
        for (var device : devices) {
            Route bestRoute = null;
            Cost bestCost = null;
            boolean bestCable = false;
            for (var node : byId.values()) {
                if (!node.available()) continue;
                Link link;
                var cable = sharedCable(device.cables(), device.fibers(), node.cables(), node.fibers());
                if (cable != null) {
                    link = new Link(node.id(), device.id(), 1, cableDistance(node.dim(), node.x(), node.y(), node.z(),
                        device.dim(), device.x(), device.y(), device.z()), cable);
                } else {
                    link = radio(device.id(), device.dim(), device.x(), device.y(), device.z(), device.range(),
                        node.id(), node.dim(), node.x(), node.y(), node.z(), node.deviceRange());
                    if (link == null) continue;
                    link = new Link(node.id(), device.id(), link.signal(), link.distance(), LinkKind.RADIO);
                }
                // Every link in range is reported (the map shows unused ones faintly); routing uses the reachable ones.
                links.add(link);
                var nodeCost = best.get(node.id());
                if (nodeCost == null || !paths.containsKey(node.id())) continue;
                int air = nodeCost.air() + (link.kind().cable() ? 0 : 1);
                if (air > MAX_HOPS) continue; // the device link would exceed the hop limit
                var cost = new Cost(device.id(), air, nodeCost.hops() + 1, Math.min(nodeCost.signal(), link.signal()), 0);
                boolean isCable = link.kind().cable();
                boolean better = bestRoute == null || (isCable && !bestCable) || (isCable == bestCable && cost.betterThan(bestCost));
                if (better) {
                    bestRoute = new Route(paths.get(node.id()), cost.signal(), link.distance(), false, link.kind(), air);
                    bestCost = cost;
                    bestCable = isCable;
                }
            }
            if (bestRoute != null) {
                routes.put(device.id(), bestRoute);
            } else if (device.legacy() && legacyGrace && root != null && root.available()) {
                // v0.3 device that is out of range under the new rules: keeps working directly, flagged on the map.
                double d = root.dim().equals(device.dim()) ? distance(root.x(), root.y(), root.z(), device.x(), device.y(), device.z()) : -1;
                routes.put(device.id(), new Route(List.of(root.id()), 0, d, true));
                links.add(new Link(root.id(), device.id(), 0, d, LinkKind.RADIO, true));
            } else {
                routes.put(device.id(), new Route(List.of(), 0, -1, false));
            }
        }
        return new Result(List.copyOf(byId.values()), List.copyOf(devices), List.copyOf(links), routes, paths);
    }

    /**
     * For linking errors: the nearest available node of the network (same dimension) and its distance; null if none.
     */
    public static Map.@Nullable Entry<Node, Double> nearestNode(Result result, String dim, double x, double y, double z) {
        Node best = null;
        double bestD = Double.MAX_VALUE;
        for (var node : result.nodes()) {
            if (!node.available() || !result.reachable(node.id()) || !node.dim().equals(dim)) continue;
            double d = distance(node.x(), node.y(), node.z(), x, y, z);
            if (d < bestD) {
                bestD = d;
                best = node;
            }
        }
        return best == null ? null : Map.entry(best, bestD);
    }
}
