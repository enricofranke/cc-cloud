package ai.ezfox.cccloud.code.engine;

import org.jspecify.annotations.Nullable;

/** A compile problem for the editor: 1-based line (and column when the engine knows it). */
public record Diagnostic(int line, @Nullable Integer column, String message, String severity) {
    public static Diagnostic error(int line, String message) {
        return new Diagnostic(Math.max(1, line), null, message, "error");
    }
}
