package ai.ezfox.cccloud.claims;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * A claim mod as CC: Cloud sees it (docs/SECURITY.md "Claims"): who claimed a chunk, and whether a player counts as that
 * team (a member or an ally). FTB Chunks implements it ({@code compat/ftbchunks/FtbClaims}); another claim mod can be
 * added with an implementation of its own and {@link ClaimGuard#setProvider}. A provider tells the guard about claim
 * changes with {@link ClaimGuard#claimsChanged} and about team changes through
 * {@link ai.ezfox.cccloud.compat.team.Teams#fireChanged}; without events the safety pass still finds them.
 * <p>
 * Called on the server thread; the map and voxel views also call it from the web threads, as they did before.
 */
public interface ClaimProvider {
    /** The team that claimed a chunk: a stable id and the name players see. */
    record Team(UUID id, String name) {
    }

    /** False while the claim data is not loaded yet (server start); everything is unclaimed until then. */
    default boolean ready() {
        return true;
    }

    /** The team that claimed this chunk, or null when nobody did. */
    @Nullable Team claimAt(ServerLevel level, int chunkX, int chunkZ);

    /** Whether the player is a member or an ally of the team (both count as the same team). */
    boolean trusted(Team team, UUID player);
}
