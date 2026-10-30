package ai.ezfox.cccloud.compat.ae2;

import ai.ezfox.cccloud.gametest.BridgeTestSupport;
import ai.ezfox.cccloud.gametest.ProductionTestSupport;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** v0.10 production statistics on a real AE2 network (creative energy cell + drive with a 1k cell + linked bridge). */
public final class Ae2ProductionGameTests {
    private Ae2ProductionGameTests() {
    }

    public static void production(GameTestHelper helper) {
        var energy = new BlockPos(1, 1, 2);
        var drive = new BlockPos(2, 1, 2);
        var bridge = new BlockPos(3, 1, 2);
        helper.setBlock(energy, BridgeTestSupport.block(helper, "ae2:creative_energy_cell"));
        helper.setBlock(drive, BridgeTestSupport.block(helper, "ae2:drive"));
        helper.setBlock(bridge, Ae2Compat.BLOCK.get());
        BridgeTestSupport.placeUplinkOnTop(helper, bridge);
        var cell = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:item_storage_cell_1k"));
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(drive), null);
        helper.assertTrue(handler != null && cell != null && cell != Items.AIR, "ME drive and 1k cell");
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(handler.insert(ItemResource.of(cell), 1, tx) == 1, "could not insert the storage cell");
            tx.commit();
        }
        var network = new ProductionTestSupport.Network() {
            @Override
            public long insert(Item item, long count) {
                var be = be(helper, bridge);
                return be.managedNode().getGrid().getStorageService().getInventory().insert(AEItemKey.of(item), count, Actionable.MODULATE, IActionSource.ofMachine(be));
            }

            @Override
            public long extract(Item item, long count) {
                var be = be(helper, bridge);
                return be.managedNode().getGrid().getStorageService().getInventory().extract(AEItemKey.of(item), count, Actionable.MODULATE, IActionSource.ofMachine(be));
            }
        };
        var linking = new BridgeTestSupport.Linking[1];
        var sequence = helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(be(helper, bridge) != null && be(helper, bridge).bridgeCore().isConnected(), "waiting for the grid to boot"))
            .thenExecute(() -> helper.assertTrue(network.insert(Items.IRON_INGOT, BridgeTestSupport.AMOUNT) == BridgeTestSupport.AMOUNT, "iron in"))
            .thenExecute(() -> linking[0] = BridgeTestSupport.placeMainframe(helper))
            .thenIdle(3)
            .thenExecute(() -> BridgeTestSupport.bind(helper, bridge, be(helper, bridge), linking[0]));
        ProductionTestSupport.steps(helper, sequence, () -> be(helper, bridge), network, () -> linking[0]).thenSucceed();
    }

    private static MeBridgeBlockEntity be(GameTestHelper helper, BlockPos pos) {
        return (MeBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }
}
