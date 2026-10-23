package ai.ezfox.cccloud.compat.easyvillagers;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Easy Villagers: an empty iron farm gets the Iron Farm card ("No villager", empty output). */
public final class EasyVillagersGameTests {
    private EasyVillagersGameTests() {
    }

    public static void ironFarm(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "easy_villagers:iron_farm"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "easyvillagers_iron_farm", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "easyvillagers_iron_farm", linked);
                helper.assertTrue(text(helper, EasyVillagersCards.STATUS).equals("No villager"), "status: " + text(helper, EasyVillagersCards.STATUS));
                helper.assertTrue(text(helper, EasyVillagersCards.VILLAGERS).equals("0"), "villagers");
                helper.assertTrue(number(helper, EasyVillagersCards.OUTPUT) == 0 && !bool(helper, EasyVillagersCards.OUTPUT_FULL), "empty output");
            })
            .thenSucceed();
    }
}
