package ai.ezfox.cccloud.connect;

import ai.ezfox.cccloud.CloudLog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The server's one outbound connection to the CC: Cloud Connect relay ({@code wss://connect.cccloud.dev/tunnel/<name>}).
 * It proves it holds the name's key (signed challenge), announces the mod version and dashboard bundle, then carries
 * the browsers' requests and dashboard WebSockets ({@link TunnelStreams}). It reconnects by itself with a growing
 * delay (1 s … 60 s) and stops for good when the relay refuses the name (taken, reserved, revoked, limits), until an
 * admin changes the config. Keep-alive: a text "ping" every 25 s, which the relay answers without waking up.
 */
public final class ConnectClient {
    /** Largest frame the relay sends us (a browser message or a request with a 257 KB body, plus headers). */
    static final int MAX_FRAME_IN = 2 * 1024 * 1024;
    static final int PING_SECONDS = 25;
    static final int DEAD_SECONDS = 75;
    static final int MAX_BACKOFF_SECONDS = 60;
    /** Relay errors that a retry cannot fix; an admin has to act (other name, release, ask the operator). */
    static final Set<String> PERMANENT = Set.of("name_taken", "reserved", "invalid_name", "revoked", "key_limit", "ip_limit", "bad_signature");

    private final ConnectSettings settings;
    private final String name;
    private final ConnectKeys keys;
    private final String version;
    private final String bundle;
    private final @Nullable String store;
    private final TunnelStreams.Opener opener;
    private final Consumer<ConnectStatus> listener;
    private final boolean releaseOnly;
    private final EventLoopGroup group;
    private final CompletableFuture<String> released = new CompletableFuture<>();
    private volatile @Nullable Channel channel;
    private volatile @Nullable TunnelStreams streams;
    private volatile boolean stopped;
    private volatile ConnectStatus status;
    private volatile @Nullable String lastError;
    private volatile boolean permanent;
    private int attempt;

    /**
     * @param releaseOnly connect only to give the name back ({@link #released()} completes, nothing else happens)
     */
    public ConnectClient(ConnectSettings settings, String name, ConnectKeys keys, String version, String bundle, @Nullable String store,
                         TunnelStreams.Opener opener, Consumer<ConnectStatus> listener, boolean releaseOnly) {
        this.settings = settings;
        this.name = name;
        this.keys = keys;
        this.version = version.length() > 64 ? version.substring(0, 64) : version;
        this.bundle = bundle;
        this.store = store;
        this.opener = opener;
        this.listener = listener;
        this.releaseOnly = releaseOnly;
        this.group = new MultiThreadIoEventLoopGroup(1, new DefaultThreadFactory("cccloud-connect", true), NioIoHandler.newFactory());
        this.status = new ConnectStatus(ConnectStatus.State.CONNECTING, name, expectedUrl(settings, name), null, true, 0, System.currentTimeMillis(), 0);
    }

    /** {@code https://<name>.cccloud.dev} for the relay {@code wss://connect.cccloud.dev} (before the relay confirms it). */
    public static String expectedUrl(ConnectSettings settings, String name) {
        URI relay = settings.relay();
        String host = relay.getHost();
        String root = host.startsWith("connect.") ? host.substring("connect.".length()) : host;
        boolean tls = "wss".equals(relay.getScheme());
        int port = relay.getPort();
        return (tls ? "https://" : "http://") + name + "." + root + (port > 0 && port != (tls ? 443 : 80) ? ":" + port : "");
    }

    public ConnectStatus status() {
        var s = streams;
        var st = status;
        return s == null || st.browsers() == s.browsers() ? st
            : new ConnectStatus(st.state(), st.name(), st.url(), st.error(), st.bundleKnown(), s.browsers(), st.since(), st.retryAt());
    }

    public String name() {
        return name;
    }

    public ConnectSettings settings() {
        return settings;
    }

    /** Completes with "released" (or the relay's error code) after {@link #release()} or a release-only client. */
    public CompletableFuture<String> released() {
        return released;
    }

    public void start() {
        group.execute(this::connect);
    }

    public void stop() {
        stopped = true;
        var ch = channel;
        if (ch != null) ch.close();
        var s = streams;
        if (s != null) s.closeAll();
        group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        released.complete("stopped");
    }

    /** Gives the name back to the relay (it becomes free for others). */
    public CompletableFuture<String> release() {
        var ch = channel;
        if (ch == null || status.state() != ConnectStatus.State.CONNECTED) {
            released.complete("not_connected");
        } else {
            var json = new JsonObject();
            json.addProperty("t", "release");
            ch.writeAndFlush(new TextWebSocketFrame(json.toString()));
            group.schedule(() -> released.complete("timeout"), 10, TimeUnit.SECONDS);
        }
        return released;
    }

    private void update(ConnectStatus next) {
        var before = status;
        status = next;
        if (before.state() != next.state() || !java.util.Objects.equals(before.error(), next.error())) listener.accept(next);
    }

    private void connect() {
        if (stopped) return;
        update(new ConnectStatus(ConnectStatus.State.CONNECTING, name, status.url(), status.error(), status.bundleKnown(), 0, System.currentTimeMillis(), 0));
        lastError = null;
        URI uri = settings.tunnelUri(name);
        boolean tls = "wss".equals(uri.getScheme());
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : tls ? 443 : 80;
        SslContext ssl;
        try {
            ssl = tls ? SslContextBuilder.forClient().endpointIdentificationAlgorithm("HTTPS").build() : null;
        } catch (Exception e) {
            CloudLog.LOGGER.error("[cccloud] Connect: TLS is not available: {}", e.toString());
            disconnected("tls");
            return;
        }
        var headers = new DefaultHttpHeaders();
        headers.set(HttpHeaderNames.USER_AGENT, "CCCloud/" + version + " (Connect)");
        var handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, false, headers, MAX_FRAME_IN, true, false,
            10_000, false, false);
        new Bootstrap().group(group).channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(512 * 1024, 2 * 1024 * 1024))
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    if (ssl != null) ch.pipeline().addLast("ssl", ssl.newHandler(ch.alloc(), host, port));
                    ch.pipeline().addLast("http", new HttpClientCodec(4096, 8192, 64 * 1024));
                    ch.pipeline().addLast("aggregator", new HttpObjectAggregator(64 * 1024));
                    ch.pipeline().addLast("idle", new IdleStateHandler(DEAD_SECONDS, PING_SECONDS, 0));
                    ch.pipeline().addLast("tunnel", new Handler(handshaker));
                }
            })
            .connect(host, port)
            .addListener((ChannelFuture f) -> {
                if (!f.isSuccess()) {
                    lastError = "unreachable";
                    if (attempt == 0) CloudLog.LOGGER.info("[cccloud] Connect: cannot reach {} ({})", host, String.valueOf(f.cause()));
                    disconnected("unreachable");
                } else {
                    channel = f.channel();
                    f.channel().closeFuture().addListener(c -> disconnected(lastError != null ? lastError : "disconnected"));
                }
            });
    }

    private void disconnected(String reason) {
        channel = null;
        var s = streams;
        streams = null;
        if (s != null) s.closeAll();
        if (stopped || releaseOnly) {
            released.complete(reason);
            return;
        }
        if (permanent) {
            update(new ConnectStatus(ConnectStatus.State.FAILED, name, status.url(), reason, status.bundleKnown(), 0, System.currentTimeMillis(), 0));
            return;
        }
        int seconds = Math.min(MAX_BACKOFF_SECONDS, 1 << Math.min(attempt, 6));
        if ("rate_limited".equals(reason) || "too_many_attempts".equals(reason)) seconds = MAX_BACKOFF_SECONDS;
        attempt++;
        long delayMs = (long) (seconds * 1000 * (0.8 + 0.4 * ThreadLocalRandom.current().nextDouble()));
        update(new ConnectStatus(ConnectStatus.State.RETRYING, name, status.url(), reason, status.bundleKnown(), 0, System.currentTimeMillis(),
            System.currentTimeMillis() + delayMs));
        try {
            group.schedule(this::connect, delayMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // stopping
        }
    }

    /** The tunnel's sink for {@link TunnelStreams}: binary frames on the WebSocket. */
    private final class Sink implements TunnelStreams.Sink {
        private final Channel ch;

        Sink(Channel ch) {
            this.ch = ch;
        }

        @Override
        public ByteBufAllocator alloc() {
            return ch.alloc();
        }

        @Override
        public void send(ByteBuf frame) {
            if (!ch.isActive()) {
                frame.release();
                return;
            }
            ch.writeAndFlush(new BinaryWebSocketFrame(frame));
        }

        @Override
        public long backlog() {
            var buffer = ch.unsafe().outboundBuffer();
            return buffer == null ? 0 : buffer.totalPendingWriteBytes();
        }
    }

    private final class Handler extends ChannelInboundHandlerAdapter {
        private final WebSocketClientHandshaker handshaker;

        Handler(WebSocketClientHandshaker handshaker) {
            this.handshaker = handshaker;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            handshaker.handshake(ctx.channel());
            super.channelActive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                if (!handshaker.isHandshakeComplete()) {
                    if (msg instanceof FullHttpResponse response) handshake(ctx, response);
                    return;
                }
                if (msg instanceof BinaryWebSocketFrame binary) {
                    var s = streams;
                    var frame = TunnelProtocol.parse(binary.content());
                    if (s != null && frame != null) s.handle(frame);
                } else if (msg instanceof TextWebSocketFrame text) {
                    control(ctx, text.text());
                } else if (msg instanceof PingWebSocketFrame ping) {
                    ctx.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
                } else if (msg instanceof CloseWebSocketFrame close) {
                    if (lastError == null && close.statusCode() == 4000) lastError = "replaced";
                    ctx.close();
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        private void handshake(ChannelHandlerContext ctx, FullHttpResponse response) {
            try {
                handshaker.finishHandshake(ctx.channel(), response);
                if (ctx.pipeline().get("ws-decoder") != null) ctx.pipeline().addAfter("ws-decoder", "ws-aggregator", new WebSocketFrameAggregator(MAX_FRAME_IN));
            } catch (WebSocketHandshakeException e) {
                // the relay refused before the WebSocket (bad or reserved name, rate limit)
                String code = "relay_error";
                try {
                    var json = JsonParser.parseString(response.content().toString(StandardCharsets.UTF_8));
                    if (json.isJsonObject() && json.getAsJsonObject().get("error") != null) code = json.getAsJsonObject().get("error").getAsString();
                } catch (RuntimeException ignored) {
                    // not JSON
                }
                if (response.status().code() == 429) code = "rate_limited";
                fail(ctx, code.matches("[a-z_]{1,40}") ? code : "relay_error");
            }
        }

        private void fail(ChannelHandlerContext ctx, String code) {
            lastError = code;
            if (PERMANENT.contains(code)) permanent = true;
            CloudLog.LOGGER.warn("[cccloud] Connect: the relay refused {}: {}", name, code);
            if (releaseOnly) released.complete(code);
            ctx.close();
        }

        private void control(ChannelHandlerContext ctx, String text) {
            if (text.equals("pong")) return;
            JsonObject m;
            try {
                var json = JsonParser.parseString(text);
                if (!json.isJsonObject()) return;
                m = json.getAsJsonObject();
            } catch (RuntimeException e) {
                return;
            }
            String t = m.has("t") && m.get("t").isJsonPrimitive() ? m.get("t").getAsString() : "";
            switch (t) {
                case "challenge" -> {
                    String nonce = m.has("nonce") ? m.get("nonce").getAsString() : "";
                    if (nonce.length() < 16 || nonce.length() > 200) {
                        fail(ctx, "protocol");
                        return;
                    }
                    var auth = new JsonObject();
                    auth.addProperty("t", "auth");
                    auth.addProperty("name", name);
                    auth.addProperty("key", keys.publicKey());
                    auth.addProperty("sig", keys.sign(TunnelProtocol.signedText(name, nonce, keys.publicKey())));
                    auth.addProperty("version", version.replaceAll("[^0-9A-Za-z.+_-]", "_"));
                    auth.addProperty("bundle", bundle);
                    if (store != null) auth.addProperty("store", store);
                    if (releaseOnly) auth.addProperty("release", true);
                    ctx.writeAndFlush(new TextWebSocketFrame(auth.toString()));
                }
                case "ready" -> {
                    attempt = 0;
                    permanent = false;
                    String url = m.has("url") ? m.get("url").getAsString() : status.url();
                    boolean known = !m.has("bundle") || m.get("bundle").getAsBoolean();
                    String host = URI.create(url).getHost();
                    streams = new TunnelStreams(new Sink(ctx.channel()), opener, host == null ? name : host.toLowerCase(Locale.ROOT));
                    update(new ConnectStatus(ConnectStatus.State.CONNECTED, name, url, null, known, 0, System.currentTimeMillis(), 0));
                    if (!known) CloudLog.LOGGER.warn("[cccloud] Connect: the relay has no dashboard for this mod build (bundle {}); browsers see "
                        + "\"Update CC: Cloud\". Released versions of CC: Cloud are published there.", bundle);
                }
                case "released" -> {
                    CloudLog.LOGGER.info("[cccloud] Connect: released the name {}", name);
                    stopped = true;
                    released.complete("released");
                    ctx.close();
                }
                case "error" -> fail(ctx, m.has("code") ? m.get("code").getAsString().replaceAll("[^a-z_]", "") : "relay_error");
                default -> {
                    // newer relays may send more; ignored
                }
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent idle) {
                if (idle.state() == IdleState.WRITER_IDLE && handshaker.isHandshakeComplete()) {
                    ctx.writeAndFlush(new TextWebSocketFrame("ping"));
                } else if (idle.state() == IdleState.READER_IDLE) {
                    lastError = "timeout";
                    ctx.close();
                }
                return;
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (lastError == null) lastError = cause instanceof javax.net.ssl.SSLException ? "tls" : "network";
            CloudLog.LOGGER.debug("[cccloud] Connect: tunnel error", cause);
            ctx.close();
        }
    }
}
