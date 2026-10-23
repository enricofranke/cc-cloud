package ai.ezfox.cccloud.network.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The cloud network of one mainframe as a graph (docs/SPEC.md "v0.4 Range and topology"). Pure Java and unit tested;
 * the game side ({@code NetworkService}) collects the inputs and only recomputes on topology changes.
 * <p>
 * Nodes are the mainframe, relays and ender relays; devices (uplinks, bridges) attach to the best reachable node.
 * A radio link exists when either side's radio reaches the other (distance ≤ the larger of the two ranges, same
 * dimension); its signal is {@code 1 − distance / range}. Ender relays of the same group are linked across any
 * distance and dimension. Routing: fewest hops, then best signal (the weakest link on the path), max {@link #MAX_HOPS}.
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
        UPLINK("uplink"), ADVANCED_UPLINK("advanced_uplink"), BRIDGE("bridge");

        public final String id;

        DeviceType(String id) {
            this.id = id;
        }
    }

    /**
     * @param nodeRange   reach to other nodes; {@code deviceRange} reach to devices
     * @param available   loaded and powered (unavailable nodes are shown but carry no traffic)
     * @param enderGroup  ender relays with the same group are linked to each other (null for other nodes)
     */
    public record Node(String id, NodeType type, String dim, double x, double y, double z, double nodeRange, double deviceRange,
                       boolean available, @Nullable String enderGroup) {
    }

    /**
     * @param legacy linked before v0.4: keeps working without a route while {@code legacyGrace} is on
     */
    public record Device(String id, DeviceType type, String dim, double x, double y, double z, double range, boolean legacy) {
    }

    public enum LinkKind { RADIO, ENDER }

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

    /** Where a device is attached: path of node ids from the mainframe to its node; empty if it has no route. */
    public record Route(List<String> path, double signal, double distance, boolean legacyOutOfRange) {
        public boolean connected() {
            return !path.isEmpty();
        }

        /** Hops from the mainframe to the device (the device link included). */
        public int hops() {
            return path.size();
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

    /**
     * @param mainframeId id of the mainframe node in {@code nodes}
     * @param legacyGrace config {@code legacyRangeGrace}
     */
    public static Result compute(String mainframeId, List<Node> nodes, List<Device> devices, boolean legacyGrace) {
        var byId = new LinkedHashMap<String, Node>();
        for (var n : nodes) byId.put(n.id(), n);
        var root = byId.get(mainframeId);

        // Node-node links (only between available nodes).
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
                if (a.enderGroup() != null && a.enderGroup().equals(b.enderGroup())) {
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

        // BFS by hops; among equal hop counts keep the path whose weakest link is strongest.
        var paths = new HashMap<String, List<String>>();
        var pathSignal = new HashMap<String, Double>();
        if (root != null && root.available()) {
            paths.put(root.id(), List.of(root.id()));
            pathSignal.put(root.id(), 1.0);
            var frontier = List.of(root.id());
            for (int hop = 1; hop <= MAX_HOPS && !frontier.isEmpty(); hop++) {
                var next = new LinkedHashMap<String, List<String>>();
                var nextSignal = new HashMap<String, Double>();
                for (var from : frontier) {
                    for (var link : adjacency.getOrDefault(from, List.of())) {
                        String to = link.a().equals(from) ? link.b() : link.a();
                        if (paths.containsKey(to)) continue;
                        double signal = Math.min(pathSignal.get(from), link.signal());
                        if (nextSignal.containsKey(to) && nextSignal.get(to) >= signal) continue;
                        var path = new ArrayList<>(paths.get(from));
                        path.add(to);
                        next.put(to, List.copyOf(path));
                        nextSignal.put(to, signal);
                    }
                }
                paths.putAll(next);
                pathSignal.putAll(nextSignal);
                frontier = List.copyOf(next.keySet());
            }
        }

        // Devices: attach to the reachable node with the fewest hops, then the best signal.
        var routes = new LinkedHashMap<String, Route>();
        for (var device : devices) {
            Route best = null;
            double bestSignal = -1;
            for (var node : byId.values()) {
                if (!node.available()) continue;
                var link = radio(device.id(), device.dim(), device.x(), device.y(), device.z(), device.range(),
                    node.id(), node.dim(), node.x(), node.y(), node.z(), node.deviceRange());
                if (link == null) continue;
                // Every link in range is reported (the map shows unused ones faintly); routing uses the reachable ones.
                links.add(new Link(node.id(), device.id(), link.signal(), link.distance(), LinkKind.RADIO));
                if (!paths.containsKey(node.id())) continue;
                var path = paths.get(node.id());
                if (path.size() >= MAX_HOPS + 1) continue; // the device link would exceed the hop limit
                double signal = Math.min(pathSignal.get(node.id()), link.signal());
                boolean better = best == null || path.size() < best.path().size() || (path.size() == best.path().size() && signal > bestSignal);
                if (better) {
                    best = new Route(path, signal, link.distance(), false);
                    bestSignal = signal;
                }
            }
            if (best != null) {
                routes.put(device.id(), best);
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
