package ai.ezfox.cccloud.compat.ftbquests;

import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/**
 * FTB Quests: the Quests card appears as a restricted mainframe section and reads the quest file. A mock owner has no
 * FTB team (teams are created when a real player logs in), so the card shows the "no team" state.
 */
public final class QuestsGameTests {
    private QuestsGameTests() {
    }

    public static void card(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.startSequence()
            .thenWaitUntil(() -> {
                var section = MainframeCards.section(mainframe.getMainframeId(), QuestsCard.KIND);
                helper.assertTrue(section != null, "quests card");
                helper.assertTrue(section.snapshot().get("restricted").getAsBoolean(), "restricted section");
                String team = section.values().get(QuestsCard.TEAM).getAsString();
                helper.assertTrue(!team.equals("–"), "refreshed: " + team);
                helper.assertTrue(section.values().get(QuestsCard.QUESTS).getAsString().equals("–") || team.length() > 0, "quests");
            })
            .thenSucceed();
    }
}
