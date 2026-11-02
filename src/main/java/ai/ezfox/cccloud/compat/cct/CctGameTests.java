package ai.ezfox.cccloud.compat.cct;

import ai.ezfox.cccloud.bridge.BridgeHost;
import ai.ezfox.cccloud.cable.CcWired;
import ai.ezfox.cccloud.compat.cct.peripheral.BridgePeripheral;
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemFullBlockEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * GameTests and test helpers that need CC: Tweaked's classes. Only reached when CC: Tweaked is installed: the tests are
 * registered by {@link CctCompat}, the helpers are called behind {@code Cct.present()}.
 */
public final class CctGameTests {
    private CctGameTests() {
    }

    /** The ROM files shipped through our datapack are visible in CC's ROM mount (require("cloud.ui"), help, programs). */
    public static void romContents(GameTestHelper helper) {
        var mount = dan200.computercraft.api.ComputerCraftAPI.createResourceMount(helper.getLevel().getServer(), "computercraft", "lua/rom");
        helper.assertTrue(mount != null, "CC ROM mount missing");
        try {
            for (var path : new String[]{"modules/main/cloud/ui.lua", "help/cloud.txt", "programs/cloud-demo.lua", "programs/cloud-storage.lua",
                "modules/main/cc/expect.lua"}) {
                helper.assertTrue(mount.exists(path), "ROM is missing " + path);
            }
            helper.assertTrue(mount.getSize("modules/main/cloud/ui.lua") > 1000, "cloud/ui.lua is empty");
        } catch (java.io.IOException e) {
            throw helper.assertionException("Could not read the ROM mount: " + e);
        }
        helper.succeed();
    }

    /** Switches the peripheral sharing of a full wired modem (a right-click on it), at a position relative to the test. */
    public static void toggleModem(GameTestHelper helper, BlockPos modem, Player player) {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(modem)) instanceof WiredModemFullBlockEntity full)) {
            throw helper.assertionException("no wired modem");
        }
        full.use(player);
    }

    /** What Lua's {@code bridge.getItem(name)} answers for a bridge, or null (the bridge has no peripheral). */
    public static @Nullable Map<String, Object> bridgeItem(BridgeHost host, String name) {
        CcWired wired = host.cloudWired();
        return wired instanceof CloudWired w && w.peripheral() instanceof BridgePeripheral bridge ? bridge.getItem(name) : null;
    }
}
