package ai.ezfox.cccloud.web;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * First handler of every connection: rejects banned and (unless allowed) non-LAN peers before any parsing, and limits
 * concurrent connections per IP and in total.
 */
@ChannelHandler.Sharable
final class ConnectionLimiter extends ChannelInboundHandlerAdapter {
    private static final AttributeKey<String> COUNTED = AttributeKey.valueOf("cccloud_counted_ip");
    private final WebInstance instance;
    private final AtomicInteger total = new AtomicInteger();
    private final Map<String, AtomicInteger> perIp = new ConcurrentHashMap<>();

    ConnectionLimiter(WebInstance instance) {
        this.instance = instance;
    }

    static String peerIp(Channel channel) {
        return channel.remoteAddress() instanceof InetSocketAddress address && address.getAddress() != null
            ? address.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String ip = peerIp(ctx.channel());
        boolean proxy = IpUtil.matches(instance.trustedProxies, ip);
        if (!proxy && instance.bannedIps.contains(ip)) {
            ctx.close();
            return;
        }
        // Non-LAN peers are answered by HttpHandler with an explanatory 403 (when webAllowInternet=false).
        ctx.channel().attr(COUNTED).set(ip);
        int all = total.incrementAndGet();
        int mine = perIp.compute(ip, (k, v) -> {
            var counter = v == null ? new AtomicInteger() : v;
            counter.incrementAndGet();
            return counter;
        }).get();
        // Behind a trusted proxy every connection has the proxy's address, so only the total limit applies there.
        if (all > CloudWebServer.MAX_CONNECTIONS || (!proxy && mine > CloudWebServer.maxConnectionsPerIp)) {
            ctx.close();
            return;
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String ip = ctx.channel().attr(COUNTED).getAndSet(null);
        if (ip != null) {
            total.decrementAndGet();
            perIp.computeIfPresent(ip, (k, v) -> v.decrementAndGet() <= 0 ? null : v);
        }
        super.channelInactive(ctx);
    }
}
