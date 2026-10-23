package ai.ezfox.cccloud.compat.railcraft;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Railcraft Reborn: a lone solid-fueled firebox gets the Steam Boiler card ("Not formed"). */
public final class RailcraftGameTests {
    private RailcraftGameTests() {
    }

    public static void boiler(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "railcraft:solid_fueled_firebox"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "railcraft_boiler", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "railcraft_boiler", linked);
                helper.assertTrue(text(helper, RailcraftCards.STATUS).equals("Not formed"), "status: " + text(helper, RailcraftCards.STATUS));
            })
            .thenSucceed();
    }
}
