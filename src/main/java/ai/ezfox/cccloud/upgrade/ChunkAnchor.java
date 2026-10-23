package ai.ezfox.cccloud.upgrade;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import org.jspecify.annotations.Nullable;

/**
 * Chunk Anchor upgrade card (optional, server config {@code chunkAnchor}, default off): keeps the mainframe's chunk and
 * the chunks of its linked devices loaded (ticking) with NeoForge's ticket API. Tickets are owned by the mainframe id;
 * the mainframe stores its anchored chunks, so stale tickets are removed after a restart. Costs FE
 * ({@code anchorBaseFE} + {@code anchorPerChunkFE} per chunk) and is limited per owner by the permission node
 * {@code cccloud.limit.anchorChunks} (default 9, counted over all of the owner's mainframes).
 */
public final class ChunkAnchor {
    /** One anchored chunk. */
    public record Key(String dim, int x, int z) {
        public long packed() {
            return ChunkPos.pack(x, z);
        }
    }

    private static final TicketController CONTROLLER = new TicketController(Identifier.fromNamespaceAndPath(CCCloud.MODID, "chunk_anchor"), (level, helper) -> {
        // Disabled on this server: drop every ticket that is still saved. Enabled: keep them, so an anchored mainframe
        // loads again after a restart (it then reconciles its tickets with what it stored).
        if (!CloudConfig.get(CloudConfig.CHUNK_ANCHOR)) {
            for (var owner : List.copyOf(helper.getEntityTickets().keySet())) helper.removeAllTickets(owner);
        }
    });

    /** mainframe id → anchored chunks; owner per mainframe for the per-player limit. Server thread only. */
    private static final Map<UUID, Set<Key>> ACTIVE = new HashMap<>();
    private static final Map<UUID, UUID> OWNERS = new HashMap<>();

    private ChunkAnchor() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterTicketControllersEvent.class, e -> e.register(CONTROLLER));
    }

    public static void clear() {
        ACTIVE.clear();
        OWNERS.clear();
    }

    /** Anchored chunks of a mainframe (0 when inactive). */
    public static int chunks(UUID mainframeId) {
        var set = ACTIVE.get(mainframeId);
        return set == null ? 0 : set.size();
    }

    public static Set<Key> active(UUID mainframeId) {
        return Set.copyOf(ACTIVE.getOrDefault(mainframeId, Set.of()));
    }

    /** Called by the mainframe after loading: the chunks it anchored before (to remove stale tickets). */
    public static void restore(UUID mainframeId, Set<Key> saved) {
        if (!saved.isEmpty()) ACTIVE.putIfAbsent(mainframeId, new LinkedHashSet<>(saved));
    }

    /** FE per tick of an anchor with {@code chunks} chunks (0 = inactive). */
    public static long cost(int chunks) {
        if (chunks <= 0) return 0;
        return (long) CloudConfig.get(CloudConfig.ANCHOR_BASE_FE) + (long) CloudConfig.get(CloudConfig.ANCHOR_PER_CHUNK_FE) * chunks;
    }

    /**
     * Brings the tickets of a mainframe in line: anchored = mainframe chunk first, then its linked devices' chunks, cut
     * at the owner's limit. {@code active=false} releases everything.
     *
     * @return true if the anchored set changed (the mainframe saves it)
     */
    public static boolean update(CloudMainframeBlockEntity mainframe, boolean active, int ownerLimit) {
        if (!(mainframe.getLevel() instanceof ServerLevel level)) return false;
        var id = mainframe.getMainframeId();
        var desired = new LinkedHashSet<Key>();
        if (active) {
            var owner = mainframe.getOwner();
            int used = 0;
            for (var e : ACTIVE.entrySet()) {
                if (!e.getKey().equals(id) && owner != null && owner.equals(OWNERS.get(e.getKey()))) used += e.getValue().size();
            }
            int budget = Math.max(0, ownerLimit - used);
            var candidates = new ArrayList<Key>();
            var p = mainframe.getBlockPos();
            candidates.add(new Key(dim(level), p.getX() >> 4, p.getZ() >> 4));
            for (var pos : mainframe.getLinkedUplinks().values()) {
                candidates.add(new Key(pos.dimension().identifier().toString(), pos.pos().getX() >> 4, pos.pos().getZ() >> 4));
            }
            for (var key : candidates) {
                if (desired.size() >= budget) break;
                desired.add(key);
            }
            if (owner != null) OWNERS.put(id, owner);
        }
        var current = ACTIVE.getOrDefault(id, Set.of());
        if (current.equals(desired)) return false;
        var server = level.getServer();
        for (var key : current) if (!desired.contains(key)) force(server, id, key, false);
        for (var key : desired) if (!current.contains(key)) force(server, id, key, true);
        if (desired.isEmpty()) {
            ACTIVE.remove(id);
            OWNERS.remove(id);
        } else {
            ACTIVE.put(id, desired);
        }
        CCCloud.LOGGER.debug("[cccloud] {} anchors {} chunk(s)", mainframe.getDisplayName(), desired.size());
        return true;
    }

    /** The mainframe was broken: release all of its tickets. */
    public static void release(MinecraftServer server, UUID mainframeId) {
        var current = ACTIVE.remove(mainframeId);
        OWNERS.remove(mainframeId);
        if (current != null) for (var key : current) force(server, mainframeId, key, false);
    }

    private static void force(MinecraftServer server, UUID owner, Key key, boolean add) {
        var level = level(server, key.dim());
        if (level == null) return;
        CONTROLLER.forceChunk(level, owner, key.x(), key.z(), add, true);
    }

    private static @Nullable ServerLevel level(MinecraftServer server, String dim) {
        var id = Identifier.tryParse(dim);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static String dim(Level level) {
        return level.dimension().identifier().toString();
    }

    /** Is the chunk at block x/z of a level anchored by any mainframe (for tests and the screen). */
    public static boolean isAnchored(Level level, int blockX, int blockZ) {
        var key = new Key(dim(level), blockX >> 4, blockZ >> 4);
        for (var set : ACTIVE.values()) if (set.contains(key)) return true;
        return false;
    }
}
