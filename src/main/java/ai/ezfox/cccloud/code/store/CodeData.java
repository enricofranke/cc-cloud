package ai.ezfox.cccloud.code.store;

import ai.ezfox.cccloud.CCCloud;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World SavedData ({@code data/cccloud/cloud_scripts.dat}): the Cloud Scripts of each mainframe with their versions,
 * autostart and storage. Server thread only. Logs and run state are not saved (a server restart starts the autostart
 * scripts again).
 */
public final class CodeData extends SavedData {
    static final Codec<ScriptVersion> VERSION_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("version").forGetter(ScriptVersion::version),
        Codec.LONG.fieldOf("time").forGetter(ScriptVersion::time),
        UUIDUtil.STRING_CODEC.fieldOf("author").forGetter(ScriptVersion::author),
        Codec.STRING.optionalFieldOf("authorName", "").forGetter(ScriptVersion::authorName),
        Codec.STRING.fieldOf("source").forGetter(ScriptVersion::source),
        Codec.STRING.optionalFieldOf("note").forGetter(v -> Optional.ofNullable(v.note()))
    ).apply(i, (version, time, author, authorName, source, note) -> new ScriptVersion(version, time, author, authorName, source, note.orElse(null))));

    static final Codec<ScriptRecord> RECORD_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("id").forGetter(ScriptRecord::id),
        Codec.STRING.fieldOf("name").forGetter(ScriptRecord::name),
        Codec.STRING.optionalFieldOf("language", "lua").forGetter(ScriptRecord::language),
        Codec.BOOL.optionalFieldOf("autostart", false).forGetter(ScriptRecord::autostart),
        UUIDUtil.STRING_CODEC.optionalFieldOf("autostartBy").forGetter(r -> Optional.ofNullable(r.autostartBy())),
        Codec.STRING.optionalFieldOf("autostartByName", "").forGetter(ScriptRecord::autostartByName),
        Codec.LONG.optionalFieldOf("created", 0L).forGetter(ScriptRecord::created),
        VERSION_CODEC.listOf().fieldOf("versions").forGetter(ScriptRecord::versions),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("store", Map.of()).forGetter(ScriptRecord::store)
    ).apply(i, (id, name, language, autostart, by, byName, created, versions, store) -> new ScriptRecord(id, name, language, autostart && by.isPresent(),
        by.orElse(null), byName, created, versions.isEmpty() ? List.of(new ScriptVersion(1, created, new UUID(0, 0), "", "", null)) : versions, store)));

    private static final Codec<CodeData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, RECORD_CODEC.listOf()).optionalFieldOf("scripts", Map.of()).forGetter(d -> Map.copyOf(d.scripts))
    ).apply(i, CodeData::new));

    public static final SavedDataType<CodeData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "cloud_scripts"), CodeData::new, CODEC);

    private final Map<UUID, List<ScriptRecord>> scripts = new HashMap<>();

    public CodeData() {
    }

    private CodeData(Map<UUID, List<ScriptRecord>> scripts) {
        for (var e : scripts.entrySet()) {
            if (!e.getValue().isEmpty()) this.scripts.put(e.getKey(), List.copyOf(e.getValue()));
        }
    }

    public static CodeData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<UUID, List<ScriptRecord>> scripts() {
        return Map.copyOf(scripts);
    }

    public List<ScriptRecord> scripts(UUID mainframeId) {
        return scripts.getOrDefault(mainframeId, List.of());
    }

    public void set(UUID mainframeId, List<ScriptRecord> list) {
        if (list.isEmpty()) scripts.remove(mainframeId);
        else scripts.put(mainframeId, List.copyOf(list));
        setDirty();
    }

    public void remove(UUID mainframeId) {
        if (scripts.remove(mainframeId) != null) setDirty();
    }

    /** Test hook: what the codec makes of this data (encode, then decode). */
    public static CodeData roundTrip(CodeData data) {
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        var json = CODEC.encodeStart(ops, data).getOrThrow();
        return CODEC.parse(ops, json).getOrThrow();
    }
}
