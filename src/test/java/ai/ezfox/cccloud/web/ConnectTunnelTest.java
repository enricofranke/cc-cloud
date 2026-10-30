package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.connect.ConnectClient;
import ai.ezfox.cccloud.connect.ConnectKeys;
import ai.ezfox.cccloud.connect.ConnectSettings;
import ai.ezfox.cccloud.connect.ConnectStatus;
import ai.ezfox.cccloud.connect.TunnelProtocol;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CC: Cloud Connect end to end inside one JVM: a fake relay (a WebSocket server that speaks the relay's side of the
 * tunnel protocol), the real tunnel client and the real dashboard pipeline behind the {@link TunnelBridge}. The
 * dashboard instance is LAN-only, as by default: tunnel traffic still gets in, with the relay's client IP.
 */
class ConnectTunnelTest {
    private final WebInstance instance = new WebInstance(new TestBridge(), new AuthStore(System::currentTimeMillis, () -> {
    }), new AccessStore(System::currentTimeMillis, () -> {
    }), null, List.of(), false, "http://127.0.0.1", 0);
    private TunnelBridge bridge;
    private FakeRelay relay;
    private ConnectClient client;
    private final List<ConnectStatus> statuses = new CopyOnWriteArrayList<>();
    @TempDir
    Path dir;

    @BeforeEach
    void start() {
        bridge = TunnelBridge.forTest(instance);
        relay = new FakeRelay();
    }

    @AfterEach
    void stop() {
        if (client != null) client.stop();
        relay.close();
        bridge.stop();
        instance.shutdownExecutors();
    }

    private ConnectClient connect(String name) throws Exception {
        var settings = new ConnectSettings(true, name, URI.create("ws://127.0.0.1:" + relay.port));
        client = new ConnectClient(settings, name, ConnectKeys.loadOrCreate(dir.resolve("keys")), "1.0.0-test", "0123456789abcdef",
            "https://cccloud.dev", bridge::open, statuses::add, false);
        client.start();
        return client;
    }

    private void awaitState(ConnectStatus.State state) throws InterruptedException {
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            if (client.status().state() == state) return;
            Thread.sleep(20);
        }
        fail("state " + state + " not reached: " + client.status());
    }

    private static ByteBuf request(long stream, String method, String path, String ip, List<String[]> headers, byte[] body) {
        var head = new JsonObject();
        head.addProperty("m", method);
        head.addProperty("p", path);
        var h = new JsonArray();
        for (var pair : headers) {
            var p = new JsonArray();
            p.add(pair[0]);
            p.add(pair[1]);
            h.add(p);
        }
        head.add("h", h);
        head.addProperty("ip", ip);
        head.addProperty("host", "abc.cccloud.dev");
        byte[] json = head.toString().getBytes(StandardCharsets.UTF_8);
        var buf = Unpooled.buffer();
        buf.writeByte(TunnelProtocol.HTTP_REQUEST).writeInt((int) stream).writeInt(json.length).writeBytes(json).writeBytes(body);
        return buf;
    }

    /** A response collected from the tunnel: status, content type, body. */
    record Answer(int status, String contentType, String body) {
    }

    private Answer answer(long stream) throws InterruptedException {
        int status = -1;
        String type = "";
        var body = new StringBuilder();
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            var f = relay.binary(stream, 5);
            if (f == null) continue;
            switch (f.type) {
                case TunnelProtocol.HTTP_HEAD -> {
                    var json = JsonParser.parseString(f.text()).getAsJsonObject();
                    status = json.get("s").getAsInt();
                    for (var e : json.getAsJsonArray("h")) if (e.getAsJsonArray().get(0).getAsString().equals("content-type")) type = e.getAsJsonArray().get(1).getAsString();
                }
                case TunnelProtocol.HTTP_BODY -> body.append(f.text());
                case TunnelProtocol.HTTP_END -> {
                    return new Answer(status, type, body.toString());
                }
                case TunnelProtocol.HTTP_ERROR -> {
                    return new Answer(-2, "", f.text());
                }
                default -> {
                }
            }
        }
        fail("no answer on stream " + stream);
        return null;
    }

    @Test
    void provesTheKeyAndAnswersRequestsThroughTheDashboardPipeline() throws Exception {
        connect("abc");
        awaitState(ConnectStatus.State.CONNECTED);
        assertEquals("https://abc.cccloud.dev", client.status().url());
        var auth = relay.auth.get(5, TimeUnit.SECONDS);
        assertEquals("abc", auth.get("name").getAsString());
        assertEquals("0123456789abcdef", auth.get("bundle").getAsString());
        assertEquals("https://cccloud.dev", auth.get("store").getAsString());
        assertTrue(relay.signatureValid, "the relay can verify the signed challenge with the announced key");

        relay.send(request(1, "GET", "/api/config", "203.0.113.7", List.<String[]>of(new String[]{"accept", "application/json"}), new byte[0]));
        var config = answer(1);
        assertEquals(200, config.status(), "a LAN-only dashboard still answers tunnel traffic");
        assertTrue(config.contentType().startsWith("application/json"));
        assertTrue(config.body().contains("\"version\""), config.body());

        // the dashboard's own rules apply: an unknown token is refused like on the LAN
        relay.send(request(2, "GET", "/api/mainframes", "203.0.113.7", List.<String[]>of(new String[]{"authorization", "Bearer nope"}), new byte[0]));
        assertEquals(401, answer(2).status());
        // bodies are passed on (a pairing attempt with a wrong code)
        relay.send(request(3, "POST", "/api/pair", "203.0.113.7", List.<String[]>of(new String[]{"content-type", "application/json"}),
            "{\"code\":\"ABCD-EFGH\",\"name\":\"phone\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(403, answer(3).status());
    }

    @Test
    void manyParallelRequestsOfOneBrowserWaitInsteadOfFailing() throws Exception {
        connect("abc");
        awaitState(ConnectStatus.State.CONNECTED);
        int n = 60; // over the dashboard's 24 connections per IP
        for (int i = 0; i < n; i++) relay.send(request(100 + i, "GET", "/api/config", "203.0.113.9", List.of(), new byte[0]));
        for (int i = 0; i < n; i++) assertEquals(200, answer(100 + i).status(), "request " + i);
    }

    @Test
    void dashboardWebSocketThroughTheTunnel() throws Exception {
        connect("abc");
        awaitState(ConnectStatus.State.CONNECTED);
        var open = new JsonObject();
        open.addProperty("m", "GET");
        open.addProperty("p", "/ws/client");
        var h = new JsonArray();
        var origin = new JsonArray();
        origin.add("origin");
        origin.add("https://abc.cccloud.dev");
        h.add(origin);
        open.add("h", h);
        open.addProperty("ip", "203.0.113.7");
        open.addProperty("host", "abc.cccloud.dev");
        relay.send(frame(TunnelProtocol.WS_OPEN, 7, open.toString()));
        assertEquals(TunnelProtocol.WS_ACCEPTED, relay.binary(7, 5000).type);
        relay.send(frame(TunnelProtocol.WS_TEXT, 7, "{\"t\":\"auth\",\"token\":\"nope\"}"));
        // refused like any browser with an unknown token: an error message and/or the close
        var reply = relay.binary(7, 5000);
        assertNotNull(reply);
        assertTrue(reply.type == TunnelProtocol.WS_TEXT && reply.text().contains("error") || reply.type == TunnelProtocol.WS_CLOSE, reply.text());

        // a foreign Origin is refused by the dashboard's cross-site check
        origin.set(1, new com.google.gson.JsonPrimitive("https://evil.example"));
        relay.send(frame(TunnelProtocol.WS_OPEN, 8, open.toString()));
        var rejected = relay.binary(8, 5000);
        assertEquals(TunnelProtocol.WS_REJECTED, rejected.type);
        assertTrue(rejected.text().contains("bad_origin"), rejected.text());
    }

    @Test
    void aRefusedNameStopsRetrying() throws Exception {
        relay.refuse = "name_taken";
        connect("abc");
        awaitState(ConnectStatus.State.FAILED);
        assertEquals("name_taken", client.status().error());
        Thread.sleep(2500);
        assertEquals(1, relay.connections.get(), "no new attempts after a permanent refusal");
    }

    @Test
    void reconnectsAfterTheRelayDropsTheTunnel() throws Exception {
        connect("abc");
        awaitState(ConnectStatus.State.CONNECTED);
        relay.dropAll();
        long until = System.currentTimeMillis() + 10_000;
        while (relay.connections.get() < 2 && System.currentTimeMillis() < until) Thread.sleep(20);
        assertEquals(2, relay.connections.get());
        awaitState(ConnectStatus.State.CONNECTED);
        assertTrue(statuses.stream().anyMatch(s -> s.state() == ConnectStatus.State.RETRYING));
    }

    @Test
    void tunnelConnectionsUseTheRelaysIpAndOnlyTheirOwnHost() throws Exception {
        assertEquals("203.0.113.7", TunnelBridge.sanitize("203.0.113.7"));
        assertEquals("2001:db8::1", TunnelBridge.sanitize("[2001:db8::1]"));
        // tunnel traffic never counts as local
        for (String lan : new String[]{"127.0.0.1", "192.168.1.5", "10.0.0.1", "::1", "fe80::1", "garbage", ""}) {
            assertEquals(TunnelBridge.UNKNOWN_IP, TunnelBridge.sanitize(lan), lan);
        }
        var responses = new LinkedBlockingQueue<FullHttpResponse>();
        var future = new CompletableFuture<Channel>();
        bridge.open("198.51.100.4", "abc.cccloud.dev", new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(1 << 20), new SimpleChannelInboundHandler<FullHttpResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
                        responses.add(msg.retainedDuplicate());
                    }
                });
            }
        }).addListener(f -> future.complete(((io.netty.channel.ChannelFuture) f).channel()));
        var ch = future.get(5, TimeUnit.SECONDS);
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/config");
        req.headers().set("Host", "other.cccloud.dev");
        ch.writeAndFlush(req);
        var res = responses.poll(5, TimeUnit.SECONDS);
        assertNotNull(res);
        assertEquals(421, res.status().code(), "another name is a wrong Host");
        res.release();
    }

    private static ByteBuf frame(int type, long stream, String text) {
        return TunnelProtocol.frame(ByteBufAllocator.DEFAULT, type, stream, text.getBytes(StandardCharsets.UTF_8));
    }

    /** The relay's side of the tunnel, enough for these tests. */
    static final class FakeRelay {
        record Bin(int type, long stream, byte[] payload) {
            String text() {
                return new String(payload, StandardCharsets.UTF_8);
            }
        }

        final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final Channel server;
        final int port;
        final AtomicInteger connections = new AtomicInteger();
        final CompletableFuture<JsonObject> auth = new CompletableFuture<>();
        final List<Channel> tunnels = new CopyOnWriteArrayList<>();
        final List<Bin> received = new ArrayList<>();
        volatile boolean signatureValid;
        volatile String refuse;

        FakeRelay() {
            server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec(), new HttpObjectAggregator(65536),
                            new WebSocketServerProtocolHandler(WebSocketServerProtocolConfig.newBuilder().websocketPath("/tunnel").checkStartsWith(true)
                                .maxFramePayloadLength(32 << 20).build()),
                            new Handler());
                    }
                })
                .bind(new InetSocketAddress("127.0.0.1", 0)).syncUninterruptibly().channel();
            port = ((InetSocketAddress) server.localAddress()).getPort();
        }

        void send(ByteBuf frame) {
            var ch = tunnels.getLast();
            ch.writeAndFlush(new BinaryWebSocketFrame(frame));
        }

        void dropAll() {
            for (var ch : tunnels) ch.close();
        }

        /** The next frame of a stream (waits up to {@code ms}). */
        Bin binary(long stream, long ms) throws InterruptedException {
            long until = System.currentTimeMillis() + ms;
            synchronized (received) {
                while (true) {
                    for (int i = 0; i < received.size(); i++) {
                        if (received.get(i).stream == stream) return received.remove(i);
                    }
                    long left = until - System.currentTimeMillis();
                    if (left <= 0) return null;
                    received.wait(left);
                }
            }
        }

        void close() {
            server.close().syncUninterruptibly();
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }

        final class Handler extends SimpleChannelInboundHandler<WebSocketFrame> {
            private String nonce;

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                    connections.incrementAndGet();
                    tunnels.add(ctx.channel());
                    nonce = Base64.getEncoder().encodeToString(("nonce-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8));
                    ctx.writeAndFlush(new TextWebSocketFrame("{\"t\":\"challenge\",\"nonce\":\"" + nonce + "\",\"v\":1}"));
                }
                super.userEventTriggered(ctx, evt);
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
                if (frame instanceof TextWebSocketFrame text) {
                    if (text.text().equals("ping")) {
                        ctx.writeAndFlush(new TextWebSocketFrame("pong"));
                        return;
                    }
                    var m = JsonParser.parseString(text.text()).getAsJsonObject();
                    if (m.get("t").getAsString().equals("auth")) {
                        String key = m.get("key").getAsString();
                        byte[] der = new byte[44];
                        System.arraycopy(HexFormat.of().parseHex("302a300506032b6570032100"), 0, der, 0, 12);
                        System.arraycopy(Base64.getDecoder().decode(key), 0, der, 12, 32);
                        var v = Signature.getInstance("Ed25519");
                        v.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der)));
                        v.update(TunnelProtocol.signedText(m.get("name").getAsString(), nonce, key).getBytes(StandardCharsets.UTF_8));
                        signatureValid = v.verify(Base64.getDecoder().decode(m.get("sig").getAsString()));
                        auth.complete(m);
                        if (refuse != null) {
                            ctx.writeAndFlush(new TextWebSocketFrame("{\"t\":\"error\",\"code\":\"" + refuse + "\"}"));
                            ctx.close();
                            return;
                        }
                        ctx.writeAndFlush(new TextWebSocketFrame("{\"t\":\"ready\",\"url\":\"https://abc.cccloud.dev\",\"bundle\":true}"));
                    }
                } else if (frame instanceof BinaryWebSocketFrame bin) {
                    var content = bin.content();
                    int type = content.getUnsignedByte(content.readerIndex());
                    long stream = content.getUnsignedInt(content.readerIndex() + 1);
                    byte[] payload = new byte[content.readableBytes() - 5];
                    content.getBytes(content.readerIndex() + 5, payload);
                    synchronized (received) {
                        received.add(new Bin(type, stream, payload));
                        received.notifyAll();
                    }
                }
            }
        }
    }
}
