package ai.ezfox.cccloud.compat.rangedpumps;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Ranged Pumps: a pump placed alone shows its state (no energy), an empty tank and its scan range. */
public final class PumpGameTests {
    private PumpGameTests() {
    }

    public static void pump(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "rangedpumps:pump"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "rangedpumps_pump", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "rangedpumps_pump", linked);
                String status = text(helper, PumpCards.STATUS);
                helper.assertTrue(status.equals("No energy") || status.startsWith("Done") || status.equals("Pumping"), "status: " + status);
                helper.assertTrue("Empty".equals(text(helper, PumpCards.FLUID)), "fluid: " + text(helper, PumpCards.FLUID));
                helper.assertTrue(number(helper, PumpCards.TANK) == 0, "tank empty");
                helper.assertTrue(text(helper, PumpCards.RANGE).equals("Not started") || text(helper, PumpCards.RANGE).contains(" of ") || text(helper, PumpCards.RANGE).endsWith("(all)"), "range read: " + text(helper, PumpCards.RANGE));
                helper.assertTrue("not_interactive".equals(act(helper, PumpCards.STATUS, "x")), "no controls");
            })
            .thenSucceed();
    }
}
