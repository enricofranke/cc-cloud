package ai.ezfox.cccloud.compat.productivefarming;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Productive Farming: a lone farm controller gets the Farm card ("Not formed"). */
public final class FarmGameTests {
    private FarmGameTests() {
    }

    public static void farm(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "productivefarming:farm_controller"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "productivefarming_farm", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "productivefarming_farm", linked);
                helper.assertTrue(text(helper, FarmCards.STATUS).equals("Not formed"), "status: " + text(helper, FarmCards.STATUS));
            })
            .thenSucceed();
    }
}
