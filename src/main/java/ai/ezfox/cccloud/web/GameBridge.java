package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.access.Role;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

/**
 * Everything the web layer needs from the game. All methods except {@link #serverExecutor()} must be called on the
 * server thread (i.e. from a task submitted to that executor). Having this as an interface keeps the HTTP layer
 * testable without a Minecraft server.
 * <p>
 * Every rights decision is made by the {@link ai.ezfox.cccloud.access.AccessResolver} behind {@link #role}; the web
 * layer turns roles into allow/deny with {@link AccessGate}.
 */
public interface GameBridge {
    Executor serverExecutor();

    /**
     * The live role of a device on a mainframe ({@link Role#NONE} if the player has no role there, is banned, lacks
     * {@code cccloud.use}, or the device is limited to another mainframe).
     */
    Role role(AuthStore.Device device, UUID mainframeId);

    /**
     * WebSocket login: resolves the role and records server-admin views in the owner's audit log. Also marks the device
     * as used (last seen, last IP).
     */
    Role authorize(AuthStore.Device device, UUID mainframeId, String ip);

    /** Shows the in-game approval prompt for a pending device. */
    void requestApproval(AuthStore.Pending pending);

    /**
     * Validates and runs an action. Most actions complete immediately; craft requests complete once the storage mod has
     * planned the job (bounded by a timeout). The future may complete on any thread.
     */
    CompletableFuture<ActionResult> dispatchAction(AuthStore.Device device, UUID mainframeId, ClientMessages.Action action, boolean pinVerified, String ip);

    /** Runs an item search on an item_search widget (read-only, any role from viewer up). */
    QueryResult dispatchQuery(AuthStore.Device device, UUID mainframeId, ClientMessages.Query query);

    /** Stores (null: resets) the validated dashboard layout of a mainframe (admin). Returns null or an error code. */
    @Nullable String setLayout(AuthStore.Device device, UUID mainframeId, @Nullable JsonObject layout);

    /** Unlinks a device from the network map (admin). Returns null or {@code unknown_node|not_allowed|read_only}. */
    @Nullable String unlinkNode(AuthStore.Device device, UUID mainframeId, String nodeId);

    /** Tells the owner that a device got locked because of wrong PINs. */
    void onPinLockout(AuthStore.Device device, UUID mainframeId, String ip);

    /** Display name of a mainframe. */
    String mainframeName(UUID mainframeId);

    /** v0.6 HTTP API ({@code /api/mainframes}, devices, access, shares): runs on the server thread. */
    AccessApi.Response api(Router.Id route, List<AuthStore.Device> devices, String path, String body, String ip);
}
