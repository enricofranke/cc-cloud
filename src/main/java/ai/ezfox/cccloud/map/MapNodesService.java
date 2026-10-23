package ai.ezfox.cccloud.map;

import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.ingame.InGameAccess;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.uplink.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends {@link MapNodes} to clients that subscribed (a client map integration such as JourneyMap, switched on in the
 * client config). Rebuilt every {@link #INTERVAL} ticks per subscriber with the live {@code view.positions} check and
 * sent only when it changed; permission loss removes the networks on the next round. Server thread only.
 */
public final class MapNodesService {
    static final int INTERVAL = 100;
    private static final Map<UUID, String> SUBSCRIBED = new HashMap<>();
    /** How the data reaches the player (tests capture it). */
    static BiConsumer<ServerPlayer, InGamePayloads.MapNodesData> sender = PacketDistributor::sendToPlayer;

    private MapNodesService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % INTERVAL != 0) return;
            for (var player : e.getServer().getPlayerList().getPlayers()) if (SUBSCRIBED.containsKey(player.getUUID())) update(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> SUBSCRIBED.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> SUBSCRIBED.clear());
    }

    /** The client switched its map waypoints on or off. */
    public static void subscribe(ServerPlayer player, boolean on) {
        if (!on) {
            SUBSCRIBED.remove(player.getUUID());
            return;
        }
        // only an actual change rebuilds, at most once a second (security review M6); the timer keeps it up to date
        if (SUBSCRIBED.containsKey(player.getUUID())
            || !ai.ezfox.cccloud.ingame.PacketLimiter.IN_GAME.allow(player.getUUID(), "map_nodes", System.currentTimeMillis())) return;
        SUBSCRIBED.put(player.getUUID(), "");
        update(player);
    }

    static void update(ServerPlayer player) {
        String json = Json.GSON.toJson(build(player));
        if (json.equals(SUBSCRIBED.get(player.getUUID()))) return;
        SUBSCRIBED.put(player.getUUID(), json);
        sender.accept(player, new InGamePayloads.MapNodesData(json));
    }

    static com.google.gson.JsonObject build(ServerPlayer player) {
        List<MapNodes.Network> networks = new ArrayList<>();
        for (var mainframe : List.copyOf(CloudNetwork.mainframes())) {
            if (mainframe.isRemoved() || mainframe.getLevel() == null) continue;
            var id = mainframe.getMainframeId();
            var result = NetworkService.result(id);
            if (result == null) continue;
            var names = new HashMap<String, String>();
            names.put(id.toString(), mainframe.getDisplayName());
            for (var device : result.devices()) {
                var section = CloudNetwork.section(UUID.fromString(device.id()));
                if (section != null && !section.getTitle().isEmpty()) names.put(device.id(), section.getTitle());
            }
            networks.add(new MapNodes.Network(id, mainframe.getDisplayName(), result, names));
        }
        return MapNodes.build(networks, id -> InGameAccess.has(player, id, Perm.VIEW_POSITIONS));
    }

    /** Test hook. */
    static boolean subscribed(ServerPlayer player) {
        return SUBSCRIBED.containsKey(player.getUUID());
    }
}
