package ai.ezfox.cccloud.compat.enderio;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import ai.ezfox.cccloud.compat.card.EnergyCard;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;

/** Ender IO: Stirling generator card with Ender IO's redstone control set from the dashboard. */
public final class EnderIoGameTests {
    private EnderIoGameTests() {
    }

    public static void stirling(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "enderio:stirling_generator"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "enderio_machine", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "enderio_machine", linked);
                helper.assertTrue(text(helper, "generation").equals("0 FE/t"), "idle generator: " + text(helper, "generation"));
                helper.assertTrue(!bool(helper, "active"), "not active without fuel");
                helper.assertTrue(text(helper, EnergyCard.REDSTONE).equals("Always active"), "redstone: " + text(helper, EnergyCard.REDSTONE));
            })
            .thenExecute(() -> {
                helper.assertTrue(act(helper, EnergyCard.REDSTONE, "Never active") == null, "set redstone control");
                var machine = (PoweredMachineBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                helper.assertTrue(machine.getRedstoneControl() == com.enderio.enderio.api.io.RedstoneControl.NEVER_ACTIVE, "Ender IO got the mode");
            })
            .thenSucceed();
    }
}
