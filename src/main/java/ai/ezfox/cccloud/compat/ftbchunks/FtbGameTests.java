package ai.ezfox.cccloud.compat.ftbchunks;

import ai.ezfox.cccloud.claims.ClaimGuard;
import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.gametest.ClaimGuardTests;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * FTB Chunks: the owner claims the test chunk; the mainframe's "Team claims" card counts it and its snapshot carries
 * the chunk in the {@code claims} overlay, restricted to canControl devices. And the claim guard with real claims
 * ({@link #claimGuard}).
 * <p>
 * Both claim real chunks, which affects every test around them (FTB protection, the claim guard), so each runs in a
 * test environment of its own ({@code cccloud:claims_card}, {@code cccloud:claims}) and unclaims when it ends.
 */
public final class FtbGameTests {
    private FtbGameTests() {
    }

    /**
     * The claim guard with FTB Chunks and FTB Teams (docs/SECURITY.md "Claims"): an uplink on a machine, a wired machine
     * card and a Cloud Switch, placed and linked by the owner in unclaimed land, are blocked as soon as another player's
     * party claims their chunks (FTB's claim event), work again while the owner is an ally of that party (FTB's ally
     * event), are blocked again when the alliance ends and work again once the chunks are unclaimed
     * ({@link ClaimGuardTests#run}).
     */
    public static void claimGuard(GameTestHelper helper) {
        var owner = ai.ezfox.cccloud.compat.ftbteams.FtbTeamsGameTests.player(helper, "cccloud_guard_owner");
        var rival = ai.ezfox.cccloud.compat.ftbteams.FtbTeamsGameTests.player(helper, "cccloud_guard_rival");
        Team[] party = {null};
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(FTBChunksAPI.api().isManagerLoaded() && FTBTeamsAPI.api().isManagerLoaded(), "FTB managers"))
            .thenWaitUntil(() -> helper.assertTrue(ClaimGuard.provider() != null && ClaimGuard.provider().ready(), "FTB Chunks is the claim provider"))
            .thenExecute(() -> {
                try {
                    party[0] = FTBTeamsAPI.api().getManager().createPartyTeam(rival, "cccloud_rivals", null, null);
                } catch (Exception e) {
                    throw helper.assertionException("could not build the rival party: " + e);
                }
                ClaimGuardTests.run(helper, owner, new ClaimGuardTests.Claimer() {
                    @Override
                    public String team() {
                        return party[0].getShortName();
                    }

                    @Override
                    public void claim(ServerLevel level, Set<ChunkPos> chunks) {
                        for (var chunk : chunks) {
                            var result = FTBChunksAPI.api().claimAsPlayer(rival, level.dimension(), chunk, false);
                            helper.assertTrue(result.isSuccess(), "claim " + chunk + ": " + result);
                        }
                    }

                    @Override
                    public void unclaim(ServerLevel level, Set<ChunkPos> chunks) {
                        var data = FTBChunksAPI.api().getManager().getOrCreateData(party[0]);
                        for (var chunk : chunks) data.unclaim(rival.createCommandSourceStack(), new ChunkDimPos(level.dimension(), chunk.x(), chunk.z()), false);
                    }

                    @Override
                    public void ally(boolean ally) {
                        var team = (PartyTeam) party[0];
                        try {
                            if (ally) team.addAlly(rival.createCommandSourceStack(), List.of(owner.nameAndId()));
                            else team.removeAlly(rival.createCommandSourceStack(), List.of(owner.nameAndId()));
                        } catch (Exception e) {
                            throw helper.assertionException("ally: " + e);
                        }
                    }

                    @Override
                    public void cleanup(ServerLevel level, Set<ChunkPos> chunks) {
                        unclaim(level, chunks);
                    }
                });
            });
    }

    public static void claims(GameTestHelper helper) {
        var owner = ai.ezfox.cccloud.compat.ftbteams.FtbTeamsGameTests.player(helper, "cccloud_claimer");
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setOwner(owner);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var chunk = ChunkPos.containing(helper.absolutePos(new BlockPos(2, 1, 2)));
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(FTBChunksAPI.api().isManagerLoaded(), "FTB Chunks manager"))
            .thenWaitUntil(() -> helper.assertTrue(MainframeCards.section(mainframe.getMainframeId(), ClaimsCard.KIND) != null, "claims card"))
            .thenExecute(() -> {
                var result = FTBChunksAPI.api().claimAsPlayer(owner, helper.getLevel().dimension(), chunk, false);
                helper.assertTrue(result.isSuccess(), "claim: " + result);
            })
            .thenWaitUntil(() -> {
                var section = MainframeCards.section(mainframe.getMainframeId(), ClaimsCard.KIND);
                var snapshot = section.snapshot();
                helper.assertTrue(snapshot.get("restricted").getAsBoolean(), "claims are canControl-only");
                var chunks = snapshot.getAsJsonObject("claims").getAsJsonArray("chunks");
                boolean found = false;
                for (var c : chunks) {
                    var o = c.getAsJsonObject();
                    if (o.get("x").getAsInt() == chunk.x() && o.get("z").getAsInt() == chunk.z()) found = true;
                }
                helper.assertTrue(found, "overlay has the claimed chunk: " + chunks);
                var claimed = section.values().get("claimed").getAsString();
                helper.assertTrue(claimed.startsWith(chunks.size() + ""), "claimed label: " + claimed);
            })
            .thenExecute(() -> {
                // a claim left behind would block devices of later tests in this chunk (claim guard)
                var data = FTBChunksAPI.api().getManager().getOrCreateData(FTBTeamsAPI.api().getManager().getTeamForPlayerID(owner.getUUID()).orElseThrow());
                data.unclaim(owner.createCommandSourceStack(), new ChunkDimPos(helper.getLevel().dimension(), chunk.x(), chunk.z()), false);
            })
            .thenSucceed();
    }
}
