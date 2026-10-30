package ai.ezfox.cccloud.web;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Where CC: Cloud Connect's tunnel enters the dashboard server: every browser request or WebSocket that arrives through
 * the tunnel becomes an in-memory Netty connection ({@link LocalChannel}) into the <b>same pipeline</b> as a TCP
 * connection ({@link WebInstance#initPipeline}). So pairing, tokens, roles, the PIN, body and frame limits, rate limits
 * and connection limits all apply unchanged.
 * <p>
 * The client IP of such a connection is the one the relay reports (from Cloudflare). It is trusted only here: it is
 * stored on the server side of the in-memory connection when the tunnel opens it ({@link #PEER}), never read from a
 * header, and a LAN or malformed address is replaced by a public placeholder, so tunnel traffic is never treated as
 * local. The Host must be the server's own {@code <name>.cccloud.dev} address.
 */
public final class TunnelBridge {
    /** Set only on the server side of tunnel connections. */
    static final AttributeKey<Peer> PEER = AttributeKey.valueOf("cccloud_tunnel_peer");
    /** A public documentation address (TEST-NET-1) used when the relay reports nothing usable. */
    static final String UNKNOWN_IP = "192.0.2.0";

    /** The browser behind a tunnel connection: its IP (from the relay) and the Host it must use. */
    public record Peer(String ip, String host) {
    }

    private final EventLoopGroup group;
    private final LocalAddress address;
    private final Channel server;
    private final Map<String, Peer> waiting = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();
    private final Supplier<@Nullable WebInstance> instance;

    private TunnelBridge(Supplier<@Nullable WebInstance> instance) {
        this.instance = instance;
        this.group = new MultiThreadIoEventLoopGroup(2, new DefaultThreadFactory("cccloud-connect-local", true), LocalIoHandler.newFactory());
        this.address = new LocalAddress("cccloud-dashboard-" + UUID.randomUUID());
        this.server = new ServerBootstrap()
            .group(group)
            .channel(LocalServerChannel.class)
            .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(256 * 1024, 1024 * 1024))
            .childHandler(new ChannelInitializer<LocalChannel>() {
                @Override
                protected void initChannel(LocalChannel ch) {
                    var peer = ch.remoteAddress() == null ? null : waiting.remove(ch.remoteAddress().id());
                    var web = TunnelBridge.this.instance.get();
                    if (peer == null || web == null) {
                        ch.close();
                        return;
                    }
                    ch.attr(PEER).set(peer);
                    web.initPipeline(ch, tunnelPolicy(web.policy));
                }
            })
            .bind(address).syncUninterruptibly().channel();
    }

    /** The bridge into the running dashboard server (it follows restarts of the server: the instance is looked up per connection). */
    public static TunnelBridge start() {
        return new TunnelBridge(CloudWebServer::currentInstance);
    }

    /** Test hook: a bridge into a test instance. */
    static TunnelBridge forTest(WebInstance instance) {
        return new TunnelBridge(() -> instance);
    }

    /** The event loops of the in-memory connections (the tunnel's local ends run here too). */
    public EventLoopGroup group() {
        return group;
    }

    /**
     * Opens an in-memory connection into the dashboard server for one browser request or WebSocket. {@code handler}
     * becomes the client side's pipeline (it speaks HTTP like a browser would).
     */
    public ChannelFuture open(String clientIp, String host, ChannelHandler handler) {
        String id = "cccloud-tunnel-" + counter.incrementAndGet() + "-" + UUID.randomUUID();
        waiting.put(id, new Peer(sanitize(clientIp), host.toLowerCase(Locale.ROOT)));
        var future = new Bootstrap().group(group).channel(LocalChannel.class).localAddress(new LocalAddress(id)).handler(handler).connect(address);
        future.addListener(f -> {
            if (!f.isSuccess()) waiting.remove(id);
        });
        return future;
    }

    /**
     * The rules of in-memory tunnel connections: those of the dashboard server, but without TLS (the connection never
     * leaves the JVM; TLS ends at the relay), without trusted proxies or the Cloudflare header (the client IP is the
     * relay's, see {@link #PEER}) and without compression (the tunnel's local side never asks for it).
     */
    static WebPolicy tunnelPolicy(WebPolicy server) {
        return new WebPolicy(null, java.util.List.of(), server.allowInternet(), false, server.allowedHosts(), false);
    }

    public void stop() {
        waiting.clear();
        server.close().awaitUninterruptibly(2, TimeUnit.SECONDS);
        group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
    }

    /** A public IP literal as reported, else {@link #UNKNOWN_IP} (tunnel traffic must never count as LAN). */
    static String sanitize(@Nullable String ip) {
        if (ip == null || ip.length() > 64 || IpUtil.literal(ip) == null || IpUtil.isLan(ip)) return UNKNOWN_IP;
        String s = ip.trim();
        return s.startsWith("[") && s.endsWith("]") ? s.substring(1, s.length() - 1) : s;
    }

    /** The tunnel peer of a server-side connection, or null for ordinary TCP connections. */
    static @Nullable Peer peer(Channel channel) {
        return channel.hasAttr(PEER) ? channel.attr(PEER).get() : null;
    }

    public static boolean isTunnel(Channel channel) {
        return peer(channel) != null;
    }

    /** Tunnel connections may only use the server's own Connect address as Host (no other names, no IPs). */
    static boolean hostAllowed(Peer peer, @Nullable String hostHeader) {
        String host = HostCheck.hostOf(hostHeader);
        return host != null && host.equals(peer.host());
    }
}
