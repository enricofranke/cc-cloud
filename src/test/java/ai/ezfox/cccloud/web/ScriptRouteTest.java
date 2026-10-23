package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Script import over HTTP: PIN, rate limit and size checks happen before anything reaches the game. */
class ScriptRouteTest {
    final UUID mainframe = UUID.randomUUID();
    final UUID uplink = UUID.randomUUID();
    final UUID owner = UUID.randomUUID();
    final TestBridge bridge = new TestBridge();
    final AuthStore auth = new AuthStore(System::currentTimeMillis, () -> {
    });
    final WebInstance instance = new WebInstance(bridge, auth, null, List.of(), true, "http://localhost:8787", 8787);
    final String base = "/api/mainframes/" + mainframe + "/computers/" + uplink;

    @AfterEach
    void cleanup() {
        instance.pinExecutor.shutdownNow();
        instance.storeExecutor.shutdownNow();
    }

    String token() {
        var pending = auth.pair(auth.currentCode(mainframe, owner).code(), "Laptop", "127.0.0.1");
        return auth.approve(pending.id(), owner, "Owner", true).token();
    }

    FullHttpResponse send(HttpMethod method, String path, String token, byte[] body, @Nullable String pin) throws InterruptedException {
        var channel = new EmbeddedChannel(new HttpHandler(instance));
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, path, Unpooled.wrappedBuffer(body));
        req.headers().set(HttpHeaderNames.HOST, "localhost:8787");
        req.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + token);
        if (pin != null) req.headers().set("X-Cloud-Pin", pin);
        channel.writeInbound(req);
        for (int i = 0; i < 200; i++) {
            channel.runPendingTasks();
            FullHttpResponse response = channel.readOutbound();
            if (response != null) return response;
            Thread.sleep(10);
        }
        throw new AssertionError("no response for " + method + " " + path);
    }

    static String error(FullHttpResponse response) {
        return JsonParser.parseString(response.content().toString(StandardCharsets.UTF_8)).getAsJsonObject().get("error").getAsString();
    }

    @Test
    void pinIsCheckedBeforeTheGame() throws Exception {
        String token = token();
        auth.setPin(mainframe, auth.hashPin("1234"));
        byte[] lua = "print('hi')".getBytes(StandardCharsets.UTF_8);
        var noPin = send(HttpMethod.PUT, base + "/files/hello.lua", token, lua, null);
        assertEquals(403, noPin.status().code());
        assertEquals("pin_required", error(noPin));
        var wrong = send(HttpMethod.PUT, base + "/files/hello.lua", token, lua, "9999");
        assertEquals("wrong_pin", error(wrong));
        assertTrue(bridge.dispatched.isEmpty(), "nothing reached the game: " + bridge.dispatched);
        send(HttpMethod.PUT, base + "/files/hello.lua", token, lua, "1234");
        assertEquals(List.of("script:SCRIPT_PUT"), bridge.dispatched, "the right PIN passes");
        // Reading needs no PIN.
        send(HttpMethod.GET, base + "/files/hello.lua", token, new byte[0], null);
        assertEquals("script:SCRIPT_GET", bridge.dispatched.getLast());
    }

    @Test
    void rateLimitAndSizes() throws Exception {
        String token = token();
        byte[] lua = "x=1".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < HttpHandler.SCRIPT_RATE; i++) send(HttpMethod.PUT, base + "/files/a.lua", token, lua, null);
        var limited = send(HttpMethod.PUT, base + "/files/a.lua", token, lua, null);
        assertEquals(429, limited.status().code());
        var bigRun = send(HttpMethod.POST, base + "/run", token, new byte[ClientMessages.MAX_BODY + 1], null);
        assertEquals(413, bigRun.status().code(), "only uploads may exceed 4 KB");
        assertEquals(404, send(HttpMethod.GET, base + "/files/a/b/c.lua", token, new byte[0], null).status().code(), "two folder levels never match");
        // "../x.lua" matches the URL pattern but is refused by ScriptFiles.validate on the server thread (unit tested).
    }

    @Test
    void storeInstallsAreVerifiedBeforeTheGame() throws Exception {
        String token = token();
        byte[] lua = "print('guard')".getBytes(StandardCharsets.UTF_8);
        var refused = send(HttpMethod.PUT, base + "/files/guard.lua?store=reactor-guard@1.2.0", token, lua, null);
        assertEquals(409, refused.status().code());
        assertEquals("store_not_configured", error(refused), "the bridge refuses (no key in the test bridge)");
        assertTrue(bridge.dispatched.isEmpty(), "an unverified store install never reaches the game");
        send(HttpMethod.PUT, base + "/files/guard.lua", token, lua, null);
        assertEquals(List.of("script:SCRIPT_PUT"), bridge.dispatched, "plain uploads are not affected");
    }
}
