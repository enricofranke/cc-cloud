package ai.ezfox.cccloud.compat.productivetrees;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Productive Trees: a sawmill placed alone gets the machine card (idle, empty). */
public final class TreeMachineGameTests {
    private TreeMachineGameTests() {
    }

    public static void sawmill(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "productivetrees:sawmill"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "productivetrees_machine", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "productivetrees_machine", linked);
                helper.assertTrue("Sawmill".equals(uplink(helper).getTitle()), "title: " + uplink(helper).getTitle());
                helper.assertTrue("Idle".equals(text(helper, TreeMachineCards.STATUS)), "status: " + text(helper, TreeMachineCards.STATUS));
                helper.assertTrue("Empty".equals(text(helper, TreeMachineCards.INPUT)), "input: " + text(helper, TreeMachineCards.INPUT));
                helper.assertTrue(number(helper, TreeMachineCards.PROGRESS) == 0, "no progress");
            })
            .thenSucceed();
    }
}
