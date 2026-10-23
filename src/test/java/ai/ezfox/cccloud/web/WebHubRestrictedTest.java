package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** v0.5: sections published with "restricted":true (Server card, Claims, Backups) only reach canControl devices. */
class WebHubRestrictedTest {
    private final WebHub hub = WebHub.INSTANCE;
    private final UUID mainframe = UUID.randomUUID();
    private final String open = UUID.randomUUID().toString();
    private final String restricted = UUID.randomUUID().toString();

    private static final class Session implements HubSession {
        final UUID mainframe;
        final AuthStore.Device device;
        final List<String> received = new ArrayList<>();

        Session(UUID mainframe, boolean canControl) {
            this.mainframe = mainframe;
            this.device = new AuthStore.Device("hash" + canControl, mainframe, "browser", canControl, 0, 0, UUID.randomUUID(), "p");
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
        public void send(List<String> messages) {
            received.addAll(messages);
        }

        @Override
        public void close(int code, String reason) {
        }

        boolean mentions(String id) {
            return received.stream().anyMatch(m -> m.contains(id));
        }
    }

    @AfterEach
    void cleanup() {
        hub.removeMainframe(mainframe);
    }

    private static JsonObject section(String id, boolean isRestricted) {
        var json = new JsonObject();
        json.addProperty("uplinkId", id);
        json.addProperty("title", "t");
        json.addProperty("online", true);
        var widgets = new JsonArray();
        var widget = new JsonObject();
        widget.addProperty("id", "tps");
        widget.addProperty("type", "gauge");
        widgets.add(widget);
        json.add("widgets", widgets);
        if (isRestricted) json.addProperty("restricted", true);
        return json;
    }

    @Test
    void readOnlyDevicesNeverSeeRestrictedSections() {
        hub.publishMainframe(mainframe, "MF", true, true);
        hub.publishUplink(mainframe, section(open, false));
        hub.publishUplink(mainframe, section(restricted, true));
        var control = new Session(mainframe, true);
        var viewer = new Session(mainframe, false);
        hub.addSession(control);
        hub.addSession(viewer);
        assertTrue(control.mentions(restricted), "snapshot for canControl has the restricted section");
        assertFalse(viewer.mentions(restricted), "snapshot for read-only devices omits it");
        assertTrue(viewer.mentions(open), "normal sections still reach read-only devices");

        control.received.clear();
        viewer.received.clear();
        hub.publishValue(mainframe, restricted, "tps", new JsonPrimitive(19.5));
        hub.publishValue(mainframe, open, "tps", new JsonPrimitive(1));
        hub.publishAlert(mainframe, restricted, "server lagging", "warn");
        hub.publishUplink(mainframe, section(restricted, true));
        hub.flush();
        assertTrue(control.mentions(restricted), "deltas reach canControl devices");
        assertFalse(viewer.mentions(restricted), "no value, alert or snapshot delta of a restricted section for read-only devices");
        assertTrue(viewer.mentions(open), "other deltas still flow");
    }

    @Test
    void sectionCanBecomeUnrestricted() {
        hub.publishMainframe(mainframe, "MF", true, true);
        hub.publishUplink(mainframe, section(restricted, true));
        hub.publishUplink(mainframe, section(restricted, false));
        var viewer = new Session(mainframe, false);
        hub.addSession(viewer);
        assertTrue(viewer.mentions(restricted), "flag follows the latest snapshot");
    }
}
