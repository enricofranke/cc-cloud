package ai.ezfox.cccloud.map;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.icons.PngCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the 2D map tiles (docs/SPEC.md "Map tiles"): 128×128-block tiles at 1 px per block, layers
 * {@code surface} and {@code slice} (a cut at a Y). Only chunks that are loaded right now are sampled – chunks are
 * never generated or loaded for this; pixels of other chunks come from the last render (cache in memory and in
 * {@code <world>/cccloud/maptiles/}). Sampling runs on the server thread within {@link #TICK_BUDGET_NANOS} per tick,
 * PNG encoding on a background thread. Tiles are re-rendered at most every 30 s after their chunks changed (chunk
 * save/unload events).
 */
public final class MapTileService {
    public static final long TICK_BUDGET_NANOS = 2_000_000;
    public static final long DEBOUNCE_MS = 30_000;
    private static final int MEMORY_TILES = 256;
    /**
     * Tiles waiting for the server thread at most; more are answered 503 (each waiting tile holds its sample buffers, and
     * jobs outlive the 15 s HTTP timeout). Security review 2026-10-30.
     */
    static final int MAX_QUEUE = 256;

    public enum Layer { SURFACE, SLICE }

    /**
     * Slices depend on who asks (ore visibility near the mainframe's nodes, claims of other teams): their key carries the
     * mainframe and the player. Surface tiles are shared (both null).
     */
    public record Key(String dim, Layer layer, int y, int tx, int tz, @Nullable UUID mainframe, @Nullable UUID player) {
        public Key(String dim, Layer layer, int y, int tx, int tz) {
            this(dim, layer, y, tx, tz, null, null);
        }

        String file() {
            String scope = layer == Layer.SURFACE ? "surface" : "slice_" + y + "_" + mainframe + "_" + player;
            return dim.replace(':', '_').replace('/', '_') + "/" + scope + "/" + tx + "_" + tz + ".png";
        }

        String area() {
            return dim + "|" + tx + "|" + tz;
        }
    }

    /** A rendered tile: PNG (null = no data at all → 204) and its ETag. */
    public record Tile(byte @Nullable [] png, String etag) {
    }

    private record Entry(@Nullable int[] argb, byte @Nullable [] png, String etag, long renderedAt) {
    }

    private static final class Job {
        final Key key;
        final CompletableFuture<Tile> future = new CompletableFuture<>();
        final MapTileRenderer.Samples samples = new MapTileRenderer.Samples();
        final int[] northRow = new int[MapTileRenderer.SIZE];
        int nextChunk;
        /** Slices: the mainframe's node positions (ores near them may show). */
        int[][] nodes = new int[0][];
        /**
         * Copied block states per loaded chunk (ChunkPos.pack): one entry per section, a PalettedContainer copy,
         * {@link #AIR} or null (not copied). Absent chunks were not loaded (or are claimed by another team).
         */
        final Map<Long, Object[]> chunks = new HashMap<>();
        /** Surface: WORLD_SURFACE heights (256 per chunk, x + 16·z). */
        final Map<Long, int[]> heights = new HashMap<>();
        /** Slices: chunks claimed by another team for the asking player (drawn empty, so older pixels never stay). */
        final java.util.Set<Long> claimed = new java.util.HashSet<>();
        int minY, minSection;

        Job(Key key) {
            this.key = key;
        }
    }

    /** Marker for an air-only section (not copied). */
    private static final Object AIR = new Object();
    private static final BlockState AIR_STATE = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

    private static final Map<Key, Entry> CACHE = java.util.Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Entry> eldest) {
            return size() > MEMORY_TILES;
        }
    });
    /** area (dim|tx|tz) → time of the last chunk change in it. */
    private static final Map<String, Long> DIRTY = new ConcurrentHashMap<>();
    private static final Map<Key, Job> JOBS = new LinkedHashMap<>();
    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static @Nullable ExecutorService encoder;

    private MapTileService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, e -> changed(e.getLevel(), e.getChunk().getPos().x(), e.getChunk().getPos().z()));
        NeoForge.EVENT_BUS.addListener(ChunkDataEvent.Save.class, e -> changed(e.getLevel(), e.getChunk().getPos().x(), e.getChunk().getPos().z()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            synchronized (JOBS) {
                JOBS.clear();
                QUEUE.clear();
            }
            CACHE.clear();
            DIRTY.clear();
            synchronized (MapTileService.class) {
                if (encoder != null) encoder.shutdownNow();
                encoder = null;
            }
        });
    }

    private static void changed(net.minecraft.world.level.LevelAccessor level, int chunkX, int chunkZ) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        DIRTY.put(serverLevel.dimension().identifier() + "|" + (chunkX >> 3) + "|" + (chunkZ >> 3), System.currentTimeMillis());
    }

    /**
     * A tile: from the memory cache if it is fresh (or changed less than 30 s ago), else from the disk cache (read on the
     * background thread, never on the server thread), else re-rendered. Server thread.
     */
    public static CompletableFuture<Tile> tile(MinecraftServer server, Key key) {
        var level = level(server, key.dim());
        if (level == null) return CompletableFuture.completedFuture(new Tile(null, "\"none\""));
        int[][] nodes = key.mainframe() != null ? XrayGuard.nodes(key.mainframe(), key.dim()) : new int[0][];
        // A slice with a chunk another team has claimed (since it was cached) is drawn again: no cached pixels of it.
        if (key.layer() == Layer.SLICE && anyClaimed(level, key)) return queue(key, nodes);
        var cached = CACHE.get(key);
        if (cached != null && fresh(key, cached)) return hit(cached);
        if (cached != null) return queue(key, nodes);
        return CompletableFuture.supplyAsync(() -> loadFromDisk(server, key), encoder())
            .thenCompose(disk -> disk != null && fresh(key, disk) ? hit(disk) : queue(key, nodes));
    }

    /** One of the tile's chunks is claimed by another team for the key's player. Server thread. */
    private static boolean anyClaimed(ServerLevel level, Key key) {
        if (key.player() == null) return false;
        for (int c = 0; c < 64; c++) {
            if (XrayGuard.claimedByOther(level, key.tx() * 8 + (c & 7), key.tz() * 8 + (c >> 3), key.player())) return true;
        }
        return false;
    }

    private static boolean fresh(Key key, Entry cached) {
        Long changedAt = DIRTY.get(key.area());
        return changedAt == null || changedAt <= cached.renderedAt() || System.currentTimeMillis() - changedAt < DEBOUNCE_MS;
    }

    private static CompletableFuture<Tile> hit(Entry cached) {
        MapStats.TILES.hits.increment();
        if (cached.png() == null) MapStats.TILES.empty.increment();
        return CompletableFuture.completedFuture(new Tile(cached.png(), cached.etag()));
    }

    /** Queues the tile for sampling (or joins the job that is already queued). Any thread. */
    private static CompletableFuture<Tile> queue(Key key, int[][] nodes) {
        synchronized (JOBS) {
            var running = JOBS.get(key);
            if (running != null) return running.future;
            if (QUEUE.size() >= MAX_QUEUE) {
                MapStats.TILES.rateLimited.increment();
                return CompletableFuture.failedFuture(new IllegalStateException("busy"));
            }
            var job = new Job(key);
            MapStats.TILES.misses.increment();
            job.nodes = nodes;
            JOBS.put(key, job);
            QUEUE.add(job);
            return job.future;
        }
    }

    private static synchronized ExecutorService encoder() {
        if (encoder == null) {
            encoder = Executors.newSingleThreadExecutor(r -> {
                var t = new Thread(r, "cccloud-map-tiles");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        }
        return encoder;
    }

    public static @Nullable ServerLevel level(MinecraftServer server, String dim) {
        var id = Identifier.tryParse(dim);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    // ---- copying (server thread, budgeted) and sampling (background) -----------------------------------------------

    private static void tick(MinecraftServer server) {
        var start = MapStats.start();
        if (copy(server, start[0])) MapStats.TILES.main(start);
    }

    /**
     * Copies the block states of queued tiles within the tick budget; true if there was work. Per loaded chunk only its
     * containers are copied ({@code PalettedContainer.copy()}, microseconds; air-only sections not at all): for the
     * surface every section up to the highest block, for a slice the sections around the cut (also of the chunks around
     * the tile, for the ore rule at the tile's border). Sampling and rendering run on the background thread.
     */
    private static boolean copy(MinecraftServer server, long start) {
        boolean worked = false;
        while (System.nanoTime() - start < TICK_BUDGET_NANOS) {
            Job job;
            synchronized (JOBS) {
                job = QUEUE.peek();
            }
            if (job == null) return worked;
            worked = true;
            var level = level(server, job.key.dim());
            if (level == null) {
                finish(server, job, null);
                continue;
            }
            boolean slice = job.key.layer() == Layer.SLICE;
            int ring = slice ? 1 : 0, side = 8 + 2 * ring;
            int baseChunkX = job.key.tx() * 8 - ring, baseChunkZ = job.key.tz() * 8 - ring;
            if (job.nextChunk == 0) {
                job.minY = level.getMinY();
                job.minSection = level.getMinSectionY();
                if (!slice) sampleNorthRow(level, job);
            }
            while (job.nextChunk < side * side && System.nanoTime() - start < TICK_BUDGET_NANOS) {
                int cx = baseChunkX + job.nextChunk % side, cz = baseChunkZ + job.nextChunk / side;
                job.nextChunk++;
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz); // never loads or generates
                // slices never show chunks claimed by another team: drawn empty (not "unknown", which keeps older pixels)
                if (slice && XrayGuard.claimedByOther(level, cx, cz, job.key.player())) {
                    job.claimed.add(net.minecraft.world.level.ChunkPos.pack(cx, cz));
                    continue;
                }
                if (chunk == null) continue;
                copyChunk(level, chunk, job, slice);
            }
            if (job.nextChunk >= side * side) {
                synchronized (JOBS) {
                    QUEUE.poll();
                }
                var done = job;
                encoder().execute(() -> {
                    var t0 = MapStats.start();
                    try {
                        sampleAll(done);
                    } catch (RuntimeException e) {
                        CCCloud.LOGGER.warn("[cccloud] Map tile {} failed", done.key, e);
                    }
                    MapStats.TILES.worker(t0);
                    render(server, done);
                });
            }
        }
        return worked;
    }

    private static void copyChunk(ServerLevel level, LevelChunk chunk, Job job, boolean slice) {
        var sections = chunk.getSections();
        var copies = new Object[sections.length];
        int from, to;
        if (slice) {
            from = level.getSectionIndex(Math.max(level.getMinY(), job.key.y() - MapTileRenderer.SLICE_DEPTH - 1));
            to = level.getSectionIndex(Math.min(level.getMaxY(), job.key.y() + 1));
        } else {
            var h = new int[256];
            int max = level.getMinY();
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) max = Math.max(max, h[dx + 16 * dz] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz));
            job.heights.put(chunk.getPos().pack(), h);
            from = 0;
            to = level.getSectionIndex(Math.min(level.getMaxY(), max));
        }
        for (int si = Math.max(0, from); si <= to && si < sections.length; si++) {
            copies[si] = sections[si].hasOnlyAir() ? AIR : sections[si].getStates().copy();
        }
        job.chunks.put(chunk.getPos().pack(), copies);
    }

    /** A copied block state, air outside the world, null where nothing was copied (unknown). Background thread. */
    @SuppressWarnings("unchecked")
    private static @Nullable BlockState state(Job job, int x, int y, int z) {
        Object[] sections = job.chunks.get(net.minecraft.world.level.ChunkPos.pack(x >> 4, z >> 4));
        if (sections == null) return null;
        int si = (y >> 4) - job.minSection;
        if (si < 0 || si >= sections.length) return AIR_STATE;
        Object c = sections[si];
        if (c == null) return null;
        if (c == AIR) return AIR_STATE;
        return ((PalettedContainer<BlockState>) c).get(x & 15, y & 15, z & 15);
    }

    /** Map colour of a copied state (no level access: like an empty world around it). */
    private static MapColor color(BlockState state, BlockPos.MutableBlockPos pos) {
        return state.getMapColor(EmptyBlockGetter.INSTANCE, pos);
    }

    /** Samples every copied chunk of the tile. Background thread. */
    private static void sampleAll(Job job) {
        var pos = new BlockPos.MutableBlockPos();
        int minY = job.minY;
        int originX = job.key.tx() * MapTileRenderer.SIZE, originZ = job.key.tz() * MapTileRenderer.SIZE;
        var s = job.samples;
        for (int c = 0; c < 64; c++) {
            int cx = job.key.tx() * 8 + (c & 7), cz = job.key.tz() * 8 + (c >> 3);
            long key = net.minecraft.world.level.ChunkPos.pack(cx, cz);
            if (job.claimed.contains(key)) {
                for (int dz = 0; dz < 16; dz++) {
                    for (int dx = 0; dx < 16; dx++) s.color[(cz * 16 + dz - originZ) * MapTileRenderer.SIZE + (cx * 16 + dx - originX)] = 0;
                }
                continue;
            }
            if (!job.chunks.containsKey(key)) continue;
            int[] heights = job.heights.get(key);
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    int x = cx * 16 + dx, z = cz * 16 + dz;
                    int i = (z - originZ) * MapTileRenderer.SIZE + (x - originX);
                    if (job.key.layer() == Layer.SURFACE) {
                        int y = heights[dx + 16 * dz];
                        var state = state(job, x, y, z);
                        while (state != null && color(state, pos.set(x, y, z)) == MapColor.NONE && y > minY) state = state(job, x, --y, z);
                        if (state == null) continue; // unknown
                        int depth = 0;
                        boolean water = !state.getFluidState().isEmpty();
                        if (water) {
                            int wy = y;
                            BlockState below;
                            while (wy > minY && depth < 64 && (below = state(job, x, wy - 1, z)) != null && !below.getFluidState().isEmpty()) {
                                wy--;
                                depth++;
                            }
                        }
                        s.color[i] = color(state, pos.set(x, y, z)).id;
                        s.height[i] = y;
                        s.depth[i] = depth;
                        s.water[i] = water && s.color[i] == MapColor.WATER.id;
                    } else {
                        int top = job.key.y();
                        int found = 0;
                        s.color[i] = 0;
                        for (int d = 0; d < MapTileRenderer.SLICE_DEPTH && top - d >= minY; d++) {
                            var state = state(job, x, top - d, z);
                            if (state == null) break;
                            var color = color(state, pos.set(x, top - d, z));
                            // ores look like stone unless they touch air near one of the mainframe's own nodes
                            if (color != MapColor.NONE && XrayGuard.isOre(state) && !(touchesAir(job, x, top - d, z) && XrayGuard.nearNode(job.nodes, x, top - d, z))) {
                                color = MapColor.STONE;
                            }
                            if (color != MapColor.NONE) {
                                s.color[i] = color.id;
                                s.depth[i] = d;
                                s.height[i] = top - d;
                                found = 1;
                                break;
                            }
                        }
                        if (found == 0) s.depth[i] = MapTileRenderer.SLICE_DEPTH;
                    }
                }
            }
        }
        job.chunks.clear(); // the copies are not needed any more
        job.heights.clear();
    }

    /** Air on a face, from the copies (unknown neighbors count as solid). */
    private static boolean touchesAir(Job job, int x, int y, int z) {
        for (var d : net.minecraft.core.Direction.values()) {
            var n = state(job, x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
            if (n != null && n.isAir()) return true;
        }
        return false;
    }

    /** Heights of the row north of the tile, so the first row is shaded like the rest (only loaded chunks). */
    private static void sampleNorthRow(ServerLevel level, Job job) {
        int z = job.key.tz() * MapTileRenderer.SIZE - 1;
        int originX = job.key.tx() * MapTileRenderer.SIZE;
        for (int x = 0; x < MapTileRenderer.SIZE; x++) {
            var chunk = level.getChunkSource().getChunkNow((originX + x) >> 4, z >> 4);
            job.northRow[x] = chunk == null ? Integer.MIN_VALUE : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, (originX + x) & 15, z & 15);
        }
    }

    // ---- rendering (background thread) -------------------------------------------------------------------------------

    /** Renders, encodes and caches a sampled tile. Background thread. */
    private static void render(MinecraftServer server, Job job) {
        var previous = CACHE.get(job.key);
        {
            var t0 = MapStats.start();
            try {
                var samples = job.samples;
                for (int x = 0; x < MapTileRenderer.SIZE; x++) if (job.northRow[x] == Integer.MIN_VALUE) job.northRow[x] = samples.height[x];
                int[] argb = job.key.layer() == Layer.SURFACE
                    ? MapTileRenderer.surface(samples, (id, b) -> MapColor.byId(id).calculateARGBColor(MapColor.Brightness.byId(b)), job.northRow)
                    : MapTileRenderer.slice(samples, (id, b) -> MapColor.byId(id).calculateARGBColor(MapColor.Brightness.byId(b)));
                argb = MapTileRenderer.merge(argb, samples, previous == null ? null : previous.argb());
                boolean any = samples.any() || (previous != null && previous.png() != null);
                byte[] png = any ? PngCodec.encode(MapTileRenderer.SIZE, MapTileRenderer.SIZE, argb) : null;
                var entry = new Entry(argb, png, etag(png), System.currentTimeMillis());
                CACHE.put(job.key, entry);
                if (Boolean.getBoolean("cccloud.gametest")) SAMPLES_FOR_TEST.put(job.key, samples);
                if (png != null) save(server, job.key, png);
                if (png == null) MapStats.TILES.empty.increment();
                MapStats.TILES.worker(t0);
                finish(server, job, new Tile(png, entry.etag()));
            } catch (RuntimeException e) {
                CCCloud.LOGGER.warn("[cccloud] Map tile {} failed", job.key, e);
                finish(server, job, new Tile(null, "\"none\""));
            }
        }
    }

    private static void finish(MinecraftServer server, Job job, @Nullable Tile tile) {
        synchronized (JOBS) {
            JOBS.remove(job.key);
            QUEUE.remove(job);
        }
        job.future.complete(tile == null ? new Tile(null, "\"none\"") : tile);
    }

    private static String etag(byte @Nullable [] png) {
        if (png == null) return "\"none\"";
        var crc = new java.util.zip.CRC32();
        crc.update(png);
        return "\"" + Long.toHexString(crc.getValue()) + "\"";
    }

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("cccloud").resolve("maptiles");
    }

    private static void save(MinecraftServer server, Key key, byte[] png) {
        try {
            var file = dir(server).resolve(key.file());
            Files.createDirectories(file.getParent());
            var temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, png);
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            CCCloud.LOGGER.debug("[cccloud] Could not cache map tile {}", key, e);
        }
    }

    private static @Nullable Entry loadFromDisk(MinecraftServer server, Key key) {
        var file = dir(server).resolve(key.file());
        try {
            if (!Files.isRegularFile(file)) return null;
            byte[] png = Files.readAllBytes(file);
            var entry = new Entry(null, png, etag(png), Files.getLastModifiedTime(file).toMillis());
            CACHE.put(key, entry);
            return entry;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Test hook: the pixels of a rendered tile. */
    public static int @Nullable [] argbForTest(Key key) {
        var entry = CACHE.get(key);
        return entry == null ? null : entry.argb();
    }

    private static final Map<Key, MapTileRenderer.Samples> SAMPLES_FOR_TEST = new ConcurrentHashMap<>();

    /** Test hook (GameTest server only): the column heights a tile was rendered from, or MIN_VALUE. */
    public static int heightForTest(Key key, int x, int z) {
        var s = SAMPLES_FOR_TEST.get(key);
        return s == null ? Integer.MIN_VALUE : s.height[Math.floorMod(z, MapTileRenderer.SIZE) * MapTileRenderer.SIZE + Math.floorMod(x, MapTileRenderer.SIZE)];
    }

    /** Test hook. */
    public static void clearForTest() {
        SAMPLES_FOR_TEST.clear();
        CACHE.clear();
        DIRTY.clear();
        synchronized (JOBS) {
            JOBS.clear();
            QUEUE.clear();
        }
    }

    /** Test hook: queues a tile without a server (the queue limit). */
    static CompletableFuture<Tile> queueForTest(Key key) {
        return queue(key, new int[0][]);
    }
}
