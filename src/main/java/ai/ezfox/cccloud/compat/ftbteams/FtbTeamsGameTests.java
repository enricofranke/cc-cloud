package ai.ezfox.cccloud.compat.ftbteams;

import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.team.TeamSource;
import ai.ezfox.cccloud.compat.team.Teams;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import ai.ezfox.cccloud.web.WebAuthData;
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
 * FTB Teams trust: the owner creates a party (public API), a second player joins it (FTB's party class, test only);
 * the member may then manage the mainframe and its devices' rights check passes (same ACL as the web layer), a
 * stranger may not, the config switch turns it off, and after leaving the party the rights are gone again.
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
                helper.assertTrue(!mainframe.canManage(member), "not trusted before joining");
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
                helper.assertTrue(mainframe.canManage(member), "a party member manages the mainframe");
                helper.assertTrue(acl(helper, mainframe.getMainframeId(), member.getUUID()), "device rights (ACL) follow the team");
                helper.assertTrue(!mainframe.canManage(stranger), "strangers are still refused");
                helper.assertTrue(!acl(helper, mainframe.getMainframeId(), stranger.getUUID()), "stranger ACL");
            })
            .thenExecute(() -> {
                IntegrationConfig.FTB_TEAMS_TRUST.set(false);
                try {
                    helper.assertTrue(!mainframe.canManage(member), "ftbTeamsTrust=false switches team trust off");
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
                helper.assertTrue(!mainframe.canManage(member), "rights end when the member leaves (live)");
                helper.assertTrue(!acl(helper, mainframe.getMainframeId(), member.getUUID()), "ACL too");
            })
            .thenSucceed();
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

    private static boolean acl(GameTestHelper helper, UUID mainframeId, UUID player) {
        var acl = WebAuthData.get(helper.getLevel().getServer()).acl(mainframeId);
        return acl != null && acl.allows(player);
    }
}
