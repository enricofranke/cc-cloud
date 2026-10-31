package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The "Pair in browser" link keeps the code in the URL fragment. */
class PairingTextTest {
    @Test
    void pairUrl() {
        assertEquals("http://192.168.1.5:8787/pair#code=ABCD-EF23", PairingText.pairUrl("http://192.168.1.5:8787", "ABCD-EF23"));
        assertEquals("https://mc.example.org/pair#code=ABCD-EF23", PairingText.pairUrl("https://mc.example.org/", "ABCD-EF23"));
        assertNull(PairingText.pairUrl(null, "ABCD-EF23"));
        assertNull(PairingText.pairUrl("", "ABCD-EF23"));
        assertNull(PairingText.pairUrl("http://x", "abc"), "only well-formed codes");
    }

    @Test
    void jarUrl() {
        assertEquals("http://192.168.1.5:8787/api/download/cccloud.jar", PairingText.jarUrl("http://192.168.1.5:8787"));
        assertEquals("https://mybase.cccloud.dev/api/download/cccloud.jar", PairingText.jarUrl("https://mybase.cccloud.dev/"));
        assertNull(PairingText.jarUrl(null));
        assertNull(PairingText.jarUrl(" "));
        assertTrue(Router.match(io.netty.handler.codec.http.HttpMethod.GET, "/api/download/cccloud.jar").id() == Router.Id.DOWNLOAD, "the link leads to the route");
    }

    @Test
    void connectWorksFromAnywhere() {
        assertEquals("https://b.cccloud.dev", PairingText.reachableUrl("https://b.cccloud.dev", "http://203.0.113.5:8787", "http://192.168.1.5:8787", false));
        assertEquals("https://b.cccloud.dev", PairingText.reachableUrl("https://b.cccloud.dev", "", "http://192.168.1.5:8787", true), "like every chat link");
    }

    @Test
    void aPlayerOnTheNetworkGetsTheDashboardAddress() {
        assertEquals("http://192.168.1.5:8787", PairingText.reachableUrl(null, "", "http://192.168.1.5:8787", true));
        assertEquals("http://192.168.1.5:8787", PairingText.reachableUrl("", "http://203.0.113.5:8787", "http://192.168.1.5:8787", true),
            "not the public address: many routers don't loop it back");
    }

    @Test
    void aPlayerElsewhereOnlyGetsAnInternetAddress() {
        assertEquals("http://203.0.113.5:8787", PairingText.reachableUrl(null, "http://203.0.113.5:8787", "http://192.168.1.5:8787", false));
        assertEquals("https://dash.example.com", PairingText.reachableUrl(null, "https://dash.example.com", "https://dash.example.com", false), "publicUrl");
        assertNull(PairingText.reachableUrl(null, "", "http://192.168.1.5:8787", false), "a LAN address leads nowhere from outside: the message comes without a link");
        assertNull(PairingText.reachableUrl(null, "", "", true));
    }

    @Test
    void cardUrl() {
        String mf = "0b6f5d38-3c2a-4a8e-9d1e-2f0c4b7a9e11", card = "7d1c2b3a-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
        assertEquals("http://192.168.1.5:8787/#/m/" + mf + "?card=" + card, PairingText.cardUrl("http://192.168.1.5:8787/", mf, card));
        assertNull(PairingText.cardUrl(null, mf, card));
        assertNull(PairingText.cardUrl("http://x", mf, "../evil"), "only UUIDs");
    }
}
