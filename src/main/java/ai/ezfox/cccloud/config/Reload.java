package ai.ezfox.cccloud.config;

import org.jspecify.annotations.Nullable;

/**
 * Whether a server config value takes effect while the server runs. Every value's comment in the toml ends with the
 * marker of its kind ({@code ConfigMarkersTest} checks both server config files), and {@link ConfigReload} reports
 * changed values by it.
 */
public enum Reload {
    /** Read where it is used, or applied by {@link ConfigReload} when the file changes. */
    LIVE("(applies live)"),
    /** Only read when the server starts; a change is reported as "needs a restart". */
    RESTART("(restart required)");

    public final String marker;

    Reload(String marker) {
        this.marker = marker;
    }

    /** The comment lines with this kind's marker appended to the last line. */
    public String[] mark(String... lines) {
        if (lines.length == 0) return new String[]{marker};
        var out = lines.clone();
        out[out.length - 1] = out[out.length - 1] + " " + marker;
        return out;
    }

    /** The kind a (built) comment ends with, or null if it has no marker. */
    public static @Nullable Reload of(@Nullable String comment) {
        if (comment == null) return null;
        String trimmed = comment.strip();
        for (var kind : values()) {
            if (trimmed.endsWith(kind.marker)) return kind;
        }
        return null;
    }
}
