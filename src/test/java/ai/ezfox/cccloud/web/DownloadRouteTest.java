package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessStore;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.timeout.IdleStateHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** /api/download/cccloud.jar: the server's own jar, public, fixed, limited per IP, streamed. */
class DownloadRouteTest {
    private static final String PATH = "/api/download/cccloud.jar";
    private final TestBridge bridge = new TestBridge();
    private final WebInstance instance = new WebInstance(bridge, new AuthStore(System::currentTimeMillis, () -> {
    }), null, List.of(), true, "http://localhost:8787", 8787);
    private EventLoopGroup group;
    @TempDir
    Path dir;

    @AfterEach
    void stop() {
        if (instance.channel != null) instance.channel.close().syncUninterruptibly();
        instance.closeConnections(ch -> true);
        instance.shutdownExecutors();
        if (group != null) group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
    }

    private byte[] jar(int size) throws IOException {
        byte[] bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        bridge.modJar = Files.write(dir.resolve("cccloud-1.0.0-beta.2+dev5.jar"), bytes);
        return bytes;
    }

    private EmbeddedChannel send(HttpMethod method) {
        var channel = new EmbeddedChannel(new HttpHandler(instance));
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, PATH, Unpooled.EMPTY_BUFFER);
        req.headers().set(HttpHeaderNames.HOST, "localhost:8787");
        channel.writeInbound(req);
        channel.runPendingTasks();
        return channel;
    }

    private FullHttpResponse full(HttpMethod method) {
        FullHttpResponse response = send(method).readOutbound();
        assertNotNull(response);
        return response;
    }

    /** One GET over a real socket on the production pipeline: the whole raw answer. */
    private byte[] rawGet() throws Exception {
        if (group == null) {
            group = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
            instance.channel = CloudWebServer.bind(instance, group, "127.0.0.1", 0);
        }
        int port = ((java.net.InetSocketAddress) instance.channel.localAddress()).getPort();
        try (var socket = new Socket(LiveConfigWebTest.LOOPBACK, port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET " + PATH + " HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return socket.getInputStream().readAllBytes(); // until the server closes
        }
    }

    private static int headEnd(byte[] raw) {
        for (int i = 3; i < raw.length; i++) if (raw[i - 3] == '\r' && raw[i - 2] == '\n' && raw[i - 1] == '\r' && raw[i] == '\n') return i + 1;
        return fail("no end of the headers");
    }

    @Test
    void anExplicitPublicRouteForExactlyOneFile() {
        var route = Router.match(HttpMethod.GET, PATH);
        assertNotNull(route);
        assertEquals(Router.Id.DOWNLOAD, route.id());
        assertEquals(Router.Access.PUBLIC, route.access());
        assertNull(AccessGate.routePerm(Router.Id.DOWNLOAD), "no mainframe permission");
        assertEquals(Router.Id.DOWNLOAD, Router.match(HttpMethod.HEAD, PATH).id());
        assertNull(Router.match(HttpMethod.POST, PATH), "read-only");
        assertNull(Router.match(HttpMethod.DELETE, PATH));
        for (var other : new String[]{"/api/download", "/api/download/", "/api/download/other.jar", "/api/download/cccloud.jar/x", "/api/download/../world.dat"}) {
            assertNull(Router.match(HttpMethod.GET, other), other);
        }
        assertEquals(405, RouteAuthTest.request(RouteAuthTest.instance(true), HttpMethod.POST, PATH, null).status().code());
    }

    @Test
    void streamsTheFileWithItsNameTypeAndLength() throws Exception {
        byte[] jar = jar(300_000);
        byte[] raw = rawGet();
        int end = headEnd(raw);
        String head = new String(raw, 0, end, StandardCharsets.ISO_8859_1);
        String lower = head.toLowerCase(Locale.ROOT);
        assertTrue(head.startsWith("HTTP/1.1 200 OK"), head);
        assertTrue(lower.contains("content-type: application/java-archive\r\n"), head);
        assertTrue(head.contains("content-disposition: attachment; filename=\"cccloud-1.0.0-beta.2+dev5.jar\"\r\n"), head);
        assertTrue(lower.contains("content-length: 300000\r\n"), head);
        assertTrue(lower.contains("cache-control: no-store\r\n"), head);
        assertTrue(lower.contains("connection: close\r\n"), head);
        assertTrue(lower.contains("x-content-type-options: nosniff\r\n"), "the usual security headers: " + head);
        assertArrayEquals(jar, java.util.Arrays.copyOfRange(raw, end, raw.length), "the file, byte for byte");
    }

    @Test
    void aJarBiggerThanTheSocketBuffersArrivesWhole() throws Exception {
        byte[] jar = jar(6_000_000); // over the write watermark: only works if the stream follows the socket
        byte[] raw = rawGet();
        assertArrayEquals(jar, java.util.Arrays.copyOfRange(raw, headEnd(raw), raw.length));
    }

    @Test
    void headAnswersTheLengthWithoutTheFile() throws Exception {
        jar(5_000);
        var response = full(HttpMethod.HEAD);
        assertEquals(200, response.status().code());
        assertEquals(0, response.content().readableBytes());
        assertEquals("5000", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertEquals("application/java-archive", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals(0, instance.downloads.get(), "no download started");
    }

    @Test
    void piecesOfOneChunkAtMost() throws Exception {
        byte[] jar = jar(HttpHandler.DOWNLOAD_CHUNK * 3 + 7);
        var channel = send(HttpMethod.GET);
        HttpResponse response = channel.readOutbound();
        assertEquals(200, response.status().code());
        var body = new ByteArrayOutputStream();
        for (Object out; (out = channel.readOutbound()) != null; ) {
            var content = ((HttpContent) out).content();
            assertTrue(content.readableBytes() <= HttpHandler.DOWNLOAD_CHUNK);
            byte[] piece = new byte[content.readableBytes()];
            content.readBytes(piece);
            body.write(piece);
            if (out instanceof LastHttpContent) break;
        }
        assertArrayEquals(jar, body.toByteArray());
        assertFalse(channel.isOpen(), "closed after the last byte");
    }

    @Test
    void offWithoutAJar() {
        var response = full(HttpMethod.GET);
        assertEquals(404, response.status().code(), "webJarDownload=false, or dev runs without a jar file");
        assertTrue(response.content().toString(StandardCharsets.UTF_8).contains("not_available"));
        assertEquals(404, RouteAuthTest.request(RouteAuthTest.instance(true), HttpMethod.GET, PATH, null).status().code());
    }

    @Test
    void theSwitchAppliesToTheNextRequest() throws Exception {
        jar(100);
        assertEquals(200, full(HttpMethod.HEAD).status().code());
        var on = bridge.modJar;
        bridge.modJar = null;
        assertEquals(404, full(HttpMethod.HEAD).status().code(), "switched off: at once");
        bridge.modJar = on;
        assertEquals(200, full(HttpMethod.HEAD).status().code(), "and on again");
    }

    @Test
    void aMissingFileIsAnError() {
        bridge.modJar = dir.resolve("gone.jar");
        assertEquals(503, full(HttpMethod.GET).status().code());
        assertEquals(0, instance.downloads.get());
    }

    @Test
    void rateLimitedPerIp() throws Exception {
        jar(100);
        for (int i = 0; i < HttpHandler.DOWNLOAD_BURST; i++) assertEquals(200, full(HttpMethod.HEAD).status().code(), "request " + i);
        var limited = full(HttpMethod.GET);
        assertEquals(429, limited.status().code());
        assertTrue(limited.content().toString(StandardCharsets.UTF_8).contains("rate_limited"));
        assertTrue(Integer.parseInt(limited.headers().get(HttpHeaderNames.RETRY_AFTER)) > 0);
        instance.downloadLimits.clear();
        assertEquals(200, full(HttpMethod.HEAD).status().code(), "another address has its own budget");
    }

    @Test
    void onlySoManyAtOnce() throws Exception {
        jar(100);
        instance.downloads.set(HttpHandler.MAX_DOWNLOADS);
        var busy = full(HttpMethod.GET);
        assertEquals(503, busy.status().code());
        assertTrue(busy.content().toString(StandardCharsets.UTF_8).contains("busy"));
        assertEquals("5", busy.headers().get(HttpHeaderNames.RETRY_AFTER));
        assertEquals(HttpHandler.DOWNLOAD_BURST, instance.downloadLimits.getOrDefault("unknown", new TokenBucket(HttpHandler.DOWNLOAD_BURST, 0)).available(), 0.01,
            "a refused download costs nothing of the budget");
        assertEquals(200, full(HttpMethod.HEAD).status().code(), "a HEAD starts no download");
    }

    @Test
    void aRunningDownloadHoldsASlotUntilItsConnectionCloses() throws Exception {
        try (var socket = stalledDownload()) {
            assertEquals(1, instance.downloads.get());
        }
        long until = System.currentTimeMillis() + 5000;
        while (instance.downloads.get() != 0 && System.currentTimeMillis() < until) Thread.sleep(10);
        assertEquals(0, instance.downloads.get(), "the slot is free again");
    }

    @Test
    void aSlowClientIsNotCutByTheReadTimeout() throws Exception {
        var ordinary = new EmbeddedChannel();
        instance.initPipeline(ordinary);
        assertEquals(CloudWebServer.HANDSHAKE_TIMEOUT_S * 1000L, ((IdleStateHandler) ordinary.pipeline().get("idle")).getReaderIdleTimeInMillis(),
            "ordinary connections keep their read timeout");
        ordinary.finishAndReleaseAll();
        try (var socket = stalledDownload()) {
            var idle = (IdleStateHandler) instance.connections.stream().filter(ch -> ch != ordinary).findFirst().orElseThrow().pipeline().get("idle");
            assertEquals(0, idle.getReaderIdleTimeInMillis(), "the client only receives now");
            assertEquals(HttpHandler.DOWNLOAD_STALL_S * 1000L, idle.getWriterIdleTimeInMillis(), "a stalled download still ends");
        }
    }

    /** A real connection whose client does not read: 8 MB do not fit into the socket buffers, so the transfer stays open. */
    private Socket stalledDownload() throws Exception {
        jar(8_000_000);
        group = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        instance.channel = CloudWebServer.bind(instance, group, "127.0.0.1", 0);
        var socket = new Socket(LiveConfigWebTest.LOOPBACK, ((java.net.InetSocketAddress) instance.channel.localAddress()).getPort());
        socket.getOutputStream().write(("GET " + PATH + " HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        long until = System.currentTimeMillis() + 5000;
        while (instance.downloads.get() != 1 && System.currentTimeMillis() < until) Thread.sleep(10);
        return socket;
    }

    @Test
    void aFileNameCannotBreakTheHeader() {
        assertEquals("attachment; filename=\"cccloud-1.0.0-beta.2+dev5.jar\"", HttpHandler.attachment("cccloud-1.0.0-beta.2+dev5.jar"));
        assertEquals("attachment; filename=\"a__b___c_.jar\"", HttpHandler.attachment("a\"\\b\r\n c;.jar"));
    }

    @Test
    void theLanOnlyRuleStillApplies() throws Exception {
        var closed = new WebInstance(bridge, new AuthStore(System::currentTimeMillis, () -> {
        }), new AccessStore(System::currentTimeMillis, () -> {
        }), null, List.of(), false, "http://localhost:8787", 8787);
        jar(100);
        assertEquals(403, RouteAuthTest.request(closed, HttpMethod.GET, PATH, null).status().code());
        closed.shutdownExecutors();
    }
}
