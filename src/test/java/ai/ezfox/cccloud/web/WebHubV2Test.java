package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WebHubV2Test {
    private final WebHub hub = WebHub.INSTANCE;
    private final UUID mainframe = UUID.randomUUID();
    private final String uplink = UUID.randomUUID().toString();

    private static final class FakeSession implements HubSession {
        final UUID mainframe;
        final AuthStore.Device device;
        final List<String> received = new ArrayList<>();

        FakeSession(UUID mainframe, boolean canControl) {
            this.mainframe = mainframe;
            this.device = new AuthStore.Device("hash", mainframe, "phone", canControl, 0, 0, UUID.randomUUID(), "p");
        }

        @Override
        public UUID mainframeId() {
            return mainframe;
        }

        @Override
        public AuthStore.@Nullable Device device() {
            return device;
        }

        @Override
        public ai.ezfox.cccloud.access.Role role() {
            return device.canControl() ? ai.ezfox.cccloud.access.Role.OPERATOR : ai.ezfox.cccloud.access.Role.VIEWER;
        }

        @Override
        public void send(List<String> messages) {
            received.addAll(messages);
        }

        @Override
        public void close(int code, String reason) {
        }

        JsonObject last(String type) {
            for (int i = received.size() - 1; i >= 0; i--) {
                var json = JsonParser.parseString(received.get(i)).getAsJsonObject();
                if (json.get("t").getAsString().equals(type)) return json;
            }
            return null;
        }
    }

    @AfterEach
    void cleanup() {
        hub.removeMainframe(mainframe);
    }

    private void publishChartUplink() {
        var snapshot = JsonParser.parseString("""
            {"uplinkId":"%s","title":"T","online":true,"widgets":[
              {"id":"temp","type":"chart","label":"Temp","opts":{"points":3,"interval":1},"history":[1,2]},
              {"id":"search","type":"item_search","label":"S","opts":{"max":64}}]}
            """.formatted(uplink)).getAsJsonObject();
        hub.publishUplink(mainframe, snapshot);
    }

    @Test
    void snapshotCarriesLayoutAndCanControl() {
        publishChartUplink();
        var session = new FakeSession(mainframe, false);
        hub.addSession(session);
        var snapshot = session.last("snapshot");
        assertTrue(snapshot.has("layout"), "layout key present");
        assertTrue(snapshot.get("layout").isJsonNull(), "null until edited");
        assertFalse(snapshot.get("canControl").getAsBoolean());
        assertFalse(snapshot.getAsJsonObject("mainframe").get("canControl").getAsBoolean());
        hub.removeSession(session);
    }

    @Test
    void layoutIsBroadcastAndPartOfLaterSnapshots() {
        var a = new FakeSession(mainframe, true);
        hub.addSession(a);
        var layout = JsonParser.parseString("{\"v\":1,\"pages\":[],\"cards\":{},\"widgets\":{}}").getAsJsonObject();
        hub.publishLayout(mainframe, layout);
        hub.flush();
        assertEquals(layout, a.last("layout").getAsJsonObject("layout"));
        var b = new FakeSession(mainframe, true);
        hub.addSession(b);
        assertEquals(layout, b.last("snapshot").getAsJsonObject("layout"));
        hub.removeSession(a);
        hub.removeSession(b);
    }

    @Test
    void layoutSavesAreLimitedPerMainframe() {
        int ok = 0;
        for (int i = 0; i < 10; i++) if (hub.tryLayoutSave(mainframe)) ok++;
        assertEquals(5, ok, "5 saves per 10 s");
        assertTrue(hub.tryLayoutSave(UUID.randomUUID()), "other mainframes are independent");
    }

    @Test
    void chartValuesAppendToTheSnapshotHistory() {
        publishChartUplink();
        hub.publishValue(mainframe, uplink, "temp", new JsonPrimitive(3));
        hub.publishValue(mainframe, uplink, "temp", new JsonPrimitive(4));
        var session = new FakeSession(mainframe, true);
        hub.addSession(session);
        var widget = session.last("snapshot").getAsJsonArray("uplinks").get(0).getAsJsonObject().getAsJsonArray("widgets").get(0).getAsJsonObject();
        assertEquals("[2,3,4]", widget.get("history").toString(), "capped at opts.points, oldest first");
        assertEquals(4, widget.get("value").getAsInt());
        hub.removeSession(session);
    }

    @Test
    void widgetTypeLookupForQueries() {
        publishChartUplink();
        assertEquals("item_search", hub.widgetType(mainframe, uplink, "search"));
        assertEquals("chart", hub.widgetType(mainframe, uplink, "temp"));
        assertNull(hub.widgetType(mainframe, uplink, "nope"));
        assertNull(hub.widgetType(UUID.randomUUID(), uplink, "search"));
    }

    @Test
    void clientCount() {
        assertEquals(0, hub.clientCount(mainframe));
        var session = new FakeSession(mainframe, true);
        hub.addSession(session);
        assertEquals(1, hub.clientCount(mainframe));
        hub.removeSession(session);
        assertEquals(0, hub.clientCount(mainframe));
    }
}
