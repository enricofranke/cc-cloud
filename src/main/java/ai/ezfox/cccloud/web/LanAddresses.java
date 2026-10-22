package ai.ezfox.cccloud.web;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The host's addresses for the dashboard URL list (in-game screen, chat). IPv4 only (IPv6 hosts produce long,
 * often temporary addresses); skips down and loopback interfaces and link-local 169.254/16. Private ranges (including
 * Tailscale's 100.64/10) come first, other (public) addresses after them.
 */
public final class LanAddresses {
    /** One address of one interface; separated from {@link NetworkInterface} so the selection is unit testable. */
    public record Candidate(String nic, boolean up, boolean loopback, InetAddress address) {
    }

    private LanAddresses() {
    }

    public static List<Candidate> scan() {
        var list = new ArrayList<Candidate>();
        try {
            for (var nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                boolean up, loopback;
                try {
                    up = nic.isUp();
                    loopback = nic.isLoopback();
                } catch (Exception e) {
                    continue;
                }
                for (var address : Collections.list(nic.getInetAddresses())) list.add(new Candidate(nic.getName(), up, loopback, address));
            }
        } catch (Exception ignored) {
            // No interfaces readable: no LAN URLs.
        }
        return list;
    }

    /** Selects and orders host addresses (dotted IPv4 strings). */
    public static List<String> select(List<Candidate> candidates) {
        var lan = new LinkedHashSet<String>();
        var other = new LinkedHashSet<String>();
        for (var c : candidates) {
            if (!c.up() || c.loopback()) continue;
            if (!(c.address() instanceof Inet4Address v4)) continue;
            if (v4.isLoopbackAddress() || v4.isLinkLocalAddress() || v4.isAnyLocalAddress() || v4.isMulticastAddress()) continue;
            String host = v4.getHostAddress();
            if (IpUtil.isLan(host)) lan.add(host);
            else other.add(host);
        }
        var result = new ArrayList<String>(lan);
        result.addAll(other);
        return result;
    }

    /**
     * Dashboard URLs: {@code publicUrl} first (if set), then one URL per host address. If the server binds to a
     * specific address, only that one is reachable and listed.
     */
    public static List<String> urls(@Nullable String publicUrl, String bind, int port, boolean tls, List<String> hosts) {
        var urls = new LinkedHashSet<String>();
        if (publicUrl != null && !publicUrl.isBlank()) {
            String p = publicUrl.trim();
            urls.add(p.endsWith("/") ? p.substring(0, p.length() - 1) : p);
        }
        String scheme = tls ? "https://" : "http://";
        String b = bind == null ? "" : bind.trim();
        if (!b.isEmpty() && !b.equals("0.0.0.0") && !b.equals("::")) {
            urls.add(scheme + (b.contains(":") && !b.startsWith("[") ? "[" + b + "]" : b) + ":" + port);
        } else {
            for (var host : hosts) urls.add(scheme + host + ":" + port);
        }
        return List.copyOf(urls);
    }
}
