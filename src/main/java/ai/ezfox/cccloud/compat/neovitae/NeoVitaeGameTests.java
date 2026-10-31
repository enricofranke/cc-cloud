package ai.ezfox.cccloud.compat.neovitae;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import com.breakinblocks.neovitae.common.blockentity.AraVitaeTile;
import com.breakinblocks.neovitae.util.AnimaTicket;
import com.breakinblocks.neovitae.util.helper.AnimaHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/** Neo Vitae: a lone altar, master ritual stone and forge get their cards; the Anima card shows the owner's EV. */
public final class NeoVitaeGameTests {
    private NeoVitaeGameTests() {
    }

    public static void altar(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "neovitae:ara_vitae"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "neovitae_altar", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, "neovitae_altar", linked))
            .thenWaitUntil(() -> helper.assertTrue(text(helper, NeoVitaeCards.TIER).startsWith("Tier "), "tier"))
            .thenExecute(() -> {
                var altar = (AraVitaeTile) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                helper.assertTrue(altar.getCapacity() > 0, "altar has a capacity");
                helper.assertTrue("Idle".equals(text(helper, NeoVitaeCards.STATUS)), "idle: " + text(helper, NeoVitaeCards.STATUS));
                helper.assertTrue(number(helper, NeoVitaeCards.BLOOD) == 0, "empty");
                helper.assertTrue(text(helper, NeoVitaeCards.BLOOD_TEXT).endsWith(" mB"), "stored text");
            })
            .thenSucceed();
    }

    public static void ritual(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "neovitae:master_ritual_stone"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "neovitae_ritual", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, "neovitae_ritual", linked))
            .thenWaitUntil(() -> helper.assertTrue("None".equals(text(helper, NeoVitaeCards.RITUAL)), "no ritual"))
            .thenExecute(() -> helper.assertTrue("Stopped".equals(text(helper, NeoVitaeCards.STATUS)), "stopped"))
            .thenSucceed();
    }

    public static void forge(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "neovitae:hellfire_forge"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "neovitae_forge", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, "neovitae_forge", linked))
            .thenWaitUntil(() -> helper.assertTrue("Idle".equals(text(helper, NeoVitaeCards.STATUS)), "idle"))
            .thenSucceed();
    }

    public static void anima(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        AnimaHelper.getAnima(owner.getUUID()).set(AnimaTicket.create(12_500).toApiTicket(), 12_500);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.startSequence()
            .thenWaitUntil(() -> {
                var section = MainframeCards.section(mainframe.getMainframeId(), AnimaCard.KIND);
                helper.assertTrue(section != null, "anima card");
                helper.assertTrue(section.snapshot().get("restricted").getAsBoolean(), "restricted section");
                var ev = section.values().get(AnimaCard.EV).getAsString();
                helper.assertTrue("12.5 k EV".equals(ev), "owner's EV: " + ev);
            })
            .thenSucceed();
    }
}
