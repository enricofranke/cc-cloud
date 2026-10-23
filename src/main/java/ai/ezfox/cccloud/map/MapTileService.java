package ai.ezfox.cccloud.map;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.icons.PngCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
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
import net.minecraft.world.level.chunk.LevelChunk;
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

        Job(Key key) {
            this.key = key;
        }
    }

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
            if (encoder != null) encoder.shutdownNow();
            encoder = null;
        });
    }

    private static void changed(net.minecraft.world.level.LevelAccessor level, int chunkX, int chunkZ) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        DIRTY.put(serverLevel.dimension().identifier() + "|" + (chunkX >> 3) + "|" + (chunkZ >> 3), System.currentTimeMillis());
    }

    /**
     * A tile: from the cache if it is fresh (or changed less than 30 s ago), else re-rendered. Server thread.
     */
    public static CompletableFuture<Tile> tile(MinecraftServer server, Key key) {
        var cached = CACHE.get(key);
        if (cached == null) cached = loadFromDisk(server, key);
        if (cached != null) {
            Long changedAt = DIRTY.get(key.area());
            boolean stale = changedAt != null && changedAt > cached.renderedAt() && System.currentTimeMillis() - changedAt >= DEBOUNCE_MS;
            if (!stale) return CompletableFuture.completedFuture(new Tile(cached.png(), cached.etag()));
        }
        var level = level(server, key.dim());
        if (level == null) return CompletableFuture.completedFuture(new Tile(null, "\"none\""));
        synchronized (JOBS) {
            var running = JOBS.get(key);
            if (running != null) return running.future;
            var job = new Job(key);
            if (key.mainframe() != null) job.nodes = XrayGuard.nodes(key.mainframe(), key.dim());
            JOBS.put(key, job);
            QUEUE.add(job);
            return job.future;
        }
    }

    public static @Nullable ServerLevel level(MinecraftServer server, String dim) {
        var id = Identifier.tryParse(dim);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    // ---- sampling (server thread, budgeted) --------------------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        long start = System.nanoTime();
        while (System.nanoTime() - start < TICK_BUDGET_NANOS) {
            Job job;
            synchronized (JOBS) {
                job = QUEUE.peek();
            }
            if (job == null) return;
            var level = level(server, job.key.dim());
            if (level == null) {
                finish(server, job, null);
                continue;
            }
            int baseChunkX = job.key.tx() * 8, baseChunkZ = job.key.tz() * 8;
            if (job.nextChunk == 0) sampleNorthRow(level, job);
            while (job.nextChunk < 64 && System.nanoTime() - start < TICK_BUDGET_NANOS) {
                int cx = baseChunkX + (job.nextChunk & 7), cz = baseChunkZ + (job.nextChunk >> 3);
                job.nextChunk++;
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz); // never loads or generates
                // slices never show chunks claimed by another team (they stay unknown/transparent)
                if (chunk != null && !(job.key.layer() == Layer.SLICE && XrayGuard.claimedByOther(level, cx, cz, job.key.player()))) sampleChunk(level, chunk, job);
            }
            if (job.nextChunk >= 64) {
                synchronized (JOBS) {
                    QUEUE.poll();
                }
                render(server, job);
            }
        }
    }

    private static void sampleChunk(ServerLevel level, LevelChunk chunk, Job job) {
        var pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinY();
        int originX = job.key.tx() * MapTileRenderer.SIZE, originZ = job.key.tz() * MapTileRenderer.SIZE;
        int chunkX = chunk.getPos().getMinBlockX(), chunkZ = chunk.getPos().getMinBlockZ();
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = chunkX + dx, z = chunkZ + dz;
                int i = (z - originZ) * MapTileRenderer.SIZE + (x - originX);
                var s = job.samples;
                if (job.key.layer() == Layer.SURFACE) {
                    int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz);
                    var state = chunk.getBlockState(pos.set(x, y, z));
                    while (state.getMapColor(level, pos) == MapColor.NONE && y > minY) state = chunk.getBlockState(pos.set(x, --y, z));
                    int depth = 0;
                    boolean water = !state.getFluidState().isEmpty();
                    if (water) {
                        int wy = y;
                        while (wy > minY && depth < 64 && !chunk.getBlockState(pos.set(x, wy - 1, z)).getFluidState().isEmpty()) {
                            wy--;
                            depth++;
                        }
                        pos.set(x, y, z);
                    }
                    s.color[i] = state.getMapColor(level, pos.set(x, y, z)).id;
                    s.height[i] = y;
                    s.depth[i] = depth;
                    s.water[i] = water && s.color[i] == MapColor.WATER.id;
                } else {
                    int top = job.key.y();
                    int found = 0;
                    s.color[i] = 0;
                    for (int d = 0; d < MapTileRenderer.SLICE_DEPTH && top - d >= minY; d++) {
                        var state = chunk.getBlockState(pos.set(x, top - d, z));
                        var color = state.getMapColor(level, pos);
                        // ores look like stone unless they touch air near one of the mainframe's own nodes
                        if (color != MapColor.NONE && XrayGuard.isOre(state) && !(touchesAir(level, x, top - d, z) && XrayGuard.nearNode(job.nodes, x, top - d, z))) {
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

    private static boolean touchesAir(ServerLevel level, int x, int y, int z) {
        var pos = new BlockPos.MutableBlockPos();
        for (var d : net.minecraft.core.Direction.values()) {
            pos.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && chunk.getBlockState(pos).isAir()) return true;
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

    private static void render(MinecraftServer server, Job job) {
        var previous = CACHE.get(job.key);
        var executor = encoder;
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(r -> {
                var t = new Thread(r, "cccloud-map-tiles");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
            encoder = executor;
        }
        executor.execute(() -> {
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
                finish(server, job, new Tile(png, entry.etag()));
            } catch (RuntimeException e) {
                CCCloud.LOGGER.warn("[cccloud] Map tile {} failed", job.key, e);
                finish(server, job, new Tile(null, "\"none\""));
            }
        });
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
    }
}
