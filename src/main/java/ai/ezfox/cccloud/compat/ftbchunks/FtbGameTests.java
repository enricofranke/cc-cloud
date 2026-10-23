package ai.ezfox.cccloud.compat.ftbchunks;

import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;

/**
 * FTB Chunks: the owner claims the test chunk; the mainframe's "Team claims" card counts it and its snapshot carries
 * the chunk in the {@code claims} overlay, restricted to canControl devices.
 */
public final class FtbGameTests {
    private FtbGameTests() {
    }

    public static void claims(GameTestHelper helper) {
        var owner = ai.ezfox.cccloud.compat.ftbteams.FtbTeamsGameTests.player(helper, "cccloud_claimer");
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setOwner(owner);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var chunk = ChunkPos.containing(helper.absolutePos(new BlockPos(2, 1, 2)));
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(FTBChunksAPI.api().isManagerLoaded(), "FTB Chunks manager"))
            .thenWaitUntil(() -> helper.assertTrue(MainframeCards.section(mainframe.getMainframeId(), ClaimsCard.KIND) != null, "claims card"))
            .thenExecute(() -> {
                var result = FTBChunksAPI.api().claimAsPlayer(owner, helper.getLevel().dimension(), chunk, false);
                helper.assertTrue(result.isSuccess(), "claim: " + result);
            })
            .thenWaitUntil(() -> {
                var section = MainframeCards.section(mainframe.getMainframeId(), ClaimsCard.KIND);
                var snapshot = section.snapshot();
                helper.assertTrue(snapshot.get("restricted").getAsBoolean(), "claims are canControl-only");
                var chunks = snapshot.getAsJsonObject("claims").getAsJsonArray("chunks");
                boolean found = false;
                for (var c : chunks) {
                    var o = c.getAsJsonObject();
                    if (o.get("x").getAsInt() == chunk.x() && o.get("z").getAsInt() == chunk.z()) found = true;
                }
                helper.assertTrue(found, "overlay has the claimed chunk: " + chunks);
                var claimed = section.values().get("claimed").getAsString();
                helper.assertTrue(claimed.startsWith(chunks.size() + ""), "claimed label: " + claimed);
            })
            .thenSucceed();
    }
}
