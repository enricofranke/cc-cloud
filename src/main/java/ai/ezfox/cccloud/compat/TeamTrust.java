package ai.ezfox.cccloud.compat;

import ai.ezfox.cccloud.access.MainframeAccess;
import ai.ezfox.cccloud.compat.team.TeamSourceProvider;
import ai.ezfox.cccloud.compat.team.Teams;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The v0.5 team trust rule (config {@code ftbTeamsTrust}), folded into the v0.6 AccessResolver: a mainframe that is not
 * assigned to a team explicitly uses its owner's team (FTB Teams) with the default rank → role mapping (owner/officer
 * admin, member operator, ally viewer). An explicit team assignment on the Access page replaces it. Pure Java.
 */
public final class TeamTrust {
    private static volatile BooleanSupplier enabled = () -> true;

    private TeamTrust() {
    }

    /** The config switch (set by {@link Integrations}). */
    public static void setEnabled(BooleanSupplier isEnabled) {
        enabled = isEnabled;
    }

    /** The implicit team binding of a mainframe owned by {@code owner}, or null (no team mod, switched off, no team). */
    public static MainframeAccess.@Nullable TeamBinding ownerTeam(@Nullable UUID owner) {
        var source = Teams.source();
        if (source == null || owner == null) return null;
        try {
            if (!enabled.getAsBoolean()) return null;
            var provider = new TeamSourceProvider(source);
            String team = provider.ownerTeam(owner);
            return team == null ? null : new MainframeAccess.TeamBinding(source.id(), team, provider.defaultMapping());
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
