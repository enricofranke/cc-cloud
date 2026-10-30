package ai.ezfox.cccloud.voxel;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.map.MapStats;
import ai.ezfox.cccloud.map.XrayGuard;
import ai.ezfox.cccloud.network.NetworkService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the voxel view: 16³ sections of exposed blocks around the network.
 * <ul>
 *   <li><b>Server thread</b> ({@link #encode}): only copies the block-state containers of the requested sections and
 *       their six neighbors from <b>loaded</b> chunks ({@code PalettedContainer.copy()}, microseconds each; air-only
 *       sections are not copied). Nothing is loaded or generated.</li>
 *   <li><b>Background</b> (2 threads): hashes the copied containers (plus the mainframe's nodes that decide ore
 *       visibility) and answers from the cache when that input hash is unchanged; otherwise builds the section with the
 *       exposed-only filter and the X-ray guard and caches it.</li>
 * </ul>
 * The cache is validated by content, so every block change (whatever caused it: players, machines, commands, block
 * movers) shows on the next request, and unchanged sections are never rebuilt.
 */
public final class VoxelService {
    private static final int CACHE_SIZE = 8192;
    /** Bump when the build logic changes (invalidates input hashes). */
    private static final int LOGIC_VERSION = 2;

    private record Entry(long input, VoxelSection.Encoded encoded) {
    }

    /** What the web sees of a block state; {@code kind} null for air. */
    private record Info(VoxelSection.@Nullable Kind kind, boolean ore) {
    }

    private static final Map<String, Entry> CACHE = java.util.Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > CACHE_SIZE;
        }
    });
    private static final Map<BlockState, Info> INFO = new ConcurrentHashMap<>();
    /** Marker for a section that only holds air (not copied). */
    private static final Object AIR = new Object();
    /** The encoding of an air-only section. */
    private static final VoxelSection.Encoded AIR_SECTION = VoxelSection.encodeGrid(new int[VoxelSection.GRID * VoxelSection.GRID * VoxelSection.GRID], List.of());
    private static @Nullable ExecutorService executor;

    private VoxelService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            CACHE.clear();
            INFO.clear();
            if (executor != null) executor.shutdownNow();
            executor = null;
        });
    }

    static String key(String dim, int cx, int cy, int cz, @Nullable UUID mainframe) {
        return dim + "|" + cx + "|" + cy + "|" + cz + "|" + mainframe;
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
     * The section document of the single-section endpoint, or null (204) if its chunk is not loaded. Server thread;
     * completes on a background thread.
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
        return encode(level, List.of(new int[]{cx, cy, cz}), mainframeId).thenApply(list -> {
            var encoded = list.getFirst();
            return encoded == null ? null : json(dim, cx, cy, cz, encoded);
        });
    }

    /** The single-section JSON of the stage-1 endpoint. */
    static JsonObject json(String dim, int cx, int cy, int cz, VoxelSection.Encoded encoded) {
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
        json.addProperty("runs", encoded.runsBase64());
        json.addProperty("exposed", encoded.exposed());
        return json;
    }

    /** The copied block states a request needs (server thread → background). */
    private record Snapshot(String dim, int minSectionY, int maxSectionY, Map<Long, Object> containers, int[][] nodes) {
        /** A section's container, {@link #AIR}, or null when its chunk is not loaded. */
        @Nullable Object at(int cx, int cy, int cz) {
            if (cy < minSectionY || cy > maxSectionY) return AIR; // outside the world: air
            return containers.get(SectionPos.asLong(cx, cy, cz));
        }
    }

    /**
     * Encodes sections (each {cx, cy, cz}; access must already be checked). Server thread: copies the needed containers;
     * the returned future completes on a background thread with one entry per section, null where the chunk is not
     * loaded or the section is outside the world.
     */
    public static CompletableFuture<List<VoxelSection.@Nullable Encoded>> encode(ServerLevel level, List<int[]> sections, @Nullable UUID mainframeId) {
        String dim = level.dimension().identifier().toString();
        int minY = level.getMinSectionY(), maxY = level.getMaxSectionY();
        var containers = new HashMap<Long, Object>();
        for (var s : sections) {
            if (s[1] < minY || s[1] > maxY) continue;
            copy(level, containers, s[0], s[1], s[2], minY, maxY);
            copy(level, containers, s[0] + 1, s[1], s[2], minY, maxY);
            copy(level, containers, s[0] - 1, s[1], s[2], minY, maxY);
            copy(level, containers, s[0], s[1] + 1, s[2], minY, maxY);
            copy(level, containers, s[0], s[1] - 1, s[2], minY, maxY);
            copy(level, containers, s[0], s[1], s[2] + 1, minY, maxY);
            copy(level, containers, s[0], s[1], s[2] - 1, minY, maxY);
        }
        int[][] nodes = mainframeId == null ? new int[0][] : XrayGuard.nodes(mainframeId, dim);
        var snapshot = new Snapshot(dim, minY, maxY, containers, nodes);
        return CompletableFuture.supplyAsync(() -> {
            var t0 = MapStats.start();
            var out = new ArrayList<VoxelSection.@Nullable Encoded>(sections.size());
            for (var s : sections) out.add(build(snapshot, s[0], s[1], s[2], mainframeId));
            MapStats.VOXELS.worker(t0);
            return out;
        }, executor());
    }

    /** Server thread: copies one section's block states if its chunk is loaded (never loads or generates). */
    private static void copy(ServerLevel level, Map<Long, Object> into, int cx, int cy, int cz, int minY, int maxY) {
        if (cy < minY || cy > maxY) return;
        long key = SectionPos.asLong(cx, cy, cz);
        if (into.containsKey(key)) return;
        var chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) {
            into.put(key, null);
            return;
        }
        var section = chunk.getSection(chunk.getSectionIndexFromSectionY(cy));
        into.put(key, section.hasOnlyAir() ? AIR : section.getStates().copy());
    }

    /** Background: one section from the snapshot, from the cache when its input is unchanged. */
    private static VoxelSection.@Nullable Encoded build(Snapshot snap, int cx, int cy, int cz, @Nullable UUID mainframeId) {
        var stats = MapStats.VOXELS;
        Object self = snap.at(cx, cy, cz);
        if (self == null || cy < snap.minSectionY() || cy > snap.maxSectionY()) {
            stats.empty.increment();
            return null;
        }
        if (self == AIR) {
            stats.hits.increment();
            return AIR_SECTION; // nothing to show, whatever is around
        }
        Object[] around = {snap.at(cx + 1, cy, cz), snap.at(cx - 1, cy, cz), snap.at(cx, cy + 1, cz), snap.at(cx, cy - 1, cz),
            snap.at(cx, cy, cz + 1), snap.at(cx, cy, cz - 1)};
        int[][] near = nearNodes(snap.nodes(), cx, cy, cz);
        long input = inputHash(self, around, near);
        String key = key(snap.dim(), cx, cy, cz, mainframeId);
        var cached = CACHE.get(key);
        if (cached != null && cached.input() == input) {
            stats.hits.increment();
            return cached.encoded();
        }
        stats.misses.increment();
        var encoded = buildGrid(self, around, near, cx, cy, cz);
        CACHE.put(key, new Entry(input, encoded));
        return encoded;
    }

    /** Nodes whose ore radius reaches into the section (only they change what the section shows). */
    private static int[][] nearNodes(int[][] nodes, int cx, int cy, int cz) {
        var list = new ArrayList<int[]>();
        for (var n : nodes) if (distance(cx, cy, cz, n[0], n[1], n[2]) <= XrayGuard.ORE_RADIUS + 1) list.add(n);
        return list.toArray(new int[0][]);
    }

    private static final ThreadLocal<FriendlyByteBuf> HASH_BUFFER = ThreadLocal.withInitial(() -> new FriendlyByteBuf(Unpooled.buffer(16 * 1024)));

    /** Hash of everything the section's encoding depends on: its states, the six neighbors, the nearby nodes. */
    @SuppressWarnings("unchecked")
    private static long inputHash(Object self, Object[] around, int[][] near) {
        var buf = HASH_BUFFER.get();
        buf.clear();
        buf.writeInt(LOGIC_VERSION);
        for (int i = -1; i < around.length; i++) {
            Object c = i < 0 ? self : around[i];
            if (c == null) buf.writeByte(0);
            else if (c == AIR) buf.writeByte(1);
            else {
                buf.writeByte(2);
                ((PalettedContainer<BlockState>) c).write(buf);
            }
        }
        for (var n : near) {
            buf.writeInt(n[0]);
            buf.writeInt(n[1]);
            buf.writeInt(n[2]);
        }
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update(buf.nioBuffer());
            byte[] d = md.digest();
            long h = 0;
            for (int i = 0; i < 8; i++) h = (h << 8) | (d[i] & 0xFF);
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Fills the bordered grid from the copied containers and encodes it (exposed-only, ore guard). */
    @SuppressWarnings("unchecked")
    private static VoxelSection.Encoded buildGrid(Object self, Object[] around, int[][] near, int cx, int cy, int cz) {
        var grid = new int[VoxelSection.GRID * VoxelSection.GRID * VoxelSection.GRID];
        var kinds = new ArrayList<VoxelSection.Kind>();
        var kindIds = new HashMap<VoxelSection.Kind, Integer>();
        var stateIds = new IdentityHashMap<BlockState, Integer>();
        var ores = new IdentityHashMap<BlockState, Boolean>();
        // the section itself
        if (self != AIR) {
            var c = (PalettedContainer<BlockState>) self;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                var state = c.get(x, y, z);
                int id = gridId(state, stateIds, kinds, kindIds);
                if (id > 0 && info(state).ore()) ores.put(state, Boolean.TRUE);
                grid[VoxelSection.gridIndex(x, y, z)] = id;
            }
        }
        // the one-block border from the six neighbors: +x, -x, +y, -y, +z, -z
        for (int f = 0; f < 6; f++) {
            Object n = around[f];
            for (int a = 0; a < 16; a++) for (int b = 0; b < 16; b++) {
                int x, y, z, nx, ny, nz;
                switch (f) {
                    case 0 -> { x = 16; y = a; z = b; nx = 0; ny = a; nz = b; }
                    case 1 -> { x = -1; y = a; z = b; nx = 15; ny = a; nz = b; }
                    case 2 -> { x = a; y = 16; z = b; nx = a; ny = 0; nz = b; }
                    case 3 -> { x = a; y = -1; z = b; nx = a; ny = 15; nz = b; }
                    case 4 -> { x = a; y = b; z = 16; nx = a; ny = b; nz = 0; }
                    default -> { x = a; y = b; z = -1; nx = a; ny = b; nz = 15; }
                }
                int g = VoxelSection.gridIndex(x, y, z);
                if (n == null) grid[g] = VoxelSection.UNKNOWN;
                else if (n != AIR) grid[g] = gridId(((PalettedContainer<BlockState>) n).get(nx, ny, nz), stateIds, kinds, kindIds);
            }
        }
        // ores look like stone unless they touch air near one of the mainframe's own nodes (inside the section only)
        if (!ores.isEmpty()) {
            int stone = gridId(Blocks.STONE.defaultBlockState(), stateIds, kinds, kindIds);
            var oreIds = new java.util.HashSet<Integer>();
            for (var state : ores.keySet()) oreIds.add(stateIds.get(state));
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int g = VoxelSection.gridIndex(x, y, z);
                if (!oreIds.contains(grid[g])) continue;
                if (!(touchesAir(grid, g) && XrayGuard.nearNode(near, cx * 16 + x, cy * 16 + y, cz * 16 + z))) grid[g] = stone;
            }
        }
        return VoxelSection.encodeGrid(grid, kinds);
    }

    private static final int[] STEPS = {1, -1, VoxelSection.GRID, -VoxelSection.GRID, VoxelSection.GRID * VoxelSection.GRID, -VoxelSection.GRID * VoxelSection.GRID};

    private static boolean touchesAir(int[] grid, int g) {
        for (int step : STEPS) if (grid[g + step] == 0) return true;
        return false;
    }

    /** Grid id of a state: 0 for air, else 1-based into {@code kinds} (equal kinds share an id). */
    private static int gridId(BlockState state, IdentityHashMap<BlockState, Integer> stateIds, List<VoxelSection.Kind> kinds, Map<VoxelSection.Kind, Integer> kindIds) {
        Integer id = stateIds.get(state);
        if (id != null) return id;
        var kind = info(state).kind();
        if (kind == null) id = 0;
        else {
            id = kindIds.get(kind);
            if (id == null) {
                kinds.add(kind);
                id = kinds.size();
                kindIds.put(kind, id);
            }
        }
        stateIds.put(state, id);
        return id;
    }

    /** BlockState → web kind (map color, flags) and ore flag; cached. Off-thread safe (no level access). */
    private static Info info(BlockState state) {
        var info = INFO.get(state);
        if (info == null) {
            info = new Info(kind(state), XrayGuard.isOre(state));
            INFO.put(state, info);
        }
        return info;
    }

    /** BlockState → web kind (map color, flags); air → null. Off-thread safe (no level access). */
    static VoxelSection.@Nullable Kind kind(@Nullable BlockState state) {
        if (state == null || state.isAir()) return null;
        var color = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        int argb = color == MapColor.NONE ? 0xFF808080 : color.calculateARGBColor(MapColor.Brightness.HIGH);
        int flags = 0;
        if (!state.canOcclude()) flags |= VoxelSection.TRANSPARENT;
        if (!state.getFluidState().isEmpty()) flags |= VoxelSection.FLUID;
        if (state.getLightEmission() > 0) flags |= VoxelSection.EMISSIVE;
        return new VoxelSection.Kind(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), argb, flags);
    }

    /** The background executor (2 low-priority threads). */
    public static synchronized Executor executor() {
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

    public static void clearForTest() {
        CACHE.clear();
    }
}
