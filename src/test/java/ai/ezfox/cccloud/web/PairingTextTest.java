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
}
