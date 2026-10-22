package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class LanAddressesTest {
    private static LanAddresses.Candidate c(String nic, boolean up, boolean loopback, String ip) throws Exception {
        return new LanAddresses.Candidate(nic, up, loopback, InetAddress.getByName(ip));
    }

    @Test
    void selectsUsableIpv4AddressesPrivateFirst() throws Exception {
        var hosts = LanAddresses.select(List.of(
            c("lo0", true, true, "127.0.0.1"),
            c("en0", true, false, "203.0.113.7"),      // public: listed, after the private ones
            c("en0", true, false, "192.168.1.171"),
            c("en0", true, false, "fe80::1"),          // IPv6: skipped
            c("en5", true, false, "169.254.12.3"),     // link-local: skipped
            c("utun4", true, false, "100.101.102.103"),// Tailscale (100.64/10): private, listed
            c("en1", false, false, "10.0.0.5"),        // interface down: skipped
            c("bridge0", true, false, "172.16.4.1"),
            c("en0", true, false, "192.168.1.171")     // duplicate
        ));
        assertEquals(List.of("192.168.1.171", "100.101.102.103", "172.16.4.1", "203.0.113.7"), hosts);
    }

    @Test
    void buildsUrls() {
        var hosts = List.of("192.168.1.171", "100.101.102.103");
        assertEquals(List.of("http://192.168.1.171:8787", "http://100.101.102.103:8787"), LanAddresses.urls("", "0.0.0.0", 8787, false, hosts));
        assertEquals(List.of("https://dash.example.com", "https://192.168.1.171:8443", "https://100.101.102.103:8443"),
            LanAddresses.urls("https://dash.example.com/", "0.0.0.0", 8443, true, hosts));
        assertEquals(List.of("http://127.0.0.1:8787"), LanAddresses.urls(null, "127.0.0.1", 8787, false, hosts), "specific bind address only");
        assertEquals(List.of("http://[fd00::5]:8787"), LanAddresses.urls(null, "fd00::5", 8787, false, hosts));
        assertTrue(LanAddresses.urls("", "", 8787, false, List.of()).isEmpty());
    }

    @Test
    void realScanNeverListsLoopbackOrLinkLocal() {
        for (var host : LanAddresses.select(LanAddresses.scan())) {
            assertFalse(host.startsWith("127.") || host.startsWith("169.254."), host);
        }
    }
}
