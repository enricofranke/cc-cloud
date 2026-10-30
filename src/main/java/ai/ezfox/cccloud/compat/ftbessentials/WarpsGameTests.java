package ai.ezfox.cccloud.compat.ftbessentials;

import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import dev.ftb.mods.ftbessentials.util.FTBEWorldData;
import dev.ftb.mods.ftbessentials.util.TeleportPos;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/** FTB Essentials: a warp set on the server shows on the restricted Warps card with dimension and coordinates. */
public final class WarpsGameTests {
    private WarpsGameTests() {
    }

    public static void warps(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.CREATIVE);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        String name = "cccloud_test_warp";
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(MainframeCards.section(mainframe.getMainframeId(), WarpsCard.KIND) != null, "warps card"))
            .thenExecute(() -> {
                var data = FTBEWorldData.getInstance();
                helper.assertTrue(data != null, "FTB Essentials world data loaded");
                data.warpManager().addDestination(name, new TeleportPos(helper.getLevel().dimension(), at), null);
            })
            .thenWaitUntil(() -> {
                var section = MainframeCards.section(mainframe.getMainframeId(), WarpsCard.KIND);
                helper.assertTrue(section.snapshot().get("restricted").getAsBoolean(), "restricted section");
                var row = section.values().get(WarpsCard.ROW + 1);
                helper.assertTrue(row != null && row.getAsString().startsWith(name + " – ") && row.getAsString().endsWith(at.getX() + ", " + at.getY() + ", " + at.getZ()),
                    "warp row: " + row);
            })
            .thenExecute(() -> FTBEWorldData.getInstance().warpManager().deleteDestination(name))
            .thenSucceed();
    }
}
