package ai.ezfox.cccloud.network;

import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.bridge.BridgeHost;
import ai.ezfox.cccloud.web.WebHub;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;

/**
 * Drops dashboard sections whose block is gone without the normal break path: {@code /setblock} and {@code /fill}
 * skip block-entity side effects, and explosions or block-moving mods may remove a block entity silently. Breaking
 * an uplink normally removes its section at once ({@code preRemoveSideEffects}); this catches the rest.
 * <p>
 * A section is an orphan when its block position is loaded but no uplink or bridge with that section id is there
 * (and none registered elsewhere) for {@link #graceTicks} – long enough for a mod that picks a block up and puts it
 * down again. Sources: the linked sections of every loaded mainframe (also orphans from before this check existed),
 * and block entities that were removed while their chunk stayed loaded (in case the mainframe is not loaded).
 * Unloaded positions never count: an unloaded section is shown greyed out, not removed. Server thread only.
 */
public final class OrphanSections {
    /** How long a section may be missing from its loaded position before it is dropped (5 minutes). */
    public static final int DEFAULT_GRACE_TICKS = 5 * 60 * 20;
    static final int SWEEP_INTERVAL = 200;
    static volatile int graceTicks = DEFAULT_GRACE_TICKS;

    private record Suspect(GlobalPos pos, UUID mainframeId, long since) {
    }

    /** Removed block entities whose chunk stayed loaded, by section id. */
    private static final Map<UUID, Suspect> REMOVED = new HashMap<>();
    /** Linked sections of loaded mainframes that are missing from their loaded position: since when. */
    private static final Map<UUID, Long> MISSING = new HashMap<>();

    private OrphanSections() {
    }

    /** A section's block entity was removed while its chunk stayed loaded (not a chunk unload, not a normal break). */
    public static void removedInPlace(GlobalPos pos, UUID sectionId, UUID mainframeId, long tick) {
        REMOVED.putIfAbsent(sectionId, new Suspect(pos, mainframeId, tick));
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % SWEEP_INTERVAL != 0) return;
        sweep(server, now, graceTicks);
    }

    /** One pass (public for GameTests, which use a short grace). */
    public static void sweep(MinecraftServer server, long now, int grace) {
        // linked sections of loaded mainframes
        var seen = new java.util.HashSet<UUID>();
        for (var mainframe : java.util.List.copyOf(CloudNetwork.mainframes())) {
            if (mainframe.isRemoved()) continue;
            for (var e : java.util.List.copyOf(mainframe.getLinkedUplinks().entrySet())) {
                UUID id = e.getKey();
                if (!missing(server, id, e.getValue())) {
                    MISSING.remove(id);
                    continue;
                }
                seen.add(id);
                long since = MISSING.computeIfAbsent(id, k -> now);
                if (now - since >= grace) {
                    MISSING.remove(id);
                    REMOVED.remove(id);
                    ai.ezfox.cccloud.CloudLog.LOGGER.info("[cccloud] Section {} of {} is gone from {} – removed from the dashboard", id,
                        mainframe.getMainframeId(), e.getValue().pos());
                    mainframe.unlinkUplink(id);
                }
            }
        }
        MISSING.keySet().retainAll(seen);
        // removed block entities whose mainframe is not loaded
        for (var it = REMOVED.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            UUID id = e.getKey();
            var s = e.getValue();
            if (!missing(server, id, s.pos())) {
                if (CloudNetwork.section(id) != null || ai.ezfox.cccloud.rsdevice.RsDevices.loaded(id) != null || !loaded(server, s.pos())) it.remove();
                continue;
            }
            if (now - s.since() < grace) continue;
            it.remove();
            var mainframe = CloudNetwork.mainframe(s.mainframeId());
            if (mainframe != null) mainframe.unlinkUplink(id);
            else WebHub.INSTANCE.publishUplinkRemoved(s.mainframeId(), id.toString());
            ai.ezfox.cccloud.CloudLog.LOGGER.info("[cccloud] Section {} of {} is gone from {} – removed from the dashboard", id, s.mainframeId(), s.pos().pos());
        }
    }

    /** True if the section is registered nowhere and its (loaded) position holds no block entity with that id. */
    static boolean missing(MinecraftServer server, UUID id, GlobalPos pos) {
        if (CloudNetwork.section(id) != null || ai.ezfox.cccloud.rsdevice.RsDevices.loaded(id) != null) return false;
        if (!loaded(server, pos)) return false;
        var level = server.getLevel(pos.dimension());
        // CHECK: never creates a block entity as a side effect of looking
        var be = level == null ? null : level.getChunkAt(pos.pos()).getBlockEntity(pos.pos(), net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
        if (be instanceof CloudUplinkBlockEntity uplink && id.equals(uplink.getUplinkId())) return false;
        if (be instanceof BridgeHost host && id.equals(host.bridgeSection().sectionId())) return false;
        if (be instanceof ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity device && id.equals(device.deviceId())) return false;
        return true;
    }

    private static boolean loaded(MinecraftServer server, GlobalPos pos) {
        var level = server.getLevel(pos.dimension());
        BlockPos p = pos.pos();
        return level != null && level.isLoaded(p) && level.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4);
    }

    public static void clear() {
        REMOVED.clear();
        MISSING.clear();
    }
}
