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

    /** Effective permissions on {@link #mainframeId()} (empty for share sessions). */
    default java.util.Set<ai.ezfox.cccloud.access.Perm> perms() {
        return ai.ezfox.cccloud.access.Perm.preset(role());
    }

    /** Updates role and permissions after a live re-check (the hub sends the {@code role} push). */
    default void setAccess(Role role, java.util.Set<ai.ezfox.cccloud.access.Perm> perms) {
    }

    /** Buttons etc. ({@code act.buttons}); shown as {@code canControl} in the protocol. */
    default boolean canControl() {
        return perms().contains(ai.ezfox.cccloud.access.Perm.ACT_BUTTONS);
    }

    /** Coordinates on the network map ({@code view.positions}). */
    default boolean seesPositions() {
        return perms().contains(ai.ezfox.cccloud.access.Perm.VIEW_POSITIONS);
    }

    /** Queues messages for sending. Must not block. */
    void send(List<String> messages);

    void close(int code, String reason);
}
