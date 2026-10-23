package ai.ezfox.cccloud.compat.arsmagica;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Ars Magica Legacy: an obelisk gets the etherium card with one gauge per etherium type. */
public final class ArsMagicaGameTests {
    private ArsMagicaGameTests() {
    }

    public static void obelisk(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "arsmagicalegacy:obelisk"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "arsmagica_etherium", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "arsmagica_etherium", linked);
                helper.assertTrue(uplink(helper).widgetCount() >= 2, "a gauge and a label per etherium type");
            })
            .thenSucceed();
    }
}
