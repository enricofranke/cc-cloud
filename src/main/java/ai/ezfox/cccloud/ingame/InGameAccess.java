package ai.ezfox.cccloud.ingame;

import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.AccessResolver;
import ai.ezfox.cccloud.access.Perm;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one place where the in-game clients (Cloud Tablet, Cloud Display, Cloud Analyzer) ask for rights, routed through
 * the AccessResolver's fine-grained permissions ({@link Access}): {@code view} to see a dashboard, the per-widget-kind
 * permission for actions (checked by the bridge, like for browsers), {@code act.buttons} for in-world display clicks,
 * {@code view.positions} for the analyzer, {@code network.link} to bind a display and {@code layout.edit} to choose
 * what it shows. Server thread only.
 */
public final class InGameAccess {
    private InGameAccess() {
    }

    public static AccessResolver.Resolution resolve(ServerPlayer player, UUID mainframeId) {
        return Access.resolve(player, mainframeId);
    }

    public static boolean has(ServerPlayer player, UUID mainframeId, Perm perm) {
        return Access.has(player, mainframeId, perm);
    }

    /** For a player who may be offline (the player who configured a display). */
    public static boolean has(MinecraftServer server, UUID player, UUID mainframeId, Perm perm) {
        return Access.resolver(server).resolve(player, mainframeId).perms().contains(perm);
    }

    public static boolean canView(ServerPlayer player, UUID mainframeId) {
        return has(player, mainframeId, Perm.VIEW);
    }
}
