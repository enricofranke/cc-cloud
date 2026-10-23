package ai.ezfox.cccloud.compat.powah;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import ai.ezfox.cccloud.compat.card.EnergyCard;
import net.minecraft.gametest.framework.GameTestHelper;
import owmii.powah.block.energycell.EnergyCellBlockEntity;
import owmii.powah.block.reactor.ReactorBlock;
import owmii.powah.block.reactor.ReactorBlockEntity;
import owmii.powah.lib.logistics.Redstone;

/** Powah: energy cell card (values + redstone mode from the dashboard) and the reactor card on an unbuilt core. */
public final class PowahGameTests {
    private PowahGameTests() {
    }

    public static void energyCell(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "powah:energy_cell_basic"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenIdle(2) // Powah applies the cell's capacity on its first tick
            .thenExecute(() -> helper.assertTrue(insertEnergy(helper, DEVICE, deviceSide(), 100_000) > 0, "the cell accepts FE"))
            .thenWaitUntil(() -> assertCard(helper, "powah_cell", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "powah_cell", linked);
                helper.assertTrue(number(helper, EnergyCard.STORED) > 0, "stored: " + value(helper, EnergyCard.STORED));
                helper.assertTrue(text(helper, "tier").equals("Basic"), "tier: " + text(helper, "tier"));
                helper.assertTrue(text(helper, EnergyCard.REDSTONE).equals("Ignore redstone"), "redstone mode: " + text(helper, EnergyCard.REDSTONE));
            })
            .thenExecute(() -> {
                helper.assertTrue("invalid_value".equals(act(helper, EnergyCard.REDSTONE, "Sometimes")), "unknown option rejected");
                helper.assertTrue(act(helper, EnergyCard.REDSTONE, "Active with signal") == null, "set redstone mode");
                var cell = (EnergyCellBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                helper.assertTrue(cell.getRedstoneMode() == Redstone.ON, "Powah got the mode: " + cell.getRedstoneMode());
            })
            .thenWaitUntil(() -> helper.assertTrue(text(helper, EnergyCard.REDSTONE).equals("Active with signal"), "card shows the new mode"))
            .thenSucceed();
    }

    public static void reactor(GameTestHelper helper) {
        var reactorBlock = block(helper, "powah:reactor_basic");
        helper.setBlock(DEVICE, reactorBlock.defaultBlockState().setValue(ReactorBlock.CORE, true));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "powah_reactor", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "powah_reactor", linked);
                helper.assertTrue(uplink(helper).getTitle().equals("Powah Reactor (Basic)"), "title: " + uplink(helper).getTitle());
                bool(helper, "built"); // Powah decides when a core counts as built; only the value must be there
                helper.assertTrue(!bool(helper, "running"), "not running");
                helper.assertTrue(number(helper, "fuel") == 0, "no fuel");
            })
            .thenExecute(() -> {
                var core = (ReactorBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                boolean before = core.isGenModeOn();
                helper.assertTrue(act(helper, "gen_mode", !before) == null, "toggle generation mode");
                helper.assertTrue(core.isGenModeOn() == !before, "reactor generation mode switched");
            })
            .thenSucceed();
    }
}
