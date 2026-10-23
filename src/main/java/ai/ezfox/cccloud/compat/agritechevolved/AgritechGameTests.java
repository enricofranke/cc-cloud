package ai.ezfox.cccloud.compat.agritechevolved;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Agritech: Evolved: an empty basic planter gets the Planter card ("No plant"). */
public final class AgritechGameTests {
    private AgritechGameTests() {
    }

    public static void planter(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "agritechevolved:basic_planter"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "agritechevolved_planter", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "agritechevolved_planter", linked);
                helper.assertTrue(text(helper, AgritechCards.STATUS).equals("No plant"), "status: " + text(helper, AgritechCards.STATUS));
                helper.assertTrue(!bool(helper, AgritechCards.OUTPUT_FULL), "output not full");
            })
            .thenSucceed();
    }
}
