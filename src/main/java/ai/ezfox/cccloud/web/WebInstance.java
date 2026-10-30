package ai.ezfox.cccloud.web;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/** State of one running dashboard server (also constructed directly by unit tests). */
final class WebInstance {
    /** The rules a connection was accepted under ({@link WebPolicy}). */
    static final AttributeKey<WebPolicy> POLICY = AttributeKey.valueOf("cccloud_policy");

    final GameBridge bridge;
    final AuthStore auth;
    /** Roles and share links (share logins are checked here, off the server thread; the store is thread-safe). */
    final ai.ezfox.cccloud.access.AccessStore access;
    /**
     * The rules new connections are accepted under: TLS, trusted proxies, LAN rule, Cloudflare header, Host list,
     * compression. Replaced as a whole by a config reload ({@link CloudWebServer#reconfigure}).
     */
    volatile WebPolicy policy;
    /** The {@code [web]} config this instance runs with (null for unit-test instances). */
    volatile @Nullable WebSettings settings;
    /** Every open connection (to close those of an old policy after a reload). */
    final ChannelGroup connections = new DefaultChannelGroup("cccloud-web", GlobalEventExecutor.INSTANCE);
    /** Connections per resolved client IP behind a trusted proxy (security review L7). */
    final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> proxiedClients = new java.util.concurrent.ConcurrentHashMap<>();
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
        this(bridge, auth, access, WebPolicy.of(ssl, trustedProxies, allowInternet), url, port);
    }

    WebInstance(GameBridge bridge, AuthStore auth, ai.ezfox.cccloud.access.AccessStore access, WebPolicy policy, String url, int port) {
        this.bridge = bridge;
        this.auth = auth;
        this.access = access;
        this.policy = policy;
        this.url = url;
        this.port = port;
        this.limiter = new ConnectionLimiter(this);
        pinExecutor.allowCoreThreadTimeOut(true);
    }

    WebPolicy policy() {
        return policy;
    }

    /** Sets up the per-connection pipeline with the current rules. */
    void initPipeline(Channel ch) {
        initPipeline(ch, policy);
    }

    /** Sets up the per-connection pipeline; the connection keeps {@code rules} until it closes. */
    void initPipeline(Channel ch, WebPolicy rules) {
        ch.attr(POLICY).set(rules);
        connections.add(ch);
        ChannelPipeline p = ch.pipeline();
        p.addLast("limit", limiter);
        var ssl = rules.ssl();
        if (ssl != null) p.addLast("ssl", ssl.newHandler(ch.alloc()));
        p.addLast("idle", new IdleStateHandler(CloudWebServer.HANDSHAKE_TIMEOUT_S, 0, 0));
        p.addLast("http", new HttpServerCodec(4096, 8192, 8192));
        p.addLast("body-limit", new BodyLimit()); // per connection: 4 KB unless an authenticated script upload
        // Script uploads may be 256 KB; every other route rejects bodies over 4 KB in HttpHandler.
        p.addLast("aggregator", new HttpObjectAggregator(ai.ezfox.cccloud.script.ScriptFiles.MAX_FILE + 1024));
        // permessage-deflate for the dashboard WebSocket (docs/SPEC.md "Scaling"): only answers an upgrade that offers it
        if (rules.compression()) p.addLast("ws-deflate", WebSocketCompression.handler());
        p.addLast("handler", new HttpHandler(this, rules));
    }

    /**
     * Closes the open connections that match (after a reload: those accepted under older rules). Dashboard WebSockets
     * get close code 1012 "service restart", so browsers reconnect at once; HTTP and event-stream connections are closed.
     */
    int closeConnections(java.util.function.Predicate<Channel> which) {
        int closed = 0;
        for (var ch : List.copyOf(connections)) {
            if (!which.test(ch)) continue;
            closed++;
            var session = ch.pipeline().get(WebSocketSession.class);
            if (session != null) session.close(1012, "server restart");
            else ch.close();
        }
        return closed;
    }

    /**
     * The {@code [api]} config changed (reload). Rate limits follow on each bucket's next use ({@code ApiHandler});
     * switching the API or its event stream off ends the open streams at once (not only at their 5-second re-check).
     */
    void applyApi(ai.ezfox.cccloud.publicapi.ApiSettings next) {
        api = next;
        if (next.enabled() && next.events()) return;
        for (var session : WebHub.INSTANCE.sessions()) {
            if (session instanceof ApiEventStream stream && stream.instance == this) stream.close(4404, "disabled");
        }
    }

    void shutdownExecutors() {
        storeExecutor.shutdownNow();
        pinExecutor.shutdownNow();
    }
}
