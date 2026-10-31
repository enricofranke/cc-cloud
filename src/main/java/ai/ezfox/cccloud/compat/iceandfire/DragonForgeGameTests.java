package ai.ezfox.cccloud.compat.iceandfire;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import com.iafenvoy.iceandfire.item.block.entity.DragonForgeBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ice and Fire: a lone forge core is "not assembled", shows its element and its inputs; the private cook time is readable. */
public final class DragonForgeGameTests {
    private DragonForgeGameTests() {
    }

    public static void forge(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "iceandfire:dragonforge_fire_core"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "iceandfire_forge", null))
            .thenExecute(() -> {
                bind(helper, linked);
                var forge = (DragonForgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                helper.assertTrue(DragonForgeCards.cookTime(forge) == 0, "cook time readable");
                forge.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
            })
            .thenWaitUntil(() -> assertCard(helper, "iceandfire_forge", linked))
            .thenWaitUntil(() -> helper.assertTrue("2 × Iron Ingot".equals(text(helper, DragonForgeCards.INPUT)), "input: " + text(helper, DragonForgeCards.INPUT)))
            .thenExecute(() -> {
                helper.assertTrue("Not assembled".equals(text(helper, DragonForgeCards.STATUS)), "lone core: " + text(helper, DragonForgeCards.STATUS));
                helper.assertTrue("Fire".equals(text(helper, DragonForgeCards.TYPE)), "element: " + text(helper, DragonForgeCards.TYPE));
            })
            .thenSucceed();
    }
}
