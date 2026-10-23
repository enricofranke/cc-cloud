package ai.ezfox.cccloud.push;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** SSRF rules for push targets: schemes, hosts, private address ranges, the allowlist. */
class UrlPolicyTest {
    static final PushTarget.Type D = PushTarget.Type.DISCORD, N = PushTarget.Type.NTFY, W = PushTarget.Type.WEBHOOK;

    @Test
    void acceptsPublicHttpsTargets() {
        assertNull(UrlPolicy.check(D, "https://discord.com/api/webhooks/123/abcDEF", Set.of()));
        assertNull(UrlPolicy.check(N, "https://ntfy.sh/my-base-alerts", Set.of()));
        assertNull(UrlPolicy.check(W, "https://hooks.example.org/cc?x=1", Set.of()));
        assertNull(UrlPolicy.check(W, "https://8.8.8.8/hook", Set.of()));
    }

    @Test
    void refusesUnsafeUrls() {
        String[][] bad = {
            {"W", "http://hooks.example.org/x", "https_only"},
            {"W", "ftp://example.org/x", "https_only"},
            {"W", "file:///etc/passwd", "invalid_url"},
            {"W", "https://user:pw@example.org/x", "invalid_url"},
            {"W", "https://example.org/x#frag", "invalid_url"},
            {"W", "https://localhost/x", "host_not_allowed"},
            {"W", "https://router.local/x", "host_not_allowed"},
            {"W", "https://127.0.0.1/x", "host_not_allowed"},
            {"W", "https://10.1.2.3/x", "host_not_allowed"},
            {"W", "https://192.168.0.10/x", "host_not_allowed"},
            {"W", "https://169.254.169.254/latest/meta-data", "host_not_allowed"},
            {"W", "https://100.64.1.1/x", "host_not_allowed"},
            {"W", "https://[::1]/x", "host_not_allowed"},
            {"W", "https://[fd00::1]/x", "host_not_allowed"},
            {"W", "https://[::ffff:10.0.0.1]/x", "host_not_allowed"},
            {"W", "https://2130706433/x", "host_not_allowed"},
            {"W", "https://0x7f.0.0.1/x", "invalid_url"},
            {"W", "https://999.1.1.1/x", "invalid_url"},
            {"D", "https://evil.example/api/webhooks/1/x", "invalid_url"},
            {"D", "https://discord.com/other", "invalid_url"},
            {"D", "https://discord.com:8443/api/webhooks/1/x", "invalid_url"},
            {"N", "http://ntfy.sh/topic", "https_only"},
            {"W", "not a url", "invalid_url"},
        };
        for (var b : bad) {
            var type = b[0].equals("D") ? D : b[0].equals("N") ? N : W;
            assertEquals(b[2], UrlPolicy.check(type, b[1], Set.of()), b[1]);
        }
    }

    @Test
    void allowlistAndLanNtfy() {
        var hosts = Set.of("ntfy.lan", "*.example.org", "192.168.1.5");
        assertNull(UrlPolicy.check(N, "http://ntfy.lan/topic", hosts), "plain http only for allow-listed ntfy hosts");
        assertNull(UrlPolicy.check(N, "http://192.168.1.5:8080/topic", hosts), "an allow-listed LAN address");
        assertEquals("https_only", UrlPolicy.check(W, "http://ntfy.lan/topic", hosts), "not for webhooks");
        assertNull(UrlPolicy.check(W, "https://hooks.example.org/x", hosts));
        assertEquals("host_not_allowed", UrlPolicy.check(W, "https://example.org/x", hosts), "*.example.org does not match the apex");
        assertEquals("host_not_allowed", UrlPolicy.check(W, "https://other.net/x", hosts), "not on the list");
        assertEquals("host_not_allowed", UrlPolicy.check(D, "https://discord.com/api/webhooks/1/x", hosts), "the list applies to Discord too");
    }

    @Test
    void publicAddresses() throws Exception {
        for (String ok : new String[]{"8.8.8.8", "1.1.1.1", "2606:4700:4700::1111", "64:ff9b::808:808"}) {
            assertTrue(UrlPolicy.isPublic(InetAddress.getByName(ok)), ok);
        }
        for (String no : new String[]{"0.0.0.0", "127.0.0.1", "10.0.0.1", "172.16.5.4", "192.168.1.1", "169.254.1.1", "100.100.1.1", "224.0.0.1",
            "255.255.255.255", "192.0.2.1", "198.18.0.1", "::", "::1", "fe80::1", "fc00::1", "fd12::1", "::ffff:127.0.0.1", "64:ff9b::a00:1",
            "2001:db8::1", "2002:0a00:0001::1", "ff02::1"}) {
            assertFalse(UrlPolicy.isPublic(InetAddress.getByName(no)), no);
        }
    }
}
