package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ingame.InGameTestSupport;
import ai.ezfox.cccloud.map.MapStats;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.voxel.VoxelAreas;
import ai.ezfox.cccloud.voxel.VoxelService;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Measures the last known terrain (docs/SPEC.md "Voxel view – last known terrain"): a base on hilly terrain with caves,
 * ores, trees and water, its 13×13 chunks loaded for real and unloaded for real. Prints the server-thread time per unload
 * snapshot, what the base takes on disk, and how long the server takes to answer the 3D view's sections live and from
 * disk with cold memory. Only runs with {@code -PvoxelBench}; everything else passes at once.
 */
public final class VoxelDiskBench {
    private static final int RADIUS_CHUNKS = 6;
    /** The default {@code voxelRadius}: more than the dashboard asks for around one node, about what a base of several nodes asks for. */
    private static final int VIEW_RADIUS = 64;

    private VoxelDiskBench() {
    }

    private static void set(ServerLevel level, BlockPos.MutableBlockPos pos, BlockState state) {
        var chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state, false);
    }

    /** Terrain around {@code center}; the base's pad is at {@code pad}. Writes the sections directly (this is a lot of blocks). */
    private static void terrain(ServerLevel level, BlockPos center, int pad) {
        var random = new Random(26_1_2);
        int size = (RADIUS_CHUNKS * 2 + 1) * 8;
        var pos = new BlockPos.MutableBlockPos();
        var ores = new BlockState[]{Blocks.COAL_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(), Blocks.COPPER_ORE.defaultBlockState(),
            Blocks.GOLD_ORE.defaultBlockState(), Blocks.REDSTONE_ORE.defaultBlockState(), Blocks.DIAMOND_ORE.defaultBlockState()};
        int minY = level.getMinY();
        var heights = new int[size * 2][size * 2];
        for (int dx = -size; dx < size; dx++) {
            for (int dz = -size; dz < size; dz++) {
                int x = center.getX() + dx, z = center.getZ() + dz;
                double d = Math.sqrt(dx * dx + dz * dz);
                double hill = 66 + 7 * Math.sin(x / 17.0) + 6 * Math.sin(z / 23.0) + 3 * Math.sin((x + z) / 7.0) + 1.5 * Math.sin((x - 2 * z) / 3.0);
                double t = Math.min(1, Math.max(0, (d - 14) / 8));
                int h = (int) Math.round(pad * (1 - t) + hill * t);
                heights[dx + size][dz + size] = h;
                for (int y = minY; y <= Math.max(h, 62); y++) {
                    pos.set(x, y, z);
                    BlockState state;
                    if (y > h) state = Blocks.WATER.defaultBlockState();
                    else if (y == h) state = h >= 62 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.SAND.defaultBlockState();
                    else if (y >= h - 3) state = Blocks.DIRT.defaultBlockState();
                    else if (y < 0) state = Blocks.DEEPSLATE.defaultBlockState();
                    else state = random.nextInt(60) == 0 ? ores[random.nextInt(ores.length)] : Blocks.STONE.defaultBlockState();
                    set(level, pos, state);
                }
            }
        }
        // caves
        for (int i = 0; i < 90; i++) {
            int cx = center.getX() + random.nextInt(size * 2) - size, cz = center.getZ() + random.nextInt(size * 2) - size, cy = 20 + random.nextInt(50);
            int r = 3 + random.nextInt(4);
            for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
                int ax = cx + dx, az = cz + dz;
                if (dx * dx + dy * dy + dz * dz > r * r || Math.abs(ax - center.getX()) >= size || Math.abs(az - center.getZ()) >= size) continue;
                set(level, pos.set(ax, cy + dy, az), Blocks.AIR.defaultBlockState());
            }
        }
        // trees and short grass
        for (int i = 0; i < 700; i++) {
            int dx = random.nextInt(size * 2 - 6) - size + 3, dz = random.nextInt(size * 2 - 6) - size + 3;
            if (dx * dx + dz * dz < 26 * 26) continue;
            int x = center.getX() + dx, z = center.getZ() + dz, h = heights[dx + size][dz + size];
            if (h < 63) continue;
            if (i % 5 != 0) {
                set(level, pos.set(x, h + 1, z), Blocks.SHORT_GRASS.defaultBlockState());
                continue;
            }
            for (int y = 1; y <= 5; y++) set(level, pos.set(x, h + y, z), Blocks.OAK_LOG.defaultBlockState());
            for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) for (int c = 3; c <= 6; c++) {
                if (a == 0 && b == 0 && c <= 5) continue;
                if (Math.abs(a) + Math.abs(b) + (c == 6 ? 2 : 0) <= 3) set(level, pos.set(x + a, h + c, z + b), Blocks.OAK_LEAVES.defaultBlockState());
            }
        }
        // the base: a floor, walls with windows, lamps
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                set(level, pos.set(center.getX() + dx, pad, center.getZ() + dz), Blocks.SPRUCE_PLANKS.defaultBlockState());
                boolean wall = Math.abs(dx) == 12 || Math.abs(dz) == 12;
                for (int y = 1; y <= 6; y++) {
                    if (wall) set(level, pos.set(center.getX() + dx, pad + y, center.getZ() + dz), (dx + dz) % 4 == 0 && y >= 3 && y <= 4 ? Blocks.GLASS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                }
                set(level, pos.set(center.getX() + dx, pad + 7, center.getZ() + dz), (dx % 6 == 0 && dz % 6 == 0) ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
    }

    private static List<int[]> sections(BlockPos node, int minY, int maxY) {
        var out = new ArrayList<int[]>();
        double x = node.getX() + .5, y = node.getY() + 1, z = node.getZ() + .5;
        for (int cx = (node.getX() >> 4) - RADIUS_CHUNKS; cx <= (node.getX() >> 4) + RADIUS_CHUNKS; cx++) {
            for (int cz = (node.getZ() >> 4) - RADIUS_CHUNKS; cz <= (node.getZ() >> 4) + RADIUS_CHUNKS; cz++) {
                for (int cy = minY; cy <= maxY; cy++) if (VoxelService.distance(cx, cy, cz, x, y, z) <= VIEW_RADIUS) out.add(new int[]{cx, cy, cz});
            }
        }
        return out;
    }

    private record Run(CompletableFuture<?> future, long[] end, long start, long mainCpu, long workerCpu) {
        String numbers() {
            return String.format("server thread %.2f ms CPU, background %.1f ms CPU, answered after %.1f ms", mainCpu / 1e6, (MapStats.VOXELS.workerCpuNanos.sum() - workerCpu) / 1e6, (end[0] - start) / 1e6);
        }
    }

    private static Run ask(ServerLevel level, List<int[]> list, UUID id) {
        long worker = MapStats.VOXELS.workerCpuNanos.sum(), main = MapStats.VOXELS.mainCpuNanos.sum();
        var t0 = MapStats.start();
        var future = VoxelService.serve(level, list, new BitSet(), id);
        MapStats.VOXELS.main(t0);
        long[] end = new long[1];
        return new Run(future.whenComplete((r, e) -> end[0] = System.nanoTime()), end, t0[0], MapStats.VOXELS.mainCpuNanos.sum() - main, worker);
    }

    private static void log(String format, Object... args) {
        CCCloud.LOGGER.info("[voxel bench] " + format, args);
    }

    public static void run(GameTestHelper helper) {
        if (!Boolean.getBoolean("cccloud.voxelBench")) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        var server = level.getServer();
        String dim = level.dimension().identifier().toString();
        var origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = (origin.getX() + 32000) >> 4, chunkZ = (origin.getZ() + 32000) >> 4;
        int pad = 72;
        var center = new BlockPos((chunkX << 4) + 8, pad, (chunkZ << 4) + 8);
        var forced = new ArrayList<ChunkPos>();
        for (int dx = -RADIUS_CHUNKS - 1; dx <= RADIUS_CHUNKS + 1; dx++) for (int dz = -RADIUS_CHUNKS - 1; dz <= RADIUS_CHUNKS + 1; dz++) forced.add(new ChunkPos(chunkX + dx, chunkZ + dz));
        for (var c : forced) level.setChunkForced(c.x(), c.z(), true);
        long t0 = System.nanoTime();
        terrain(level, center, pad);
        log("terrain: {} ms", (System.nanoTime() - t0) / 1_000_000);
        var owner = InGameTestSupport.player(helper, "voxel_bench");
        var floor = new BlockPos(center.getX() - origin.getX(), center.getY() - origin.getY(), center.getZ() - origin.getZ());
        var placed = CloudGameTests.placeMainframe(helper, owner, floor);
        placed.setOwner(owner);
        placed.setEnabled(true);
        var id = placed.getMainframeId();
        helper.onEachTick(() -> {
            var mainframe = CloudNetwork.mainframe(id);
            if (mainframe != null) mainframe.energy().set(mainframe.energy().capacity());
        });
        var list = sections(placed.getBlockPos(), level.getMinSectionY(), level.getMaxSectionY());
        var disk = VoxelService.diskForTest(server);
        var root = server.getWorldPath(LevelResource.ROOT).resolve("cccloud/voxels").resolve(id.toString());
        var liveExposed = new long[1];
        var runs = new Run[1];
        java.util.function.Consumer<String> report = label -> {
            log("{}: {}", label, MapStats.report(server.getTickCount())[2]);
            long[] size = new long[3];
            try (var files = java.nio.file.Files.walk(root)) {
                files.filter(p -> p.getFileName().toString().endsWith(".bin")).forEach(p -> {
                    try {
                        long s = java.nio.file.Files.size(p);
                        size[0]++;
                        size[1] += s;
                        size[2] += (s + 4095) & ~4095L;
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            int stored = 0, shown = 0;
            long exposed = 0;
            for (int cx = chunkX - RADIUS_CHUNKS - 1; cx <= chunkX + RADIUS_CHUNKS + 1; cx++) {
                for (int cz = chunkZ - RADIUS_CHUNKS - 1; cz <= chunkZ + RADIUS_CHUNKS + 1; cz++) {
                    for (var e : disk.column(id, dim, cx, cz).entrySet()) {
                        stored++;
                        exposed += e.getValue().section().exposed();
                        if (e.getValue().section().exposed() > 0) shown++;
                    }
                }
            }
            log("{}: disk {} column files, {} sections ({} with blocks), {} exposed blocks, {} KB of data, {} KB on a 4 KB file system, {} B per stored section", label, size[0], stored, shown, exposed,
                size[1] / 1024, size[2] / 1024, stored == 0 ? 0 : size[1] / stored);
        };
        var sequence = helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(placed.isOnline() && VoxelAreas.points(id, dim).length > 0 && VoxelAreas.at(level.dimension(), chunkX, chunkZ) != null, "online, area indexed"));
        // nobody ever looked at this area: the unload alone fills the cache
        sequence = unload(sequence, helper, level, forced, id, chunkX, chunkZ, "first fill, nobody looking", report);
        sequence = load(sequence, helper, level, forced, id, chunkX, chunkZ);
        for (var label : List.of("live, memory cold", "live, memory cold", "live, memory warm")) {
            sequence = sequence.thenExecute(() -> {
                if (label.endsWith("cold")) VoxelService.clearForTest();
                runs[0] = ask(level, list, id);
            }).thenWaitUntil(() -> helper.assertTrue(runs[0].future().isDone(), "done")).thenExecute(() -> {
                long exposed = 0;
                for (var s : (List<?>) runs[0].future().join()) if (s instanceof VoxelService.Served served) exposed += served.data().exposed();
                liveExposed[0] = exposed;
                log("{}: {} sections, {} exposed blocks, {}", label, list.size(), exposed, runs[0].numbers());
            }).thenIdle(5);
        }
        sequence = sequence.thenWaitUntil(() -> helper.assertTrue(VoxelService.idleForTest(server), "written"));
        sequence = unload(sequence, helper, level, forced, id, chunkX, chunkZ, "second unload, after the live view", report);
        for (var label : List.of("from disk, memory cold", "from disk, memory cold", "from disk, memory cold", "from memory")) {
            sequence = sequence.thenExecute(() -> {
                if (label.endsWith("cold")) VoxelService.clearForTest();
                runs[0] = ask(level, list, id);
            }).thenWaitUntil(() -> helper.assertTrue(runs[0].future().isDone(), "done")).thenExecute(() -> {
                long known = 0, exposed = 0;
                for (var s : (List<?>) runs[0].future().join()) {
                    if (s instanceof VoxelService.Served served) {
                        known++;
                        exposed += served.data().exposed();
                    }
                }
                log("{}: {} sections ({} known, {} exposed blocks), {}", label, list.size(), known, exposed, runs[0].numbers());
            }).thenIdle(5);
        }
        sequence = load(sequence, helper, level, forced, id, chunkX, chunkZ);
        sequence = unload(sequence, helper, level, forced, id, chunkX, chunkZ, "third unload, JIT warm", report);
        sequence.thenExecute(() -> {
            // what every other chunk of the world pays: a lookup that finds no area
            var threads = java.lang.management.ManagementFactory.getThreadMXBean();
            int rounds = 4_000_000, hits = 0;
            long cpu = threads.getCurrentThreadCpuTime();
            for (int i = 0; i < rounds; i++) if (VoxelAreas.at(level.dimension(), chunkX + 5000 + (i & 1023), chunkZ + 5000 + (i >> 10)) != null) hits++;
            log("unload outside every area: {} ns per lookup ({} hits)", (threads.getCurrentThreadCpuTime() - cpu) / (double) rounds, hits);
            ai.ezfox.cccloud.block.MainframeRetention.purge(server, id);
        }).thenSucceed();
    }

    private static net.minecraft.gametest.framework.GameTestSequence unload(net.minecraft.gametest.framework.GameTestSequence sequence, GameTestHelper helper, ServerLevel level, List<ChunkPos> forced, UUID id,
                                                                            int chunkX, int chunkZ, String label, java.util.function.Consumer<String> report) {
        var server = level.getServer();
        return sequence.thenExecute(() -> {
                MapStats.reset(server.getTickCount());
                for (var c : forced) level.setChunkForced(c.x(), c.z(), false);
            })
            .thenWaitUntil(() -> helper.assertTrue(level.getChunkSource().getChunkNow(chunkX, chunkZ) == null && CloudNetwork.mainframe(id) == null
                && MapStats.LAST_KNOWN.snapshots.sum() >= 60, "unloaded: " + MapStats.LAST_KNOWN.snapshots.sum()))
            .thenIdle(40)
            .thenWaitUntil(() -> helper.assertTrue(VoxelService.idleForTest(server), "built"))
            .thenExecute(() -> report.accept(label));
    }

    private static net.minecraft.gametest.framework.GameTestSequence load(net.minecraft.gametest.framework.GameTestSequence sequence, GameTestHelper helper, ServerLevel level, List<ChunkPos> forced, UUID id,
                                                                          int chunkX, int chunkZ) {
        return sequence.thenExecute(() -> {
                for (var c : forced) level.setChunkForced(c.x(), c.z(), true);
            })
            .thenWaitUntil(() -> {
                for (var c : forced) helper.assertTrue(level.getChunkSource().getChunkNow(c.x(), c.z()) != null, "loading");
                var mainframe = CloudNetwork.mainframe(id);
                helper.assertTrue(mainframe != null && mainframe.isOnline(), "mainframe back");
            })
            .thenIdle(20);
    }
}
