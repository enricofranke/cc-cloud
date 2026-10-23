package ai.ezfox.cccloud.rules;

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
 * World SavedData ({@code data/cccloud/rules.dat}): the rules of each mainframe as validated JSON (re-validated on
 * load) and the run log (audit, last {@link #LOG_SIZE} runs). Server thread only.
 */
public final class RuleData extends SavedData {
    public static final int LOG_SIZE = 50;

    public record LogEntry(long time, String ruleId, String name, String result, boolean manual) {
        static final Codec<LogEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("time").forGetter(LogEntry::time),
            Codec.STRING.fieldOf("rule").forGetter(LogEntry::ruleId),
            Codec.STRING.optionalFieldOf("name", "").forGetter(LogEntry::name),
            Codec.STRING.fieldOf("result").forGetter(LogEntry::result),
            Codec.BOOL.optionalFieldOf("manual", false).forGetter(LogEntry::manual)
        ).apply(i, LogEntry::new));
    }

    private static final Codec<RuleData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING.listOf()).optionalFieldOf("rules", Map.of()).forGetter(d -> Map.copyOf(d.rules)),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, LogEntry.CODEC.listOf()).optionalFieldOf("log", Map.of()).forGetter(d -> Map.copyOf(d.log))
    ).apply(i, RuleData::new));

    public static final SavedDataType<RuleData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "rules"), RuleData::new, CODEC);

    /** mainframe → rule JSON strings (in order). */
    private final Map<UUID, List<String>> rules = new HashMap<>();
    private final Map<UUID, List<LogEntry>> log = new HashMap<>();

    public RuleData() {
    }

    private RuleData(Map<UUID, List<String>> rules, Map<UUID, List<LogEntry>> log) {
        this.rules.putAll(rules);
        this.log.putAll(log);
    }

    public static RuleData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<UUID, List<String>> rules() {
        return rules;
    }

    public void setRules(UUID mainframeId, List<String> json) {
        if (json.isEmpty()) rules.remove(mainframeId);
        else rules.put(mainframeId, List.copyOf(json));
        setDirty();
    }

    public List<LogEntry> log(UUID mainframeId) {
        return log.getOrDefault(mainframeId, List.of());
    }

    public void addLog(UUID mainframeId, LogEntry entry) {
        var list = new java.util.ArrayList<>(log.getOrDefault(mainframeId, List.of()));
        list.add(entry);
        while (list.size() > LOG_SIZE) list.removeFirst();
        log.put(mainframeId, List.copyOf(list));
        setDirty();
    }

    public void remove(UUID mainframeId) {
        boolean changed = rules.remove(mainframeId) != null;
        changed |= log.remove(mainframeId) != null;
        if (changed) setDirty();
    }
}
