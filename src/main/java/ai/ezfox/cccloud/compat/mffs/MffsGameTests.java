package ai.ezfox.cccloud.compat.mffs;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonPrimitive;
import dev.su5ed.mffs.blockentity.ProjectorBlockEntity;
import dev.su5ed.mffs.setup.ModBlocks;
import dev.su5ed.mffs.setup.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * MFFS: a projector with a cube mode, one scale module and full Fortron. The dashboard toggle is PIN-protected: without
 * a verified PIN the action is refused and the field stays off; with it the field comes up (force field blocks
 * appear); switching it off removes them. The web layer refuses the action before this point for read-only devices
 * (canControl) and asks for the PIN because the published widget has {@code opts.pin} (checked here through the hub).
 */
public final class MffsGameTests {
    private MffsGameTests() {
    }

    public static void projector(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "mffs:projector"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenExecute(() -> {
                var be = be(helper);
                be.projectorModeSlot.setItem(new ItemStack(ModItems.CUBE_MODE.get()));
                be.upgradeSlots.get(0).setItem(new ItemStack(ModItems.SCALE_MODULE.get()));
                be.fortronStorage.setStoredFortron(be.fortronStorage.getFortronCapacity());
                be.setChanged();
                helper.onEachTick(() -> {
                    var p = be(helper);
                    if (p != null) p.fortronStorage.setStoredFortron(p.fortronStorage.getFortronCapacity());
                });
            })
            .thenWaitUntil(() -> assertCard(helper, "mffs_projector", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "mffs_projector", linked);
                helper.assertTrue(text(helper, MffsCards.STATUS).equals("Off"), "status: " + text(helper, MffsCards.STATUS));
                var uplinkId = uplink(helper).getUplinkId().toString();
                helper.assertTrue(WebHub.INSTANCE.widgetNeedsPin(linked.mainframe().getMainframeId(), uplinkId, MffsCards.FIELD),
                    "the published toggle asks the browser for the PIN");
            })
            .thenExecute(() -> {
                helper.assertTrue("pin_required".equals(act(helper, MffsCards.FIELD, new JsonPrimitive(true), false)), "no PIN, no field");
                helper.assertTrue(!be(helper).isActive(), "still off");
                helper.assertTrue(act(helper, MffsCards.FIELD, new JsonPrimitive(true), true) == null, "with the PIN the field goes on");
                helper.assertTrue(be(helper).isActive(), "projector active");
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(fieldBlock(helper), "force field blocks are projected");
                helper.assertTrue(text(helper, MffsCards.STATUS).equals("Projecting"), "status: " + text(helper, MffsCards.STATUS));
                helper.assertTrue(bool(helper, MffsCards.FIELD), "toggle shows on");
            })
            .thenExecute(() -> {
                helper.assertTrue("pin_required".equals(act(helper, MffsCards.FIELD, new JsonPrimitive(false), false)), "switching off needs the PIN too");
                helper.assertTrue(act(helper, MffsCards.FIELD, new JsonPrimitive(false), true) == null, "switch off");
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(!fieldBlock(helper), "field removed");
                helper.assertTrue(text(helper, MffsCards.STATUS).equals("Off"), "status off");
            })
            .thenSucceed();
    }

    private static ProjectorBlockEntity be(GameTestHelper helper) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE)) instanceof ProjectorBlockEntity p ? p : null;
    }

    /** A cube of scale 1 around the projector: (1,1,1) relative to it is part of the shell. */
    private static boolean fieldBlock(GameTestHelper helper) {
        var p = helper.absolutePos(DEVICE).offset(new BlockPos(-1, 1, -1));
        return helper.getLevel().getBlockState(p).is(ModBlocks.FORCE_FIELD.get());
    }
}
