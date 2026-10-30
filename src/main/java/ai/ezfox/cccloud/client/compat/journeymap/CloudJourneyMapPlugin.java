package ai.ezfox.cccloud.client.compat.journeymap;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.client.CloudClientConfig;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import ai.ezfox.cccloud.map.MapWaypoints;
import com.google.gson.JsonParser;
import java.util.Set;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.common.waypoint.WaypointGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * JourneyMap (docs/SPEC.md v0.8 wave 3, docs/COMPAT.md): nodes of every network where the player has
 * {@code view.positions} as temporary (non-persistent) JourneyMap waypoints, all in one waypoint group "CC: Cloud" so
 * JourneyMap's waypoint manager can hide them at once (the group itself is kept, so that choice survives). Which node
 * types: client config {@code journeyMapWaypointTypes} (default mainframes and Ender Relays). Only mainframes get
 * JourneyMap's in-world marker and beam; the rest show on the map only. Names are the device's card title (computer
 * label, Lua title, machine card) or the block's name. Discovered by JourneyMap through its annotation, so this class
 * is only loaded when JourneyMap is installed. The client subscribes to the server's {@code map_nodes} payload while
 * {@code journeyMapWaypoints} is on; the server re-checks the permission every 5 s and only sends networks the player
 * may locate.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class CloudJourneyMapPlugin implements IClientPlugin {
    private static @Nullable IClientAPI api;
    /** What the server was last told (null = nothing sent in this connection). */
    private static @Nullable Boolean subscribed;
    /** The last payload, re-applied when the type filter changes. */
    private static @Nullable String lastJson;
    private static Set<String> appliedTypes = Set.of();
    private static int ticks;

    @Override
    public String getModId() {
        return CCCloud.MODID;
    }

    @Override
    public void initialize(IClientAPI clientApi) {
        api = clientApi;
        InGamePayloads.clientMapNodesHandler = payload -> Minecraft.getInstance().execute(() -> apply(payload.json()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            subscribed = null;
            lastJson = null;
            clear();
        });
    }

    private static void tick() {
        if (++ticks % 40 != 0 || Minecraft.getInstance().getConnection() == null) return;
        boolean want = CloudClientConfig.journeyMapWaypoints();
        var json = lastJson;
        if (want && json != null && !CloudClientConfig.journeyMapWaypointTypes().equals(appliedTypes)) apply(json);
        if (subscribed != null && subscribed == want) return;
        try {
            ClientPacketDistributor.sendToServer(new InGamePayloads.MapNodesSubscribe(want));
            subscribed = want;
        } catch (RuntimeException e) {
            // Server without CC: Cloud's channel: try again later.
            return;
        }
        if (!want) {
            lastJson = null;
            clear();
        }
    }

    private static void clear() {
        var a = api;
        if (a != null) a.removeAllWaypoints(CCCloud.MODID);
    }

    static int color(String type) {
        return switch (type) {
            case "mainframe" -> 0xFFC34A;
            case "relay", "advanced_relay", "ender_relay" -> 0x4AC3FF;
            case "bridge" -> 0x9B6BFF;
            default -> 0x6BFF8E;
        };
    }

    /** Our waypoint group: the one JourneyMap kept from an earlier session (with the player's on/off choice) or a new one. */
    private static @Nullable WaypointGroup group(IClientAPI a) {
        try {
            var group = a.getWaypointGroupByName(CCCloud.MODID, MapWaypoints.GROUP);
            if (group != null) return group;
            group = WaypointFactory.createWaypointGroup(CCCloud.MODID, MapWaypoints.GROUP);
            group.setDescription(I18n.get("cccloud.journeymap.group"));
            a.addWaypointGroup(group);
            return group;
        } catch (RuntimeException | LinkageError e) {
            CCCloud.LOGGER.debug("[cccloud] JourneyMap waypoint group unavailable: {}", e.toString());
            return null;
        }
    }

    private static void apply(String json) {
        var a = api;
        if (a == null) return;
        clear();
        lastJson = json;
        if (!CloudClientConfig.journeyMapWaypoints()) return;
        var types = CloudClientConfig.journeyMapWaypointTypes();
        appliedTypes = types;
        try {
            var nodes = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("nodes");
            WaypointGroup group = null;
            boolean groupLooked = false;
            for (var element : nodes) {
                var n = element.getAsJsonObject();
                String type = n.get("type").getAsString();
                if (!MapWaypoints.shown(type, types)) continue;
                var dimId = Identifier.tryParse(n.get("dim").getAsString());
                if (dimId == null) continue;
                String title = MapWaypoints.title(type, n.has("name") ? n.get("name").getAsString() : null);
                String name = title != null ? title : I18n.get(MapWaypoints.nameKey(type));
                var pos = new BlockPos(n.get("x").getAsInt(), n.get("y").getAsInt(), n.get("z").getAsInt());
                var waypoint = WaypointFactory.createClientWaypoint(CCCloud.MODID, pos, name, ResourceKey.create(Registries.DIMENSION, dimId), false);
                waypoint.setColor(color(type));
                boolean inWorld = MapWaypoints.inWorld(type);
                waypoint.setShowInWorld(inWorld);
                waypoint.setShowBeacon(inWorld);
                a.addWaypoint(CCCloud.MODID, waypoint);
                if (!groupLooked) {
                    groupLooked = true;
                    group = group(a);
                }
                if (group != null) group.addWaypoint(waypoint);
            }
        } catch (RuntimeException | LinkageError e) {
            CCCloud.LOGGER.warn("[cccloud] JourneyMap waypoints disabled: {}", e.toString());
            InGamePayloads.clientMapNodesHandler = null;
        }
    }
}
