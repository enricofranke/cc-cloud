package ai.ezfox.cccloud.compat.storage;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import ai.ezfox.cccloud.compat.card.ItemStorageCard;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity;

/** Sophisticated Backpacks: a placed backpack is a Storage card; items put in through its capability show up. */
public final class BackpackGameTests {
    private BackpackGameTests() {
    }

    public static void backpack(GameTestHelper helper) {
        var block = block(helper, "sophisticatedbackpacks:iron_backpack");
        helper.setBlock(DEVICE, block);
        // BackpackItem is no BlockItem: new ItemStack(block) would be air, and the mod then falls back to an empty wrapper.
        ((BackpackBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE))).setBackpack(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("sophisticatedbackpacks:iron_backpack"))));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "sophisticatedbackpacks_storage", null))
            .thenExecute(() -> {
                bind(helper, linked);
                var handler = ItemStorageCard.handler(helper.getLevel(), helper.absolutePos(DEVICE), deviceSide());
                helper.assertTrue(handler != null, "backpack has an item capability");
                try (var tx = Transaction.openRoot()) {
                    int inserted = handler.insert(ItemResource.of(Items.IRON_INGOT), 100, tx);
                    helper.assertTrue(inserted == 100, "100 ingots fit: " + inserted);
                    tx.commit();
                }
            })
            .thenWaitUntil(() -> {
                assertCard(helper, "sophisticatedbackpacks_storage", linked);
                helper.assertTrue(text(helper, ItemStorageCard.ITEMS).startsWith("100"), "items: " + text(helper, ItemStorageCard.ITEMS));
                helper.assertTrue("1".equals(text(helper, ItemStorageCard.TYPES)), "types: " + text(helper, ItemStorageCard.TYPES));
            })
            .thenSucceed();
    }
}
