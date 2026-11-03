package ai.ezfox.cccloud.layout;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class LayoutValidatorTest {
    private static final String UPLINK = "8a1f2c4e-0000-4000-8000-000000000001";
    private static final String VALID = """
        {"v":1,
         "pages":[{"id":"main","title":"Overview","icon":"home"},{"id":"power","title":"Power","icon":"bolt"}],
         "cards":{"%1$s":{"page":"power","order":0,"span":2,"title":"Reactor","hidden":false,"color":"red","icon":"flame"},
                  "%1$s/core":{"color":"purple"},"%1$s~power":{}},
         "widgets":{"%1$s/temp":{"order":0,"span":1,"label":"Core temp","hidden":false}}}
        """.formatted(UPLINK);

    private static LayoutValidator.Result validate(String json) {
        return LayoutValidator.validate(JsonParser.parseString(json));
    }

    @Test
    void viewsNeedASourceAndAType() {
        String head = "{\"v\":2,\"pages\":[],\"cards\":{},\"widgets\":{";
        assertTrue(validate(head + "\"v:w1\":{\"card\":\"c:a\",\"src\":\"u/temp\",\"as\":\"gauge\",\"display\":\"number\"}}}").ok());
        assertFalse(validate(head + "\"v:w1\":{\"card\":\"c:a\",\"as\":\"gauge\"}}}").ok(), "no source");
        assertFalse(validate(head + "\"v:w1\":{\"card\":\"c:a\",\"src\":\"u/temp\",\"as\":\"rocket\"}}}").ok(), "unknown type");
        assertFalse(validate(head + "\"v:w1\":{\"src\":\"u/temp\",\"as\":\"gauge\"}}}").ok(), "no card");
        assertFalse(validate(head + "\"u/temp\":{\"src\":\"u/other\"}}}").ok(), "only views have a source");
    }

    @Test
    void acceptsTheSpecExample() {
        var result = validate(VALID);
        assertTrue(result.ok(), String.valueOf(result.detail()));
        assertNotNull(result.layout());
        assertTrue(validate("{\"v\":1,\"pages\":[],\"cards\":{},\"widgets\":{}}").ok(), "empty layout");
        assertTrue(validate("{\"v\":1,\"pages\":[],\"cards\":{\"gone-uplink\":{\"page\":\"unknown\"}},\"widgets\":{}}").ok(),
            "unknown ids and pages are kept");
    }

    @Test
    void rejectsSchemaViolations() {
        String[] bad = {
            "null", "[]", "{}",
            "{\"v\":3,\"pages\":[],\"cards\":{},\"widgets\":{}}",
            "{\"v\":1,\"cards\":{},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{},\"widgets\":{},\"extra\":1}",
            "{\"v\":1,\"pages\":[{\"id\":\"a b\",\"title\":\"x\"}],\"cards\":{},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[{\"id\":\"a\",\"title\":\"\"}],\"cards\":{},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[{\"id\":\"a\",\"title\":\"x\"},{\"id\":\"a\",\"title\":\"y\"}],\"cards\":{},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[{\"id\":\"a\",\"title\":\"x\",\"icon\":\"skull\"}],\"cards\":{},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[{\"id\":\"a\",\"title\":\"x\",\"order\":1}],\"cards\":{},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{\"k\":{\"span\":5}},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{\"k\":{\"order\":-1}},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{\"k\":{\"order\":10001}},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{\"k\":{\"order\":1.5}},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{\"k\":{\"color\":\"pink\"}},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{\"k\":{\"hidden\":\"yes\"}},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{\"k\":{\"title\":\"" + "x".repeat(49) + "\"}},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{\"k\":{\"page\":\"" + "x".repeat(33) + "\"}},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{\"k\":{\"label\":\"x\"}},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{\"" + "k".repeat(161) + "\":{}},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{\"k\":1},\"widgets\":{}}",
            "{\"v\":1,\"pages\":[],\"cards\":{},\"widgets\":{\"w\":{\"title\":\"x\"}}}",
            "{\"v\":1,\"pages\":[],\"cards\":{},\"widgets\":{\"w\":{\"label\":\"" + "x".repeat(49) + "\"}}}",
            "{\"v\":1,\"pages\":[],\"cards\":[],\"widgets\":{}}",
        };
        for (var json : bad) {
            var result = validate(json);
            assertEquals("invalid_layout", result.error(), json);
        }
    }

    @Test
    void enforcesCountLimits() {
        var pages = new StringBuilder();
        for (int i = 0; i <= LayoutValidator.MAX_PAGES; i++) pages.append(i == 0 ? "" : ",").append("{\"id\":\"p").append(i).append("\",\"title\":\"P\"}");
        assertEquals("invalid_layout", validate("{\"v\":1,\"pages\":[" + pages + "],\"cards\":{},\"widgets\":{}}").error());
        var cards = new StringBuilder();
        for (int i = 0; i <= LayoutValidator.MAX_CARDS; i++) cards.append(i == 0 ? "" : ",").append("\"c").append(i).append("\":{}");
        assertEquals("invalid_layout", validate("{\"v\":1,\"pages\":[],\"cards\":{" + cards + "},\"widgets\":{}}").error());
    }

    @Test
    void rejectsOversizedDocuments() {
        // just over MAX_BYTES (256 KB since the limits were raised for big bases) with fewer than MAX_WIDGETS entries
        var widgets = new StringBuilder();
        for (int i = 0; i < 2600; i++) widgets.append(i == 0 ? "" : ",").append("\"").append("w".repeat(50)).append(i).append("\":{\"label\":\"").append("x".repeat(48)).append("\"}");
        assertTrue(widgets.length() > LayoutValidator.MAX_BYTES);
        assertEquals("too_large", validate("{\"v\":1,\"pages\":[],\"cards\":{},\"widgets\":{" + widgets + "}}").error());
    }

    private static final String V2 = """
        {"v":2,
         "pages":[{"id":"main","title":"Overview","icon":"home"}],
         "cards":{"%1$s":{"page":"main","x":0,"y":0,"w":3,"h":10,"title":"Reactor","hidden":false,"color":"red","icon":"flame","style":"compact"},
                  "c:card1":{"page":"main","x":3,"y":0,"w":6,"h":4,"title":"My overview"}},
         "widgets":{"%1$s/temp":{"card":"c:card1","x":0,"y":0,"w":6,"h":5,"label":"Core temp","hidden":false,"color":"red","icon":"flame","display":"bar"}}}
        """.formatted(UPLINK);

    @Test
    void acceptsV2() {
        var result = validate(V2);
        assertTrue(result.ok(), String.valueOf(result.detail()));
        assertTrue(validate("{\"v\":2,\"pages\":[],\"cards\":{},\"widgets\":{}}").ok());
        assertTrue(validate("{\"v\":2,\"pages\":[],\"cards\":{\"c:x\":{\"x\":9,\"w\":3}},\"widgets\":{\"a/b\":{\"card\":\"c:gone\"}}}").ok(),
            "x + w = 12, a widget pointing at a missing card");
    }

    @Test
    void rejectsV2Violations() {
        String[][] bad = {
            {"cards", "{\"%s\":{\"order\":1}}"},
            {"cards", "{\"%s\":{\"span\":2}}"},
            {"cards", "{\"%s\":{\"x\":10,\"w\":3}}"},
            {"cards", "{\"%s\":{\"w\":2}}"},
            {"cards", "{\"%s\":{\"x\":12}}"},
            {"cards", "{\"%s\":{\"y\":100000}}"},
            {"cards", "{\"%s\":{\"h\":61}}"},
            {"cards", "{\"%s\":{\"h\":0}}"},
            {"cards", "{\"%s\":{\"x\":1.5}}"},
            {"cards", "{\"%s\":{\"style\":\"huge\"}}"},
            {"cards", "{\"c:bad key\":{}}"},
            {"cards", "{\"c:\":{}}"},
            {"cards", "{\"c:0123456789012345678901234\":{}}"},
            {"widgets", "{\"%s/a\":{\"order\":1}}"},
            {"widgets", "{\"%s/a\":{\"x\":6,\"w\":7}}"},
            {"widgets", "{\"%s/a\":{\"h\":41}}"},
            {"widgets", "{\"%s/a\":{\"w\":13}}"},
            {"widgets", "{\"%s/a\":{\"display\":\"pie\"}}"},
            {"widgets", "{\"%s/a\":{\"display\":\"auto\"}}"},
            {"widgets", "{\"%s/a\":{\"color\":\"pink\"}}"},
            {"widgets", "{\"%s/a\":{\"icon\":\"nope\"}}"},
            {"widgets", "{\"%s/a\":{\"card\":\"\"}}"},
            {"widgets", "{\"%s/a\":{\"style\":\"compact\"}}"},
        };
        for (var b : bad) {
            String part = b[1].replace("%s", UPLINK);
            String json = b[0].equals("cards")
                ? "{\"v\":2,\"pages\":[],\"cards\":" + part + ",\"widgets\":{}}"
                : "{\"v\":2,\"pages\":[],\"cards\":{},\"widgets\":" + part + "}";
            var result = validate(json);
            assertEquals("invalid_layout", result.error(), json);
        }
        // v1 does not know the v2 fields
        assertFalse(validate("{\"v\":1,\"pages\":[],\"cards\":{\"a\":{\"x\":0}},\"widgets\":{}}").ok());
        assertFalse(validate("{\"v\":1,\"pages\":[],\"cards\":{},\"widgets\":{\"a/b\":{\"card\":\"c:x\"}}}").ok());
    }

    private static String v2Card(String patch) {
        return "{\"v\":2,\"pages\":[],\"cards\":{\"" + UPLINK + "\":" + patch + "},\"widgets\":{}}";
    }

    @Test
    void keepsTheCardDesign() {
        String css = "background: linear-gradient(135deg, #1e3a5f, var(--panel));\\nborder-radius: 12px;\\nletter-spacing: 0.05em;";
        var result = validate(v2Card("{\"x\":0,\"w\":4,\"bg\":\"custom\",\"bgColor\":\"#1E3A5F\",\"css\":\"" + css + "\"}"));
        assertTrue(result.ok(), String.valueOf(result.detail()));
        var card = result.layout().getAsJsonObject("cards").getAsJsonObject(UPLINK);
        assertEquals("custom", card.get("bg").getAsString(), "kept, not stripped");
        assertEquals("#1E3A5F", card.get("bgColor").getAsString());
        assertEquals(css.replace("\\n", "\n"), card.get("css").getAsString());
        for (String bg : CardStyle.BACKGROUNDS) assertTrue(validate(v2Card("{\"bg\":\"" + bg + "\"}")).ok(), bg);
        assertTrue(validate(v2Card("{\"bgColor\":\"#000000\"}")).ok(), "a colour without the custom option is harmless");
        assertTrue(validate(v2Card("{\"css\":\"\"}")).ok());
        // widget icons are part of v2 too
        assertTrue(validate("{\"v\":2,\"pages\":[],\"cards\":{},\"widgets\":{\"" + UPLINK + "/status\":{\"icon\":\"flame\"}}}").ok());
    }

    @Test
    void refusesABadCardDesign() {
        String[] bad = {
            "{\"bg\":\"neon\"}",
            "{\"bg\":\"\"}",
            "{\"bg\":\"TINT-GOLD\"}",
            "{\"bg\":1}",
            "{\"bg\":[\"custom\"]}",
            "{\"bgColor\":\"red\"}",
            "{\"bgColor\":\"#fff\"}",
            "{\"bgColor\":\"#1e3a5f; color: red\"}",
            "{\"bgColor\":\"url(x)\"}",
            "{\"bgColor\":123456}",
            "{\"css\":42}",
            "{\"css\":{\"color\":\"red\"}}",
            "{\"css\":[\"color: red\"]}",
            "{\"css\":null}",
            "{\"css\":\"background: url(https://x.test/a.png)\"}",
            "{\"css\":\"background: \\\\75 rl(https://x.test/a.png)\"}",
            "{\"css\":\"color: red; } body { display: none\"}",
            "{\"css\":\"@import x\"}",
            "{\"css\":\"color: red !important\"}",
            "{\"css\":\"color: red /* x */\"}",
            "{\"css\":\"position: fixed; inset: 0\"}",
            "{\"css\":\"background: var(--x, url(x))\"}",
            "{\"css\":\"background: var(--evil)\"}",
            "{\"css\":\"color: \\u0072\\u0065\\u0064\\u200b\"}",
            "{\"css\":\"" + "color: red;".repeat(200) + "\"}",
            "{\"styles\":\"color: red\"}",
        };
        for (var patch : bad) {
            var result = validate(v2Card(patch));
            assertEquals("invalid_layout", result.error(), patch);
        }
        assertEquals("cards[" + UPLINK + "].css", validate(v2Card("{\"css\":\"color: expression(x)\"}")).detail());
        assertEquals("cards[" + UPLINK + "].bg", validate(v2Card("{\"bg\":\"neon\"}")).detail());
        // v1 documents and widgets have no design
        assertFalse(validate("{\"v\":1,\"pages\":[],\"cards\":{\"a\":{\"bg\":\"tint-red\"}},\"widgets\":{}}").ok());
        assertFalse(validate("{\"v\":1,\"pages\":[],\"cards\":{\"a\":{\"css\":\"color: red\"}},\"widgets\":{}}").ok());
        assertFalse(validate("{\"v\":2,\"pages\":[],\"cards\":{},\"widgets\":{\"a/b\":{\"css\":\"color: red\"}}}").ok());
        assertFalse(validate("{\"v\":2,\"pages\":[],\"cards\":{},\"widgets\":{\"a/b\":{\"bg\":\"tint-red\"}}}").ok());
    }

    @Test
    void keepsAndChecksTheCardParts() {
        String parts = "{\"title\":\"color: var(--accent); font-size: 15px\",\"gauge_fill\":\"color: #5ef0c4; filter: drop-shadow(0 0 3px #5ef0c4)\"}";
        var result = validate(v2Card("{\"x\":0,\"w\":4,\"parts\":" + parts + "}"));
        assertTrue(result.ok(), String.valueOf(result.detail()));
        var card = result.layout().getAsJsonObject("cards").getAsJsonObject(UPLINK);
        assertEquals("color: var(--accent); font-size: 15px", card.getAsJsonObject("parts").get("title").getAsString());
        assertTrue(validate(v2Card("{\"parts\":{}}")).ok());
        for (String bad : new String[] {
            "{\"parts\":\"color: red\"}",
            "{\"parts\":[]}",
            "{\"parts\":null}",
            "{\"parts\":{\"title\":42}}",
            "{\"parts\":{\"card\":\"color: red\"}}",
            "{\"parts\":{\"title\":\"background: red\"}}",
            "{\"parts\":{\"button\":\"background-image: url(https://x.test/a.png)\"}}",
            "{\"parts\":{\"value\":\"color: red; } .pin { display: none\"}}",
            "{\"parts\":{\"screen\":\"--cp-title-color: red\"}}",
            "{\"parts\":{\"title\":\"" + "color: red;".repeat(50) + "\"}}",
        }) {
            var r = validate(v2Card(bad));
            assertEquals("invalid_layout", r.error(), bad);
            assertEquals("cards[" + UPLINK + "].parts", r.detail(), bad);
        }
        assertFalse(validate("{\"v\":1,\"pages\":[],\"cards\":{\"a\":{\"parts\":{}}},\"widgets\":{}}").ok(), "v1 has no parts");
    }

    @Test
    void aDesignedV2LayoutIsNotMigrated() {
        var layout = JsonParser.parseString(v2Card("{\"bg\":\"pat-grid\",\"css\":\"color: var(--text)\"}")).getAsJsonObject();
        assertTrue(LayoutValidator.validate(layout).ok());
        assertNull(LayoutData.migrate(layout, java.util.List.of(new com.google.gson.JsonObject())), "already v2");
    }

    @Test
    void widgetMinimumsByType() {
        var layout = JsonParser.parseString("{\"v\":2,\"pages\":[],\"cards\":{},\"widgets\":{\"u/chart\":{\"w\":3},\"u/g\":{\"w\":2},"
            + "\"u/hidden\":{\"w\":1,\"hidden\":true},\"gone/x\":{\"w\":1}}}").getAsJsonObject();
        java.util.Map<String, String> types = java.util.Map.of("u/chart", "chart", "u/g", "gauge", "u/hidden", "item_search");
        assertEquals("widgets[u/chart].w", LayoutValidator.widgetMinimums(layout, types::get));
        layout.getAsJsonObject("widgets").getAsJsonObject("u/chart").addProperty("w", 4);
        assertNull(LayoutValidator.widgetMinimums(layout, types::get), "hidden and unknown widgets are not checked");
        assertEquals(6, LayoutValidator.minWidgetW("item_search"));
        assertEquals(3, LayoutValidator.minWidgetW("toggle"));
    }
}
