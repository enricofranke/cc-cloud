package ai.ezfox.cccloud.compat.refinedtypes;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Refined Types: a Network Energizer without a network has no reserve; the card says so. */
public final class EnergizerGameTests {
    private EnergizerGameTests() {
    }

    public static void energizer(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "refinedtypes:network_energizer"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "refinedtypes_energizer", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "refinedtypes_energizer", linked);
                helper.assertTrue(text(helper, EnergizerCards.ENERGY).equals("No Energy Disks"), "energy: " + text(helper, EnergizerCards.ENERGY));
            })
            .thenSucceed();
    }
}
