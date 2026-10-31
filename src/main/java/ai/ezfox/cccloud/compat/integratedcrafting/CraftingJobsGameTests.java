package ai.ezfox.cccloud.compat.integratedcrafting;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.cyclops.commoncapabilities.api.ingredient.MixedIngredients;
import org.cyclops.integratedcrafting.api.crafting.CraftingJob;
import org.cyclops.integratedcrafting.api.crafting.ICraftingInterface;
import org.cyclops.integratedcrafting.core.CraftingHelpers;
import org.cyclops.integratedcrafting.core.network.CraftingNetwork;
import org.cyclops.integratedcrafting.gametest.GameTestHelpersIntegratedCrafting;
import org.cyclops.integratedcrafting.part.PartTypes;
import org.cyclops.integrateddynamics.RegistryEntries;
import org.cyclops.integrateddynamics.api.part.PartPos;
import org.cyclops.integrateddynamics.core.helper.NetworkHelpers;
import org.cyclops.integrateddynamics.core.helper.PartHelpers;

/**
 * Integrated Crafting: a logic cable with a Crafting Interface shows the job list; a job handed to the interface gets a
 * row with what it makes (it can never run: nothing is behind the interface), and Cancel all removes it.
 */
public final class CraftingJobsGameTests {
    private CraftingJobsGameTests() {
    }

    /**
     * What the network does after handing a job to an interface. Its public scheduling needs a recipe from a machine
     * behind the interface, which this test does not have.
     */
    private static void index(GameTestHelper helper, CraftingJob job, ICraftingInterface state) {
        var network = NetworkHelpers.getNetwork(helper.getLevel(), helper.absolutePos(DEVICE), deviceSide()).orElseThrow();
        try {
            var add = CraftingNetwork.class.getDeclaredMethod("addCraftingJob", int.class, CraftingJob.class, ICraftingInterface.class);
            add.setAccessible(true);
            add.invoke(CraftingHelpers.getCraftingNetwork(network).orElseThrow(), job.getChannel(), job, state);
        } catch (ReflectiveOperationException e) {
            throw helper.assertionException("cannot index the job: " + e);
        }
    }

    public static void jobs(GameTestHelper helper) {
        helper.setBlock(DEVICE, RegistryEntries.BLOCK_CABLE.value());
        var part = PartTypes.INTERFACE_CRAFTING;
        PartHelpers.addPart(helper.getLevel(), helper.absolutePos(DEVICE), Direction.WEST, part, new ItemStack(part.getItem()));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "integratedcrafting_jobs", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "integratedcrafting_jobs", linked);
                helper.assertTrue(text(helper, CraftingJobsCards.STATUS).equals("No jobs"), "status: " + text(helper, CraftingJobsCards.STATUS));
                helper.assertTrue(text(helper, CraftingJobsCards.INTERFACES).equals("1"), "interfaces: " + text(helper, CraftingJobsCards.INTERFACES));
            })
            .thenExecute(() -> {
                var pos = PartPos.of(helper.getLevel(), helper.absolutePos(DEVICE), Direction.WEST);
                var state = (ICraftingInterface) PartHelpers.getPart(pos).getState();
                var recipe = GameTestHelpersIntegratedCrafting.createRecipeDefinition(helper.getLevel(), RecipeType.CRAFTING, Identifier.withDefaultNamespace("stick"));
                var job = new CraftingJob(4242, 0, recipe, 3, MixedIngredients.fromRecipeInput(recipe));
                state.scheduleCraftingJob(job);
                index(helper, job, state);
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(text(helper, CraftingJobsCards.JOB + 0).startsWith("12× Stick"), "job: " + text(helper, CraftingJobsCards.JOB + 0));
                helper.assertTrue(!text(helper, CraftingJobsCards.STATUS).equals("No jobs"), "status: " + text(helper, CraftingJobsCards.STATUS));
            })
            .thenExecute(() -> helper.assertTrue(act(helper, CraftingJobsCards.CANCEL, true) == null, "cancel"))
            .thenWaitUntil(() -> helper.assertTrue(text(helper, CraftingJobsCards.STATUS).equals("No jobs"), "cancelled: " + text(helper, CraftingJobsCards.STATUS)))
            .thenSucceed();
    }
}
