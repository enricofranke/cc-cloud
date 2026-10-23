package ai.ezfox.cccloud.push;

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

/** World SavedData ({@code data/cccloud/push.dat}): the push targets of each mainframe (with their secrets). */
public final class PushData extends SavedData {
    private static final Codec<PushData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING.listOf()).optionalFieldOf("targets", Map.of()).forGetter(PushData::saved)
    ).apply(i, PushData::new));

    public static final SavedDataType<PushData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "push"), PushData::new, CODEC);

    private final PushStore store = new PushStore(this::setDirty);
    private final Map<UUID, List<String>> stored = new HashMap<>();
    private boolean loaded;

    public PushData() {
        loaded = true;
    }

    private PushData(Map<UUID, List<String>> targets) {
        stored.putAll(targets);
    }

    private Map<UUID, List<String>> saved() {
        return loaded ? store.save() : Map.copyOf(stored);
    }

    public static PushData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** The store; on first use the stored targets are validated against the current config. */
    public PushStore store() {
        if (!loaded) {
            loaded = true;
            int dropped = store.load(stored, PushService.allowedHosts());
            if (dropped > 0) {
                CCCloud.LOGGER.warn("[cccloud] Dropped {} push target(s) that are not allowed any more", dropped);
                setDirty();
            }
            stored.clear();
        }
        return store;
    }
}
