package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.access.Role;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * A {@link GameBridge} for WebSocket/HTTP tests: runs everything inline, records what reached the "game", and resolves
 * roles with a pluggable function (default: canControl devices are operators, read-only devices viewers).
 */
class TestBridge implements GameBridge {
    final List<String> dispatched = new ArrayList<>();
    BiFunction<AuthStore.Device, UUID, Role> roles = (device, mainframe) -> device.canControl() ? Role.OPERATOR : Role.VIEWER;

    @Override
    public Executor serverExecutor() {
        return Runnable::run;
    }

    /** Optional: exact permissions (else the preset of the role). */
    BiFunction<AuthStore.Device, UUID, java.util.Set<ai.ezfox.cccloud.access.Perm>> perms = null;

    Role role(AuthStore.Device device, UUID mainframeId) {
        if (device.mainframeId() != null && !device.mainframeId().equals(mainframeId)) return Role.NONE;
        return roles.apply(device, mainframeId);
    }

    @Override
    public ai.ezfox.cccloud.access.AccessResolver.Resolution access(AuthStore.Device device, UUID mainframeId) {
        var role = role(device, mainframeId);
        var set = role == Role.NONE ? java.util.Set.<ai.ezfox.cccloud.access.Perm>of()
            : perms != null ? perms.apply(device, mainframeId) : ai.ezfox.cccloud.access.Perm.preset(role);
        return new ai.ezfox.cccloud.access.AccessResolver.Resolution(role, ai.ezfox.cccloud.access.AccessResolver.Source.DIRECT, null, set);
    }

    @Override
    public ai.ezfox.cccloud.access.AccessResolver.Resolution authorize(AuthStore.Device device, UUID mainframeId, String ip) {
        return access(device, mainframeId);
    }

    @Override
    public void requestApproval(AuthStore.Pending pending) {
    }

    @Override
    public CompletableFuture<ActionResult> dispatchAction(AuthStore.Device device, UUID mainframeId, ClientMessages.Action action, boolean pinVerified, String ip) {
        dispatched.add("action:" + action.id());
        return ActionResult.done(null);
    }

    @Override
    public QueryResult dispatchQuery(AuthStore.Device device, UUID mainframeId, ClientMessages.Query query) {
        dispatched.add("query:" + query.id());
        return QueryResult.fail("x");
    }

    @Override
    public @Nullable String setLayout(AuthStore.Device device, UUID mainframeId, @Nullable JsonObject layout) {
        dispatched.add("layout");
        return null;
    }

    @Override
    public @Nullable String redstoneOverride(AuthStore.Device device, UUID mainframeId, ClientMessages.RedstoneOverride override) {
        dispatched.add("rs_override:" + override.channel() + "=" + override.level());
        return null;
    }

    @Override
    public @Nullable String unlinkNode(AuthStore.Device device, UUID mainframeId, String nodeId) {
        dispatched.add("unlink:" + nodeId);
        return null;
    }

    @Override
    public GameBridge.RuleResult ruleCommand(AuthStore.Device device, UUID mainframeId, ClientMessages.RuleCommand command) {
        dispatched.add(command.op());
        return new GameBridge.RuleResult(null, null, null);
    }

    @Override
    public GameBridge.StorageResult storage(AuthStore.Device device, UUID mainframeId, ClientMessages.Storage request) {
        dispatched.add("storage:" + request.op());
        return new GameBridge.StorageResult(null, new com.google.gson.JsonObject());
    }

    @Override
    public void craftPreview(AuthStore.Device device, UUID mainframeId, ClientMessages.CraftPreview request, java.util.function.Consumer<GameBridge.StorageResult> done) {
        dispatched.add("preview:" + request.name());
        done.accept(new GameBridge.StorageResult(null, new com.google.gson.JsonObject()));
    }

    @Override
    public java.util.concurrent.CompletableFuture<GameBridge.MapResult> mapTile(AuthStore.Device device, UUID mainframeId, String dim, boolean slice, int y, int tx, int tz) {
        dispatched.add("map:" + tx + "," + tz);
        return java.util.concurrent.CompletableFuture.completedFuture(new GameBridge.MapResult(204, null, null, null));
    }

    @Override
    public java.util.concurrent.CompletableFuture<GameBridge.VoxelResult> voxels(AuthStore.Device device, UUID mainframeId, String dim, int cx, int cy, int cz) {
        dispatched.add("voxels");
        return java.util.concurrent.CompletableFuture.completedFuture(new GameBridge.VoxelResult(204, null, null));
    }

    @Override
    public void onPinLockout(AuthStore.Device device, UUID mainframeId, String ip) {
    }

    @Override
    public String mainframeName(UUID mainframeId) {
        return "Test";
    }

    @Override
    public AccessApi.Response script(GameBridge.ScriptRequest request) {
        dispatched.add("script:" + request.route());
        return AccessApi.error(404, "not_found");
    }

    @Override
    public AccessApi.Response api(Router.Id route, List<AuthStore.Device> devices, String path, String body, String ip) {
        dispatched.add("api:" + route);
        return AccessApi.error(404, "not_found");
    }

    @Override
    public @org.jspecify.annotations.Nullable String terminalOpen(AuthStore.Device device, UUID mainframeId, UUID uplinkId,
                                                                  ai.ezfox.cccloud.terminal.TerminalService.Viewer viewer) {
        dispatched.add("term_open");
        return null;
    }

    @Override
    public @org.jspecify.annotations.Nullable String terminalInput(AuthStore.Device device, UUID mainframeId, UUID uplinkId,
                                                                   ai.ezfox.cccloud.terminal.TerminalService.Viewer viewer, com.google.gson.JsonElement events) {
        dispatched.add("term_input");
        return null;
    }
}
