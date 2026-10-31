package ai.ezfox.cccloud.compat.ftbranks;

import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import dev.ftb.mods.ftbranks.api.FTBRanksAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/** FTB Ranks: a rank created through the mod's API shows up on the restricted card, strongest first. */
public final class RanksGameTests {
    private static final String RANK = "cccloud_test";

    private RanksGameTests() {
    }

    public static void ranks(GameTestHelper helper) {
        var manager = FTBRanksAPI.manager();
        manager.getRank(RANK).ifPresent(rank -> manager.deleteRank(RANK));
        manager.createRank(RANK, 999_999, false);
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.startSequence()
            .thenWaitUntil(() -> {
                var section = MainframeCards.section(mainframe.getMainframeId(), RanksCard.KIND);
                helper.assertTrue(section != null, "ranks card");
                helper.assertTrue(section.snapshot().get("restricted").getAsBoolean(), "restricted section");
                var row = section.values().get(RanksCard.ROW + 1);
                helper.assertTrue(row != null && row.getAsString().startsWith(RANK + " (power 999999): "), "strongest rank first: " + row);
            })
            .thenExecute(() -> manager.deleteRank(RANK))
            .thenSucceed();
    }
}
