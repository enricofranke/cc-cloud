package ai.ezfox.cccloud.redstone;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * Game side of wireless redstone: collects the connected transmitter/receiver uplinks of every mainframe network
 * (radio, relays, cables and ender links – whatever routes the device), computes the channels
 * ({@link RedstoneChannels}) and drives the receivers' output. Runs on the next tick after a change (input change,
 * mode/channel edit, network recompute, override) plus a cheap pass every second; receivers change at most every
 * 2 ticks (faster clocks are smoothed). Server thread only.
 */
public final class RedstoneService {
    private static final int SAFETY_INTERVAL = 20;
    private static boolean dirty = true;
    private static Map<String, Map<String, RedstoneChannels.Channel>> state = Map.of();
    private static final Map<String, Map<String, RedstoneChannels.Override>> OVERRIDES = new HashMap<>();
    private static final Map<String, JsonObject> PUBLISHED = new HashMap<>();

    private RedstoneService() {
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void clear() {
        state = Map.of();
        OVERRIDES.clear();
        PUBLISHED.clear();
        dirty = true;
    }

    public static void tick(MinecraftServer server) {
        if (!dirty && server.getTickCount() % SAFETY_INTERVAL != 0) return;
        dirty = false;
        try {
            recompute(server);
        } catch (RuntimeException e) {
            CCCloud.LOGGER.error("[cccloud] Could not update wireless redstone", e);
        }
    }

    private static void recompute(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (var mf : OVERRIDES.values()) mf.values().removeIf(o -> !o.active(now));
        OVERRIDES.values().removeIf(Map::isEmpty);
        var members = new ArrayList<RedstoneChannels.Member>();
        var receivers = new ArrayList<CloudUplinkBlockEntity>();
        for (var uplink : CloudNetwork.uplinks()) {
            if (uplink.isRemoved() || uplink.getLevel() == null) continue;
            var mode = uplink.redstoneMode();
            var link = uplink.getLink();
            var route = NetworkService.route(uplink.getUplinkId());
            boolean connected = link != null && route != null && route.connected() && mode != RedstoneMode.OFF
                && RedstoneChannels.validName(uplink.redstoneChannel());
            if (mode == RedstoneMode.RECEIVE) receivers.add(uplink);
            if (!connected) continue;
            members.add(new RedstoneChannels.Member(link.id().toString(), uplink.redstoneChannel(), mode == RedstoneMode.TRANSMIT,
                mode == RedstoneMode.TRANSMIT ? uplink.redstoneInput() : 0));
        }
        state = RedstoneChannels.compute(members, OVERRIDES, now);
        long gameTime = server.overworld().getGameTime();
        for (var uplink : receivers) {
            var link = uplink.getLink();
            var route = NetworkService.route(uplink.getUplinkId());
            int target = link == null || route == null || !route.connected() ? 0
                : RedstoneChannels.levelFor(state, link.id().toString(), uplink.redstoneChannel());
            if (!uplink.applyRedstoneOutput(target, gameTime)) dirty = true; // smoothed: try again next tick
        }
        // Uplinks that left receiver mode switch their output off.
        for (var uplink : CloudNetwork.uplinks()) {
            if (uplink.redstoneMode() != RedstoneMode.RECEIVE && uplink.redstoneOutput() != 0 && !uplink.applyRedstoneOutput(0, gameTime)) dirty = true;
        }
        publish();
    }

    private static void publish() {
        var seen = new HashSet<String>();
        for (var e : state.entrySet()) {
            seen.add(e.getKey());
            var json = toJson(e.getValue().values());
            if (json.equals(PUBLISHED.get(e.getKey()))) continue;
            PUBLISHED.put(e.getKey(), json);
            WebHub.INSTANCE.publishRedstone(UUID.fromString(e.getKey()), json);
        }
        for (var id : new ArrayList<>(PUBLISHED.keySet())) {
            if (seen.contains(id)) continue;
            PUBLISHED.remove(id);
            WebHub.INSTANCE.publishRedstone(UUID.fromString(id), toJson(java.util.List.of()));
        }
    }

    /** {@code {channels:[{name, level, signal, transmitters, receivers, override?:{level, until}}], limit}}. */
    static JsonObject toJson(java.util.Collection<RedstoneChannels.Channel> channels) {
        var json = new JsonObject();
        var array = new JsonArray();
        for (var c : channels) {
            var o = new JsonObject();
            o.addProperty("name", c.name());
            o.addProperty("level", c.level());
            o.addProperty("signal", c.signal());
            o.addProperty("transmitters", c.transmitters());
            o.addProperty("receivers", c.receivers());
            if (c.override() != null) {
                var ov = new JsonObject();
                ov.addProperty("level", c.override().level());
                if (c.override().until() == 0) ov.add("until", com.google.gson.JsonNull.INSTANCE);
                else ov.addProperty("until", c.override().until());
                o.add("override", ov);
            }
            array.add(o);
        }
        json.add("channels", array);
        json.addProperty("limit", RedstoneChannels.MAX_CHANNELS);
        return json;
    }

    /** Channels of a mainframe network (for tests, Lua and the screen). */
    public static Map<String, RedstoneChannels.Channel> channels(UUID mainframeId) {
        return state.getOrDefault(mainframeId.toString(), Map.of());
    }

    /**
     * Operator override from the dashboard: {@code level} 0–15 for {@code seconds} (null = until reset); level null
     * clears it. Returns null or an error code.
     */
    public static @Nullable String override(UUID mainframeId, String channel, @Nullable Integer level, @Nullable Integer seconds) {
        if (!RedstoneChannels.validName(channel)) return "invalid_channel";
        var map = OVERRIDES.computeIfAbsent(mainframeId.toString(), k -> new HashMap<>());
        if (level == null) {
            map.remove(channel);
        } else {
            if (level < 0 || level > 15 || (seconds != null && (seconds < 1 || seconds > 86_400))) return "invalid_argument";
            if (!map.containsKey(channel) && channels(mainframeId).size() >= RedstoneChannels.MAX_CHANNELS
                && !channels(mainframeId).containsKey(channel)) return "too_many_channels";
            map.put(channel, new RedstoneChannels.Override(level, seconds == null ? 0 : System.currentTimeMillis() + seconds * 1000L));
        }
        dirty = true;
        return null;
    }
}
