package ai.ezfox.cccloud.compat.team;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A team/party mod seen through a neutral interface (v0.5 B, prepared for v0.6 "Teams / clans"): who is in which team
 * with which rank. It makes no permission decisions; core code maps teams and ranks to rights ({@code TeamTrust} in
 * v0.5, the role resolver in v0.6). Implementations live in {@code compat/<modid>} (FTB Teams). Pure Java.
 * Methods are called on the server thread and must be cheap; they return empty/NONE when the mod's data is not loaded.
 */
public interface TeamSource {
    /** Rank of a player in a team, highest first. */
    enum Rank {
        OWNER, OFFICER, MEMBER, ALLY, NONE;

        /** Owner, officer or member. */
        public boolean isMember() {
            return this == OWNER || this == OFFICER || this == MEMBER;
        }
    }

    /** A team: its id, display name and colour (0xRRGGBB). */
    record Team(UUID id, String name, int color) {
    }

    /** Short id of the provider, e.g. "ftbteams". */
    String id();

    /** The team the player plays in (for FTB Teams: the party, else the player's own team). */
    Optional<Team> teamOf(UUID player);

    /** The player's rank in the given team ({@link Rank#NONE} if unknown). */
    Rank rank(UUID teamId, UUID player);

    /** All players with at least member rank in the team. */
    Set<UUID> members(UUID teamId);
}
