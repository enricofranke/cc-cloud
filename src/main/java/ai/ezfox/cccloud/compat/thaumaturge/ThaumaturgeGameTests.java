package ai.ezfox.cccloud.compat.thaumaturge;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.nodes.NodeType;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.aura.node.NodeData;
import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTestHelper;

/** Thaumaturge: a jar shows what is put in it, a node its aspects; lone matrix and crucible get their cards with the chunk's aura. */
public final class ThaumaturgeGameTests {
    private ThaumaturgeGameTests() {
    }

    public static void jar(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "thaumaturge:jar_normal"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "thaumaturge_jar", null))
            .thenExecute(() -> {
                bind(helper, linked);
                var jar = (BlockEntityJar) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                jar.setStorageContents(TTAspects.IGNIS, 50);
            })
            .thenWaitUntil(() -> assertCard(helper, "thaumaturge_jar", linked))
            .thenWaitUntil(() -> helper.assertTrue("Ignis 50".equals(text(helper, ThaumaturgeCards.ASPECTS)), "aspect: " + text(helper, ThaumaturgeCards.ASPECTS)))
            .thenExecute(() -> {
                helper.assertTrue(number(helper, ThaumaturgeCards.ESSENTIA) == 20, "20 % of 250");
                helper.assertTrue("50 / 250".equals(text(helper, ThaumaturgeCards.AMOUNT)), "amount");
            })
            .thenSucceed();
    }

    public static void node(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "thaumaturge:node"));
        // A node without aspects removes itself on its first tick.
        var ignis = helper.getLevel().registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY).getOrThrow(TTAspects.IGNIS);
        var aspects = AspectList.of(new AspectInstance(ignis, 40));
        ((BlockEntityNode) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE))).applyNodeData(new NodeData(NodeType.NORMAL, Optional.empty(), aspects, aspects));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "thaumaturge_node", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, "thaumaturge_node", linked))
            .thenWaitUntil(() -> helper.assertTrue(text(helper, ThaumaturgeCards.AURA).startsWith("Vis "), "aura: " + text(helper, ThaumaturgeCards.AURA)))
            .thenExecute(() -> {
                helper.assertTrue("Normal".equals(text(helper, ThaumaturgeCards.TYPE)), "type: " + text(helper, ThaumaturgeCards.TYPE));
                helper.assertTrue(text(helper, ThaumaturgeCards.ASPECTS).startsWith("Ignis "), "aspects: " + text(helper, ThaumaturgeCards.ASPECTS));
            })
            .thenSucceed();
    }

    public static void matrix(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "thaumaturge:infusion_matrix"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "thaumaturge_matrix", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, "thaumaturge_matrix", linked))
            .thenWaitUntil(() -> helper.assertTrue("Not activated".equals(text(helper, ThaumaturgeCards.STATE)), "lone matrix: " + text(helper, ThaumaturgeCards.STATE)))
            .thenSucceed();
    }

    public static void crucible(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "thaumaturge:crucible"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "thaumaturge_crucible", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, "thaumaturge_crucible", linked))
            .thenWaitUntil(() -> helper.assertTrue("0 / 1000 mB".equals(text(helper, ThaumaturgeCards.WATER)), "water: " + text(helper, ThaumaturgeCards.WATER)))
            .thenSucceed();
    }
}
