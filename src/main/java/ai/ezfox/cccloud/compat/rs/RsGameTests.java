package ai.ezfox.cccloud.compat.rs;

import ai.ezfox.cccloud.gametest.BridgeTestSupport;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

/** Builds a minimal powered RS2 network: creative controller + 1k storage block + RS Cloud Bridge. */
public final class RsGameTests {
    private RsGameTests() {
    }

    public static void storage(GameTestHelper helper) {
        var controller = new BlockPos(1, 1, 2);
        var storageBlock = new BlockPos(2, 1, 2);
        var bridge = new BlockPos(3, 1, 2);
        helper.setBlock(controller, BridgeTestSupport.block(helper, "refinedstorage:creative_controller"));
        helper.setBlock(storageBlock, BridgeTestSupport.block(helper, "refinedstorage:1k_storage_block"));
        helper.setBlock(bridge, RsCompat.BLOCK.get());
        BridgeTestSupport.placeUplinkOnTop(helper, bridge);

        boolean[] filled = {false};
        boolean[] once = {false};
        var linking = new BridgeTestSupport.Linking[1];
        helper.startSequence().thenWaitUntil(() -> {
            var be = (RsBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(bridge));
            helper.assertTrue(be != null, "bridge block entity missing");
            helper.assertTrue(be.bridgeCore().isConnected(), "waiting for the RS network to power up");
            if (!filled[0]) {
                var storage = be.node().getNetwork().getComponent(StorageNetworkComponent.class);
                long inserted = storage.insert(new ItemResource(Items.IRON_INGOT), BridgeTestSupport.AMOUNT, Action.EXECUTE, Actor.EMPTY);
                helper.assertTrue(inserted == BridgeTestSupport.AMOUNT, "inserted " + inserted + " into the RS network");
                filled[0] = true;
            }
            BridgeTestSupport.assertBridge(helper, bridge, be, be.peripheral(), once);
            var storage = be.bridgeCore().storage();
            helper.assertTrue(storage.has("itemsTotal") && storage.get("itemsTotal").getAsLong() >= 1000, "RS itemsTotal from the 1k storage block: " + storage);
        })
            .thenExecute(() -> linking[0] = BridgeTestSupport.placeMainframe(helper))
            .thenIdle(3)
            .thenExecute(() -> BridgeTestSupport.bind(helper, bridge, be(helper, bridge), linking[0]))
            .thenWaitUntil(() -> BridgeTestSupport.assertLinked(helper, bridge, be(helper, bridge), linking[0]))
            .thenExecute(() -> BridgeTestSupport.unlinkAndAssert(helper, bridge, be(helper, bridge), linking[0]))
            .thenWaitUntil(() -> BridgeTestSupport.assertUnlinked(helper, bridge, be(helper, bridge), linking[0]))
            .thenSucceed();
    }

    private static RsBridgeBlockEntity be(GameTestHelper helper, BlockPos pos) {
        return (RsBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }
}