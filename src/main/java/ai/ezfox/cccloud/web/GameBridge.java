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

    /** Cloud Redstone devices: rename / regroup (network.link). Returns null or an error code. */
    default @Nullable String redstoneDevice(AuthStore.Device device, UUID mainframeId, ClientMessages.RedstoneDevice message) {
        return "unsupported";
    }

    /** v0.5 rules command (role already checked by the session, re-checked live here). */
    RuleResult ruleCommand(AuthStore.Device device, UUID mainframeId, ClientMessages.RuleCommand command);

    /** @param id the id of a created rule */
    record RuleResult(@Nullable String error, @Nullable String detail, @Nullable String id) {
    }

    /** v0.10 scenes: save or delete (rules.edit, re-checked live here); answered like rules ({@code scene_result}). */
    default RuleResult sceneCommand(AuthStore.Device device, UUID mainframeId, ClientMessages.SceneCommand command) {
        return new RuleResult("disabled", null, null);
    }

    /** v0.10 public API: one request of an authenticated API key (server thread; the owner's live rights are checked here). */
    default AccessApi.Response apiV1(ai.ezfox.cccloud.publicapi.ApiKeys.Key key, Router.Id route, String path, java.util.Map<String, String> query, String body,
                                     String ip) {
        return AccessApi.error(404, "not_found");
    }

    /**
     * v0.10 event stream: what the stream of this key may see now (the owner's live rights ∩ {@code read:sections}), or
     * null if it may not (any more). Server thread.
     */
    default java.util.@Nullable Set<ai.ezfox.cccloud.access.Perm> apiStreamAccess(ai.ezfox.cccloud.publicapi.ApiKeys.Key key, String ip) {
        return null;
    }

    /** v0.10 production statistics (permissions re-checked live here). Server thread. */
    default StorageResult production(AuthStore.Device device, UUID mainframeId, ClientMessages.Production request) {
        return StorageResult.fail("disabled");
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

    /** Voxel view section: status 200 (json), 204 (not loaded), 403/404 (error). */
    record VoxelResult(int status, @Nullable JsonObject json, @Nullable String error) {
    }

    /** One 16³ section of exposed blocks (view.positions, within voxelRadius of the network). Server thread; completes later. */
    CompletableFuture<VoxelResult> voxels(AuthStore.Device device, UUID mainframeId, String dim, int cx, int cy, int cz);

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

    /** v0.5 E: opens the terminal of the computer behind an uplink for a session (server thread); null or an error code. */
    default @Nullable String terminalOpen(AuthStore.Device device, UUID mainframeId, UUID uplinkId, ai.ezfox.cccloud.terminal.TerminalService.Viewer viewer) {
        return "disabled";
    }

    /** v0.5 E: input for an open terminal (server thread); null or an error code. */
    default @Nullable String terminalInput(AuthStore.Device device, UUID mainframeId, UUID uplinkId, ai.ezfox.cccloud.terminal.TerminalService.Viewer viewer,
                                           com.google.gson.JsonElement events) {
        return "disabled";
    }

    default void terminalClose(UUID mainframeId, @Nullable UUID uplinkId, ai.ezfox.cccloud.terminal.TerminalService.Viewer viewer) {
    }

    /** Security review L9: verifies a store install against the signed index (blocking; null = ok, else an error code). */
    default @Nullable String verifyStoreFile(ScriptRequest request) {
        return "store_not_configured";
    }

    /** The live recipes of CC: Cloud items ({@code GET /api/recipes}): a serialized JSON document and its ETag. */
    record RecipesJson(byte[] json, String etag) {
    }

    /**
     * The server's current recipes for our items, or null if there are none to show. Called on the server thread; may
     * complete later on another thread.
     */
    default CompletableFuture<@Nullable RecipesJson> recipes() {
        return CompletableFuture.completedFuture(null);
    }

    /** Security review L3: a share link works only while its creator may still create shares (null creator: older links). */
    default boolean shareCreatorAllowed(ai.ezfox.cccloud.access.AccessStore.Share share) {
        return true;
    }
}
