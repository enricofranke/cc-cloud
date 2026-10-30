package ai.ezfox.cccloud.web.internet;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.web.IpUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;

/**
 * "What is my IP": the server's public IPv4 address, for the internet link shown in game. Asked only in internet modes
 * without {@code internetDomain}: when {@code /cccloud internet} runs and when the dashboard starts. Plain-text services,
 * tried in order; the answer must be a public IP literal. Nothing is sent but a plain GET.
 */
public final class PublicIp {
    /** Services that answer with the caller's IPv4 address as plain text. */
    public static final List<String> SERVICES = List.of("https://api.ipify.org", "https://checkip.amazonaws.com", "https://ipv4.icanhazip.com");
    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "cccloud-public-ip");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile @Nullable String last;

    private PublicIp() {
    }

    /** The last address found (until the server stops), or null. */
    public static @Nullable String last() {
        return last;
    }

    /** No lookups in GameTests and dev runs that switch them off. */
    public static boolean enabled() {
        return !Boolean.getBoolean("cccloud.gametest") && !Boolean.getBoolean("cccloud.noPublicIpLookup");
    }

    /** Looks the address up off the calling thread; completes with null when no service answered with a public IP. */
    public static CompletableFuture<@Nullable String> lookup() {
        if (!enabled()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
            for (String service : SERVICES) {
                try {
                    var request = HttpRequest.newBuilder(URI.create(service)).timeout(Duration.ofSeconds(5)).header("User-Agent", "CC-Cloud").GET().build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    String ip = response.statusCode() == 200 ? parse(new String(response.body(), StandardCharsets.US_ASCII)) : null;
                    if (ip != null) {
                        last = ip;
                        return ip;
                    }
                } catch (Exception e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    CloudLog.LOGGER.debug("[cccloud] Public IP lookup at {} failed: {}", service, e.toString());
                }
            }
            return null;
        }, EXECUTOR);
    }

    /** The answer of a service: one public IPv4 literal (whitespace around it allowed), else null. */
    public static @Nullable String parse(@Nullable String body) {
        if (body == null || body.length() > 64) return null;
        String s = body.trim();
        if (!s.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}") || IpUtil.literal(s) == null || IpUtil.isLan(s)) return null;
        if (s.startsWith("0.") || s.startsWith("224.") || Integer.parseInt(s.substring(0, s.indexOf('.'))) >= 224) return null;
        return s;
    }
}
