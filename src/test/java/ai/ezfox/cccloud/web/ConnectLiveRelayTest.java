package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.connect.ConnectClient;
import ai.ezfox.cccloud.connect.ConnectKeys;
import ai.ezfox.cccloud.connect.ConnectSettings;
import ai.ezfox.cccloud.connect.ConnectStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Against the real, deployed relay (opt-in, it uses the internet): a test name is claimed at
 * {@code wss://connect.cccloud.dev}, {@code https://<name>.cccloud.dev} serves the official dashboard, its API and its
 * WebSocket reach the dashboard pipeline in this JVM through the tunnel, and the name is released at the end.
 * <pre>
 * CCCLOUD_CONNECT_LIVE=wss://connect.cccloud.dev ./gradlew test --tests '*ConnectLiveRelayTest'
 * </pre>
 */
class ConnectLiveRelayTest {
    /** A bundle the relay has published (1.0.0-beta.1). */
    static final String PUBLISHED_BUNDLE = "e016769f9f30731d";

    @TempDir
    Path dir;

    @Test
    void realRelay() throws Exception {
        String relayUrl = System.getenv("CCCLOUD_CONNECT_LIVE");
        assumeTrue(relayUrl != null && !relayUrl.isBlank(), "CCCLOUD_CONNECT_LIVE not set");
        String name = "e2e-check-" + UUID.randomUUID().toString().substring(0, 6);
        var instance = new WebInstance(new TestBridge(), new AuthStore(System::currentTimeMillis, () -> {
        }), new AccessStore(System::currentTimeMillis, () -> {
        }), null, List.of(), false, "http://127.0.0.1", 0);
        var bridge = TunnelBridge.forTest(instance);
        var settings = new ConnectSettings(true, name, URI.create(relayUrl));
        var client = new ConnectClient(settings, name, ConnectKeys.loadOrCreate(dir), "1.0.0-beta.1", PUBLISHED_BUNDLE, "https://cccloud.dev",
            bridge::open, s -> {
            }, false);
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        try {
            client.start();
            long until = System.currentTimeMillis() + 30_000;
            while (client.status().state() != ConnectStatus.State.CONNECTED && System.currentTimeMillis() < until) Thread.sleep(100);
            assertEquals(ConnectStatus.State.CONNECTED, client.status().state(), String.valueOf(client.status()));
            String base = client.status().url();
            assertEquals("https://" + name + ".cccloud.dev", base);

            var index = http.send(HttpRequest.newBuilder(URI.create(base + "/")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, index.statusCode());
            assertTrue(index.body().contains("<title>CC: Cloud</title>"), index.body());
            String csp = index.headers().firstValue("content-security-policy").orElse("");
            assertTrue(csp.contains("script-src 'self'") && csp.contains("wss://" + name + ".cccloud.dev"), csp);
            var asset = index.body().replaceAll("(?s).*src=\"(/assets/[^\"]+)\".*", "$1");
            var js = http.send(HttpRequest.newBuilder(URI.create(base + asset)).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, js.statusCode(), asset);
            assertTrue(js.headers().firstValue("cache-control").orElse("").contains("immutable"));

            var config = http.send(HttpRequest.newBuilder(URI.create(base + "/api/config")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, config.statusCode(), config.body());
            assertTrue(config.body().contains("\"version\""), config.body());
            var unauthorized = http.send(HttpRequest.newBuilder(URI.create(base + "/api/mainframes")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, unauthorized.statusCode(), unauthorized.body());

            // the dashboard WebSocket: an unknown token is refused by the dashboard server behind the tunnel
            var answer = new CompletableFuture<String>();
            var socket = http.newWebSocketBuilder().header("Origin", base).buildAsync(URI.create(base.replace("https://", "wss://") + "/ws/client"),
                new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        answer.complete("text:" + data);
                        return WebSocket.Listener.super.onText(ws, data, last);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                        answer.complete("close:" + code);
                        return null;
                    }
                }).get(15, TimeUnit.SECONDS);
            socket.sendText("{\"t\":\"auth\",\"token\":\"nope\"}", true);
            String reply = answer.get(15, TimeUnit.SECONDS);
            assertTrue(reply.contains("error") || reply.startsWith("close:44"), reply);
            socket.abort();

            // a foreign Origin never gets a dashboard WebSocket
            var foreign = http.newWebSocketBuilder().header("Origin", "https://evil.example")
                .buildAsync(URI.create(base.replace("https://", "wss://") + "/ws/client"), new WebSocket.Listener() {
                });
            assertThrows(Exception.class, () -> foreign.get(15, TimeUnit.SECONDS));

            assertEquals("released", client.release().get(15, TimeUnit.SECONDS));
            var check = http.send(HttpRequest.newBuilder(URI.create(relayUrl.replace("wss://", "https://") + "/api/check/" + name)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertTrue(check.body().contains("\"available\":true"), check.body());
            var gone = http.send(HttpRequest.newBuilder(URI.create(base + "/")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, gone.statusCode(), "released: no dashboard there any more");
        } finally {
            client.stop();
            bridge.stop();
            instance.shutdownExecutors();
        }
    }
}
