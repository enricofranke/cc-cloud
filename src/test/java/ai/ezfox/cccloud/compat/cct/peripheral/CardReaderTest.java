package ai.ezfox.cccloud.compat.cct.peripheral;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CardReaderTest {
    private static JsonObject section(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void passesValuesAndLeavesFramesOut() {
        var card = CardReader.card(section("{\"uplinkId\":\"u\",\"title\":\"Reactor\",\"online\":true,\"template\":\"mekanism\",\"bridge\":\"secret\",\"widgets\":["
            + "{\"id\":\"temp\",\"type\":\"gauge\",\"label\":\"Temp\",\"opts\":{\"unit\":\"K\",\"max\":1200,\"pin\":true},\"value\":812},"
            + "{\"id\":\"map\",\"type\":\"canvas\",\"opts\":{\"width\":100},\"value\":{\"canvas\":1,\"c\":[[\"c\",\"red\"]]}}]}"));
        assertEquals("Reactor", card.get("title"));
        assertEquals("mekanism", card.get("kind"));
        assertFalse(card.containsKey("bridge"), "a fixed set of fields");
        var widgets = (Map<?, ?>) card.get("widgets");
        var temp = (Map<?, ?>) widgets.get(1);
        assertEquals(812.0, temp.get("value"));
        assertEquals("K", temp.get("unit"));
        assertFalse(temp.containsKey("pin"));
        assertFalse(((Map<?, ?>) widgets.get(2)).containsKey("value"), "no frames");
    }

    @Test
    void listIsCapped() {
        var many = new java.util.ArrayList<JsonObject>();
        for (int i = 0; i < CardReader.MAX_CARDS + 10; i++) many.add(section("{\"uplinkId\":\"u" + i + "\",\"title\":\"t\",\"widgets\":[]}"));
        var list = CardReader.list(List.copyOf(many));
        assertEquals(CardReader.MAX_CARDS, list.size());
        assertEquals(0.0, ((Map<?, ?>) list.get(1)).get("widgets"));
    }
}
