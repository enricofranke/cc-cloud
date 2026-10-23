package ai.ezfox.cccloud.analyzer;

import ai.ezfox.cccloud.network.graph.NetworkGraph;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The Cloud Analyzer's data (docs/SPEC.md "In-game network visualization"): the part of the player's networks around
 * them (nodes with their ranges and hop counts, devices with status and signal, links), and the placement preview
 * ("would connect to … / out of range"). Pure Java, shared by the server (building the payload), the client (overlay,
 * HUD, preview) and the tests. Uses the same link rules as {@link NetworkGraph}: two ends link when their distance is
 * at most the larger of their ranges, signal = 1 − distance / range, a route's signal is its weakest link, devices pick
 * the fewest hops, then the best signal.
 */
public final class AnalyzerGeometry {
    /** Only things within this distance of the player are sent. */
    public static final int RADIUS = 128;
    /** Nodes + devices + links per payload at most. */
    public static final int MAX_ELEMENTS = 256;

    /** One network the player may see: its mainframe id/name, the computed topology, display names and statuses by id. */
    public record Network(String mainframeId, String name, NetworkGraph.Result result, Map<String, String> names, Map<String, String> statuses) {
    }

    /** A node as the client sees it. {@code hops} = −1 if it does not carry this network's traffic. */
    public record PNode(String id, String name, String type, String dim, double x, double y, double z, double nodeRange, double deviceRange,
                        boolean available, int hops, double pathSignal) {
        public boolean reachable() {
            return available && hops >= 0;
        }
    }

    /** What a device or relay placed at a position would do. {@code via} is null when nothing is in range. */
    public record Preview(boolean connects, @Nullable PNode via, double signal, int hops, @Nullable PNode nearest, double distance, double range) {
    }

    private AnalyzerGeometry() {
    }

    static double dist(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---- server: payload --------------------------------------------------------------------------------------------

    /**
     * Builds the payload for a player at ({@code x,y,z}) in {@code dim}: nodes, devices and links of the given networks
     * (already filtered by role) within {@link #RADIUS}, at most {@link #MAX_ELEMENTS}, nearest first.
     */
    public static JsonObject build(List<Network> networks, String dim, double x, double y, double z, Map<String, int[]> ranges) {
        var nodes = new JsonArray();
        var devices = new JsonArray();
        var links = new JsonArray();
        record Candidate(double distance, String kind, JsonObject json) {
        }
        var candidates = new ArrayList<Candidate>();
        for (var net : networks) {
            var result = net.result();
            var positions = new HashMap<String, double[]>();
            var signals = new HashMap<String, Double>();
            for (var link : result.links()) signals.put(key(link.a(), link.b()), link.signal());
            for (var node : result.nodes()) {
                positions.put(node.id(), new double[]{node.x(), node.y(), node.z()});
                if (!node.dim().equals(dim)) continue;
                double d = dist(node.x(), node.y(), node.z(), x, y, z);
                if (d > RADIUS + node.deviceRange()) continue; // its bubble may reach the player
                var path = result.nodePaths().get(node.id());
                var n = new JsonObject();
                n.addProperty("id", node.id());
                n.addProperty("net", net.mainframeId());
                n.addProperty("name", net.names().getOrDefault(node.id(), node.type().id));
                n.addProperty("type", node.type().id);
                n.addProperty("x", round(node.x()));
                n.addProperty("y", round(node.y()));
                n.addProperty("z", round(node.z()));
                n.addProperty("nr", (int) node.nodeRange());
                n.addProperty("dr", (int) node.deviceRange());
                n.addProperty("ok", node.available());
                n.addProperty("hops", path == null ? -1 : path.size() - 1);
                n.addProperty("ps", path == null ? 0 : round2(pathSignal(path, signals)));
                n.addProperty("status", net.statuses().getOrDefault(node.id(), node.available() ? (path != null ? "online" : "offline") : "no_power"));
                candidates.add(new Candidate(d, "n", n));
            }
            for (var device : result.devices()) {
                positions.put(device.id(), new double[]{device.x(), device.y(), device.z()});
                if (!device.dim().equals(dim)) continue;
                double d = dist(device.x(), device.y(), device.z(), x, y, z);
                if (d > RADIUS) continue;
                var route = result.route(device.id());
                var n = new JsonObject();
                n.addProperty("id", device.id());
                n.addProperty("net", net.mainframeId());
                n.addProperty("name", net.names().getOrDefault(device.id(), device.type().id));
                n.addProperty("type", device.type().id);
                n.addProperty("x", round(device.x()));
                n.addProperty("y", round(device.y()));
                n.addProperty("z", round(device.z()));
                n.addProperty("r", (int) device.range());
                boolean connected = route != null && route.connected();
                n.addProperty("signal", connected ? round2(route.signal()) : 0);
                n.addProperty("hops", connected ? route.hops() : -1);
                n.addProperty("status", net.statuses().getOrDefault(device.id(), connected ? (route.legacyOutOfRange() ? "legacy_out_of_range" : "online") : "no_route"));
                candidates.add(new Candidate(d, "d", n));
            }
            for (var link : result.links()) {
                var a = positions.get(link.a());
                var b = positions.get(link.b());
                if (a == null || b == null || link.distance() < 0) continue;
                double d = Math.min(dist(a[0], a[1], a[2], x, y, z), dist(b[0], b[1], b[2], x, y, z));
                if (d > RADIUS || !sameDim(result, link.a(), link.b(), dim)) continue;
                var l = new JsonObject();
                l.addProperty("a", link.a());
                l.addProperty("b", link.b());
                l.addProperty("signal", round2(link.signal()));
                l.addProperty("kind", link.kind() == NetworkGraph.LinkKind.ENDER ? "ender" : "radio");
                if (isRouteLink(result, link)) l.addProperty("used", true);
                candidates.add(new Candidate(d + 0.5, "l", l));
            }
        }
        candidates.sort((p, q) -> Double.compare(p.distance(), q.distance()));
        int count = 0;
        for (var c : candidates) {
            if (count++ >= MAX_ELEMENTS) break;
            switch (c.kind()) {
                case "n" -> nodes.add(c.json());
                case "d" -> devices.add(c.json());
                default -> links.add(c.json());
            }
        }
        var json = new JsonObject();
        json.add("nodes", nodes);
        json.add("devices", devices);
        json.add("links", links);
        var r = new JsonObject();
        for (var e : ranges.entrySet()) {
            var a = new JsonArray();
            for (int v : e.getValue()) a.add(v);
            r.add(e.getKey(), a);
        }
        json.add("ranges", r);
        json.addProperty("dim", dim);
        return json;
    }

    private static boolean sameDim(NetworkGraph.Result result, String a, String b, String dim) {
        String da = dimOf(result, a), db = dimOf(result, b);
        return dim.equals(da) && dim.equals(db);
    }

    private static @Nullable String dimOf(NetworkGraph.Result result, String id) {
        for (var n : result.nodes()) if (n.id().equals(id)) return n.dim();
        for (var d : result.devices()) if (d.id().equals(id)) return d.dim();
        return null;
    }

    /** A link that a route uses (node paths and device attachments). */
    private static boolean isRouteLink(NetworkGraph.Result result, NetworkGraph.Link link) {
        for (var path : result.nodePaths().values()) {
            for (int i = 1; i < path.size(); i++) {
                if (key(path.get(i - 1), path.get(i)).equals(key(link.a(), link.b()))) return true;
            }
        }
        for (var e : result.routes().entrySet()) {
            var path = e.getValue().path();
            if (!e.getValue().connected() || path.isEmpty()) continue;
            if (key(path.getLast(), e.getKey()).equals(key(link.a(), link.b()))) return true;
        }
        return false;
    }

    static double pathSignal(List<String> path, Map<String, Double> signals) {
        double s = 1;
        for (int i = 1; i < path.size(); i++) s = Math.min(s, signals.getOrDefault(key(path.get(i - 1), path.get(i)), 0.0));
        return s;
    }

    static String key(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    // ---- client: parsing & preview -----------------------------------------------------------------------------------

    public static List<PNode> nodes(JsonObject payload) {
        var list = new ArrayList<PNode>();
        if (!payload.has("nodes")) return list;
        for (var e : payload.getAsJsonArray("nodes")) {
            var n = e.getAsJsonObject();
            list.add(new PNode(n.get("id").getAsString(), n.get("name").getAsString(), n.get("type").getAsString(), payload.get("dim").getAsString(),
                n.get("x").getAsDouble(), n.get("y").getAsDouble(), n.get("z").getAsDouble(), n.get("nr").getAsDouble(), n.get("dr").getAsDouble(),
                n.get("ok").getAsBoolean(), n.get("hops").getAsInt(), n.get("ps").getAsDouble()));
        }
        return list;
    }

    /**
     * What would happen if a device with range {@code deviceRange} (or, with {@code asNode}, a relay with node range
     * {@code range}) were placed at the position: the node it would attach to, its signal and hop count; or the
     * nearest node and how far away it is.
     */
    public static Preview preview(List<PNode> nodes, String dim, double x, double y, double z, double range, boolean asNode) {
        PNode best = null;
        double bestSignal = -1;
        int bestHops = Integer.MAX_VALUE;
        PNode nearest = null;
        double nearestD = Double.MAX_VALUE, nearestRange = 0;
        for (var node : nodes) {
            if (!node.available() || !node.dim().equals(dim)) continue;
            double d = dist(node.x(), node.y(), node.z(), x, y, z);
            double r = Math.max(range, asNode ? node.nodeRange() : node.deviceRange());
            if (node.reachable() && d < nearestD) {
                nearestD = d;
                nearest = node;
                nearestRange = r;
            }
            if (r <= 0 || d > r || !node.reachable()) continue;
            int hops = node.hops() + 1;
            if (hops > NetworkGraph.MAX_HOPS) continue;
            double signal = Math.min(node.pathSignal(), 1 - d / r);
            if (best == null || hops < bestHops || (hops == bestHops && signal > bestSignal)) {
                best = node;
                bestHops = hops;
                bestSignal = signal;
            }
        }
        if (best != null) return new Preview(true, best, bestSignal, bestHops, nearest, nearestD, nearestRange);
        return new Preview(false, null, 0, -1, nearest, nearest == null ? -1 : nearestD, nearestRange);
    }
}
