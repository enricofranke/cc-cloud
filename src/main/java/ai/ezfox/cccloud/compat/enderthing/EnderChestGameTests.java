package ai.ezfox.cccloud.compat.enderthing;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import ai.ezfox.cccloud.compat.card.ItemStorageCard;
import dev.gigaherz.enderthing.blocks.EnderKeyChestBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Enderthing: a key chest with items shows them as a Storage card; made private, the card goes away. */
public final class EnderChestGameTests {
    private EnderChestGameTests() {
    }

    public static void keyChest(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "enderthing:key_chest"));
        helper.getBlockEntity(DEVICE, EnderKeyChestBlockEntity.class).setKey(4242);
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenExecute(() -> {
                var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(DEVICE), deviceSide());
                try (var tx = Transaction.openRoot()) {
                    handler.insert(ItemResource.of(Items.IRON_INGOT), 100, tx);
                    tx.commit();
                }
            })
            .thenWaitUntil(() -> assertCard(helper, "enderthing_storage", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "enderthing_storage", linked);
                helper.assertTrue(text(helper, ItemStorageCard.ITEMS).startsWith("100"), "items: " + text(helper, ItemStorageCard.ITEMS));
            })
            .thenExecute(() -> helper.getBlockEntity(DEVICE, EnderKeyChestBlockEntity.class).setPrivate(true))
            .thenWaitUntil(() -> helper.assertTrue(!"enderthing_storage".equals(uplink(helper).deviceKind()), "private: no card"))
            .thenSucceed();
    }
}
