package ai.ezfox.cccloud.access;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A source of teams/clans/parties whose members get a role on a mainframe (docs/SPEC.md v0.6 "Teams / clans").
 * <p>
 * The core ships the vanilla scoreboard provider; mod integrations (FTB Teams, Open Parties and Claims) implement this
 * interface in {@code compat/<modid>} and register it with {@link TeamProviders#register} during mod construction when
 * their mod is loaded. All methods are called on the server thread and must be cheap (they run on every rights
 * re-check, i.e. on connect, on every action and every 5 seconds per open dashboard): read the mod's in-memory data,
 * never do I/O.
 */
public interface TeamProvider {
    /** A team as shown on the web "Access" page. {@code id} must be stable across restarts (e.g. the team UUID). */
    record Team(String id, String name) {
    }

    /** Stable provider id used in saved data and the web protocol, e.g. {@code ftbteams}, {@code opac}, {@code scoreboard}. */
    String id();

    /** False while the backing mod/data is not ready (e.g. before the server started). */
    default boolean available() {
        return true;
    }

    /** The ranks of this provider, highest first, e.g. {@code owner, officer, member, ally}. */
    List<String> ranks();

    /** Default rank → role mapping for a newly assigned team ({@link Role#NONE} = no access). */
    Map<String, Role> defaultMapping();

    /** The player's rank in the team, or null if the player is not part of it (or the team no longer exists). */
    @Nullable String rankOf(String teamId, UUID player);

    /** The team with this id, or null if it does not exist (any more). */
    @Nullable Team team(String teamId);

    /** Teams the player belongs to (offered when assigning a mainframe to a team). */
    List<Team> teamsOf(UUID player);

    /** A team member with rank, for the web "Access" page. */
    record Member(UUID uuid, String name, String rank) {
    }

    /** Members of a team (for display only; access is always decided by {@link #rankOf}). Optional. */
    default List<Member> members(String teamId) {
        return List.of();
    }
}
