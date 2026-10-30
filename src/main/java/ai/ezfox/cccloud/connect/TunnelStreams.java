package ai.ezfox.cccloud.connect;

import ai.ezfox.cccloud.CloudLog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
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
import io.netty.util.ReferenceCountUtil;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * The streams of one tunnel: every browser request and dashboard WebSocket the relay forwards becomes an in-memory
 * connection into the dashboard server (opened through {@link Opener}, in the game: the {@code TunnelBridge}). The
 * local side speaks HTTP and WebSocket exactly like a browser, so the server applies all of its usual checks.
 * <p>
 * Like a browser's connection pool, at most {@link #MAX_HTTP_PER_CLIENT} requests of one client IP run at once; more
 * wait here (the relay lets up to 64 through per name, the dashboard server allows 24 connections per IP). A tunnel that
 * cannot send fast enough ({@link Sink#backlog()} over {@link #MAX_BACKLOG}) drops its dashboard WebSockets with 1013,
 * like a slow browser on the LAN; the dashboards reconnect and get a fresh snapshot.
 */
public final class TunnelStreams {
    /** HTTP requests of one client IP that run at the same time. */
    static final int MAX_HTTP_PER_CLIENT = 12;
    /** Requests waiting for a slot, in total. */
    static final int MAX_WAITING = 256;
    /** One answer at most (the relay's limit). */
    static final long MAX_RESPONSE = 16L * 1024 * 1024;
    /** Body chunk size on the tunnel. */
    static final int CHUNK = 64 * 1024;
    /** A dashboard message at most (server to browser). */
    static final int MAX_MESSAGE = 16 * 1024 * 1024;
    /** Browser messages kept while the local WebSocket handshake is still running. */
    static final int MAX_EARLY_MESSAGES = 64;
    /** Bytes queued on the tunnel before dashboard WebSockets are dropped as too slow. */
    static final long MAX_BACKLOG = 8L * 1024 * 1024;
    /** Request and response headers that belong to one connection, not to the request. */
    static final Set<String> HOP_BY_HOP = Set.of("connection", "keep-alive", "proxy-connection", "transfer-encoding", "te", "trailer", "upgrade",
        "content-length", "host", "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto", "x-real-ip", "cf-connecting-ip",
        "proxy-authorization", "proxy-authenticate");

    /** Where frames to the relay go. */
    public interface Sink {
        ByteBufAllocator alloc();

        /** Sends one binary frame (takes ownership of the buffer). */
        void send(ByteBuf frame);

        /** Bytes waiting to be written to the relay. */
        long backlog();
    }

    /** Opens an in-memory connection into the dashboard server for a client IP and Host. */
    public interface Opener {
        ChannelFuture open(String clientIp, String host, ChannelHandler handler);
    }

    private final Sink sink;
    private final Opener opener;
    private final String host;
    private final Map<Long, Stream> streams = new ConcurrentHashMap<>();
    private final Map<String, Gate> gates = new HashMap<>();
    private int waiting;

    public TunnelStreams(Sink sink, Opener opener, String host) {
        this.sink = sink;
        this.opener = opener;
        this.host = host.toLowerCase(Locale.ROOT);
    }

    /** Open dashboard WebSockets (browser tabs) through the tunnel. */
    public int browsers() {
        int n = 0;
        for (var s : streams.values()) if (s instanceof WsStream) n++;
        return n;
    }

    public int open() {
        return streams.size();
    }

    /** Handles one binary frame from the relay (the frame's buffer is only valid during this call). */
    public void handle(TunnelProtocol.Frame f) {
        switch (f.type()) {
            case TunnelProtocol.HTTP_REQUEST -> {
                var request = TunnelProtocol.request(f.payload());
                if (request == null || streams.containsKey(f.stream())) {
                    sink.send(TunnelProtocol.frame(sink.alloc(), TunnelProtocol.HTTP_ERROR, f.stream(), "bad_request".getBytes(StandardCharsets.UTF_8)));
                    return;
                }
                byte[] body = new byte[request.body().readableBytes()];
                request.body().getBytes(request.body().readerIndex(), body);
                var stream = new HttpStream(f.stream(), request.head(), body);
                streams.put(f.stream(), stream);
                admit(stream);
            }
            case TunnelProtocol.WS_OPEN -> {
                var head = TunnelProtocol.head(f.payload().toString(StandardCharsets.UTF_8));
                if (head == null || streams.containsKey(f.stream())) {
                    sink.send(TunnelProtocol.closeFrame(sink.alloc(), f.stream(), 1008, "bad_request"));
                    return;
                }
                var stream = new WsStream(f.stream(), head);
                streams.put(f.stream(), stream);
                stream.start();
            }
            case TunnelProtocol.WS_TEXT, TunnelProtocol.WS_BINARY -> {
                if (streams.get(f.stream()) instanceof WsStream ws) {
                    ws.fromBrowser(f.type() == TunnelProtocol.WS_TEXT, f.payload());
                } else {
                    sink.send(TunnelProtocol.closeFrame(sink.alloc(), f.stream(), 1001, "gone"));
                }
            }
            case TunnelProtocol.WS_CLOSE -> {
                if (streams.get(f.stream()) instanceof WsStream ws) ws.closeFromBrowser(TunnelProtocol.closeCode(f.payload()), TunnelProtocol.closeReason(f.payload()));
            }
            case TunnelProtocol.CANCEL -> {
                var s = streams.get(f.stream());
                if (s != null) s.cancel();
            }
            default -> {
                // newer relays may send more types; unknown ones are ignored
            }
        }
    }

    /** The tunnel is gone: every local connection ends (the relay closes the browser side). */
    public void closeAll() {
        for (var s : streams.values()) s.cancel();
        streams.clear();
        synchronized (gates) {
            gates.clear();
            waiting = 0;
        }
    }

    // ---- per-client limit -----------------------------------------------------------------------------------------------

    private static final class Gate {
        int active;
        final ArrayDeque<HttpStream> queue = new ArrayDeque<>();
    }

    private void admit(HttpStream stream) {
        boolean start;
        synchronized (gates) {
            var gate = gates.computeIfAbsent(stream.head.ip(), k -> new Gate());
            if (gate.active < MAX_HTTP_PER_CLIENT) {
                gate.active++;
                start = true;
            } else if (waiting < MAX_WAITING) {
                gate.queue.add(stream);
                waiting++;
                start = false;
            } else {
                start = false;
                stream.busy();
                return;
            }
        }
        if (start) stream.start();
    }

    private void release(HttpStream stream) {
        HttpStream next = null;
        synchronized (gates) {
            var gate = gates.get(stream.head.ip());
            if (gate == null) return;
            if (gate.queue.remove(stream)) {
                waiting--;
                return;
            }
            gate.active--;
            while (!gate.queue.isEmpty()) {
                var candidate = gate.queue.poll();
                waiting--;
                if (streams.containsKey(candidate.id)) {
                    next = candidate;
                    gate.active++;
                    break;
                }
            }
            if (gate.active <= 0 && gate.queue.isEmpty()) gates.remove(stream.head.ip());
        }
        if (next != null) next.start();
    }

    // ---- streams --------------------------------------------------------------------------------------------------------

    private abstract class Stream extends ChannelInboundHandlerAdapter {
        final long id;
        final TunnelProtocol.Head head;
        volatile @Nullable Channel channel;
        volatile boolean finished;

        Stream(long id, TunnelProtocol.Head head) {
            this.id = id;
            this.head = head;
        }

        abstract void cancel();

        void connect(ChannelHandler... handlers) {
            var self = this;
            opener.open(head.ip(), host, new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(handlers);
                    ch.pipeline().addLast("stream", self);
                }
            }).addListener((ChannelFuture f) -> {
                if (!f.isSuccess()) {
                    failed("unavailable");
                    return;
                }
                channel = f.channel();
                if (finished) f.channel().close();
            });
        }

        abstract void failed(String reason);

        /** Headers for the dashboard server: the browser's (as the relay allowed them) without connection headers. */
        DefaultHttpHeaders headers() {
            var h = new DefaultHttpHeaders();
            for (var e : head.headers()) if (!HOP_BY_HOP.contains(e.getKey())) h.add(e.getKey(), e.getValue());
            h.set(HttpHeaderNames.HOST, host);
            return h;
        }
    }

    /** One HTTP request: one in-memory connection, closed after the answer. */
    private final class HttpStream extends Stream {
        private final byte[] body;
        private long size;
        private boolean headSent;

        HttpStream(long id, TunnelProtocol.Head head, byte[] body) {
            super(id, head);
            this.body = body;
        }

        void start() {
            if (finished) return;
            connect(new HttpClientCodec(4096, 16 * 1024, CHUNK));
        }

        /** Too many requests waiting: answered at once. */
        void busy() {
            var json = new JsonObject();
            json.addProperty("error", "busy");
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            sink.send(TunnelProtocol.jsonFrame(sink.alloc(), TunnelProtocol.HTTP_HEAD, id,
                TunnelProtocol.responseHead(503, java.util.List.of(Map.entry("content-type", "application/json; charset=utf-8"), Map.entry("retry-after", "1")))));
            sink.send(TunnelProtocol.frame(sink.alloc(), TunnelProtocol.HTTP_BODY, id, bytes));
            sink.send(TunnelProtocol.frame(sink.alloc(), TunnelProtocol.HTTP_END, id, (ByteBuf) null));
            finished = true;
            streams.remove(id);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
            if (finished) {
                ctx.close();
                return;
            }
            var method = HttpMethod.valueOf(head.method());
            var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, head.path(), Unpooled.wrappedBuffer(body));
            request.headers().set(headers());
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(request);
            super.channelActive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                if (finished) return;
                if (msg instanceof HttpResponse response && !headSent) {
                    headSent = true;
                    var list = new java.util.ArrayList<Map.Entry<String, String>>();
                    for (var e : response.headers()) {
                        String name = e.getKey().toLowerCase(Locale.ROOT);
                        if (!HOP_BY_HOP.contains(name)) list.add(Map.entry(name, e.getValue()));
                    }
                    sink.send(TunnelProtocol.jsonFrame(sink.alloc(), TunnelProtocol.HTTP_HEAD, id, TunnelProtocol.responseHead(response.status().code(), list)));
                }
                if (msg instanceof HttpContent content) {
                    var data = content.content();
                    size += data.readableBytes();
                    if (size > MAX_RESPONSE) {
                        fail("too_large");
                        ctx.close();
                        return;
                    }
                    for (int at = data.readerIndex(); at < data.writerIndex(); at += CHUNK) {
                        int n = Math.min(CHUNK, data.writerIndex() - at);
                        sink.send(TunnelProtocol.frame(sink.alloc(), TunnelProtocol.HTTP_BODY, id, data.slice(at, n)));
                    }
                    if (msg instanceof LastHttpContent) {
                        finished = true;
                        sink.send(TunnelProtocol.frame(sink.alloc(), TunnelProtocol.HTTP_END, id, (ByteBuf) null));
                        done();
                        ctx.close();
                    }
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (!finished) fail("closed");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            CloudLog.LOGGER.debug("[cccloud] Connect: local request failed", cause);
            ctx.close();
        }

        private void fail(String reason) {
            if (finished) return;
            finished = true;
            sink.send(TunnelProtocol.frame(sink.alloc(), TunnelProtocol.HTTP_ERROR, id, reason.getBytes(StandardCharsets.UTF_8)));
            done();
        }

        @Override
        void failed(String reason) {
            fail(reason);
        }

        private void done() {
            if (streams.remove(id) != null) release(this);
        }

        @Override
        void cancel() {
            finished = true;
            var ch = channel;
            if (ch != null) ch.close();
            done();
        }
    }

    /** One dashboard WebSocket: the in-memory connection does the WebSocket handshake like a browser. */
    private final class WsStream extends Stream {
        private @Nullable WebSocketClientHandshaker handshaker;
        private final ArrayDeque<Object> early = new ArrayDeque<>();
        private volatile boolean accepted;
        private boolean closeSent;

        WsStream(long id, TunnelProtocol.Head head) {
            super(id, head);
        }

        void start() {
            connect(new HttpClientCodec(4096, 16 * 1024, CHUNK), new HttpObjectAggregator(64 * 1024));
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
            if (finished) {
                ctx.close();
                return;
            }
            var uri = URI.create("ws://" + host + head.path());
            var h = headers();
            // The browser's Origin decides the server's cross-site check; without one (no browser) none is invented.
            handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, false, h, MAX_MESSAGE, true, false, 10_000, false, false);
            handshaker.handshake(ctx.channel());
            super.channelActive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                var hs = handshaker;
                if (hs != null && !hs.isHandshakeComplete()) {
                    if (msg instanceof FullHttpResponse response) handshakeDone(ctx, hs, response);
                    return;
                }
                if (msg instanceof TextWebSocketFrame text) {
                    toBrowser(TunnelProtocol.WS_TEXT, text.content());
                } else if (msg instanceof BinaryWebSocketFrame binary) {
                    toBrowser(TunnelProtocol.WS_BINARY, binary.content());
                } else if (msg instanceof PingWebSocketFrame ping) {
                    ctx.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
                } else if (msg instanceof CloseWebSocketFrame close) {
                    sendClose(close.statusCode() < 0 ? 1000 : close.statusCode(), close.reasonText());
                    ctx.close();
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        private void handshakeDone(ChannelHandlerContext ctx, WebSocketClientHandshaker hs, FullHttpResponse response) {
            try {
                hs.finishHandshake(ctx.channel(), response);
            } catch (WebSocketHandshakeException e) {
                var json = new JsonObject();
                json.addProperty("s", response.status().code());
                json.addProperty("e", errorOf(response));
                sink.send(TunnelProtocol.jsonFrame(sink.alloc(), TunnelProtocol.WS_REJECTED, id, json));
                closeSent = true;
                finished = true;
                streams.remove(id);
                ctx.close();
                return;
            }
            var p = ctx.pipeline();
            if (p.get("ws-decoder") != null) p.addAfter("ws-decoder", "ws-aggregator", new WebSocketFrameAggregator(MAX_MESSAGE));
            accepted = true;
            sink.send(TunnelProtocol.frame(sink.alloc(), TunnelProtocol.WS_ACCEPTED, id, (ByteBuf) null));
            synchronized (early) {
                while (!early.isEmpty()) ctx.write(early.poll());
            }
            ctx.flush();
        }

        private String errorOf(FullHttpResponse response) {
            try {
                var json = JsonParser.parseString(response.content().toString(StandardCharsets.UTF_8));
                if (json.isJsonObject() && json.getAsJsonObject().get("error") != null) {
                    String e = json.getAsJsonObject().get("error").getAsString();
                    if (e.matches("[a-z_]{1,40}")) return e;
                }
            } catch (RuntimeException ignored) {
                // not JSON
            }
            return "rejected";
        }

        private void toBrowser(int type, ByteBuf content) {
            if (sink.backlog() > MAX_BACKLOG) {
                // the tunnel cannot keep up (slow upload): like a slow browser on the LAN, the dashboard reconnects
                sendClose(1013, "too slow");
                var ch = channel;
                if (ch != null) ch.close();
                return;
            }
            sink.send(TunnelProtocol.frame(sink.alloc(), type, id, content));
        }

        void fromBrowser(boolean text, ByteBuf payload) {
            Object frame = text ? new TextWebSocketFrame(payload.toString(StandardCharsets.UTF_8))
                : new BinaryWebSocketFrame(Unpooled.copiedBuffer(payload));
            var ch = channel;
            if (accepted && ch != null) {
                ch.writeAndFlush(frame);
                return;
            }
            synchronized (early) {
                if (early.size() >= MAX_EARLY_MESSAGES) {
                    ReferenceCountUtil.release(frame);
                    closeFromBrowser(1008, "too_early");
                    return;
                }
                early.add(frame);
            }
            // the handshake may have finished meanwhile
            if (accepted && ch != null) {
                synchronized (early) {
                    while (!early.isEmpty()) ch.write(early.poll());
                }
                ch.flush();
            }
        }

        void closeFromBrowser(int code, String reason) {
            closeSent = true;
            finished = true;
            streams.remove(id);
            var ch = channel;
            if (ch == null) return;
            if (accepted) {
                int c = code >= 1000 && code <= 4999 && code != 1005 && code != 1006 && code != 1015 ? code : 1000;
                ch.writeAndFlush(new CloseWebSocketFrame(c, reason)).addListener(f -> ch.close());
            } else {
                ch.close();
            }
        }

        private void sendClose(int code, String reason) {
            if (closeSent) return;
            closeSent = true;
            finished = true;
            streams.remove(id);
            sink.send(TunnelProtocol.closeFrame(sink.alloc(), id, code, reason));
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            synchronized (early) {
                while (!early.isEmpty()) ReferenceCountUtil.release(early.poll());
            }
            sendClose(1001, "server_closed");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            CloudLog.LOGGER.debug("[cccloud] Connect: local WebSocket failed", cause);
            ctx.close();
        }

        @Override
        void failed(String reason) {
            sendClose(1011, reason);
        }

        @Override
        void cancel() {
            closeSent = true;
            finished = true;
            streams.remove(id);
            var ch = channel;
            if (ch != null) ch.close();
        }
    }
}
