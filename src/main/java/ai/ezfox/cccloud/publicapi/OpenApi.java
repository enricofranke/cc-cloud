package ai.ezfox.cccloud.publicapi;

import ai.ezfox.cccloud.CloudLog;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * The OpenAPI 3 description of the public API v1 ({@code GET /api/v1/openapi.json}), bundled as
 * {@code /cccloud_api/openapi.json} in the jar and read once.
 */
public final class OpenApi {
    public static final String RESOURCE = "/cccloud_api/openapi.json";
    private static volatile byte @Nullable [] cached;

    private OpenApi() {
    }

    public static byte @Nullable [] bytes() {
        var bytes = cached;
        if (bytes != null) return bytes;
        try (var in = OpenApi.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return null;
            bytes = in.readAllBytes();
            cached = bytes;
            return bytes;
        } catch (IOException e) {
            CloudLog.LOGGER.warn("[cccloud] Could not read {}", RESOURCE, e);
            return null;
        }
    }
}
