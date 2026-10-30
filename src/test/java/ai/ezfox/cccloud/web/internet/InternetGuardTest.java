package ai.ezfox.cccloud.web.internet;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.web.AuthStore;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** The plain-HTTP rules: who is strict, the PIN unlock, the token expiry and the stricter limits. */
class InternetGuardTest {
    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final UUID mainframe = UUID.randomUUID();

    private InternetGuard guard(String mode) {
        return new InternetGuard(InternetSettings.resolve(new InternetSettings.Inputs(mode, "", 8787, false, "", false, 24)), now::get);
    }

    private AuthStore.Device device(long lastUsed) {
        return new AuthStore.Device("hash", mainframe, "Phone", true, 0, lastUsed, UUID.randomUUID(), "Steve");
    }

    @Test
    void onlyInternetAddressesInHttpModeAreStrict() {
        var http = guard("http");
        assertTrue(http.strict("203.0.113.9"));
        assertTrue(http.strict("2001:db8::5"));
        for (String lan : new String[]{"127.0.0.1", "192.168.1.20", "10.0.0.3", "172.16.4.4", "100.64.1.1", "::1", "fd00::1"}) {
            assertFalse(http.strict(lan), lan);
        }
        assertFalse(guard("off").strict("203.0.113.9"));
        assertFalse(new InternetGuard(InternetSettings.OFF, now::get).strict("203.0.113.9"));
        assertTrue(http.unlocked("hash", mainframe, "192.168.1.20"), "the LAN needs no unlock");
        assertTrue(guard("off").unlocked("hash", mainframe, "203.0.113.9"), "off: nothing to unlock");
    }

    @Test
    void thePinUnlocksForTenMinutesPerBrowserMainframeAndAddress() {
        var http = guard("http");
        String ip = "203.0.113.9";
        assertFalse(http.unlocked("hash", mainframe, ip));
        assertEquals(600, http.unlock("hash", mainframe, ip));
        assertTrue(http.unlocked("hash", mainframe, ip));
        assertEquals(600, http.unlockedFor("hash", mainframe, ip));
        assertFalse(http.unlocked("other", mainframe, ip), "another browser");
        assertFalse(http.unlocked("hash", UUID.randomUUID(), ip), "another mainframe (its own PIN)");
        assertFalse(http.unlocked("hash", mainframe, "198.51.100.1"), "the same token from another address");
        now.addAndGet(InternetGuard.UNLOCK_MS - 1);
        assertTrue(http.unlocked("hash", mainframe, ip));
        now.addAndGet(1);
        assertFalse(http.unlocked("hash", mainframe, ip), "expired after 10 minutes");
        http.cleanup();
        assertEquals(0, http.unlockedFor("hash", mainframe, ip));
        assertEquals(0, guard("off").unlock("hash", mainframe, ip), "nothing to unlock outside plain-HTTP mode");
    }

    @Test
    void tokensUsedFromTheInternetExpireAfterTheIdleTime() {
        var http = guard("http");
        long day = 24 * InternetSettings.HOUR_MS;
        assertFalse(http.tooIdle(device(now.get() - day), "203.0.113.9"), "exactly 24 h is fine");
        assertTrue(http.tooIdle(device(now.get() - day - 1), "203.0.113.9"));
        assertFalse(http.tooIdle(device(now.get() - 20 * day), "192.168.1.20"), "LAN: the normal 30 days");
        assertFalse(guard("off").tooIdle(device(now.get() - 20 * day), "203.0.113.9"), "other modes: the normal 30 days");
        var shorter = new InternetGuard(InternetSettings.resolve(new InternetSettings.Inputs("http", "", 8787, false, "", false, 2)), now::get);
        assertTrue(shorter.tooIdle(device(now.get() - 3 * InternetSettings.HOUR_MS), "203.0.113.9"), "configurable");
    }

    @Test
    void failuresBlockAnInternetAddress() {
        var http = guard("http");
        String ip = "203.0.113.9";
        for (int i = 0; i < InternetGuard.PUBLIC_FAILURES - 1; i++) http.failure(ip);
        assertFalse(http.blocked(ip));
        http.failure(ip);
        assertTrue(http.blocked(ip));
        assertFalse(http.blocked("198.51.100.1"), "per address");
        for (int i = 0; i < 10; i++) http.failure("192.168.1.5");
        assertFalse(http.blocked("192.168.1.5"), "LAN addresses are not counted");
        now.addAndGet(InternetGuard.FAILURE_WINDOW_MS + 1);
        assertFalse(http.blocked(ip), "the window passes");
    }

    @Test
    void aReloadKeepsUnlocksAndFailures() {
        var guard = guard("http");
        String ip = "203.0.113.9";
        guard.unlock("hash", mainframe, ip);
        for (int i = 0; i < InternetGuard.PUBLIC_FAILURES; i++) guard.failure(ip);
        guard.update(InternetSettings.OFF);
        assertFalse(guard.strict(ip), "off now");
        assertTrue(guard.unlocked("hash", mainframe, ip));
        guard.update(InternetSettings.resolve(new InternetSettings.Inputs("http", "", 8787, false, "", false, 24)));
        assertTrue(guard.blocked(ip), "no fresh budget after a reload");
        assertTrue(guard.unlocked("hash", mainframe, ip));
    }

    @Test
    void stricterRates() {
        var http = guard("http");
        assertEquals(5, http.actionsPerSecond("203.0.113.9"));
        assertEquals(10, http.actionsPerSecond("192.168.1.2"));
        assertEquals(60, http.apiPerMinute("203.0.113.9"));
        assertEquals(120, http.apiPerMinute("192.168.1.2"));
        assertEquals(10, guard("off").actionsPerSecond("203.0.113.9"));
    }
}
