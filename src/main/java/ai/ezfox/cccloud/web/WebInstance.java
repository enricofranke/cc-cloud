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
    /** Roles and share links (share logins are checked here, off the server thread; the store is thread-safe). */
    final ai.ezfox.cccloud.access.AccessStore access;
    final @Nullable SslContext ssl;
    final List<IpUtil.Cidr> trustedProxies;
    /** Host names allowed besides IP literals, localhost and LAN names (publicUrl host + webAllowedHosts). */
    volatile java.util.Set<String> allowedHosts = java.util.Set.of();
    /** Connections per resolved client IP behind a trusted proxy (security review L7). */
    final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> proxiedClients = new java.util.concurrent.ConcurrentHashMap<>();
    /** Config {@code webTrustCloudflareHeader}. */
    volatile boolean trustCloudflare;
    final boolean allowInternet;
    final ConnectionLimiter limiter;
    /** /api/icon: 200 requests per minute per IP. */
    final java.util.concurrent.ConcurrentHashMap<String, TokenBucket> iconLimits = new java.util.concurrent.ConcurrentHashMap<>();
    /** /api/recipes: 60 requests per minute per IP. */
    final java.util.concurrent.ConcurrentHashMap<String, TokenBucket> recipeLimits = new java.util.concurrent.ConcurrentHashMap<>();
    final FailureLimiter pairLimiter = new FailureLimiter(5, 60_000, System::currentTimeMillis);
    /** v0.6 API: requests per minute per IP. */
    final java.util.concurrent.ConcurrentHashMap<String, TokenBucket> apiLimits = new java.util.concurrent.ConcurrentHashMap<>();
    /** Voxel section budget per device (HttpHandler.VOXEL_BUDGET, refilled at VOXEL_RATE per second). */
    final java.util.concurrent.ConcurrentHashMap<String, TokenBucket> voxelLimits = new java.util.concurrent.ConcurrentHashMap<>();
    /** Map tiles per device (60 per minute). */
    final java.util.concurrent.ConcurrentHashMap<String, TokenBucket> mapLimits = new java.util.concurrent.ConcurrentHashMap<>();
    /** Script import writes per device (10 per minute). */
    final java.util.concurrent.ConcurrentHashMap<String, TokenBucket> scriptLimits = new java.util.concurrent.ConcurrentHashMap<>();
    /** Share links: wrong share id/secret attempts per IP (5 per minute, then blocked). */
    final FailureLimiter shareLimiter = new FailureLimiter(5, 60_000, System::currentTimeMillis);
    /** Share links: logins per share link (30 per minute). */
    final java.util.concurrent.ConcurrentHashMap<String, TokenBucket> shareLogins = new java.util.concurrent.ConcurrentHashMap<>();
    /** v0.10 public API: the key store (the game's SavedData; tests use their own) and the {@code [api]} config. */
    volatile ai.ezfox.cccloud.publicapi.ApiKeys apiKeys = new ai.ezfox.cccloud.publicapi.ApiKeys(System::currentTimeMillis, () -> { });
    volatile ai.ezfox.cccloud.publicapi.ApiSettings api = ai.ezfox.cccloud.publicapi.ApiSettings.OFF;
    /** Public API requests per key hash and per IP (config limits, per minute); item searches per key (5 per second). */
    final java.util.concurrent.ConcurrentHashMap<String, TokenBucket> apiKeyLimits = new java.util.concurrent.ConcurrentHashMap<>();
    final java.util.concurrent.ConcurrentHashMap<String, TokenBucket> apiIpLimits = new java.util.concurrent.ConcurrentHashMap<>();
    final java.util.concurrent.ConcurrentHashMap<String, TokenBucket> apiSearchLimits = new java.util.concurrent.ConcurrentHashMap<>();
    /** Open event streams per key hash, and in total. */
    final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> apiStreams = new java.util.concurrent.ConcurrentHashMap<>();
    final java.util.concurrent.atomic.AtomicInteger apiStreamTotal = new java.util.concurrent.atomic.AtomicInteger();
    /** PIN hashing is slow on purpose, so it runs on its own thread with a small bounded queue. */
    /** Store install verification (fetches the signed index; L9). */
    final ThreadPoolExecutor storeExecutor = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16),
        new DefaultThreadFactory("cccloud-store", true));
    final ThreadPoolExecutor pinExecutor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32),
        new DefaultThreadFactory("cccloud-pin", true));
    /** Set again after binding when the requested port was 0 (GameTests: a free port). */
    volatile String url;
    volatile int port;
    volatile Set<String> bannedIps = Set.of();
    @Nullable EventLoopGroup group;
    @Nullable Channel channel;
    @Nullable ScheduledFuture<?> flushTask;
    @Nullable ScheduledFuture<?> cleanupTask;

    WebInstance(GameBridge bridge, AuthStore auth, @Nullable SslContext ssl, List<IpUtil.Cidr> trustedProxies, boolean allowInternet, String url, int port) {
        this(bridge, auth, new ai.ezfox.cccloud.access.AccessStore(System::currentTimeMillis, () -> { }), ssl, trustedProxies, allowInternet, url, port);
    }

    WebInstance(GameBridge bridge, AuthStore auth, ai.ezfox.cccloud.access.AccessStore access, @Nullable SslContext ssl, List<IpUtil.Cidr> trustedProxies,
                boolean allowInternet, String url, int port) {
        this.bridge = bridge;
        this.auth = auth;
        this.access = access;
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
        p.addLast("body-limit", new BodyLimit()); // per connection: 4 KB unless an authenticated script upload
        // Script uploads may be 256 KB; every other route rejects bodies over 4 KB in HttpHandler.
        p.addLast("aggregator", new HttpObjectAggregator(ai.ezfox.cccloud.script.ScriptFiles.MAX_FILE + 1024));
        p.addLast("handler", new HttpHandler(this));
    }

    void shutdownExecutors() {
        storeExecutor.shutdownNow();
        pinExecutor.shutdownNow();
    }
}
