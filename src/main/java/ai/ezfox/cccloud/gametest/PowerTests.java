package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.power.PowerState;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Mainframe power: FE goes in through the energy capability (also through the legacy IEnergyStorage wrapper), the
 * mainframe runs; when the buffer is drained it keeps going for the 5 s grace period and then goes offline with
 * reason no_power; refilled, it reconnects on its own.
 */
public final class PowerTests {
    private PowerTests() {
    }

    @SuppressWarnings({"deprecation", "removal"})
    public static void power(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        var level = helper.getLevel();
        long[] cutAt = {-1};

        helper.startSequence()
            .thenExecute(() -> {
                // Modern cable: EnergyHandler on the upper half, from the side.
                var upper = level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(CloudGameTests.UPPER), Direction.NORTH);
                helper.assertTrue(upper != null, "energy capability on the upper half");
                try (var tx = Transaction.openRoot()) {
                    int inserted = upper.insert(1_000_000, tx);
                    helper.assertTrue(inserted == 2_000, "insert is limited to 2000 FE per tick, got " + inserted);
                    tx.commit();
                }
                try (var tx = Transaction.openRoot()) {
                    helper.assertTrue(upper.insert(500, tx) == 0, "the per-tick limit also applies to a second call");
                }
                try (var tx = Transaction.openRoot()) {
                    helper.assertTrue(upper.extract(100, tx) == 0, "receive-only");
                }
            })
            .thenIdle(1)
            .thenExecute(() -> {
                // Legacy cable code: IEnergyStorage wrapper around the capability, on the lower half from below.
                var lower = level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(CloudGameTests.LOWER), Direction.DOWN);
                var legacy = net.neoforged.neoforge.energy.IEnergyStorage.of(lower);
                // canExtract() is only NeoForge's best guess (capacity > 0); what counts is that nothing comes out.
                helper.assertTrue(legacy.canReceive() && legacy.extractEnergy(100, false) == 0, "legacy view: receive-only");
                int simulated = legacy.receiveEnergy(1500, true);
                int received = legacy.receiveEnergy(1500, false);
                helper.assertTrue(simulated == 1500 && received == 1500, "legacy IEnergyStorage feeds the buffer: " + simulated + "/" + received);
                // Fill most of the buffer directly for the rest of the test.
                mainframe.energy().set(10_000);
                mainframe.setEnabled(true);
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(mainframe.hasPower() && mainframe.isOnline(), "powered mainframe is online");
                helper.assertTrue(mainframe.energyUsage() >= 40, "base usage " + mainframe.energyUsage());
                helper.assertTrue(mainframe.reason() == null, "no offline reason");
            })
            .thenExecute(() -> mainframe.energy().set(0))
            .thenIdle(PowerState.GRACE_TICKS / 2)
            .thenExecute(() -> helper.assertTrue(mainframe.hasPower() && mainframe.isOnline(), "brownout: still running within the 5 s grace"))
            .thenWaitUntil(() -> {
                helper.assertTrue(!mainframe.hasPower(), "out of power after the grace period");
                helper.assertTrue(!mainframe.isOnline(), "offline without power");
                helper.assertTrue("no_power".equals(mainframe.reason()), "reason no_power, got " + mainframe.reason());
                helper.assertTrue(mainframe.status() == CloudStatus.OFFLINE, "status offline");
                helper.assertTrue(info(mainframe) != null && !info(mainframe).get("ok").getAsBoolean(), "hub power.ok=false");
                cutAt[0] = level.getGameTime();
            })
            .thenExecute(() -> mainframe.energy().set(10_000))
            .thenWaitUntil(() -> {
                helper.assertTrue(mainframe.hasPower() && mainframe.isOnline(), "reconnects once refilled");
                helper.assertTrue(info(mainframe) != null && info(mainframe).get("ok").getAsBoolean(), "hub power.ok=true");
            })
            .thenSucceed();
    }

    private static com.google.gson.JsonObject info(CloudMainframeBlockEntity mainframe) {
        return ai.ezfox.cccloud.web.WebHub.INSTANCE.powerForTest(mainframe.getMainframeId());
    }
}
