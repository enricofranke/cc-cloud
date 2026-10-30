package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * docs/SPEC.md "Scaling": value deltas of a flush go out as one {@code values} message, published sections are copy on
 * write (a snapshot taken earlier never changes), and a session never gets a delta before its snapshot.
 */
class WebHubScaleTest {
    private final WebHub hub = WebHub.INSTANCE;
    private final UUID mainframe = UUID.randomUUID();

    private static final class Session implements HubSession {
        final UUID mainframe;
        final AuthStore.Device device = new AuthStore.Device("hash", UUID.randomUUID(), "browser", true, 0, 0, UUID.randomUUID(), "p");
        final List<String> received = new ArrayList<>();
        final boolean inGame;

        Session(UUID mainframe, boolean inGame) {
            this.mainframe = mainframe;
            this.inGame = inGame;
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
            return ai.ezfox.cccloud.access.Role.OWNER;
        }

        @Override
        public boolean inGame() {
            return inGame;
        }

        @Override
        public synchronized void send(List<String> messages) {
            received.addAll(messages);
        }

        @Override
        public void close(int code, String reason) {
        }

        synchronized List<String> received() {
            return new ArrayList<>(received);
        }
    }

    @AfterEach
    void cleanup() {
        hub.removeMainframe(mainframe);
    }

    private static JsonObject section(String id, int widgets) {
        var json = new JsonObject();
        json.addProperty("uplinkId", id);
        json.addProperty("title", "Reactor");
        json.addProperty("online", true);
        var array = new JsonArray();
        for (int i = 0; i < widgets; i++) {
            var widget = new JsonObject();
            widget.addProperty("id", "g" + i);
            widget.addProperty("type", "gauge");
            widget.addProperty("value", 0);
            array.add(widget);
        }
        var chart = new JsonObject();
        chart.addProperty("id", "c");
        chart.addProperty("type", "chart");
        var opts = new JsonObject();
        opts.addProperty("points", 3);
        chart.add("opts", opts);
        chart.add("history", new JsonArray());
        array.add(chart);
        json.add("widgets", array);
        return json;
    }

    @Test
    void valuesOfAFlushAreOneMessage() {
        var session = new Session(mainframe, false);
        String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
        hub.publishUplink(mainframe, section(a, 5));
        hub.publishUplink(mainframe, section(b, 5));
        hub.addSession(session);
        hub.flush();
        session.received.clear();
        for (int tick = 0; tick < 3; tick++) {
            for (int i = 0; i < 5; i++) {
                hub.publishValue(mainframe, a, "g" + i, new JsonPrimitive(tick * 10 + i));
                hub.publishValue(mainframe, b, "g" + i, new JsonPrimitive(tick * 100 + i));
            }
        }
        hub.flush();
        var messages = session.received();
        assertEquals(1, messages.size(), "one message for 30 value changes: " + messages);
        var values = JsonParser.parseString(messages.getFirst()).getAsJsonObject();
        assertEquals("values", values.get("t").getAsString());
        var s = values.getAsJsonObject("s");
        assertEquals(24, s.getAsJsonObject(a).get("g4").getAsInt(), "the latest value of each widget");
        assertEquals(204, s.getAsJsonObject(b).get("g4").getAsInt());
        assertEquals(5, s.getAsJsonObject(a).size());
        assertEquals(24, hub.widgetValue(mainframe, a, "g4").getAsInt());
    }

    @Test
    void chartsAppendAndTrimLikeBefore() {
        String a = UUID.randomUUID().toString();
        hub.publishUplink(mainframe, section(a, 1));
        for (int i = 1; i <= 5; i++) hub.publishValue(mainframe, a, "c", new JsonPrimitive(i));
        var history = hub.sectionSnapshot(mainframe, a).getAsJsonArray("widgets").get(1).getAsJsonObject().getAsJsonArray("history");
        assertEquals("[3,4,5]", history.toString(), "opts.points = 3");
    }

    @Test
    void publishedSectionsAreCopyOnWrite() {
        String a = UUID.randomUUID().toString();
        hub.publishUplink(mainframe, section(a, 2));
        hub.publishValue(mainframe, a, "c", new JsonPrimitive(1));
        var before = hub.sectionSnapshot(mainframe, a).toString();
        var session = new Session(mainframe, false);
        hub.addSession(session);
        String snapshot = session.received().getFirst();
        hub.publishValue(mainframe, a, "g1", new JsonPrimitive(42));
        hub.publishValue(mainframe, a, "c", new JsonPrimitive(2));
        // the snapshot text was built from the old objects; the hub now has new ones
        assertTrue(snapshot.contains(before.substring(1, before.length() - 1)), "snapshot carries the section as it was");
        assertEquals(42, hub.widgetValue(mainframe, a, "g1").getAsInt());
        assertEquals("[1,2]", hub.sectionSnapshot(mainframe, a).getAsJsonArray("widgets").get(2).getAsJsonObject().get("history").toString());
    }

    @Test
    void snapshotComesFirstAndHasTheSections() {
        String a = UUID.randomUUID().toString();
        hub.publishUplink(mainframe, section(a, 3));
        hub.publishValue(mainframe, a, "g0", new JsonPrimitive(7));
        var session = new Session(mainframe, false);
        hub.addSession(session);
        hub.publishValue(mainframe, a, "g0", new JsonPrimitive(8));
        hub.flush();
        var messages = session.received();
        assertTrue(messages.getFirst().startsWith("{\"t\":\"snapshot\""), messages.getFirst());
        var snap = JsonParser.parseString(messages.getFirst()).getAsJsonObject();
        assertEquals(7, snap.getAsJsonArray("uplinks").get(0).getAsJsonObject().getAsJsonArray("widgets").get(0).getAsJsonObject().get("value").getAsInt());
        assertTrue(snap.has("layout"), "layout is always there (null = defaults)");
        assertTrue(messages.getLast().contains("\"g0\":8"), "the later value follows: " + messages);
        // a section message queued before the session joined is built at flush time: never older than the snapshot
        for (var m : messages.subList(1, messages.size())) {
            var json = JsonParser.parseString(m).getAsJsonObject();
            if (json.get("t").getAsString().equals("uplink")) assertEquals(8, json.getAsJsonArray("widgets").get(0).getAsJsonObject().get("value").getAsInt(), m);
        }
    }

    @Test
    void inGameSnapshotsComeAsOneMessagePerSection() throws Exception {
        for (int i = 0; i < 3; i++) hub.publishUplink(mainframe, section(UUID.randomUUID().toString(), 2));
        var session = new Session(mainframe, true);
        hub.addSession(session);
        long until = System.currentTimeMillis() + 5000;
        while (session.received().size() < 4 && System.currentTimeMillis() < until) Thread.sleep(5);
        var messages = session.received();
        assertEquals(4, messages.size(), "snapshot head + 3 sections (serialised off the caller's thread)");
        var head = JsonParser.parseString(messages.getFirst()).getAsJsonObject();
        assertEquals(0, head.getAsJsonArray("uplinks").size());
        for (int i = 1; i < 4; i++) assertEquals("uplink", JsonParser.parseString(messages.get(i)).getAsJsonObject().get("t").getAsString());
    }

    @Test
    void biggestSectionsFitAnInGamePayload() throws Exception {
        // the largest section a script can publish: 64 widgets (with pages), every one a 300-point chart with long texts
        for (int i = 0; i < 3; i++) {
            var json = new JsonObject();
            json.addProperty("uplinkId", UUID.randomUUID().toString());
            json.addProperty("title", "t".repeat(48));
            json.addProperty("online", true);
            var widgets = new JsonArray();
            for (int w = 0; w < 64; w++) {
                var widget = new JsonObject();
                widget.addProperty("id", "w".repeat(30) + w);
                widget.addProperty("type", "chart");
                widget.addProperty("label", "l".repeat(48));
                var opts = new JsonObject();
                opts.addProperty("points", 300);
                opts.addProperty("unit", "u".repeat(16));
                widget.add("opts", opts);
                var history = new JsonArray();
                for (int p = 0; p < 300; p++) history.add(123456.789 + p);
                widget.add("history", history);
                widgets.add(widget);
            }
            json.add("widgets", widgets);
            hub.publishUplink(mainframe, json);
        }
        var session = new Session(mainframe, true);
        hub.addSession(session);
        long until = System.currentTimeMillis() + 5000;
        while (session.received().size() < 4 && System.currentTimeMillis() < until) Thread.sleep(5);
        int largest = session.received().stream().mapToInt(String::length).max().orElse(0);
        assertTrue(largest < ai.ezfox.cccloud.ingame.InGamePayloads.MAX_DATA / 4, "largest in-game message " + largest);
    }

    @Test
    void historyIndexFollowsPublishAndRemove() {
        String a = UUID.randomUUID().toString();
        var json = section(a, 1);
        var opts = new JsonObject();
        opts.addProperty("history", "24h");
        json.getAsJsonArray("widgets").get(0).getAsJsonObject().add("opts", opts);
        hub.publishUplink(mainframe, json);
        hub.publishValue(mainframe, a, "g0", new JsonPrimitive(5));
        assertEquals("24h", hub.historyRange(mainframe, a, "g0"));
        assertTrue(hub.historySamples().stream().anyMatch(s -> s.uplinkId().equals(a) && s.value() == 5));
        hub.publishUplinkRemoved(mainframe, a);
        assertNull(hub.historyRange(mainframe, a, "g0"));
        assertTrue(hub.historySamples().stream().noneMatch(s -> s.uplinkId().equals(a)));
    }
}
