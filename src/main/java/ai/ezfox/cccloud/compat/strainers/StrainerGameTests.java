package ai.ezfox.cccloud.compat.strainers;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import com.benbenlaw.strainers.block.entity.StrainerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** BBL Strainers: an empty strainer says what is missing; with gravel, a mesh and water it strains. */
public final class StrainerGameTests {
    private StrainerGameTests() {
    }

    public static void strainer(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "strainers:strainer"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "strainers_strainer", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "strainers_strainer", linked);
                helper.assertTrue(text(helper, StrainerCards.STATUS).equals("No input"), "status: " + text(helper, StrainerCards.STATUS));
            })
            .thenExecute(() -> {
                var be = helper.getBlockEntity(DEVICE, StrainerBlockEntity.class);
                var mesh = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("strainers", "diamond_mesh"));
                try (var tx = Transaction.openRoot()) {
                    be.getItemHandler().set(StrainerBlockEntity.INPUT_SLOT, ItemResource.of(Items.GRAVEL), 16);
                    be.getItemHandler().set(StrainerBlockEntity.MESH_SLOT, ItemResource.of(mesh), 1);
                    be.getFluidHandler().insert(0, FluidResource.of(Fluids.WATER), 8000, tx);
                    tx.commit();
                }
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(text(helper, StrainerCards.MESH).contains("Mesh"), "mesh: " + text(helper, StrainerCards.MESH));
                helper.assertTrue(text(helper, StrainerCards.FLUID).contains("Water"), "fluid: " + text(helper, StrainerCards.FLUID));
                helper.assertTrue(text(helper, StrainerCards.STATUS).equals("Straining"), "status: " + text(helper, StrainerCards.STATUS));
                helper.assertTrue(number(helper, StrainerCards.PROGRESS) > 0, "progress: " + value(helper, StrainerCards.PROGRESS));
            })
            .thenSucceed();
    }
}
