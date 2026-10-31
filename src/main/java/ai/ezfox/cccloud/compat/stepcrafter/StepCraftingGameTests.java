package ai.ezfox.cccloud.compat.stepcrafter;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Stepcrafter: a monitor without a network shows the card, says so and refuses Cancel all. */
public final class StepCraftingGameTests {
    private StepCraftingGameTests() {
    }

    public static void monitor(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "stepcrafter:step_crafting_monitor"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "stepcrafter_monitor", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "stepcrafter_monitor", linked);
                helper.assertTrue(text(helper, StepCraftingCards.STATUS).equals("Not active"), "status: " + text(helper, StepCraftingCards.STATUS));
                helper.assertTrue("unavailable".equals(act(helper, StepCraftingCards.CANCEL, true)), "cancel refused");
            })
            .thenSucceed();
    }
}
