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

    private static final BlockPos BRIDGE = new BlockPos(3, 1, 2);
    private static final BlockPos CPU = new BlockPos(2, 1, 3);

    public static void crafting(GameTestHelper helper) {
        network(helper, true);
        StorageV2TestSupport.run(helper, helper.startSequence(), () -> core(helper), () -> addLogs(helper)).thenSucceed();
    }

    /**
     * Auto-stock while no crafting CPU exists: the request waits (one {@code waiting} row with {@code no_cpu}, state
     * {@code waiting_cpu}, no failure) through several cooldowns instead of filling the jobs list with failed rows, and
     * crafts once a CPU is built.
     */
    public static void autoStockWaitsForCpu(GameTestHelper helper) {
        network(helper, false);
        var planks = StorageV2TestSupport.PLANKS;
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(core(helper).isConnected() && core(helper).isCraftable(planks), "pattern for oak planks known"))
            .thenExecute(() -> {
                addLogs(helper);
                helper.assertTrue(core(helper).setAutoStock(planks, 4, 8, true) == null, "auto-stock set");
            })
            .thenWaitUntil(() -> helper.assertTrue("waiting_cpu".equals(core(helper).watchInfo(planks).state()),
                "waiting for a CPU: " + core(helper).watchInfo(planks) + " " + core(helper).jobRows(16)))
            // Several cooldowns pass: still exactly one row, never failed.
            .thenExecute(() -> core(helper).advanceClockForTest(ai.ezfox.cccloud.bridge.Watchlist.COOLDOWN_MS))
            .thenIdle(25)
            .thenExecute(() -> core(helper).advanceClockForTest(ai.ezfox.cccloud.bridge.Watchlist.COOLDOWN_MS))
            .thenIdle(25)
            .thenExecute(() -> core(helper).advanceClockForTest(ai.ezfox.cccloud.bridge.Watchlist.COOLDOWN_MS))
            .thenIdle(25)
            .thenExecute(() -> {
                var rows = core(helper).jobRows(16);
                helper.assertTrue(rows.size() == 1, "one row for the item: " + rows);
                var row = rows.get(0).getAsJsonObject();
                helper.assertTrue(row.get("status").getAsString().equals("waiting") && row.get("error").getAsString().equals("no_cpu")
                    && row.get("source").getAsString().equals("autostock"), "a waiting auto-stock row: " + row);
                var info = core(helper).watchInfo(planks);
                helper.assertTrue(info.state().equals("waiting_cpu") && info.failures() == 0, "no failure counted: " + info);
            })
            // A CPU appears: the same job is planned and crafts.
            .thenExecute(() -> {
                helper.setBlock(CPU, BridgeTestSupport.block(helper, "ae2:1k_crafting_storage"));
                core(helper).advanceClockForTest(ai.ezfox.cccloud.bridge.Watchlist.COOLDOWN_MS);
            })
            .thenWaitUntil(() -> {
                var row = core(helper).getItem(planks);
                helper.assertTrue(row != null && row.count() >= 4, "crafted once a CPU was free: " + row + " " + core(helper).jobRows(16));
            })
            .thenExecute(() -> {
                var rows = core(helper).jobRows(16).toString();
                helper.assertTrue(!rows.contains("\"failed\"") && !rows.contains("\"waiting\""), "no failed or waiting rows left: " + rows);
            })
            .thenSucceed();
    }

    /**
     * A real AE2 hang: a pattern with no assembler to run it. The job turns {@code stuck} after the configured time with
     * one report, Retry cancels it and asks for the same craft again, and it finishes once the assembler is there.
     */
    public static void stuck(GameTestHelper helper) {
        network(helper, true, false);
        var planks = StorageV2TestSupport.PLANKS;
        var job = new String[1];
        var first = new String[1];
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(core(helper).isConnected() && core(helper).isCraftable(planks), "pattern for oak planks known"))
            .thenExecute(() -> addLogs(helper))
            .thenIdle(5)
            .thenExecute(() -> {
                var ticket = core(helper).craft(planks, 4, "Steve", false, "dashboard");
                helper.assertTrue(ticket.error() == null, "craft accepted: " + ticket.error());
                job[0] = first[0] = ticket.jobId();
            })
            .thenWaitUntil(() -> helper.assertTrue(status(helper, job[0]).equals("running"), "running: " + core(helper).jobRows(16)))
            .thenIdle(40)
            .thenExecute(() -> helper.assertTrue(status(helper, job[0]).equals("running") && core(helper).stuckSeq() == 0, "not stuck yet"))
            .thenExecute(() -> core(helper).advanceTicksForTest(1300))
            .thenWaitUntil(() -> helper.assertTrue(status(helper, job[0]).equals("stuck"), "stuck: " + core(helper).jobRows(16)))
            .thenIdle(45)
            .thenExecute(() -> {
                helper.assertTrue(core(helper).stuckSeq() == 1, "reported once: " + core(helper).stuckSeq());
                var row = row(helper, job[0]);
                helper.assertTrue(row.get("retryable").getAsBoolean() && row.has("error"), "a retryable row with a reason: " + row);
                helper.setBlock(new BlockPos(1, 1, 4), BridgeTestSupport.block(helper, "ae2:molecular_assembler"));
            })
            .thenIdle(5)
            .thenExecute(() -> {
                var again = core(helper).retryCraft(job[0], "Steve");
                helper.assertTrue(again.error() == null, "retried: " + again.error());
                job[0] = again.jobId();
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(status(helper, first[0]).equals("cancelled"), "old job cancelled: " + core(helper).jobRows(16));
                var item = core(helper).getItem(planks);
                helper.assertTrue(item != null && item.count() >= 4, "crafted after the retry: " + item + " " + core(helper).jobRows(16));
            })
            .thenSucceed();
    }

    /**
     * Not enough items: AE2 refuses the simulated plan, so the craft waits as an order with what it misses (no failure,
     * no stuck report), the maximum search answers 0, and the order starts by itself once the logs arrive.
     */
    public static void order(GameTestHelper helper) {
        network(helper, true);
        var planks = StorageV2TestSupport.PLANKS;
        var job = new String[1];
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(core(helper).isConnected() && core(helper).isCraftable(planks), "pattern for oak planks known"))
            .thenExecute(() -> {
                var ticket = core(helper).craft(planks, 4, "Steve", false, "dashboard");
                helper.assertTrue(ticket.error() == null, "taken as an order: " + ticket.error());
                job[0] = ticket.jobId();
            })
            .thenWaitUntil(() -> {
                var row = row(helper, job[0]);
                helper.assertTrue(row.get("status").getAsString().equals("waiting") && row.get("error").getAsString().equals("not_enough_items"), "waiting: " + row);
                var missing = row.getAsJsonArray("missing");
                helper.assertTrue(missing != null && missing.toString().contains("minecraft:oak_log"), "names the logs: " + row);
            })
            .thenExecute(() -> core(helper).advanceTicksForTest(1300))
            .thenIdle(45)
            .thenExecute(() -> {
                helper.assertTrue(status(helper, job[0]).equals("waiting") && core(helper).stuckSeq() == 0, "a waiting order is not stuck: " + core(helper).jobRows(16));
                addLogs(helper);
                core(helper).advanceTicksForTest(1300);
            })
            .thenWaitUntil(() -> {
                var item = core(helper).getItem(planks);
                helper.assertTrue(item != null && item.count() >= 4, "the order started by itself: " + item + " " + core(helper).jobRows(16));
            })
            .thenSucceed();
    }

    private static com.google.gson.JsonObject row(GameTestHelper helper, String jobId) {
        for (var row : core(helper).jobRows(16)) if (row.getAsJsonObject().get("jobId").getAsString().equals(jobId)) return row.getAsJsonObject();
        throw helper.assertionException("no row for job " + jobId + ": " + core(helper).jobRows(16));
    }

    private static String status(GameTestHelper helper, String jobId) {
        return row(helper, jobId).get("status").getAsString();
    }

    private static ai.ezfox.cccloud.bridge.BridgeCore core(GameTestHelper helper) {
        return ((MeBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(BRIDGE))).bridgeCore();
    }

    private static void addLogs(GameTestHelper helper) {
        var bridgeBe = (MeBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(BRIDGE));
        long inserted = bridgeBe.managedNode().getGrid().getStorageService().getInventory()
            .insert(AEItemKey.of(Items.OAK_LOG), 64, Actionable.MODULATE, IActionSource.ofMachine(bridgeBe));
        helper.assertTrue(inserted == 64, "logs inserted: " + inserted);
    }

    /** The network; {@code withCpu} adds the 1k crafting storage (one CPU). */
    private static void network(GameTestHelper helper, boolean withCpu) {
        network(helper, withCpu, true);
    }

    /** {@code withAssembler} false: the pattern is known, but no machine can run it. */
    @SuppressWarnings("unchecked")
    private static void network(GameTestHelper helper, boolean withCpu, boolean withAssembler) {
        var energy = new BlockPos(1, 1, 2);
        var drive = new BlockPos(2, 1, 2);
        var provider = new BlockPos(1, 1, 3);
        var assembler = new BlockPos(1, 1, 4);
        helper.setBlock(energy, BridgeTestSupport.block(helper, "ae2:creative_energy_cell"));
        helper.setBlock(drive, BridgeTestSupport.block(helper, "ae2:drive"));
        helper.setBlock(BRIDGE, Ae2Compat.BLOCK.get());
        if (withCpu) helper.setBlock(CPU, BridgeTestSupport.block(helper, "ae2:1k_crafting_storage"));
        helper.setBlock(provider, BridgeTestSupport.block(helper, "ae2:pattern_provider"));
        if (withAssembler) helper.setBlock(assembler, BridgeTestSupport.block(helper, "ae2:molecular_assembler"));

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

    }
}
