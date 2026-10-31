package ai.ezfox.cccloud.compat.integratedtunnels;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.cyclops.integrateddynamics.RegistryEntries;
import org.cyclops.integrateddynamics.core.helper.PartHelpers;
import org.cyclops.integratedtunnels.part.PartTypes;

/**
 * Integrated Tunnels: a logic cable with an Item Interface on a chest. The uplink on the cable shows the chest's items
 * through the network's index and finds them in the search.
 */
public final class ItemNetworkGameTests {
    private ItemNetworkGameTests() {
    }

    public static void network(GameTestHelper helper) {
        BlockPos chest = DEVICE.west();
        helper.setBlock(chest, Blocks.CHEST);
        if (helper.getBlockEntity(chest, ChestBlockEntity.class) instanceof ChestBlockEntity c) c.setItem(0, new ItemStack(Items.IRON_INGOT, 40));
        helper.setBlock(DEVICE, RegistryEntries.BLOCK_CABLE.value());
        PartHelpers.addPart(helper.getLevel(), helper.absolutePos(DEVICE), Direction.WEST, PartTypes.INTERFACE_ITEM, new ItemStack(PartTypes.INTERFACE_ITEM.getItem()));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "integratedtunnels_network", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "integratedtunnels_network", linked);
                helper.assertTrue(text(helper, ItemNetworkCards.INTERFACES).equals("1"), "interfaces: " + text(helper, ItemNetworkCards.INTERFACES));
                helper.assertTrue(text(helper, ItemNetworkCards.ITEMS).equals("40"), "items: " + text(helper, ItemNetworkCards.ITEMS));
                helper.assertTrue(text(helper, ItemNetworkCards.TYPES).equals("1"), "types: " + text(helper, ItemNetworkCards.TYPES));
                var found = uplink(helper).handleQuery(ItemNetworkCards.Network.SEARCH, "iron");
                helper.assertTrue(found.error() == null && found.rows().size() == 1, "search: " + found);
            })
            .thenSucceed();
    }
}
