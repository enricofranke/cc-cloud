package ai.ezfox.cccloud.compat.advancedae;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Advanced AE: a single quantum computer block (no multiblock) gets the Quantum Computer card in its "Not formed"
 * state, and "Cancel all jobs" answers {@code unavailable}. Forming a full quantum computer (core, storage, multi-threader
 * and structure blocks around a powered AE2 grid) is not covered headless.
 */
public final class AdvancedAeGameTests {
    private AdvancedAeGameTests() {
    }

    public static void quantumUnit(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "advanced_ae:quantum_unit"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "advancedae_quantum", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "advancedae_quantum", linked);
                helper.assertTrue(text(helper, AdvancedAeCards.STATUS).equals("Not formed"), "status: " + text(helper, AdvancedAeCards.STATUS));
                helper.assertTrue("unavailable".equals(act(helper, AdvancedAeCards.CANCEL, true)), "nothing to cancel");
            })
            .thenSucceed();
    }
}
