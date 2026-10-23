package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.Role;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A connected, authenticated dashboard client as seen by the {@link WebHub}: a device with a role, or a share viewer. */
public interface HubSession {
    UUID mainframeId();

    /** The device (browser) of a normal session; null for share sessions. */
    AuthStore.@Nullable Device device();

    /** The share link of a share session, else null. */
    default AccessStore.@Nullable Share share() {
        return null;
    }

    /** The role on {@link #mainframeId()} ({@link Role#SHARE} for share sessions). Re-checked live. */
    Role role();

    /** Updates the role after a live re-check (the hub sends the {@code role} push). */
    default void setRole(Role role) {
    }

    default boolean canControl() {
        return role().canControl();
    }

    /** Queues messages for sending. Must not block. */
    void send(List<String> messages);

    void close(int code, String reason);
}
