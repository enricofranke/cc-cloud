package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.access.MainframeAccess;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.ingame.InGameTestSupport;
import ai.ezfox.cccloud.web.WebAuthData;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Security review H1: every in-game way of changing access (mainframe screen, commands) follows the same rank rule as
 * the web API – a viewer with a custom {@code access.manage} cannot make anyone, including themselves, more than a
 * viewer.
 */
public final class SecurityTests {
    private SecurityTests() {
    }

    public static void noEscalationInGame(GameTestHelper helper) {
        var setup = InGameTestSupport.mainframe(helper, "sec_owner");
        var vic = InGameTestSupport.player(helper, "sec_viewer");
        var fred = InGameTestSupport.player(helper, "sec_friend");
        var server = helper.getLevel().getServer();
        var store = WebAuthData.access(server);
        var mf = setup.id();
        InGameTestSupport.grant(helper, setup, vic, Role.VIEWER);
        InGameTestSupport.grant(helper, setup, fred, Role.VIEWER);
        store.setOverrides(mf, vic.getUUID(), new MainframeAccess.Overrides(Set.of(Perm.ACCESS_MANAGE), Set.of()));
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(mf) != null, "mainframe registered"))
            .thenExecute(() -> {
                var ops = ai.ezfox.cccloud.gui.GuiNetwork.opsForTest(vic, setup.mainframe());
                helper.assertTrue("no_permission".equals(ops.setRole(vic.getUUID(), Role.ADMIN)), "screen: not themselves");
                helper.assertTrue("no_permission".equals(ops.setRole(fred.getUUID(), Role.OPERATOR)), "screen: not above their role");
                helper.assertTrue("no_permission".equals(ops.untrust(setup.owner().getUUID())) || "not_trusted".equals(ops.untrust(setup.owner().getUUID())),
                    "screen: never the owner");
                // commands, run as the viewer
                // entity selectors (@s) need permission level 2; mainframe rights are checked on the player, not the source
                var source = vic.createCommandSourceStack().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER);
                for (String command : new String[]{"cccloud role @s[type=player] admin", "cccloud trust @s[type=player]"}) {
                    try {
                        server.getCommands().getDispatcher().execute(command, source);
                        throw helper.assertionException("command was allowed: " + command);
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        // refused (not allowed / already has a role)
                    }
                }
                var grant = store.access(mf).grants().get(vic.getUUID());
                helper.assertTrue(grant != null && grant.role() == Role.VIEWER, "commands: still a viewer: " + grant);
                helper.assertTrue(!ai.ezfox.cccloud.access.Access.resolver(server).resolve(vic.getUUID(), mf).perms().contains(Perm.TERMINAL), "no terminal");
                // control: the command path really runs for this player (leaving is allowed)
                try {
                    server.getCommands().getDispatcher().execute("cccloud role @s[type=player] none", source);
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    throw helper.assertionException("command failed: " + e.getMessage());
                }
                helper.assertTrue(store.access(mf).grants().get(vic.getUUID()) == null, "commands reach the rule (self-removal works)");
            })
            .thenSucceed();
    }
}
