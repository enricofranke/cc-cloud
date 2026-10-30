package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Claim guard (docs/SECURITY.md "Claims"): a blocked card names the claiming team only for sessions with
 * {@code view.positions} (the right that also shows player names); viewers, share links, displays, the tablet and API
 * keys without it get the card without the name.
 */
class WebHubClaimTeamTest {
    private final WebHub hub = WebHub.INSTANCE;
    private final UUID mainframe = UUID.randomUUID();
    private final String blocked = UUID.randomUUID().toString();
    private final String redstone = UUID.randomUUID().toString();
    private static final String TEAM = "Secret Rivals";

    private static final class Session implements HubSession {
        final UUID mainframe;
        final AuthStore.Device device;
        final List<String> received = new ArrayList<>();

        Session(UUID mainframe, boolean operator) {
            this.mainframe = mainframe;
            this.device = new AuthStore.Device("hash" + operator, mainframe, "browser", operator, 0, 0, UUID.randomUUID(), "p");
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

        boolean mentions(String text) {
            return received.stream().anyMatch(m -> m.contains(text));
        }
    }

    @AfterEach
    void cleanup() {
        hub.removeMainframe(mainframe);
    }

    private JsonObject blockedSection(String title) {
        var json = new JsonObject();
        json.addProperty("uplinkId", blocked);
        json.addProperty("title", title);
        json.addProperty("online", true);
        json.add("widgets", new JsonArray());
        json.add("blocked", JsonParser.parseString("{\"reason\":\"claim\",\"team\":\"" + TEAM + "\"}"));
        return json;
    }

    private JsonObject redstoneCard() {
        return JsonParser.parseString("""
            {"uplinkId":"%s","title":"Redstone","online":true,"mode":"direct","template":"redstone",
             "widgets":[{"id":"d_aaaaaaaaaaaa","type":"toggle","label":"Gate","opts":{"value":false}}],
             "blockedWidgets":{"d_aaaaaaaaaaaa":{"reason":"claim","team":"%s"}}}""".formatted(redstone, TEAM)).getAsJsonObject();
    }

    @Test
    void onlyViewersWithPositionsSeeTheTeam() {
        var operator = new Session(mainframe, true);
        var viewer = new Session(mainframe, false);
        assertTrue(operator.seesPositions() && !viewer.seesPositions(), "operator presets include view.positions, viewers do not");
        hub.publishMainframe(mainframe, "MF", true, true);
        hub.publishUplink(mainframe, blockedSection("Furnace"));
        hub.publishUplink(mainframe, redstoneCard());
        hub.addSession(operator);
        hub.addSession(viewer);
        assertTrue(operator.mentions(TEAM), "snapshot with positions names the team");
        assertTrue(viewer.mentions(blocked) && viewer.mentions("\"blocked\":{\"reason\":\"claim\"}"), "the viewer gets the blocked card");
        assertTrue(viewer.mentions("\"blockedWidgets\""), "and the blocked widget");
        assertFalse(viewer.mentions(TEAM), "but never the team's name");

        operator.received.clear();
        viewer.received.clear();
        hub.publishUplink(mainframe, blockedSection("Furnace 2"));
        hub.flush();
        assertTrue(operator.mentions("Furnace 2") && operator.mentions(TEAM), "deltas with positions name the team");
        assertTrue(viewer.mentions("Furnace 2"), "the viewer gets the delta");
        assertFalse(viewer.mentions(TEAM), "without the team's name");
    }

    @Test
    void displaysAndTheApiGetThePublicVersion() {
        hub.publishMainframe(mainframe, "MF", true, true);
        hub.publishUplink(mainframe, blockedSection("Furnace"));
        assertFalse(hub.sectionText(mainframe, blocked).contains(TEAM), "Cloud Display text");
        assertFalse(hub.sectionSnapshot(mainframe, blocked, false).toString().contains(TEAM), "tablet, API keys without view.positions");
        for (var s : hub.sectionSnapshots(mainframe, false)) assertFalse(s.toString().contains(TEAM), "API list without view.positions");
        assertTrue(hub.sectionSnapshot(mainframe, blocked, true).toString().contains(TEAM), "the full version exists for positions");
        assertTrue(hub.sectionSnapshot(mainframe, blocked, false).has("blocked"), "the public version is still blocked");
        // the offline flag rewrites the head: still without the name in public
        hub.publishMainframe(mainframe, "MF", false, true);
        assertFalse(hub.sectionText(mainframe, blocked).contains(TEAM));
        assertFalse(hub.sectionSnapshot(mainframe, blocked, false).toString().contains(TEAM));
        assertTrue(hub.sectionSnapshot(mainframe, blocked, true).toString().contains(TEAM));
    }
}
