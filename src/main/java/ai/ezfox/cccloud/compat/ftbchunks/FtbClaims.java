package ai.ezfox.cccloud.compat.ftbchunks;

import ai.ezfox.cccloud.map.XrayGuard;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * FTB Chunks claims for the X-ray guard (security review M4): cave slices and voxels never show chunks claimed by a
 * team the player is not a member or ally of.
 */
final class FtbClaims implements XrayGuard.Claims {
    @Override
    public boolean claimedByOther(ServerLevel level, int chunkX, int chunkZ, UUID player) {
        var api = FTBChunksAPI.api();
        if (!api.isManagerLoaded()) return false;
        var chunk = api.getManager().getChunk(new ChunkDimPos(level.dimension(), chunkX, chunkZ));
        if (chunk == null) return false;
        var team = chunk.getTeamData().getTeam();
        return team != null && !team.getRankForPlayer(player).isAllyOrBetter();
    }
}
