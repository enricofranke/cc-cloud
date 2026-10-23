package ai.ezfox.cccloud.compat.simplebackups;

import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/** Simple Backups: the Backups card appears; "Backup now" runs a backup and the last-backup time shows it. */
public final class SimpleBackupsGameTests {
    private SimpleBackupsGameTests() {
    }

    public static void card(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.startSequence()
            .thenWaitUntil(() -> {
                var section = MainframeCards.section(mainframe.getMainframeId(), BackupsCard.KIND);
                helper.assertTrue(section != null && section.values().get(BackupsCard.LAST).isJsonPrimitive(), "backups card");
                helper.assertTrue(!"–".equals(section.values().get(BackupsCard.LAST).getAsString()), "refreshed");
            })
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline() && mainframe.status() == ai.ezfox.cccloud.block.CloudStatus.ONLINE, "mainframe online"))
            .thenIdle(25)
            .thenExecute(() -> {
                var section = MainframeCards.section(mainframe.getMainframeId(), BackupsCard.KIND);
                var bad = section.handleAction(BackupsCard.BACKUP_NOW, new JsonPrimitive(false), null, "gametest", false).getNow(null);
                helper.assertTrue(bad != null && "invalid_value".equals(bad.error()), "buttons only accept true");
                var result = section.handleAction(BackupsCard.BACKUP_NOW, new JsonPrimitive(true), null, "gametest", false).getNow(null);
                helper.assertTrue(result != null && result.error() == null, "backup started: " + (result == null ? null : result.error()));
            })
            .thenWaitUntil(() -> {
                var last = MainframeCards.section(mainframe.getMainframeId(), BackupsCard.KIND).values().get(BackupsCard.LAST).getAsString();
                helper.assertTrue(!last.equals("No backup yet"), "last backup shown: " + last);
            })
            .thenSucceed();
    }
}
