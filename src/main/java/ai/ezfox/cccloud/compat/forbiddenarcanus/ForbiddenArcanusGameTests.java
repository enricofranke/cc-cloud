package ai.ezfox.cccloud.compat.forbiddenarcanus;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;

/** Forbidden &amp; Arcanus: a lone Hephaestus Forge shows tier 1, idle, and empty essences. */
public final class ForbiddenArcanusGameTests {
    private ForbiddenArcanusGameTests() {
    }

    public static void forge(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "forbidden_arcanus:hephaestus_forge"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "forbiddenarcanus_forge", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "forbiddenarcanus_forge", linked);
                helper.assertTrue(text(helper, HephaestusForgeCards.STATUS).startsWith("Tier 1"), "status: " + text(helper, HephaestusForgeCards.STATUS));
                helper.assertTrue(number(helper, "essence_aureal") == 0, "no aureal yet");
            })
            .thenSucceed();
    }
}
