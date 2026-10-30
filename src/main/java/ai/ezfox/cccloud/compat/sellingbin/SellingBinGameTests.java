package ai.ezfox.cccloud.compat.sellingbin;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import com.wdiscute.sellingbin.bin.SellingBinBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Selling Bin: a bin placed alone (it grows its second block) gets the card; auto-sell switches on and off again in
 * the same tick (a test world has no currencies, so the bin must not run a sale cycle); "Sell now" with nothing in it
 * is refused.
 */
public final class SellingBinGameTests {
    private SellingBinGameTests() {
    }

    public static void bin(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "selling_bin:selling_bin"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "sellingbin_bin", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "sellingbin_bin", linked);
                helper.assertTrue("Empty".equals(text(helper, SellingBinCards.SELLING)), "nothing to sell: " + text(helper, SellingBinCards.SELLING));
                helper.assertTrue("0".equals(text(helper, SellingBinCards.CREDIT)), "no credit: " + text(helper, SellingBinCards.CREDIT));
            })
            .thenExecute(() -> {
                helper.assertTrue("unavailable".equals(act(helper, SellingBinCards.SELL, true)), "empty bin: nothing to sell");
                helper.assertTrue(act(helper, SellingBinCards.AUTO, true) == null, "auto-sell on");
                boolean on = binAt(helper).instaSell;
                helper.assertTrue(act(helper, SellingBinCards.AUTO, false) == null, "auto-sell off");
                helper.assertTrue(on && !binAt(helper).instaSell, "the bin's own flag follows");
            })
            .thenSucceed();
    }

    private static SellingBinBlockEntity binAt(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(DEVICE);
        var center = net.nikdo53.tinymultiblocklib.block.IMultiBlock.getCenter(level, pos);
        return (SellingBinBlockEntity) level.getBlockEntity(center == null ? pos : center);
    }
}
