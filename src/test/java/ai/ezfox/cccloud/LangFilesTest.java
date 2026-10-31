package ai.ezfox.cccloud;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** One broken language file costs the game every name of the mod, without a test or a build noticing otherwise. */
class LangFilesTest {
    private static JsonObject lang(String name) {
        var in = LangFilesTest.class.getResourceAsStream("/assets/cccloud/lang/" + name + ".json");
        assertNotNull(in, name);
        return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void bothFilesParseAndHaveTheSameKeys() {
        assertEquals(lang("en_us").keySet(), lang("de_de").keySet());
    }
}
