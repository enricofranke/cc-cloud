package ai.ezfox.cccloud.compat.ftbchunks;

import ai.ezfox.cccloud.claims.ClaimGuard;
import ai.ezfox.cccloud.claims.ClaimProvider;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.event.ChunkChangeEvent;
import dev.ftb.mods.ftbchunks.api.neoforge.FTBChunksEvent;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * FTB Chunks as the {@link ClaimProvider} of the claim guard (docs/SECURITY.md "Claims"): devices, the Cloud Probe, map
 * slices and voxels never read chunks claimed by a team the player is not a member or ally of. Claims and unclaims
 * reach the guard at once through FTB Chunks' {@code ChunkChange.Post} event; team and ally changes come from FTB Teams
 * ({@code compat/ftbteams/FtbTeamsSource}).
 */
final class FtbClaims implements ClaimProvider {
    static void init() {
        ClaimGuard.setProvider(new FtbClaims());
        NeoForge.EVENT_BUS.addListener(FTBChunksEvent.ChunkChange.Post.class, e -> {
            var data = e.getEventData();
            var op = data.operation();
            if (op != ChunkChangeEvent.Operation.CLAIM && op != ChunkChangeEvent.Operation.UNCLAIM) return;
            var pos = data.claimedChunk().getPos();
            ClaimGuard.claimsChanged(pos.dimension(), pos.x(), pos.z());
        });
    }

    @Override
    public boolean ready() {
        return FTBChunksAPI.api().isManagerLoaded() && FTBTeamsAPI.api().isManagerLoaded();
    }

    @Override
    public @Nullable Team claimAt(ServerLevel level, int chunkX, int chunkZ) {
        var api = FTBChunksAPI.api();
        if (!api.isManagerLoaded()) return null;
        var chunk = api.getManager().getChunk(new ChunkDimPos(level.dimension(), chunkX, chunkZ));
        if (chunk == null) return null;
        var team = chunk.getTeamData().getTeam();
        return team == null ? null : new Team(team.getId(), team.getShortName());
    }

    @Override
    public boolean trusted(Team team, UUID player) {
        var api = FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) return false;
        var t = api.getManager().getTeamByID(team.id());
        return t.isPresent() && t.get().getRankForPlayer(player).isAllyOrBetter();
    }
}
