package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker13;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Deny by default: walks every registered route and WebSocket message type without a token. */
class RouteAuthTest {
    private static final class StubBridge implements GameBridge {
        @Override
        public Executor serverExecutor() {
            return Runnable::run;
        }

        @Override
        public @Nullable String checkRights(AuthStore.Device device) {
            return null;
        }

        @Override
        public void requestApproval(AuthStore.Pending pending) {
        }

        @Override
        public java.util.concurrent.CompletableFuture<ActionResult> dispatchAction(AuthStore.Device device, ClientMessages.Action action, boolean pinVerified, String ip) {
            fail("dispatchAction must not be reached without authentication");
            return ActionResult.done(null);
        }

        @Override
        public QueryResult dispatchQuery(AuthStore.Device device, ClientMessages.Query query) {
            fail("dispatchQuery must not be reached without authentication");
            return QueryResult.fail("x");
        }

        @Override
        public @Nullable String setLayout(AuthStore.Device device, com.google.gson.@Nullable JsonObject layout) {
            fail("setLayout must not be reached without authentication");
            return null;
        }

        @Override
        public void onPinLockout(AuthStore.Device device, String ip) {
        }

        @Override
        public String mainframeName(UUID mainframeId) {
            return "Test";
        }
    }

    static WebInstance instance(boolean allowInternet) {
        var auth = new AuthStore(System::currentTimeMillis, () -> {
        });
        return new WebInstance(new StubBridge(), auth, null, List.of(), allowInternet, "http://localhost:8787", 8787);
    }

    private static FullHttpResponse request(WebInstance instance, HttpMethod method, String path, @Nullable String authorization) {
        var channel = new EmbeddedChannel(new HttpHandler(instance));
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, path, Unpooled.EMPTY_BUFFER);
        req.headers().set(HttpHeaderNames.HOST, "localhost:8787");
        if (authorization != null) req.headers().set(HttpHeaderNames.AUTHORIZATION, authorization);
        channel.writeInbound(req);
        FullHttpResponse response = channel.readOutbound();
        assertNotNull(response, method + " " + path);
        return response;
    }

    @Test
    void everyProtectedRouteNeedsAToken() {
        var instance = instance(true);
        int protectedRoutes = 0;
        for (var route : Router.ROUTES) {
            var response = request(instance, route.method(), route.example(), null);
            if (route.access() == Router.Access.DEVICE) {
                protectedRoutes++;
                assertEquals(401, response.status().code(), route.id() + " without token");
                assertEquals(401, request(instance, route.method(), route.example(), "Bearer invalid").status().code(), route.id() + " with bad token");
            } else {
                assertNotEquals(500, response.status().code(), route.id().toString());
            }
            assertEquals("nosniff", response.headers().get("X-Content-Type-Options"));
            assertEquals("DENY", response.headers().get("X-Frame-Options"));
            assertTrue(response.headers().get("Content-Security-Policy").contains("default-src 'self'"));
            assertNull(response.headers().get("Access-Control-Allow-Origin"), "no CORS");
        }
        assertTrue(protectedRoutes > 0);
    }

    @Test
    void unknownApiPathsAndMethodsAreRejected() {
        var instance = instance(true);
        assertEquals(404, request(instance, HttpMethod.GET, "/api/secret", null).status().code());
        assertEquals(404, request(instance, HttpMethod.GET, "/api", null).status().code());
        assertEquals(405, request(instance, HttpMethod.DELETE, "/api/mainframes", null).status().code());
        assertEquals(405, request(instance, HttpMethod.POST, "/", null).status().code());
        var noStore = request(instance, HttpMethod.GET, "/api/mainframes", null);
        assertEquals("no-store", noStore.headers().get(HttpHeaderNames.CACHE_CONTROL));
    }

    @Test
    void publicPairEndpointsBehave() {
        var instance = instance(true);
        assertEquals(400, request(instance, HttpMethod.POST, "/api/pair", null).status().code(), "empty body");
        assertEquals(404, request(instance, HttpMethod.GET, "/api/pair/AAAAAAAAAAAAAAAAAAAAAAAA", null).status().code(), "unknown pairing id");
        var channel = new EmbeddedChannel(new HttpHandler(instance));
        var body = "{\"code\":\"ABCD-EFGH\",\"name\":\"Phone\"}".getBytes(StandardCharsets.UTF_8);
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/pair", Unpooled.wrappedBuffer(body));
        channel.writeInbound(req);
        channel.runPendingTasks();
        FullHttpResponse response = channel.readOutbound();
        assertEquals(403, response.status().code(), "wrong code -> 403");
    }

    @Test
    void nonLanClientsAreRejectedByDefault() {
        // EmbeddedChannel has no IP address, so it counts as "not LAN".
        var response = request(instance(false), HttpMethod.GET, "/", null);
        assertEquals(403, response.status().code());
        String body = response.content().toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"error\":\"lan_only\""), body);
        assertTrue(body.contains("webAllowInternet"));
    }

    @Test
    void webSocketMessagesNeedAuthentication() {
        var instance = instance(true);
        var handshaker = new WebSocketServerHandshaker13("ws://localhost/ws/client", null, WebSocketDecoderConfig.newBuilder().build());
        var channel = new EmbeddedChannel(new WebSocketSession(instance, handshaker, "127.0.0.1", null));
        channel.writeInbound(new TextWebSocketFrame("{\"t\":\"action\",\"uplinkId\":\"" + UUID.randomUUID() + "\",\"id\":\"x\",\"value\":true}"));
        Object out = channel.readOutbound();
        var close = assertInstanceOf(CloseWebSocketFrame.class, out);
        assertEquals(4401, close.statusCode());

        var second = new EmbeddedChannel(new WebSocketSession(instance, handshaker, "127.0.0.1", null));
        second.writeInbound(new TextWebSocketFrame("{\"t\":\"auth\",\"token\":\"wrong\"}"));
        TextWebSocketFrame error = second.readOutbound();
        assertTrue(error.text().contains("unauthorized"));
        assertInstanceOf(CloseWebSocketFrame.class, second.readOutbound());
    }

    @Test
    void originMustMatchHost() {
        assertTrue(HttpHandler.originMatchesHost(null, "localhost:8787"));
        assertTrue(HttpHandler.originMatchesHost("http://localhost:8787", "localhost:8787"));
        assertTrue(HttpHandler.originMatchesHost("https://dash.example.com", "dash.example.com"));
        assertFalse(HttpHandler.originMatchesHost("http://evil.example", "localhost:8787"));
        assertFalse(HttpHandler.originMatchesHost("http://localhost:9999", "localhost:8787"));
        assertFalse(HttpHandler.originMatchesHost("null", "localhost:8787"));
    }
}
