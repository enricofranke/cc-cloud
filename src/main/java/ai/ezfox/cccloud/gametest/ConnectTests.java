package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.connect.ConnectService;
import ai.ezfox.cccloud.connect.ConnectStatus;
import ai.ezfox.cccloud.ingame.InGameTestSupport;
import ai.ezfox.cccloud.web.CloudWebServer;
import ai.ezfox.cccloud.web.internet.InternetMode;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

/**
 * {@code /cccloud internet connect}: only the server owner claims a name, bad names are refused before anything is
 * written, a claim writes {@code internetAccess = connect} and the name and applies it live (the dashboard server's
 * running mode becomes {@code connect}, the tunnel starts), and {@code /cccloud internet off} stops it. No network: the
 * relay is pointed at a closed local port, so the tunnel only ever retries. Same batch as {@code internet_mode_switch}
 * (it writes the config); everything is restored at the end.
 */
public final class ConnectTests {
    private ConnectTests() {
    }

    public static void modeSwitch(GameTestHelper helper) {
        var setup = InGameTestSupport.mainframe(helper, "connect_owner");
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(setup.id()) != null, "mainframe registered"))
            .thenExecute(() -> {
                String modeBefore = CloudConfig.get(CloudConfig.INTERNET_ACCESS);
                String nameBefore = CloudConfig.get(CloudConfig.CONNECT_NAME);
                String relayBefore = CloudConfig.get(CloudConfig.CONNECT_RELAY);
                try {
                    var owner = setup.owner().createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);
                    var admin = setup.owner().createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);
                    CloudConfig.CONNECT_RELAY.set("ws://127.0.0.1:1"); // nothing listens there: no traffic leaves the test
                    helper.assertTrue(run(dispatcher, "cccloud internet connect gametest-base", admin) < 0, "op level 2 cannot claim");
                    helper.assertTrue(run(dispatcher, "cccloud internet connect", admin) == 1, "op level 2 sees the status");
                    helper.assertTrue(run(dispatcher, "cccloud internet connect bad--name", owner) == 0, "a bad name is refused");
                    helper.assertTrue(run(dispatcher, "cccloud internet connect www", owner) == 0, "a reserved name is refused");
                    helper.assertTrue(CloudConfig.get(CloudConfig.INTERNET_ACCESS).equals(modeBefore), "nothing written when refused");

                    helper.assertTrue(run(dispatcher, "cccloud internet connect gametest-base", owner) == 1, "claimed");
                    helper.assertTrue(CloudConfig.get(CloudConfig.INTERNET_ACCESS).equals("connect"), "mode written: " + CloudConfig.get(CloudConfig.INTERNET_ACCESS));
                    helper.assertTrue(CloudConfig.get(CloudConfig.CONNECT_NAME).equals("gametest-base"), "name written");
                    helper.assertTrue(CloudWebServer.internetMode() == InternetMode.CONNECT, "applied live: " + CloudWebServer.internetMode());
                    helper.assertTrue(!CloudWebServer.allowsInternet(), "the dashboard port stays LAN only");
                    var state = ConnectService.status().state();
                    helper.assertTrue(state == ConnectStatus.State.CONNECTING || state == ConnectStatus.State.RETRYING, "the tunnel runs: " + ConnectService.status());
                    helper.assertTrue(run(dispatcher, "cccloud internet status", admin) == 1, "status");
                    helper.assertTrue(run(dispatcher, "cccloud internet off", owner) == 1, "switched off");
                    ConnectService.apply();
                    helper.assertTrue(ConnectService.status().state() == ConnectStatus.State.OFF, "the tunnel stopped: " + ConnectService.status());
                    helper.assertTrue(CloudConfig.get(CloudConfig.CONNECT_NAME).equals("gametest-base"), "off keeps the name");
                } finally {
                    CloudConfig.INTERNET_ACCESS.set(modeBefore);
                    CloudConfig.CONNECT_NAME.set(nameBefore);
                    CloudConfig.CONNECT_RELAY.set(relayBefore);
                    CloudConfig.SPEC.save();
                    ai.ezfox.cccloud.config.ConfigReload.apply(server, "test");
                    ConnectService.apply();
                }
            })
            .thenSucceed();
    }

    private static int run(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, String command,
                           net.minecraft.commands.CommandSourceStack source) {
        try {
            return dispatcher.execute(command, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return -1;
        }
    }
}
