package ai.ezfox.cccloud.compat.rs;

import ai.ezfox.cccloud.gametest.BridgeTestSupport;
import ai.ezfox.cccloud.gametest.StorageV2TestSupport;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.autocrafting.CraftingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.PatternState;
import com.refinedmods.refinedstorage.common.autocrafting.autocrafter.AutocrafterBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;

/** Storage v2 on a real RS2 network: creative controller, 1k storage block, an autocrafter with a crafting pattern. */
public final class RsCraftingGameTests {
    private RsCraftingGameTests() {
    }

    public static void crafting(GameTestHelper helper) {
        var controller = new BlockPos(1, 1, 2);
        var storageBlock = new BlockPos(2, 1, 2);
        var bridge = new BlockPos(3, 1, 2);
        var autocrafter = new BlockPos(1, 2, 2); // on top of the controller (the autocrafter does not connect on its front face)
        helper.setBlock(controller, BridgeTestSupport.block(helper, "refinedstorage:creative_controller"));
        helper.setBlock(storageBlock, BridgeTestSupport.block(helper, "refinedstorage:1k_storage_block"));
        helper.setBlock(bridge, RsCompat.BLOCK.get());
        helper.setBlock(autocrafter, BridgeTestSupport.block(helper, "refinedstorage:autocrafter"));

        var stacks = new ArrayList<ItemStack>();
        stacks.add(new ItemStack(Items.OAK_LOG));
        for (int i = 1; i < 9; i++) stacks.add(ItemStack.EMPTY);
        var pattern = new ItemStack(com.refinedmods.refinedstorage.common.content.Items.INSTANCE.getPattern());
        pattern.set(DataComponents.INSTANCE.getPatternState(), new PatternState(UUID.randomUUID(), PatternType.CRAFTING));
        pattern.set(DataComponents.INSTANCE.getCraftingPatternState(), new CraftingPatternState(false, CraftingInput.ofPositioned(3, 3, stacks)));

        java.util.function.Supplier<ai.ezfox.cccloud.bridge.BridgeCore> core = () -> ((RsBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(bridge))).bridgeCore();
        Runnable addLogs = () -> {
            var be = (RsBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(bridge));
            long inserted = be.node().getNetwork().getComponent(StorageNetworkComponent.class).insert(new ItemResource(Items.OAK_LOG), 64, Action.EXECUTE, Actor.EMPTY);
            helper.assertTrue(inserted == 64, "logs inserted: " + inserted);
        };
        var sequence = helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(core.get().isConnected(), "RS network up"))
            .thenExecute(() -> {
                // Insert the pattern once the autocrafter is part of the network.
                var crafterBe = (AutocrafterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(autocrafter));
                crafterBe.getPatternContainer().setItem(0, pattern);
                crafterBe.getPatternContainer().setChanged();
            });
        StorageV2TestSupport.run(helper, sequence, core, addLogs).thenSucceed();
    }
}
