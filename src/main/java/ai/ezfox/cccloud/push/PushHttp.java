package ai.ezfox.cccloud.push;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.jspecify.annotations.Nullable;

/**
 * The outgoing HTTP POST of push notifications, with the SSRF checks of {@link UrlPolicy} at send time: the host is
 * resolved once, every address must be public (unless the server allow-listed the host), and the connection goes to
 * that checked address – no second lookup (no DNS rebinding). TLS verifies the certificate and the host name.
 * Redirects are never followed; timeouts 5 s for connect and for the answer. Blocking DNS: call from a worker thread.
 */
public final class PushHttp {
    public static final int TIMEOUT_MS = 5000;

    /** status = HTTP status, or 0 with an error ({@code unresolved}, {@code blocked_address}, {@code connect_failed}, {@code timeout}, {@code tls}, {@code io}). */
    public record Result(int status, @Nullable String error) {
        public boolean ok() {
            return error == null && status >= 200 && status < 300;
        }

        /** Network problems, 429 and 5xx are worth another try. */
        public boolean retryable() {
            if (error != null) return !error.equals("blocked_address") && !error.equals("unresolved") && !error.equals("tls");
            return status == 429 || status >= 500;
        }

        public String code() {
            return error != null ? error : ok() ? "ok" : "http_" + status;
        }
    }

    /** Resolves a host name (replaceable in tests). */
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Resolver resolver;
    private @Nullable MultiThreadIoEventLoopGroup group;
    private @Nullable SslContext ssl;

    public PushHttp(Resolver resolver) {
        this.resolver = resolver;
    }

    public PushHttp() {
        this(InetAddress::getAllByName);
    }

    /** The address to connect to, or null if the host must not be contacted. */
    public static @Nullable InetAddress pick(InetAddress[] addresses, boolean trusted) {
        if (addresses.length == 0) return null;
        if (!trusted) for (var a : addresses) if (!UrlPolicy.isPublic(a)) return null; // all of them must be public
        return addresses[0];
    }

    public Result post(String url, PushFormat.Request request, Set<String> allowedHosts) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (java.net.URISyntaxException e) {
            return new Result(0, "invalid_url");
        }
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort() > 0 ? uri.getPort() : https ? 443 : 80;
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host.startsWith("[") ? host.substring(1, host.length() - 1) : host);
        } catch (UnknownHostException e) {
            return new Result(0, "unresolved");
        }
        var address = pick(addresses, UrlPolicy.allowed(host, allowedHosts));
        if (address == null) return new Result(0, "blocked_address");
        try {
            return send(new InetSocketAddress(address, port), host, port, https, uri, request).get(3L * TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return new Result(0, "timeout");
        } catch (Exception e) {
            return new Result(0, "io");
        }
    }

    private synchronized MultiThreadIoEventLoopGroup group() {
        if (group == null) group = new MultiThreadIoEventLoopGroup(1, new DefaultThreadFactory("cccloud-push-io", true), NioIoHandler.newFactory());
        return group;
    }

    private synchronized SslContext ssl() throws SSLException {
        if (ssl == null) ssl = SslContextBuilder.forClient().endpointIdentificationAlgorithm("HTTPS").build();
        return ssl;
    }

    private CompletableFuture<Result> send(InetSocketAddress target, String host, int port, boolean https, URI uri, PushFormat.Request request) throws SSLException {
        var future = new CompletableFuture<Result>();
        SslContext context = https ? ssl() : null;
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
        var httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, path, Unpooled.wrappedBuffer(request.body()));
        boolean defaultPort = uri.getPort() <= 0;
        httpRequest.headers().set(HttpHeaderNames.HOST, defaultPort ? host : host + ":" + port);
        httpRequest.headers().set(HttpHeaderNames.CONNECTION, "close");
        httpRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, request.body().length);
        request.headers().forEach((k, v) -> httpRequest.headers().set(k, v));
        var bootstrap = new Bootstrap()
            .group(group())
            .channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT_MS)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    if (context != null) ch.pipeline().addLast(context.newHandler(ch.alloc(), host.startsWith("[") ? host.substring(1, host.length() - 1) : host, port));
                    ch.pipeline().addLast(new ReadTimeoutHandler(TIMEOUT_MS, TimeUnit.MILLISECONDS));
                    ch.pipeline().addLast(new HttpClientCodec());
                    ch.pipeline().addLast(new HttpObjectAggregator(64 * 1024));
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
                            future.complete(new Result(msg.status().code(), null)); // 3xx is not followed: counts as a failure
                            ctx.close();
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            String code = cause instanceof io.netty.handler.timeout.ReadTimeoutException ? "timeout"
                                : cause instanceof SSLException || cause.getCause() instanceof SSLException ? "tls" : "io";
                            future.complete(new Result(0, code));
                            ctx.close();
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) {
                            future.complete(new Result(0, "io"));
                        }
                    });
                }
            });
        bootstrap.connect(target).addListener((io.netty.channel.ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                future.complete(new Result(0, f.cause() instanceof io.netty.channel.ConnectTimeoutException ? "timeout" : "connect_failed"));
                return;
            }
            f.channel().writeAndFlush(httpRequest).addListener(w -> {
                if (!w.isSuccess()) future.complete(new Result(0, "io"));
            });
        });
        return future;
    }

    public synchronized void shutdown() {
        if (group != null) group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        group = null;
    }
}
