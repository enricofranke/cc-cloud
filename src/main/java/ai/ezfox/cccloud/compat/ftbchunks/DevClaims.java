package ai.ezfox.cccloud.compat.ftbchunks;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * FTB Teams and FTB Chunks for the dev commands ({@code tools/e2e}): scripted players, parties, allies and claims through
 * the same calls the {@code FtbGameTests} use. Only loaded with {@code -Dcccloud.devCommands=true} and FTB Chunks.
 */
public final class DevClaims {
    private DevClaims() {
    }

    public static ServerPlayer player(ServerLevel level, GameProfile profile) {
        var player = FakePlayerFactory.get(level, profile);
        ((TeamManagerImpl) FTBTeamsAPI.api().getManager()).playerLoggedIn(player, profile.id(), profile.name());
        return player;
    }

    /** Creates the player's party unless the player already is in one. Returns the team's name. */
    public static String party(ServerPlayer owner, String name) throws Exception {
        var manager = FTBTeamsAPI.api().getManager();
        var current = manager.getTeamForPlayerID(owner.getUUID());
        if (current.isPresent() && current.get().isPartyTeam()) return current.get().getShortName();
        return manager.createPartyTeam(owner, name, null, null).getShortName();
    }

    public static void ally(ServerPlayer owner, ServerPlayer other, boolean on) throws Exception {
        var team = (PartyTeam) FTBTeamsAPI.api().getManager().getTeamForPlayerID(owner.getUUID()).orElseThrow();
        if (on) team.addAlly(owner.createCommandSourceStack(), List.of(other.nameAndId()));
        else team.removeAlly(owner.createCommandSourceStack(), List.of(other.nameAndId()));
    }

    public static String claim(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
        return FTBChunksAPI.api().claimAsPlayer(player, level.dimension(), chunk, false).toString();
    }

    public static void unclaim(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
        var team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(player.getUUID()).orElseThrow();
        FTBChunksAPI.api().getManager().getOrCreateData(team).unclaim(player.createCommandSourceStack(), new ChunkDimPos(level.dimension(), chunk.x(), chunk.z()), false);
    }
}
