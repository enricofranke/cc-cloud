package ai.ezfox.cccloud.block;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * World SavedData ({@code data/cccloud/removed_mainframes.dat}): mainframes whose block was removed, kept for
 * {@code mainframeRetentionDays} (docs/SPEC.md "Mainframe removal"). Their server-side data (devices, accounts, PIN,
 * layout, rules, scenes, API keys, push targets, cached sections) stays untouched meanwhile, so placing the mainframe's
 * item again (or one from {@code /cccloud mainframe recover}) restores everything; afterwards {@link #purge} deletes it.
 * The entry also keeps what only the block entity knew: owner, dashboard switch and the linked devices. Server thread only.
 */
public final class MainframeRetention extends SavedData {
    /**
     * @param dropped   the block may have dropped its item (survival break with the right tool, explosions, block
     *                  movers): then only server admins may hand out another one
     * @param recovered an item was handed out with {@code /cccloud mainframe recover} (owners get one per removal)
     */
    public record Entry(Optional<UUID> owner, String ownerName, long removedAt, GlobalPos pos, boolean dropped, boolean recovered,
                        boolean enabled, Map<UUID, GlobalPos> uplinks) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.optionalFieldOf("owner").forGetter(Entry::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(Entry::ownerName),
            Codec.LONG.fieldOf("removed_at").forGetter(Entry::removedAt),
            GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
            Codec.BOOL.optionalFieldOf("dropped", true).forGetter(Entry::dropped),
            Codec.BOOL.optionalFieldOf("recovered", false).forGetter(Entry::recovered),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Entry::enabled),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, GlobalPos.CODEC).optionalFieldOf("uplinks", Map.of()).forGetter(Entry::uplinks)
        ).apply(i, Entry::new));

        public boolean ownedBy(UUID player) {
            return owner.isPresent() && owner.get().equals(player);
        }

        Entry withRecovered() {
            return new Entry(owner, ownerName, removedAt, pos, dropped, true, enabled, uplinks);
        }
    }

    private static final Codec<MainframeRetention> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC).optionalFieldOf("removed", Map.of()).forGetter(d -> Map.copyOf(d.entries))
    ).apply(i, MainframeRetention::new));

    public static final SavedDataType<MainframeRetention> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(CCCloud.MODID, "removed_mainframes"), MainframeRetention::new, CODEC
    );
    private static final long DAY_MS = 86_400_000L;
    /** Expired entries are looked for once a minute. */
    private static final int PURGE_INTERVAL = 1200;

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public MainframeRetention() {
    }

    private MainframeRetention(Map<UUID, Entry> entries) {
        this.entries.putAll(entries);
    }

    public static MainframeRetention get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static long retentionMs() {
        return CloudConfig.get(CloudConfig.MAINFRAME_RETENTION_DAYS) * DAY_MS;
    }

    /** Millis when the data of this removed mainframe is deleted. */
    public static long purgeAt(Entry entry) {
        return entry.removedAt() + retentionMs();
    }

    /** Keeps a removed mainframe (its data stays); the dashboard shows it as removed until it is restored or purged. */
    public void removed(UUID id, Entry entry) {
        entries.put(id, entry);
        setDirty();
        WebHub.INSTANCE.publishRemoved(id, name(entry), purgeAt(entry));
    }

    /** The name the mainframe had (it is named after its position). */
    public static String name(Entry entry) {
        var p = entry.pos().pos();
        return "Mainframe @ " + p.getX() + " " + p.getY() + " " + p.getZ();
    }

    public @Nullable Entry entry(UUID id) {
        return entries.get(id);
    }

    /** All removed mainframes (oldest first). */
    public Map<UUID, Entry> entries() {
        return java.util.Collections.unmodifiableMap(entries);
    }

    /** The mainframe was placed again: forgets the entry and returns it (null if it was not removed or already purged). */
    public @Nullable Entry restore(UUID id) {
        var entry = entries.remove(id);
        if (entry != null) {
            setDirty();
            WebHub.INSTANCE.publishRemoved(id, name(entry), 0);
        }
        return entry;
    }

    public void markRecovered(UUID id) {
        var entry = entries.get(id);
        if (entry == null) return;
        entries.put(id, entry.withRecovered());
        setDirty();
    }

    /** Removed mainframes of a player (they still count for the AccessStore, but not against the mainframe limit). */
    public int ownedBy(UUID player) {
        int n = 0;
        for (var entry : entries.values()) if (entry.ownedBy(player)) n++;
        return n;
    }

    /** Deletes the data of every entry whose retention ended at {@code now}; returns their ids. */
    public List<UUID> purgeExpired(MinecraftServer server, long now) {
        var expired = new ArrayList<UUID>();
        for (var e : entries.entrySet()) if (purgeAt(e.getValue()) <= now) expired.add(e.getKey());
        for (var id : expired) purge(server, id);
        return expired;
    }

    /** Server start: removed mainframes show as removed on the dashboard again, expired ones are deleted. */
    public void publishAll(MinecraftServer server, long now) {
        purgeExpired(server, now);
        for (var e : entries.entrySet()) WebHub.INSTANCE.publishRemoved(e.getKey(), name(e.getValue()), purgeAt(e.getValue()));
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % PURGE_INTERVAL != 0) return;
        var data = get(server);
        if (!data.entries.isEmpty()) data.purgeExpired(server, System.currentTimeMillis());
    }

    /**
     * Deletes everything the server keeps for a mainframe: pairing code, PIN, devices, accounts and roles, rules,
     * scenes, production statistics, API keys, push targets, cached sections and the layout; dashboards are disconnected.
     * Used when the retention ends, for {@code /cccloud mainframe purge} and when the retention is 0.
     */
    public static void purge(MinecraftServer server, UUID mainframeId) {
        var store = WebAuthData.get(server);
        int devices = store.devices(mainframeId).size();
        store.forgetMainframe(mainframeId);
        WebAuthData.access(server).forget(mainframeId);
        ai.ezfox.cccloud.rules.RuleService.forget(server, mainframeId);
        ai.ezfox.cccloud.scene.SceneService.forget(server, mainframeId);
        ai.ezfox.cccloud.production.ProductionService.forget(server, mainframeId);
        ai.ezfox.cccloud.publicapi.ApiKeyData.get(server).keys().forgetMainframe(mainframeId);
        ai.ezfox.cccloud.push.PushService.forget(server, mainframeId);
        ai.ezfox.cccloud.network.SectionCache.get(server).forgetMainframe(mainframeId);
        ai.ezfox.cccloud.rsdevice.RsDevices.purge(server, mainframeId);
        WebHub.INSTANCE.removeMainframe(mainframeId);
        ai.ezfox.cccloud.layout.LayoutData.get(server).remove(mainframeId);
        var data = get(server);
        if (data.entries.remove(mainframeId) != null) data.setDirty();
        CCCloud.LOGGER.info("[cccloud] Deleted the data of removed mainframe {}: pairing code, PIN, {} device(s), roles, layout, rules and scenes",
            mainframeId, devices);
    }
}
