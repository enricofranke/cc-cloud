package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.command.InternetCommand;
import ai.ezfox.cccloud.ingame.InGameTestSupport;
import ai.ezfox.cccloud.web.WebAuthData;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

/**
 * {@code /cccloud internet}: switching needs the server owner (op level 4), plain HTTP works without any PIN, the mode
 * (and {@code internetRequirePin}) are written to the server config and applied live. Runs in its own batch
 * ({@code cccloud:internet}): it writes the config and restores it at the end.
 */
public final class InternetTests {
    private InternetTests() {
    }

    public static void modeSwitch(GameTestHelper helper) {
        var setup = InGameTestSupport.mainframe(helper, "net_owner");
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        UUID mf = setup.id();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(mf) != null, "mainframe registered"))
            .thenExecute(() -> {
                String modeBefore = CloudConfig.get(CloudConfig.INTERNET_ACCESS);
                String domainBefore = CloudConfig.get(CloudConfig.INTERNET_DOMAIN);
                boolean pinBefore = CloudConfig.get(CloudConfig.INTERNET_REQUIRE_PIN);
                try {
                    var owner = setup.owner().createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);
                    var admin = setup.owner().createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);
                    helper.assertTrue(!WebAuthData.get(server).hasPin(mf), "the new mainframe has no PIN");

                    // only the owner level may switch; the status is for admins
                    helper.assertTrue(run(dispatcher, "cccloud internet http", admin) < 0, "op level 2 cannot switch");
                    helper.assertTrue(run(dispatcher, "cccloud internet", admin) == 1, "op level 2 sees the status");
                    helper.assertTrue(run(dispatcher, "cccloud internet http not a domain!", owner) < 0, "a bad domain is refused");

                    // no PIN needed anywhere: the mode switches on and applies live
                    helper.assertTrue(run(dispatcher, "cccloud internet http mybase.example.org", owner) == 1, "switched without any PIN");
                    helper.assertTrue(CloudConfig.get(CloudConfig.INTERNET_ACCESS).equals("http"), "written: " + CloudConfig.get(CloudConfig.INTERNET_ACCESS));
                    helper.assertTrue(CloudConfig.get(CloudConfig.INTERNET_DOMAIN).equals("mybase.example.org"), "domain written");
                    helper.assertTrue(ai.ezfox.cccloud.web.CloudWebServer.internetMode() == ai.ezfox.cccloud.web.internet.InternetMode.HTTP,
                        "applied live: " + ai.ezfox.cccloud.web.CloudWebServer.internetMode());
                    helper.assertTrue(ai.ezfox.cccloud.web.CloudWebServer.allowsInternet(), "the internet may connect");
                    helper.assertTrue(run(dispatcher, "cccloud internet status", admin) == 1, "status");

                    // strict mode is a config switch and applies live too
                    CloudConfig.INTERNET_REQUIRE_PIN.set(true);
                    CloudConfig.SPEC.save();
                    ai.ezfox.cccloud.config.ConfigReload.apply(server, "test");
                    helper.assertTrue(ai.ezfox.cccloud.web.CloudWebServer.requiresPin(), "internetRequirePin applied live");
                    helper.assertTrue(InternetCommand.ownersWithoutPin(server).stream().anyMatch(o -> o.startsWith("net_owner")),
                        "the owner is listed: " + InternetCommand.ownersWithoutPin(server));

                    helper.assertTrue(run(dispatcher, "cccloud internet off", owner) == 1, "switched off");
                    helper.assertTrue(CloudConfig.get(CloudConfig.INTERNET_ACCESS).equals("off"), "off written");
                    helper.assertTrue(ai.ezfox.cccloud.web.CloudWebServer.internetMode() == ai.ezfox.cccloud.web.internet.InternetMode.OFF, "off live");
                    helper.assertTrue(CloudConfig.get(CloudConfig.INTERNET_DOMAIN).equals("mybase.example.org"), "the domain is kept");
                } finally {
                    CloudConfig.INTERNET_ACCESS.set(modeBefore);
                    CloudConfig.INTERNET_DOMAIN.set(domainBefore);
                    CloudConfig.INTERNET_REQUIRE_PIN.set(pinBefore);
                    CloudConfig.SPEC.save();
                    ai.ezfox.cccloud.config.ConfigReload.apply(server, "test");
                }
            })
            .thenSucceed();
    }

    /** The command's result, or -1 when it was refused by Brigadier (unknown, not allowed, bad argument). */
    private static int run(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, String command,
                           net.minecraft.commands.CommandSourceStack source) {
        try {
            return dispatcher.execute(command, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return -1;
        }
    }
}
