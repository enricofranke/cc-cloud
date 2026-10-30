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
import java.util.ArrayList;
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

    /** Server start: a fresh dashboard server from the config (stops a running one first). */
    public static synchronized void start(MinecraftServer server) {
        stop();
        open(server);
    }

    /** Starts the dashboard server from the {@code [web]} config; logs and returns without one if it cannot start. */
    private static void open(MinecraftServer server) {
        String version = net.neoforged.fml.ModList.get().getModContainerById(ai.ezfox.cccloud.CCCloud.MODID)
            .map(c -> c.getModInfo().getVersion().toString()).orElse("dev");
        PublicConfig.setSource(() -> new PublicConfig.Settings(version, CloudConfig.get(CloudConfig.SCRIPT_STORE), CloudConfig.get(CloudConfig.SCRIPT_STORE_URL),
            CloudConfig.get(CloudConfig.SCRIPT_STORE_KEY), CloudConfig.get(CloudConfig.TERMINAL_ENABLED), CloudConfig.get(CloudConfig.VOXEL_VIEW), CloudConfig.get(CloudConfig.PUSH_ENABLED),
            CloudConfig.get(CloudConfig.RULES_ENABLED), true, CloudConfig.get(CloudConfig.SCENES_ENABLED), CloudConfig.get(CloudConfig.PRODUCTION_ENABLED),
            CloudConfig.get(CloudConfig.API_ENABLED)));
        var settings = WebSettings.fromConfig();
        if (!settings.enabled()) {
            CloudLog.LOGGER.info("[cccloud] Dashboard web server disabled in config (webEnabled=false)");
            return;
        }
        WebPolicy policy;
        try {
            policy = policy(settings);
        } catch (Exception e) {
            CloudLog.LOGGER.error("[cccloud] Could not load TLS certificate/key ({} / {}): {}. The dashboard will NOT start.", settings.certPath(), settings.keyPath(), e.toString());
            return;
        }

        var auth = WebAuthData.get(server);
        int expired = auth.expireDevices();
        if (expired > 0) CloudLog.LOGGER.info("[cccloud] {} dashboard device(s) expired after 30 days without use", expired);

        var instance = new WebInstance(new MinecraftBridge(server, auth), auth, WebAuthData.access(server), policy,
            computeUrl(server, settings.bind(), settings.port(), policy.ssl() != null, settings.publicUrl()), settings.port());
        instance.settings = settings;
        instance.internet.update(settings.internet());
        instance.publicUrl = settings.publicUrl();
        instance.bannedIps = bannedIps(server);
        instance.apiKeys = ai.ezfox.cccloud.publicapi.ApiKeyData.get(server).keys();
        instance.api = MinecraftBridge.apiSettings();
        var group = new MultiThreadIoEventLoopGroup(2, new DefaultThreadFactory("cccloud-web", true), NioIoHandler.newFactory());
        instance.group = group;
        try {
            instance.channel = bind(instance, group, settings.bind(), settings.port());
        } catch (Exception e) {
            CloudLog.LOGGER.error("[cccloud] Could not start the dashboard web server on {}:{} ({}). Is the port already in use? "
                + "The game keeps running without the dashboard; change webPort in serverconfig/cccloud-server.toml (applies without a restart) "
                + "or free the port and run /cccloud config reload.", settings.bind(), settings.port(), e.toString());
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            instance.shutdownExecutors();
            return;
        }

        if (settings.port() == 0 && instance.channel.localAddress() instanceof InetSocketAddress bound) {
            // dev/GameTest runs: an ephemeral port, so parallel test runs never collide (a failed bind would keep every mainframe offline)
            instance.port = bound.getPort();
            instance.url = computeUrl(server, settings.bind(), bound.getPort(), policy.ssl() != null, settings.publicUrl());
        }
        instance.flushTask = group.next().scheduleAtFixedRate(() -> {
            try {
                WebHub.INSTANCE.flush();
            } catch (Throwable t) {
                CloudLog.LOGGER.error("[cccloud] Error while flushing dashboard updates", t);
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
        instance.cleanupTask = group.next().scheduleAtFixedRate(() -> {
            instance.pairLimiter.cleanup();
            instance.internet.cleanup();
        }, 1, 1, TimeUnit.MINUTES);
        current = instance;
        announce(instance);
    }

    /**
     * The rules for new connections from {@code [web]}: TLS loaded from the files (throws if they cannot be loaded),
     * trusted proxies parsed (invalid entries are logged and ignored), the Host list with the host of publicUrl.
     * Used by the start and by every reload, so both check the same things.
     */
    static WebPolicy policy(WebSettings settings) throws Exception {
        SslContext ssl = null;
        if (settings.tls()) ssl = SslContextBuilder.forServer(new File(settings.certPath()), new File(settings.keyPath())).build();
        var proxies = IpUtil.parseList(settings.trustedProxies(), bad -> CloudLog.LOGGER.error("[cccloud] Ignoring invalid webTrustedProxies entry '{}'", bad));
        var hosts = new HashSet<String>();
        for (var h : settings.allowedHosts()) hosts.add(h.trim().toLowerCase(java.util.Locale.ROOT));
        String publicHost = HostCheck.hostOfUrl(settings.publicUrl());
        if (publicHost != null) hosts.add(publicHost);
        if (settings.internet().domain() != null) hosts.add(settings.internet().domain());
        return new WebPolicy(ssl, proxies, settings.allowInternet(), settings.trustCloudflare(), hosts, settings.compression());
    }

    /** A listening socket for {@code instance} on the given address; new connections get the instance's current rules. */
    static io.netty.channel.Channel bind(WebInstance instance, io.netty.channel.EventLoopGroup group, String bind, int port) {
        return new ServerBootstrap()
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
    }

    /** What a server with these settings says at start and after a reload: the address line and the warnings. */
    static void announce(WebInstance instance) {
        var settings = instance.settings;
        if (settings == null) return;
        boolean tls = instance.policy.ssl() != null;
        CloudLog.LOGGER.info("[cccloud] Dashboard web server listening on {}:{} ({}, {}) – open {}", settings.bind().isEmpty() ? "*" : settings.bind(), instance.port,
            tls ? "HTTPS" : "plain HTTP", settings.allowInternet() ? "internet allowed" : "LAN only", instance.url);
        for (var warning : warnings(settings, tls)) CloudLog.LOGGER.warn(warning);
        if (settings.internet().mode() == ai.ezfox.cccloud.web.internet.InternetMode.HTTP) plainHttp(instance, tls);
    }

    /**
     * {@code internetAccess = http}: a big warning in the log, the mainframes that cannot be controlled from the internet
     * (no PIN), and the internet link (public IP looked up in the background when no domain or publicUrl is set).
     */
    private static void plainHttp(WebInstance instance, boolean tls) {
        boolean requirePin = instance.internet.settings().requirePin();
        CloudLog.LOGGER.warn("[cccloud] internetAccess = http: the dashboard is open to the internet over plain HTTP, so logins and PINs can be read on "
            + "the network path; {} (switch off: /cccloud internet off)", requirePin ? "control from the internet needs the mainframe PIN (internetRequirePin)"
            : "stricter: internetRequirePin = true, safer: HTTPS");
        if (tls) CloudLog.LOGGER.info("[cccloud] internetAccess = http uses your TLS certificate (tlsCertPath), so connections are encrypted");
        var server = ai.ezfox.cccloud.network.CloudNetwork.server();
        if (server != null && requirePin) {
            var missing = ai.ezfox.cccloud.command.InternetCommand.mainframesWithoutPin(server);
            if (!missing.isEmpty()) {
                CloudLog.LOGGER.info("[cccloud] {} mainframe(s) have no PIN and stay view-only from the internet until their owner sets one "
                    + "(look at the mainframe and /cccloud pin set <4-8 digits>): {}", missing.size(), String.join(", ", missing));
            }
        }
        if (instance.publicUrl.isEmpty()) {
            int port = instance.port;
            ai.ezfox.cccloud.web.internet.PublicIp.lookup().thenAccept(ip -> {
                if (ip == null || current != instance || !instance.publicUrl.isEmpty()) return;
                instance.publicUrl = ai.ezfox.cccloud.web.internet.InternetSettings.url(tls ? "https" : "http", ip, port);
                CloudLog.LOGGER.info("[cccloud] Dashboard from the internet: {} (TCP port {} must be forwarded to this machine)", instance.publicUrl, port);
            });
        }
    }

    /** The {@code internetAccess} mode of the running dashboard (OFF when it does not run). */
    public static ai.ezfox.cccloud.web.internet.InternetMode internetMode() {
        var instance = current;
        return instance == null ? ai.ezfox.cccloud.web.internet.InternetMode.OFF : instance.internet.mode();
    }

    /** {@code internetAccess = http} with {@code internetRequirePin} on the running dashboard (GameTests, status). */
    public static boolean requiresPin() {
        var instance = current;
        return instance != null && instance.internet.settings().requirePin();
    }

    /** The internet link of the running dashboard ({@code publicUrl}, the domain or the looked-up public IP), or "". */
    public static String publicUrl() {
        var instance = current;
        return instance == null ? "" : instance.publicUrl;
    }

    /** The warnings of a configuration (the same at start and after a reload). */
    static List<String> warnings(WebSettings settings, boolean tls) {
        var list = new ArrayList<String>();
        for (String note : settings.internet().notes()) list.add("[cccloud] " + note);
        // plain-HTTP mode has its own, bigger warning (announce)
        if (!tls && settings.allowInternet() && settings.internet().mode() != ai.ezfox.cccloud.web.internet.InternetMode.HTTP) {
            list.add("[cccloud] webAllowInternet=true with plain HTTP: tokens and PINs travel unencrypted. Configure TLS "
                + "(tlsCertPath/tlsKeyPath) or put the dashboard behind an HTTPS reverse proxy or tunnel (see docs/HOSTING.md).");
        }
        return list;
    }

    /** Result of applying {@code [web]} to the running server: {@code ok=false} means nothing of it was applied. */
    public record WebChange(boolean ok, boolean changed, String message) {
        static WebChange unchanged() {
            return new WebChange(true, false, "unchanged");
        }

        static WebChange failed(String message) {
            return new WebChange(false, false, message);
        }
    }

    /**
     * Config reload: applies {@code [web]} to the running server. Starts or stops it for {@code webEnabled}; otherwise
     * see {@link #reconfigure(WebInstance, WebSettings, java.util.function.BiFunction)}. Logs like a fresh start (the
     * address line and the same warnings) or a clear error. Server thread.
     */
    public static synchronized WebChange reconfigure(MinecraftServer server) {
        var next = WebSettings.fromConfig();
        var instance = current;
        if (!next.enabled()) {
            if (instance == null) return WebChange.unchanged();
            closeListener();
            CloudLog.LOGGER.info("[cccloud] Dashboard web server stopped (webEnabled=false)");
            return new WebChange(true, true, "web server stopped (webEnabled=false)");
        }
        if (instance == null) {
            open(server);
            var started = current;
            return started != null ? new WebChange(true, true, "web server started on " + started.url)
                : WebChange.failed("the web server could not start (see the server log); the dashboard stays off");
        }
        var change = reconfigure(instance, next, (settings, port) -> computeUrl(server, settings.bind(), port, instance.policy.ssl() != null, settings.publicUrl()));
        if (!change.ok()) CloudLog.LOGGER.error("[cccloud] {}", change.message());
        else if (change.changed()) announce(instance);
        return change;
    }

    /**
     * Applies new {@code [web]} settings to a running instance, all or nothing:
     * <ol>
     * <li>the rules are built like at a start (TLS files loaded; if that fails nothing changes);</li>
     * <li>a new address is bound <b>before</b> the old one is closed; if that fails nothing changes and the old one keeps
     * serving. Only the same port on an overlapping address (e.g. 0.0.0.0 → 127.0.0.1) cannot listen twice: then the
     * old socket is closed first and bound again if the new address fails;</li>
     * <li>the rules for new connections are swapped in one step, the old listener is closed;</li>
     * <li>connections accepted under the old rules are closed (WebSockets with 1012, browsers reconnect at once), unless
     * only compression or the certificate files' contents changed.</li>
     * </ol>
     */
    static synchronized WebChange reconfigure(WebInstance instance, WebSettings next, java.util.function.BiFunction<WebSettings, Integer, String> urlFor) {
        var old = instance.settings;
        if (next.equals(old)) return WebChange.unchanged();
        WebPolicy policy;
        try {
            policy = policy(next);
        } catch (Exception e) {
            return WebChange.failed("Could not load TLS certificate/key (" + next.certPath() + " / " + next.keyPath() + "): " + e
                + ". The dashboard keeps running with its previous [web] settings.");
        }
        var previous = instance.channel;
        var listening = previous;
        boolean moved = old == null || previous == null || !old.sameAddress(next);
        if (moved) {
            try {
                listening = bind(instance, instance.group, next.bind(), next.port());
            } catch (Exception first) {
                boolean overlap = old != null && previous != null && old.port() == next.port() && next.port() != 0;
                if (!overlap) {
                    return WebChange.failed("Could not listen on " + address(next) + " (" + first + "). The dashboard keeps running on "
                        + (old != null ? address(old) : "its old address") + " with its previous [web] settings.");
                }
                // Same port on an overlapping address: the old socket has to go first.
                previous.close().awaitUninterruptibly(2, TimeUnit.SECONDS);
                try {
                    listening = bind(instance, instance.group, next.bind(), next.port());
                } catch (Exception second) {
                    try {
                        instance.channel = bind(instance, instance.group, old.bind(), old.port());
                    } catch (Exception back) {
                        instance.channel = null;
                        return WebChange.failed("Could not listen on " + address(next) + " (" + second + ") nor again on " + address(old) + " (" + back
                            + "). The dashboard is NOT reachable now; fix [web] and run /cccloud config reload.");
                    }
                    return WebChange.failed("Could not listen on " + address(next) + " (" + second + "). The dashboard keeps running on "
                        + address(old) + " with its previous [web] settings.");
                }
                previous = null; // already closed
            }
        }
        boolean reconnect = old == null || !old.sameConnectionRules(next);
        // Swap: new connections from here on get the new rules.
        instance.policy = policy;
        instance.settings = next;
        instance.internet.update(next.internet());
        instance.publicUrl = next.publicUrl();
        instance.channel = listening;
        if (moved) {
            int port = next.port() == 0 && listening != null && listening.localAddress() instanceof InetSocketAddress bound ? bound.getPort() : next.port();
            instance.port = port;
        }
        instance.url = urlFor.apply(next, instance.port);
        if (moved && previous != null) previous.close().awaitUninterruptibly(2, TimeUnit.SECONDS);
        int closed = reconnect ? instance.closeConnections(ch -> ch.attr(WebInstance.POLICY).get() != policy) : 0;
        var message = new StringBuilder();
        if (moved) message.append("now listening on ").append(address(next)).append(", ").append(instance.url);
        else message.append("same address, new rules for new connections");
        if (reconnect) message.append("; ").append(closed).append(" open connection(s) closed to reconnect under the new rules");
        return new WebChange(true, true, message.toString());
    }

    private static String address(WebSettings settings) {
        return (settings.bind().isEmpty() ? "*" : settings.bind()) + ":" + settings.port();
    }

    /** The {@code [api]} config changed (reload): the running server takes it at once. Server thread. */
    public static void applyApi() {
        var instance = current;
        if (instance != null) instance.applyApi(MinecraftBridge.apiSettings());
    }

    /** The {@code [api]} settings of the running server (GameTests), or null. */
    public static ai.ezfox.cccloud.publicapi.@Nullable ApiSettings apiSettings() {
        var instance = current;
        return instance == null ? null : instance.api;
    }

    /** WebSocket compression of the running server's new connections (GameTests). */
    public static boolean compression() {
        var instance = current;
        return instance != null && instance.policy.compression();
    }

    /**
     * {@code webEnabled=false} by a reload: closes the listener and every connection but keeps the dashboard state
     * ({@link WebHub}: sections, layouts, in-game tablets), so switching it on again needs no restart.
     */
    private static void closeListener() {
        var instance = current;
        current = null;
        if (instance == null) return;
        try {
            if (instance.flushTask != null) instance.flushTask.cancel(false);
            if (instance.cleanupTask != null) instance.cleanupTask.cancel(false);
            if (instance.channel != null) instance.channel.close().awaitUninterruptibly(2, TimeUnit.SECONDS);
            instance.closeConnections(ch -> true);
            if (instance.group != null) instance.group.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(5, TimeUnit.SECONDS);
            instance.shutdownExecutors();
        } catch (Exception e) {
            CloudLog.LOGGER.error("[cccloud] Error while stopping the dashboard web server", e);
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
            instance.applyApi(MinecraftBridge.apiSettings()); // also applied at once by a config reload (ConfigReload)
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

    /** Re-checks every session right away (config reload of team providers). Server thread. */
    public static void recheckAll() {
        var instance = current;
        if (instance != null) recheck(instance, null);
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
            session.recheckInternet();
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

    private static String computeUrl(MinecraftServer server, String bind, int port, boolean tls, String publicUrl) {
        String configured = publicUrl.trim();
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
        var settings = instance.settings;
        // the running server's settings (a reload that could not be applied leaves the file ahead of them)
        return LanAddresses.urls(settings != null ? instance.publicUrl : CloudConfig.get(CloudConfig.PUBLIC_URL),
            settings != null ? settings.bind() : CloudConfig.get(CloudConfig.WEB_BIND), instance.port, instance.policy.ssl() != null,
            LanAddresses.select(LanAddresses.scan()));
    }

    public static boolean allowsInternet() {
        var instance = current;
        return instance != null && instance.policy.allowInternet();
    }

    public static boolean usesTls() {
        var instance = current;
        return instance != null && instance.policy.ssl() != null;
    }

    /** Test hook for the load test (all clients come from 127.0.0.1). */
    public static void setMaxConnectionsPerIpForTest(int value) {
        maxConnectionsPerIp = value;
    }

    /** The running instance, for CC: Cloud Connect's in-memory tunnel connections ({@link TunnelBridge}); null when stopped. */
    static @Nullable WebInstance currentInstance() {
        return current;
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
