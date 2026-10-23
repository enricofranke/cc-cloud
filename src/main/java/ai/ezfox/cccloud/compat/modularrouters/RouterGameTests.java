package ai.ezfox.cccloud.compat.modularrouters;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import me.desht.modularrouters.block.tile.ModularRouterBlockEntity;
import me.desht.modularrouters.logic.settings.RedstoneBehaviour;
import net.minecraft.gametest.framework.GameTestHelper;

/** Modular Routers: an empty router gets the card; switching the redstone mode to "Never" turns it off. */
public final class RouterGameTests {
    private RouterGameTests() {
    }

    public static void router(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "modularrouters:modular_router"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "modularrouters_router", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "modularrouters_router", linked);
                helper.assertTrue(text(helper, RouterCards.STATUS).equals("No modules"), "status: " + text(helper, RouterCards.STATUS));
                helper.assertTrue(text(helper, RouterCards.REDSTONE).equals("Always"), "mode: " + text(helper, RouterCards.REDSTONE));
            })
            .thenExecute(() -> {
                helper.assertTrue(act(helper, RouterCards.REDSTONE, "Never") == null, "switched");
                var be = (ModularRouterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                helper.assertTrue(be.getRedstoneBehaviour() == RedstoneBehaviour.NEVER, "router mode Never");
            })
            .thenSucceed();
    }
}
