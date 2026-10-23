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
    ai.ezfox.cccloud.access.AccessResolver.Resolution access(AuthStore.Device device, UUID mainframeId);

    /**
     * WebSocket login: resolves the role and records server-admin views in the owner's audit log. Also marks the device
     * as used (last seen, last IP).
     */
    ai.ezfox.cccloud.access.AccessResolver.Resolution authorize(AuthStore.Device device, UUID mainframeId, String ip);

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

    /** v0.7 wireless redstone override of a channel (act.redstone). Returns null or an error code. */
    @Nullable String redstoneOverride(AuthStore.Device device, UUID mainframeId, ClientMessages.RedstoneOverride override);

    /** v0.5 rules command (role already checked by the session, re-checked live here). */
    RuleResult ruleCommand(AuthStore.Device device, UUID mainframeId, ClientMessages.RuleCommand command);

    /** @param id the id of a created rule */
    record RuleResult(@Nullable String error, @Nullable String detail, @Nullable String id) {
    }

    /** Storage card v2 result: {@code data} on success, else {@code error}. */
    record StorageResult(@Nullable String error, @Nullable JsonObject data) {
        public static StorageResult fail(String error) {
            return new StorageResult(error, null);
        }
    }

    /** Storage card v2 operation (permission re-checked live). Server thread. */
    StorageResult storage(AuthStore.Device device, UUID mainframeId, ClientMessages.Storage request);

    /** Craft preview; {@code done} gets the plan (as {@code data}) once the crafting calculation finished. Server thread. */
    void craftPreview(AuthStore.Device device, UUID mainframeId, ClientMessages.CraftPreview request, java.util.function.Consumer<StorageResult> done);

    /** A map tile: status 200 (png), 204 (no data), 403 (error) or 404. */
    record MapResult(int status, byte @Nullable [] png, @Nullable String etag, @Nullable String error) {
    }

    /** 2D map tile (view.positions, within 512 blocks of the mainframe's network). Server thread; completes later. */
    CompletableFuture<MapResult> mapTile(AuthStore.Device device, UUID mainframeId, String dim, boolean slice, int y, int tx, int tz);

    /** Tells the owner that a device got locked because of wrong PINs. */
    void onPinLockout(AuthStore.Device device, UUID mainframeId, String ip);

    /** Display name of a mainframe. */
    String mainframeName(UUID mainframeId);

    /** A script import request (docs/SPEC.md "Script import – HTTP API"); the PIN is already checked when needed. */
    record ScriptRequest(Router.Id route, AuthStore.Device device, UUID mainframeId, UUID uplinkId, @Nullable String file,
                         java.util.Map<String, String> query, byte[] body, String ip) {
    }

    /** Script import (files and run on the computer behind an uplink card). Server thread. */
    AccessApi.Response script(ScriptRequest request);

    /** v0.6 HTTP API ({@code /api/mainframes}, devices, access, shares): runs on the server thread. */
    AccessApi.Response api(Router.Id route, List<AuthStore.Device> devices, String path, String body, String ip);
}
