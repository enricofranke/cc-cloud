package ai.ezfox.cccloud.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** The strict rule schema: every trigger/condition/action, limits, unknown fields, loop protection, round trip. */
class RuleCodecTest {
    static final String U = "0a1b2c3d-0000-4000-8000-000000000001";

    static RuleCodec.Result parse(String json) {
        return RuleCodec.parse(JsonParser.parseString(json), 4096);
    }

    @Test
    void validRuleRoundTrips() {
        String json = "{\"name\":\"Cool reactor\",\"cooldown\":10,\"when\":{\"type\":\"widget\",\"uplinkId\":\"" + U + "\",\"widgetId\":\"temp\",\"op\":\">\",\"value\":90},"
            + "\"if\":[{\"type\":\"power\",\"op\":\">=\",\"value\":20},{\"type\":\"online\",\"uplinkId\":\"" + U + "\"}],"
            + "\"then\":[{\"type\":\"redstone\",\"uplinkId\":\"" + U + "\",\"side\":\"top\",\"value\":15},{\"type\":\"alert\",\"text\":\"Too hot\",\"level\":\"warn\"},"
            + "{\"type\":\"craft\",\"uplinkId\":\"" + U + "\",\"item\":\"minecraft:ice\",\"amount\":64},{\"type\":\"dashboard\",\"enabled\":true},"
            + "{\"type\":\"widget\",\"uplinkId\":\"" + U + "\",\"widgetId\":\"pump\",\"value\":true}],\"notify\":true}";
        var result = parse(json);
        assertNull(result.error(), result.error());
        var rule = result.rule();
        assertEquals(5, rule.actions().size());
        assertTrue(rule.push());
        var again = RuleCodec.parse(RuleCodec.toJson(rule.withId("0a1b2c3d")), 4096);
        assertEquals(rule.withId("0a1b2c3d"), again.rule(), "toJson/parse round trip");
    }

    @Test
    void everyTrigger() {
        for (String when : new String[]{
            "{\"type\":\"items\",\"uplinkId\":\"" + U + "\",\"item\":\"minecraft:iron_ingot\",\"op\":\"<\",\"value\":64}",
            "{\"type\":\"power\",\"op\":\"<\",\"value\":10}",
            "{\"type\":\"offline\",\"uplinkId\":\"" + U + "\"}",
            "{\"type\":\"interval\",\"minutes\":5}",
            "{\"type\":\"time\",\"time\":13000}"}) {
            var r = parse("{\"name\":\"x\",\"when\":" + when + ",\"then\":[{\"type\":\"alert\",\"text\":\"t\"}]}");
            assertNull(r.error(), when + " → " + r.error());
        }
    }

    @Test
    void rejectsBadInput() {
        String ok = "{\"type\":\"alert\",\"text\":\"t\"}";
        String[] bad = {
            "{\"name\":\"x\",\"when\":{\"type\":\"interval\",\"minutes\":0},\"then\":[" + ok + "]}",
            "{\"name\":\"x\",\"when\":{\"type\":\"interval\",\"minutes\":5},\"then\":[]}",
            "{\"name\":\"x\",\"when\":{\"type\":\"interval\",\"minutes\":5},\"then\":[" + ok + "],\"cooldown\":1}",
            "{\"name\":\"x\",\"when\":{\"type\":\"interval\",\"minutes\":5},\"then\":[" + ok + "],\"extra\":1}",
            "{\"name\":\"\",\"when\":{\"type\":\"interval\",\"minutes\":5},\"then\":[" + ok + "]}",
            "{\"name\":\"x\",\"when\":{\"type\":\"widget\",\"uplinkId\":\"nope\",\"widgetId\":\"a\",\"op\":\">\",\"value\":1},\"then\":[" + ok + "]}",
            "{\"name\":\"x\",\"when\":{\"type\":\"power\",\"op\":\"~\",\"value\":1},\"then\":[" + ok + "]}",
            "{\"name\":\"x\",\"when\":{\"type\":\"time\",\"time\":24000},\"then\":[" + ok + "]}",
            "{\"name\":\"x\",\"when\":{\"type\":\"interval\",\"minutes\":5},\"then\":[{\"type\":\"redstone\",\"uplinkId\":\"" + U + "\",\"side\":\"up\",\"value\":15}]}",
            "{\"name\":\"x\",\"when\":{\"type\":\"interval\",\"minutes\":5},\"then\":[{\"type\":\"craft\",\"uplinkId\":\"" + U + "\",\"item\":\"minecraft:ice\",\"amount\":5000}]}",
            "{\"name\":\"x\",\"when\":{\"type\":\"interval\",\"minutes\":5},\"then\":[{\"type\":\"alert\",\"text\":\"t\",\"level\":\"panic\"}]}",
            "{\"name\":\"x\",\"when\":{\"type\":\"interval\",\"minutes\":5},\"then\":[{\"type\":\"exec\",\"command\":\"/op me\"}]}",
            "{\"name\":\"x\",\"when\":{\"type\":\"interval\",\"minutes\":5},\"then\":[" + ok + ",".repeat(1) + ok + "," + ok + "," + ok + "," + ok + "," + ok + "," + ok
                + "," + ok + "," + ok + "," + ok + "," + ok + "]}",
        };
        for (String json : bad) assertNotNull(parse(json).error(), json);
        assertNotNull(RuleCodec.parse(JsonParser.parseString("[]"), 4096).error());
    }

    @Test
    void ruleCannotTriggerItself() {
        String self = "{\"name\":\"loop\",\"when\":{\"type\":\"widget\",\"uplinkId\":\"" + U + "\",\"widgetId\":\"pump\",\"op\":\"==\",\"value\":1},"
            + "\"then\":[{\"type\":\"widget\",\"uplinkId\":\"" + U + "\",\"widgetId\":\"pump\",\"value\":false}]}";
        assertNotNull(parse(self).error(), "writes the widget it watches");
        String redstone = "{\"name\":\"loop\",\"when\":{\"type\":\"widget\",\"uplinkId\":\"" + U + "\",\"widgetId\":\"redstone\",\"op\":\"==\",\"value\":1},"
            + "\"then\":[{\"type\":\"redstone\",\"uplinkId\":\"" + U + "\",\"side\":\"top\",\"value\":0}]}";
        assertNotNull(parse(redstone).error());
    }
}
