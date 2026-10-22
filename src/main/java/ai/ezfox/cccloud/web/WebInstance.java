package ai.ezfox.cccloud.web;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/** State of one running dashboard server (also constructed directly by unit tests). */
final class WebInstance {
    final GameBridge bridge;
    final AuthStore auth;
    final @Nullable SslContext ssl;
    final List<IpUtil.Cidr> trustedProxies;
    final boolean allowInternet;
    final ConnectionLimiter limiter;
    /** /api/icon: 200 requests per minute per IP. */
    final java.util.concurrent.ConcurrentHashMap<String, TokenBucket> iconLimits = new java.util.concurrent.ConcurrentHashMap<>();
    final FailureLimiter pairLimiter = new FailureLimiter(5, 60_000, System::currentTimeMillis);
    /** PIN hashing is slow on purpose, so it runs on its own thread with a small bounded queue. */
    final ThreadPoolExecutor pinExecutor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32),
        new DefaultThreadFactory("cccloud-pin", true));
    final String url;
    final int port;
    volatile Set<String> bannedIps = Set.of();
    @Nullable EventLoopGroup group;
    @Nullable Channel channel;
    @Nullable ScheduledFuture<?> flushTask;
    @Nullable ScheduledFuture<?> cleanupTask;

    WebInstance(GameBridge bridge, AuthStore auth, @Nullable SslContext ssl, List<IpUtil.Cidr> trustedProxies, boolean allowInternet, String url, int port) {
        this.bridge = bridge;
        this.auth = auth;
        this.ssl = ssl;
        this.trustedProxies = trustedProxies;
        this.allowInternet = allowInternet;
        this.url = url;
        this.port = port;
        this.limiter = new ConnectionLimiter(this);
        pinExecutor.allowCoreThreadTimeOut(true);
    }

    /** Sets up the per-connection pipeline. */
    void initPipeline(Channel ch) {
        ChannelPipeline p = ch.pipeline();
        p.addLast("limit", limiter);
        if (ssl != null) p.addLast("ssl", ssl.newHandler(ch.alloc()));
        p.addLast("idle", new IdleStateHandler(CloudWebServer.HANDSHAKE_TIMEOUT_S, 0, 0));
        p.addLast("http", new HttpServerCodec(4096, 8192, 8192));
        p.addLast("aggregator", new HttpObjectAggregator(ClientMessages.MAX_BODY));
        p.addLast("handler", new HttpHandler(this));
    }

    void shutdownExecutors() {
        pinExecutor.shutdownNow();
    }
}
