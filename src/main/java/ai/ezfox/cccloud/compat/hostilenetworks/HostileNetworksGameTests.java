package ai.ezfox.cccloud.compat.hostilenetworks;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Hostile Neural Networks: an empty, powered simulation chamber shows "No data model" and its energy level. */
public final class HostileNetworksGameTests {
    private HostileNetworksGameTests() {
    }

    public static void chamber(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "hostilenetworks:sim_chamber"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenExecute(() -> helper.assertTrue(insertEnergy(helper, DEVICE, deviceSide(), 500_000) > 0, "chamber accepts FE"))
            .thenWaitUntil(() -> assertCard(helper, "hostilenetworks_chamber", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "hostilenetworks_chamber", linked);
                helper.assertTrue(text(helper, HostileNetworksCards.MODEL).equals("–"), "no model: " + text(helper, HostileNetworksCards.MODEL));
                helper.assertTrue(!bool(helper, HostileNetworksCards.RUNNING), "not simulating");
                helper.assertTrue(number(helper, HostileNetworksCards.ENERGY) > 0, "energy %: " + value(helper, HostileNetworksCards.ENERGY));
                helper.assertTrue("not_interactive".equals(act(helper, HostileNetworksCards.ENERGY, 1)), "read-only card");
            })
            .thenSucceed();
    }
}
