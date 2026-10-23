package ai.ezfox.cccloud.compat.productivemetalworks;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Productive Metalworks: a lone foundry controller gets the Foundry card ("Not formed", no fuel, empty tank). */
public final class MetalworksGameTests {
    private MetalworksGameTests() {
    }

    public static void foundry(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "productivemetalworks:black_foundry_controller"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "productivemetalworks_foundry", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "productivemetalworks_foundry", linked);
                helper.assertTrue(text(helper, MetalworksCards.STATUS).equals("Not formed"), "status: " + text(helper, MetalworksCards.STATUS));
                helper.assertTrue(text(helper, MetalworksCards.MOLTEN).equals("Empty"), "molten: " + text(helper, MetalworksCards.MOLTEN));
            })
            .thenSucceed();
    }
}
