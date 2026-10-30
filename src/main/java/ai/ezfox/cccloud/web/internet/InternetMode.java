package ai.ezfox.cccloud.web.internet;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The config value {@code internetAccess} (and {@code /cccloud internet <mode>}): how the dashboard is reachable from the
 * internet. Each mode maps to the advanced {@code [web]} keys ({@link InternetSettings#resolve}).
 */
public enum InternetMode {
    /** LAN only (default); the advanced keys decide everything, as before this setting existed. */
    OFF,
    /** Plain HTTP over the internet: insecure, with the extra protections of {@link InternetGuard}. */
    HTTP;

    /** The config spelling: {@code off}, {@code http}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable InternetMode parse(@Nullable String text) {
        if (text == null) return null;
        String s = text.trim().toLowerCase(Locale.ROOT);
        for (var mode : values()) if (mode.id().equals(s)) return mode;
        return null;
    }
}
