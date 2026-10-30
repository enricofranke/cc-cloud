package ai.ezfox.cccloud.publicapi;

import java.util.List;

/**
 * The server config of the public API ({@code [api]}, docs/SPEC.md v0.10), as one immutable snapshot the web layer
 * reads on its own threads (refreshed by the game; tests build their own).
 *
 * @param perKeyPerMinute requests per minute per key
 * @param perIpPerMinute  requests per minute per client IP
 * @param maxBody         largest JSON body in bytes
 * @param corsOrigins     exact origins that get CORS headers
 * @param events          the Server-Sent Events stream is available
 * @param streamsPerKey   open event streams per key (at most {@link #MAX_STREAMS} in total)
 */
public record ApiSettings(boolean enabled, int keysPerPlayer, int perKeyPerMinute, int perIpPerMinute, int maxBody, List<String> corsOrigins, boolean events,
                          int streamsPerKey) {
    public static final int MAX_STREAMS = 32;
    public static final ApiSettings OFF = new ApiSettings(false, 5, 60, 120, 2048, List.of(), true, 2);

    public ApiSettings {
        corsOrigins = List.copyOf(corsOrigins);
    }
}
