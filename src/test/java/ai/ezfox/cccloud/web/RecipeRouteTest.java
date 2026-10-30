package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** /api/recipes: public like the docs and icons, read-only, rate limited per IP, revalidated with an ETag. */
class RecipeRouteTest {
    private static final String BODY = "{\"v\":1,\"recipes\":[],\"removed\":[\"cccloud:cloud_sensor\"],\"inactive\":[],\"icons\":{},\"names\":{},\"truncated\":false}";

    private static final class RecipeBridge extends TestBridge {
        int calls;

        @Override
        public GameBridge.@Nullable RecipesJson recipes() {
            calls++;
            return new GameBridge.RecipesJson(BODY.getBytes(StandardCharsets.UTF_8), "\"r1234\"");
        }
    }

    private static WebInstance instance(GameBridge bridge) {
        var auth = new AuthStore(System::currentTimeMillis, () -> {
        });
        return new WebInstance(bridge, auth, null, List.of(), true, "http://localhost:8787", 8787);
    }

    private static FullHttpResponse get(WebInstance instance, @Nullable String ifNoneMatch) {
        var channel = new EmbeddedChannel(new HttpHandler(instance));
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/recipes", Unpooled.EMPTY_BUFFER);
        req.headers().set(HttpHeaderNames.HOST, "localhost:8787");
        if (ifNoneMatch != null) req.headers().set(HttpHeaderNames.IF_NONE_MATCH, ifNoneMatch);
        channel.writeInbound(req);
        channel.runPendingTasks();
        FullHttpResponse response = channel.readOutbound();
        assertNotNull(response);
        return response;
    }

    @Test
    void publicReadOnlyRoute() {
        var route = Router.match(HttpMethod.GET, "/api/recipes");
        assertNotNull(route);
        assertEquals(Router.Id.RECIPES, route.id());
        assertEquals(Router.Access.PUBLIC, route.access());
        assertNull(AccessGate.routePerm(Router.Id.RECIPES), "no mainframe permission: game data, like the docs");
        assertNull(Router.match(HttpMethod.POST, "/api/recipes"), "read-only");
        assertNull(Router.match(HttpMethod.GET, "/api/recipes/cccloud:cloud_core"));
        assertEquals(405, RouteAuthTest.request(RouteAuthTest.instance(true), HttpMethod.POST, "/api/recipes", null).status().code());
    }

    @Test
    void servesTheCatalogWithAnEtag() {
        var bridge = new RecipeBridge();
        var instance = instance(bridge);
        var response = get(instance, null);
        assertEquals(200, response.status().code());
        assertEquals(BODY, response.content().toString(StandardCharsets.UTF_8));
        assertEquals("application/json; charset=utf-8", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals("\"r1234\"", response.headers().get(HttpHeaderNames.ETAG));
        assertEquals("no-cache", response.headers().get(HttpHeaderNames.CACHE_CONTROL), "a /reload can change it any time");
        assertEquals("nosniff", response.headers().get("X-Content-Type-Options"));
        assertNull(response.headers().get("Access-Control-Allow-Origin"), "no CORS");

        var again = get(instance, "\"r1234\"");
        assertEquals(304, again.status().code());
        assertEquals(0, again.content().readableBytes());
        assertEquals(2, bridge.calls);
    }

    @Test
    void notAvailableWithoutAGame() {
        // RouteAuthTest's bridge has no recipes (like a server that cannot build them): the dashboard falls back.
        var response = RouteAuthTest.request(RouteAuthTest.instance(true), HttpMethod.GET, "/api/recipes", null);
        assertEquals(404, response.status().code());
        assertTrue(response.content().toString(StandardCharsets.UTF_8).contains("not_available"));
    }

    @Test
    void rateLimitedPerIp() {
        var instance = instance(new RecipeBridge());
        int limited = 0;
        for (int i = 0; i < HttpHandler.RECIPES_RATE + 10; i++) if (get(instance, null).status().code() == 429) limited++;
        assertTrue(limited >= 5, "requests over 60/min get 429, got " + limited);
    }

    @Test
    void lanOnlyRuleStillApplies() {
        var response = RouteAuthTest.request(RouteAuthTest.instance(false), HttpMethod.GET, "/api/recipes", null);
        assertEquals(403, response.status().code());
    }
}
