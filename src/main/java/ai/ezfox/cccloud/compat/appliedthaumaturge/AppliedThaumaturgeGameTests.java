package ai.ezfox.cccloud.compat.appliedthaumaturge;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Applied Thaumaturge: assembler and Essentia Interface get their cards; without a controller the network is offline. */
public final class AppliedThaumaturgeGameTests {
    private AppliedThaumaturgeGameTests() {
    }

    public static void assembler(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "appliedthaumaturge:arcane_assembler"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "appliedthaumaturge_assembler", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, "appliedthaumaturge_assembler", linked))
            .thenWaitUntil(() -> helper.assertTrue("No ME network".equals(text(helper, AppliedThaumaturgeCards.STATUS)), "status: " + text(helper, AppliedThaumaturgeCards.STATUS)))
            .thenExecute(() -> {
                helper.assertTrue("0".equals(text(helper, AppliedThaumaturgeCards.PATTERNS)), "no patterns");
                helper.assertTrue("Not linked".equals(text(helper, AppliedThaumaturgeCards.LINK)), "no vis link");
            })
            .thenSucceed();
    }

    public static void essentiaInterface(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "appliedthaumaturge:essentia_interface"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "appliedthaumaturge_essentia", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, "appliedthaumaturge_essentia", linked))
            .thenWaitUntil(() -> helper.assertTrue("Offline".equals(text(helper, AppliedThaumaturgeCards.STATUS)), "status: " + text(helper, AppliedThaumaturgeCards.STATUS)))
            .thenSucceed();
    }
}
