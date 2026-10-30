package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessStore;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The connection limits on a real socket server (the production pipeline): a browser's burst of parallel connections
 * (6 per pool, a WebSocket per tab, preconnects) all get answers; a connection just over the per-IP limit gets 503 with
 * Retry-After instead of an empty response; far over the limit sockets are still closed at once.
 */
class ConnectionLimitTest {
    private final WebInstance instance = new WebInstance(new TestBridge(), new AuthStore(System::currentTimeMillis, () -> {
    }), new AccessStore(System::currentTimeMillis, () -> {
    }), null, List.of(), false, "http://127.0.0.1", 0);
    private EventLoopGroup group;
    private Channel server;
    private int port;
    private final List<Socket> open = new ArrayList<>();

    @BeforeEach
    void start() {
        group = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    instance.initPipeline(ch);
                }
            })
            .bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0)).syncUninterruptibly().channel();
        port = ((InetSocketAddress) server.localAddress()).getPort();
    }

    @AfterEach
    void stop() throws IOException {
        for (var s : open) s.close();
        server.close().syncUninterruptibly();
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        instance.shutdownExecutors();
    }

    private Socket connect() throws IOException {
        var socket = new Socket(InetAddress.getLoopbackAddress(), port);
        socket.setSoTimeout(5000);
        open.add(socket);
        return socket;
    }

    /** Sends a keep-alive GET and returns the status line and headers, or "" if the server closed without a response. */
    private static String get(Socket socket, String path) throws IOException {
        socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: keep-alive\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        return readHead(socket.getInputStream());
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
        if (at >= 0) {
            int length = Integer.parseInt(text.substring(at + 15, text.indexOf("\r\n", at)).trim());
            assertEquals(length, in.readNBytes(length).length, "full body");
        }
        return text;
    }

    /** Waits until the server has counted (accepted) all sockets opened so far. */
    private void settle() throws InterruptedException {
        Thread.sleep(150);
    }

    @Test
    void aBrowserBurstIsAnsweredCompletely() throws Exception {
        // Chrome on a fresh load with three tabs: 6 + 6 pooled connections, 3 WebSockets, 3 preconnects.
        int burst = 18;
        assertTrue(burst <= CloudWebServer.MAX_CONNECTIONS_PER_IP, "a browser fits under the per-IP limit");
        var pool = Executors.newFixedThreadPool(burst);
        try {
            var tasks = new ArrayList<Callable<String>>();
            for (int i = 0; i < burst; i++) tasks.add(() -> get(connect(), "/api/config"));
            for (var result : pool.invokeAll(tasks)) {
                String head = result.get(10, TimeUnit.SECONDS);
                assertTrue(head.startsWith("HTTP/1.1 200"), "every connection gets a response, got: " + head.lines().findFirst().orElse("(empty)"));
            }
        } finally {
            pool.shutdownNow();
        }
        // Keep-alive: the same sockets serve further requests.
        assertTrue(get(open.getFirst(), "/api/config").startsWith("HTTP/1.1 200"));
    }

    @Test
    void justOverTheLimitGets503WithRetryAfter() throws Exception {
        for (int i = 0; i < CloudWebServer.MAX_CONNECTIONS_PER_IP; i++) connect();
        settle();
        var extra = connect();
        String head = get(extra, "/api/config");
        assertTrue(head.startsWith("HTTP/1.1 503"), "503 instead of an empty response: " + head);
        assertTrue(head.toLowerCase().contains("retry-after: 1"), head);
        assertTrue(head.toLowerCase().contains("connection: close"), head);
        assertEquals(-1, extra.getInputStream().read(), "closed after the answer");
        // The connections under the limit still work.
        assertTrue(get(open.getFirst(), "/api/config").startsWith("HTTP/1.1 200"));
    }

    @Test
    void farOverTheLimitIsClosedAtOnce() throws Exception {
        for (int i = 0; i < CloudWebServer.MAX_CONNECTIONS_PER_IP + CloudWebServer.OVER_LIMIT_GRACE; i++) connect();
        settle();
        var flood = connect();
        String head;
        try {
            head = get(flood, "/api/config");
        } catch (IOException reset) {
            head = "";
        }
        assertEquals("", head, "beyond the grace the socket is closed without a response");
    }

    @Test
    void closedConnectionsFreeTheirSlots() throws Exception {
        for (int i = 0; i < CloudWebServer.MAX_CONNECTIONS_PER_IP; i++) connect();
        settle();
        for (var s : open) s.close();
        open.clear();
        Thread.sleep(300);
        assertTrue(get(connect(), "/api/config").startsWith("HTTP/1.1 200"), "slots are released when sockets close");
    }
}
