package ai.ezfox.cccloud.network;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.bridge.BridgeSection;
import ai.ezfox.cccloud.cable.CloudWired;
import ai.ezfox.cccloud.cable.EnderFiberLinkBlockEntity;
import ai.ezfox.cccloud.cable.FiberNetworks;
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
import net.minecraft.server.level.ServerLevel;
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

    /** Set from any thread (CC wired network changes); read on the server thread. */
    private static volatile boolean dirty = true;
    /** Fiber blocks or cloud elements changed: flood-fill the fiber networks again. */
    private static volatile boolean fiberDirty = true;
    private static final Map<UUID, NetworkGraph.Result> RESULTS = new HashMap<>();
    private static final Map<UUID, NetworkGraph.Route> ROUTES = new HashMap<>();
    /** Device counts per mainframe for the power formula (avoids iterating devices every tick). */
    private static final Map<UUID, NetworkPower.Counts> COUNTS = new HashMap<>();
    /** Devices cut off because their mainframe is over its device capacity. */
    private static final Set<UUID> OVER_CAPACITY = new HashSet<>();
    /** Element id → fiber network ids (last {@link FiberNetworks} pass). */
    private static Map<String, Set<String>> fibers = Map.of();
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

    /**
     * Only this mainframe's network changed (one of its devices loaded, unloaded or changed): recomputed on the next tick
     * without touching the other networks (big servers: a chunk load must not recompute every mainframe; docs/SPEC.md
     * "Scaling"). Null = unknown, everything.
     */
    public static void markDirty(@Nullable UUID mainframeId) {
        if (mainframeId == null) dirty = true;
        else DIRTY_MAINFRAMES.add(mainframeId);
    }

    private static final Set<UUID> DIRTY_MAINFRAMES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Set<UUID>> OVER_BY_MAINFRAME = new HashMap<>();

    /** Fiber cables, junctions, ender fiber links or cloud elements were placed, removed, loaded or unloaded. */
    public static void markFiberDirty() {
        fiberDirty = true;
        dirty = true;
    }

    public static void clear() {
        RESULTS.clear();
        SCREEN.clear();
        PUBLISHED.clear();
        ROUTES.clear();
        COUNTS.clear();
        SERVING.clear();
        LAST_ONLINE.clear();
        LAST_TYPE.clear();
        OVER_CAPACITY.clear();
        fibers = Map.of();
        CloudWired.clear();
        ai.ezfox.cccloud.uplink.WiredMachines.clear();
        EnderFiberLinkBlockEntity.clearLoaded();
        dirty = true;
        fiberDirty = true;
    }

    /**
     * Called every server tick; recomputes only what is dirty. The 10 s safety net is spread over the ticks (each
     * mainframe at its own tick) instead of recomputing every network at once; the fiber flood-fill runs every 10 s and
     * recomputes everything only when the fiber networks changed.
     */
    public static void tick(MinecraftServer server) {
        CloudWired.tick();
        int t = server.getTickCount();
        try {
            if (fiberDirty || t % SAFETY_INTERVAL == 0) {
                fiberDirty = false;
                var before = fibers;
                fibers = FiberNetworks.compute(fiberElements(), EnderFiberLinkBlockEntity.loaded());
                if (!fibers.equals(before)) dirty = true;
            }
            if (dirty) {
                dirty = false;
                DIRTY_MAINFRAMES.clear();
                recompute(null);
                return;
            }
            Set<UUID> due = null;
            if (!DIRTY_MAINFRAMES.isEmpty()) {
                due = new HashSet<>(DIRTY_MAINFRAMES);
                DIRTY_MAINFRAMES.removeAll(due);
            }
            for (var mainframe : CloudNetwork.mainframes()) {
                if (Math.floorMod(mainframe.getMainframeId().hashCode() + t, SAFETY_INTERVAL) != 0) continue;
                if (due == null) due = new HashSet<>();
                due.add(mainframe.getMainframeId());
            }
            if (due != null) recompute(due);
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
        if (uplink.isProbe()) return NetworkGraph.DeviceType.PROBE;
        return uplink.isAdvanced() ? NetworkGraph.DeviceType.ADVANCED_UPLINK : NetworkGraph.DeviceType.UPLINK;
    }

    public static int deviceRange(NetworkGraph.DeviceType type) {
        return CloudConfig.get(switch (type) {
            case UPLINK -> CloudConfig.RANGE_UPLINK;
            case ADVANCED_UPLINK, PROBE -> CloudConfig.RANGE_ADVANCED_UPLINK;
            case BRIDGE -> CloudConfig.RANGE_BRIDGE;
            case REDSTONE -> CloudConfig.RANGE_REDSTONE_DEVICE;
        });
    }

    private static NetworkGraph.Node mainframeNode(CloudMainframeBlockEntity m) {
        var p = m.getBlockPos();
        var effects = m.effects();
        // Range upgrade multiplies the mainframe's node range; the Quantum Core makes it an Ender node of its own group.
        double range = CloudConfig.get(CloudConfig.RANGE_MAINFRAME) * effects.rangeFactor();
        String id = m.getMainframeId().toString();
        return new NetworkGraph.Node(id, NetworkGraph.NodeType.MAINFRAME, dim(m.getLevel()), p.getX() + .5, p.getY() + 1, p.getZ() + .5,
            range, range, m.isOnline(), effects.enderNode() ? id : null, m.wired().cableBuses(), fibers(id));
    }

    private static NetworkGraph.Node relayNode(CloudRelayBlockEntity r, UUID mainframeId) {
        var p = r.getBlockPos();
        var type = r.type();
        String group = type == RelayType.ENDER && mainframeId.equals(r.enderGroup()) ? mainframeId.toString() : null;
        String id = r.relayId().toString();
        return new NetworkGraph.Node(id, type.nodeType, dim(r.getLevel()), p.getX() + .5, p.getY() + .5, p.getZ() + .5,
            type.nodeRange(), type.deviceRange(), r.hasPower() && !r.isRemoved(), group, r.wired().cableBuses(), fibers(id));
    }

    /** Fiber network ids of an element (from the last fiber pass). */
    public static Set<String> fibers(String elementId) {
        return fibers.getOrDefault(elementId, Set.of());
    }

    /** Every loaded cloud element with its blocks, for the fiber flood-fill. */
    private static List<FiberNetworks.Element> fiberElements() {
        var list = new ArrayList<FiberNetworks.Element>();
        for (var m : CloudNetwork.mainframes()) {
            if (m.isRemoved() || !(m.getLevel() instanceof ServerLevel level)) continue;
            list.add(new FiberNetworks.Element(m.getMainframeId().toString(), level, List.of(m.getBlockPos(), m.getBlockPos().above())));
        }
        for (var r : CloudNetwork.relays()) {
            if (r.isRemoved() || !(r.getLevel() instanceof ServerLevel level)) continue;
            list.add(new FiberNetworks.Element(r.relayId().toString(), level, List.of(r.getBlockPos())));
        }
        for (var u : CloudNetwork.uplinks()) {
            if (u.isRemoved() || !(u.getLevel() instanceof ServerLevel level)) continue;
            list.add(new FiberNetworks.Element(u.getUplinkId().toString(), level, List.of(u.getBlockPos())));
        }
        for (var section : CloudNetwork.bridgeSections()) {
            if (!(section instanceof BridgeSection bridge)) continue;
            var be = bridge.host().asBlockEntity();
            if (be.isRemoved() || !(be.getLevel() instanceof ServerLevel level)) continue;
            list.add(new FiberNetworks.Element(bridge.sectionId().toString(), level, List.of(be.getBlockPos())));
        }
        return list;
    }

    private static Set<String> cables(@Nullable CloudWired wired) {
        return wired == null ? Set.of() : wired.cableBuses();
    }

    private static List<NetworkGraph.Node> nodes(CloudMainframeBlockEntity m) {
        var nodes = new ArrayList<NetworkGraph.Node>();
        nodes.add(mainframeNode(m));
        for (var relay : CloudNetwork.relays()) {
            if (relay.getLevel() != null && relay.servesForTest(m.getMainframeId())) nodes.add(relayNode(relay, m.getMainframeId()));
        }
        return nodes;
    }

    /** Everything (only = null) or only these mainframes' networks; the others keep their results. */
    private static void recompute(@Nullable Set<UUID> only) {
        recomputations++;
        if (only == null) {
            RESULTS.clear();
            ROUTES.clear();
            COUNTS.clear();
            OVER_CAPACITY.clear();
            OVER_BY_MAINFRAME.clear();
        } else {
            for (var id : only) {
                var old = RESULTS.remove(id);
                // routes of devices that meanwhile belong to another network are not this one's to remove
                if (old != null) for (var d : old.devices()) ROUTES.remove(UUID.fromString(d.id()), old.route(d.id()));
                COUNTS.remove(id);
                var over = OVER_BY_MAINFRAME.remove(id);
                if (over != null) OVER_CAPACITY.removeAll(over);
            }
        }
        boolean grace = CloudConfig.get(CloudConfig.LEGACY_RANGE_GRACE);
        var devicesByMainframe = new HashMap<UUID, List<NetworkGraph.Device>>();
        for (var uplink : CloudNetwork.uplinks()) {
            var link = uplink.getLink();
            if (link == null || uplink.getLevel() == null || uplink.isRemoved() || (only != null && !only.contains(link.id()))) continue;
            var p = uplink.getBlockPos();
            var type = deviceType(uplink);
            String id = uplink.getUplinkId().toString();
            devicesByMainframe.computeIfAbsent(link.id(), k -> new ArrayList<>()).add(new NetworkGraph.Device(id, type,
                dim(uplink.getLevel()), p.getX() + .5, p.getY() + .5, p.getZ() + .5, deviceRange(type), uplink.isLegacyLink(),
                uplink.wired().cableBuses(), fibers(id)));
        }
        for (var section : CloudNetwork.bridgeSections()) {
            if (!(section instanceof BridgeSection bridge) || bridge.getLink() == null) continue;
            if (only != null && !only.contains(bridge.getLink().id())) continue;
            var be = bridge.host().asBlockEntity();
            if (be.getLevel() == null || be.isRemoved()) continue;
            var p = be.getBlockPos();
            String id = bridge.sectionId().toString();
            devicesByMainframe.computeIfAbsent(bridge.getLink().id(), k -> new ArrayList<>()).add(new NetworkGraph.Device(id,
                NetworkGraph.DeviceType.BRIDGE, dim(be.getLevel()), p.getX() + .5, p.getY() + .5, p.getZ() + .5,
                deviceRange(NetworkGraph.DeviceType.BRIDGE), bridge.isLegacyLink(), cables(bridge.host().cloudWired()), fibers(id)));
        }
        // Cloud Redstone devices: radio only (no cables), a fraction of a device in capacity and FE.
        for (var device : ai.ezfox.cccloud.rsdevice.RsDevices.loadedDevices()) {
            var link = device.getLink();
            if (link == null || device.getLevel() == null || device.isRemoved() || (only != null && !only.contains(link.id()))) continue;
            var p = device.getBlockPos();
            devicesByMainframe.computeIfAbsent(link.id(), k -> new ArrayList<>()).add(new NetworkGraph.Device(device.deviceId().toString(),
                NetworkGraph.DeviceType.REDSTONE, dim(device.getLevel()), p.getX() + .5, p.getY() + .5, p.getZ() + .5,
                deviceRange(NetworkGraph.DeviceType.REDSTONE), false));
        }
        for (var mainframe : CloudNetwork.mainframes()) {
            if (mainframe.getLevel() == null || mainframe.isRemoved()) continue;
            var id = mainframe.getMainframeId();
            if (only != null && !only.contains(id)) continue;
            var devices = devicesByMainframe.getOrDefault(id, List.of());
            var computed = NetworkGraph.compute(id.toString(), nodes(mainframe), devices, grace);
            // v0.7 capacity: devices beyond the mainframe's limit go over_capacity (most hops / most recent first).
            var limit = mainframe.effects().devices();
            var over = limit == ai.ezfox.cccloud.upgrade.UpgradeEffects.UNLIMITED ? Set.<String>of()
                : ai.ezfox.cccloud.network.graph.Capacity.overCapacity(computed, id.toString(), limit, CloudConfig.get(CloudConfig.FIBER_CAPACITY_WEIGHT),
                    CloudConfig.get(CloudConfig.REDSTONE_CAPACITY_WEIGHT), CloudConfig.get(CloudConfig.PROBE_CAPACITY_UNITS), linkOrder(mainframe));
            var overIds = new HashSet<UUID>();
            for (var o : over) overIds.add(UUID.fromString(o));
            OVER_CAPACITY.addAll(overIds);
            if (!overIds.isEmpty()) OVER_BY_MAINFRAME.put(id, overIds);
            var result = ai.ezfox.cccloud.network.graph.Capacity.without(computed, over);
            RESULTS.put(id, result);
            for (var device : devices) ROUTES.put(UUID.fromString(device.id()), result.route(device.id()));
            COUNTS.put(id, NetworkPower.counts(result, CloudConfig.get(CloudConfig.FIBER_CAPACITY_WEIGHT), CloudConfig.get(CloudConfig.REDSTONE_CAPACITY_WEIGHT),
                CloudConfig.get(CloudConfig.PROBE_CAPACITY_UNITS)));
            // The map JSON is the expensive part (two forms, ~1 KB per device): built only when this network changed, or
            // at least once a minute for the slow parts (power numbers, names) - docs/SPEC.md "Scaling".
            var key = new PublishKey(result, Set.copyOf(over), mainframe.isOnline(), mainframe.isEnabled(), mainframe.reason(),
                Set.copyOf(mainframe.getLinkedUplinks().keySet()), namesHash(id, mainframe.getLinkedUplinks().keySet()), probeTargets(result));
            var last = PUBLISHED.get(id);
            long now = System.currentTimeMillis();
            if (last != null && last.key().equals(key) && now - last.at() < REPUBLISH_MS) {
                // unchanged: only remember that its online parts are still online (lastSeen on the map)
                if (mainframe.isOnline()) {
                    for (var node : result.nodes()) if (node.available() && result.reachable(node.id())) LAST_ONLINE.put(node.id(), now);
                    for (var e : result.routes().entrySet()) {
                        if (e.getValue().connected() && !e.getValue().legacyOutOfRange() && !over.contains(e.getKey())) LAST_ONLINE.put(e.getKey(), now);
                    }
                }
                continue;
            }
            PUBLISHED.put(id, new Published(key, now));
            WebHub.INSTANCE.publishNetwork(id, toJson(mainframe, result, true), toJson(mainframe, result, false));
        }
        // Relays serving any network (from every network's last result)
        SERVING.clear();
        for (var result : RESULTS.values()) {
            for (var node : result.nodes()) {
                if (node.type() != NetworkGraph.NodeType.MAINFRAME && result.reachable(node.id())) SERVING.add(UUID.fromString(node.id()));
            }
        }
        // Routes may have changed: wireless redstone follows on the same tick.
        ai.ezfox.cccloud.redstone.RedstoneService.markDirty();
    }

    // ---- queries -----------------------------------------------------------------------------------------------------

    /** The device's route, or null while the network has not been computed yet. */
    public static NetworkGraph.@Nullable Route route(UUID deviceId) {
        return ROUTES.get(deviceId);
    }

    /** The device is linked and reachable, but its mainframe is over its device capacity. */
    public static boolean overCapacity(UUID deviceId) {
        return OVER_CAPACITY.contains(deviceId);
    }

    private static Map<String, Integer> linkOrder(CloudMainframeBlockEntity mainframe) {
        var order = new HashMap<String, Integer>();
        int i = 0;
        for (var id : mainframe.getLinkedUplinks().keySet()) order.put(id.toString(), i++);
        return order;
    }

    /** Capacity in use: relays on device routes + routed devices (fiber weighted). */
    public static double capacityUsed(UUID mainframeId) {
        var result = RESULTS.get(mainframeId);
        int relays = result == null ? 0 : ai.ezfox.cccloud.network.graph.Capacity.relays(result, mainframeId.toString());
        return relays + counts(mainframeId).capacityUsed();
    }

    public static boolean isServing(UUID relayId) {
        return SERVING.contains(relayId);
    }

    public static NetworkPower.Counts counts(UUID mainframeId) {
        return COUNTS.getOrDefault(mainframeId, NetworkPower.Counts.EMPTY);
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
        return checkLink(mainframe, level, pos, type, null, null);
    }

    /**
     * Linking check for a real device: besides radio range, a cable (CC wired network or fiber) to a node of the network
     * also counts.
     *
     * @param deviceId the device's network id (for its fiber networks), {@code wired} its CC wired element
     */
    public static @Nullable Component checkLink(CloudMainframeBlockEntity mainframe, Level level, BlockPos pos, NetworkGraph.DeviceType type,
                                                @Nullable String deviceId, @Nullable CloudWired wired) {
        if (fiberDirty) {
            fiberDirty = false;
            fibers = FiberNetworks.compute(fiberElements(), EnderFiberLinkBlockEntity.loaded());
        }
        // Capacity must count links made earlier in this tick (linking is a rare player action, so this is cheap).
        if (dirty) {
            dirty = false;
            DIRTY_MAINFRAMES.clear();
            recompute(null);
        } else if (DIRTY_MAINFRAMES.remove(mainframe.getMainframeId())) {
            recompute(Set.of(mainframe.getMainframeId()));
        }
        // Linking is a geometric check: nodes count even while they (or the mainframe) have no power right now.
        var nodes = new ArrayList<NetworkGraph.Node>();
        for (var n : nodes(mainframe)) {
            nodes.add(n.withAvailable(true));
        }
        var device = new NetworkGraph.Device("probe", type, dim(level), pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, deviceRange(type), false,
            cables(wired), deviceId == null ? Set.of() : fibers(deviceId));
        var result = NetworkGraph.compute(mainframe.getMainframeId().toString(), nodes, List.of(device), false);
        var route = result.route("probe");
        if (route != null && route.connected()) {
            // v0.7 capacity: a new link must fit (re-linking a device that is already counted is fine).
            int limit = mainframe.effects().devices();
            boolean counted = deviceId != null && ROUTES.containsKey(UUID.fromString(deviceId)) && route(UUID.fromString(deviceId)) != null
                && route(UUID.fromString(deviceId)).connected();
            double weight = ai.ezfox.cccloud.network.graph.Capacity.weight(device, route, CloudConfig.get(CloudConfig.FIBER_CAPACITY_WEIGHT),
                CloudConfig.get(CloudConfig.REDSTONE_CAPACITY_WEIGHT), CloudConfig.get(CloudConfig.PROBE_CAPACITY_UNITS));
            if (limit != ai.ezfox.cccloud.upgrade.UpgradeEffects.UNLIMITED && !counted && capacityUsed(mainframe.getMainframeId()) + weight > limit + 1e-9) {
                return Component.translatable("cccloud.network.capacity_full", limit);
            }
            return null;
        }
        var nearest = NetworkGraph.nearestNode(result, dim(level), pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (nearest == null) return Component.translatable("cccloud.network.no_node_here");
        var node = nearest.getKey();
        return Component.translatable("cccloud.network.out_of_range", Component.translatable("cccloud.network.node." + node.type().id),
            (int) Math.round(nearest.getValue()), (int) Math.max(type == null ? 0 : deviceRange(type), node.deviceRange()),
            (int) Math.floor(node.x()) + " " + (int) Math.floor(node.y()) + " " + (int) Math.floor(node.z()));
    }

    /** Power breakdown of a mainframe's network ({@link NetworkPower}); clients are counted live. */
    public static Map<String, Long> power(UUID mainframeId, int clients) {
        var effects = ai.ezfox.cccloud.upgrade.Upgrades.effects(mainframeId);
        return NetworkPower.breakdown(rates(), counts(mainframeId), clients, CloudConfig.get(CloudConfig.FIBER_POWER_FACTOR), effects.feFactor(),
            ai.ezfox.cccloud.upgrade.ChunkAnchor.cost(ai.ezfox.cccloud.upgrade.ChunkAnchor.chunks(mainframeId)));
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
                case "redstone" -> counts.redstone();
                case "probes" -> counts.probes();
                case "clients" -> Math.max(0, clients);
                case "hops" -> counts.extraHops();
                default -> null;
            };
            if (count != null) item.addProperty("count", count);
            int fiber = switch (e.getKey()) {
                case "uplinks" -> counts.fiberUplinks();
                case "advanced_uplinks" -> counts.fiberAdvancedUplinks();
                case "bridges" -> counts.fiberBridges();
                case "probes" -> counts.fiberProbes();
                default -> 0;
            };
            if (fiber > 0) item.addProperty("fiber", fiber);
            item.addProperty("fe", e.getValue());
            array.add(item);
        }
        return array;
    }

    public static NetworkPower.Rates rates() {
        return new NetworkPower.Rates(CloudConfig.get(CloudConfig.MAINFRAME_BASE_FE), CloudConfig.get(CloudConfig.MAINFRAME_PER_UPLINK_FE),
            CloudConfig.get(CloudConfig.MAINFRAME_PER_ADVANCED_UPLINK_FE), CloudConfig.get(CloudConfig.MAINFRAME_PER_BRIDGE_FE),
            CloudConfig.get(CloudConfig.MAINFRAME_PER_CLIENT_FE), CloudConfig.get(CloudConfig.MAINFRAME_PER_HOP_FE),
            CloudConfig.get(CloudConfig.MAINFRAME_PER_REDSTONE_DEVICE_FE), CloudConfig.get(CloudConfig.MAINFRAME_PER_PROBE_FE));
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
                // Device capacity in use (fiber members weigh fiberCapacityWeight) and how many devices are cabled.
                var counts = counts(mainframe.getMainframeId());
                var capacity = new JsonObject();
                capacity.addProperty("devices", counts.devices());
                capacity.addProperty("used", Math.round(capacityUsed(mainframe.getMainframeId()) * 100) / 100.0);
                int limit = mainframe.effects().devices();
                if (limit != ai.ezfox.cccloud.upgrade.UpgradeEffects.UNLIMITED) capacity.addProperty("limit", limit);
                else capacity.add("limit", com.google.gson.JsonNull.INSTANCE);
                capacity.addProperty("over", OVER_CAPACITY.isEmpty() ? 0 : (int) result.devices().stream()
                    .filter(d -> OVER_CAPACITY.contains(UUID.fromString(d.id()))).count());
                capacity.addProperty("wired", counts.wired());
                capacity.addProperty("fiber", counts.fiber());
                n.add("capacity", capacity);
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
            String type = deviceTypeId(mainframe.getMainframeId(), device, section);
            LAST_TYPE.put(device.id(), type);
            n.addProperty("type", type);
            n.addProperty("name", deviceName(mainframe.getMainframeId(), device.id(), section));
            redstoneFields(n, mainframe.getMainframeId(), device.id());
            if (positions) probeTarget(n, section);
            String status = OVER_CAPACITY.contains(UUID.fromString(device.id())) ? "over_capacity"
                : route == null || !route.connected() ? "no_route" : route.legacyOutOfRange() ? "legacy_out_of_range"
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
                if (route.wired()) n.addProperty("via", route.fiber() ? "fiber" : "cable");
            }
            nodes.add(n);
        }
        for (var e : mainframe.getLinkedUplinks().entrySet()) {
            String id = e.getKey().toString();
            if (loaded.contains(id)) continue;
            var n = new JsonObject();
            n.addProperty("id", id);
            var rsType = ai.ezfox.cccloud.rsdevice.RsDevices.typeId(mainframe.getMainframeId(), e.getKey());
            n.addProperty("type", rsType != null ? rsType : LAST_TYPE.getOrDefault(id, NetworkGraph.DeviceType.UPLINK.id));
            n.addProperty("name", deviceName(mainframe.getMainframeId(), id, null));
            redstoneFields(n, mainframe.getMainframeId(), id);
            n.addProperty("status", "unloaded");
            n.addProperty("uplinkId", id);
            lastSeen(n, id, "unloaded");
            var p = e.getValue().pos();
            if (positions) n.add("pos", pos(p.getX() + .5, p.getY() + .5, p.getZ() + .5));
            n.addProperty("dim", e.getValue().dimension().identifier().toString());
            nodes.add(n);
        }
        json.add("nodes", nodes);
        // Big networks: a device's links to nodes it does not use (shown faintly on the map) are left out; they were
        // most of a 500-device map (5 links per device with 4 relays; docs/SPEC.md "Scaling").
        boolean trim = result.devices().size() > TRIM_LINKS_ABOVE;
        var usedDeviceLinks = new HashSet<String>();
        if (trim) {
            for (var e : result.routes().entrySet()) {
                if (e.getValue().connected()) usedDeviceLinks.add(e.getValue().path().getLast() + ">" + e.getKey());
            }
        }
        int trimmed = 0;
        var links = new JsonArray();
        for (var link : result.links()) {
            if (!(included.contains(link.a()) || loaded.contains(link.a())) || !(included.contains(link.b()) || loaded.contains(link.b()))) continue;
            if (trim && loaded.contains(link.b()) && !link.legacy() && !usedDeviceLinks.contains(link.a() + ">" + link.b())) {
                trimmed++;
                continue;
            }
            var l = new JsonObject();
            l.addProperty("a", link.a());
            l.addProperty("b", link.b());
            l.addProperty("signal", Math.round(link.signal() * 100) / 100.0);
            boolean cable = link.kind().cable();
            if (positions && link.distance() >= 0 && !cable) l.addProperty("dist", Math.round(link.distance() * 10) / 10.0);
            // v0.7 protocol: cables are kind "wired" (CC cable and fiber); "medium" tells them apart.
            l.addProperty("kind", cable ? "wired" : link.kind() == NetworkGraph.LinkKind.ENDER ? "ender" : "radio");
            if (cable) l.addProperty("medium", link.kind() == NetworkGraph.LinkKind.FIBER ? "fiber" : "cc");
            if (link.weak()) l.addProperty("weak", true);
            if (link.legacy()) l.addProperty("legacy", true);
            links.add(l);
        }
        json.add("links", links);
        if (trimmed > 0) json.addProperty("unusedLinks", trimmed);
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

    /** Above this many devices the map leaves out the links a device does not use (count in {@code unusedLinks}). */
    public static final int TRIM_LINKS_ABOVE = 200;

    /** Device nodes the in-game mainframe screen gets at most (docs/SPEC.md "Scaling"); the rest is counted in {@code more}. */
    public static final int SCREEN_MAX_DEVICES = 600;
    /** Names on the screen are cut to this (the tree row cannot show more). */
    static final int SCREEN_NAME = 32;

    /** A compact network for the in-game screen, its content hash, and the recomputation it was built from. */
    public record ScreenNetwork(JsonObject json, long version, long builtAt) {
    }

    private static final Map<UUID, ScreenNetwork> SCREEN = new HashMap<>();

    /** What a mainframe's published map was built from; an equal key within {@link #REPUBLISH_MS} skips the rebuild. */
    private record PublishKey(NetworkGraph.Result result, Set<String> over, boolean online, boolean enabled, @Nullable String reason, Set<UUID> linked,
                              int names, List<String> probes) {
    }

    /** Names and redstone groups of a mainframe's devices as shown on the map (a rename from the map must show at once). */
    private static int namesHash(UUID mainframeId, Set<UUID> linked) {
        int h = 0;
        for (var id : linked) {
            String key = id.toString();
            var group = ai.ezfox.cccloud.rsdevice.RsDevices.group(mainframeId, id);
            h += key.hashCode() * 31 + deviceName(mainframeId, key, null).hashCode() * 17 + (group == null ? 0 : group.hashCode());
        }
        return h;
    }

    private record Published(PublishKey key, long at) {
    }

    private static final Map<UUID, Published> PUBLISHED = new HashMap<>();
    static final long REPUBLISH_MS = 60_000;

    /**
     * The network as the in-game mainframe screen shows it (tree list and mini map): short node ids ({@code n0} is the
     * mainframe), only name, type, status, parent, x/z and the dimension when it differs from the mainframe's, and only
     * the links of the tree. Devices with problems come first; at most {@link #SCREEN_MAX_DEVICES} device nodes, the
     * rest is counted in {@code more}. Rebuilt once per network recomputation and shared by every open screen;
     * {@code version} changes only when the content does, so open screens get it again only then.
     */
    public static @Nullable ScreenNetwork screenNetwork(CloudMainframeBlockEntity mainframe) {
        var result = RESULTS.get(mainframe.getMainframeId());
        if (result == null) return null;
        var cached = SCREEN.get(mainframe.getMainframeId());
        if (cached != null && cached.builtAt() == recomputations) return cached;
        var full = toJson(mainframe, result, true);
        var json = compactForScreen(full, NetworkService.dim(mainframe.getLevel()), SCREEN_MAX_DEVICES);
        var built = new ScreenNetwork(json, ai.ezfox.cccloud.uplink.Json.GSON.toJson(json).hashCode() & 0xffffffffL, recomputations);
        SCREEN.put(mainframe.getMainframeId(), built);
        return built;
    }

    /** Pure: the compact screen form of a {@link #toJson} network (unit tested). */
    public static JsonObject compactForScreen(JsonObject full, String mainframeDim, int maxDevices) {
        var nodes = full.getAsJsonArray("nodes");
        var ordered = new ArrayList<JsonObject>();
        var devices = new ArrayList<JsonObject>();
        for (var e : nodes) {
            var n = e.getAsJsonObject();
            boolean device = n.has("uplinkId");
            (device ? devices : ordered).add(n);
        }
        // problems first (they are what a player opens the screen for), then by name
        devices.sort(java.util.Comparator.comparingInt((JsonObject n) -> "online".equals(str(n, "status")) || "unloaded".equals(str(n, "status")) ? 1 : 0)
            .thenComparing(n -> str(n, "name")));
        int more = Math.max(0, devices.size() - maxDevices);
        ordered.addAll(devices.subList(0, Math.min(devices.size(), maxDevices)));
        var shortId = new HashMap<String, String>();
        for (int i = 0; i < ordered.size(); i++) shortId.put(str(ordered.get(i), "id"), "n" + i);
        var outNodes = new JsonArray();
        for (var n : ordered) {
            var o = new JsonObject();
            o.addProperty("id", shortId.get(str(n, "id")));
            o.addProperty("type", str(n, "type"));
            String name = str(n, "name");
            o.addProperty("name", name.length() > SCREEN_NAME ? name.substring(0, SCREEN_NAME) : name);
            o.addProperty("status", str(n, "status"));
            String parent = n.has("parent") ? shortId.get(str(n, "parent")) : null;
            if (parent != null) o.addProperty("parent", parent);
            if (n.has("flags")) o.add("flags", n.get("flags"));
            if (n.get("pos") instanceof JsonObject p) {
                var pos = new JsonObject();
                pos.add("x", p.get("x"));
                pos.add("z", p.get("z"));
                o.add("pos", pos);
            }
            // Cloud Probe: the machine it reads (x/z), drawn as a line on the mini map
            if (n.get("target") instanceof JsonObject t) {
                var target = new JsonObject();
                target.add("x", t.get("x"));
                target.add("z", t.get("z"));
                o.add("target", target);
            }
            String dim = str(n, "dim");
            if (!dim.isEmpty() && !dim.equals(mainframeDim)) o.addProperty("dim", dim);
            outNodes.add(o);
        }
        // only the links of the tree: each node's link to its parent
        var parentOf = new HashMap<String, String>();
        for (var n : ordered) if (n.has("parent")) parentOf.put(str(n, "id"), str(n, "parent"));
        var outLinks = new JsonArray();
        for (var e : full.getAsJsonArray("links")) {
            var l = e.getAsJsonObject();
            String a = str(l, "a"), b = str(l, "b");
            boolean tree = a.equals(parentOf.get(b)) || b.equals(parentOf.get(a));
            if (!tree || !shortId.containsKey(a) || !shortId.containsKey(b)) continue;
            var o = new JsonObject();
            o.addProperty("a", shortId.get(a));
            o.addProperty("b", shortId.get(b));
            o.add("signal", l.get("signal"));
            o.add("kind", l.get("kind"));
            if (l.has("medium")) o.add("medium", l.get("medium"));
            outLinks.add(o);
        }
        var json = new JsonObject();
        json.addProperty("dim", mainframeDim);
        json.add("nodes", outNodes);
        json.add("links", outLinks);
        if (more > 0) json.addProperty("more", more);
        return json;
    }

    private static String str(JsonObject o, String key) {
        var e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static String deviceTypeId(UUID mainframeId, NetworkGraph.Device device, @Nullable DashboardSection section) {
        if (device.type() == NetworkGraph.DeviceType.REDSTONE) {
            var type = ai.ezfox.cccloud.rsdevice.RsDevices.typeId(mainframeId, UUID.fromString(device.id()));
            return type != null ? type : device.type().id;
        }
        if (device.type() != NetworkGraph.DeviceType.BRIDGE) return device.type().id;
        return section instanceof BridgeSection bridge && "rs".equals(bridge.host().bridgeCore().kind()) ? "rs_bridge" : "me_bridge";
    }

    /** A device's name on the map: its section title, or a redstone device's name. */
    public static String deviceName(UUID mainframeId, String deviceId, @Nullable DashboardSection section) {
        if (section == null) section = CloudNetwork.section(UUID.fromString(deviceId));
        if (section != null) return section.getTitle();
        var name = ai.ezfox.cccloud.rsdevice.RsDevices.name(mainframeId, UUID.fromString(deviceId));
        return name == null ? "" : name;
    }

    /**
     * Cloud Probes: the machine they read as {@code target} (block position, only with positions like {@code pos}; the
     * same check as every read, so a target out of range, unloaded or in a foreign claim is left out). Same dimension as
     * the probe.
     */
    private static void probeTarget(JsonObject node, @Nullable DashboardSection section) {
        var target = probeReadPos(section);
        if (target != null) node.add("target", pos(target.getX(), target.getY(), target.getZ()));
    }

    private static @Nullable BlockPos probeReadPos(@Nullable DashboardSection section) {
        return section instanceof CloudUplinkBlockEntity probe && probe.isProbe() && !probe.isRemoved() ? probe.readPos() : null;
    }

    /** What the probes of a network read right now (part of the publish key: a new target redraws the map). */
    private static List<String> probeTargets(NetworkGraph.Result result) {
        List<String> list = null;
        for (var device : result.devices()) {
            if (device.type() != NetworkGraph.DeviceType.PROBE) continue;
            var target = probeReadPos(CloudNetwork.section(UUID.fromString(device.id())));
            if (list == null) list = new ArrayList<>();
            list.add(device.id() + "@" + (target == null ? "" : target.asLong()));
        }
        return list == null ? List.of() : list;
    }

    /** Redstone devices: the card (group) they are on, so the dashboard can rename/regroup them from the map. */
    private static void redstoneFields(JsonObject node, UUID mainframeId, String deviceId) {
        var group = ai.ezfox.cccloud.rsdevice.RsDevices.group(mainframeId, UUID.fromString(deviceId));
        if (group == null) return;
        node.addProperty("group", group);
        node.addProperty("redstone", true);
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
