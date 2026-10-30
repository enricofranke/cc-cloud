package ai.ezfox.cccloud.production;

import ai.ezfox.cccloud.CCCloud;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World SavedData ({@code data/cccloud/production.dat}): the production charts pinned to each mainframe's Production
 * section (v0.10, at most {@link #MAX_PINS}). The rates themselves are not stored. Server thread only.
 */
public final class ProductionData extends SavedData {
    public static final int MAX_PINS = 16;

    /**
     * A pinned chart.
     *
     * @param kind     item, storage or energy
     * @param uplinkId the storage section (item and storage pins), else ""
     * @param name     the item id (item pins), else ""
     */
    public record Pin(String id, String kind, String uplinkId, String name, String label) {
        static final Codec<Pin> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Pin::id),
            Codec.STRING.fieldOf("kind").forGetter(Pin::kind),
            Codec.STRING.optionalFieldOf("uplinkId", "").forGetter(Pin::uplinkId),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Pin::name),
            Codec.STRING.optionalFieldOf("label", "").forGetter(Pin::label)
        ).apply(i, Pin::new));
    }

    private static final Codec<ProductionData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Pin.CODEC.listOf()).optionalFieldOf("pins", Map.of()).forGetter(d -> Map.copyOf(d.pins))
    ).apply(i, ProductionData::new));

    public static final SavedDataType<ProductionData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "production"),
        ProductionData::new, CODEC);

    private final Map<UUID, List<Pin>> pins = new HashMap<>();

    public ProductionData() {
    }

    private ProductionData(Map<UUID, List<Pin>> pins) {
        this.pins.putAll(pins);
    }

    public static ProductionData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<UUID, List<Pin>> all() {
        return pins;
    }

    public List<Pin> pins(UUID mainframeId) {
        return pins.getOrDefault(mainframeId, List.of());
    }

    public void setPins(UUID mainframeId, List<Pin> list) {
        if (list.isEmpty()) pins.remove(mainframeId);
        else pins.put(mainframeId, List.copyOf(list));
        setDirty();
    }

    public void remove(UUID mainframeId) {
        if (pins.remove(mainframeId) != null) setDirty();
    }
}
