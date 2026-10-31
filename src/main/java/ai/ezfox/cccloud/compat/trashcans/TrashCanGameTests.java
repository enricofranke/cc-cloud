package ai.ezfox.cccloud.compat.trashcans;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Trash Cans: items pushed into an item can show up as deleted; the filter mode switches from the dashboard. */
public final class TrashCanGameTests {
    private TrashCanGameTests() {
    }

    public static void item(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "trashcans:item_trash_can"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "trashcans_can", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "trashcans_can", linked);
                helper.assertTrue(text(helper, TrashCanCards.HANDLES).equals("Items"), "takes: " + text(helper, TrashCanCards.HANDLES));
                helper.assertTrue(text(helper, TrashCanCards.DELETED + 0).equals("–"), "nothing deleted: " + text(helper, TrashCanCards.DELETED + 0));
            })
            .thenExecute(() -> {
                var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(DEVICE), deviceSide());
                try (var tx = Transaction.openRoot()) {
                    helper.assertTrue(handler.insert(ItemResource.of(Items.DIRT), 32, tx) == 32, "the can takes it");
                    tx.commit();
                }
                helper.assertTrue(act(helper, TrashCanCards.ITEM_ALLOW, !bool(helper, TrashCanCards.ITEM_ALLOW)) == null, "toggle");
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(text(helper, TrashCanCards.DELETED + 0).equals("32× Dirt"), "deleted: " + text(helper, TrashCanCards.DELETED + 0));
                helper.assertTrue(bool(helper, TrashCanCards.ITEM_ALLOW), "allow list on");
            })
            .thenSucceed();
    }
}
