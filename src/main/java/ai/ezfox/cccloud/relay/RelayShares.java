package ai.ezfox.cccloud.relay;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.access.TeamProviders;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.network.graph.RelaySharing;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Game side of {@link RelaySharing}: the team answers come from the enabled team providers (FTB Teams with allies,
 * scoreboard teams, ...; config {@code teams.disabledTeamProviders}) through one cache per server. A team change event
 * clears the cache and recomputes every network once; scoreboard teams have no events and apply within
 * {@link RelaySharing#TTL_MS} (with the next safety pass). Nothing here runs per tick.
 */
public final class RelayShares {
    public static final RelaySharing.Cache CACHE = new RelaySharing.Cache(
        (relayOwner, player) -> TeamProviders.sameTeamOrAlly(TeamProviders.enabled(), relayOwner, player), System::currentTimeMillis, RelaySharing.TTL_MS);
    private static final AtomicBoolean LEGACY_LOGGED = new AtomicBoolean();

    private RelayShares() {
    }

    public static void init() {
        ai.ezfox.cccloud.compat.team.Teams.onChange(player -> teamsChanged());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            CACHE.clear();
            LEGACY_LOGGED.set(false); // the next start logs a legacy relay again
        });
    }

    /** Someone joined or left a team, allies changed, or the team settings were reloaded: every network again. */
    public static void teamsChanged() {
        CACHE.clear();
        NetworkService.markDirty();
    }

    /** The server's default for relays whose owner did not choose ({@code network.relayShareDefault}). */
    public static RelaySharing.Share serverDefault() {
        var share = RelaySharing.Share.byId(CloudConfig.get(CloudConfig.RELAY_SHARE_DEFAULT));
        return share != null ? share : RelaySharing.Share.TEAM;
    }

    /** Whether a relay of this owner with this setting carries the network of a mainframe of {@code mainframeOwner}. */
    public static boolean carries(@Nullable UUID relayOwner, RelaySharing.Share share, @Nullable UUID mainframeOwner) {
        return RelaySharing.carries(relayOwner, share, mainframeOwner, CACHE);
    }

    /** A relay without an owner was loaded: one log line per server run, not one per relay. */
    static void legacyLoaded(BlockPos pos) {
        if (!LEGACY_LOGGED.compareAndSet(false, true)) return;
        CCCloud.LOGGER.info("[cccloud] The relay at {} has no owner (placed before relays had owners, or by a machine): it carries every network "
            + "in range, as before. Break and place it again to make it a team relay. Logged once for all such relays.", pos.toShortString());
    }
}
