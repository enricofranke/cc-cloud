package ai.ezfox.cccloud.code.engine;

/**
 * One line of execution inside an engine instance: the script's main body, or one call of a handler or timer. Tasks
 * of one instance never run at the same time; each can be paused (a sleep, an action, a used-up CPU share) and
 * continued later.
 */
public interface Task {
    /** What started it, for the log: {@code main}, {@code on change}, {@code every 10s}. */
    String label();
}
