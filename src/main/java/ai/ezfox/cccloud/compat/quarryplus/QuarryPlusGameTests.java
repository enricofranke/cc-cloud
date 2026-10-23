package ai.ezfox.cccloud.compat.quarryplus;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import com.yogpc.qp.machine.PowerEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * QuarryPlus: a quarry set with setBlock has no area; placed like a player places it (default area) and powered it
 * starts working, and the card follows: status, energy, area size.
 */
public final class QuarryPlusGameTests {
    private QuarryPlusGameTests() {
    }

    public static void quarry(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "quarryplus:quarry"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "quarryplus_quarry", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "quarryplus_quarry", linked);
                helper.assertTrue(text(helper, QuarryPlusCards.STATUS).equals("No area set"), "status: " + text(helper, QuarryPlusCards.STATUS));
                helper.assertTrue(text(helper, QuarryPlusCards.AREA).equals("Not set"), "area: " + text(helper, QuarryPlusCards.AREA));
            })
            .thenExecute(() -> {
                // Placing it like a player does: QuarryPlus picks its default area in front of the quarry and starts.
                var p = helper.absolutePos(DEVICE);
                var state = helper.getLevel().getBlockState(p);
                state.getBlock().setPlacedBy(helper.getLevel(), p, state, linked.owner(), new ItemStack(state.getBlock()));
                helper.onEachTick(() -> {
                    if (helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE)) instanceof PowerEntity e) e.addEnergy(e.getMaxEnergy(), false);
                });
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(text(helper, QuarryPlusCards.AREA).contains(" × "), "area: " + text(helper, QuarryPlusCards.AREA));
                helper.assertTrue(number(helper, QuarryPlusCards.ENERGY) > 0, "energy: " + value(helper, QuarryPlusCards.ENERGY));
                String status = text(helper, QuarryPlusCards.STATUS);
                helper.assertTrue(!status.equals("No area set") && !status.equals("Finished"), "working: " + status);
            })
            .thenSucceed();
    }
}
