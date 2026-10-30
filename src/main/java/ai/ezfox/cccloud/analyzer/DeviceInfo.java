package ai.ezfox.cccloud.analyzer;

import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.bridge.BridgeHost;
import ai.ezfox.cccloud.ingame.InGameAccess;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.network.graph.NetworkGraph;
import ai.ezfox.cccloud.relay.CloudRelayBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Network facts about one cloud block for a player (analyzer right-click, Jade): status, signal, hops, route (type ids
 * from the mainframe to the device) and the route's positions. Only for networks where the player has at least viewer
 * role; relays report the first such network they serve. Server thread only.
 */
public record DeviceInfo(UUID mainframeId, String kind, String status, double signal, int hops, List<String> route, List<Vec3> routePositions,
                         long energy, long capacity) {

    public static @Nullable DeviceInfo of(ServerPlayer player, Level level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof CloudMainframeBlock) {
            var mainframe = CloudMainframeBlock.getBlockEntity(level, pos, state);
            if (mainframe == null || !visible(player, mainframe.getMainframeId())) return null;
            String status = "no_power".equals(mainframe.reason()) ? "no_power" : mainframe.isOnline() && mainframe.isEnabled() ? "online" : "offline";
            return new DeviceInfo(mainframe.getMainframeId(), "mainframe", status, 1, 0, List.of("mainframe"),
                List.of(mainframe.getBlockPos().getCenter()), mainframe.energy().energy(), mainframe.energy().capacity());
        }
        if (be instanceof CloudRelayBlockEntity relay) {
            for (var mainframe : List.copyOf(CloudNetwork.mainframes())) {
                var id = mainframe.getMainframeId();
                var result = NetworkService.result(id);
                if (result == null || !visible(player, id)) continue;
                var path = result.nodePaths().get(relay.relayId().toString());
                String status = !relay.hasPower() ? "no_power" : path != null ? "online" : "no_route";
                if (path == null && result.nodes().stream().noneMatch(n -> n.id().equals(relay.relayId().toString()))) continue;
                return new DeviceInfo(id, relay.type().nodeType.id, status, path == null ? 0 : AnalyzerGeometry.pathSignal(path, signals(result)),
                    path == null ? -1 : path.size() - 1, path == null ? List.of() : types(result, path), path == null ? List.of() : positions(result, path),
                    relay.energy().energy(), relay.energy().capacity());
            }
            return null;
        }
        UUID deviceId = null;
        UUID mainframeId = null;
        String kind = null;
        if (be instanceof CloudUplinkBlockEntity uplink && uplink.getLink() != null) {
            deviceId = uplink.getUplinkId();
            mainframeId = uplink.getLink().id();
            kind = uplink.isProbe() ? "probe" : uplink.isAdvanced() ? "advanced_uplink" : "uplink";
        } else if (be instanceof BridgeHost bridge && bridge.bridgeSection().getLink() != null) {
            deviceId = bridge.bridgeSection().sectionId();
            mainframeId = bridge.bridgeSection().getLink().id();
            kind = "bridge";
        } else if (be instanceof ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity device && device.getLink() != null) {
            deviceId = device.deviceId();
            mainframeId = device.getLink().id();
            kind = device.kind().block;
        }
        if (deviceId == null || !visible(player, mainframeId)) return null;
        var result = NetworkService.result(mainframeId);
        var route = NetworkService.route(deviceId);
        if (result == null || route == null) return new DeviceInfo(mainframeId, kind, "unloaded", 0, -1, List.of(), List.of(), -1, -1);
        String status = !route.connected() ? "no_route" : route.legacyOutOfRange() ? "legacy_out_of_range" : "online";
        var positions = new ArrayList<>(positions(result, route.path()));
        positions.add(pos.getCenter());
        var types = new ArrayList<>(types(result, route.path()));
        types.add(kind);
        return new DeviceInfo(mainframeId, kind, status, route.signal(), route.connected() ? route.hops() : -1, types, positions, -1, -1);
    }

    static boolean visible(ServerPlayer player, UUID mainframeId) {
        return InGameAccess.canView(player, mainframeId);
    }

    private static java.util.Map<String, Double> signals(NetworkGraph.Result result) {
        var map = new java.util.HashMap<String, Double>();
        for (var link : result.links()) map.put(AnalyzerGeometry.key(link.a(), link.b()), link.signal());
        return map;
    }

    private static List<String> types(NetworkGraph.Result result, List<String> path) {
        var list = new ArrayList<String>();
        for (var id : path) for (var n : result.nodes()) if (n.id().equals(id)) list.add(n.type().id);
        return list;
    }

    private static List<Vec3> positions(NetworkGraph.Result result, List<String> path) {
        var list = new ArrayList<Vec3>();
        for (var id : path) for (var n : result.nodes()) if (n.id().equals(id)) list.add(new Vec3(n.x(), n.y(), n.z()));
        return list;
    }
}
