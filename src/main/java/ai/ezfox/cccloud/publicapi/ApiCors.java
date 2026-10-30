package ai.ezfox.cccloud.publicapi;

import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * CORS of the public API (docs/SPEC.md v0.10): off unless the request's {@code Origin} is listed exactly in config
 * {@code apiCorsOrigins}. Pure Java, unit tested.
 */
public final class ApiCors {
    public static final String ALLOW_METHODS = "GET, POST";
    public static final String ALLOW_HEADERS = "Authorization, Content-Type";
    public static final int MAX_AGE = 600;

    private ApiCors() {
    }

    /** {@code http(s)://host[:port]} without path, user info, query or fragment. */
    public static boolean validOrigin(@Nullable String origin) {
        if (origin == null || origin.isEmpty() || origin.length() > 200 || origin.endsWith("/")) return false;
        try {
            var uri = new URI(origin);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null && uri.getRawUserInfo() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty()) && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    /** The value for {@code Access-Control-Allow-Origin}, or null (no CORS headers at all). */
    public static @Nullable String allowOrigin(@Nullable String origin, List<? extends String> allowed) {
        if (origin == null || !validOrigin(origin)) return null;
        for (var a : allowed) if (a.equalsIgnoreCase(origin)) return origin;
        return null;
    }
}
