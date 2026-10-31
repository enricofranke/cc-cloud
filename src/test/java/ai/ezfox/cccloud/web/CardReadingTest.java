package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A program reads cards with a plain viewer's eyes: no restricted section, private values only in their public form. */
class CardReadingTest {
    private final WebHub hub = WebHub.INSTANCE;
    private final UUID mainframe = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        hub.clear();
    }

    private void publish(String id, String json) {
        hub.publishUplink(mainframe, JsonParser.parseString("{\"uplinkId\":\"" + id + "\",\"title\":\"" + id + "\",\"online\":true" + json + "}").getAsJsonObject());
    }

    @Test
    void restrictedSectionsAreNeverHandedOut() {
        publish("open", ",\"widgets\":[{\"id\":\"temp\",\"type\":\"gauge\",\"opts\":{},\"value\":812}]");
        publish("server", ",\"restricted\":true,\"widgets\":[{\"id\":\"secret\",\"type\":\"label\",\"opts\":{},\"value\":\"x\"}]");
        var sections = hub.viewerSections(mainframe);
        assertEquals(1, sections.size());
        assertEquals("open", sections.getFirst().get("uplinkId").getAsString());
    }

    @Test
    void privateValuesComeInTheirPublicForm() {
        publish("sensor", ",\"widgets\":[{\"id\":\"log\",\"type\":\"sensor_log\",\"opts\":{},\"value\":{\"names\":[\"Steve\"]},\"publicValue\":{\"count\":1}}]");
        var widget = hub.viewerSections(mainframe).getFirst().getAsJsonArray("widgets").get(0).getAsJsonObject();
        assertEquals("{\"count\":1}", widget.get("value").toString());
    }

    @Test
    void claimTeamsStayHidden() {
        publish("blocked", ",\"blocked\":{\"team\":\"Rivals\"},\"widgets\":[]");
        assertFalse(hub.viewerSections(mainframe).getFirst().toString().contains("Rivals"));
    }

    @Test
    void canvasBudgetCountsBytesPerMainframe() {
        publish("c", ",\"widgets\":[]");
        int frames = 0;
        while (hub.tryCanvasBytes(mainframe, ai.ezfox.cccloud.uplink.Limits.CANVAS_FRAME_BYTES) && frames < 100) frames++;
        assertTrue(frames >= 2 && frames < 10, "a burst of two frames, then the rate: " + frames);
    }
}
