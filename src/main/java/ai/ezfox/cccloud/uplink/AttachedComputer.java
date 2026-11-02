package ai.ezfox.cccloud.uplink;

import org.jspecify.annotations.Nullable;

/**
 * A computer that uses an uplink through its {@code cloud_uplink} peripheral (CC: Tweaked's {@code IComputerAccess},
 * wrapped in {@code compat/cct}): the uplink queues {@code cloud_action} events on it. Equal wrappers mean the same
 * attachment.
 */
public interface AttachedComputer {
    int id();

    /** The name the computer knows the uplink by ({@code "top"}, {@code "cloud_uplink_0"}, …). */
    String attachmentName();

    void queueEvent(String event, @Nullable Object... args);
}
