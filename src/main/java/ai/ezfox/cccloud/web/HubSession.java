package ai.ezfox.cccloud.web;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A connected, authenticated dashboard client as seen by the {@link WebHub}. */
public interface HubSession {
    UUID mainframeId();

    AuthStore.@Nullable Device device();

    /** Queues messages for sending. Must not block. */
    void send(List<String> messages);

    void close(int code, String reason);
}
