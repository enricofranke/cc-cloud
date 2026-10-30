package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.connect.ConnectClient;
import ai.ezfox.cccloud.connect.ConnectKeys;
import ai.ezfox.cccloud.connect.ConnectSettings;
import ai.ezfox.cccloud.connect.ConnectStatus;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Against the real relay code under {@code wrangler dev} (opt-in): the mod's tunnel client, the relay Worker and its
 * Durable Object, and the dashboard pipeline behind the tunnel. Start the relay in the website repository first:
 * <pre>
 * cd connect &amp;&amp; npx wrangler dev --port 8788 --var ROOT_DOMAIN:localhost --var DEV_MODE:1
 * CCCLOUD_CONNECT_DEV_RELAY=ws://127.0.0.1:8788 ./gradlew test --tests '*ConnectDevRelayTest'
 * </pre>
 * Skipped when the variable is not set. wrangler dev rewrites the Host header, so the dashboard host goes in
 * {@code X-Dev-Host} (only read with {@code DEV_MODE=1}).
 */
class ConnectDevRelayTest {
    /** A bundle published in the relay's bundles.json (1.0.0-beta.1), so the relay serves its dashboard. */
    static final String PUBLISHED_BUNDLE = "e016769f9f30731d";

    @TempDir
    Path dir;

    @Test
    void tunnelThroughTheLocalRelay() throws Exception {
        String relayUrl = System.getenv("CCCLOUD_CONNECT_DEV_RELAY");
        assumeTrue(relayUrl != null && !relayUrl.isBlank(), "CCCLOUD_CONNECT_DEV_RELAY not set");
        var relay = URI.create(relayUrl);
        int port = relay.getPort();
        String name = "itest-" + UUID.randomUUID().toString().substring(0, 8);
        String host = name + ".localhost";

        var instance = new WebInstance(new TestBridge(), new AuthStore(System::currentTimeMillis, () -> {
        }), new AccessStore(System::currentTimeMillis, () -> {
        }), null, List.of(), false, "http://127.0.0.1", 0);
        var bridge = TunnelBridge.forTest(instance);
        var settings = new ConnectSettings(true, name, relay);
        var keys = ConnectKeys.loadOrCreate(dir);
        var client = new ConnectClient(settings, name, keys, "1.0.0-beta.1", PUBLISHED_BUNDLE, "https://cccloud.dev", bridge::open, s -> {
        }, false);
        try {
            client.start();
            long until = System.currentTimeMillis() + 15_000;
            while (client.status().state() != ConnectStatus.State.CONNECTED && System.currentTimeMillis() < until) Thread.sleep(50);
            assertEquals(ConnectStatus.State.CONNECTED, client.status().state(), String.valueOf(client.status()));
            assertTrue(client.status().bundleKnown());

            // the relay serves the official dashboard itself, with its CSP
            String index = get(port, host, "/");
            assertTrue(index.startsWith("HTTP/1.1 200"), index);
            assertTrue(index.contains("<title>CC: Cloud</title>"), index);
            assertTrue(index.toLowerCase().contains("content-security-policy: default-src 'self'"), index);

            // /api goes through the tunnel into the dashboard pipeline (LAN-only instance: tunnel traffic still gets in)
            String config = get(port, host, "/api/config");
            assertTrue(config.startsWith("HTTP/1.1 200"), config);
            assertTrue(config.contains("\"version\""), config);
            assertTrue(config.toLowerCase().contains("sandbox"), "data answers carry a sandboxing CSP");
            String unauthorized = get(port, host, "/api/mainframes");
            assertTrue(unauthorized.startsWith("HTTP/1.1 401"), unauthorized);

            // give the name back: free again
            assertEquals("released", client.release().get());
            String check = get(port, "connect.localhost", "/api/check/" + name);
            assertTrue(check.contains("\"available\":true"), check);
        } finally {
            client.stop();
            bridge.stop();
            instance.shutdownExecutors();
        }
    }

    /** A plain HTTP/1.1 GET with the given Host (the relay picks the dashboard by host name). */
    private static String get(int port, String host, String path) throws Exception {
        try (var socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(15_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\nX-Dev-Host: " + host + ":" + port + "\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return read(socket.getInputStream());
        }
    }

    private static String read(InputStream in) throws Exception {
        var out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
