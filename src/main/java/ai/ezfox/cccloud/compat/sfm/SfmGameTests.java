package ai.ezfox.cccloud.compat.sfm;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Super Factory Manager: a manager without a disk gets the Factory Manager card ("No disk"). */
public final class SfmGameTests {
    private SfmGameTests() {
    }

    public static void manager(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "sfm:manager"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "sfm_manager", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "sfm_manager", linked);
                helper.assertTrue(text(helper, SfmCards.STATE).equals("No disk"), "state: " + text(helper, SfmCards.STATE));
                helper.assertTrue(text(helper, SfmCards.ERRORS).equals("0"), "errors");
            })
            .thenSucceed();
    }
}
