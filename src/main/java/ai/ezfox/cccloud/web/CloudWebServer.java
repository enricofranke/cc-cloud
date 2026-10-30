package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.CloudConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * The embedded dashboard server: one per Minecraft server, on its own 2-thread Netty event loop.
 * <p>
 * Netty threads never touch the world. Everything in-game goes through the {@link GameBridge} (server thread); state
 * flows the other way through the {@link WebHub}.
 */
public final class CloudWebServer {
    public static final int HANDSHAKE_TIMEOUT_S = 10;
    public static final int IDLE_TIMEOUT_S = 60;
    public static final int AUTH_TIMEOUT_S = 5;
    public static final int PING_INTERVAL_S = 20;
    /**
     * Open connections per IP. A browser alone uses up to 6 HTTP/1.1 connections per host and privacy mode (credentialed
     * and uncredentialed requests have separate pools), plus one WebSocket per tab and speculative preconnects; 8 was
     * reached by a single tab, and the next socket was closed without a response (ERR_EMPTY_RESPONSE in Chrome).
     */
    public static final int MAX_CONNECTIONS_PER_IP = 24;
    public static final int MAX_CONNECTIONS = 256;
    /**
     * Connections over a limit up to this many more are kept just long enough to answer their first request with 503 +
     * Retry-After (a client sees why); beyond that they are closed at once, as before.
     */
    public static final int OVER_LIMIT_GRACE = 8;
    /** Read timeout of such a refused connection. */
    public static final int REFUSED_TIMEOUT_S = 2;

    /** Overridable for the load test only. */
    static volatile int maxConnectionsPerIp = MAX_CONNECTIONS_PER_IP;

    private static volatile @Nullable WebInstance current;
    private static long lastExpiry;

    private CloudWebServer() {
    }

    public static boolean isRunning() {
        var instance = current;
        return instance != null && instance.channel != null && instance.channel.isActive();
    }

    public static int port() {
        var instance = current;
        return instance == null ? -1 : instance.port;
    }

    public static String dashboardUrl() {
        var instance = current;
        return instance == null ? "" : instance.url;
    }

    public static synchronized void start(MinecraftServer server) {
        stop();
        String version = net.neoforged.fml.ModList.get().getModContainerById(ai.ezfox.cccloud.CCCloud.MODID)
            .map(c -> c.getModInfo().getVersion().toString()).orElse("dev");
        PublicConfig.setSource(() -> new PublicConfig.Settings(version, CloudConfig.get(CloudConfig.SCRIPT_STORE), CloudConfig.get(CloudConfig.SCRIPT_STORE_URL),
            CloudConfig.get(CloudConfig.SCRIPT_STORE_KEY), CloudConfig.get(CloudConfig.TERMINAL_ENABLED), CloudConfig.get(CloudConfig.VOXEL_VIEW), CloudConfig.get(CloudConfig.PUSH_ENABLED),
            CloudConfig.get(CloudConfig.RULES_ENABLED), true, CloudConfig.get(CloudConfig.SCENES_ENABLED), CloudConfig.get(CloudConfig.PRODUCTION_ENABLED),
            CloudConfig.get(CloudConfig.API_ENABLED)));
        if (!CloudConfig.get(CloudConfig.WEB_ENABLED)) {
            CloudLog.LOGGER.info("[cccloud] Dashboard web server disabled in config (webEnabled=false)");
            return;
        }
        String bind = CloudConfig.get(CloudConfig.WEB_BIND).trim();
        // Dev runs (build.gradle) use another port so they do not collide with a game running on the same machine.
        int port = Integer.getInteger("cccloud.devWebPort", CloudConfig.get(CloudConfig.WEB_PORT));
        boolean allowInternet = CloudConfig.get(CloudConfig.WEB_ALLOW_INTERNET);
        var proxies = IpUtil.parseList(CloudConfig.get(CloudConfig.WEB_TRUSTED_PROXIES),
            bad -> CloudLog.LOGGER.error("[cccloud] Ignoring invalid webTrustedProxies entry '{}'", bad));

        SslContext ssl = null;
        String cert = CloudConfig.get(CloudConfig.TLS_CERT_PATH).trim();
        String key = CloudConfig.get(CloudConfig.TLS_KEY_PATH).trim();
        if (!cert.isEmpty() || !key.isEmpty()) {
            try {
                ssl = SslContextBuilder.forServer(new File(cert), new File(key)).build();
            } catch (Exception e) {
                CloudLog.LOGGER.error("[cccloud] Could not load TLS certificate/key ({} / {}): {}. The dashboard will NOT start.", cert, key, e.toString());
                return;
            }
        }

        var auth = WebAuthData.get(server);
        int expired = auth.expireDevices();
        if (expired > 0) CloudLog.LOGGER.info("[cccloud] {} dashboard device(s) expired after 30 days without use", expired);

        var instance = new WebInstance(new MinecraftBridge(server, auth), auth, WebAuthData.access(server), ssl, proxies, allowInternet,
            computeUrl(server, bind, port, ssl != null), port);
        instance.bannedIps = bannedIps(server);
        instance.trustCloudflare = CloudConfig.get(CloudConfig.WEB_TRUST_CLOUDFLARE_HEADER);
        instance.compression = CloudConfig.get(CloudConfig.WEB_COMPRESSION);
        var hosts = new java.util.HashSet<String>();
        for (var h : CloudConfig.get(CloudConfig.WEB_ALLOWED_HOSTS)) hosts.add(h.trim().toLowerCase(java.util.Locale.ROOT));
        String publicHost = HostCheck.hostOfUrl(CloudConfig.get(CloudConfig.PUBLIC_URL));
        if (publicHost != null) hosts.add(publicHost);
        instance.allowedHosts = java.util.Set.copyOf(hosts);
        instance.apiKeys = ai.ezfox.cccloud.publicapi.ApiKeyData.get(server).keys();
        instance.api = MinecraftBridge.apiSettings();
        var group = new MultiThreadIoEventLoopGroup(2, new DefaultThreadFactory("cccloud-web", true), NioIoHandler.newFactory());
        instance.group = group;
        try {
            instance.channel = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 64)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(256 * 1024, 1024 * 1024))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        instance.initPipeline(ch);
                    }
                })
                .bind(bind.isEmpty() ? new InetSocketAddress(port) : new InetSocketAddress(bind, port))
                .syncUninterruptibly()
                .channel();
        } catch (Exception e) {
            CloudLog.LOGGER.error("[cccloud] Could not start the dashboard web server on {}:{} ({}). Is the port already in use? "
                + "The game keeps running without the dashboard; change webPort in serverconfig/cccloud-server.toml.", bind, port, e.toString());
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            instance.shutdownExecutors();
            return;
        }

        if (port == 0 && instance.channel.localAddress() instanceof InetSocketAddress bound) {
            // dev/GameTest runs: an ephemeral port, so parallel test runs never collide (a failed bind would keep every mainframe offline)
            instance.port = bound.getPort();
            instance.url = computeUrl(server, bind, bound.getPort(), ssl != null);
        }
        instance.flushTask = group.next().scheduleAtFixedRate(() -> {
            try {
                WebHub.INSTANCE.flush();
            } catch (Throwable t) {
                CloudLog.LOGGER.error("[cccloud] Error while flushing dashboard updates", t);
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
        instance.cleanupTask = group.next().scheduleAtFixedRate(instance.pairLimiter::cleanup, 1, 1, TimeUnit.MINUTES);
        current = instance;
        CloudLog.LOGGER.info("[cccloud] Dashboard web server listening on {}:{} ({}, {}) – open {}", bind.isEmpty() ? "*" : bind, port,
            ssl != null ? "HTTPS" : "plain HTTP", allowInternet ? "internet allowed" : "LAN only", instance.url);
        if (ssl == null && allowInternet) {
            CloudLog.LOGGER.warn("[cccloud] webAllowInternet=true with plain HTTP: tokens and PINs travel unencrypted. Configure TLS "
                + "(tlsCertPath/tlsKeyPath) or put the dashboard behind an HTTPS reverse proxy or tunnel (see docs/HOSTING.md).");
        }
    }

    public static synchronized void stop() {
        var instance = current;
        current = null;
        if (instance == null) return;
        try {
            if (instance.flushTask != null) instance.flushTask.cancel(false);
            if (instance.cleanupTask != null) instance.cleanupTask.cancel(false);
            WebHub.INSTANCE.clear();
            if (instance.channel != null) instance.channel.close().awaitUninterruptibly(2, TimeUnit.SECONDS);
            if (instance.group != null) instance.group.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(5, TimeUnit.SECONDS);
            instance.shutdownExecutors();
            CloudLog.LOGGER.info("[cccloud] Dashboard web server stopped");
        } catch (Exception e) {
            CloudLog.LOGGER.error("[cccloud] Error while stopping the dashboard web server", e);
        }
    }

    /**
     * Periodic housekeeping on the server thread (every 5 s): refreshes the IP ban snapshot, re-checks the role of
     * every connected device (bans, role and team changes, de-op take effect live), closes sessions of revoked/expired
     * share links and expires old devices.
     */
    public static void serverTick(MinecraftServer server) {
        var instance = current;
        if (instance == null) return;
        try {
            instance.bannedIps = bannedIps(server);
            instance.api = MinecraftBridge.apiSettings(); // config reloads apply within 5 s
            recheck(instance, null);
            long now = System.currentTimeMillis();
            if (now - lastExpiry > 3_600_000L) {
                lastExpiry = now;
                int expired = instance.auth.expireDevices();
                if (expired > 0) CloudLog.LOGGER.info("[cccloud] {} dashboard device(s) expired after 30 days without use", expired);
                instance.access.expireShares();
                instance.apiKeys.expire();
            }
        } catch (RuntimeException e) {
            CloudLog.LOGGER.error("[cccloud] Error in dashboard housekeeping", e);
        }
    }

    /** Re-checks the sessions of one mainframe right away (after a role, team or device change). Server thread. */
    public static void recheck(MinecraftServer server, java.util.UUID mainframeId) {
        var instance = current;
        if (instance != null) recheck(instance, mainframeId);
    }

    /** Test hook: the live re-check against a test instance. */
    static void recheckForTest(WebInstance instance, java.util.UUID mainframeId) {
        recheck(instance, mainframeId);
    }

    private static void recheck(WebInstance instance, java.util.@Nullable UUID only) {
        for (var session : WebHub.INSTANCE.sessions()) {
            if (only != null && !only.equals(session.mainframeId())) continue;
            var share = session.share();
            if (share != null) {
                var stored = instance.access.share(share.id());
                if (stored == null) {
                    session.send(List.of("{\"t\":\"error\",\"error\":\"share_revoked\"}"));
                    session.close(4401, "share_revoked");
                } else if (stored.expires() != 0 && stored.expires() <= System.currentTimeMillis()) {
                    session.send(List.of("{\"t\":\"error\",\"error\":\"share_expired\"}"));
                    session.close(4401, "share_expired");
                } else if (!instance.bridge.shareCreatorAllowed(stored)) {
                    session.send(List.of("{\"t\":\"error\",\"error\":\"share_revoked\"}"));
                    session.close(4401, "share_revoked");
                }
                continue;
            }
            var device = session.device();
            if (device == null) continue;
            if (!instance.auth.isValid(device.tokenHash())) {
                session.close(4401, "revoked");
                continue;
            }
            var access = instance.bridge.access(device, session.mainframeId());
            var role = access.role();
            if (role == ai.ezfox.cccloud.access.Role.NONE) {
                CloudLog.LOGGER.info("[cccloud] Disconnecting device '{}' of {}: no role on {} any more", device.name(), device.playerName(), session.mainframeId());
                session.send(List.of("{\"t\":\"error\",\"error\":\"no_access\"}"));
                session.close(4403, "no_access");
            } else if (role != session.role() || !access.perms().equals(session.perms())) {
                WebHub.INSTANCE.pushRole(session, role, access.perms());
            }
        }
    }

    private static java.util.Set<String> bannedIps(MinecraftServer server) {
        var list = server.getPlayerList().getIpBans().getUserList();
        return Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(list)));
    }

    private static String computeUrl(MinecraftServer server, String bind, int port, boolean tls) {
        String configured = CloudConfig.get(CloudConfig.PUBLIC_URL).trim();
        if (!configured.isEmpty()) return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        String host;
        if (!bind.isEmpty() && !bind.equals("0.0.0.0") && !bind.equals("::")) {
            host = bind;
        } else if (server.getLocalIp() != null && !server.getLocalIp().isBlank()) {
            host = server.getLocalIp();
        } else {
            var hosts = LanAddresses.select(LanAddresses.scan());
            host = hosts.isEmpty() ? "localhost" : hosts.getFirst();
        }
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
        return (tls ? "https://" : "http://") + host + ":" + port;
    }

    /** All dashboard URLs for the in-game screen: publicUrl first, then every LAN address of the host. */
    public static List<String> dashboardUrls() {
        var instance = current;
        if (instance == null) return List.of();
        return LanAddresses.urls(CloudConfig.get(CloudConfig.PUBLIC_URL), CloudConfig.get(CloudConfig.WEB_BIND), instance.port, instance.ssl != null,
            LanAddresses.select(LanAddresses.scan()));
    }

    public static boolean allowsInternet() {
        var instance = current;
        return instance != null && instance.allowInternet;
    }

    public static boolean usesTls() {
        var instance = current;
        return instance != null && instance.ssl != null;
    }

    /** Test hook for the load test (all clients come from 127.0.0.1). */
    public static void setMaxConnectionsPerIpForTest(int value) {
        maxConnectionsPerIp = value;
    }

    /** For the load test: issues a device token directly (server thread). */
    public static @Nullable String issueTestToken(MinecraftServer server, java.util.UUID mainframeId, java.util.UUID player) {
        var auth = WebAuthData.get(server);
        var code = auth.currentCode(mainframeId, player);
        var pending = auth.pair(AuthStore.formatCode(code.code()), "loadtest", "127.0.0.1");
        if (pending == null) return null;
        var approved = auth.approve(pending.id(), player, "loadtest", true);
        return approved == null ? null : approved.token();
    }
}
