package ai.ezfox.cccloud.compat.ae2;

import ai.ezfox.cccloud.gametest.BridgeTestSupport;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Builds a minimal powered AE2 network: creative energy cell + ME drive with a 1k cell + ME Cloud Bridge. */
public final class Ae2GameTests {
    private Ae2GameTests() {
    }

    public static void storage(GameTestHelper helper) {
        var energy = new BlockPos(1, 1, 2);
        var drive = new BlockPos(2, 1, 2);
        var bridge = new BlockPos(3, 1, 2);
        helper.setBlock(energy, BridgeTestSupport.block(helper, "ae2:creative_energy_cell"));
        helper.setBlock(drive, BridgeTestSupport.block(helper, "ae2:drive"));
        helper.setBlock(bridge, Ae2Compat.BLOCK.get());
        BridgeTestSupport.placeUplinkOnTop(helper, bridge);

        var cell = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:item_storage_cell_1k"));
        helper.assertTrue(cell != null && cell != Items.AIR, "AE2 1k item cell missing");
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(drive), null);
        helper.assertTrue(handler != null, "ME drive exposes no item handler");
        try (var tx = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(cell), 1, tx);
            helper.assertTrue(inserted == 1, "could not insert the storage cell into the drive");
            tx.commit();
        }

        boolean[] filled = {false};
        boolean[] once = {false};
        var linking = new BridgeTestSupport.Linking[1];
        helper.startSequence().thenWaitUntil(() -> {
            var be = (MeBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(bridge));
            helper.assertTrue(be != null, "bridge block entity missing");
            helper.assertTrue(be.bridgeCore().isConnected(), "waiting for the grid to boot");
            if (!filled[0]) {
                var grid = be.managedNode().getGrid();
                long inserted = grid.getStorageService().getInventory().insert(AEItemKey.of(Items.IRON_INGOT), BridgeTestSupport.AMOUNT,
                    Actionable.MODULATE, IActionSource.ofMachine(be));
                helper.assertTrue(inserted == BridgeTestSupport.AMOUNT, "inserted " + inserted + " into the ME network");
                filled[0] = true;
            }
            BridgeTestSupport.assertBridge(helper, bridge, be, be.peripheral(), once);
            var storage = be.bridgeCore().storage();
            helper.assertTrue(storage.has("itemsTotal") && storage.get("itemsTotal").getAsLong() > 0, "AE2 itemsTotal from the 1k cell: " + storage);
            helper.assertTrue(be.bridgeCore().adapter().energy() > 0, "creative cell energy");
        })
            .thenExecute(() -> linking[0] = BridgeTestSupport.placeMainframe(helper))
            .thenIdle(3)
            .thenExecute(() -> BridgeTestSupport.bind(helper, bridge, be(helper, bridge), linking[0]))
            .thenWaitUntil(() -> BridgeTestSupport.assertLinked(helper, bridge, be(helper, bridge), linking[0]))
            .thenExecute(() -> BridgeTestSupport.unlinkAndAssert(helper, bridge, be(helper, bridge), linking[0]))
            .thenWaitUntil(() -> BridgeTestSupport.assertUnlinked(helper, bridge, be(helper, bridge), linking[0]))
            .thenSucceed();
    }

    private static MeBridgeBlockEntity be(GameTestHelper helper, BlockPos pos) {
        return (MeBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }
}