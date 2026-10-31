package ai.ezfox.cccloud.compat.apothicenchanting;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import dev.shadowsoffire.apothic_enchanting.library.EnchLibraryTile;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/** Apothic Enchanting: a book put into the library shows up as a row with its level and points. */
public final class LibraryGameTests {
    private LibraryGameTests() {
    }

    public static void library(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "apothic_enchanting:library"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "apothicenchanting_library", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, "apothicenchanting_library", linked))
            .thenWaitUntil(() -> helper.assertTrue("No enchantments yet".equals(text(helper, LibraryCards.EMPTY)), "empty line"))
            .thenExecute(() -> {
                var sharpness = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
                ItemStack book = EnchantmentHelper.createBook(new EnchantmentInstance(sharpness, 3));
                ((EnchLibraryTile) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE))).depositBook(book);
            })
            .thenWaitUntil(() -> helper.assertTrue(uplink(helper).getWidget(LibraryCards.ROW + 1) != null, "row appears"))
            .thenWaitUntil(() -> {
                helper.assertTrue("1".equals(text(helper, LibraryCards.COUNT)), "one enchantment");
                var row = text(helper, LibraryCards.ROW + 1);
                helper.assertTrue(row.equals("Sharpness 3 (4 pts)"), "row: " + row);
            })
            .thenSucceed();
    }
}
