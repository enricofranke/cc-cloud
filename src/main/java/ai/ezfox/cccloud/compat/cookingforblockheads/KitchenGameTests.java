package ai.ezfox.cccloud.compat.cookingforblockheads;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.blay09.mods.cookingforblockheads.block.entity.ToasterBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Cooking for Blockheads: an oven and a toaster alone, a fridge as a Storage card with search. */
public final class KitchenGameTests {
    private KitchenGameTests() {
    }

    public static void oven(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "cookingforblockheads:white_oven"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "cookingforblockheads_oven", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "cookingforblockheads_oven", linked);
                helper.assertTrue("Idle".equals(text(helper, KitchenCards.STATUS)), "status: " + text(helper, KitchenCards.STATUS));
                helper.assertTrue("Empty".equals(text(helper, KitchenCards.INPUT)), "input: " + text(helper, KitchenCards.INPUT));
                helper.assertTrue(number(helper, KitchenCards.COOKING) == 0, "nothing cooking");
            })
            .thenSucceed();
    }

    public static void toaster(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "cookingforblockheads:toaster"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "cookingforblockheads_toaster", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, "cookingforblockheads_toaster", linked))
            .thenExecute(() -> {
                helper.assertTrue("unavailable".equals(act(helper, KitchenCards.POWER, true)), "an empty toaster stays off");
                var toaster = (ToasterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                toaster.getContainer().setItem(0, new ItemStack(Items.BREAD));
                helper.assertTrue(act(helper, KitchenCards.POWER, true) == null, "switched on");
                helper.assertTrue(toaster.isActive(), "toaster active");
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(bool(helper, KitchenCards.POWER), "toggle follows");
                helper.assertTrue(text(helper, KitchenCards.INPUT).endsWith("Bread"), "inside: " + text(helper, KitchenCards.INPUT));
            })
            .thenExecute(() -> {
                helper.assertTrue(act(helper, KitchenCards.POWER, false) == null, "switched off");
                var toaster = (ToasterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                helper.assertTrue(!toaster.isActive(), "toaster off");
            })
            .thenSucceed();
    }

    public static void fridge(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "cookingforblockheads:white_fridge"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenExecute(() -> {
                var be = helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                ((net.blay09.mods.cookingforblockheads.block.entity.FridgeBlockEntity) be).getContainer().setItem(0, new ItemStack(Items.APPLE, 5));
            })
            .thenWaitUntil(() -> assertCard(helper, "cookingforblockheads_storage", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "cookingforblockheads_storage", linked);
                helper.assertTrue("Fridge".equals(uplink(helper).getTitle()), "title: " + uplink(helper).getTitle());
            })
            .thenSucceed();
    }
}
