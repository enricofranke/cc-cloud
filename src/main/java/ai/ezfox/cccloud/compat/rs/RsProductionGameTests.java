package ai.ezfox.cccloud.compat.rs;

import ai.ezfox.cccloud.gametest.BridgeTestSupport;
import ai.ezfox.cccloud.gametest.ProductionTestSupport;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** v0.10 production statistics on a real RS2 network (creative controller + 1k storage block + linked bridge). */
public final class RsProductionGameTests {
    private RsProductionGameTests() {
    }

    public static void production(GameTestHelper helper) {
        var controller = new BlockPos(1, 1, 2);
        var storageBlock = new BlockPos(2, 1, 2);
        var bridge = new BlockPos(3, 1, 2);
        helper.setBlock(controller, BridgeTestSupport.block(helper, "refinedstorage:creative_controller"));
        helper.setBlock(storageBlock, BridgeTestSupport.block(helper, "refinedstorage:1k_storage_block"));
        helper.setBlock(bridge, RsCompat.BLOCK.get());
        BridgeTestSupport.placeUplinkOnTop(helper, bridge);
        var network = new ProductionTestSupport.Network() {
            @Override
            public long insert(Item item, long count) {
                return storage(helper, bridge).insert(new ItemResource(item), count, Action.EXECUTE, Actor.EMPTY);
            }

            @Override
            public long extract(Item item, long count) {
                return storage(helper, bridge).extract(new ItemResource(item), count, Action.EXECUTE, Actor.EMPTY);
            }
        };
        var linking = new BridgeTestSupport.Linking[1];
        var sequence = helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(be(helper, bridge) != null && be(helper, bridge).bridgeCore().isConnected(), "waiting for the RS network"))
            .thenExecute(() -> helper.assertTrue(network.insert(Items.IRON_INGOT, BridgeTestSupport.AMOUNT) == BridgeTestSupport.AMOUNT, "iron in"))
            .thenExecute(() -> linking[0] = BridgeTestSupport.placeMainframe(helper))
            .thenIdle(3)
            .thenExecute(() -> BridgeTestSupport.bind(helper, bridge, be(helper, bridge), linking[0]));
        ProductionTestSupport.steps(helper, sequence, () -> be(helper, bridge), network, () -> linking[0]).thenSucceed();
    }

    private static StorageNetworkComponent storage(GameTestHelper helper, BlockPos bridge) {
        return be(helper, bridge).node().getNetwork().getComponent(StorageNetworkComponent.class);
    }

    private static RsBridgeBlockEntity be(GameTestHelper helper, BlockPos pos) {
        return (RsBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }
}
