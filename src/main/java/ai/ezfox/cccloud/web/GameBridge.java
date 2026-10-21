package ai.ezfox.cccloud.web;

import java.util.UUID;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

/**
 * Everything the web layer needs from the game. All methods except {@link #serverExecutor()} must be called on the
 * server thread (i.e. from a task submitted to that executor). Having this as an interface keeps the HTTP layer
 * testable without a Minecraft server.
 */
public interface GameBridge {
    Executor serverExecutor();

    /** Re-checks the live rights of the player a device belongs to. Returns null if allowed, else an error code. */
    @Nullable String checkRights(AuthStore.Device device);

    /** Shows the in-game approval prompt for a pending device. */
    void requestApproval(AuthStore.Pending pending);

    /** Validates and runs an action. Returns null on success, else an error code. */
    @Nullable String dispatchAction(AuthStore.Device device, ClientMessages.Action action, boolean pinVerified, String ip);

    /** Tells the owner that a device got locked because of wrong PINs. */
    void onPinLockout(AuthStore.Device device, String ip);

    /** Display name of a mainframe. */
    String mainframeName(UUID mainframeId);
}
