package ai.ezfox.cccloud.compat.silentgear;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.silentchaos512.gear.block.alloymaker.AlloyMakerBlockEntity;
import net.silentchaos512.gear.block.charger.ChargerBlockEntity;
import net.silentchaos512.gear.block.press.MetalPressBlockEntity;

/** Silent Gear: machines show their items; the private progress fields and work times are still where the card reads them. */
public final class SilentGearGameTests {
    private SilentGearGameTests() {
    }

    private static Container container(GameTestHelper helper) {
        return (Container) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
    }

    public static void alloy(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "silentgear:alloy_forge"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "silentgear_machine", null))
            .thenExecute(() -> {
                bind(helper, linked);
                helper.assertTrue(SilentGearCards.field(AlloyMakerBlockEntity.class, null, "WORK_TIME") > 0, "work time readable");
                helper.assertTrue(SilentGearCards.field(AlloyMakerBlockEntity.class, container(helper), SilentGearCards.PROGRESS) == 0, "progress readable");
                container(helper).setItem(0, new ItemStack(Items.IRON_INGOT, 3));
            })
            .thenWaitUntil(() -> assertCard(helper, "silentgear_machine", linked))
            .thenWaitUntil(() -> helper.assertTrue("3 × Iron Ingot".equals(text(helper, SilentGearCards.INPUT)), "input: " + text(helper, SilentGearCards.INPUT)))
            .thenExecute(() -> helper.assertTrue("Idle".equals(text(helper, SilentGearCards.STATUS)), "idle"))
            .thenSucceed();
    }

    public static void press(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "silentgear:metal_press"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "silentgear_machine", null))
            .thenExecute(() -> {
                bind(helper, linked);
                helper.assertTrue(SilentGearCards.field(MetalPressBlockEntity.class, null, "WORK_TIME") > 0, "work time readable");
                container(helper).setItem(1, new ItemStack(Items.COPPER_INGOT));
            })
            .thenWaitUntil(() -> assertCard(helper, "silentgear_machine", linked))
            .thenWaitUntil(() -> helper.assertTrue("Copper Ingot".equals(text(helper, SilentGearCards.OUTPUT)), "output: " + text(helper, SilentGearCards.OUTPUT)))
            .thenSucceed();
    }

    public static void charger(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "silentgear:starlight_charger"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "silentgear_charger", null))
            .thenExecute(() -> {
                bind(helper, linked);
                helper.assertTrue(SilentGearCards.field(ChargerBlockEntity.class, container(helper), SilentGearCards.CHARGE) >= 0, "charge readable");
            })
            .thenWaitUntil(() -> assertCard(helper, "silentgear_charger", linked))
            .thenWaitUntil(() -> helper.assertTrue(!"–".equals(text(helper, SilentGearCards.CHARGE)), "charge shown"))
            .thenSucceed();
    }
}
