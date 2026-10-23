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

    /** M3: a Link Card cannot pull a device away from another mainframe without network.link there. */
    public static void noLinkTakeover(GameTestHelper helper) {
        var victim = InGameTestSupport.mainframe(helper, "sec_victim");
        var attackerPlayer = InGameTestSupport.player(helper, "sec_attacker");
        var other = CloudGameTests.placeMainframe(helper, attackerPlayer, new net.minecraft.core.BlockPos(1, 0, 0));
        other.setOwner(attackerPlayer);
        CloudGameTests.keepPowered(helper, other);
        InGameTestSupport.placeUplink(helper);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(victim.id()) != null
                && ai.ezfox.cccloud.network.CloudNetwork.mainframe(other.getMainframeId()) != null, "both mainframes registered"))
            .thenExecute(() -> {
                var uplink = InGameTestSupport.uplink(helper);
                uplink.bind(victim.link(), victim.owner());
                helper.assertTrue(uplink.getLink() != null && uplink.getLink().id().equals(victim.id()), "the victim linked their uplink");
                var attackerLink = new ai.ezfox.cccloud.item.MainframeLink(net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), other.getBlockPos()),
                    other.getMainframeId());
                uplink.bind(attackerLink, attackerPlayer);
                helper.assertTrue(uplink.getLink().id().equals(victim.id()), "the attacker's Link Card does not take it over");
                // control: someone with network.link on both may move it
                InGameTestSupport.grant(helper, victim, attackerPlayer, Role.ADMIN);
                uplink.bind(attackerLink, attackerPlayer);
                helper.assertTrue(uplink.getLink().id().equals(other.getMainframeId()), "moved with rights on both");
            })
            .thenSucceed();
    }

    /** M5/L11: the mainframe screen shows node positions only with view.positions, each management part with its own right. */
    public static void screenShowsOnlyAllowedParts(GameTestHelper helper) {
        var setup = InGameTestSupport.mainframe(helper, "sec_screen_owner");
        var viewer = InGameTestSupport.player(helper, "sec_screen_viewer");
        var deviceAdmin = InGameTestSupport.player(helper, "sec_screen_devices");
        var store = WebAuthData.access(helper.getLevel().getServer());
        InGameTestSupport.grant(helper, setup, viewer, Role.VIEWER);
        InGameTestSupport.grant(helper, setup, deviceAdmin, Role.VIEWER);
        store.setOverrides(setup.id(), deviceAdmin.getUUID(), new MainframeAccess.Overrides(Set.of(Perm.DEVICES_MANAGE, Perm.VIEW_POSITIONS), Set.of()));
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.NetworkService.result(setup.id()) != null, "network computed"))
            .thenExecute(() -> {
                var v = com.google.gson.JsonParser.parseString(ai.ezfox.cccloud.gui.GuiViews.mainframe(setup.mainframe(), viewer, null)).getAsJsonObject();
                helper.assertTrue(!v.has("network"), "viewer: no node coordinates");
                helper.assertTrue(!v.has("devices") && !v.has("trusted") && !v.has("code") && !v.has("audit"), "viewer: no management");
                var d = com.google.gson.JsonParser.parseString(ai.ezfox.cccloud.gui.GuiViews.mainframe(setup.mainframe(), deviceAdmin, null)).getAsJsonObject();
                helper.assertTrue(d.has("network"), "with view.positions: the network");
                helper.assertTrue(d.has("devices") && d.has("code"), "devices.manage: devices and code");
                helper.assertTrue(!d.has("trusted") && !d.has("audit") && !d.has("pinSet"), "but not the access list, audit or PIN state");
                var o = com.google.gson.JsonParser.parseString(ai.ezfox.cccloud.gui.GuiViews.mainframe(setup.mainframe(), setup.owner(), null)).getAsJsonObject();
                helper.assertTrue(o.has("trusted") && o.has("audit") && o.has("pinSet") && o.has("network"), "the owner sees everything");
            })
            .thenSucceed();
    }
}
