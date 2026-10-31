package ai.ezfox.cccloud.join;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The fallback texts that old clients see: from the mod's language files, by the language the client reports. */
class JoinTextTest {
    private static final String[] KEYS = {"cccloud.join.mismatch", "cccloud.join.older", "cccloud.join.update", "cccloud.join.download", "cccloud.join.no_link",
        "cccloud.join.version", "cccloud.join.copy"};

    @Test
    void everyTextExistsInEnglishAndGermanWithTheSameArguments() {
        for (var key : KEYS) {
            String en = JoinText.fallback("en_us", key);
            String de = JoinText.fallback("de_de", key);
            assertNotNull(en, key);
            assertNotNull(de, key);
            assertNotEquals(en, de, key);
            assertEquals(en.split("%s", -1).length, de.split("%s", -1).length, key);
        }
    }

    @Test
    void thePlayersLanguageDecides() {
        assertEquals(JoinText.fallback("de_de", "cccloud.join.older"), JoinText.fallback("de_at", "cccloud.join.older"), "other German variants read de_de");
        assertEquals(JoinText.fallback("en_us", "cccloud.join.older"), JoinText.fallback("fr_fr", "cccloud.join.older"), "no such language: English");
        assertEquals(JoinText.fallback("en_us", "cccloud.join.older"), JoinText.fallback("../../secret", "cccloud.join.older"), "a client's language is not a path");
        assertEquals(JoinText.fallback("de_de", "cccloud.join.older"), JoinText.fallback("DE_DE", "cccloud.join.older"));
    }

    @Test
    void onlyTheJoinTextsAreKept() throws IOException {
        var texts = JoinText.read(new ByteArrayInputStream("{\"cccloud.join.a\":\"x\",\"item.cccloud.b\":\"y\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(java.util.Map.of("cccloud.join.a", "x"), texts);
    }
}
