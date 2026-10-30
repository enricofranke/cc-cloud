package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.publicapi.ApiKeys;
import ai.ezfox.cccloud.publicapi.ApiScope;
import ai.ezfox.cccloud.publicapi.ApiSettings;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * v0.10 public API, HTTP layer: off by default (the routes do not exist), keys only in the Authorization header (never in
 * the URL), device tokens and API keys never cross over, expiry, IP allow-list, per-key and per-IP rate limits with
 * Retry-After, body limit, CORS only for listed origins, and the OpenAPI description.
 */
class ApiHttpTest {
    static final String MF = "00000000-0000-0000-0000-000000000000";
    final long[] now = {System.currentTimeMillis()};
    final TestBridge bridge = new TestBridge();
    final AuthStore auth = new AuthStore(System::currentTimeMillis, () -> {
    });
    final WebInstance instance = new WebInstance(bridge, auth, new AccessStore(System::currentTimeMillis, () -> {
    }), null, List.of(), true, "http://localhost:8787", 8787);
    final ApiKeys keys = new ApiKeys(() -> now[0], () -> {
    });

    ApiHttpTest() {
        instance.apiKeys = keys;
    }

    static ApiSettings on(int perKey, int perIp, int maxBody, List<String> cors) {
        return new ApiSettings(true, 5, perKey, perIp, maxBody, cors, true, 2);
    }

    String key(String name, List<String> ips, long expiresIn) {
        return keys.create(UUID.randomUUID(), "Steve", UUID.fromString(MF), name, EnumSet.allOf(ApiScope.class), expiresIn, ips).secret();
    }

    FullHttpResponse request(HttpMethod method, String path, @Nullable String authorization, @Nullable String origin, @Nullable String body) {
        var channel = new EmbeddedChannel(new HttpHandler(instance));
        var content = body == null ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8));
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, path, content);
        req.headers().set(HttpHeaderNames.HOST, "localhost:8787");
        if (authorization != null) req.headers().set(HttpHeaderNames.AUTHORIZATION, authorization);
        if (origin != null) req.headers().set(HttpHeaderNames.ORIGIN, origin);
        channel.writeInbound(req);
        channel.runPendingTasks();
        FullHttpResponse response = channel.readOutbound();
        assertNotNull(response, method + " " + path);
        return response;
    }

    static String error(FullHttpResponse response) {
        String body = response.content().toString(StandardCharsets.UTF_8);
        int at = body.indexOf("\"error\":\"");
        return at < 0 ? "" : body.substring(at + 9, body.indexOf('"', at + 9));
    }

    @Test
    void offByDefaultTheRoutesDoNotExist() {
        assertFalse(instance.api.enabled(), "apiEnabled=false by default");
        String secret = key("bot", List.of(), 0);
        for (var route : Router.ROUTES) {
            if (!route.id().isPublicApi()) continue;
            var response = request(route.method(), route.example(), "Bearer " + secret, null, null);
            assertEquals(404, response.status().code(), route.id() + " does not exist while the API is off");
            assertEquals("not_found", error(response));
        }
        assertEquals(404, request(HttpMethod.GET, "/api/v1/anything", "Bearer " + secret, null, null).status().code());
        assertTrue(bridge.dispatched.isEmpty());
    }

    @Test
    void keysOnlyInTheHeader() {
        instance.api = on(60, 120, 2048, List.of());
        String secret = key("bot", List.of(), 0);
        for (String param : new String[]{"key", "api_key", "token", "access_token", "KEY"}) {
            var response = request(HttpMethod.GET, "/api/v1/me?" + param + "=" + secret, null, null, null);
            assertEquals(400, response.status().code(), param);
            assertEquals("key_in_query", error(response));
        }
        assertEquals(400, request(HttpMethod.GET, "/api/v1/me?key=x", "Bearer " + secret, null, null).status().code(), "refused even with a header");
        assertEquals(401, request(HttpMethod.GET, "/api/v1/me", null, null, null).status().code());
        assertTrue(bridge.dispatched.isEmpty(), "a key in the URL never reaches the game");
        assertEquals(200, request(HttpMethod.GET, "/api/v1/me", "Bearer " + secret, null, null).status().code());
        assertEquals(List.of("api:API_ME:bot"), bridge.dispatched);
    }

    @Test
    void deviceTokensAndApiKeysNeverCrossOver() {
        instance.api = on(60, 120, 2048, List.of());
        var pending = auth.pair(auth.accountCode(UUID.randomUUID()).code(), "Laptop", "127.0.0.1");
        String deviceToken = auth.approve(pending.id(), pending.account(), "Alex", true).token();
        assertEquals(401, request(HttpMethod.GET, "/api/v1/me", "Bearer " + deviceToken, null, null).status().code(), "no device tokens on /api/v1");
        String secret = key("bot", List.of(), 0);
        assertEquals(401, request(HttpMethod.GET, "/api/mainframes", "Bearer " + secret, null, null).status().code(), "no API keys on the dashboard API");
        assertEquals(401, request(HttpMethod.GET, "/api/v1/me", "Bearer cck_" + "A".repeat(43), null, null).status().code(), "unknown key");
        assertEquals(401, request(HttpMethod.GET, "/api/v1/me", "Bearer " + secret + ", Bearer " + key("two", List.of(), 0), null, null).status().code(),
            "exactly one key");
    }

    @Test
    void expiryAndIpAllowList() {
        instance.api = on(60, 120, 2048, List.of());
        String day = key("day", List.of(), 86_400);
        assertEquals(200, request(HttpMethod.GET, "/api/v1/me", "Bearer " + day, null, null).status().code());
        now[0] += 86_400_001L;
        var expired = request(HttpMethod.GET, "/api/v1/me", "Bearer " + day, null, null);
        assertEquals(401, expired.status().code());
        assertEquals("expired", error(expired));
        // The embedded channel has no address ("unknown"): a key limited to an IP range refuses it.
        String limited = key("lan", List.of("192.168.0.0/16"), 0);
        var refused = request(HttpMethod.GET, "/api/v1/me", "Bearer " + limited, null, null);
        assertEquals(403, refused.status().code());
        assertEquals("ip_not_allowed", error(refused));
    }

    @Test
    void rateLimitsAndBodySize() {
        instance.api = on(2, 120, 256, List.of());
        String secret = key("bot", List.of(), 0);
        assertEquals(200, request(HttpMethod.GET, "/api/v1/me", "Bearer " + secret, null, null).status().code());
        assertEquals(200, request(HttpMethod.GET, "/api/v1/me", "Bearer " + secret, null, null).status().code());
        var limited = request(HttpMethod.GET, "/api/v1/me", "Bearer " + secret, null, null);
        assertEquals(429, limited.status().code(), "2 per minute per key");
        assertEquals("30", limited.headers().get(HttpHeaderNames.RETRY_AFTER));
        assertEquals(200, request(HttpMethod.GET, "/api/v1/me", "Bearer " + key("other", List.of(), 0), null, null).status().code(), "per key");
        instance.api = on(60, 1, 256, List.of());
        instance.apiIpLimits.clear();
        String fresh = key("fresh", List.of(), 0);
        assertEquals(200, request(HttpMethod.GET, "/api/v1/me", "Bearer " + fresh, null, null).status().code());
        var ip = request(HttpMethod.GET, "/api/v1/me", "Bearer " + fresh, null, null);
        assertEquals(429, ip.status().code(), "1 per minute per IP");
        assertEquals("60", ip.headers().get(HttpHeaderNames.RETRY_AFTER));
        instance.api = on(60, 120, 256, List.of());
        instance.apiIpLimits.clear();
        var big = request(HttpMethod.POST, "/api/v1/mainframes/" + MF + "/sections/" + MF + "/widgets/lamp", "Bearer " + key("big", List.of(), 0), null,
            "{\"value\":\"" + "x".repeat(300) + "\"}");
        assertEquals(413, big.status().code(), "apiMaxBody");
    }

    @Test
    void corsOnlyForListedOrigins() {
        instance.api = on(60, 120, 2048, List.of("https://bot.example.org"));
        String secret = key("bot", List.of(), 0);
        var none = request(HttpMethod.GET, "/api/v1/me", "Bearer " + secret, null, null);
        assertNull(none.headers().get("Access-Control-Allow-Origin"));
        assertEquals("same-origin", none.headers().get("Cross-Origin-Resource-Policy"));
        var foreign = request(HttpMethod.GET, "/api/v1/me", "Bearer " + secret, "https://evil.example", null);
        assertNull(foreign.headers().get("Access-Control-Allow-Origin"), "unlisted origins get no CORS headers");
        var listed = request(HttpMethod.GET, "/api/v1/me", "Bearer " + secret, "https://bot.example.org", null);
        assertEquals("https://bot.example.org", listed.headers().get("Access-Control-Allow-Origin"));
        assertEquals("Origin", listed.headers().get(HttpHeaderNames.VARY));
        assertEquals("cross-origin", listed.headers().get("Cross-Origin-Resource-Policy"));
        assertEquals("nosniff", listed.headers().get("X-Content-Type-Options"));
        var preflight = request(HttpMethod.OPTIONS, "/api/v1/me", null, "https://bot.example.org", null);
        assertEquals(204, preflight.status().code());
        assertEquals("GET, POST", preflight.headers().get("Access-Control-Allow-Methods"));
        assertTrue(preflight.headers().get("Access-Control-Allow-Headers").contains("Authorization"));
        var badPreflight = request(HttpMethod.OPTIONS, "/api/v1/me", null, "https://evil.example", null);
        assertNull(badPreflight.headers().get("Access-Control-Allow-Origin"));
        assertTrue(ai.ezfox.cccloud.publicapi.ApiCors.validOrigin("http://localhost:3000"));
        assertFalse(ai.ezfox.cccloud.publicapi.ApiCors.validOrigin("https://bot.example.org/"));
        assertFalse(ai.ezfox.cccloud.publicapi.ApiCors.validOrigin("https://user@bot.example.org"));
        assertFalse(ai.ezfox.cccloud.publicapi.ApiCors.validOrigin("ftp://bot.example.org"));
    }

    @Test
    void openApiDescription() {
        assertEquals(404, request(HttpMethod.GET, "/api/v1/openapi.json", null, null, null).status().code(), "not while the API is off");
        instance.api = on(60, 120, 2048, List.of());
        var spec = request(HttpMethod.GET, "/api/v1/openapi.json", null, null, null);
        assertEquals(200, spec.status().code());
        String body = spec.content().toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"openapi\": \"3.0.3\""), body.substring(0, Math.min(200, body.length())));
        for (var route : Router.ROUTES) {
            if (route.access() != Router.Access.API_KEY) continue;
            String path = route.example().replace(MF, "{mainframeId}").replace("00000000-0000-0000-0000-000000000001", "{sectionId}")
                .replace("/widgets/lamp", "/widgets/{widgetId}").replace("/0a1b2c3d/", "/{sceneId}/");
            assertTrue(body.contains("\"" + path + "\""), "documented: " + path);
        }
    }
}
