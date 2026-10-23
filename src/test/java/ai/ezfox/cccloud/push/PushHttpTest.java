package ai.ezfox.cccloud.push;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/** The send path: DNS answers are checked (rebinding to a private address is refused), real POSTs, no redirects. */
class PushHttpTest {
    @Test
    void privateDnsAnswersAreBlocked() throws Exception {
        var http = new PushHttp(host -> new InetAddress[]{InetAddress.getByName("93.184.216.34"), InetAddress.getByName("127.0.0.1")});
        var request = new PushFormat.Request(Map.of(), new byte[0]);
        assertEquals("blocked_address", http.post("https://hooks.example.org/x", request, Set.of()).code(), "one private answer is enough to refuse");
        var unresolved = new PushHttp(host -> {
            throw new java.net.UnknownHostException(host);
        });
        assertEquals("unresolved", unresolved.post("https://nope.example.org/x", request, Set.of()).code());
        assertNull(PushHttp.pick(new InetAddress[]{InetAddress.getByName("10.0.0.1")}, false));
        assertNotNull(PushHttp.pick(new InetAddress[]{InetAddress.getByName("10.0.0.1")}, true), "allow-listed hosts are trusted");
    }

    @Test
    void postsToAnAllowListedNtfyAndDoesNotFollowRedirects() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        var seen = new CopyOnWriteArrayList<String>();
        server.createContext("/topic", ex -> {
            seen.add(ex.getRequestMethod() + " " + ex.getRequestHeaders().getFirst("Title") + " " + ex.getRequestHeaders().getFirst("Priority") + " "
                + ex.getRequestHeaders().getFirst("Authorization") + " " + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.createContext("/index.json", ex -> {
            byte[] body = "{\"format\":1}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/moved", ex -> {
            ex.getResponseHeaders().add("Location", "http://127.0.0.1:1/");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.start();
        var http = new PushHttp();
        try {
            int port = server.getAddress().getPort();
            var target = new PushTarget("0a1b2c3d", PushTarget.Type.NTFY, "LAN", "http://127.0.0.1:" + port + "/topic", "tk_secret", "info",
                EnumSet.allOf(PushTarget.Event.class), true);
            var message = new PushMessage(UUID.randomUUID(), "Base", PushTarget.Event.ALERTS, "warn", "Reactor", "Core hot", 0);
            var result = http.post(target.url(), PushFormat.request(target, message), Set.of("127.0.0.1"));
            assertTrue(result.ok(), result.code());
            assertEquals(List.of("POST Reactor - Base 4 Bearer tk_secret Core hot"), seen);
            var moved = http.post("http://127.0.0.1:" + port + "/moved", PushFormat.request(target, message), Set.of("127.0.0.1"));
            assertEquals("http_302", moved.code(), "redirects are not followed");
            assertFalse(moved.retryable());
            assertEquals("blocked_address", http.post(target.url(), PushFormat.request(target, message), Set.of()).code(), "not without the allowlist");
            var get = http.get("http://127.0.0.1:" + port + "/index.json", Set.of("127.0.0.1"), 1024);
            assertTrue(get.ok() && "{\"format\":1}".equals(new String(get.body(), StandardCharsets.UTF_8)), "GET returns the body (store index)");
            assertEquals("blocked_address", http.get("http://127.0.0.1:" + port + "/index.json", Set.of(), 1024).code(), "the same SSRF rules");
            assertEquals("connect_failed", http.post("http://127.0.0.1:1/x", PushFormat.request(target, message), Set.of("127.0.0.1")).code());
        } finally {
            http.shutdown();
            server.stop(0);
        }
    }
}
