package ai.ezfox.cccloud.scene;

import ai.ezfox.cccloud.CCCloud;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
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
 * World SavedData ({@code data/cccloud/scenes.dat}): the scenes of each mainframe as validated JSON (re-validated on
 * load) with their owner, and the run log (last {@link #LOG_SIZE} runs). Server thread only.
 */
public final class SceneData extends SavedData {
    public static final int LOG_SIZE = 50;

    /** A stored scene: its JSON ({@link SceneCodec#toJson}) and the player who saved it. */
    public record Stored(String json, UUID owner, String ownerName) {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("json").forGetter(Stored::json),
            UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(Stored::owner),
            Codec.STRING.optionalFieldOf("ownerName", "").forGetter(Stored::ownerName)
        ).apply(i, Stored::new));
    }

    public record LogEntry(long time, String sceneId, String name, String by, String source, String result) {
        static final Codec<LogEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("time").forGetter(LogEntry::time),
            Codec.STRING.fieldOf("scene").forGetter(LogEntry::sceneId),
            Codec.STRING.optionalFieldOf("name", "").forGetter(LogEntry::name),
            Codec.STRING.optionalFieldOf("by", "").forGetter(LogEntry::by),
            Codec.STRING.optionalFieldOf("source", "").forGetter(LogEntry::source),
            Codec.STRING.fieldOf("result").forGetter(LogEntry::result)
        ).apply(i, LogEntry::new));
    }

    private static final Codec<SceneData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Stored.CODEC.listOf()).optionalFieldOf("scenes", Map.of()).forGetter(d -> Map.copyOf(d.scenes)),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, LogEntry.CODEC.listOf()).optionalFieldOf("log", Map.of()).forGetter(d -> Map.copyOf(d.log))
    ).apply(i, SceneData::new));

    public static final SavedDataType<SceneData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "scenes"), SceneData::new, CODEC);

    private final Map<UUID, List<Stored>> scenes = new HashMap<>();
    private final Map<UUID, List<LogEntry>> log = new HashMap<>();

    public SceneData() {
    }

    private SceneData(Map<UUID, List<Stored>> scenes, Map<UUID, List<LogEntry>> log) {
        this.scenes.putAll(scenes);
        this.log.putAll(log);
    }

    public static SceneData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<UUID, List<Stored>> scenes() {
        return scenes;
    }

    public void setScenes(UUID mainframeId, List<Stored> list) {
        if (list.isEmpty()) scenes.remove(mainframeId);
        else scenes.put(mainframeId, List.copyOf(list));
        setDirty();
    }

    public List<LogEntry> log(UUID mainframeId) {
        return log.getOrDefault(mainframeId, List.of());
    }

    public void addLog(UUID mainframeId, LogEntry entry) {
        var list = new ArrayList<>(log.getOrDefault(mainframeId, List.of()));
        list.add(entry);
        while (list.size() > LOG_SIZE) list.removeFirst();
        log.put(mainframeId, List.copyOf(list));
        setDirty();
    }

    public void remove(UUID mainframeId) {
        boolean changed = scenes.remove(mainframeId) != null;
        changed |= log.remove(mainframeId) != null;
        if (changed) setDirty();
    }
}
