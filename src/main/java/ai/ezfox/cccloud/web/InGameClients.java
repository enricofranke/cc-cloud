package ai.ezfox.cccloud.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * v0.7: entry point for in-game dashboard clients (Cloud Tablet, Cloud Display). They are authenticated by being the
 * player, so they get a synthetic {@link AuthStore.Device} (never stored, no token) and go through exactly the same
 * live role check ({@code AccessGate} via the AccessResolver), uplink check, validation and audit log as a browser
 * ({@link MinecraftBridge}). Server thread only.
 */
public final class InGameClients {
    /** Prefix of the synthetic device hashes (never a real SHA-256 hex string, so it cannot collide with a token). */
    public static final String DEVICE_PREFIX = "ingame:";

    private InGameClients() {
    }

    /**
     * The synthetic device of a player on a mainframe ({@code source} = "tablet" | "display", shown in the audit log).
     * It is limited to that mainframe and not capped, so its role is exactly the player's role from the AccessResolver.
     */
    public static AuthStore.Device device(ServerPlayer player, UUID mainframeId, String source) {
        long now = System.currentTimeMillis();
        String name = player.getGameProfile().name() + " (" + source + ")";
        return new AuthStore.Device(DEVICE_PREFIX + player.getUUID(), mainframeId, name, true, now, now, player.getUUID(), player.getGameProfile().name(),
            "in-game", "");
    }

    /**
     * Parses and runs a browser-format action message ({@code {"t":"action",...}}) for an in-game device. PINs must be
     * checked by the caller ({@code pinVerified}).
     */
    public static CompletableFuture<ActionResult> action(MinecraftServer server, AuthStore.Device device, ClientMessages.Action action, boolean pinVerified) {
        return new MinecraftBridge(server, WebAuthData.get(server)).dispatchAction(device, device.mainframeId(), action, pinVerified, "in-game");
    }

    public static QueryResult query(MinecraftServer server, AuthStore.Device device, ClientMessages.Query query) {
        return new MinecraftBridge(server, WebAuthData.get(server)).dispatchQuery(device, device.mainframeId(), query);
    }

    /** PIN check with the same lockout as browsers (the lockout is per player). Slow (PBKDF2): call off the server thread. */
    public static AuthStore.PinCheck checkPin(MinecraftServer server, AuthStore.Device device, String pin) {
        return WebAuthData.get(server).checkPin(device.mainframeId(), device.tokenHash(), pin);
    }

    /** The published snapshot of one section ({uplinkId,title,online,widgets,...}), or null. Any thread. */
    public static @Nullable JsonObject section(UUID mainframeId, String sectionId) {
        return WebHub.INSTANCE.sectionSnapshot(mainframeId, sectionId);
    }

    /** Ids and titles of the sections of a mainframe (for pickers); restricted ones are flagged. Any thread. */
    public static JsonArray sections(UUID mainframeId) {
        return WebHub.INSTANCE.sectionList(mainframeId);
    }

    /** Sends pending hub messages when the embedded web server (which normally flushes every 100 ms) is not running. */
    public static void flushIfNoWebServer() {
        if (!CloudWebServer.isRunning()) WebHub.INSTANCE.flush();
    }
}
