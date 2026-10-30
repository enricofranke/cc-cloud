package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.voxel.VoxelSection;
import ai.ezfox.cccloud.voxel.VoxelService;
import ai.ezfox.cccloud.web.GameBridge;
import ai.ezfox.cccloud.web.MinecraftBridge;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.GameType;
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
        VoxelService.clearForTest();
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
                VoxelService.clearForTest();
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
}
