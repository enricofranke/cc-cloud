package ai.ezfox.cccloud.compat.cobblegengalore;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** Cobblegen Galore: a stone generator between water and lava makes cobblestone; the card shows it and the buffer fills. */
public final class CobblegenGameTests {
    private CobblegenGameTests() {
    }

    public static void generator(GameTestHelper helper) {
        // Walls keep the fluids next to the generator.
        for (var p : new BlockPos[]{new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(2, 1, 0), new BlockPos(1, 1, 3), new BlockPos(3, 1, 3), new BlockPos(2, 1, 4)}) {
            helper.setBlock(p, Blocks.GLASS);
        }
        helper.setBlock(DEVICE, block(helper, "cobblegengalore:block_gen_stone"));
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.LAVA);
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "cobblegengalore_generator", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "cobblegengalore_generator", linked);
                helper.assertTrue(text(helper, CobblegenCards.PRODUCING).equals("Cobblestone"), "producing: " + text(helper, CobblegenCards.PRODUCING));
                helper.assertTrue(!text(helper, CobblegenCards.BUFFER).startsWith("0 /"), "buffer: " + text(helper, CobblegenCards.BUFFER));
            })
            .thenSucceed();
    }
}
