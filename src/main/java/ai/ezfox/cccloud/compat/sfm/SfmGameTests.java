package ai.ezfox.cccloud.compat.sfm;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Super Factory Manager: a manager without a disk gets the Factory Manager card ("No disk"); the program on its disk. */
public final class SfmGameTests {
    private SfmGameTests() {
    }

    public static void manager(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "sfm:manager"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "sfm_manager", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "sfm_manager", linked);
                helper.assertTrue(text(helper, SfmCards.STATE).equals("No disk"), "state: " + text(helper, SfmCards.STATE));
                helper.assertTrue(text(helper, SfmCards.ERRORS).equals("0"), "errors");
            })
            .thenSucceed();
    }

    /** The on-demand program: compiled before saving, a broken one only on confirm, undo once, and nothing without a disk. */
    public static void program(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "sfm:manager"));
        var level = helper.getLevel();
        var pos = helper.absolutePos(DEVICE);
        var be = (ca.teamdman.sfm.common.blockentity.ManagerBlockEntity) level.getBlockEntity(pos);
        var card = new SfmCards.Manager(level, pos, Direction.NORTH, be);
        helper.assertTrue(!card.detail().get("disk").getAsBoolean(), "no disk yet");
        helper.assertTrue("no_disk".equals(card.write("save", "x", false).error()), "needs a disk");
        be.setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("sfm:disk"))));
        String good = "NAME \"one\"\nEVERY 20 TICKS DO\n    INPUT FROM a\n    OUTPUT TO b\nEND\n";
        String good2 = "NAME \"two\"\nEVERY 40 TICKS DO\n    INPUT FROM a\n    OUTPUT TO b\nEND\n";
        String broken = "EVERY DO";

        var checked = card.write("check", broken, false);
        helper.assertTrue(checked.error() == null && !checked.data().getAsJsonArray("errors").isEmpty(), "check names the errors");
        helper.assertTrue(card.detail().get("program").getAsString().isEmpty(), "a check writes nothing");

        var saved = card.write("save", good, false);
        helper.assertTrue(saved.error() == null, "saved: " + saved.error());
        helper.assertTrue(saved.data().get("program").getAsString().strip().equals(good.strip()) && saved.data().getAsJsonArray("errors").isEmpty(), "program on the disk: " + saved.data());
        helper.assertTrue(saved.data().get("state").getAsString().equals("Running") && saved.data().get("name").getAsString().equals("one"), "runs: " + saved.data());

        helper.assertTrue(card.write("save", good2, false).error() == null, "second save");
        var refused = card.write("save", broken, false);
        helper.assertTrue("compile_failed".equals(refused.error()) && !refused.data().getAsJsonArray("errors").isEmpty(), "broken refused: " + refused.error());
        helper.assertTrue(card.detail().get("program").getAsString().strip().equals(good2.strip()), "the working program stayed");

        var forced = card.write("save", broken, true);
        helper.assertTrue(forced.error() == null && forced.data().get("state").getAsString().equals("Program has errors"), "written on confirm: " + forced.data());
        var undone = card.write("undo", null, false);
        helper.assertTrue(undone.error() == null && undone.data().get("program").getAsString().strip().equals(good2.strip()) && !undone.data().get("undo").getAsBoolean(), "undo: " + undone.data());
        helper.assertTrue("nothing_to_undo".equals(card.write("undo", null, false).error()), "undo works once");
        helper.succeed();
    }
}
