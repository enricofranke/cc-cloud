package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.AccessResolver;
import ai.ezfox.cccloud.access.CloudPermissions;
import ai.ezfox.cccloud.access.MainframeAccess;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.access.ScoreboardTeams;
import ai.ezfox.cccloud.access.ServerPermissions;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.WebAuthData;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.handler.IPermissionHandler;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

/** v0.6 roles in a running server: permission nodes (with and without a permission mod), scoreboard teams, migration. */
public final class AccessTests {
    private AccessTests() {
    }

    /**
     * Nodes are registered with the PermissionAPI. Without a permission mod (NeoForge's default handler) the admin
     * nodes follow the op level; with a permission mod (a handler installed the way LuckPerms/FTB Ranks do) they follow
     * the mod, independent of op.
     */
    public static void permissionNodes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        var id = mainframe.getMainframeId();
        var names = PermissionAPI.getRegisteredNodes().stream().map(PermissionNode::getNodeName).toList();
        for (var node : ServerPermissions.Node.values()) helper.assertTrue(names.contains(node.fullName()), "registered: " + node.fullName());
        helper.assertTrue(names.contains("cccloud.limit.mainframes"), "integer limit node registered");

        // Default handler: op level 2 grants the admin nodes.
        var resolver = Access.resolver(server);
        helper.assertTrue(resolver.role(stranger.getUUID(), id) == Role.NONE, "non-op stranger has no role");
        helper.assertTrue(CloudPermissions.of(server).mainframeLimit(stranger.getUUID()) == 3, "default limit 3");
        var profile = new NameAndId(stranger.getUUID(), stranger.getGameProfile().name());
        // Level 2 explicitly (the GameTest server's default operator level is 0).
        server.getPlayerList().op(profile, java.util.Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER), java.util.Optional.empty());
        try {
            var r = resolver.resolve(stranger.getUUID(), id);
            helper.assertTrue(r.role() == Role.ADMIN && r.source() == AccessResolver.Source.SERVER_ADMIN, "ops are admins via cccloud.admin.manage: " + r
                + " isOp=" + server.getPlayerList().isOp(profile) + " perms=" + server.getProfilePermissions(profile)
                + " manage=" + CloudPermissions.of(server).has(stranger.getUUID(), ServerPermissions.Node.ADMIN_MANAGE)
                + " use=" + CloudPermissions.of(server).has(stranger.getUUID(), ServerPermissions.Node.USE)
                + " banned=" + CloudPermissions.of(server).banned(stranger.getUUID()) + " handler=" + PermissionAPI.getActivePermissionHandler());
        } finally {
            server.getPlayerList().deop(profile);
        }
        helper.assertTrue(resolver.role(stranger.getUUID(), id) == Role.NONE, "de-op applies at once");

        // A permission mod: only cccloud.admin.view for the stranger, cccloud.use denied for the owner.
        IPermissionHandler previous = handler();
        IPermissionHandler fake = new IPermissionHandler() {
            @Override
            public Identifier getIdentifier() {
                return Identifier.fromNamespaceAndPath("cccloud", "test_permissions");
            }

            @Override
            public Set<PermissionNode<?>> getRegisteredNodes() {
                return previous.getRegisteredNodes();
            }

            @Override
            public <T> T getPermission(ServerPlayer player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
                return getOfflinePermission(player.getUUID(), node, context);
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> T getOfflinePermission(UUID player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
                if (node.getNodeName().equals("cccloud.admin.view") && player.equals(stranger.getUUID())) return (T) Boolean.TRUE;
                if (node.getNodeName().equals("cccloud.use") && player.equals(owner.getUUID())) return (T) Boolean.FALSE;
                if (node.getNodeName().equals("cccloud.limit.mainframes")) return (T) Integer.valueOf(7);
                return node.getDefaultResolver().resolve(null, player, context);
            }
        };
        setHandler(fake);
        try {
            var r = resolver.resolve(stranger.getUUID(), id);
            helper.assertTrue(r.role() == Role.VIEWER && r.source() == AccessResolver.Source.SERVER_ADMIN, "admin.view from the permission mod, without op: " + r);
            helper.assertTrue(resolver.role(owner.getUUID(), id) == Role.NONE, "cccloud.use=false removes even the owner's role");
            helper.assertTrue(CloudPermissions.of(server).mainframeLimit(stranger.getUUID()) == 7, "integer node from the permission mod");
        } finally {
            setHandler(previous);
        }
        helper.assertTrue(resolver.role(owner.getUUID(), id) == Role.OWNER, "back to the default handler");
        helper.succeed();
    }

    private static IPermissionHandler handler() {
        try {
            var field = PermissionAPI.class.getDeclaredField("activeHandler");
            field.setAccessible(true);
            return (IPermissionHandler) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setHandler(IPermissionHandler handler) {
        try {
            var field = PermissionAPI.class.getDeclaredField("activeHandler");
            field.setAccessible(true);
            field.set(null, handler);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Vanilla scoreboard team assigned to a mainframe: joining/leaving the team changes the role live. */
    public static void scoreboardTeam(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var member = helper.makeMockPlayer(GameType.SURVIVAL);
        String name = member.getGameProfile().name();
        server.services().nameToIdCache().add(new NameAndId(member.getUUID(), name));
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        var id = mainframe.getMainframeId();
        var scoreboard = server.getScoreboard();
        String teamName = "cccloud_t" + (helper.absolutePos(net.minecraft.core.BlockPos.ZERO).hashCode() & 0xffff);
        var team = scoreboard.getPlayerTeam(teamName) != null ? scoreboard.getPlayerTeam(teamName) : scoreboard.addPlayerTeam(teamName);
        var store = WebAuthData.access(server);
        var provider = new ScoreboardTeams();
        store.setTeam(id, new MainframeAccess.TeamBinding(ScoreboardTeams.ID, teamName, provider.defaultMapping()));
        var resolver = Access.resolver(server);
        try {
            helper.assertTrue(resolver.role(member.getUUID(), id) == Role.NONE, "not in the team yet");
            scoreboard.addPlayerToTeam(name, team);
            var r = resolver.resolve(member.getUUID(), id);
            helper.assertTrue(r.role() == Role.OPERATOR && r.source() == AccessResolver.Source.TEAM, "team member → operator: " + r);
            store.setMapping(id, "member", Role.VIEWER);
            helper.assertTrue(resolver.role(member.getUUID(), id) == Role.VIEWER, "mapping change");
            scoreboard.removePlayerFromTeam(name, team);
            helper.assertTrue(resolver.role(member.getUUID(), id) == Role.NONE, "left the team");
        } finally {
            scoreboard.removePlayerTeam(team);
        }
        helper.succeed();
    }

    /**
     * v0.3 saved data (owner + trusted ACL, device tokens without the v0.6 fields) loads into v0.6: trusted players are
     * admins, the device keeps working, limited to its mainframe.
     */
    public static void migrationV03(GameTestHelper helper) {
        var mf = UUID.randomUUID();
        var other = UUID.randomUUID();
        var owner = UUID.randomUUID();
        var trusted = UUID.randomUUID();
        String token = "legacy-token-for-the-migration-test";
        String v03 = """
            {"pairing_codes":{},
             "acls":{"%s":{"owner":"%s","trusted":["%s"]}},
             "devices":[{"hash":"%s","mainframe":"%s","name":"Phone","can_control":true,"created":1,"last_used":%d,"player":"%s","player_name":"Sam"}],
             "pins":{}}
            """.formatted(mf, owner, trusted, AuthStore.hash(token), mf, System.currentTimeMillis(), trusted);
        var decoded = WebAuthData.TYPE.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(v03)).getOrThrow();
        var access = decoded.accessStore();
        var entry = access.access(mf);
        helper.assertTrue(entry != null && owner.equals(entry.owner()), "owner migrated");
        helper.assertTrue(entry.grants().get(trusted).role() == Role.ADMIN, "trusted → admin");
        var device = decoded.authStore().authenticate(token);
        helper.assertTrue(device != null && mf.equals(device.mainframeId()) && !device.isAccount(), "v0.3 token still valid, limited to its mainframe");
        access.setOwner(other, trusted, "Sam");
        var resolver = new AccessResolver(access, CloudPermissions.of(helper.getLevel().getServer()), ai.ezfox.cccloud.access.TeamProviders::get);
        helper.assertTrue(resolver.deviceRole(device, mf) == Role.ADMIN, "device role = the player's role");
        helper.assertTrue(resolver.deviceRole(device, other) == Role.NONE, "not on the player's other mainframe");
        // Saved again, the v0.3 ACL is gone and the v0.6 data round-trips.
        var saved = WebAuthData.TYPE.codec().encodeStart(JsonOps.INSTANCE, decoded).getOrThrow().getAsJsonObject();
        helper.assertTrue(!saved.has("acls") || saved.getAsJsonObject("acls").isEmpty(), "old ACL not written again");
        var again = WebAuthData.TYPE.codec().parse(JsonOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(again.accessStore().access(mf).grants().get(trusted).role() == Role.ADMIN, "round trip");
        helper.assertTrue(again.authStore().authenticate(token) != null, "device round trip");
        helper.succeed();
    }
}
