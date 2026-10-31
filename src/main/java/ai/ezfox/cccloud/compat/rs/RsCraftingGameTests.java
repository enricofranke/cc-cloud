package ai.ezfox.cccloud.compat.rs;

import ai.ezfox.cccloud.gametest.BridgeTestSupport;
import ai.ezfox.cccloud.gametest.StorageV2TestSupport;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.autocrafting.CraftingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.PatternState;
import com.refinedmods.refinedstorage.common.autocrafting.ProcessingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.autocrafter.AutocrafterBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    private static final BlockPos BRIDGE = new BlockPos(3, 1, 2);
    private static final BlockPos AUTOCRAFTER = new BlockPos(1, 2, 2); // on top of the controller (the autocrafter does not connect on its front face)

    public static void crafting(GameTestHelper helper) {
        StorageV2TestSupport.run(helper, setup(helper), () -> core(helper), () -> addLogs(helper)).thenSucceed();
    }

    /** Not enough items: RS only starts a successful preview, so the craft waits as an order and starts once the logs arrive. */
    public static void order(GameTestHelper helper) {
        var planks = StorageV2TestSupport.PLANKS;
        var job = new String[1];
        setup(helper)
            .thenWaitUntil(() -> helper.assertTrue(core(helper).isCraftable(planks), "pattern known"))
            .thenExecute(() -> {
                var ticket = core(helper).craft(planks, 4, "Steve", false, "dashboard");
                helper.assertTrue(ticket.error() == null, "taken as an order: " + ticket.error());
                job[0] = ticket.jobId();
            })
            .thenWaitUntil(() -> {
                var row = row(helper, job[0]);
                helper.assertTrue(row.get("status").getAsString().equals("waiting") && row.get("error").getAsString().equals("not_enough_items"), "waiting: " + row);
                helper.assertTrue(row.toString().contains("minecraft:oak_log"), "names the logs: " + row);
            })
            .thenExecute(() -> {
                addLogs(helper);
                core(helper).advanceTicksForTest(1300);
            })
            .thenWaitUntil(() -> {
                var item = core(helper).getItem(planks);
                helper.assertTrue(item != null && item.count() >= 4, "the order started by itself: " + item + " " + core(helper).jobRows(16));
            })
            .thenSucceed();
    }

    /**
     * A real RS hang: a processing pattern on an autocrafter that faces nothing, so its input has nowhere to go. The job
     * turns stuck with a reason, is reported once, and Retry cancels it and asks for the same craft again.
     */
    public static void stuck(GameTestHelper helper) {
        var planks = StorageV2TestSupport.PLANKS;
        var job = new String[1];
        var first = new String[1];
        var input = new ProcessingPatternState.ProcessingIngredient(new ResourceAmount(new ItemResource(Items.OAK_LOG), 1), List.of());
        var inputs = new ArrayList<Optional<ProcessingPatternState.ProcessingIngredient>>();
        inputs.add(Optional.of(input));
        var outputs = new ArrayList<Optional<ResourceAmount>>();
        outputs.add(Optional.of(new ResourceAmount(new ItemResource(Items.OAK_PLANKS), 4)));
        var pattern = new ItemStack(com.refinedmods.refinedstorage.common.content.Items.INSTANCE.getPattern());
        pattern.set(DataComponents.INSTANCE.getPatternState(), new PatternState(UUID.randomUUID(), PatternType.PROCESSING));
        pattern.set(DataComponents.INSTANCE.getProcessingPatternState(), new ProcessingPatternState(inputs, outputs));
        setup(helper, pattern)
            .thenWaitUntil(() -> helper.assertTrue(core(helper).isCraftable(planks), "pattern known"))
            .thenExecute(() -> addLogs(helper))
            .thenExecute(() -> {
                var ticket = core(helper).craft(planks, 4, "Steve", false, "dashboard");
                helper.assertTrue(ticket.error() == null, "craft accepted: " + ticket.error());
                job[0] = first[0] = ticket.jobId();
            })
            .thenWaitUntil(() -> helper.assertTrue(row(helper, job[0]).get("status").getAsString().equals("running"), "running: " + core(helper).jobRows(16)))
            .thenExecute(() -> core(helper).advanceTicksForTest(1300))
            .thenWaitUntil(() -> helper.assertTrue(row(helper, job[0]).get("status").getAsString().equals("stuck"), "stuck: " + core(helper).jobRows(16)))
            .thenIdle(45)
            .thenExecute(() -> {
                helper.assertTrue(core(helper).stuckSeq() == 1, "reported once: " + core(helper).stuckSeq() + " " + row(helper, job[0]));
                helper.assertTrue(row(helper, job[0]).has("error") && row(helper, job[0]).get("retryable").getAsBoolean(), "a retryable row with a reason: " + row(helper, job[0]));
                var again = core(helper).retryCraft(job[0], "Steve");
                helper.assertTrue(again.error() == null, "retried: " + again.error());
                job[0] = again.jobId();
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(row(helper, first[0]).get("status").getAsString().equals("cancelled"), "old job cancelled: " + core(helper).jobRows(16));
                helper.assertTrue(row(helper, job[0]).get("status").getAsString().equals("running"), "the same craft again: " + core(helper).jobRows(16));
            })
            .thenSucceed();
    }

    private static com.google.gson.JsonObject row(GameTestHelper helper, String jobId) {
        for (var row : core(helper).jobRows(16)) if (row.getAsJsonObject().get("jobId").getAsString().equals(jobId)) return row.getAsJsonObject();
        throw helper.assertionException("no row for job " + jobId + ": " + core(helper).jobRows(16));
    }

    private static ai.ezfox.cccloud.bridge.BridgeCore core(GameTestHelper helper) {
        return ((RsBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(BRIDGE))).bridgeCore();
    }

    private static void addLogs(GameTestHelper helper) {
        var be = (RsBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(BRIDGE));
        long inserted = be.node().getNetwork().getComponent(StorageNetworkComponent.class).insert(new ItemResource(Items.OAK_LOG), 64, Action.EXECUTE, Actor.EMPTY);
        helper.assertTrue(inserted == 64, "logs inserted: " + inserted);
    }

    /** The network with a crafting pattern (oak log → planks) in the autocrafter, once connected. */
    private static net.minecraft.gametest.framework.GameTestSequence setup(GameTestHelper helper) {
        var stacks = new ArrayList<ItemStack>();
        stacks.add(new ItemStack(Items.OAK_LOG));
        for (int i = 1; i < 9; i++) stacks.add(ItemStack.EMPTY);
        var pattern = new ItemStack(com.refinedmods.refinedstorage.common.content.Items.INSTANCE.getPattern());
        pattern.set(DataComponents.INSTANCE.getPatternState(), new PatternState(UUID.randomUUID(), PatternType.CRAFTING));
        pattern.set(DataComponents.INSTANCE.getCraftingPatternState(), new CraftingPatternState(false, CraftingInput.ofPositioned(3, 3, stacks)));
        return setup(helper, pattern);
    }

    private static net.minecraft.gametest.framework.GameTestSequence setup(GameTestHelper helper, ItemStack pattern) {
        var controller = new BlockPos(1, 1, 2);
        var storageBlock = new BlockPos(2, 1, 2);
        helper.setBlock(controller, BridgeTestSupport.block(helper, "refinedstorage:creative_controller"));
        helper.setBlock(storageBlock, BridgeTestSupport.block(helper, "refinedstorage:1k_storage_block"));
        helper.setBlock(BRIDGE, RsCompat.BLOCK.get());
        helper.setBlock(AUTOCRAFTER, BridgeTestSupport.block(helper, "refinedstorage:autocrafter"));
        return helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(core(helper).isConnected(), "RS network up"))
            .thenExecute(() -> {
                // Insert the pattern once the autocrafter is part of the network.
                var crafterBe = (AutocrafterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(AUTOCRAFTER));
                crafterBe.getPatternContainer().setItem(0, pattern);
                crafterBe.getPatternContainer().setChanged();
            });
    }
}
