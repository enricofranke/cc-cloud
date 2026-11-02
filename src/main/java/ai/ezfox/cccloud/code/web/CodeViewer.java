package ai.ezfox.cccloud.code.web;

import java.util.List;

/**
 * A dashboard session as the Cloud Scripts side sees it: where {@code code_status} and {@code code_log} pushes go. Its
 * methods are called on the server thread; {@link #send} must not block.
 */
public interface CodeViewer {
    /** Queues messages (JSON text) for the session. */
    void send(List<String> messages);

    /** The session is open and still has {@code code.edit} (checked again before every push). */
    boolean allowed();
}
