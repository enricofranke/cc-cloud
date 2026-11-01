package ai.ezfox.cccloud.reports;

import ai.ezfox.cccloud.push.PushFormat;
import ai.ezfox.cccloud.push.PushHttp;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The HTTPS POST to the report service, through the push client: every address the host resolves to must be public, the
 * connection goes to the checked address, the certificate and host name are verified, redirects are not followed and
 * the timeouts are 5 seconds. Only a content type and a fixed user agent go along: no version, no cookie, no id.
 */
public final class ReportHttp implements ReportEngine.Transport {
    public static final String BASE = "https://reports.cccloud.dev/v1/";
    private static final Map<String, String> HEADERS = Map.of("Content-Type", "application/json", "User-Agent", "CC-Cloud");

    private @Nullable PushHttp http;

    /** The client and its event loop thread only exist once something is actually sent. */
    private synchronized PushHttp client() {
        if (http == null) http = new PushHttp();
        return http;
    }

    @Override
    public boolean post(String channel, byte[] body) {
        return client().post(BASE + channel, new PushFormat.Request(HEADERS, body), Set.of()).ok();
    }

    public synchronized void shutdown() {
        if (http != null) http.shutdown();
        http = null;
    }
}
