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
    void cardUrl() {
        String mf = "0b6f5d38-3c2a-4a8e-9d1e-2f0c4b7a9e11", card = "7d1c2b3a-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
        assertEquals("http://192.168.1.5:8787/#/m/" + mf + "?card=" + card, PairingText.cardUrl("http://192.168.1.5:8787/", mf, card));
        assertNull(PairingText.cardUrl(null, mf, card));
        assertNull(PairingText.cardUrl("http://x", mf, "../evil"), "only UUIDs");
    }
}
