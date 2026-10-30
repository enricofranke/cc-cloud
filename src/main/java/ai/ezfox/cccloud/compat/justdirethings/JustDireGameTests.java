package ai.ezfox.cccloud.compat.justdirethings;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import com.direwolf20.justdirethings.common.blockentities.basebe.RedstoneControlledBE;
import com.direwolf20.justdirethings.util.MiscHelpers;
import net.minecraft.gametest.framework.GameTestHelper;

/** Just Dire Things: a coal generator, a block breaker and a sensor placed alone; the redstone mode select switches the machine's own mode. */
public final class JustDireGameTests {
    private JustDireGameTests() {
    }

    private static RedstoneControlledBE be(GameTestHelper helper) {
        return (RedstoneControlledBE) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
    }

    public static void generator(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "justdirethings:generatort1"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "justdirethings_generator", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "justdirethings_generator", linked);
                helper.assertTrue(uplink(helper).getTitle().equals("Simple Coal Generator"), "title: " + uplink(helper).getTitle());
                helper.assertTrue("No fuel".equals(text(helper, JustDireCards.STATUS)), "status: " + text(helper, JustDireCards.STATUS));
                helper.assertTrue("Empty".equals(text(helper, JustDireCards.EXTRA)), "fuel slot: " + text(helper, JustDireCards.EXTRA));
                helper.assertTrue("Always on".equals(text(helper, JustDireCards.REDSTONE)), "mode: " + text(helper, JustDireCards.REDSTONE));
            })
            .thenExecute(() -> {
                helper.assertTrue(act(helper, JustDireCards.REDSTONE, "On with signal") == null, "mode set");
                helper.assertTrue(be(helper).getRedstoneControlData().redstoneMode == MiscHelpers.RedstoneMode.HIGH, "generator mode HIGH");
                helper.assertTrue("invalid_value".equals(act(helper, JustDireCards.REDSTONE, "Sometimes")), "unknown mode refused");
            })
            .thenWaitUntil(() -> {
                helper.assertTrue("On with signal".equals(text(helper, JustDireCards.REDSTONE)), "select follows");
                helper.assertTrue("Off (no redstone signal)".equals(text(helper, JustDireCards.STATUS)), "status: " + text(helper, JustDireCards.STATUS));
            })
            .thenSucceed();
    }

    public static void machine(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "justdirethings:blockbreakert2"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "justdirethings_machine", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "justdirethings_machine", linked);
                helper.assertTrue(uplink(helper).getTitle().equals("Advanced Block Breaker"), "title: " + uplink(helper).getTitle());
                helper.assertTrue("Active".equals(text(helper, JustDireCards.STATUS)), "status: " + text(helper, JustDireCards.STATUS));
                helper.assertTrue(text(helper, JustDireCards.SPEED).startsWith("Every"), "speed: " + text(helper, JustDireCards.SPEED));
                helper.assertTrue(uplink(helper).getWidget(JustDireCards.ENERGY) != null, "advanced machines use energy");
            })
            .thenExecute(() -> {
                helper.assertTrue(act(helper, JustDireCards.REDSTONE, "One step per pulse") == null, "mode set");
                helper.assertTrue(be(helper).getRedstoneControlData().redstoneMode == MiscHelpers.RedstoneMode.PULSE, "machine mode PULSE");
            })
            .thenWaitUntil(() -> helper.assertTrue("Waits for a redstone pulse".equals(text(helper, JustDireCards.STATUS)), "status: " + text(helper, JustDireCards.STATUS)))
            .thenSucceed();
    }

    public static void sensor(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "justdirethings:sensort1"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "justdirethings_sensor", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "justdirethings_sensor", linked);
                helper.assertTrue(!text(helper, JustDireCards.TARGET).equals("–"), "target: " + text(helper, JustDireCards.TARGET));
                helper.assertTrue(value(helper, JustDireCards.SIGNAL).isJsonPrimitive(), "signal indicator");
                helper.assertTrue("not_interactive".equals(act(helper, JustDireCards.SIGNAL, true)), "read-only");
            })
            .thenSucceed();
    }
}
