package ai.ezfox.cccloud.compat.card;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import ai.ezfox.cccloud.uplink.WidgetType;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * The generic FE Energy card on a block without a mod-specific card: Hostile Neural Networks' loot
 * fabricator (test instance conditioned on {@code hostilenetworks}, whose card only covers the simulation chamber). Covers detection, the values, the history chart, publishing
 * to the dashboard and leaving the card when the machine is replaced.
 */
public final class CardGameTests {
    private CardGameTests() {
    }

    public static void genericEnergy(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "hostilenetworks:loot_fabricator"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        int[] inserted = {0};
        helper.onEachTick(() -> {
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE)) != null)) return;
            if (inserted[0] < 50_000) inserted[0] += insertEnergy(helper, DEVICE, deviceSide(), 500);
        });
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, EnergyCard.KIND, null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, EnergyCard.KIND, linked);
                helper.assertTrue(uplink(helper).getTitle().equals("Loot Fabricator"), "title from the block id: " + uplink(helper).getTitle());
                var handler = EnergyCard.handler(helper.getLevel(), helper.absolutePos(DEVICE), deviceSide());
                helper.assertTrue(handler != null && handler.getAmountAsLong() > 0, "the fabricator took FE");
                double expected = Fmt.percent(handler.getAmountAsLong(), handler.getCapacityAsLong());
                helper.assertTrue(Math.abs(number(helper, EnergyCard.STORED) - expected) <= 1, "stored %: " + value(helper, EnergyCard.STORED) + " vs " + expected);
                helper.assertTrue(!text(helper, EnergyCard.ENERGY).startsWith("0 /"), "energy label shows the stored FE: " + text(helper, EnergyCard.ENERGY));
                helper.assertTrue(text(helper, EnergyCard.ENERGY).endsWith(" FE"), "energy label: " + text(helper, EnergyCard.ENERGY));
                helper.assertTrue(text(helper, EnergyCard.FLOW).endsWith("FE/t"), "net flow: " + text(helper, EnergyCard.FLOW));
                var chart = uplink(helper).getWidget(EnergyCard.HISTORY);
                helper.assertTrue(chart != null && chart.type() == WidgetType.CHART && chart.historySize() > 0, "history chart sampled");
                helper.assertTrue(chart.chartPoints() == CardWidgets.HISTORY_POINTS, "60 min of points");
                helper.assertTrue("not_interactive".equals(act(helper, EnergyCard.STORED, 1)), "gauges are read-only");
                helper.assertTrue(uplink(helper).getWidget(EnergyCard.REDSTONE) == null, "no redstone mode exposed");
            })
            .thenExecute(() -> helper.setBlock(DEVICE, Blocks.STONE))
            .thenWaitUntil(() -> {
                helper.assertTrue(uplink(helper).deviceKind().isEmpty(), "card gone with the block");
                helper.assertTrue(uplink(helper).widgetCount() == 0, "no widgets left");
            })
            .thenSucceed();
    }
}
