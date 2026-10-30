package ai.ezfox.cccloud.analyzer;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.bridge.BridgeRegistry;
import ai.ezfox.cccloud.ingame.InGameAccess;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.relay.RelayType;
import ai.ezfox.cccloud.uplink.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends the Cloud Analyzer overlay data to players who hold the analyzer, a Link Card or a cloud device item: only
 * networks where they have the {@code view.positions} permission (AccessResolver), only within {@link AnalyzerGeometry#RADIUS} blocks,
 * at most {@link AnalyzerGeometry#MAX_ELEMENTS} elements. Checked every 10 ticks; sent when it changed and at least
 * every 2 s while held; an empty payload once when the player stops holding such an item. Server thread only.
 */
public final class AnalyzerService {
    static final int CHECK_INTERVAL = 10;
    static final int RESEND_TICKS = 40;

    private record Sent(String json, long tick) {
    }

    private static final Map<UUID, Sent> SENT = new HashMap<>();
    /** How the data reaches the player (GameTests capture it). */
    static BiConsumer<ServerPlayer, InGamePayloads.AnalyzerData> sender = PacketDistributor::sendToPlayer;

    private AnalyzerService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(AnalyzerService::onTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> SENT.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> SENT.clear());
    }

    /** The analyzer, a Link Card, or an item that places a cloud device. */
    public static boolean showsOverlay(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof CloudAnalyzerItem || item == ModRegistry.CLOUD_LINK_CARD.get()) return true;
        if (item == ModRegistry.CLOUD_MAINFRAME_ITEM.get() || item == ModRegistry.CLOUD_UPLINK_ITEM.get() || item == ModRegistry.ADVANCED_CLOUD_UPLINK_ITEM.get()
            || item == ModRegistry.CLOUD_RELAY_ITEM.get() || item == ModRegistry.ADVANCED_CLOUD_RELAY_ITEM.get() || item == ModRegistry.ENDER_RELAY_ITEM.get()) {
            return true;
        }
        for (var bridge : BridgeRegistry.ITEMS) if (item == bridge.get()) return true;
        return isRedstoneDevice(item);
    }

    /** Cloud Switch / Button / Lamp / Sensor items (placement preview with the redstone device range). */
    public static boolean isRedstoneDevice(net.minecraft.world.item.Item item) {
        return item == ModRegistry.CLOUD_SWITCH_ITEM.get() || item == ModRegistry.CLOUD_BUTTON_ITEM.get() || item == ModRegistry.CLOUD_LAMP_ITEM.get()
            || item == ModRegistry.CLOUD_SENSOR_ITEM.get();
    }

    static boolean holding(ServerPlayer player) {
        return showsOverlay(player.getMainHandItem()) || showsOverlay(player.getOffhandItem());
    }

    private static void onTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        long tick = server.getTickCount();
        if (tick % CHECK_INTERVAL != 0) return;
        for (var player : server.getPlayerList().getPlayers()) update(player, tick);
    }

    /** Checks one player (also used by the GameTests with fake players). */
    static void update(ServerPlayer player, long tick) {
        var sent = SENT.get(player.getUUID());
        if (!holding(player)) {
            if (sent != null && !sent.json().isEmpty()) {
                sender.accept(player, new InGamePayloads.AnalyzerData("{}"));
                SENT.put(player.getUUID(), new Sent("", tick));
            }
            return;
        }
        String json = Json.GSON.toJson(build(player));
        if (sent != null && sent.json().equals(json) && tick - sent.tick() < RESEND_TICKS) return;
        sender.accept(player, new InGamePayloads.AnalyzerData(json));
        SENT.put(player.getUUID(), new Sent(json, tick));
    }

    /** The player's payload: only networks where the player has {@code view.positions}. */
    static com.google.gson.JsonObject build(ServerPlayer player) {
        var networks = new ArrayList<AnalyzerGeometry.Network>();
        for (var mainframe : List.copyOf(CloudNetwork.mainframes())) {
            if (mainframe.isRemoved() || mainframe.getLevel() == null) continue;
            var id = mainframe.getMainframeId();
            if (!InGameAccess.has(player, id, ai.ezfox.cccloud.access.Perm.VIEW_POSITIONS)) continue;
            var result = NetworkService.result(id);
            if (result == null) continue;
            var names = new HashMap<String, String>();
            var statuses = new HashMap<String, String>();
            names.put(id.toString(), mainframe.getDisplayName());
            statuses.put(id.toString(), "no_power".equals(mainframe.reason()) ? "no_power" : mainframe.isOnline() && mainframe.isEnabled() ? "online" : "offline");
            for (var device : result.devices()) {
                String name = NetworkService.deviceName(id, device.id(), null);
                if (!name.isEmpty()) names.put(device.id(), name);
            }
            networks.add(new AnalyzerGeometry.Network(id.toString(), mainframe.getDisplayName(), result, names, statuses));
        }
        var p = player.position();
        return AnalyzerGeometry.build(networks, player.level().dimension().identifier().toString(), p.x, p.y, p.z, ranges());
    }

    /** Ranges for the placement preview: device types [range], node types [nodeRange, deviceRange]. */
    static Map<String, int[]> ranges() {
        var map = new LinkedHashMap<String, int[]>();
        map.put("uplink", new int[]{CloudConfig.get(CloudConfig.RANGE_UPLINK)});
        map.put("advanced_uplink", new int[]{CloudConfig.get(CloudConfig.RANGE_ADVANCED_UPLINK)});
        map.put("bridge", new int[]{CloudConfig.get(CloudConfig.RANGE_BRIDGE)});
        map.put("redstone", new int[]{CloudConfig.get(CloudConfig.RANGE_REDSTONE_DEVICE)});
        for (var type : RelayType.values()) map.put(type.nodeType.id, new int[]{type.nodeRange(), type.deviceRange()});
        return map;
    }

    /** Test hook: forget what was sent. */
    static void reset(ServerPlayer player) {
        SENT.remove(player.getUUID());
    }
}
