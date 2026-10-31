package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.block.MainframeRetention;
import ai.ezfox.cccloud.ingame.InGameTestSupport;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.voxel.VoxelAreas;
import ai.ezfox.cccloud.voxel.VoxelBatch;
import ai.ezfox.cccloud.voxel.VoxelDisk;
import ai.ezfox.cccloud.voxel.VoxelSection;
import ai.ezfox.cccloud.voxel.VoxelService;
import ai.ezfox.cccloud.web.GameBridge;
import ai.ezfox.cccloud.web.MinecraftBridge;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Voxel view sections of a real world: an ore enclosed in stone is not sent (no X-ray) while the stone shell is,
 * sections far from the network and viewers are refused, and the encoding decodes back.
 */
public final class VoxelTests {
    private VoxelTests() {
    }

    public static void exposedOnly(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        CloudGameTests.keepPowered(helper, mainframe);
        var center = new BlockPos(5, 3, 5);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) helper.setBlock(center.offset(dx, dy, dz), Blocks.STONE);
        helper.setBlock(center, Blocks.DIAMOND_ORE);
        // M4: an ore that touches air: shown near the mainframe's nodes, stone for a dashboard without nodes there
        var exposedOre = new BlockPos(8, 3, 8);
        helper.setBlock(exposedOre, Blocks.GOLD_ORE);
        var oreAbs = helper.absolutePos(exposedOre);
        @SuppressWarnings("unchecked") CompletableFuture<com.google.gson.JsonObject>[] oreViews = new CompletableFuture[2];
        var abs = helper.absolutePos(center);
        String dim = helper.getLevel().dimension().identifier().toString();
        int cx = abs.getX() >> 4, cy = abs.getY() >> 4, cz = abs.getZ() >> 4;
        @SuppressWarnings("unchecked") CompletableFuture<GameBridge.VoxelResult>[] result = new CompletableFuture[1];
        UUID viewer = UUID.randomUUID();
        VoxelService.clearForTest(null);
        // stage 2: stone has stored textures (as if a client had uploaded them), the ore never gets a texture request
        var textures = ai.ezfox.cccloud.voxel.BlockTextureService.store();
        var px = new int[256];
        java.util.Arrays.fill(px, 0xFF7F7F7F);
        textures.putSprite("minecraft:block/stone", ai.ezfox.cccloud.icons.PngCodec.encode(16, 16, px));
        String stoneSprite = "minecraft:block/stone";
        textures.putBlock("minecraft:stone", "gametest", new String[]{stoneSprite, stoneSprite, stoneSprite, stoneSprite, stoneSprite, stoneSprite}, 0);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline() && ai.ezfox.cccloud.network.NetworkService.result(mainframe.getMainframeId()) != null,
                "mainframe online with its network"))
            .thenExecute(() -> {
                var access = Access.resolver(server).resolve(owner.getUUID(), mainframe.getMainframeId());
                result[0] = MinecraftBridge.voxels(server, access, owner.getUUID(), mainframe.getMainframeId(), dim, cx, cy, cz);
                var far = MinecraftBridge.voxels(server, access, owner.getUUID(), mainframe.getMainframeId(), dim, cx + 20, cy, cz).getNow(null);
                helper.assertTrue(far != null && far.status() == 403 && "out_of_range".equals(far.error()), "only near the network: " + far);
                mainframe.setRole(new NameAndId(viewer, "viewer"), Role.VIEWER);
                var asViewer = MinecraftBridge.voxels(server, Access.resolver(server).resolve(viewer, mainframe.getMainframeId()), viewer,
                    mainframe.getMainframeId(), dim, cx, cy, cz).getNow(null);
                helper.assertTrue(asViewer != null && asViewer.status() == 403 && "not_allowed".equals(asViewer.error()), "viewers get no voxels: " + asViewer);
            })
            .thenWaitUntil(() -> helper.assertTrue(result[0].isDone(), "section encoded"))
            .thenExecute(() -> {
                var r = result[0].join();
                helper.assertTrue(r.status() == 200 && r.json() != null, "section: " + r.status() + " " + r.error());
                var json = r.json();
                var indices = VoxelSection.decode(json.get("runs").getAsString()); // round trip of the wire format
                var palette = json.getAsJsonArray("palette");
                int ore = indices[VoxelSection.index(abs.getX() & 15, abs.getY() & 15, abs.getZ() & 15)];
                helper.assertTrue(ore == 0, "the enclosed ore is not sent");
                helper.assertTrue(!palette.toString().contains("diamond_ore"), "no ore in the palette: " + palette);
                var top = abs.above();
                int shell = indices[VoxelSection.index(top.getX() & 15, top.getY() & 15, top.getZ() & 15)];
                helper.assertTrue(shell > 0 && palette.get(shell - 1).getAsJsonObject().get("id").getAsString().equals("minecraft:stone"),
                    "the stone shell above it is exposed and sent");
                helper.assertTrue(json.get("exposed").getAsInt() > 0, "exposed count");
                var stone = palette.get(shell - 1).getAsJsonObject();
                helper.assertTrue(stone.has("tex") && stone.getAsJsonArray("tex").size() == 6 && stone.getAsJsonArray("tex").get(1).getAsInt() >= 0,
                    "stage 2: textured faces: " + stone);
                helper.assertTrue(json.has("atlas") && json.getAsJsonObject("atlas").get("url").getAsString().startsWith("/api/voxel-atlas/"), "atlas: " + json.get("atlas"));
                var px2 = ai.ezfox.cccloud.icons.PngCodec.size(textures.atlas());
                helper.assertTrue(px2 != null && px2[0] >= 16, "atlas image");
                oreViews[0] = VoxelService.section(helper.getLevel(), oreAbs.getX() >> 4, oreAbs.getY() >> 4, oreAbs.getZ() >> 4, mainframe.getMainframeId());
                oreViews[1] = VoxelService.section(helper.getLevel(), oreAbs.getX() >> 4, oreAbs.getY() >> 4, oreAbs.getZ() >> 4, UUID.randomUUID());
            })
            .thenWaitUntil(() -> helper.assertTrue(oreViews[0] != null && oreViews[1] != null && oreViews[0].isDone() && oreViews[1].isDone(), "ore sections encoded"))
            .thenExecute(() -> {
                int i = VoxelSection.index(oreAbs.getX() & 15, oreAbs.getY() & 15, oreAbs.getZ() & 15);
                var near = oreViews[0].join();
                var nearIdx = VoxelSection.decode(near.get("runs").getAsString())[i];
                helper.assertTrue(nearIdx > 0 && near.getAsJsonArray("palette").get(nearIdx - 1).getAsJsonObject().get("id").getAsString().equals("minecraft:gold_ore"),
                    "an ore touching air near the network is shown");
                var far = oreViews[1].join();
                var farIdx = VoxelSection.decode(far.get("runs").getAsString())[i];
                helper.assertTrue(farIdx > 0 && far.getAsJsonArray("palette").get(farIdx - 1).getAsJsonObject().get("id").getAsString().equals("minecraft:stone"),
                    "without own nodes nearby the ore is shown as stone: " + far.getAsJsonArray("palette"));
            })
            .thenSucceed();
    }

    /**
     * Changed sections are rebuilt, unchanged ones come from the cache: the cache is validated by the section's
     * content, so even a change that fires no neighbor update (flag 2 only, like block movers) shows on the next
     * request, and a dashboard that sends the current hash gets "same".
     */
    public static void dirtyInvalidation(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        CloudGameTests.keepPowered(helper, mainframe);
        // a quiet section above the test structures: other tests running next to this one must not change it or the
        // touching layers of its neighbors (the cache key), so the spot is the third block of the section above
        int originY = helper.absolutePos(BlockPos.ZERO).getY();
        int sectionY = Math.floorDiv(originY + 13, 16) + 1;
        var spot = new BlockPos(6, sectionY * 16 + 2 - originY, 6);
        helper.setBlock(spot, Blocks.STONE);
        var abs = helper.absolutePos(spot);
        var level = helper.getLevel();
        String dim = level.dimension().identifier().toString();
        int cx = abs.getX() >> 4, cy = abs.getY() >> 4, cz = abs.getZ() >> 4;
        int i = VoxelSection.index(abs.getX() & 15, abs.getY() & 15, abs.getZ() & 15);
        var section = java.util.List.of(new int[]{cx, cy, cz});
        @SuppressWarnings("unchecked") CompletableFuture<java.util.List<VoxelSection.Encoded>>[] step = new CompletableFuture[4];
        @SuppressWarnings("unchecked") CompletableFuture<GameBridge.VoxelBatchResult>[] batch = new CompletableFuture[1];
        java.util.function.Function<Integer, String> idAt = n -> {
            var e = step[n].join().getFirst();
            int k = VoxelSection.decode(e.runs())[i];
            return k == 0 ? "nothing" : e.palette().get(k - 1).id();
        };
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline() && ai.ezfox.cccloud.network.NetworkService.result(mainframe.getMainframeId()) != null,
                "mainframe online with its network"))
            .thenExecute(() -> {
                VoxelService.clearForTest(mainframe.getMainframeId());
                step[0] = VoxelService.encode(level, section, mainframe.getMainframeId());
            })
            .thenWaitUntil(() -> helper.assertTrue(step[0] != null && step[0].isDone(), "built"))
            .thenExecute(() -> step[1] = VoxelService.encode(level, section, mainframe.getMainframeId()))
            .thenWaitUntil(() -> helper.assertTrue(step[1] != null && step[1].isDone(), "asked again"))
            .thenExecute(() -> {
                helper.assertTrue(step[1].join().getFirst() == step[0].join().getFirst(), "an unchanged section comes from the cache");
                helper.assertTrue(idAt.apply(1).equals("minecraft:stone"), "stone at the spot: " + idAt.apply(1));
                helper.setBlock(spot, Blocks.GOLD_BLOCK); // a normal change (neighbor updates)
                step[2] = VoxelService.encode(level, section, mainframe.getMainframeId());
            })
            .thenWaitUntil(() -> helper.assertTrue(step[2] != null && step[2].isDone(), "rebuilt"))
            .thenExecute(() -> {
                helper.assertTrue(step[2].join().getFirst() != step[1].join().getFirst(), "the changed section was rebuilt");
                helper.assertTrue(idAt.apply(2).equals("minecraft:gold_block"), "the new block shows: " + idAt.apply(2));
                // flag 2 only: no neighbor notification at all, still visible on the next request
                level.setBlock(abs, Blocks.DIAMOND_BLOCK.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                step[3] = VoxelService.encode(level, section, mainframe.getMainframeId());
            })
            .thenWaitUntil(() -> helper.assertTrue(step[3] != null && step[3].isDone(), "rebuilt without neighbor updates"))
            .thenExecute(() -> {
                helper.assertTrue(idAt.apply(3).equals("minecraft:diamond_block"), "a silent change shows too: " + idAt.apply(3));
                var access = Access.resolver(server).resolve(owner.getUUID(), mainframe.getMainframeId());
                var have = new java.util.ArrayList<String>();
                have.add(step[3].join().getFirst().hashHex());
                batch[0] = MinecraftBridge.voxelBatch(server, access, owner.getUUID(), mainframe.getMainframeId(), dim, section, have, false);
            })
            .thenWaitUntil(() -> helper.assertTrue(batch[0] != null && batch[0].isDone(), "batch answered"))
            .thenExecute(() -> {
                var r = batch[0].join();
                helper.assertTrue(r.status() == 200 && r.body() != null, "batch: " + r.status() + " " + r.error());
                var read = ai.ezfox.cccloud.voxel.VoxelBatch.read(r.body());
                helper.assertTrue(read.size() == 1 && read.getFirst().status() == ai.ezfox.cccloud.voxel.VoxelBatch.Status.SAME,
                    "the dashboard's current hash gets same: " + read);
                helper.setBlock(spot, Blocks.AIR); // outside the structure: not cleaned up by the framework
            })
            .thenSucceed();
    }

    private static String idAt(VoxelBatch.Decoded section, BlockPos pos) {
        int k = section.cells()[VoxelSection.index(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15)];
        return k == 0 ? "nothing" : section.palette().get(k - 1).get("id").getAsString();
    }

    private static boolean hasKind(VoxelSection.Encoded section, String id) {
        return section.palette().stream().anyMatch(k -> k.id().equals(id));
    }

    private static boolean ready(CompletableFuture<?> future) {
        return future != null && future.isDone();
    }

    /** A batch for the owner, as the route asks for it (the mainframe's chunk may be unloaded). */
    private static CompletableFuture<GameBridge.VoxelBatchResult> ask(MinecraftServer server, UUID player, UUID mainframe, String dim, int[] section, String have) {
        var access = Access.resolver(server).resolve(player, mainframe);
        return MinecraftBridge.voxelBatch(server, access, player, mainframe, dim, List.of(section), Arrays.asList(have), false);
    }

    private static VoxelBatch.Decoded answer(GameTestHelper helper, CompletableFuture<GameBridge.VoxelBatchResult> future) {
        var r = future.join();
        helper.assertTrue(r.status() == 200 && r.body() != null, "batch: " + r.status() + " " + r.error());
        return VoxelBatch.read(r.body()).getFirst();
    }

    /**
     * Over the tick limit an unloaded chunk waits in the queue instead of costing the tick, and nothing of it is lost: the
     * block nobody asked for reaches the disk once the queue runs, long after the chunk went.
     */
    public static void lastKnownDeferred(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        String dim = level.dimension().identifier().toString();
        var origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = (origin.getX() + 20000) >> 4, chunkZ = (origin.getZ() + 16000) >> 4;
        var floor = new BlockPos((chunkX << 4) + 2 - origin.getX(), 1, (chunkZ << 4) + 2 - origin.getZ());
        var forced = new ArrayList<ChunkPos>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) forced.add(new ChunkPos(chunkX + dx, chunkZ + dz));
        for (var c : forced) level.setChunkForced(c.x(), c.z(), true);
        var owner = InGameTestSupport.player(helper, "vlk_deferred");
        var mainframe = CloudGameTests.placeMainframe(helper, owner, floor);
        mainframe.setOwner(owner);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var id = mainframe.getMainframeId();
        var spot = helper.absolutePos(floor).offset(5, 2, 5);
        int[] section = {spot.getX() >> 4, spot.getY() >> 4, spot.getZ() >> 4};
        var disk = VoxelService.diskForTest(server);
        long[] before = new long[1];
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline() && VoxelAreas.points(id, dim).length > 0, "mainframe online with its area"))
            .thenExecute(() -> {
                level.setBlock(spot, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                before[0] = ai.ezfox.cccloud.map.MapStats.LAST_KNOWN.deferred.sum();
                VoxelService.deferAllForTest(true);
                for (var c : forced) level.setChunkForced(c.x(), c.z(), false);
            })
            .thenWaitUntil(() -> helper.assertTrue(level.getChunkSource().getChunkNow(chunkX, chunkZ) == null && CloudNetwork.mainframe(id) == null
                && ai.ezfox.cccloud.map.MapStats.LAST_KNOWN.deferred.sum() > before[0], "the chunk went and waits"))
            .thenIdle(40)
            .thenExecute(() -> {
                var stored = disk.column(id, dim, section[0], section[2]).get(section[1]);
                boolean early = stored != null && hasKind(stored.section(), "minecraft:diamond_block");
                VoxelService.deferAllForTest(false);
                helper.assertTrue(!early, "a chunk over the limit costs nothing until the queue runs");
            })
            .thenWaitUntil(() -> {
                var stored = disk.column(id, dim, section[0], section[2]).get(section[1]);
                helper.assertTrue(VoxelService.idleForTest(server) && stored != null && hasKind(stored.section(), "minecraft:diamond_block"),
                    "the queue filled the cache: " + ai.ezfox.cccloud.map.MapStats.report(0)[2]);
            })
            .thenExecute(() -> ai.ezfox.cccloud.block.MainframeRetention.purge(server, id))
            .thenSucceed();
    }

    /**
     * The whole path of Enrico's report with real chunks: a mainframe far from the test structures unloads with its
     * chunks, and the route still answers with the terrain from memory and from disk, including a block nobody asked for
     * before the chunk went (the snapshot of the unload). Ores stay stone, claims and the area still apply, a removed
     * mainframe shows nothing, and the purge leaves nothing behind.
     */
    public static void lastKnown(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        String dim = level.dimension().identifier().toString();
        var origin = helper.absolutePos(BlockPos.ZERO);
        // block 2 2 of a chunk far from everything, so these chunks really unload
        int chunkX = (origin.getX() + 16000) >> 4, chunkZ = (origin.getZ() + 16000) >> 4;
        var floor = new BlockPos((chunkX << 4) + 2 - origin.getX(), 1, (chunkZ << 4) + 2 - origin.getZ());
        var forced = new ArrayList<ChunkPos>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) forced.add(new ChunkPos(chunkX + dx, chunkZ + dz));
        for (var c : forced) level.setChunkForced(c.x(), c.z(), true);
        var owner = InGameTestSupport.player(helper, "vlk_owner");
        var mainframe = CloudGameTests.placeMainframe(helper, owner, floor);
        mainframe.setOwner(owner);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var id = mainframe.getMainframeId();
        var base = helper.absolutePos(floor);
        var ore = new BlockPos((chunkX << 4) + 7, base.getY() + 2, (chunkZ << 4) + 7);
        var spot = ore.east();
        level.setBlock(ore, Blocks.GOLD_ORE.defaultBlockState(), Block.UPDATE_ALL);
        int[] section = {ore.getX() >> 4, ore.getY() >> 4, ore.getZ() >> 4};
        @SuppressWarnings("unchecked") CompletableFuture<GameBridge.VoxelBatchResult>[] batch = new CompletableFuture[6];
        String[] hash = new String[2];
        var disk = VoxelService.diskForTest(server);
        var claimTeam = new ai.ezfox.cccloud.claims.ClaimProvider.Team[1];
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline() && VoxelAreas.points(id, dim).length > 0, "mainframe online with its area"))
            .thenExecute(() -> batch[0] = ask(server, owner.getUUID(), id, dim, section, null))
            .thenWaitUntil(() -> helper.assertTrue(ready(batch[0]), "live answer"))
            .thenExecute(() -> {
                var live = answer(helper, batch[0]);
                helper.assertTrue(live.status() == VoxelBatch.Status.OK && !live.old(), "a loaded chunk is read live: " + live.status());
                helper.assertTrue(idAt(live, ore).equals("minecraft:gold_ore"), "an ore touching air near the mainframe shows live: " + idAt(live, ore));
                hash[0] = live.hash();
            })
            .thenWaitUntil(() -> helper.assertTrue(VoxelService.idleForTest(server) && disk.column(id, dim, section[0], section[2]).containsKey(section[1]), "built sections go to disk"))
            .thenExecute(() -> {
                var stored = disk.column(id, dim, section[0], section[2]).get(section[1]).section();
                helper.assertTrue(!hasKind(stored, "minecraft:gold_ore") && hasKind(stored, "minecraft:stone"), "the disk never holds an ore: " + stored.palette());
                // nobody asks for this block before the chunk goes: only the snapshot of the unload can know it
                level.setBlock(spot, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                for (var c : forced) level.setChunkForced(c.x(), c.z(), false);
            })
            .thenWaitUntil(() -> helper.assertTrue(level.getChunkSource().getChunkNow(chunkX, chunkZ) == null && CloudNetwork.mainframe(id) == null
                && NetworkService.result(id) == null, "the mainframe's chunk is unloaded"))
            .thenWaitUntil(() -> {
                var stored = disk.column(id, dim, section[0], section[2]).get(section[1]);
                helper.assertTrue(VoxelService.idleForTest(server) && stored != null && hasKind(stored.section(), "minecraft:diamond_block"),
                    "the unload filled the cache: " + ai.ezfox.cccloud.map.MapStats.report(0)[2]);
            })
            .thenExecute(() -> {
                var view = VoxelService.view(server, id, dim);
                helper.assertTrue(!view.liveAt(section[0], section[1], section[2]) && view.knownAt(section[0], section[1], section[2]) && !view.unknown(),
                    "no live view, but the last known area");
                batch[1] = ask(server, owner.getUUID(), id, dim, section, hash[0]);
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(batch[1]), "answer from memory"))
            .thenExecute(() -> {
                var fromMemory = answer(helper, batch[1]);
                helper.assertTrue(fromMemory.status() == VoxelBatch.Status.OK && fromMemory.old(), "last known: " + fromMemory.status() + " " + fromMemory.old());
                helper.assertTrue(idAt(fromMemory, ore).equals("minecraft:stone"), "the X-ray guard holds for last known terrain: " + idAt(fromMemory, ore));
                helper.assertTrue(idAt(fromMemory, spot).equals("minecraft:diamond_block"), "the block of the last visit: " + idAt(fromMemory, spot));
                hash[1] = fromMemory.hash();
                VoxelService.clearForTest(id);
                batch[2] = ask(server, owner.getUUID(), id, dim, section, null);
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(batch[2]), "answer from disk"))
            .thenExecute(() -> {
                var fromDisk = answer(helper, batch[2]);
                helper.assertTrue(fromDisk.status() == VoxelBatch.Status.OK && fromDisk.old() && hash[1].equals(fromDisk.hash()), "disk and memory agree: " + fromDisk.hash());
                helper.assertTrue(idAt(fromDisk, ore).equals("minecraft:stone") && idAt(fromDisk, spot).equals("minecraft:diamond_block"), "the same from disk");
                batch[3] = ask(server, owner.getUUID(), id, dim, section, hash[1]);
                // another team claims the chunk: it goes for this player, whatever is cached
                var claims = TestClaims.get();
                claimTeam[0] = claims.team("voxel_last_known", java.util.Set.of(owner.getUUID()));
                claims.claim(level, ore, claimTeam[0]);
                batch[4] = ask(server, owner.getUUID(), id, dim, section, null);
                claims.forget(level, claimTeam[0]);
                var far = ask(server, owner.getUUID(), id, dim, new int[]{section[0] + 40, section[1], section[2]}, null).join();
                helper.assertTrue(VoxelBatch.read(far.body()).getFirst().status() == VoxelBatch.Status.OUT_OF_RANGE, "nothing outside the last known area");
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(batch[3]) && ready(batch[4]), "answers"))
            .thenExecute(() -> {
                var same = answer(helper, batch[3]);
                helper.assertTrue(same.status() == VoxelBatch.Status.SAME && same.old(), "the browser's copy is current, and last known: " + same.status());
                helper.assertTrue(answer(helper, batch[4]).status() == VoxelBatch.Status.CLAIMED, "a claim hides cached terrain too");
                // a restart: the area and the map come back from the file
                helper.assertTrue(disk.flush(5000), "written");
                VoxelAreas.load(server, disk);
                helper.assertTrue(VoxelAreas.known(id), "the area is back");
                var net = ai.ezfox.cccloud.web.WebHub.INSTANCE.networkForTest(id, true);
                helper.assertTrue(net != null && net.getAsJsonArray("nodes").get(0).getAsJsonObject().get("status").getAsString().equals("offline"), "the map is back, offline: " + net);
                helper.assertTrue(net.getAsJsonArray("nodes").get(0).getAsJsonObject().has("pos"), "with positions for those who may see them");
                var stripped = ai.ezfox.cccloud.web.WebHub.INSTANCE.networkForTest(id, false);
                helper.assertTrue(stripped != null && !stripped.toString().contains("\"pos\""), "and without for the others");
                // removed: its view ends at once, also from the caches
                var retention = MainframeRetention.get(server);
                retention.removed(id, new MainframeRetention.Entry(java.util.Optional.of(owner.getUUID()), "vlk_owner", System.currentTimeMillis(), GlobalPos.of(level.dimension(), base),
                    false, false, true, Map.of(), ""));
                batch[5] = ask(server, owner.getUUID(), id, dim, section, null);
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(batch[5]), "answer"))
            .thenExecute(() -> {
                helper.assertTrue(answer(helper, batch[5]).status() == VoxelBatch.Status.OUT_OF_RANGE, "a removed mainframe shows no terrain");
                var dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("cccloud/voxels").resolve(id.toString());
                helper.assertTrue(java.nio.file.Files.exists(dir), "kept during the retention");
                MainframeRetention.purge(server, id);
                helper.assertTrue(!VoxelAreas.known(id), "the area is gone");
                helper.assertTrue(disk.column(id, dim, section[0], section[2]).isEmpty(), "nothing readable any more");
            })
            .thenWaitUntil(() -> helper.assertTrue(disk.flush(100) && !java.nio.file.Files.exists(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("cccloud/voxels").resolve(id.toString())), "the purge deletes its folder"))
            .thenSucceed();
    }

    /**
     * Ores near a node depend on the nodes, so the last known path never shows them: with the node in place (from memory
     * and from disk), after the node is gone (the variant does not change, and the live path follows), and for a file
     * that holds an ore anyway (an ore tag that grew after the section was stored).
     */
    public static void lastKnownOres(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        String dim = level.dimension().identifier().toString();
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var id = mainframe.getMainframeId();
        var relayPos = mainframe.getBlockPos().above(48);
        level.setBlock(relayPos, ai.ezfox.cccloud.ModRegistry.CLOUD_RELAY.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(relayPos) instanceof ai.ezfox.cccloud.relay.CloudRelayBlockEntity relay) {
            relay.fillForTest();
            relay.restrictForTest(id);
        }
        // within 16 blocks of the relay and far from the mainframe, in the section above the relay's (which changes with it)
        var ore = new BlockPos(relayPos.getX(), ((relayPos.getY() >> 4) + 1) * 16, relayPos.getZ());
        level.setBlock(ore, Blocks.GOLD_ORE.defaultBlockState(), Block.UPDATE_ALL);
        int cx = ore.getX() >> 4, cy = ore.getY() >> 4, cz = ore.getZ() >> 4;
        var section = List.of(new int[]{cx, cy, cz});
        var stale = new BitSet();
        stale.set(0);
        var live = new BitSet();
        @SuppressWarnings("unchecked") CompletableFuture<List<VoxelService.Served>>[] step = new CompletableFuture[6];
        java.util.function.Function<Integer, String> idAt = n -> {
            var served = step[n].join().getFirst();
            helper.assertTrue(served != null, "step " + n + ": no section");
            int k = VoxelSection.decode(served.data().runs())[VoxelSection.index(ore.getX() & 15, ore.getY() & 15, ore.getZ() & 15)];
            return k == 0 ? "nothing" : served.data().palette().get(k - 1).id();
        };
        var disk = VoxelService.diskForTest(server);
        // the neighbor column holds a section with an ore, as a file written before the ore tag grew would
        int[] grid = new int[VoxelSection.GRID * VoxelSection.GRID * VoxelSection.GRID];
        grid[VoxelSection.gridIndex(1, 1, 1)] = 1;
        var withOre = VoxelSection.encodeGrid(grid, List.of(new VoxelSection.Kind("minecraft:gold_ore", 0xFFFFFF00, 0)));
        helper.startSequence()
            .thenWaitUntil(() -> {
                var result = NetworkService.result(id);
                helper.assertTrue(mainframe.isOnline() && result != null && result.reachable(((ai.ezfox.cccloud.relay.CloudRelayBlockEntity) level.getBlockEntity(relayPos)).relayId().toString()),
                    "the relay is part of the network");
            })
            .thenExecute(() -> {
                VoxelService.clearForTest(id);
                step[0] = VoxelService.serve(level, section, live, id);
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(step[0]), "live"))
            .thenExecute(() -> {
                helper.assertTrue(idAt.apply(0).equals("minecraft:gold_ore"), "shown live near the relay: " + idAt.apply(0));
                step[1] = VoxelService.serve(level, section, stale, id);
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(step[1]), "last known from memory"))
            .thenExecute(() -> {
                helper.assertTrue(step[1].join().getFirst().lastSeen(), "marked");
                helper.assertTrue(idAt.apply(1).equals("minecraft:stone"), "the memory cache has an ore-free variant: " + idAt.apply(1));
                helper.assertTrue(disk.put(id, dim, cx + 1, cz, Map.of(cy, new VoxelDisk.Stored(1, 1, 63, withOre))), "queued");
            })
            .thenWaitUntil(() -> helper.assertTrue(VoxelService.idleForTest(server) && disk.column(id, dim, cx, cz).containsKey(cy), "written"))
            .thenExecute(() -> {
                VoxelService.clearForTest(id);
                step[2] = VoxelService.serve(level, section, stale, id);
                step[3] = VoxelService.serve(level, List.of(new int[]{cx + 1, cy, cz}), stale, id);
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(step[2]) && ready(step[3]), "last known from disk"))
            .thenExecute(() -> {
                helper.assertTrue(idAt.apply(2).equals("minecraft:stone"), "the same from disk: " + idAt.apply(2));
                helper.assertTrue(step[3].join().getFirst() != null, "the section written with an ore is on disk");
                var strip = step[3].join().getFirst().data();
                helper.assertTrue(!hasKind(strip, "minecraft:gold_ore") && hasKind(strip, "minecraft:stone"), "a stored ore is stripped on the way out: " + strip.palette());
                level.setBlock(relayPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenWaitUntil(() -> {
                var result = NetworkService.result(id);
                helper.assertTrue(result != null && result.nodes().size() == 1, "the relay is gone: " + (result == null ? "?" : result.nodes().size()));
            })
            .thenExecute(() -> {
                step[4] = VoxelService.serve(level, section, live, id);
                step[5] = VoxelService.serve(level, section, stale, id);
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(step[4]) && ready(step[5]), "after the node change"))
            .thenExecute(() -> {
                helper.assertTrue(idAt.apply(4).equals("minecraft:stone"), "live: no node near it any more: " + idAt.apply(4));
                helper.assertTrue(idAt.apply(5).equals("minecraft:stone"), "last known: still none: " + idAt.apply(5));
                helper.assertTrue(step[5].join().getFirst().data().hash() == step[1].join().getFirst().data().hash(), "the last known variant does not depend on the nodes");
                level.setBlock(ore, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenSucceed();
    }

    /**
     * The last known area follows an online network at once, also with a device whose chunk is not loaded; a mainframe that
     * is loaded but offline reaches no nodes and keeps its area. Its chunk being loaded changes nothing: the route answers
     * with what the network saw, not with blocks that were placed where it no longer reaches.
     */
    public static void lastKnownArea(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        String dim = level.dimension().identifier().toString();
        var owner = InGameTestSupport.player(helper, "vlk_area");
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        mainframe.setOwner(owner);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var id = mainframe.getMainframeId();
        var link = new ai.ezfox.cccloud.item.MainframeLink(GlobalPos.of(level.dimension(), mainframe.getBlockPos()), id);
        var device = mainframe.getBlockPos().above(30);
        level.setBlock(device.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(device, ai.ezfox.cccloud.ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(ai.ezfox.cccloud.block.CloudUplinkBlock.FACING, net.minecraft.core.Direction.DOWN),
            Block.UPDATE_ALL);
        java.util.function.Predicate<BlockPos> inArea = pos -> Arrays.stream(VoxelAreas.points(id, dim)).anyMatch(p -> Math.floor(p[0]) == pos.getX() && Math.floor(p[1]) == pos.getY() && Math.floor(p[2]) == pos.getZ());
        var seen = mainframe.getBlockPos().above().east();
        var placedLater = seen.offset((seen.getX() & 15) == 15 ? -1 : 1, 0, 0);
        int[] section = {seen.getX() >> 4, seen.getY() >> 4, seen.getZ() >> 4};
        @SuppressWarnings("unchecked") CompletableFuture<GameBridge.VoxelBatchResult>[] batch = new CompletableFuture[2];
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline() && inArea.test(mainframe.getBlockPos().above()), "the mainframe's area"))
            .thenExecute(() -> {
                level.setBlock(seen, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                batch[0] = ask(server, owner.getUUID(), id, dim, section, null);
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(batch[0]), "live answer"))
            .thenExecute(() -> {
                var live = answer(helper, batch[0]);
                helper.assertTrue(!live.old() && idAt(live, seen).equals("minecraft:gold_block"), "read live: " + idAt(live, seen));
            })
            .thenExecute(() -> {
                if (!(level.getBlockEntity(device) instanceof ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity uplink)) throw helper.assertionException("uplink missing");
                uplink.bind(link, owner);
                helper.assertTrue(uplink.getLink() != null, "linked");
            })
            .thenWaitUntil(() -> helper.assertTrue(inArea.test(device), "a linked device joins the area"))
            .thenExecute(() -> {
                level.setBlock(device, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(device.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenWaitUntil(() -> helper.assertTrue(!inArea.test(device), "a device that was removed leaves it"))
            .thenExecute(() -> level.setBlock(mainframe.getBlockPos().west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL))
            .thenWaitUntil(() -> helper.assertTrue(!mainframe.isOnline(), "kill switch"))
            .thenExecute(() -> NetworkService.markDirty(id))
            .thenIdle(5)
            .thenExecute(() -> {
                helper.assertTrue(inArea.test(mainframe.getBlockPos().above()), "an offline mainframe keeps its area");
                var view = VoxelService.view(server, id, dim);
                helper.assertTrue(view.knownAt(mainframe.getBlockPos().getX() >> 4, mainframe.getBlockPos().getY() >> 4, mainframe.getBlockPos().getZ() >> 4), "the view still knows it");
                helper.assertTrue(!VoxelService.view(server, UUID.randomUUID(), dim).liveAt(0, 0, 0), "a mainframe nobody knows has no view");
                helper.assertTrue(VoxelService.view(server, UUID.randomUUID(), dim).unknown(), "and says so");
                level.setBlock(placedLater, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                batch[1] = ask(server, owner.getUUID(), id, dim, section, null);
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(batch[1]), "last known answer"))
            .thenExecute(() -> {
                var old = answer(helper, batch[1]);
                helper.assertTrue(old.old() && idAt(old, seen).equals("minecraft:gold_block"), "the last visit, though the chunk is loaded: " + old.old());
                helper.assertTrue(!idAt(old, placedLater).equals("minecraft:diamond_block"), "a block placed out of the network's reach does not show");
                level.setBlock(mainframe.getBlockPos().west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenSucceed();
    }
}
