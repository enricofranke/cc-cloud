package ai.ezfox.cccloud.voxel;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.network.NetworkService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the voxel view (stage 1): one 16³ section of exposed blocks around the network. The block states
 * (section plus a one-block border) are copied on the server thread from <b>loaded</b> chunks only; filtering and
 * encoding run in the background. Results are cached and invalidated (debounced) when blocks in or next to the section
 * change.
 */
public final class VoxelService {
    public static final long DEBOUNCE_MS = 2_000;
    private static final int CACHE_SIZE = 1024;

    private record Entry(JsonObject json, long renderedAt) {
    }

    private static final Map<String, Entry> CACHE = java.util.Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > CACHE_SIZE;
        }
    });
    private static final Map<String, Long> DIRTY = new ConcurrentHashMap<>();
    private static @Nullable ExecutorService executor;

    private VoxelService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(BlockEvent.NeighborNotifyEvent.class, e -> {
            if (!(e.getLevel() instanceof ServerLevel level)) return;
            var pos = e.getPos();
            String dim = level.dimension().identifier().toString();
            long now = System.currentTimeMillis();
            // The section and its neighbors (exposure depends on the blocks next to it).
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                DIRTY.put(key(dim, (pos.getX() + dx) >> 4, (pos.getY() + dy) >> 4, (pos.getZ() + dz) >> 4), now);
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            CACHE.clear();
            DIRTY.clear();
            if (executor != null) executor.shutdownNow();
            executor = null;
        });
    }

    static String key(String dim, int cx, int cy, int cz) {
        return dim + "|" + cx + "|" + cy + "|" + cz;
    }

    /** Distance from a point to the section's box (0 inside). Pure. */
    public static double distance(int cx, int cy, int cz, double x, double y, double z) {
        double dx = Math.max(Math.max(cx * 16 - x, 0), x - (cx * 16 + 16));
        double dy = Math.max(Math.max(cy * 16 - y, 0), y - (cy * 16 + 16));
        double dz = Math.max(Math.max(cz * 16 - z, 0), z - (cz * 16 + 16));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Only sections within voxelRadius of a node or device of the mainframe's network (same dimension). */
    public static boolean allowed(UUID mainframeId, String dim, int cx, int cy, int cz) {
        var result = NetworkService.result(mainframeId);
        if (result == null) return false;
        int radius = CloudConfig.get(CloudConfig.VOXEL_RADIUS);
        for (var node : result.nodes()) {
            if (node.dim().equals(dim) && result.reachable(node.id()) && distance(cx, cy, cz, node.x(), node.y(), node.z()) <= radius) return true;
        }
        for (var device : result.devices()) {
            if (device.dim().equals(dim) && distance(cx, cy, cz, device.x(), device.y(), device.z()) <= radius) return true;
        }
        return false;
    }

    /**
     * The section document, or null (204) if its chunk is not loaded. Server thread; completes on a background thread
     * (or right away from the cache).
     */
    public static CompletableFuture<@Nullable JsonObject> section(ServerLevel level, int cx, int cy, int cz) {
        return section(level, cx, cy, cz, null);
    }

    /**
     * The section as a mainframe's dashboard sees it: ores show only where they touch air near that mainframe's nodes
     * (security review M4), so the cache is per mainframe.
     */
    public static CompletableFuture<@Nullable JsonObject> section(ServerLevel level, int cx, int cy, int cz, @Nullable UUID mainframeId) {
        String dim = level.dimension().identifier().toString();
        String area = key(dim, cx, cy, cz);
        String key = area + "|" + mainframeId;
        int[][] nodes = mainframeId == null ? new int[0][] : ai.ezfox.cccloud.map.XrayGuard.nodes(mainframeId, dim);
        var cached = CACHE.get(key);
        Long changed = DIRTY.get(area);
        var stats = ai.ezfox.cccloud.map.MapStats.VOXELS;
        if (cached != null && (changed == null || changed <= cached.renderedAt() || System.currentTimeMillis() - changed < DEBOUNCE_MS)) {
            stats.hits.increment();
            return CompletableFuture.completedFuture(cached.json());
        }
        if (cy < level.getMinSectionY() || cy > level.getMaxSectionY() || level.getChunkSource().getChunkNow(cx, cz) == null) {
            stats.empty.increment();
            return CompletableFuture.completedFuture(null);
        }
        stats.misses.increment();
        // Copy the block states (section + 1-block border) on the server thread; unloaded neighbor chunks stay unknown.
        var states = new BlockState[18 * 18 * 18];
        var known = new boolean[18 * 18 * 18];
        var pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinY(), maxY = level.getMaxY();
        for (int lx = -1; lx <= 16; lx++) {
            for (int lz = -1; lz <= 16; lz++) {
                int x = cx * 16 + lx, z = cz * 16 + lz;
                var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) continue;
                for (int ly = -1; ly <= 16; ly++) {
                    int y = cy * 16 + ly;
                    int i = (lx + 1) + 18 * (lz + 1) + 324 * (ly + 1);
                    if (y < minY || y > maxY) {
                        known[i] = true; // outside the world: air
                        continue;
                    }
                    states[i] = chunk.getBlockState(pos.set(x, y, z));
                    known[i] = true;
                }
            }
        }
        long started = System.currentTimeMillis();
        return CompletableFuture.supplyAsync(() -> {
            long t0 = System.nanoTime();
            var json = encode(dim, cx, cy, cz, states, known, nodes);
            CACHE.put(key, new Entry(json, started));
            stats.workerNanos.add(System.nanoTime() - t0);
            return json;
        }, executor());
    }

    private static synchronized ExecutorService executor() {
        if (executor == null) {
            executor = Executors.newFixedThreadPool(2, r -> {
                var t = new Thread(r, "cccloud-voxels");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        }
        return executor;
    }

    /** BlockState → web kind (map color, flags); air → null. Off-thread safe (no level access). */
    static VoxelSection.@Nullable Kind kind(@Nullable BlockState state, Map<BlockState, VoxelSection.Kind> cache) {
        if (state == null || state.isAir()) return null;
        return cache.computeIfAbsent(state, s -> {
            var color = s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            int argb = color == MapColor.NONE ? 0xFF808080 : color.calculateARGBColor(MapColor.Brightness.HIGH);
            int flags = 0;
            if (!s.canOcclude()) flags |= VoxelSection.TRANSPARENT;
            if (!s.getFluidState().isEmpty()) flags |= VoxelSection.FLUID;
            if (s.getLightEmission() > 0) flags |= VoxelSection.EMISSIVE;
            return new VoxelSection.Kind(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(), argb, flags);
        });
    }

    static JsonObject encode(String dim, int cx, int cy, int cz, BlockState[] states, boolean[] known) {
        return encode(dim, cx, cy, cz, states, known, new int[0][]);
    }

    static JsonObject encode(String dim, int cx, int cy, int cz, BlockState[] states, boolean[] known, int[][] nodes) {
        var kinds = new HashMap<BlockState, VoxelSection.Kind>();
        var encoded = VoxelSection.encode(new VoxelSection.Lookup() {
            @Override
            public VoxelSection.@Nullable Kind at(int x, int y, int z) {
                var state = states[(x + 1) + 18 * (z + 1) + 324 * (y + 1)];
                // ores look like stone unless they touch air near one of the mainframe's own nodes (inside the section only)
                if (x >= 0 && x < 16 && y >= 0 && y < 16 && z >= 0 && z < 16 && ai.ezfox.cccloud.map.XrayGuard.isOre(state)
                    && !(touchesAir(states, known, x, y, z) && ai.ezfox.cccloud.map.XrayGuard.nearNode(nodes, cx * 16 + x, cy * 16 + y, cz * 16 + z))) {
                    state = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
                }
                return kind(state, kinds);
            }

            @Override
            public boolean known(int x, int y, int z) {
                return known[(x + 1) + 18 * (z + 1) + 324 * (y + 1)];
            }
        });
        var json = new JsonObject();
        json.addProperty("dim", dim);
        json.addProperty("cx", cx);
        json.addProperty("cy", cy);
        json.addProperty("cz", cz);
        var palette = new JsonArray();
        for (var kind : encoded.palette()) {
            var p = new JsonObject();
            p.addProperty("id", kind.id());
            p.addProperty("color", kind.color());
            p.addProperty("flags", kind.flags());
            palette.add(p);
        }
        json.add("palette", palette);
        json.addProperty("runs", encoded.runs());
        json.addProperty("exposed", encoded.exposed());
        return json;
    }

    private static boolean touchesAir(BlockState[] states, boolean[] known, int x, int y, int z) {
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (var d : dirs) {
            int i = (x + d[0] + 1) + 18 * (z + d[2] + 1) + 324 * (y + d[1] + 1);
            if (known[i] && (states[i] == null || states[i].isAir())) return true;
        }
        return false;
    }

    public static void clearForTest() {
        CACHE.clear();
        DIRTY.clear();
    }
}
