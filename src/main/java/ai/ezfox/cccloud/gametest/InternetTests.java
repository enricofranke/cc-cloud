package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.command.InternetCommand;
import ai.ezfox.cccloud.ingame.InGameTestSupport;
import ai.ezfox.cccloud.web.WebAuthData;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

/**
 * {@code /cccloud internet}: switching needs the server owner (op level 4), plain HTTP is refused while a mainframe has
 * no PIN, and the mode is written to the server config and applied live. Runs in its own batch ({@code cccloud:internet}): it writes the
 * config and gives PIN-less mainframes a PIN for a moment; both are restored at the end.
 */
public final class InternetTests {
    private InternetTests() {
    }

    public static void modeSwitch(GameTestHelper helper) {
        var setup = InGameTestSupport.mainframe(helper, "net_owner");
        var server = helper.getLevel().getServer();
        var auth = WebAuthData.get(server);
        var dispatcher = server.getCommands().getDispatcher();
        UUID mf = setup.id();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(mf) != null, "mainframe registered"))
            .thenExecute(() -> {
                String modeBefore = CloudConfig.get(CloudConfig.INTERNET_ACCESS);
                String domainBefore = CloudConfig.get(CloudConfig.INTERNET_DOMAIN);
                var pinned = new ArrayList<UUID>();
                try {
                    var owner = setup.owner().createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);
                    var admin = setup.owner().createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);
                    helper.assertTrue(InternetCommand.mainframesWithoutPin(server).stream().anyMatch(s -> s.startsWith(setup.mainframe().getDisplayName())),
                        "the new mainframe is listed without a PIN: " + InternetCommand.mainframesWithoutPin(server));
                    helper.assertTrue(run(dispatcher, "cccloud internet http", owner) == 0, "refused without a PIN");
                    helper.assertTrue(CloudConfig.get(CloudConfig.INTERNET_ACCESS).equals(modeBefore), "nothing written when refused");

                    // only the owner level may switch; the status is for admins
                    helper.assertTrue(run(dispatcher, "cccloud internet http", admin) < 0, "op level 2 cannot switch");
                    helper.assertTrue(run(dispatcher, "cccloud internet", admin) == 1, "op level 2 sees the status");

                    for (var id : WebAuthData.access(server).mainframes()) {
                        if (!auth.hasPin(id)) {
                            auth.setPin(id, auth.hashPin("4711"));
                            pinned.add(id);
                        }
                    }
                    helper.assertTrue(InternetCommand.mainframesWithoutPin(server).isEmpty(), "every mainframe has a PIN now");
                    helper.assertTrue(run(dispatcher, "cccloud internet http not a domain!", owner) < 0, "a bad domain is refused");
                    helper.assertTrue(run(dispatcher, "cccloud internet http mybase.example.org", owner) == 1, "switched");
                    helper.assertTrue(CloudConfig.get(CloudConfig.INTERNET_ACCESS).equals("http"), "written: " + CloudConfig.get(CloudConfig.INTERNET_ACCESS));
                    helper.assertTrue(CloudConfig.get(CloudConfig.INTERNET_DOMAIN).equals("mybase.example.org"), "domain written");
                    helper.assertTrue(ai.ezfox.cccloud.web.CloudWebServer.internetMode() == ai.ezfox.cccloud.web.internet.InternetMode.HTTP,
                        "applied live: " + ai.ezfox.cccloud.web.CloudWebServer.internetMode());
                    helper.assertTrue(ai.ezfox.cccloud.web.CloudWebServer.allowsInternet(), "the internet may connect");
                    helper.assertTrue(run(dispatcher, "cccloud internet status", admin) == 1, "status");
                    helper.assertTrue(run(dispatcher, "cccloud internet off", owner) == 1, "switched off");
                    helper.assertTrue(CloudConfig.get(CloudConfig.INTERNET_ACCESS).equals("off"), "off written");
                    helper.assertTrue(ai.ezfox.cccloud.web.CloudWebServer.internetMode() == ai.ezfox.cccloud.web.internet.InternetMode.OFF, "off live");
                    helper.assertTrue(CloudConfig.get(CloudConfig.INTERNET_DOMAIN).equals("mybase.example.org"), "the domain is kept");
                } finally {
                    for (var id : pinned) auth.setPin(id, null);
                    CloudConfig.INTERNET_ACCESS.set(modeBefore);
                    CloudConfig.INTERNET_DOMAIN.set(domainBefore);
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
