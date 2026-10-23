package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class IpUtilTest {
    @Test
    void lanRanges() {
        for (String ip : List.of("127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.178.20", "100.64.1.1", "::1", "fd00::1", "fe80::1", "::ffff:192.168.1.2")) {
            assertTrue(IpUtil.isLan(ip), ip);
        }
        for (String ip : List.of("8.8.8.8", "172.32.0.1", "100.128.0.1", "2001:db8::1", "1.1.1.1", "unknown", "", "example.com")) {
            assertFalse(IpUtil.isLan(ip), ip);
        }
    }

    @Test
    void forwardedHeadersAreIgnoredWithoutTrustedProxy() {
        assertEquals("8.8.8.8", IpUtil.clientIp("8.8.8.8", List.of(), true, "192.168.1.1", List.of("192.168.1.1")));
        var proxies = List.of(IpUtil.Cidr.parse("127.0.0.1"));
        assertEquals("8.8.8.8", IpUtil.clientIp("8.8.8.8", proxies, true, null, List.of("192.168.1.1")), "peer is not a trusted proxy");
    }

    @Test
    void forwardedHeadersFromTrustedProxy() {
        var proxies = List.of(IpUtil.Cidr.parse("127.0.0.1"), IpUtil.Cidr.parse("10.0.0.0/8"));
        assertEquals("203.0.113.9", IpUtil.clientIp("127.0.0.1", proxies, true, "203.0.113.9", List.of()), "cloudflared opt-in");
        // M2: without the opt-in a client-supplied CF-Connecting-IP passed through by Caddy is ignored
        assertEquals("198.51.100.7", IpUtil.clientIp("127.0.0.1", proxies, false, "1.2.3.4", List.of("198.51.100.7")));
        assertEquals("127.0.0.1", IpUtil.clientIp("127.0.0.1", proxies, false, "1.2.3.4", List.of()), "header alone does nothing");
        // Right-most untrusted hop wins; a spoofed left-most entry is ignored.
        assertEquals("203.0.113.9", IpUtil.clientIp("127.0.0.1", proxies, false, null, List.of("192.168.1.1, 203.0.113.9, 10.0.0.5")));
        assertEquals("127.0.0.1", IpUtil.clientIp("127.0.0.1", proxies, true, "garbage", List.of("garbage")));
    }

    @Test
    void cidrMatching() {
        var net = IpUtil.Cidr.parse("192.168.0.0/16");
        assertTrue(net.contains(IpUtil.literal("192.168.255.1")));
        assertFalse(net.contains(IpUtil.literal("192.169.0.1")));
        assertFalse(net.contains(IpUtil.literal("::1")));
        assertThrows(IllegalArgumentException.class, () -> IpUtil.Cidr.parse("example.com"));
    }
}
