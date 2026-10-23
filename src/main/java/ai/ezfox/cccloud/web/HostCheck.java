package ai.ezfox.cccloud.web;

import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Host header allow-list against DNS rebinding (security review L5): a web page on an attacker's domain that resolves
 * to the server's LAN address sends its own name as Host and is refused. Allowed: IP literals, {@code localhost},
 * mDNS/LAN names ({@code *.local}, {@code *.lan}, {@code *.home.arpa}), the host of {@code publicUrl} and the config
 * list {@code webAllowedHosts}. Pure Java, unit tested.
 */
public final class HostCheck {
    private HostCheck() {
    }

    /** The host part of a Host header (without port), lower case, or null if malformed. */
    static @Nullable String hostOf(@Nullable String header) {
        if (header == null || header.isBlank() || header.length() > 255) return null;
        String h = header.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[")) {
            int end = h.indexOf(']');
            return end < 0 ? null : h.substring(0, end + 1);
        }
        int colon = h.lastIndexOf(':');
        if (colon >= 0 && h.indexOf(':') == colon) h = h.substring(0, colon);
        return h.isEmpty() ? null : h;
    }

    public static boolean allowed(@Nullable String header, Set<String> extra) {
        String host = hostOf(header);
        if (host == null) return false;
        if (host.equals("localhost") || host.endsWith(".localhost")) return true;
        if (host.startsWith("[") || IpUtil.literal(host) != null) return true; // IP literals cannot be rebound
        if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")) return true;
        for (String e : extra) if (host.equals(e.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    /** The host of a configured URL ({@code https://dash.example.com/} → {@code dash.example.com}), or null. */
    public static @Nullable String hostOfUrl(@Nullable String url) {
        if (url == null || url.isBlank()) return null;
        try {
            var h = new java.net.URI(url.trim()).getHost();
            return h == null ? null : h.toLowerCase(Locale.ROOT);
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }
}
