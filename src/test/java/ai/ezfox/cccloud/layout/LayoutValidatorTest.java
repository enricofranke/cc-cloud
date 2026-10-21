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
            "{\"v\":2,\"pages\":[],\"cards\":{},\"widgets\":{}}",
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
        var widgets = new StringBuilder();
        for (int i = 0; i < 2000; i++) widgets.append(i == 0 ? "" : ",").append("\"").append("w".repeat(30)).append(i).append("\":{\"label\":\"").append("x".repeat(40)).append("\"}");
        assertEquals("too_large", validate("{\"v\":1,\"pages\":[],\"cards\":{},\"widgets\":{" + widgets + "}}").error());
    }
}
