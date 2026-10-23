package ai.ezfox.cccloud.compat;

import ai.ezfox.cccloud.compat.team.TeamSource;
import ai.ezfox.cccloud.compat.team.Teams;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The v0.5 team trust rule (docs/SPEC.md v0.5 B, config {@code ftbTeamsTrust}): players with at least member rank in
 * the mainframe owner's team count as trusted. The team data comes from the active {@link TeamSource} (FTB Teams); the
 * decision is made here, not in the adapter. Consulted live by {@code AuthStore.Acl.allows} and
 * {@code CloudMainframeBlockEntity.canManage}, so team changes apply on the next action or rights check.
 * <p>
 * v0.6 replaces this with role mapping (owner/officer → admin, member → operator, ally → viewer) in the core access
 * resolver; the adapter stays the same. Pure Java (the auth store's unit tests use it); failures count as "not trusted".
 */
public final class TeamTrust {
    private static volatile BooleanSupplier enabled = () -> true;

    private TeamTrust() {
    }

    /** The config switch (set by {@link Integrations}). */
    public static void setEnabled(BooleanSupplier isEnabled) {
        enabled = isEnabled;
    }

    /** True if {@code player} is trusted through the owner's team. */
    public static boolean trusts(@Nullable UUID owner, UUID player) {
        var source = Teams.source();
        if (source == null || owner == null || owner.equals(player)) return false;
        try {
            if (!enabled.getAsBoolean()) return false;
            var team = source.teamOf(owner);
            return team.isPresent() && source.rank(team.get().id(), player).isMember();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
