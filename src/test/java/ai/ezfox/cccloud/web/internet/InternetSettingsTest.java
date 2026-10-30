package ai.ezfox.cccloud.web.internet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** {@code internetAccess} → the effective web settings (the advanced keys stay overrides). */
class InternetSettingsTest {
    private static InternetSettings resolve(String mode, String domain, boolean allowInternet, String publicUrl, boolean tls) {
        return InternetSettings.resolve(new InternetSettings.Inputs(mode, domain, 8787, allowInternet, publicUrl, tls, 24));
    }

    @Test
    void offIsExactlyTheAdvancedKeys() {
        var lan = resolve("off", "", false, "", false);
        assertEquals(InternetMode.OFF, lan.mode());
        assertFalse(lan.allowInternet(), "default: LAN only");
        assertFalse(lan.strictPublic());
        assertEquals("", lan.publicUrl());
        // an older setup with webAllowInternet = true (e.g. behind Caddy) keeps working unchanged
        var legacy = resolve("off", "", true, "https://dash.example.com", false);
        assertTrue(legacy.allowInternet());
        assertFalse(legacy.strictPublic(), "no plain-HTTP rules for hand-made setups");
        assertEquals("https://dash.example.com", legacy.publicUrl());
    }

    @Test
    void httpOpensTheInternetWithTheStrictRules() {
        var http = resolve("http", "", false, "", false);
        assertEquals(InternetMode.HTTP, http.mode());
        assertTrue(http.allowInternet(), "the mode implies webAllowInternet");
        assertTrue(http.strictPublic());
        assertFalse(http.requirePin(), "by default no PIN is needed to control from the internet");
        assertTrue(InternetSettings.resolve(new InternetSettings.Inputs("http", "", 8787, false, "", false, 24, true)).requirePin(), "internetRequirePin");
        assertFalse(InternetSettings.resolve(new InternetSettings.Inputs("off", "", 8787, true, "", false, 24, true)).requirePin(), "only in http mode");
        assertEquals("", http.publicUrl(), "no domain: the public IP is looked up later");
        assertEquals(24 * InternetSettings.HOUR_MS, http.publicTokenIdleMs());
        assertNull(http.domain());

        var named = resolve("HTTP", "MyBase.DuckDNS.org", false, "", false);
        assertEquals("mybase.duckdns.org", named.domain());
        assertEquals("http://mybase.duckdns.org:8787", named.publicUrl());

        assertEquals("http://[2001:db8::1]:8787", resolve("http", "2001:db8::1", false, "", false).publicUrl());
        assertEquals("https://dash.example.com", resolve("http", "mybase.example.org", false, "https://dash.example.com", false).publicUrl(),
            "publicUrl stays an override");
        var tls = resolve("http", "mybase.example.org", false, "", true);
        assertEquals("https://mybase.example.org:8787", tls.publicUrl(), "own TLS files: https");
        assertTrue(tls.strictPublic(), "the protections stay");
        assertFalse(tls.notes().isEmpty());
    }

    @Test
    void badValuesFallBackSafely() {
        var unknown = resolve("ftp", "", false, "", false);
        assertEquals(InternetMode.OFF, unknown.mode());
        assertFalse(unknown.allowInternet());
        assertFalse(unknown.notes().isEmpty());
        var badDomain = resolve("http", "http://x.org:80/", false, "", false);
        assertNull(badDomain.domain());
        assertEquals("", badDomain.publicUrl());
        assertFalse(badDomain.notes().isEmpty());
        assertEquals(1, InternetSettings.resolve(new InternetSettings.Inputs("http", "", 8787, false, "", false, 0)).publicTokenIdleMs()
            / InternetSettings.HOUR_MS, "at least one hour");
    }

    @Test
    void domains() {
        for (String ok : new String[]{"mybase.duckdns.org", "a.b", "x-1.example.com", "203.0.113.5", "2001:db8::1", "MC.Example.Org", "example.org."}) {
            assertTrue(InternetSettings.validDomain(ok), ok);
        }
        for (String bad : new String[]{"", "localhost", "http://a.org", "a.org:8787", "a.org/x", "-a.org", "a-.org", "a..org", "1.2.3", "a.123",
            "under_score.org", "a b.org", "x".repeat(64) + ".org"}) {
            assertFalse(InternetSettings.validDomain(bad), bad);
        }
        assertEquals("https://a.org", InternetSettings.url("https", "a.org", 443));
        assertEquals("http://a.org", InternetSettings.url("http", "a.org", 80));
        assertEquals("http://a.org:8787", InternetSettings.url("http", "a.org", 8787));
    }

    @Test
    void modes() {
        assertEquals(InternetMode.HTTP, InternetMode.parse(" Http "));
        assertEquals(InternetMode.OFF, InternetMode.parse("off"));
        assertNull(InternetMode.parse("on"));
        assertNull(InternetMode.parse(null));
        assertEquals("http", InternetMode.HTTP.id());
    }
}
