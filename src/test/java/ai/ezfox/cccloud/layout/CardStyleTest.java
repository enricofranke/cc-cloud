package ai.ezfox.cccloud.layout;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The server's check of a card's design: the shared fixture (src/test/resources/layout/card_style_fixture.json, generated
 * from cloud/web/src/lib/cardstyle.ts and checked by its Vitest suite too) must come out the same here, plus the cases a
 * JSON file cannot hold well.
 */
class CardStyleTest {
    static JsonObject fixture() throws Exception {
        try (var in = CardStyleTest.class.getResourceAsStream("/layout/card_style_fixture.json")) {
            assertNotNull(in, "fixture missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Set<String> strings(JsonArray array) {
        var out = new HashSet<String>();
        array.forEach(e -> out.add(e.getAsString()));
        return out;
    }

    @Test
    void listsMatchTheDashboard() throws Exception {
        var f = fixture();
        assertEquals(strings(f.getAsJsonArray("backgrounds")), CardStyle.BACKGROUNDS);
        assertEquals(strings(f.getAsJsonArray("properties")), CardStyle.PROPERTIES);
        assertEquals(strings(f.getAsJsonArray("functions")), CardStyle.FUNCTIONS);
        assertEquals(strings(f.getAsJsonArray("tokens")), CardStyle.TOKENS);
        assertEquals(f.get("maxCss").getAsInt(), CardStyle.MAX_CSS);
    }

    @Test
    void fixtureCasesComeOutTheSame() throws Exception {
        var f = fixture();
        int accepted = 0;
        for (var e : f.getAsJsonArray("accept")) {
            String css = e.getAsString();
            assertNull(CardStyle.cssProblem(css), css);
            accepted++;
        }
        int rejected = 0;
        for (var e : f.getAsJsonArray("reject")) {
            var c = e.getAsJsonObject();
            String css = c.get("css").getAsString();
            assertEquals(c.get("reason").getAsString(), CardStyle.cssProblem(css), css);
            rejected++;
        }
        assertTrue(accepted >= 10 && rejected >= 30, "fixture has its cases");
    }

    @Test
    void lengthCap() {
        String decl = "color: red;\n";
        String fits = decl.repeat(CardStyle.MAX_CSS / decl.length());
        assertNull(CardStyle.cssProblem(fits));
        assertEquals("too_long", CardStyle.cssProblem(fits + " ".repeat(CardStyle.MAX_CSS - fits.length() + 1)));
        assertEquals("too_long", CardStyle.cssProblem("color: red;".repeat(10_000)));
    }

    @Test
    void forbiddenTokensInAnyCase() {
        String[][] cases = {
            {"background: UrL(x);", "network"},
            {"background: url\t(x);", "network"},
            {"background-image: IMAGE-SET(a 1x);", "network"},
            {"color: Attr(x);", "script"},
            {"color: EXPRESSION(x);", "script"},
            {"color: red; BEHAVIOUR: x;", "script"},
            {"background: JavaScript :x;", "script"},
            {"color: red; -MOZ-BINDING: x;", "script"},
            {"color: red\\;", "escape"},
            {"color: red; }", "block"},
            {"color: red; @media x", "block"},
            {"color: red*/", "comment"},
            {"color: red ! important", "important"},
        };
        for (var c : cases) assertEquals(c[1], CardStyle.cssProblem(c[0]), c[0]);
    }

    @Test
    void onlyPlainAscii() {
        for (String css : new String[] {
            "color: r\u00e9d;", "color: red;\u200b", "color: red;\u202e", "color: \uff52\uff45\uff44;", "color: red;\u0000",
            "color: 'red';", "color: \"red\";", "color: red;<", "color: red_x;", "color: red;\u00a0",
        }) {
            assertEquals("chars", CardStyle.cssProblem(css), css);
        }
    }

    @Test
    void declarationsOnly() {
        assertNull(CardStyle.cssProblem(""));
        assertNull(CardStyle.cssProblem("  ;\n; "));
        assertNull(CardStyle.cssProblem("Color : RED"), "case does not matter");
        assertEquals("property", CardStyle.cssProblem("div: red;"));
        assertEquals("property", CardStyle.cssProblem("color red;"));
        assertEquals("property", CardStyle.cssProblem("color: red: blue;"));
        assertEquals("property", CardStyle.cssProblem("color:   ;"));
        assertEquals("property", CardStyle.cssProblem("color: red; position: fixed"));
        assertEquals("function", CardStyle.cssProblem("color: calc(1px);"));
        assertEquals("function", CardStyle.cssProblem("color: (red);"), "a bare parenthesis is no function");
        assertEquals("token", CardStyle.cssProblem("color: var(--x);"));
        assertEquals("token", CardStyle.cssProblem("color: var(--text, var(--evil));"), "fallbacks are checked too");
        assertNull(CardStyle.cssProblem("color: var(--text, var(--muted));"));
    }

    @Test
    void hexColours() {
        assertTrue(CardStyle.isHexColor("#1e3a5f"));
        assertTrue(CardStyle.isHexColor("#ABCDEF"));
        for (String bad : new String[] {"1e3a5f", "#1e3a5", "#1e3a5f0", "#gggggg", "red", "#1e3a5f;", " #1e3a5f"}) {
            assertFalse(CardStyle.isHexColor(bad), bad);
        }
    }
}
