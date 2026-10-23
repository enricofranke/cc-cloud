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
                result[0] = MinecraftBridge.voxels(server, access, mainframe.getMainframeId(), dim, cx, cy, cz);
                var far = MinecraftBridge.voxels(server, access, mainframe.getMainframeId(), dim, cx + 20, cy, cz).getNow(null);
                helper.assertTrue(far != null && far.status() == 403 && "out_of_range".equals(far.error()), "only near the network: " + far);
                mainframe.setRole(new NameAndId(viewer, "viewer"), Role.VIEWER);
                var asViewer = MinecraftBridge.voxels(server, Access.resolver(server).resolve(viewer, mainframe.getMainframeId()),
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
            })
            .thenSucceed();
    }
}
