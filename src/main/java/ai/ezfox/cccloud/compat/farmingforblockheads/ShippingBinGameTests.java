package ai.ezfox.cccloud.compat.farmingforblockheads;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.blay09.mods.farmingforblockheads.block.entity.ShippingBinBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Farming for Blockheads: a shipping bin alone shows an empty shipment, then the goods put into it. */
public final class ShippingBinGameTests {
    private ShippingBinGameTests() {
    }

    public static void bin(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "farmingforblockheads:shipping_bin"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "farmingforblockheads_shipping_bin", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "farmingforblockheads_shipping_bin", linked);
                helper.assertTrue("Empty".equals(text(helper, ShippingBinCards.GOODS)), "no goods: " + text(helper, ShippingBinCards.GOODS));
                helper.assertTrue(text(helper, ShippingBinCards.VALUE).startsWith("0 / "), "value: " + text(helper, ShippingBinCards.VALUE));
            })
            .thenExecute(() -> {
                var bin = (ShippingBinBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                bin.getContainer().setItem(0, new ItemStack(Items.WHEAT, 3));
            })
            .thenWaitUntil(() -> helper.assertTrue(text(helper, ShippingBinCards.GOODS).endsWith("Wheat"), "goods: " + text(helper, ShippingBinCards.GOODS)))
            .thenSucceed();
    }
}
