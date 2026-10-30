package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.publicapi.ApiCors;
import ai.ezfox.cccloud.publicapi.ApiKeys;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * Netty side of the public API v1 (docs/SPEC.md v0.10): a key in the URL is refused, the Bearer key is looked up
 * (hash, constant-time compare), expiry, IP allow-list, per-IP and per-key rate limits and the body size are checked,
 * then the route runs on the server thread ({@link GameBridge#apiV1}) with the owner's live rights. The caller
 * ({@link HttpHandler}) has already applied the LAN rule, IP bans, the Host allow-list and answered 404 while the API is
 * switched off.
 */
final class ApiHandler {
    /** Query parameters that look like credentials: refused, so keys never end up in URLs and logs. */
    static final Set<String> KEY_PARAMS = Set.of("key", "api_key", "apikey", "token", "access_token", "auth");
    /** Item searches per key: 5 per second. */
    static final int SEARCH_RATE = 5;

    private ApiHandler() {
    }

    static void handle(HttpHandler http, WebInstance instance, ChannelHandlerContext ctx, FullHttpRequest req, Router.Route route, String ip) {
        var settings = instance.api;
        String origin = ApiCors.allowOrigin(req.headers().get(HttpHeaderNames.ORIGIN), settings.corsOrigins());
        var decoder = new QueryStringDecoder(req.uri());
        for (String name : decoder.parameters().keySet()) {
            if (KEY_PARAMS.contains(name.toLowerCase(Locale.ROOT))) {
                respond(http, ctx, req, 400, error("key_in_query"), origin, 0);
                return;
            }
        }
        var tokens = HttpHandler.bearerTokens(req);
        if (tokens.size() != 1 || !ApiKeys.looksLikeKey(tokens.getFirst())) {
            respond(http, ctx, req, 401, error("unauthorized"), origin, 0);
            return;
        }
        if (!bucket(instance, instance.apiIpLimits, ip, false).tryAcquire()) {
            respond(http, ctx, req, 429, error("rate_limited"), origin, retryAfter(settings.perIpPerMinute()));
            return;
        }
        var check = instance.apiKeys.authenticate(tokens.getFirst());
        if (check.status() != ApiKeys.Status.OK || check.key() == null) {
            respond(http, ctx, req, 401, error(check.status() == ApiKeys.Status.EXPIRED ? "expired" : "unauthorized"), origin, 0);
            return;
        }
        var key = check.key();
        if (!ApiKeys.ipAllowed(key, ip)) {
            respond(http, ctx, req, 403, error("ip_not_allowed"), origin, 0);
            return;
        }
        if (!bucket(instance, instance.apiKeyLimits, key.hash(), true).tryAcquire()) {
            respond(http, ctx, req, 429, error("rate_limited"), origin, retryAfter(settings.perKeyPerMinute()));
            return;
        }
        if (req.content().readableBytes() > settings.maxBody()) {
            respond(http, ctx, req, 413, error("too_large"), origin, 0);
            return;
        }
        if (route.id() == Router.Id.API_ITEMS) {
            if (instance.apiSearchLimits.size() > 10_000) instance.apiSearchLimits.clear();
            if (!instance.apiSearchLimits.computeIfAbsent(key.hash(), k -> new TokenBucket(SEARCH_RATE, SEARCH_RATE)).tryAcquire()) {
                respond(http, ctx, req, 429, error("rate_limited"), origin, 1);
                return;
            }
        }
        if (route.id() == Router.Id.API_EVENTS) {
            ApiEventStream.open(http, instance, ctx, req, key, ip, origin);
            return;
        }
        String path = decoder.path();
        var query = new LinkedHashMap<String, String>();
        for (var e : decoder.parameters().entrySet()) {
            if (query.size() >= 8) break;
            String value = e.getValue().isEmpty() ? "" : e.getValue().getFirst();
            if (e.getKey().length() <= 32 && value.length() <= 256) query.put(e.getKey(), value);
        }
        String body = req.content().toString(StandardCharsets.UTF_8);
        CompletableFuture.supplyAsync(() -> instance.bridge.apiV1(key, route.id(), path, Map.copyOf(query), body, ip), instance.bridge.serverExecutor())
            .orTimeout(5, TimeUnit.SECONDS)
            .whenComplete((response, err) -> ctx.executor().execute(() -> {
                if (err != null || response == null) {
                    if (err != null && !(err instanceof java.util.concurrent.TimeoutException)) CloudLog.LOGGER.warn("[cccloud] Error handling API {}", route.id(), err);
                    respond(http, ctx, req, 503, error("unavailable"), origin, 0);
                } else {
                    respond(http, ctx, req, response.status(), response.body(), origin, response.status() == 429 ? 2 : 0);
                }
            }));
    }

    /** {@code OPTIONS /api/v1/…}: CORS preflight, only for listed origins (others get no CORS headers). */
    static void preflight(HttpHandler http, WebInstance instance, ChannelHandlerContext ctx, FullHttpRequest req) {
        String origin = ApiCors.allowOrigin(req.headers().get(HttpHeaderNames.ORIGIN), instance.api.corsOrigins());
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT, Unpooled.EMPTY_BUFFER);
        if (origin != null) {
            cors(response, origin);
            response.headers().set("Access-Control-Allow-Methods", ApiCors.ALLOW_METHODS);
            response.headers().set("Access-Control-Allow-Headers", ApiCors.ALLOW_HEADERS);
            response.headers().set("Access-Control-Max-Age", String.valueOf(ApiCors.MAX_AGE));
        }
        http.write(ctx, req, response);
    }

    /** {@code GET /api/v1/openapi.json}: the API description (public while the API is enabled). */
    static void openApi(HttpHandler http, WebInstance instance, ChannelHandlerContext ctx, FullHttpRequest req) {
        byte[] spec = ai.ezfox.cccloud.publicapi.OpenApi.bytes();
        if (spec == null) {
            http.sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("not_found"));
            return;
        }
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(spec));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        String origin = ApiCors.allowOrigin(req.headers().get(HttpHeaderNames.ORIGIN), instance.api.corsOrigins());
        if (origin != null) cors(response, origin);
        http.write(ctx, req, response);
    }

    static void respond(HttpHandler http, ChannelHandlerContext ctx, FullHttpRequest req, int status, JsonObject body, @Nullable String origin, long retryAfter) {
        FullHttpResponse response = HttpHandler.json(HttpResponseStatus.valueOf(status), body);
        if (origin != null) cors(response, origin);
        if (retryAfter > 0) response.headers().set(HttpHeaderNames.RETRY_AFTER, String.valueOf(retryAfter));
        http.write(ctx, req, response);
    }

    /** CORS headers for an allowed origin; the resource may then be read cross-origin. */
    static void cors(HttpResponse response, String origin) {
        response.headers().set("Access-Control-Allow-Origin", origin);
        response.headers().set(HttpHeaderNames.VARY, "Origin");
    }

    /**
     * The per-key or per-IP bucket with the limit of the current {@code [api]} config (read fresh, not from the request's
     * snapshot): after a reload every bucket in use takes the new limit on its next request and keeps what was used.
     */
    private static TokenBucket bucket(WebInstance instance, java.util.concurrent.ConcurrentHashMap<String, TokenBucket> map, String key, boolean perKey) {
        if (map.size() > 10_000) map.clear();
        int perMinute = perKey ? instance.api.perKeyPerMinute() : instance.api.perIpPerMinute();
        var bucket = map.computeIfAbsent(key, k -> new TokenBucket(perMinute, perMinute / 60.0));
        bucket.reconfigure(perMinute, perMinute / 60.0);
        return bucket;
    }

    /** Seconds until a per-minute bucket has a token again. */
    static long retryAfter(int perMinute) {
        return Math.max(1, (long) Math.ceil(60.0 / Math.max(1, perMinute)));
    }

    static JsonObject error(String code) {
        var json = new JsonObject();
        json.addProperty("error", code);
        return json;
    }
}
