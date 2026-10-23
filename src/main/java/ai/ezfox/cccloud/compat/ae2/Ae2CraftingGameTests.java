package ai.ezfox.cccloud.compat.ae2;

import ai.ezfox.cccloud.gametest.BridgeTestSupport;
import ai.ezfox.cccloud.gametest.StorageV2TestSupport;
import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Storage v2 on a real AE2 network: creative energy, drive + 1k cell, a 1k crafting storage (one CPU), a pattern
 * provider with an encoded crafting pattern (oak log → 4 oak planks) next to a molecular assembler.
 */
public final class Ae2CraftingGameTests {
    private Ae2CraftingGameTests() {
    }

    @SuppressWarnings("unchecked")
    public static void crafting(GameTestHelper helper) {
        var energy = new BlockPos(1, 1, 2);
        var drive = new BlockPos(2, 1, 2);
        var bridge = new BlockPos(3, 1, 2);
        var cpu = new BlockPos(2, 1, 3);
        var provider = new BlockPos(1, 1, 3);
        var assembler = new BlockPos(1, 1, 4);
        helper.setBlock(energy, BridgeTestSupport.block(helper, "ae2:creative_energy_cell"));
        helper.setBlock(drive, BridgeTestSupport.block(helper, "ae2:drive"));
        helper.setBlock(bridge, Ae2Compat.BLOCK.get());
        helper.setBlock(cpu, BridgeTestSupport.block(helper, "ae2:1k_crafting_storage"));
        helper.setBlock(provider, BridgeTestSupport.block(helper, "ae2:pattern_provider"));
        helper.setBlock(assembler, BridgeTestSupport.block(helper, "ae2:molecular_assembler"));

        var cell = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:item_storage_cell_1k"));
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(drive), null);
        helper.assertTrue(handler != null, "ME drive exposes no item handler");
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(handler.insert(ItemResource.of(cell), 1, tx) == 1, "cell into the drive");
            tx.commit();
        }

        // The encoded crafting pattern (public AE2 API) into the provider's pattern slots (AE2 internals, test only).
        var server = helper.getLevel().getServer();
        var recipe = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse("minecraft:oak_planks")));
        helper.assertTrue(recipe.isPresent(), "oak planks recipe");
        var inputs = new ItemStack[9];
        java.util.Arrays.fill(inputs, ItemStack.EMPTY);
        inputs[0] = new ItemStack(Items.OAK_LOG);
        var pattern = PatternDetailsHelper.encodeCraftingPattern((RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) recipe.get(), inputs,
            new ItemStack(Items.OAK_PLANKS, 4), false, false);
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(provider));
        try {
            var logic = be.getClass().getMethod("getLogic").invoke(be);
            var inv = logic.getClass().getMethod("getPatternInv").invoke(logic);
            var rest = (ItemStack) inv.getClass().getMethod("addItems", ItemStack.class).invoke(inv, pattern);
            helper.assertTrue(rest.isEmpty(), "pattern accepted by the provider");
        } catch (ReflectiveOperationException e) {
            throw helper.assertionException("could not insert the pattern: " + e);
        }

        java.util.function.Supplier<ai.ezfox.cccloud.bridge.BridgeCore> core = () -> ((MeBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(bridge))).bridgeCore();
        Runnable addLogs = () -> {
            var bridgeBe = (MeBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(bridge));
            long inserted = bridgeBe.managedNode().getGrid().getStorageService().getInventory()
                .insert(AEItemKey.of(Items.OAK_LOG), 64, Actionable.MODULATE, IActionSource.ofMachine(bridgeBe));
            helper.assertTrue(inserted == 64, "logs inserted: " + inserted);
        };
        StorageV2TestSupport.run(helper, helper.startSequence(), core, addLogs).thenSucceed();
    }
}
