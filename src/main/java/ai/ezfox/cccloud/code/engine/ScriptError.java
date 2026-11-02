package ai.ezfox.cccloud.code.engine;

import org.jspecify.annotations.Nullable;

/**
 * An error the script did not catch, a compile error or a stop by a limit, the same for every engine: its
 * {@link Kind}, the message as the script would print it ({@code Night mode:12: attempt to index a nil value}, i.e.
 * {@code chunk:line: detail}), the line in the script if the message names one, and the engine's traceback for the log.
 * The kinds are the ones the WebAssembly spike uses (docs/WASM_SPIKE.md on branch wasm-spike).
 */
public final class ScriptError extends Exception {
    public enum Kind {
        /** The source does not parse. */
        SYNTAX,
        /** An uncaught error in the script. */
        RUNTIME,
        /** Ran too long without waiting for anything. */
        CPU_LIMIT,
        /** Allocated or kept more than its memory budget. */
        MEMORY_LIMIT,
        /** Recursion deeper than the engine or the thread allows. */
        STACK_OVERFLOW,
        /** Stopped from outside (a person, the kill switch, the server stopping). */
        KILLED,
        /** The engine itself failed (a bug, a budget below the engine's minimum, a language this server cannot run). */
        ENGINE;

        /** {@code cpu_limit}: the form in the dashboard protocol. */
        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final Kind kind;
    private final @Nullable Integer line;
    private final @Nullable String traceback;

    public ScriptError(Kind kind, String message, @Nullable Integer line, @Nullable String traceback) {
        super(message, null, false, false);
        this.kind = kind;
        this.line = line;
        this.traceback = traceback;
    }

    /** A {@link Kind#RUNTIME} error. */
    public ScriptError(String message, @Nullable Integer line, @Nullable String traceback) {
        this(Kind.RUNTIME, message, line, traceback);
    }

    public Kind kind() {
        return kind;
    }

    public @Nullable Integer line() {
        return line;
    }

    public @Nullable String traceback() {
        return traceback;
    }

    /** The line from a message that starts with {@code <chunk>:<line>:}, or null. */
    public static @Nullable Integer lineOf(String message, String chunk) {
        if (!message.startsWith(chunk + ":")) return null;
        int start = chunk.length() + 1;
        int end = start;
        while (end < message.length() && Character.isDigit(message.charAt(end))) end++;
        if (end == start || end >= message.length() || message.charAt(end) != ':') return null;
        try {
            return Integer.parseInt(message.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
