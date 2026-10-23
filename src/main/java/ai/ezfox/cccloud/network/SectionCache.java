package ai.ezfox.cccloud.network;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World SavedData ({@code data/cccloud/sections.dat}): the last snapshot (widgets and values) of every uplink/bridge
 * section whose chunk got unloaded, so the dashboard keeps showing it greyed out ({@code "loaded":false},
 * {@code "lastSeen"}) – also after a server restart, without loading chunks. A live snapshot replaces it on load.
 * Bounded: {@link #MAX_BYTES} per section, {@link #MAX_SECTIONS} per mainframe. Server thread only.
 */
public final class SectionCache extends SavedData {
    public static final int MAX_BYTES = 64 * 1024;
    public static final int MAX_SECTIONS = 256;

    private static final Codec<SectionCache> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(Codec.STRING, Codec.STRING)).optionalFieldOf("sections", Map.of())
            .forGetter(c -> Map.copyOf(c.sections))
    ).apply(i, SectionCache::new));

    public static final SavedDataType<SectionCache> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "sections"), SectionCache::new, CODEC);

    /** mainframe → section id → snapshot JSON (with loaded=false, lastSeen). */
    private final Map<UUID, Map<String, String>> sections = new HashMap<>();

    public SectionCache() {
    }

    private SectionCache(Map<UUID, Map<String, String>> sections) {
        for (var e : sections.entrySet()) this.sections.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
    }

    public static SectionCache get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Marks a snapshot as cached (unloaded, offline, lastSeen = now). */
    public static JsonObject unloaded(JsonObject snapshot) {
        var copy = snapshot.deepCopy();
        copy.addProperty("online", false);
        copy.addProperty("loaded", false);
        copy.addProperty("lastSeen", System.currentTimeMillis());
        return copy;
    }

    /** Chunk unload: remember the section's last snapshot. */
    public void store(UUID mainframeId, JsonObject snapshot) {
        String id = snapshot.get("uplinkId").getAsString();
        String json = snapshot.toString();
        if (json.length() > MAX_BYTES) return;
        var map = sections.computeIfAbsent(mainframeId, k -> new LinkedHashMap<>());
        if (!map.containsKey(id) && map.size() >= MAX_SECTIONS) return;
        map.put(id, json);
        setDirty();
    }

    /** The section was unlinked or broken. */
    public void forget(UUID mainframeId, String sectionId) {
        var map = sections.get(mainframeId);
        if (map != null && map.remove(sectionId) != null) {
            if (map.isEmpty()) sections.remove(mainframeId);
            setDirty();
        }
    }

    public void forgetMainframe(UUID mainframeId) {
        if (sections.remove(mainframeId) != null) setDirty();
    }

    /** Server start: every cached section goes to the hub (sections that load later replace their copy). */
    public void publishAll() {
        for (var e : sections.entrySet()) {
            for (var json : e.getValue().values()) {
                try {
                    var snapshot = JsonParser.parseString(json).getAsJsonObject();
                    snapshot.addProperty("online", false);
                    snapshot.addProperty("loaded", false);
                    WebHub.INSTANCE.publishUplink(e.getKey(), snapshot);
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    /** Server stop: remember every loaded linked section (chunks unload after the world was saved). */
    public void captureAll() {
        for (var uplink : CloudNetwork.uplinks()) {
            var link = uplink.getLink();
            if (link != null && !uplink.isRemoved()) store(link.id(), unloaded(uplink.snapshot()));
        }
        for (var section : CloudNetwork.bridgeSections()) {
            if (section instanceof ai.ezfox.cccloud.bridge.BridgeSection bridge && bridge.getLink() != null) store(bridge.getLink().id(), unloaded(bridge.snapshot()));
        }
    }

    public int size(UUID mainframeId) {
        return sections.getOrDefault(mainframeId, Map.of()).size();
    }
}
