package ai.ezfox.cccloud.push;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * SSRF protection for push targets (docs/SPEC.md v0.5 D). Pure Java, unit tested.
 * <ul>
 *   <li>https only; plain http only for an ntfy target whose host the server explicitly allows (ntfy on the LAN).</li>
 *   <li>No user info, no fragment; Discord targets must be {@code https://discord.com/api/webhooks/…}.</li>
 *   <li>Config {@code pushAllowedHosts}: when not empty, only those hosts ({@code host} or {@code *.domain}).</li>
 *   <li>At send time every resolved address must be public (no loopback, private, link-local, CGNAT, multicast,
 *       unique-local, documentation or reserved ranges); explicitly allowed hosts are trusted. The connection then goes
 *       to the checked address (no second DNS lookup), and redirects are never followed.</li>
 * </ul>
 */
public final class UrlPolicy {
    public static final Set<String> DISCORD_HOSTS = Set.of("discord.com", "discordapp.com", "canary.discord.com", "ptb.discord.com");
    private static final Pattern HOST = Pattern.compile("[a-z0-9.-]{1,253}|\\[[0-9a-f:.]{2,45}]");

    private UrlPolicy() {
    }

    /** Null if the URL may be stored for this target type, else {@code invalid_url}, {@code https_only} or {@code host_not_allowed}. */
    public static @Nullable String check(PushTarget.Type type, String url, Set<String> allowedHosts) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return "invalid_url";
        }
        if (!uri.isAbsolute() || uri.isOpaque() || uri.getRawUserInfo() != null || uri.getRawFragment() != null) return "invalid_url";
        String host = uri.getHost();
        if (host == null || !HOST.matcher(host.toLowerCase(Locale.ROOT)).matches()) return "invalid_url";
        host = host.toLowerCase(Locale.ROOT);
        if (uri.getPort() == 0 || uri.getPort() > 65535) return "invalid_url";
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        boolean listed = allowed(host, allowedHosts);
        if (scheme.equals("http")) {
            if (type != PushTarget.Type.NTFY || !listed) return "https_only";
        } else if (!scheme.equals("https")) {
            return "https_only";
        }
        if (!allowedHosts.isEmpty() && !listed) return "host_not_allowed";
        if (type == PushTarget.Type.DISCORD) {
            String path = uri.getRawPath();
            if (!DISCORD_HOSTS.contains(host) || path == null || !path.startsWith("/api/webhooks/") || uri.getPort() != -1) return "invalid_url";
        }
        // An IP literal or a name that is obviously local must be explicitly allowed.
        if (!listed && (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal"))) return "host_not_allowed";
        if (!listed && literal(host) instanceof InetAddress address && !isPublic(address)) return "host_not_allowed";
        return null;
    }

    /** Whether the server's {@code pushAllowedHosts} names this host ({@code example.org} or {@code *.example.org}). */
    public static boolean allowed(String host, Set<String> allowedHosts) {
        String h = host.toLowerCase(Locale.ROOT);
        for (String entry : allowedHosts) {
            String e = entry.strip().toLowerCase(Locale.ROOT);
            if (e.isEmpty()) continue;
            if (e.equals(h)) return true;
            if (e.startsWith("*.") && h.endsWith(e.substring(1)) && h.length() > e.length() - 1) return true;
        }
        return false;
    }

    /** True for addresses on the public internet. */
    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) return publicV4(b);
        if (address instanceof Inet6Address) {
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return false; // fc00::/7 unique local
            if (first == 0xfe && (b[1] & 0xc0) == 0xc0) return false; // fec0::/10 site local (deprecated)
            if (first == 0x20 && (b[1] & 0xff) == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) return false; // 2001:db8::/32
            boolean zeroPrefix = true;
            for (int i = 0; i < 10; i++) if (b[i] != 0) zeroPrefix = false;
            if (zeroPrefix && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) return publicV4(new byte[]{b[12], b[13], b[14], b[15]}); // ::ffff:v4
            boolean compat = true;
            for (int i = 0; i < 12; i++) if (b[i] != 0) compat = false;
            if (compat) return false; // ::/96 (IPv4-compatible, ::, ::1)
            if (first == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b) {
                return publicV4(new byte[]{b[12], b[13], b[14], b[15]}); // 64:ff9b::/96 NAT64
            }
            if (first == 0x20 && (b[1] & 0xff) == 0x02) return publicV4(new byte[]{b[2], b[3], b[4], b[5]}); // 2002::/16 6to4
            return (first & 0xe0) == 0x20; // only global unicast 2000::/3
        }
        return false;
    }

    private static boolean publicV4(byte[] b) {
        int a = b[0] & 0xff, c = b[1] & 0xff, d = b[2] & 0xff;
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false; // this net, private, loopback, multicast, reserved, broadcast
        if (a == 100 && (c & 0xc0) == 64) return false; // 100.64/10 CGNAT
        if (a == 169 && c == 254) return false;
        if (a == 172 && (c & 0xf0) == 16) return false;
        if (a == 192 && c == 168) return false;
        if (a == 192 && c == 0 && (d == 0 || d == 2)) return false; // 192.0.0/24, 192.0.2/24
        if (a == 198 && (c == 18 || c == 19)) return false; // benchmarking
        if (a == 198 && c == 51 && d == 100) return false;
        if (a == 203 && c == 0 && d == 113) return false;
        return true;
    }

    /** The address of an IP literal host, or null for a name (no DNS lookup). */
    static @Nullable InetAddress literal(String host) {
        String h = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        boolean v4 = h.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
        boolean v6 = h.contains(":") && h.matches("[0-9a-fA-F:.]+");
        if (!v4 && !v6) {
            // decimal/hex/octal tricks like 2130706433 or 0x7f.1 are not accepted as host names either
            return h.matches("[0-9.x]+") || h.matches("0x[0-9a-f.]+") ? loopback() : null;
        }
        if (v4) {
            for (String part : h.split("\\.")) if (Integer.parseInt(part) > 255) return loopback();
        }
        try {
            return InetAddress.getByName(h); // literal: no lookup
        } catch (java.net.UnknownHostException e) {
            return null;
        }
    }

    /** Stand-in for numeric hosts we refuse to interpret (they count as not public). */
    private static InetAddress loopback() {
        return InetAddress.getLoopbackAddress();
    }
}
