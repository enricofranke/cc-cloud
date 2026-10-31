package ai.ezfox.cccloud.voxel;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.block.MainframeRetention;
import ai.ezfox.cccloud.map.MapStats;
import ai.ezfox.cccloud.map.XrayGuard;
import ai.ezfox.cccloud.network.NetworkService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
 * <p>
 * Chunks that are not loaded show their <b>last known</b> terrain (docs/SPEC.md "Voxel view – last known terrain"): every
 * built section also goes to the {@link VoxelDisk disk cache}, and a chunk in a mainframe's {@link VoxelAreas view area}
 * that unloads is copied once more, so the cache fills without anyone looking. Last known sections never show an ore:
 * what is shown near a mainframe's nodes depends on those nodes, which may have changed since, so memory and disk keep an
 * ore-free variant for this path, and that is the only variant that is ever stored (docs/SECURITY.md).
 */
public final class VoxelService {
    private static final int CACHE_SIZE = 8192;
    /** Bump when the build logic changes (invalidates input hashes and the disk cache). */
    private static final int LOGIC_VERSION = 3;
    /** Time the unload snapshots may take per tick; beyond it they leave out the neighbors in other chunks. */
    static final long TICK_BUDGET_NANOS = 500_000;
    /** ... and beyond this limit the chunks wait for later ticks. */
    static final long TICK_LIMIT_NANOS = 2_000_000;
    /** Unload snapshots waiting for the background threads at most; more are dropped. */
    static final int MAX_SNAPSHOTS = 512;
    /** ... and at server stop, when the loaded rest of every view area is copied at once. */
    private static final int MAX_STOP_SNAPSHOTS = 1024;
    private static final long DAY_MS = 86_400_000L;

    /**
     * A built section: {@code input} validates it for the live path, {@code encoded} is what the live view shows and
     * {@code stored} the ore-free variant for the last known path (the same encoding when no ore shows).
     */
    private record Entry(long input, VoxelSection.Encoded encoded, VoxelDisk.Stored stored) {
    }

    /** The two variants of a build (the same object when no ore shows). */
    private record Built(VoxelSection.Encoded encoded, VoxelSection.Encoded oreFree) {
    }

    /** Hashes of a section's blocks, of them with the touching neighbor layers, and which neighbors were loaded. */
    private record Hashes(long self, long content, int known) {
    }

    /** A section for the dashboard; {@code lastSeen}: from the caches, because it cannot be read live right now. */
    public record Served(VoxelSection.Encoded data, boolean lastSeen) {
    }

    /**
     * What a mainframe may see in a dimension right now: {@code live} opens the chunks as well, {@code known} (the
     * {@link VoxelAreas last known area}) only the caches. {@code unknown}: the mainframe is not loaded and no area is
     * known yet, so nothing can be said about a section (nothing is served, and nothing is called out of range).
     */
    public record View(double[][] live, double[][] known, boolean unknown) {
        public boolean liveAt(int cx, int cy, int cz) {
            return allowed(live, cx, cy, cz);
        }

        public boolean knownAt(int cx, int cy, int cz) {
            return allowed(known, cx, cy, cz);
        }
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
    private static final int[][] NO_NODES = new int[0][];
    /** The encoding of an air-only section. */
    private static final VoxelSection.Encoded AIR_SECTION = VoxelSection.encodeGrid(new int[VoxelSection.GRID * VoxelSection.GRID * VoxelSection.GRID], List.of());
    private static @Nullable ExecutorService executor;
    private static @Nullable VoxelDisk disk;
    private static final AtomicInteger SNAPSHOTS = new AtomicInteger();
    /** Unloaded chunks over the tick limit, at most {@link #MAX_SNAPSHOTS}. Server thread only. */
    private static final ArrayDeque<Deferred> DEFERRED = new ArrayDeque<>();
    private static boolean deferAllForTest;
    private static int budgetTick;
    private static long budgetUsed;
    private static long nextSweep = Long.MAX_VALUE;

    private VoxelService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, VoxelService::chunkUnloaded);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> snapshotLoaded(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            ExecutorService pool;
            VoxelDisk store;
            synchronized (VoxelService.class) {
                pool = executor;
                store = disk;
                executor = null;
                disk = null;
            }
            // what the last chunks were copied for goes to disk first
            if (pool != null) {
                pool.shutdown();
                try {
                    pool.awaitTermination(2, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                pool.shutdownNow();
            }
            if (store != null) store.close(2000);
            DEFERRED.clear();
            CACHE.clear();
            INFO.clear();
            VoxelAreas.clear();
            nextSweep = Long.MAX_VALUE;
        });
    }

    /** Server start: the view areas and the saved network maps come back, the disk cache is swept. */
    public static void serverStarted(MinecraftServer server) {
        var store = disk(server);
        VoxelAreas.load(server, store);
        store.sweep();
        nextSweep = System.currentTimeMillis() + DAY_MS;
    }

    private static void tick(MinecraftServer server) {
        VoxelAreas.tick();
        snapshotDeferred(server);
        if (server.getTickCount() % 1200 != 0) return;
        long now = System.currentTimeMillis();
        if (now < nextSweep) return;
        nextSweep = now + DAY_MS;
        disk(server).sweep();
    }

    /** Config reload of the {@code voxelCache*} values: sweeps with the new caps right away (background thread). */
    public static void sweepNow(MinecraftServer server) {
        disk(server).sweep();
    }

    /** This world's disk cache. Any thread. */
    static synchronized VoxelDisk disk(MinecraftServer server) {
        if (disk == null) {
            var store = new VoxelDisk(server.getWorldPath(LevelResource.ROOT).resolve("cccloud").resolve("voxels"), LOGIC_VERSION, VoxelService::limits);
            MapStats.voxelDiskBytes = store::bytesOnDisk;
            disk = store;
        }
        return disk;
    }

    private static VoxelDisk.Limits limits() {
        return new VoxelDisk.Limits(CloudConfig.get(CloudConfig.VOXEL_CACHE_MAX_AGE_DAYS) * DAY_MS, CloudConfig.get(CloudConfig.VOXEL_CACHE_MAINFRAME_MB) * 1048576L,
            CloudConfig.get(CloudConfig.VOXEL_CACHE_TOTAL_MB) * 1048576L);
    }

    /**
     * Mainframe purged (docs/SPEC.md "Mainframe removal"): its sections go from memory at once, its folder under
     * {@code <world>/cccloud/voxels} on the background thread.
     */
    public static void purgeMainframe(MinecraftServer server, UUID mainframeId) {
        String suffix = "|" + mainframeId;
        synchronized (CACHE) {
            CACHE.keySet().removeIf(k -> k.endsWith(suffix));
        }
        VoxelAreas.forget(mainframeId);
        disk(server).purge(mainframeId);
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

    /**
     * The live view and the last known area of a mainframe in a dimension (computed once per request). A removed mainframe
     * has neither. Server thread.
     */
    public static View view(MinecraftServer server, UUID mainframeId, String dim) {
        var live = viewPoints(mainframeId, dim);
        boolean removed = MainframeRetention.get(server).entry(mainframeId) != null;
        boolean unknown = !removed && NetworkService.result(mainframeId) == null && !VoxelAreas.known(mainframeId);
        return new View(live, removed ? new double[0][] : VoxelAreas.points(mainframeId, dim), unknown);
    }

    /**
     * Positions of the reachable nodes and the devices of the mainframe's network in this dimension, whose voxelRadius
     * opens the view (computed once per request). Server thread.
     */
    public static double[][] viewPoints(UUID mainframeId, String dim) {
        var result = NetworkService.result(mainframeId);
        if (result == null) return new double[0][];
        var list = new ArrayList<double[]>();
        for (var node : result.nodes()) {
            if (node.dim().equals(dim) && result.reachable(node.id())) list.add(new double[]{node.x(), node.y(), node.z()});
        }
        for (var device : result.devices()) {
            if (device.dim().equals(dim)) list.add(new double[]{device.x(), device.y(), device.z()});
        }
        return list.toArray(new double[0][]);
    }

    /** A section within voxelRadius (box to point) of one of the points. */
    public static boolean allowed(double[][] points, int cx, int cy, int cz) {
        int radius = CloudConfig.get(CloudConfig.VOXEL_RADIUS);
        for (var p : points) if (distance(cx, cy, cz, p[0], p[1], p[2]) <= radius) return true;
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
        return section(level, cx, cy, cz, mainframeId, false);
    }

    /** {@code lastKnown}: the section is only in the mainframe's last known area, so it comes from the caches only. */
    public static CompletableFuture<@Nullable JsonObject> section(ServerLevel level, int cx, int cy, int cz, @Nullable UUID mainframeId, boolean lastKnown) {
        String dim = level.dimension().identifier().toString();
        var only = new BitSet();
        only.set(0, lastKnown);
        return serve(level, List.of(new int[]{cx, cy, cz}), only, mainframeId).thenApply(list -> {
            var served = list.getFirst();
            return served == null ? null : json(dim, cx, cy, cz, served);
        });
    }

    /** The single-section JSON of the stage-1 endpoint. */
    static JsonObject json(String dim, int cx, int cy, int cz, Served served) {
        var encoded = served.data();
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
        if (served.lastSeen()) json.addProperty("old", true);
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

    /** What one background job read from and built for the disk cache, per column ({@link #column}). */
    private static final class Batch {
        final Map<Long, Map<Integer, VoxelDisk.Stored>> read = new HashMap<>();
        final Map<Long, Map<Integer, VoxelDisk.Stored>> built = new HashMap<>();

        void store(@Nullable VoxelDisk store, String dim, @Nullable UUID mainframeId) {
            if (store == null || mainframeId == null) return;
            for (var e : built.entrySet()) store.put(mainframeId, dim, (int) (e.getKey() >> 32), (int) (long) e.getKey(), e.getValue());
        }
    }

    private static long column(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /**
     * Encodes sections (each {cx, cy, cz}; access must already be checked). Server thread: copies the needed containers;
     * the returned future completes on a background thread with one entry per section, null where the chunk is not
     * loaded or the section is outside the world.
     */
    public static CompletableFuture<List<VoxelSection.@Nullable Encoded>> encode(ServerLevel level, List<int[]> sections, @Nullable UUID mainframeId) {
        return serve(level, sections, new BitSet(), mainframeId).thenApply(list -> {
            var out = new ArrayList<VoxelSection.@Nullable Encoded>(list.size());
            for (var served : list) out.add(served == null ? null : served.data());
            return out;
        });
    }

    /**
     * Sections for the dashboard (each {cx, cy, cz}; access must already be checked). A section whose chunk is loaded is
     * read as in {@link #encode}, one whose chunk is not comes from the caches; {@code lastKnown} marks the sections that
     * are only in the last known area, which come from the caches whether their chunk is loaded or not. Null: nothing
     * known. Server thread; completes on a background thread.
     */
    public static CompletableFuture<List<@Nullable Served>> serve(ServerLevel level, List<int[]> sections, BitSet lastKnown, @Nullable UUID mainframeId) {
        String dim = level.dimension().identifier().toString();
        int minY = level.getMinSectionY(), maxY = level.getMaxSectionY();
        var containers = new HashMap<Long, Object>();
        for (int i = 0; i < sections.size(); i++) {
            var s = sections.get(i);
            if (lastKnown.get(i) || s[1] < minY || s[1] > maxY) continue;
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
        var store = mainframeId == null ? null : disk(level.getServer());
        return CompletableFuture.supplyAsync(() -> {
            var t0 = MapStats.start();
            var batch = new Batch();
            var out = new ArrayList<@Nullable Served>(sections.size());
            for (int i = 0; i < sections.size(); i++) {
                var s = sections.get(i);
                out.add(lastKnown.get(i) ? lastSeen(store, batch, dim, s[0], s[1], s[2], mainframeId) : build(snapshot, store, batch, s[0], s[1], s[2], mainframeId));
            }
            batch.store(store, dim, mainframeId);
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
        into.put(key, states(chunk, cy));
    }

    /** Server thread: a copy of a section's block states, or {@link #AIR}. */
    private static Object states(LevelChunk chunk, int cy) {
        var section = chunk.getSection(chunk.getSectionIndexFromSectionY(cy));
        return section.hasOnlyAir() ? AIR : section.getStates().copy();
    }

    private static Object[] around(Snapshot snap, int cx, int cy, int cz) {
        return new Object[]{snap.at(cx + 1, cy, cz), snap.at(cx - 1, cy, cz), snap.at(cx, cy + 1, cz), snap.at(cx, cy - 1, cz),
            snap.at(cx, cy, cz + 1), snap.at(cx, cy, cz - 1)};
    }

    /** Background: one section from the snapshot, from the cache when its input is unchanged. */
    private static @Nullable Served build(Snapshot snap, @Nullable VoxelDisk store, Batch batch, int cx, int cy, int cz, @Nullable UUID mainframeId) {
        var stats = MapStats.VOXELS;
        if (cy < snap.minSectionY() || cy > snap.maxSectionY()) {
            stats.empty.increment();
            return null;
        }
        Object self = snap.at(cx, cy, cz);
        if (self == null) return lastSeen(store, batch, snap.dim(), cx, cy, cz, mainframeId); // not loaded
        if (self == AIR) {
            stats.hits.increment();
            return new Served(AIR_SECTION, false); // nothing to show, whatever is around
        }
        Object[] around = around(snap, cx, cy, cz);
        int[][] near = nearNodes(snap.nodes(), cx, cy, cz);
        var hashes = hashes(self, around);
        long input = withNodes(hashes.content(), near);
        String key = key(snap.dim(), cx, cy, cz, mainframeId);
        var cached = CACHE.get(key);
        if (cached != null && cached.input() == input) {
            stats.hits.increment();
            return new Served(cached.encoded(), false);
        }
        stats.misses.increment();
        var built = buildGrid(self, around, near, cx, cy, cz);
        var stored = new VoxelDisk.Stored(hashes.self(), hashes.content(), hashes.known(), built.oreFree());
        CACHE.put(key, new Entry(input, built.encoded(), stored));
        batch.built.computeIfAbsent(column(cx, cz), k -> new HashMap<>()).put(cy, stored);
        return new Served(built.encoded(), false);
    }

    /**
     * Background: the last known state of a section that cannot be read live, from memory, then from disk. Always the
     * ore-free variant (see the class comment).
     */
    private static @Nullable Served lastSeen(@Nullable VoxelDisk store, Batch batch, String dim, int cx, int cy, int cz, @Nullable UUID mainframeId) {
        var stats = MapStats.VOXELS;
        var cached = mainframeId == null ? null : CACHE.get(key(dim, cx, cy, cz, mainframeId));
        var stored = cached != null ? cached.stored() : mainframeId == null || store == null ? null
            : batch.read.computeIfAbsent(column(cx, cz), k -> readColumn(store, dim, cx, cz, mainframeId)).get(cy);
        if (stored == null) {
            stats.empty.increment();
            return null;
        }
        stats.hits.increment();
        MapStats.LAST_KNOWN.served.increment();
        if (store != null && mainframeId != null) store.touch(mainframeId, dim, cx, cz);
        return new Served(stored.section(), true);
    }

    /** A column from disk, into the memory cache too (the sections around it are usually asked for next). */
    private static Map<Integer, VoxelDisk.Stored> readColumn(VoxelDisk store, String dim, int cx, int cz, UUID mainframeId) {
        MapStats.LAST_KNOWN.reads.increment();
        var out = new HashMap<Integer, VoxelDisk.Stored>();
        for (var e : store.column(mainframeId, dim, cx, cz).entrySet()) {
            var s = e.getValue();
            var section = withoutOres(s.section());
            var stored = section == s.section() ? s : new VoxelDisk.Stored(s.self(), s.content(), s.known(), section);
            out.put(e.getKey(), stored);
            CACHE.putIfAbsent(key(dim, cx, e.getKey(), cz, mainframeId), new Entry(s.content(), section, stored));
        }
        return out;
    }

    /**
     * What comes from disk goes through the ore rule again: nothing stored shows an ore, but the ore tag belongs to the
     * packs and may have grown since the section was built.
     */
    private static VoxelSection.Encoded withoutOres(VoxelSection.Encoded section) {
        List<VoxelSection.Kind> palette = null;
        for (int i = 0; i < section.palette().size(); i++) {
            var id = Identifier.tryParse(section.palette().get(i).id());
            if (id == null || !XrayGuard.isOre(BuiltInRegistries.BLOCK.getValue(id).defaultBlockState())) continue;
            if (palette == null) palette = new ArrayList<>(section.palette());
            palette.set(i, java.util.Objects.requireNonNull(kind(Blocks.STONE.defaultBlockState())));
        }
        if (palette == null) return section;
        var list = List.copyOf(palette);
        return new VoxelSection.Encoded(list, section.runs(), section.exposed(), VoxelSection.hash(list, section.runs()));
    }

    // ---- snapshots of unloading chunks (server thread) and building them (background) ------------------------------------

    /**
     * Sections of a chunk column within {@code radius} (box to point, like {@link #allowed}) of one of the points. Pure.
     * The world's section range is {@code minY..maxY}; the result's bit n stands for section {@code minY + n}.
     */
    static BitSet sectionsInRange(double[][] points, int cx, int cz, int radius, int minY, int maxY) {
        var sections = new BitSet();
        double r2 = (double) radius * radius;
        for (var p : points) {
            double dx = Math.max(Math.max(cx * 16 - p[0], 0), p[0] - (cx * 16 + 16));
            double dz = Math.max(Math.max(cz * 16 - p[2], 0), p[2] - (cz * 16 + 16));
            double d2 = dx * dx + dz * dz;
            if (d2 > r2) continue;
            double h = Math.sqrt(r2 - d2);
            int from = Math.max(minY, (int) Math.ceil((p[1] - h - 16) / 16)), to = Math.min(maxY, (int) Math.floor((p[1] + h) / 16));
            if (from <= to) sections.set(from - minY, to - minY + 1);
        }
        return sections;
    }

    /**
     * A chunk unloads: if it is in a view area, its sections there are built in the background. Nearly every chunk ends at
     * the lookup. The chunk is saved and nothing writes to it any more, so its own sections need no copy; only its
     * neighbors do, and over the tick budget (an area unloading at once) they are left out. That only costs the blocks
     * exposed across the chunk's border, and {@link VoxelDisk#keeps} does not let such a build replace a better one. Over
     * the tick limit the chunk only goes in a queue: its sections stay valid, so a large area spreads over a few ticks.
     */
    private static void chunkUnloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        var server = level.getServer();
        if (!server.isSameThread()) return;
        var pos = chunk.getPos();
        var hits = VoxelAreas.at(level.dimension(), pos.x(), pos.z());
        if (hits == null) return;
        if (!CloudConfig.get(CloudConfig.VOXEL_VIEW)) return;
        if (!budgetLeft(server)) {
            if (DEFERRED.size() < MAX_SNAPSHOTS) {
                DEFERRED.add(new Deferred(level, chunk, hits));
                MapStats.LAST_KNOWN.deferred.increment();
            } else {
                MapStats.LAST_KNOWN.dropped.increment();
            }
            return;
        }
        var t0 = MapStats.start();
        boolean neighbors = budgetUsed < TICK_BUDGET_NANOS;
        if (snapshot(level, chunk, hits, MAX_SNAPSHOTS, true, neighbors)) budgetUsed += MapStats.LAST_KNOWN.snapshot(t0, neighbors);
    }

    private static boolean budgetLeft(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick != budgetTick) {
            budgetTick = tick;
            budgetUsed = 0;
        }
        return budgetUsed < TICK_LIMIT_NANOS && !deferAllForTest;
    }

    /** What is left of the tick limit goes to the waiting chunks. */
    private static void snapshotDeferred(MinecraftServer server) {
        while (!DEFERRED.isEmpty() && budgetLeft(server)) {
            var next = DEFERRED.poll();
            var t0 = MapStats.start();
            if (snapshot(next.level(), next.chunk(), next.hits(), MAX_SNAPSHOTS, true, false)) budgetUsed += MapStats.LAST_KNOWN.snapshot(t0, false);
        }
    }

    /** An unloaded chunk waiting for its snapshot. */
    private record Deferred(ServerLevel level, LevelChunk chunk, VoxelAreas.Hit[] hits) {
    }

    /** One mainframe's sections of a snapshot. */
    private record Fill(UUID mainframe, BitSet sections) {
    }

    /**
     * Collects what the hits need of a chunk and queues the builds. Only sections in range count, and side neighbors only
     * of sections with blocks; neighbors that are not loaded stay unknown.
     *
     * @param unloaded the chunk is gone: its sections are used as they are, else they are copied
     * @param neighbors with the neighbors in the four chunks around it
     * @return false if the chunk has nothing in range or the queue is full
     */
    private static boolean snapshot(ServerLevel level, LevelChunk chunk, VoxelAreas.Hit[] hits, int limit, boolean unloaded, boolean neighbors) {
        int cx = chunk.getPos().x(), cz = chunk.getPos().z(), minY = level.getMinSectionY(), maxY = level.getMaxSectionY();
        int radius = CloudConfig.get(CloudConfig.VOXEL_RADIUS);
        var retention = MainframeRetention.get(level.getServer());
        var fills = new ArrayList<Fill>(hits.length);
        var wanted = new BitSet();
        for (var hit : hits) {
            if (retention.entry(hit.mainframe()) != null) continue;
            var sections = sectionsInRange(hit.points(), cx, cz, radius, minY, maxY);
            if (sections.isEmpty()) continue;
            fills.add(new Fill(hit.mainframe(), sections));
            wanted.or(sections);
        }
        if (fills.isEmpty()) return false;
        if (SNAPSHOTS.get() >= limit) {
            MapStats.LAST_KNOWN.dropped.increment();
            return false;
        }
        var source = level.getChunkSource();
        LevelChunk east = neighbors ? source.getChunkNow(cx + 1, cz) : null, west = neighbors ? source.getChunkNow(cx - 1, cz) : null;
        LevelChunk south = neighbors ? source.getChunkNow(cx, cz + 1) : null, north = neighbors ? source.getChunkNow(cx, cz - 1) : null;
        var containers = new HashMap<Long, Object>(64);
        for (int i = wanted.nextSetBit(0); i >= 0; i = wanted.nextSetBit(i + 1)) {
            int cy = minY + i;
            var self = own(containers, chunk, cx, cy, cz, unloaded);
            if (cy > minY) own(containers, chunk, cx, cy - 1, cz, unloaded);
            if (cy < maxY) own(containers, chunk, cx, cy + 1, cz, unloaded);
            if (self == AIR) continue;
            side(containers, east, cx + 1, cy, cz);
            side(containers, west, cx - 1, cy, cz);
            side(containers, south, cx, cy, cz + 1);
            side(containers, north, cx, cy, cz - 1);
        }
        MapStats.LAST_KNOWN.snapshots.increment();
        if (!neighbors) MapStats.LAST_KNOWN.partial.increment();
        var snapshot = new Snapshot(level.dimension().identifier().toString(), minY, maxY, containers, NO_NODES);
        var store = disk(level.getServer());
        SNAPSHOTS.incrementAndGet();
        executor().execute(() -> {
            try {
                var t0 = MapStats.start();
                for (var fill : fills) fill(store, snapshot, cx, cz, fill);
                MapStats.VOXELS.worker(t0);
            } catch (RuntimeException e) {
                ai.ezfox.cccloud.CloudLog.LOGGER.debug("[cccloud] Could not build the terrain of chunk {} {}", cx, cz, e);
            } finally {
                SNAPSHOTS.decrementAndGet();
            }
        });
        return true;
    }

    private static Object own(Map<Long, Object> into, LevelChunk chunk, int cx, int cy, int cz, boolean unloaded) {
        return into.computeIfAbsent(SectionPos.asLong(cx, cy, cz), k -> {
            var section = chunk.getSection(chunk.getSectionIndexFromSectionY(cy));
            return section.hasOnlyAir() ? AIR : unloaded ? section.getStates() : section.getStates().copy();
        });
    }

    private static void side(Map<Long, Object> into, @Nullable LevelChunk chunk, int cx, int cy, int cz) {
        if (chunk != null) into.put(SectionPos.asLong(cx, cy, cz), states(chunk, cy));
    }

    /**
     * Background: the sections of an unloaded chunk for one mainframe. Only what changed is built and written; everything
     * is built without nodes, so it is the ore-free variant.
     */
    private static void fill(VoxelDisk store, Snapshot snap, int cx, int cz, Fill fill) {
        var stats = MapStats.LAST_KNOWN;
        var old = store.column(fill.mainframe(), snap.dim(), cx, cz);
        var updates = new HashMap<Integer, VoxelDisk.Stored>();
        var sections = fill.sections();
        for (int i = sections.nextSetBit(0); i >= 0; i = sections.nextSetBit(i + 1)) {
            int cy = snap.minSectionY() + i;
            Object self = snap.at(cx, cy, cz);
            var stored = old.get(cy);
            String key = key(snap.dim(), cx, cy, cz, fill.mainframe());
            if (self == AIR) {
                // only where it replaces blocks: a section that always was air needs no entry
                if (stored == null || stored.section().exposed() == 0) continue;
                var air = new VoxelDisk.Stored(0, 0, 0, AIR_SECTION);
                CACHE.put(key, new Entry(0, AIR_SECTION, air));
                updates.put(cy, air);
                continue;
            }
            if (self == null) continue;
            // the unloaded chunk's own container: writing it into the hash buffer takes its lock, so that goes to a copy
            @SuppressWarnings("unchecked") var states = ((PalettedContainer<BlockState>) self).copy();
            Object[] around = around(snap, cx, cy, cz);
            var hashes = hashes(states, around);
            if (stored != null && VoxelDisk.keeps(stored, hashes.self(), hashes.content(), hashes.known())) {
                stats.unchanged.increment();
                continue;
            }
            var cached = CACHE.get(key);
            if (cached != null && cached.stored().content() == hashes.content()) {
                stats.unchanged.increment();
                updates.put(cy, cached.stored());
                continue;
            }
            stats.built.increment();
            var built = buildGrid(states, around, NO_NODES, cx, cy, cz);
            var update = new VoxelDisk.Stored(hashes.self(), hashes.content(), hashes.known(), built.oreFree());
            CACHE.put(key, new Entry(hashes.content(), built.oreFree(), update));
            updates.put(cy, update);
        }
        store.put(fill.mainframe(), snap.dim(), cx, cz, updates);
    }

    /** Server stop: chunks with tickets never unload, so what is loaded of every view area is copied now. */
    private static void snapshotLoaded(MinecraftServer server) {
        if (!CloudConfig.get(CloudConfig.VOXEL_VIEW)) return;
        for (var next : DEFERRED) snapshot(next.level(), next.chunk(), next.hits(), MAX_STOP_SNAPSHOTS, true, false);
        DEFERRED.clear();
        int radius = CloudConfig.get(CloudConfig.VOXEL_RADIUS);
        var seen = new java.util.HashSet<String>();
        for (var area : VoxelAreas.all().values()) {
            for (var dim : area.entrySet()) {
                var level = ai.ezfox.cccloud.map.MapTileService.level(server, dim.getKey());
                if (level == null) continue;
                for (var p : dim.getValue()) {
                    int x0 = Math.floorDiv((int) Math.floor(p[0] - radius), 16), x1 = Math.floorDiv((int) Math.floor(p[0] + radius), 16);
                    int z0 = Math.floorDiv((int) Math.floor(p[2] - radius), 16), z1 = Math.floorDiv((int) Math.floor(p[2] + radius), 16);
                    for (int cx = x0; cx <= x1; cx++) {
                        for (int cz = z0; cz <= z1; cz++) {
                            if (!seen.add(dim.getKey() + "|" + cx + "|" + cz)) continue;
                            var chunk = level.getChunkSource().getChunkNow(cx, cz);
                            var hits = chunk == null ? null : VoxelAreas.at(level.dimension(), cx, cz);
                            if (hits != null) snapshot(level, chunk, hits, MAX_STOP_SNAPSHOTS, false, true);
                        }
                    }
                }
            }
        }
    }

    /** Nodes whose ore radius reaches into the section (only they change what the section shows). */
    private static int[][] nearNodes(int[][] nodes, int cx, int cy, int cz) {
        var list = new ArrayList<int[]>();
        for (var n : nodes) if (distance(cx, cy, cz, n[0], n[1], n[2]) <= XrayGuard.ORE_RADIUS + 1) list.add(n);
        return list.toArray(new int[0][]);
    }

    private static final ThreadLocal<FriendlyByteBuf> HASH_BUFFER = ThreadLocal.withInitial(() -> new FriendlyByteBuf(Unpooled.buffer(16 * 1024)));

    /**
     * Hashes of what the section's encoding depends on: its own states, then also the touching layer of each of the six
     * neighbors (only those decide exposure and the ore rule). Activity elsewhere in a neighbor does not rebuild the
     * section.
     */
    @SuppressWarnings("unchecked")
    private static Hashes hashes(Object self, Object[] around) {
        var buf = HASH_BUFFER.get();
        buf.clear();
        buf.writeInt(LOGIC_VERSION);
        ((PalettedContainer<BlockState>) self).write(buf);
        long own = digest(buf);
        int known = 0;
        for (int f = 0; f < 6; f++) {
            Object c = around[f];
            if (c == null) buf.writeByte(0);
            else if (c == AIR) buf.writeByte(1);
            else {
                buf.writeByte(2);
                var n = (PalettedContainer<BlockState>) c;
                for (int a = 0; a < 16; a++) for (int b = 0; b < 16; b++) {
                    // the neighbor's layer that touches this section: +x, -x, +y, -y, +z, -z
                    var state = switch (f) {
                        case 0 -> n.get(0, a, b);
                        case 1 -> n.get(15, a, b);
                        case 2 -> n.get(a, 0, b);
                        case 3 -> n.get(a, 15, b);
                        case 4 -> n.get(a, b, 0);
                        default -> n.get(a, b, 15);
                    };
                    buf.writeVarInt(net.minecraft.world.level.block.Block.getId(state));
                }
            }
            if (c != null) known |= 1 << f;
        }
        return new Hashes(own, digest(buf), known);
    }

    /** The input of the live path: the content plus the nearby nodes, which decide the ore rule. */
    private static long withNodes(long content, int[][] near) {
        if (near.length == 0) return content;
        var buf = HASH_BUFFER.get();
        buf.clear();
        buf.writeLong(content);
        for (var n : near) {
            buf.writeInt(n[0]);
            buf.writeInt(n[1]);
            buf.writeInt(n[2]);
        }
        return digest(buf);
    }

    /** First 8 bytes of the SHA-256 of what the buffer holds. */
    private static long digest(FriendlyByteBuf buf) {
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
    private static Built buildGrid(Object self, Object[] around, int[][] near, int cx, int cy, int cz) {
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
        if (ores.isEmpty()) {
            var encoded = VoxelSection.encodeGrid(grid, kinds);
            return new Built(encoded, encoded);
        }
        int stone = gridId(Blocks.STONE.defaultBlockState(), stateIds, kinds, kindIds);
        var oreIds = new java.util.HashSet<Integer>();
        for (var state : ores.keySet()) oreIds.add(stateIds.get(state));
        boolean shown = false;
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int g = VoxelSection.gridIndex(x, y, z);
            if (!oreIds.contains(grid[g])) continue;
            if (touchesAir(grid, g) && XrayGuard.nearNode(near, cx * 16 + x, cy * 16 + y, cz * 16 + z)) shown = true;
            else grid[g] = stone;
        }
        var encoded = VoxelSection.encodeGrid(grid, kinds);
        if (!shown) return new Built(encoded, encoded);
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int g = VoxelSection.gridIndex(x, y, z);
            if (oreIds.contains(grid[g])) grid[g] = stone;
        }
        return new Built(encoded, VoxelSection.encodeGrid(grid, kinds));
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

    /** Test hook: this world's disk cache. */
    public static VoxelDisk diskForTest(MinecraftServer server) {
        return disk(server);
    }

    /** Test hook: waits until the unload snapshots queued so far are built and the disk cache has written them. */
    public static boolean idleForTest(MinecraftServer server) {
        return SNAPSHOTS.get() == 0 && DEFERRED.isEmpty() && disk(server).flush(100);
    }

    /** Test hook: every unload waits, as if the tick limit was used up, until this is switched off. */
    public static void deferAllForTest(boolean on) {
        deferAllForTest = on;
    }
}
