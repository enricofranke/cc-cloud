package ai.ezfox.cccloud.rsdevice;

import ai.ezfox.cccloud.CCCloud;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * World SavedData ({@code data/cccloud/redstone_devices.dat}): every linked Cloud Redstone device per mainframe with
 * its name, group, position and last state, so the dashboard cards keep devices whose chunk is not loaded (last
 * values, actions answer {@code unloaded}) and survive restarts. Server thread only.
 */
public final class RsDeviceData extends SavedData {
    static final Codec<RsDevice> DEVICE = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(RsDevice::id),
        Codec.STRING.xmap(s -> {
            var kind = RsKind.byId(s);
            return kind == null ? RsKind.SWITCH : kind;
        }, k -> k.id).fieldOf("kind").forGetter(RsDevice::kind),
        Codec.STRING.optionalFieldOf("name", "").forGetter(RsDevice::name),
        Codec.STRING.optionalFieldOf("group", "").forGetter(RsDevice::group),
        Codec.STRING.fieldOf("dim").forGetter(RsDevice::dim),
        Codec.INT.fieldOf("x").forGetter(RsDevice::x),
        Codec.INT.fieldOf("y").forGetter(RsDevice::y),
        Codec.INT.fieldOf("z").forGetter(RsDevice::z),
        Codec.BOOL.optionalFieldOf("analog", false).forGetter(RsDevice::analog),
        Codec.INT.optionalFieldOf("level", 0).forGetter(RsDevice::level),
        Codec.BOOL.optionalFieldOf("on", false).forGetter(RsDevice::on),
        Codec.INT.optionalFieldOf("preset", 15).forGetter(RsDevice::preset),
        Codec.INT.optionalFieldOf("pulse", RsDevice.PULSE_DEFAULT).forGetter(RsDevice::pulse),
        Codec.BOOL.optionalFieldOf("follow", false).forGetter(RsDevice::follow),
        Codec.BOOL.optionalFieldOf("pin", false).forGetter(RsDevice::pin),
        Codec.BOOL.optionalFieldOf("confirm", false).forGetter(RsDevice::confirm)
    ).apply(i, RsDevice::new));

    private static final Codec<RsDeviceData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, DEVICE.listOf()).optionalFieldOf("devices", Map.of()).forGetter(RsDeviceData::save)
    ).apply(i, RsDeviceData::new));

    public static final SavedDataType<RsDeviceData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "redstone_devices"),
        RsDeviceData::new, CODEC);

    /** mainframe → device id → device (link order). */
    private final Map<UUID, LinkedHashMap<UUID, RsDevice>> devices = new HashMap<>();

    public RsDeviceData() {
    }

    private RsDeviceData(Map<UUID, List<RsDevice>> saved) {
        for (var e : saved.entrySet()) {
            var map = new LinkedHashMap<UUID, RsDevice>();
            for (var d : e.getValue()) map.put(d.id(), d);
            devices.put(e.getKey(), map);
        }
    }

    private Map<UUID, List<RsDevice>> save() {
        var out = new HashMap<UUID, List<RsDevice>>();
        for (var e : devices.entrySet()) out.put(e.getKey(), List.copyOf(e.getValue().values()));
        return out;
    }

    public static RsDeviceData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public java.util.Set<UUID> mainframes() {
        return devices.keySet();
    }

    public Map<UUID, RsDevice> devices(UUID mainframeId) {
        var map = devices.get(mainframeId);
        return map == null ? Map.of() : map;
    }

    public @Nullable RsDevice device(UUID mainframeId, UUID deviceId) {
        var map = devices.get(mainframeId);
        return map == null ? null : map.get(deviceId);
    }

    /** @return true if something changed */
    public boolean put(UUID mainframeId, RsDevice device) {
        var map = devices.computeIfAbsent(mainframeId, k -> new LinkedHashMap<>());
        if (device.equals(map.put(device.id(), device))) return false;
        setDirty();
        return true;
    }

    public boolean remove(UUID mainframeId, UUID deviceId) {
        var map = devices.get(mainframeId);
        if (map == null || map.remove(deviceId) == null) return false;
        if (map.isEmpty()) devices.remove(mainframeId);
        setDirty();
        return true;
    }

    public void forget(UUID mainframeId) {
        if (devices.remove(mainframeId) != null) setDirty();
    }
}
