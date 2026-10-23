package ai.ezfox.cccloud.client.compat.journeymap;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.client.CloudClientConfig;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import com.google.gson.JsonParser;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.client.Minecraft;
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
 * JourneyMap (docs/SPEC.md v0.8 wave 3): the mainframes, relays and devices of every network where the player has
 * {@code view.positions} as temporary (non-persistent) JourneyMap waypoints. Discovered by JourneyMap through its
 * annotation, so this class is only loaded when JourneyMap is installed. The client subscribes to the server's
 * {@code map_nodes} payload while the client config {@code journeyMapWaypoints} is on; the server re-checks the
 * permission every 5 s and only sends networks the player may locate.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class CloudJourneyMapPlugin implements IClientPlugin {
    private static @Nullable IClientAPI api;
    /** What the server was last told (null = nothing sent in this connection). */
    private static @Nullable Boolean subscribed;
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
            clear();
        });
    }

    private static void tick() {
        if (++ticks % 40 != 0 || Minecraft.getInstance().getConnection() == null) return;
        boolean want = CloudClientConfig.journeyMapWaypoints();
        if (subscribed != null && subscribed == want) return;
        try {
            ClientPacketDistributor.sendToServer(new InGamePayloads.MapNodesSubscribe(want));
            subscribed = want;
        } catch (RuntimeException e) {
            // Server without CC: Cloud's channel: try again later.
            return;
        }
        if (!want) clear();
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

    private static void apply(String json) {
        var a = api;
        if (a == null) return;
        clear();
        if (!CloudClientConfig.journeyMapWaypoints()) return;
        try {
            var nodes = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("nodes");
            for (var element : nodes) {
                var n = element.getAsJsonObject();
                var dimId = Identifier.tryParse(n.get("dim").getAsString());
                if (dimId == null) continue;
                String type = n.get("type").getAsString();
                String name = type.equals("mainframe") ? n.get("mfName").getAsString() : n.get("mfName").getAsString() + ": " + n.get("name").getAsString();
                var pos = new BlockPos(n.get("x").getAsInt(), n.get("y").getAsInt(), n.get("z").getAsInt());
                var waypoint = WaypointFactory.createClientWaypoint(CCCloud.MODID, pos, "CC: Cloud – " + name, ResourceKey.create(Registries.DIMENSION, dimId), false);
                waypoint.setColor(color(type));
                a.addWaypoint(CCCloud.MODID, waypoint);
            }
        } catch (RuntimeException | LinkageError e) {
            CCCloud.LOGGER.warn("[cccloud] JourneyMap waypoints disabled: {}", e.toString());
            InGamePayloads.clientMapNodesHandler = null;
        }
    }
}
