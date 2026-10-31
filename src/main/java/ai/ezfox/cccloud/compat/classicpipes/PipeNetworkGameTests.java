package ai.ezfox.cccloud.compat.classicpipes;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Classic Pipes: a request pipe with a provider pipe on a chest. The pipes find each other, the provider reports the
 * chest and the card shows it on the request pipe, the way the Request Pipe's own list does.
 */
public final class PipeNetworkGameTests {
    private PipeNetworkGameTests() {
    }

    /** Sets a pipe with the connections a player's placement would give it. */
    private static void place(GameTestHelper helper, BlockPos pos, String id) {
        var state = block(helper, id).defaultBlockState();
        helper.setBlock(pos, state);
        var level = helper.getLevel();
        var abs = helper.absolutePos(pos);
        helper.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(abs), level, abs));
    }

    public static void network(GameTestHelper helper) {
        BlockPos provider = DEVICE.west();
        BlockPos chest = provider.west();
        helper.setBlock(chest, Blocks.CHEST);
        helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(Items.REDSTONE, 24));
        place(helper, DEVICE, "classicpipes:request_pipe");
        place(helper, provider, "classicpipes:provider_pipe");
        place(helper, DEVICE, "classicpipes:request_pipe");
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "classicpipes_network", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "classicpipes_network", linked);
                helper.assertTrue(text(helper, PipeNetworkCards.STATUS).equals("Connected"), "status: " + text(helper, PipeNetworkCards.STATUS));
                helper.assertTrue(text(helper, PipeNetworkCards.ITEMS).equals("24"), "items: " + text(helper, PipeNetworkCards.ITEMS));
                var found = uplink(helper).handleQuery(PipeNetworkCards.Network.SEARCH, "redstone");
                helper.assertTrue(found.error() == null && found.rows().size() == 1, "search: " + found.rows());
            })
            .thenSucceed();
    }
}
