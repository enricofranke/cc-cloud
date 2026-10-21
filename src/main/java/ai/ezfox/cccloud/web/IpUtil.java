package ai.ezfox.cccloud.web;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** IP helpers: private-range check, CIDR matching and client IP resolution behind trusted proxies. Plain Java. */
public final class IpUtil {
    /** Loopback, private and link-local/ULA ranges that count as "LAN". */
    private static final List<Cidr> LAN = List.of(
        Cidr.parse("127.0.0.0/8"), Cidr.parse("10.0.0.0/8"), Cidr.parse("172.16.0.0/12"), Cidr.parse("192.168.0.0/16"),
        Cidr.parse("100.64.0.0/10"), Cidr.parse("169.254.0.0/16"),
        Cidr.parse("::1/128"), Cidr.parse("fc00::/7"), Cidr.parse("fe80::/10")
    );

    public record Cidr(byte[] network, int prefix) {
        public static Cidr parse(String text) {
            String s = text.trim();
            int slash = s.indexOf('/');
            byte[] addr = literal(slash < 0 ? s : s.substring(0, slash));
            if (addr == null) throw new IllegalArgumentException("Not an IP address: " + text);
            int prefix = slash < 0 ? addr.length * 8 : Integer.parseInt(s.substring(slash + 1));
            if (prefix < 0 || prefix > addr.length * 8) throw new IllegalArgumentException("Bad prefix: " + text);
            return new Cidr(addr, prefix);
        }

        public boolean contains(byte @Nullable [] address) {
            if (address == null || address.length != network.length) return false;
            int full = prefix / 8, rest = prefix % 8;
            for (int i = 0; i < full; i++) if (address[i] != network[i]) return false;
            if (rest == 0) return true;
            int mask = (0xFF << (8 - rest)) & 0xFF;
            return (address[full] & mask) == (network[full] & mask);
        }
    }

    private IpUtil() {
    }

    /**
     * Parses an IP literal without DNS lookups. Returns null for anything that is not a plain IPv4/IPv6 literal.
     * IPv4-mapped IPv6 addresses are returned as IPv4.
     */
    public static byte @Nullable [] literal(@Nullable String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        int zone = s.indexOf('%');
        if (zone >= 0) s = s.substring(0, zone);
        if (s.isEmpty()) return null;
        boolean v4 = s.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
        boolean v6 = s.contains(":") && s.matches("[0-9A-Fa-f:.]+");
        if (!v4 && !v6) return null;
        if (v4) {
            for (String part : s.split("\\.")) if (Integer.parseInt(part) > 255) return null;
        }
        try {
            byte[] bytes = InetAddress.getByName(s).getAddress(); // literal only, no lookup for these inputs
            if (bytes.length == 16 && isV4Mapped(bytes)) {
                return new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
            }
            return bytes;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isV4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) if (b[i] != 0) return false;
        return b[10] == (byte) 0xFF && b[11] == (byte) 0xFF;
    }

    public static boolean isLan(@Nullable String ip) {
        byte[] address = literal(ip);
        if (address == null) return false;
        for (var cidr : LAN) if (cidr.contains(address)) return true;
        return false;
    }

    public static List<Cidr> parseList(List<? extends String> entries, java.util.function.Consumer<String> onError) {
        var list = new ArrayList<Cidr>();
        for (String entry : entries) {
            try {
                if (!entry.isBlank()) list.add(Cidr.parse(entry));
            } catch (RuntimeException e) {
                onError.accept(entry);
            }
        }
        return list;
    }

    public static boolean matches(List<Cidr> list, @Nullable String ip) {
        byte[] address = literal(ip);
        if (address == null) return false;
        for (var cidr : list) if (cidr.contains(address)) return true;
        return false;
    }

    /**
     * The client IP. Forwarding headers are only honoured when the direct peer is a trusted proxy. For
     * X-Forwarded-For, the right-most address that is not itself a trusted proxy is used.
     */
    public static String clientIp(String peer, List<Cidr> trustedProxies, @Nullable String cfConnectingIp, List<String> xForwardedFor) {
        if (trustedProxies.isEmpty() || !matches(trustedProxies, peer)) return peer;
        if (cfConnectingIp != null && literal(cfConnectingIp) != null) return cfConnectingIp.trim();
        var hops = new ArrayList<String>();
        for (String header : xForwardedFor) for (String part : header.split(",")) hops.add(part.trim());
        for (int i = hops.size() - 1; i >= 0; i--) {
            String hop = hops.get(i);
            if (literal(hop) == null) break;
            if (!matches(trustedProxies, hop)) return hop;
        }
        return peer;
    }
}
