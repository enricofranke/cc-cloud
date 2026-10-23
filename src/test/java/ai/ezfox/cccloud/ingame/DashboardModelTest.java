package ai.ezfox.cccloud.ingame;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DashboardModelTest {
    @Test
    void appliesHubMessagesLikeTheBrowser() {
        var model = new DashboardModel();
        assertFalse(model.hasSnapshot());
        assertTrue(model.apply("""
            [{"t":"snapshot","canControl":false,"mainframe":{"id":"m","name":"Base","online":true,"enabled":true},
              "uplinks":[{"uplinkId":"u1","title":"Reactor","online":true,"widgets":[
                {"id":"t","type":"gauge","value":10},
                {"id":"c","type":"chart","opts":{"points":3},"history":[1,2,3]}]}],"layout":null}]"""));
        assertTrue(model.hasSnapshot());
        assertFalse(model.canControl());
        model.apply("[{\"t\":\"value\",\"uplinkId\":\"u1\",\"id\":\"t\",\"value\":42},{\"t\":\"value\",\"uplinkId\":\"u1\",\"id\":\"c\",\"value\":4}]");
        var widgets = model.uplinks().get("u1").getAsJsonArray("widgets");
        assertEquals(42, widgets.get(0).getAsJsonObject().get("value").getAsInt());
        assertEquals("[2,3,4]", widgets.get(1).getAsJsonObject().get("history").toString(), "chart history appended and trimmed");
        model.apply("[{\"t\":\"role\",\"role\":\"operator\",\"canControl\":true}]");
        assertTrue(model.canControl(), "role push");
        model.apply("[{\"t\":\"uplink\",\"uplinkId\":\"u2\",\"title\":\"Lights\",\"online\":true,\"widgets\":[]}]");
        assertEquals(2, model.uplinks().size());
        model.apply("[{\"t\":\"uplink_removed\",\"uplinkId\":\"u1\"}]");
        assertEquals(1, model.uplinks().size());
        model.apply("[{\"t\":\"state\",\"online\":false,\"enabled\":true,\"reason\":\"no_power\"}]");
        assertFalse(model.mainframe().get("online").getAsBoolean());
        model.apply("[{\"t\":\"action_result\",\"reqId\":\"r1\",\"ok\":false,\"error\":\"read_only\"}]");
        assertEquals("read_only", model.result("r1").get("error").getAsString());
        var view = JsonParser.parseString(model.toJson()).getAsJsonObject();
        assertTrue(view.get("ready").getAsBoolean());
        assertEquals(1, view.getAsJsonArray("uplinks").size());
        assertFalse(model.apply("not json"), "garbage is ignored");
    }
}
