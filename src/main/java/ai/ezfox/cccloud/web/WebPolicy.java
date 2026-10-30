package ai.ezfox.cccloud.web;

import io.netty.handler.ssl.SslContext;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The rules a connection is accepted and served under: TLS, trusted proxies, the LAN-only rule, the Cloudflare header,
 * the Host allow-list and WebSocket compression. One immutable object per configuration; every connection keeps the
 * one it was accepted with ({@link WebInstance#POLICY}) until it closes, so a request never sees half of an old and half
 * of a new configuration. A reload that changes the rules closes the connections of older policies.
 *
 * @param allowedHosts host names allowed besides IP literals, localhost and LAN names (publicUrl host + webAllowedHosts)
 */
record WebPolicy(@Nullable SslContext ssl, List<IpUtil.Cidr> trustedProxies, boolean allowInternet, boolean trustCloudflare, Set<String> allowedHosts,
                 boolean compression) {
    WebPolicy {
        trustedProxies = List.copyOf(trustedProxies);
        allowedHosts = Set.copyOf(allowedHosts);
    }

    /** The rules of unit-test instances (no Host names, no Cloudflare header, compression on). */
    static WebPolicy of(@Nullable SslContext ssl, List<IpUtil.Cidr> trustedProxies, boolean allowInternet) {
        return new WebPolicy(ssl, trustedProxies, allowInternet, false, Set.of(), true);
    }
}
