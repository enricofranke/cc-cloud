package ai.ezfox.cccloud.compat.productivebees;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import cy.jdkdigital.productivebees.common.block.entity.AdvancedBeehiveBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Productive Bees: an advanced oak hive card; filling every output slot raises "output full". */
public final class ProductiveBeesGameTests {
    private ProductiveBeesGameTests() {
    }

    public static void hive(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "productivebees:advanced_oak_beehive"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "productivebees_hive", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "productivebees_hive", linked);
                helper.assertTrue(text(helper, ProductiveBeesCards.BEES).startsWith("0 / "), "no bees: " + text(helper, ProductiveBeesCards.BEES));
                helper.assertTrue(number(helper, ProductiveBeesCards.OUTPUT) == 0, "empty output");
                helper.assertTrue(!bool(helper, ProductiveBeesCards.OUTPUT_FULL), "not full");
            })
            .thenExecute(() -> {
                var hive = (AdvancedBeehiveBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                var items = hive.inventoryHandler;
                for (int slot = ProductiveBeesCards.FIRST_OUTPUT; slot <= ProductiveBeesCards.LAST_OUTPUT; slot++) {
                    items.setStackInSlot(slot, new ItemStack(Items.HONEYCOMB, 64));
                }
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(bool(helper, ProductiveBeesCards.OUTPUT_FULL), "output full");
                helper.assertTrue(number(helper, ProductiveBeesCards.OUTPUT) == 100, "100 % used: " + value(helper, ProductiveBeesCards.OUTPUT));
            })
            .thenSucceed();
    }
}
