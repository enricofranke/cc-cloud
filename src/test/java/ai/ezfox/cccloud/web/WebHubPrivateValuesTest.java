package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.ezfox.cccloud.access.Role;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Private values (Advanced Cloud Sensor: player names): operators get the value, viewers, displays and the public API
 * form get the public value, in snapshots, value deltas, section messages and alerts.
 */
class WebHubPrivateValuesTest {
    private final WebHub hub = WebHub.INSTANCE;
    private final UUID mainframe = UUID.randomUUID();
    private final String sensor = UUID.randomUUID().toString();

    private static final class Session implements HubSession {
        final UUID mainframe;
        final Role role;
        final List<String> received = new ArrayList<>();

        Session(UUID mainframe, Role role) {
            this.mainframe = mainframe;
            this.role = role;
        }

        @Override
        public UUID mainframeId() {
            return mainframe;
        }

        @Override
        public AuthStore.@Nullable Device device() {
            return new AuthStore.Device("h" + role.id, mainframe, "b", role != Role.VIEWER, 0, 0, UUID.randomUUID(), "p");
        }

        @Override
        public Role role() {
            return role;
        }

        @Override
        public void send(List<String> messages) {
            received.addAll(messages);
        }

        @Override
        public void close(int code, String reason) {
        }

        boolean got(String s) {
            return received.stream().anyMatch(m -> m.contains(s));
        }
    }

    @AfterEach
    void cleanup() {
        hub.removeMainframe(mainframe);
    }

    private JsonObject card(String who, String pub) {
        var json = new JsonObject();
        json.addProperty("uplinkId", sensor);
        json.addProperty("title", "Sensor");
        json.addProperty("online", true);
        var widgets = new JsonArray();
        var w = new JsonObject();
        w.addProperty("id", "activity");
        w.addProperty("type", "sensor_log");
        w.addProperty("value", who);
        w.addProperty("publicValue", pub);
        widgets.add(w);
        var plain = new JsonObject();
        plain.addProperty("id", "occupied");
        plain.addProperty("type", "indicator");
        plain.addProperty("value", true);
        widgets.add(plain);
        json.add("widgets", widgets);
        return json;
    }

    @Test
    void operatorsGetNamesEveryoneElseThePublicForm() {
        hub.publishMainframe(mainframe, "MF", true, true);
        hub.publishUplink(mainframe, card("Steve inside", "A player inside"));
        var operator = new Session(mainframe, Role.OPERATOR);
        var viewer = new Session(mainframe, Role.VIEWER);
        hub.addSession(operator);
        hub.addSession(viewer);
        assertTrue(operator.got("Steve inside"), "operator snapshot");
        assertTrue(viewer.got("A player inside") && !viewer.got("Steve"), "viewer snapshot");
        assertFalse(viewer.got("publicValue") || operator.got("publicValue"), "the public value is never sent as such");

        operator.received.clear();
        viewer.received.clear();
        hub.publishValue(mainframe, sensor, "activity", new JsonPrimitive("Alex inside"), new JsonPrimitive("A player inside"));
        hub.publishAlert(mainframe, sensor, "Alex entered Sensor", "warn", "A player entered Sensor");
        hub.flush();
        assertTrue(operator.got("Alex inside") && operator.got("Alex entered Sensor"), "operator deltas");
        assertTrue(viewer.got("A player inside") && viewer.got("A player entered Sensor"), "viewer deltas");
        assertFalse(viewer.got("Alex"), "no name in any viewer message");

        operator.received.clear();
        viewer.received.clear();
        hub.publishUplink(mainframe, card("Bob inside", "A player inside"));
        hub.flush();
        assertTrue(operator.got("Bob inside"), "section message");
        assertFalse(viewer.got("Bob"), "the viewer's section message is the public form");

        var display = hub.sectionText(mainframe, sensor);
        assertNotNull(display);
        assertFalse(display.contains("Bob"), "displays get the public form");
        assertTrue(hub.sectionSnapshot(mainframe, sensor, true).toString().contains("Bob"));
        assertFalse(hub.sectionSnapshot(mainframe, sensor, false).toString().contains("Bob"));
        assertFalse(hub.sectionSnapshots(mainframe, false).toString().contains("Bob"), "API keys without view.positions");
        assertTrue(hub.widgetValue(mainframe, sensor, "occupied").getAsBoolean(), "ordinary values are unchanged");
    }

    @Test
    void aValueWithoutPublicFormIsNotPrivateAnyMore() {
        hub.publishMainframe(mainframe, "MF", true, true);
        hub.publishUplink(mainframe, card("Steve inside", "A player inside"));
        hub.publishValue(mainframe, sensor, "activity", new JsonPrimitive("Nobody inside"));
        var viewer = new Session(mainframe, Role.VIEWER);
        hub.addSession(viewer);
        assertTrue(viewer.got("Nobody inside"));
    }
}
