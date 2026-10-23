package ai.ezfox.cccloud.compat.energymeter;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import com.almostreliable.energymeter.block.component.IoConfig;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Energy Meter: the card switches the meter to Consume mode from the dashboard, then 200 FE/t are pushed into the
 * meter's input side and the card shows the measured flow, the status and the transferred total.
 */
public final class EnergyMeterGameTests {
    private static final int RATE = 200;

    private EnergyMeterGameTests() {
    }

    public static void meter(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "energymeter:meter"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        boolean[] feeding = {false};
        helper.onEachTick(() -> {
            if (feeding[0]) insertEnergy(helper, DEVICE, Direction.NORTH, RATE);
        });
        helper.startSequence()
            .thenExecute(() -> be(helper).getIoConfig().setSetting(Direction.NORTH, IoConfig.IoSettingWithPriority.IN))
            .thenWaitUntil(() -> assertCard(helper, "energymeter", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "energymeter", linked);
                helper.assertTrue(text(helper, EnergyMeterCards.MODE).equals("Split"), "default mode: " + text(helper, EnergyMeterCards.MODE));
            })
            .thenExecute(() -> {
                helper.assertTrue("invalid_value".equals(act(helper, EnergyMeterCards.MODE, "Teleport")), "unknown mode rejected");
                helper.assertTrue(act(helper, EnergyMeterCards.MODE, "Consume") == null, "switch to Consume");
                helper.assertTrue(be(helper).getTransferMode() == MeterBlockEntity.TransferMode.CONSUME, "the meter got the mode");
                feeding[0] = true;
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(Math.abs(be(helper).getEnergyRate() - RATE) < 1, "meter measures " + be(helper).getEnergyRate());
                helper.assertTrue(text(helper, EnergyMeterCards.FLOW).equals(RATE + " FE/t"), "card flow: " + text(helper, EnergyMeterCards.FLOW));
                helper.assertTrue(text(helper, EnergyMeterCards.STATUS).equals("Consuming"), "status: " + text(helper, EnergyMeterCards.STATUS));
                helper.assertTrue(!text(helper, EnergyMeterCards.TOTAL).equals("0 FE"), "total: " + text(helper, EnergyMeterCards.TOTAL));
                helper.assertTrue(uplink(helper).getWidget(EnergyMeterCards.HISTORY).historySize() > 0, "flow chart sampled");
            })
            .thenSucceed();
    }

    private static MeterBlockEntity be(GameTestHelper helper) {
        return (MeterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
    }
}
