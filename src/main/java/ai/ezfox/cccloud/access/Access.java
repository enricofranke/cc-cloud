package ai.ezfox.cccloud.access;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.web.WebAuthData;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Game-side entry point to the {@link AccessResolver}: every in-game permission check (screens, commands, Link Cards,
 * relays) asks here. Server thread only.
 */
public final class Access {
    private static final long AUDIT_DEDUP_MS = 10 * 60 * 1000L;

    private Access() {
    }

    public static void init() {
        CloudPermissions.init();
        UseGuard.init();
        // Team changes (FTB Teams events) apply right away, not only on the next 5 s re-check.
        ai.ezfox.cccloud.compat.team.Teams.onChange(player -> {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) server.execute(() -> ai.ezfox.cccloud.web.CloudWebServer.serverTick(server));
        });
        ai.ezfox.cccloud.compat.team.TeamSourceProvider.setNames(id -> {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            return server == null ? "" : CloudPermissions.nameOf(server, id);
        });
        TeamProviders.register(new ScoreboardTeams());
    }

    public static AccessResolver resolver(MinecraftServer server) {
        return new AccessResolver(WebAuthData.access(server), CloudPermissions.of(server), TeamProviders::get, ai.ezfox.cccloud.compat.TeamTrust::ownerTeam);
    }

    public static AccessResolver.Resolution resolve(ServerPlayer player, UUID mainframeId) {
        var server = player.level().getServer();
        return server == null ? AccessResolver.Resolution.NONE : resolver(server).resolve(player.getUUID(), mainframeId);
    }

    public static Role role(ServerPlayer player, UUID mainframeId) {
        return resolve(player, mainframeId).role();
    }

    /** Admin or owner: devices, roles, PIN, Link Cards, the dashboard switch. */
    public static boolean canManage(ServerPlayer player, UUID mainframeId) {
        return role(player, mainframeId).atLeast(Role.ADMIN);
    }

    public static boolean has(ServerPlayer player, ServerPermissions.Node node) {
        var server = player.level().getServer();
        return server != null && CloudPermissions.of(server).has(player.getUUID(), node);
    }

    /**
     * Records that a server admin (permission node, not a role on the mainframe) looked at or changed a mainframe, so
     * the owner sees it in the audit log. Repeated views by the same admin are recorded once per 10 minutes.
     */
    public static void auditAdmin(MinecraftServer server, UUID mainframeId, String adminName, String what) {
        var store = WebAuthData.access(server);
        String text = what + " " + adminName;
        long now = System.currentTimeMillis();
        for (var entry : store.audit(mainframeId)) {
            if (entry.text().equals(text) && now - entry.time() < AUDIT_DEDUP_MS) return;
        }
        store.audit(mainframeId, text);
        CCCloud.LOGGER.info("[cccloud] Audit {}: {}", mainframeId, text);
    }
}
