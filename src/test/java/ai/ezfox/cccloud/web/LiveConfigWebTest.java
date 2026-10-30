package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessStore;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Config reload of {@code [web]} on a real socket server (the production pipeline): a new address is bound before the
 * old one closes, a failed bind or unreadable TLS files keep everything as it was, new rules close the connections of
 * the old ones (which then get the new rules, exactly like after a fresh start), and compression alone keeps them.
 */
class LiveConfigWebTest {
    /** The address the test servers bind to (getLoopbackAddress() may be ::1). */
    static final InetAddress LOOPBACK = loopback();
    private final AuthStore auth = new AuthStore(System::currentTimeMillis, () -> {
    });
    private EventLoopGroup group;
    private WebInstance instance;
    private final List<Socket> open = new ArrayList<>();
    private final List<ServerSocket> blockers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        group = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
    }

    @AfterEach
    void tearDown() throws IOException {
        for (var s : open) s.close();
        for (var s : blockers) s.close();
        if (instance != null) {
            if (instance.channel != null) instance.channel.close().syncUninterruptibly();
            instance.closeConnections(ch -> true);
            instance.shutdownExecutors();
        }
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
    }

    static WebSettings settings(String bind, int port, boolean internet, List<String> proxies, boolean compression, String cert, String key) {
        return new WebSettings(true, bind, port, "", internet, proxies, false, compression, List.of(), cert, key, WebSettings.fingerprint(cert, key));
    }

    static WebSettings lan(int port) {
        return settings("127.0.0.1", port, false, List.of(), true, "", "");
    }

    private void start(WebSettings s) throws Exception {
        instance = new WebInstance(new TestBridge(), auth, new AccessStore(System::currentTimeMillis, () -> {
        }), CloudWebServer.policy(s), "http://127.0.0.1:" + s.port(), s.port());
        instance.settings = s;
        instance.group = group;
        instance.channel = CloudWebServer.bind(instance, group, s.bind(), s.port());
    }

    private CloudWebServer.WebChange reconfigure(WebSettings next) {
        return CloudWebServer.reconfigure(instance, next, (s, port) -> "http://" + s.bind() + ":" + port);
    }

    private static InetAddress loopback() {
        try {
            return InetAddress.getByName("127.0.0.1");
        } catch (java.net.UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    static int freePort() throws IOException {
        try (var socket = new ServerSocket(0, 1, LOOPBACK)) {
            return socket.getLocalPort();
        }
    }

    private Socket connect(int port) throws IOException {
        var socket = new Socket(LOOPBACK, port);
        socket.setSoTimeout(5000);
        open.add(socket);
        return socket;
    }

    /** A keep-alive GET; returns the status line and headers, or "" if the server closed the connection. */
    private static String get(Socket socket, String path, @Nullable String forwardedFor) throws IOException {
        String extra = forwardedFor == null ? "" : "X-Forwarded-For: " + forwardedFor + "\r\n";
        try {
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\n" + extra + "Connection: keep-alive\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return readHead(socket.getInputStream());
        } catch (SocketException e) {
            return ""; // reset by the server
        }
    }

    private static String readHead(InputStream in) throws IOException {
        var head = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            head.write(b);
            if (head.size() >= 4 && head.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) break;
        }
        String text = head.toString(StandardCharsets.ISO_8859_1);
        int at = text.toLowerCase().indexOf("content-length:");
        if (at >= 0) in.readNBytes(Integer.parseInt(text.substring(at + 15, text.indexOf("\r\n", at)).trim()));
        return text;
    }

    private static boolean closedByServer(Socket socket) throws IOException {
        try {
            return socket.getInputStream().read() == -1;
        } catch (SocketException e) {
            return true;
        }
    }

    private static boolean refuses(int port) {
        try (var s = new Socket(LOOPBACK, port)) {
            s.setSoTimeout(1000);
            s.getOutputStream().write("GET /api/config HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            return s.getInputStream().read() == -1;
        } catch (ConnectException e) {
            return true;
        } catch (IOException e) {
            return true;
        }
    }

    @Test
    void aNewPortIsBoundBeforeTheOldOneCloses() throws Exception {
        int a = freePort();
        start(lan(a));
        var before = connect(a);
        assertTrue(get(before, "/api/config", null).startsWith("HTTP/1.1 200"));
        int b = freePort();
        var change = reconfigure(lan(b));
        assertTrue(change.ok() && change.changed(), change.message());
        assertTrue(change.message().contains("now listening on 127.0.0.1:" + b), change.message());
        assertEquals(b, instance.port);
        assertEquals("http://127.0.0.1:" + b, instance.url);
        assertTrue(get(connect(b), "/api/config", null).startsWith("HTTP/1.1 200"), "the new port serves");
        assertTrue(closedByServer(before), "connections on the old address are closed (browsers reconnect)");
        assertTrue(refuses(a), "the old port is closed");
    }

    @Test
    void aFailedBindKeepsTheOldListenerAndRules() throws Exception {
        int a = freePort();
        start(lan(a));
        var policy = instance.policy;
        var oldSettings = instance.settings;
        var kept = connect(a);
        assertTrue(get(kept, "/api/config", null).startsWith("HTTP/1.1 200"));
        var taken = new ServerSocket(0, 1, LOOPBACK);
        blockers.add(taken);
        var next = settings("127.0.0.1", taken.getLocalPort(), true, List.of(), true, "", "");
        var change = reconfigure(next);
        assertFalse(change.ok(), "the port is in use");
        assertTrue(change.message().contains("keeps running on 127.0.0.1:" + a), change.message());
        assertSame(policy, instance.policy, "nothing of the new [web] section applies, not even webAllowInternet");
        assertSame(oldSettings, instance.settings);
        assertEquals(a, instance.port);
        assertTrue(get(kept, "/api/config", null).startsWith("HTTP/1.1 200"), "open connections keep working");
        assertTrue(get(connect(a), "/api/config", null).startsWith("HTTP/1.1 200"), "the old port still accepts");
        // a later reload with the port free again applies it
        taken.close();
        var retry = reconfigure(next);
        assertTrue(retry.ok(), retry.message());
        assertTrue(instance.policy.allowInternet());
    }

    @Test
    void samePortOnAnotherAddressNeverLeavesTheDashboardWithoutAListener() throws Exception {
        int a = freePort();
        start(lan(a));
        // 127.0.0.2 shares the port with 127.0.0.1's listener on some systems and does not exist on others: either the
        // switch succeeds, or the old address serves again.
        var change = reconfigure(settings("127.0.0.2", a, false, List.of(), true, "", ""));
        if (change.ok()) {
            assertEquals("127.0.0.2", instance.settings.bind());
            try (var s = new Socket("127.0.0.2", a)) {
                assertTrue(s.isConnected());
            }
        } else {
            assertTrue(change.message().contains("keeps running on 127.0.0.1:" + a), change.message());
            assertTrue(instance.channel != null && instance.channel.isActive(), "listening again on the old address");
            assertTrue(get(connect(a), "/api/config", null).startsWith("HTTP/1.1 200"));
        }
    }

    @Test
    void unreadableTlsFilesChangeNothing() throws Exception {
        int a = freePort();
        start(lan(a));
        var policy = instance.policy;
        var change = reconfigure(settings("127.0.0.1", a, true, List.of(), true, "/nonexistent/fullchain.pem", "/nonexistent/privkey.pem"));
        assertFalse(change.ok());
        assertTrue(change.message().startsWith("Could not load TLS certificate/key"), change.message());
        assertTrue(change.message().contains("keeps running with its previous [web] settings"), change.message());
        assertSame(policy, instance.policy, "never internet access over plain HTTP because TLS failed");
        assertFalse(instance.policy.allowInternet());
        assertTrue(get(connect(a), "/api/config", null).startsWith("HTTP/1.1 200"));
    }

    @Test
    void newRulesCloseOldConnectionsAndApplyLikeAFreshStart() throws Exception {
        int a = freePort();
        // behind a proxy on the same host: the forwarded client IP decides the LAN rule
        start(settings("127.0.0.1", a, true, List.of("127.0.0.1"), true, "", ""));
        var internet = connect(a);
        assertTrue(get(internet, "/api/config", "8.8.8.8").startsWith("HTTP/1.1 200"), "internet allowed");
        // reload: webAllowInternet = false
        var change = reconfigure(settings("127.0.0.1", a, false, List.of("127.0.0.1"), true, "", ""));
        assertTrue(change.ok(), change.message());
        assertTrue(change.message().contains("1 open connection(s) closed"), change.message());
        assertTrue(closedByServer(internet), "a connection accepted under the old rules does not stay open");
        String refused = get(connect(a), "/api/config", "8.8.8.8");
        assertTrue(refused.startsWith("HTTP/1.1 403"), "same as after a fresh start with webAllowInternet=false: " + refused);
        assertTrue(get(connect(a), "/api/config", "192.168.1.20").startsWith("HTTP/1.1 200"), "LAN clients still get in");
        // reload: the proxy is no longer trusted, so its forwarded header is ignored (the peer itself is loopback)
        var lan = connect(a);
        assertTrue(get(lan, "/api/config", "192.168.1.20").startsWith("HTTP/1.1 200"));
        assertTrue(reconfigure(lan(a)).ok());
        assertTrue(closedByServer(lan));
        assertTrue(get(connect(a), "/api/config", "8.8.8.8").startsWith("HTTP/1.1 200"), "X-Forwarded-For from an untrusted peer is ignored");
    }

    @Test
    void compressionAloneKeepsOpenConnections() throws Exception {
        int a = freePort();
        start(lan(a));
        var kept = connect(a);
        assertTrue(get(kept, "/api/config", null).startsWith("HTTP/1.1 200"));
        var change = reconfigure(settings("127.0.0.1", a, false, List.of(), false, "", ""));
        assertTrue(change.ok() && change.changed(), change.message());
        assertFalse(change.message().contains("closed"), change.message());
        assertFalse(instance.policy.compression(), "new connections without permessage-deflate");
        assertTrue(get(kept, "/api/config", null).startsWith("HTTP/1.1 200"), "still open");
        assertEquals(CloudWebServer.WebChange.unchanged(), reconfigure(settings("127.0.0.1", a, false, List.of(), false, "", "")));
    }

    @Test
    void internetOverPlainHttpWarnsAtStartAndReload() {
        var plain = settings("", 8787, true, List.of(), true, "", "");
        var warnings = CloudWebServer.warnings(plain, false);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("webAllowInternet=true with plain HTTP"), warnings.getFirst());
        assertTrue(CloudWebServer.warnings(plain, true).isEmpty(), "TLS");
        assertTrue(CloudWebServer.warnings(lan(8787), false).isEmpty(), "LAN only");
    }

    @Test
    void connectionRulesAndAddress() {
        var base = lan(8787);
        assertTrue(base.sameConnectionRules(lan(8787)));
        assertFalse(base.sameAddress(lan(8788)));
        assertFalse(base.sameConnectionRules(settings("127.0.0.1", 8787, true, List.of(), true, "", "")), "LAN rule");
        assertFalse(base.sameConnectionRules(settings("127.0.0.1", 8787, false, List.of("10.0.0.1"), true, "", "")), "proxies");
        assertTrue(base.sameConnectionRules(settings("127.0.0.1", 8787, false, List.of(), false, "", "")), "compression only");
        assertEquals("", WebSettings.fingerprint("", ""));
        assertEquals("unreadable", WebSettings.fingerprint("/nonexistent/a", "/nonexistent/b"));
    }
}
