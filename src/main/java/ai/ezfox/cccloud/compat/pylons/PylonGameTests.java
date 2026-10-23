package ai.ezfox.cccloud.compat.pylons;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;
import net.permutated.pylons.machines.base.AbstractPylonTile;

/** Pylons: an expulsion pylon gets the card; the toggle switches the pylon off and on again. */
public final class PylonGameTests {
    private PylonGameTests() {
    }

    private static AbstractPylonTile be(GameTestHelper helper) {
        return (AbstractPylonTile) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
    }

    public static void pylon(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "pylons:expulsion_pylon"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "pylons_pylon", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, "pylons_pylon", linked))
            .thenExecute(() -> {
                boolean before = be(helper).shouldWork();
                helper.assertTrue(act(helper, PylonCards.WORKING, !before) == null, "toggle");
                helper.assertTrue(be(helper).shouldWork() == !before, "pylon switched");
                helper.assertTrue(act(helper, PylonCards.WORKING, before) == null, "toggle back");
                helper.assertTrue(be(helper).shouldWork() == before, "pylon switched back");
            })
            .thenSucceed();
    }
}
