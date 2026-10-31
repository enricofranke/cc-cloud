package ai.ezfox.cccloud.compat.pipez;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import de.maxhenkel.pipez.blocks.PipeBlock;
import de.maxhenkel.pipez.blocks.tileentity.ItemPipeTileEntity;
import de.maxhenkel.pipez.items.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Pipez: an item pipe between two chests. A pipe only has a block entity once it is configured, and an uplink only holds
 * on one that has, so extraction comes first (the mod's own call); it reports side and target, with an upgrade also the
 * rate, and "Always off" from the dashboard switches it off through the mod's own redstone mode.
 */
public final class PipeGameTests {
    private PipeGameTests() {
    }

    private static ItemPipeTileEntity entity(GameTestHelper helper) {
        return helper.getBlockEntity(DEVICE, ItemPipeTileEntity.class);
    }

    public static void pipe(GameTestHelper helper) {
        BlockPos source = DEVICE.west();
        BlockPos target = DEVICE.north();
        helper.setBlock(source, Blocks.CHEST);
        helper.setBlock(target, Blocks.CHEST);
        helper.setBlock(DEVICE, block(helper, "pipez:item_pipe"));
        var state = helper.getBlockState(DEVICE);
        ((PipeBlock) state.getBlock()).setExtracting(helper.getLevel(), helper.absolutePos(DEVICE), Direction.WEST, true);
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "pipez_pipe", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "pipez_pipe", linked);
                helper.assertTrue(text(helper, PipeCards.EXTRACTING).equals("West"), "extracting: " + text(helper, PipeCards.EXTRACTING));
                helper.assertTrue(text(helper, PipeCards.STATUS).equals("Running"), "status: " + text(helper, PipeCards.STATUS));
            })
            .thenExecute(() -> entity(helper).setUpgradeItem(Direction.WEST, new ItemStack(ModItems.ADVANCED_UPGRADE.get())))
            .thenWaitUntil(() -> {
                helper.assertTrue(text(helper, PipeCards.UPGRADE).equals("Advanced"), "upgrade: " + text(helper, PipeCards.UPGRADE));
                helper.assertTrue(text(helper, PipeCards.RATE).contains("items /"), "rate: " + text(helper, PipeCards.RATE));
            })
            .thenExecute(() -> helper.assertTrue(act(helper, PipeCards.REDSTONE, "Always off") == null, "switch off"))
            .thenWaitUntil(() -> {
                helper.assertTrue(text(helper, PipeCards.STATUS).equals("Switched off"), "status: " + text(helper, PipeCards.STATUS));
                helper.assertTrue(text(helper, PipeCards.REDSTONE).equals("Always off"), "mode: " + text(helper, PipeCards.REDSTONE));
            })
            .thenSucceed();
    }
}
