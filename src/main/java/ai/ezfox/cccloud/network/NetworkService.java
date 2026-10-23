package ai.ezfox.cccloud.network;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.bridge.BridgeSection;
import ai.ezfox.cccloud.network.graph.NetworkGraph;
import ai.ezfox.cccloud.network.graph.NetworkPower;
import ai.ezfox.cccloud.relay.CloudRelayBlockEntity;
import ai.ezfox.cccloud.relay.RelayType;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Game side of the v0.4 network: collects mainframes, relays and devices, computes every mainframe's
 * {@link NetworkGraph} and keeps the result for status, routing, linking checks, power and the map. Server thread only.
 * <p>
 * Recomputed only when the topology changes ({@link #markDirty()}: blocks placed/broken/loaded/unloaded, power flips,
 * links, config reload) plus a cheap safety pass every 10 s. Devices only look up their route; nothing iterates all
 * devices per tick.
 */
public final class NetworkService {
    private static final int SAFETY_INTERVAL = 200;

    /** Device counts per mainframe for the power formula (avoids iterating devices every tick). */
    public record Counts(int uplinks, int advancedUplinks, int bridges, int extraHops) {
        public static final Counts EMPTY = new Counts(0, 0, 0, 0);
    }

    private static boolean dirty = true;
    private static final Map<UUID, NetworkGraph.Result> RESULTS = new HashMap<>();
    private static final Map<UUID, NetworkGraph.Route> ROUTES = new HashMap<>();
    private static final Map<UUID, Counts> COUNTS = new HashMap<>();
    private static final Set<UUID> SERVING = new HashSet<>();
    private static long recomputations;
    /** Last time (ms) a node/device was seen online, and the last known device type (for unloaded devices). */
    private static final Map<String, Long> LAST_ONLINE = new HashMap<>();
    private static final Map<String, String> LAST_TYPE = new HashMap<>();

    private NetworkService() {
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void clear() {
        RESULTS.clear();
        ROUTES.clear();
        COUNTS.clear();
        SERVING.clear();
        LAST_ONLINE.clear();
        LAST_TYPE.clear();
        dirty = true;
    }

    /** Called every server tick; recomputes only when dirty (and every 10 s as a safety net). */
    public static void tick(MinecraftServer server) {
        if (!dirty && server.getTickCount() % SAFETY_INTERVAL != 0) return;
        dirty = false;
        try {
            recompute();
        } catch (RuntimeException e) {
            CCCloud.LOGGER.error("[cccloud] Could not compute the cloud network", e);
        }
    }

    public static long recomputations() {
        return recomputations;
    }

    static String dim(Level level) {
        return level.dimension().identifier().toString();
    }

    public static NetworkGraph.DeviceType deviceType(CloudUplinkBlockEntity uplink) {
        return uplink.isAdvanced() ? NetworkGraph.DeviceType.ADVANCED_UPLINK : NetworkGraph.DeviceType.UPLINK;
    }

    public static int deviceRange(NetworkGraph.DeviceType type) {
        return CloudConfig.get(switch (type) {
            case UPLINK -> CloudConfig.RANGE_UPLINK;
            case ADVANCED_UPLINK -> CloudConfig.RANGE_ADVANCED_UPLINK;
            case BRIDGE -> CloudConfig.RANGE_BRIDGE;
        });
    }

    private static NetworkGraph.Node mainframeNode(CloudMainframeBlockEntity m) {
        var p = m.getBlockPos();
        int range = CloudConfig.get(CloudConfig.RANGE_MAINFRAME);
        return new NetworkGraph.Node(m.getMainframeId().toString(), NetworkGraph.NodeType.MAINFRAME, dim(m.getLevel()), p.getX() + .5, p.getY() + 1, p.getZ() + .5,
            range, range, m.isOnline(), null);
    }

    private static NetworkGraph.Node relayNode(CloudRelayBlockEntity r, UUID mainframeId) {
        var p = r.getBlockPos();
        var type = r.type();
        String group = type == RelayType.ENDER && mainframeId.equals(r.enderGroup()) ? mainframeId.toString() : null;
        return new NetworkGraph.Node(r.relayId().toString(), type.nodeType, dim(r.getLevel()), p.getX() + .5, p.getY() + .5, p.getZ() + .5,
            type.nodeRange(), type.deviceRange(), r.hasPower() && !r.isRemoved(), group);
    }

    private static List<NetworkGraph.Node> nodes(CloudMainframeBlockEntity m) {
        var nodes = new ArrayList<NetworkGraph.Node>();
        nodes.add(mainframeNode(m));
        for (var relay : CloudNetwork.relays()) if (relay.getLevel() != null) nodes.add(relayNode(relay, m.getMainframeId()));
        return nodes;
    }

    private static void recompute() {
        recomputations++;
        RESULTS.clear();
        ROUTES.clear();
        COUNTS.clear();
        SERVING.clear();
        boolean grace = CloudConfig.get(CloudConfig.LEGACY_RANGE_GRACE);
        var devicesByMainframe = new HashMap<UUID, List<NetworkGraph.Device>>();
        for (var uplink : CloudNetwork.uplinks()) {
            var link = uplink.getLink();
            if (link == null || uplink.getLevel() == null || uplink.isRemoved()) continue;
            var p = uplink.getBlockPos();
            var type = deviceType(uplink);
            devicesByMainframe.computeIfAbsent(link.id(), k -> new ArrayList<>()).add(new NetworkGraph.Device(uplink.getUplinkId().toString(), type,
                dim(uplink.getLevel()), p.getX() + .5, p.getY() + .5, p.getZ() + .5, deviceRange(type), uplink.isLegacyLink()));
        }
        for (var section : CloudNetwork.bridgeSections()) {
            if (!(section instanceof BridgeSection bridge) || bridge.getLink() == null) continue;
            var be = bridge.host().asBlockEntity();
            if (be.getLevel() == null || be.isRemoved()) continue;
            var p = be.getBlockPos();
            devicesByMainframe.computeIfAbsent(bridge.getLink().id(), k -> new ArrayList<>()).add(new NetworkGraph.Device(bridge.sectionId().toString(),
                NetworkGraph.DeviceType.BRIDGE, dim(be.getLevel()), p.getX() + .5, p.getY() + .5, p.getZ() + .5,
                deviceRange(NetworkGraph.DeviceType.BRIDGE), bridge.isLegacyLink()));
        }
        for (var mainframe : CloudNetwork.mainframes()) {
            if (mainframe.getLevel() == null || mainframe.isRemoved()) continue;
            var id = mainframe.getMainframeId();
            var devices = devicesByMainframe.getOrDefault(id, List.of());
            var result = NetworkGraph.compute(id.toString(), nodes(mainframe), devices, grace);
            RESULTS.put(id, result);
            int uplinks = 0, advanced = 0, bridges = 0, hops = 0;
            for (var device : devices) {
                var route = result.route(device.id());
                ROUTES.put(UUID.fromString(device.id()), route);
                if (route == null || !route.connected()) continue;
                switch (device.type()) {
                    case UPLINK -> uplinks++;
                    case ADVANCED_UPLINK -> advanced++;
                    case BRIDGE -> bridges++;
                }
                hops += Math.max(0, route.hops() - 1);
            }
            COUNTS.put(id, new Counts(uplinks, advanced, bridges, hops));
            for (var node : result.nodes()) {
                if (node.type() != NetworkGraph.NodeType.MAINFRAME && result.reachable(node.id())) SERVING.add(UUID.fromString(node.id()));
            }
            WebHub.INSTANCE.publishNetwork(id, toJson(mainframe, result, true), toJson(mainframe, result, false));
        }
    }

    // ---- queries -----------------------------------------------------------------------------------------------------

    /** The device's route, or null while the network has not been computed yet. */
    public static NetworkGraph.@Nullable Route route(UUID deviceId) {
        return ROUTES.get(deviceId);
    }

    public static boolean isServing(UUID relayId) {
        return SERVING.contains(relayId);
    }

    public static Counts counts(UUID mainframeId) {
        return COUNTS.getOrDefault(mainframeId, Counts.EMPTY);
    }

    public static NetworkGraph.@Nullable Result result(UUID mainframeId) {
        return RESULTS.get(mainframeId);
    }

    /**
     * Linking check (Link Card): the device at {@code pos} must reach the mainframe's network under the current ranges.
     *
     * @return null if it can join, else the error to show (names the nearest node and its distance)
     */
    public static @Nullable Component checkLink(CloudMainframeBlockEntity mainframe, Level level, BlockPos pos, NetworkGraph.DeviceType type) {
        // Linking is a geometric check: nodes count even while they (or the mainframe) have no power right now.
        var nodes = new ArrayList<NetworkGraph.Node>();
        for (var n : nodes(mainframe)) {
            nodes.add(new NetworkGraph.Node(n.id(), n.type(), n.dim(), n.x(), n.y(), n.z(), n.nodeRange(), n.deviceRange(), true, n.enderGroup()));
        }
        var device = new NetworkGraph.Device("probe", type, dim(level), pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, deviceRange(type), false);
        var result = NetworkGraph.compute(mainframe.getMainframeId().toString(), nodes, List.of(device), false);
        var route = result.route("probe");
        if (route != null && route.connected()) return null;
        var nearest = NetworkGraph.nearestNode(result, dim(level), pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (nearest == null) return Component.translatable("cccloud.network.no_node_here");
        var node = nearest.getKey();
        return Component.translatable("cccloud.network.out_of_range", Component.translatable("cccloud.network.node." + node.type().id),
            (int) Math.round(nearest.getValue()), (int) Math.max(type == null ? 0 : deviceRange(type), node.deviceRange()),
            (int) Math.floor(node.x()) + " " + (int) Math.floor(node.y()) + " " + (int) Math.floor(node.z()));
    }

    /** Power breakdown of a mainframe's network ({@link NetworkPower}); clients are counted live. */
    public static Map<String, Long> power(UUID mainframeId, int clients) {
        var counts = counts(mainframeId);
        var rates = rates();
        var map = new java.util.LinkedHashMap<String, Long>();
        map.put("base", rates.base());
        map.put("uplinks", rates.uplink() * counts.uplinks());
        map.put("advanced_uplinks", rates.advancedUplink() * counts.advancedUplinks());
        map.put("bridges", rates.bridge() * counts.bridges());
        map.put("clients", rates.client() * Math.max(0, clients));
        map.put("hops", rates.extraHop() * counts.extraHops());
        long total = 0;
        for (var v : map.values()) total += Math.max(0, v);
        map.put("total", total);
        return map;
    }

    /** {@code [{key, count?, fe}]} for the dashboard (keys base, uplinks, advanced_uplinks, bridges, clients, hops; sums to usage). */
    public static JsonArray breakdownJson(UUID mainframeId, int clients) {
        var counts = counts(mainframeId);
        var power = power(mainframeId, clients);
        var array = new JsonArray();
        for (var e : power.entrySet()) {
            if (e.getKey().equals("total")) continue;
            var item = new JsonObject();
            item.addProperty("key", e.getKey());
            Integer count = switch (e.getKey()) {
                case "uplinks" -> counts.uplinks();
                case "advanced_uplinks" -> counts.advancedUplinks();
                case "bridges" -> counts.bridges();
                case "clients" -> Math.max(0, clients);
                case "hops" -> counts.extraHops();
                default -> null;
            };
            if (count != null) item.addProperty("count", count);
            item.addProperty("fe", e.getValue());
            array.add(item);
        }
        return array;
    }

    public static NetworkPower.Rates rates() {
        return new NetworkPower.Rates(CloudConfig.get(CloudConfig.MAINFRAME_BASE_FE), CloudConfig.get(CloudConfig.MAINFRAME_PER_UPLINK_FE),
            CloudConfig.get(CloudConfig.MAINFRAME_PER_ADVANCED_UPLINK_FE), CloudConfig.get(CloudConfig.MAINFRAME_PER_BRIDGE_FE),
            CloudConfig.get(CloudConfig.MAINFRAME_PER_CLIENT_FE), CloudConfig.get(CloudConfig.MAINFRAME_PER_HOP_FE));
    }

    // ---- JSON (snapshot, deltas, Lua, in-game map) -------------------------------------------------------------------

    /**
     * {@code {nodes:[{id,type,name,pos?,dim,status,power?,range,deviceRange,flags?}], links:[{a,b,signal,dist?,kind}], routes:{deviceId:[nodeIds]}}}.
     * Without positions (read-only dashboards) {@code pos} and {@code dist} are left out.
     */
    public static JsonObject toJson(CloudMainframeBlockEntity mainframe, NetworkGraph.Result result, boolean positions) {
        var json = new JsonObject();
        var nodes = new JsonArray();
        var included = new HashSet<String>();
        String mfId = mainframe.getMainframeId().toString();
        var relaysById = new HashMap<String, CloudRelayBlockEntity>();
        for (var relay : CloudNetwork.relays()) relaysById.put(relay.relayId().toString(), relay);
        for (var node : result.nodes()) {
            boolean isRoot = node.id().equals(mfId);
            boolean reachable = result.reachable(node.id());
            // Unreachable relays are shown when they are near the network (e.g. a relay that ran out of power).
            if (!isRoot && !reachable && !nearNetwork(node, result)) continue;
            included.add(node.id());
            var n = new JsonObject();
            n.addProperty("id", node.id());
            n.addProperty("type", node.type().id);
            String status;
            if (isRoot) {
                n.addProperty("name", mainframe.getDisplayName());
                status = "no_power".equals(mainframe.reason()) ? "no_power" : mainframe.isOnline() && mainframe.isEnabled() ? "online" : "offline";
                var power = new JsonObject();
                power.addProperty("stored", mainframe.energy().energy());
                power.addProperty("capacity", mainframe.energy().capacity());
                power.addProperty("usage", mainframe.energyUsage());
                power.addProperty("ok", mainframe.hasPower());
                n.add("power", power);
            } else {
                var relay = relaysById.get(node.id());
                n.addProperty("name", relay != null ? relay.getBlockState().getBlock().getName().getString() : node.type().id);
                status = !node.available() ? "no_power" : reachable ? "online" : "offline";
                if (relay != null) {
                    var power = new JsonObject();
                    power.addProperty("stored", relay.energy().energy());
                    power.addProperty("capacity", relay.energy().capacity());
                    power.addProperty("usage", relay.type().usage());
                    power.addProperty("ok", relay.hasPower());
                    n.add("power", power);
                }
            }
            n.addProperty("status", status);
            lastSeen(n, node.id(), status);
            var path = result.nodePaths().get(node.id());
            if (path != null && path.size() > 1) {
                n.addProperty("parent", path.get(path.size() - 2));
                n.addProperty("hops", path.size() - 1);
            }
            if (positions) n.add("pos", pos(node.x(), node.y(), node.z()));
            n.addProperty("dim", node.dim());
            n.addProperty("range", (int) node.nodeRange());
            n.addProperty("deviceRange", (int) node.deviceRange());
            nodes.add(n);
        }
        // Devices (loaded ones from the result, unloaded ones from the mainframe's link list).
        var loaded = new HashSet<String>();
        for (var device : result.devices()) {
            loaded.add(device.id());
            var route = result.route(device.id());
            var n = new JsonObject();
            n.addProperty("id", device.id());
            var section = CloudNetwork.section(UUID.fromString(device.id()));
            String type = deviceTypeId(device, section);
            LAST_TYPE.put(device.id(), type);
            n.addProperty("type", type);
            n.addProperty("name", section != null ? section.getTitle() : "");
            String status = route == null || !route.connected() ? "no_route" : route.legacyOutOfRange() ? "legacy_out_of_range"
                : mainframe.isOnline() ? "online" : "offline";
            n.addProperty("status", status);
            n.addProperty("uplinkId", device.id());
            lastSeen(n, device.id(), status);
            if (route != null && route.legacyOutOfRange()) {
                var flags = new JsonArray();
                flags.add("legacy_out_of_range");
                n.add("flags", flags);
            }
            if (positions) n.add("pos", pos(device.x(), device.y(), device.z()));
            n.addProperty("dim", device.dim());
            n.addProperty("range", (int) device.range());
            if (route != null && route.connected()) {
                n.addProperty("hops", route.hops());
                n.addProperty("parent", route.path().getLast());
            }
            nodes.add(n);
        }
        for (var e : mainframe.getLinkedUplinks().entrySet()) {
            String id = e.getKey().toString();
            if (loaded.contains(id)) continue;
            var n = new JsonObject();
            n.addProperty("id", id);
            n.addProperty("type", LAST_TYPE.getOrDefault(id, NetworkGraph.DeviceType.UPLINK.id));
            n.addProperty("name", "");
            n.addProperty("status", "unloaded");
            n.addProperty("uplinkId", id);
            lastSeen(n, id, "unloaded");
            var p = e.getValue().pos();
            if (positions) n.add("pos", pos(p.getX() + .5, p.getY() + .5, p.getZ() + .5));
            n.addProperty("dim", e.getValue().dimension().identifier().toString());
            nodes.add(n);
        }
        json.add("nodes", nodes);
        var links = new JsonArray();
        for (var link : result.links()) {
            if (!(included.contains(link.a()) || loaded.contains(link.a())) || !(included.contains(link.b()) || loaded.contains(link.b()))) continue;
            var l = new JsonObject();
            l.addProperty("a", link.a());
            l.addProperty("b", link.b());
            l.addProperty("signal", Math.round(link.signal() * 100) / 100.0);
            if (positions && link.distance() >= 0) l.addProperty("dist", Math.round(link.distance() * 10) / 10.0);
            l.addProperty("kind", link.kind() == NetworkGraph.LinkKind.ENDER ? "ender" : "radio");
            if (link.weak()) l.addProperty("weak", true);
            if (link.legacy()) l.addProperty("legacy", true);
            links.add(l);
        }
        json.add("links", links);
        // Routes: node id -> ids from the mainframe to that node, both ends included.
        var routes = new JsonObject();
        for (var e : result.nodePaths().entrySet()) {
            if (!included.contains(e.getKey())) continue;
            var path = new JsonArray();
            for (var hop : e.getValue()) path.add(hop);
            routes.add(e.getKey(), path);
        }
        for (var e : result.routes().entrySet()) {
            if (!e.getValue().connected()) continue;
            var path = new JsonArray();
            for (var hop : e.getValue().path()) path.add(hop);
            path.add(e.getKey());
            routes.add(e.getKey(), path);
        }
        json.add("routes", routes);
        return json;
    }

    private static String deviceTypeId(NetworkGraph.Device device, @Nullable DashboardSection section) {
        if (device.type() != NetworkGraph.DeviceType.BRIDGE) return device.type().id;
        return section instanceof BridgeSection bridge && "rs".equals(bridge.host().bridgeCore().kind()) ? "rs_bridge" : "me_bridge";
    }

    /** Remembers when a node was online; adds {@code lastSeen} (ms) to nodes that are not online. */
    private static void lastSeen(JsonObject node, String id, String status) {
        long now = System.currentTimeMillis();
        if (status.equals("online")) {
            LAST_ONLINE.put(id, now);
            return;
        }
        var last = LAST_ONLINE.get(id);
        if (last != null) node.addProperty("lastSeen", last);
    }

    private static boolean nearNetwork(NetworkGraph.Node node, NetworkGraph.Result result) {
        for (var other : result.nodes()) {
            if (!result.reachable(other.id()) || !other.dim().equals(node.dim())) continue;
            double d = Math.sqrt(Math.pow(other.x() - node.x(), 2) + Math.pow(other.y() - node.y(), 2) + Math.pow(other.z() - node.z(), 2));
            if (d <= Math.max(other.nodeRange(), node.nodeRange())) return true;
        }
        return false;
    }

    private static JsonObject pos(double x, double y, double z) {
        var json = new JsonObject();
        json.addProperty("x", (int) Math.floor(x));
        json.addProperty("y", (int) Math.floor(y));
        json.addProperty("z", (int) Math.floor(z));
        return json;
    }
}
