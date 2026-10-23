package ai.ezfox.cccloud.history;

import ai.ezfox.cccloud.CCCloud;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World SavedData ({@code data/cccloud/history.dat}): the long-term series of every mainframe (≤ 2 MB each). */
public final class HistoryData extends SavedData {
    private static final Codec<HistoryData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(Codec.STRING, Codec.STRING)).optionalFieldOf("series", Map.of())
            .forGetter(d -> d.store.save())
    ).apply(i, HistoryData::new));

    public static final SavedDataType<HistoryData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "history"), HistoryData::new, CODEC);

    private final HistoryStore store = new HistoryStore(this::setDirty);

    public HistoryData() {
    }

    private HistoryData(Map<UUID, Map<String, String>> series) {
        store.load(new HashMap<>(series));
    }

    public static HistoryData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public HistoryStore store() {
        return store;
    }
}
