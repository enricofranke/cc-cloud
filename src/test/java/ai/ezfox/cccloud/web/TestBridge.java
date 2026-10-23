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

    @Override
    public Role role(AuthStore.Device device, UUID mainframeId) {
        if (device.mainframeId() != null && !device.mainframeId().equals(mainframeId)) return Role.NONE;
        return roles.apply(device, mainframeId);
    }

    @Override
    public Role authorize(AuthStore.Device device, UUID mainframeId, String ip) {
        return role(device, mainframeId);
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
    public @Nullable String unlinkNode(AuthStore.Device device, UUID mainframeId, String nodeId) {
        dispatched.add("unlink:" + nodeId);
        return null;
    }

    @Override
    public void onPinLockout(AuthStore.Device device, UUID mainframeId, String ip) {
    }

    @Override
    public String mainframeName(UUID mainframeId) {
        return "Test";
    }

    @Override
    public AccessApi.Response api(Router.Id route, List<AuthStore.Device> devices, String path, String body, String ip) {
        dispatched.add("api:" + route);
        return AccessApi.error(404, "not_found");
    }
}
