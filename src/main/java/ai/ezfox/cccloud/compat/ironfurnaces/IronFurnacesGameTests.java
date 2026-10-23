package ai.ezfox.cccloud.compat.ironfurnaces;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import ironfurnaces.tileentity.furnaces.BlockIronFurnaceTileBase;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Iron Furnaces: an iron furnace smelts raw iron with coal; the card shows it, and the redstone mode can be switched. */
public final class IronFurnacesGameTests {
    private IronFurnacesGameTests() {
    }

    private static BlockIronFurnaceTileBase be(GameTestHelper helper) {
        return (BlockIronFurnaceTileBase) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
    }

    public static void furnace(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "ironfurnaces:iron_furnace"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenExecute(() -> {
                be(helper).inventory.set(BlockIronFurnaceTileBase.INPUT, new ItemStack(Items.RAW_IRON, 8));
                be(helper).inventory.set(BlockIronFurnaceTileBase.FUEL, new ItemStack(Items.COAL, 4));
                be(helper).setChanged();
            })
            .thenWaitUntil(() -> assertCard(helper, "ironfurnaces_furnace", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "ironfurnaces_furnace", linked);
                helper.assertTrue(uplink(helper).getTitle().equals("Iron Furnace"), "title: " + uplink(helper).getTitle());
                helper.assertTrue(text(helper, IronFurnacesCards.OUTPUT).endsWith("Iron Ingot"), "output: " + text(helper, IronFurnacesCards.OUTPUT));
                helper.assertTrue(text(helper, IronFurnacesCards.REDSTONE).equals("Ignore redstone"), "mode: " + text(helper, IronFurnacesCards.REDSTONE));
            })
            .thenExecute(() -> {
                helper.assertTrue(act(helper, IronFurnacesCards.REDSTONE, "Run with signal") == null, "mode switched");
                helper.assertTrue(be(helper).furnaceSettings.getRedstoneSetting() == 2, "furnace setting");
                helper.assertTrue("invalid_value".equals(act(helper, IronFurnacesCards.REDSTONE, "Sometimes")), "unknown option refused");
            })
            .thenSucceed();
    }
}
