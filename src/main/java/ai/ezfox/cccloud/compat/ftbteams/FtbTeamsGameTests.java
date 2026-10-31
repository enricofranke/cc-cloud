package ai.ezfox.cccloud.compat.ftbteams;

import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.team.TeamSource;
import ai.ezfox.cccloud.compat.team.Teams;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/**
 * FTB Teams roles (v0.6, AccessResolver): the owner creates a party (public API), a second player joins it (FTB's
 * party class, test only); the member then has the operator role (rank member → operator, live), a stranger has none,
 * the config switch turns the implicit owner's-team mapping off, and after leaving the party the role is gone again.
 */
public final class FtbTeamsGameTests {
    private FtbTeamsGameTests() {
    }

    public static void trust(GameTestHelper helper) {
        var owner = player(helper, "cccloud_owner");
        var member = player(helper, "cccloud_member");
        var stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        var changes = new AtomicInteger();
        Teams.onChange(id -> {
            if (id.equals(member.getUUID())) changes.incrementAndGet();
        });
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setOwner(owner);
        Team[] party = {null};
        helper.startSequence()
            .thenWaitUntil(() -> {
                var manager = FTBTeamsAPI.api().getManager();
                helper.assertTrue(manager.getPlayerTeamForPlayerID(owner.getUUID()).isPresent(), "FTB player team of the owner");
                helper.assertTrue(manager.getPlayerTeamForPlayerID(member.getUUID()).isPresent(), "FTB player team of the member");
            })
            .thenExecute(() -> {
                helper.assertTrue(role(helper, mainframe.getMainframeId(), member.getUUID()) == ai.ezfox.cccloud.access.Role.NONE, "no role before joining");
                try {
                    party[0] = FTBTeamsAPI.api().getManager().createPartyTeam(owner, "cccloud_test", null, null);
                    var partyTeam = (PartyTeam) party[0];
                    partyTeam.invite(owner, List.of(member.nameAndId()));
                    partyTeam.join(member);
                } catch (Exception e) {
                    throw helper.assertionException("could not build the party: " + e);
                }
            })
            .thenWaitUntil(() -> {
                var source = Teams.source();
                helper.assertTrue(source != null && "ftbteams".equals(source.id()), "FTB Teams is the team source");
                var team = source.teamOf(owner.getUUID());
                helper.assertTrue(team.isPresent() && team.get().id().equals(party[0].getId()), "owner's team is the party");
                helper.assertTrue(source.rank(party[0].getId(), owner.getUUID()) == TeamSource.Rank.OWNER, "owner rank");
                helper.assertTrue(source.rank(party[0].getId(), member.getUUID()) == TeamSource.Rank.MEMBER, "member rank");
                helper.assertTrue(source.members(party[0].getId()).contains(member.getUUID()), "members list");
                helper.assertTrue(changes.get() > 0, "the join was reported as a team change");
                helper.assertTrue(role(helper, mainframe.getMainframeId(), member.getUUID()) == ai.ezfox.cccloud.access.Role.OPERATOR,
                    "a party member operates the mainframe (rank member → operator)");
                helper.assertTrue(!mainframe.canManage(member), "members do not manage (admin is owner/officer)");
                helper.assertTrue(!mainframe.canManage(stranger), "strangers are still refused");
                helper.assertTrue(role(helper, mainframe.getMainframeId(), stranger.getUUID()) == ai.ezfox.cccloud.access.Role.NONE, "stranger role");
            })
            .thenExecute(() -> {
                IntegrationConfig.FTB_TEAMS_TRUST.set(false);
                try {
                    helper.assertTrue(role(helper, mainframe.getMainframeId(), member.getUUID()) == ai.ezfox.cccloud.access.Role.NONE,
                        "ftbTeamsTrust=false switches the owner's-team roles off");
                } finally {
                    IntegrationConfig.FTB_TEAMS_TRUST.set(true);
                }
            })
            .thenExecute(() -> {
                try {
                    ((PartyTeam) party[0]).leave(member.getUUID());
                } catch (Exception e) {
                    throw helper.assertionException("leave failed: " + e);
                }
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(role(helper, mainframe.getMainframeId(), member.getUUID()) == ai.ezfox.cccloud.access.Role.NONE,
                    "the role ends when the member leaves (live)");
            })
            .thenSucceed();
    }

    /**
     * Relays belong to a team, with FTB Teams (docs/SPEC.md "Relays belong to a team"): the owner's relay does not carry
     * a stranger's or a future party member's network; joining the party routes the member's uplink over it, an ally of
     * the party is carried too, and leaving ends it again – all through FTB's own events, without waiting.
     */
    public static void relay(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = player(helper, "cccloud_r_owner");
        var mate = player(helper, "cccloud_r_mate");
        var ally = player(helper, "cccloud_r_ally");
        var stranger = player(helper, "cccloud_r_stranger");
        var mainframe = CloudGameTests.placeMainframe(helper, mate, new BlockPos(2, 0, 2));
        mainframe.setOwner(mate);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var link = new ai.ezfox.cccloud.item.MainframeLink(net.minecraft.core.GlobalPos.of(level.dimension(), mainframe.getBlockPos()), mainframe.getMainframeId());
        var relayPos = mainframe.getBlockPos().above(80);
        var upPos = mainframe.getBlockPos().above(120);
        level.setBlock(relayPos, ai.ezfox.cccloud.ModRegistry.CLOUD_RELAY.get().defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        var relay = (ai.ezfox.cccloud.relay.CloudRelayBlockEntity) level.getBlockEntity(relayPos);
        relay.setOwner(owner.getUUID(), owner.getGameProfile().name()); // fake players place nothing with an owner
        relay.fillForTest();
        relay.restrictForTest(mainframe.getMainframeId());
        level.setBlock(upPos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(upPos, ai.ezfox.cccloud.ModRegistry.CLOUD_UPLINK.get().defaultBlockState()
            .setValue(ai.ezfox.cccloud.block.CloudUplinkBlock.FACING, net.minecraft.core.Direction.DOWN), net.minecraft.world.level.block.Block.UPDATE_ALL);
        Team[] party = {null};
        helper.startSequence()
            .thenIdle(3) // the relay and the uplink register on their first tick
            .thenWaitUntil(() -> helper.assertTrue(FTBTeamsAPI.api().getManager().getPlayerTeamForPlayerID(mate.getUUID()).isPresent(), "FTB knows the players"))
            .thenExecute(() -> {
                helper.assertTrue(!relay.carries(mate.getUUID()) && !relay.carries(ally.getUUID()) && !relay.carries(stranger.getUUID()),
                    "nobody else's network before the party");
                uplink(helper, upPos).bind(link, mate);
                helper.assertTrue(uplink(helper, upPos).getLink() == null, "the mate cannot link through the owner's relay yet");
                try {
                    party[0] = FTBTeamsAPI.api().getManager().createPartyTeam(owner, "cccloud_relay", null, null);
                    ((PartyTeam) party[0]).invite(owner, List.of(mate.nameAndId()));
                    ((PartyTeam) party[0]).join(mate);
                    ((PartyTeam) party[0]).addAlly(level.getServer().createCommandSourceStack(), List.of(ally.nameAndId()));
                } catch (Exception e) {
                    throw helper.assertionException("could not build the party: " + e);
                }
            })
            .thenExecute(() -> {
                // FTB's join/ally events cleared the cached answers: no manual refresh here.
                helper.assertTrue(relay.carries(mate.getUUID()), "a party member's network is carried");
                helper.assertTrue(relay.carries(ally.getUUID()), "an ally of the party is carried");
                helper.assertTrue(!relay.carries(stranger.getUUID()), "a stranger's is not");
                uplink(helper, upPos).bind(link, mate);
                helper.assertTrue(uplink(helper, upPos).getLink() != null, "the member links through the relay: "
                    + ai.ezfox.cccloud.network.NetworkService.checkLink(mainframe, level, upPos, ai.ezfox.cccloud.network.graph.NetworkGraph.DeviceType.UPLINK));
            })
            .thenWaitUntil(() -> {
                var route = ai.ezfox.cccloud.network.NetworkService.route(uplink(helper, upPos).getUplinkId());
                helper.assertTrue(route != null && route.connected() && route.path().contains(relay.relayId().toString()), "routed over the relay: " + route);
            })
            .thenExecute(() -> {
                try {
                    ((PartyTeam) party[0]).leave(mate.getUUID());
                } catch (Exception e) {
                    throw helper.assertionException("leave failed: " + e);
                }
            })
            .thenWaitUntil(() -> {
                var route = ai.ezfox.cccloud.network.NetworkService.route(uplink(helper, upPos).getUplinkId());
                helper.assertTrue(!relay.carries(mate.getUUID()), "left the party: not carried any more");
                helper.assertTrue(route != null && !route.connected(), "and the route over the relay is gone: " + route);
            })
            .thenExecute(() -> {
                for (var pos : List.of(relayPos, upPos, upPos.below())) {
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                }
            })
            .thenSucceed();
    }

    private static ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity uplink(GameTestHelper helper, BlockPos pos) {
        if (!(helper.getLevel().getBlockEntity(pos) instanceof ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity uplink)) {
            throw helper.assertionException("uplink missing at " + pos);
        }
        return uplink;
    }

    /**
     * A server-side player FTB Teams knows: a NeoForge fake player, announced to FTB's team manager the way a login
     * would (FTB internals, test only). Mock players with a fake connection are avoided because other mods send
     * login payloads to them (e.g. Hostile Neural Networks' config) and fail the test.
     */
    public static ServerPlayer player(GameTestHelper helper, String name) {
        var id = UUID.nameUUIDFromBytes(("cccloud-test:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, name));
        ((TeamManagerImpl) FTBTeamsAPI.api().getManager()).playerLoggedIn(player, id, name);
        return player;
    }

    private static ai.ezfox.cccloud.access.Role role(GameTestHelper helper, UUID mainframeId, UUID player) {
        return ai.ezfox.cccloud.access.Access.resolver(helper.getLevel().getServer()).role(player, mainframeId);
    }
}
