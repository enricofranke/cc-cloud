package ai.ezfox.cccloud.map;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.client.CloudClientConfig;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** JourneyMap waypoint rules: default types, categories, in-world markers and names. */
class MapWaypointsTest {
    private static final String[] TYPES = {"mainframe", "relay", "advanced_relay", "ender_relay", "uplink", "advanced_uplink", "bridge"};

    @Test
    void defaultsShowOnlyMainframesAndEnderRelays() {
        Set<String> defaults = Set.copyOf(CloudClientConfig.JOURNEYMAP_WAYPOINT_TYPES.getDefault());
        assertEquals(Set.of("mainframe", "ender_relay"), defaults);
        for (var t : TYPES) assertEquals(t.equals("mainframe") || t.equals("ender_relay"), MapWaypoints.shown(t, defaults), t);
        // every node type belongs to a category the config accepts
        for (var t : TYPES) assertTrue(CloudClientConfig.WAYPOINT_TYPES.contains(MapWaypoints.category(t)), t);
        assertTrue(MapWaypoints.shown("advanced_relay", Set.of("relay")));
        assertTrue(MapWaypoints.shown("advanced_uplink", Set.of("uplink")));
        assertFalse(MapWaypoints.shown("uplink", Set.of()));
    }

    @Test
    void onlyMainframesInTheWorld() {
        for (var t : TYPES) assertEquals(t.equals("mainframe"), MapWaypoints.inWorld(t), t);
    }

    @Test
    void readableNames() {
        assertEquals("Reactor", MapWaypoints.title("uplink", " Reactor "));
        assertEquals("Computer #7", MapWaypoints.title("advanced_uplink", "Computer #7"));
        assertNull(MapWaypoints.title("uplink", ""));
        assertNull(MapWaypoints.title("relay", null));
        assertNull(MapWaypoints.title("mainframe", "Mainframe @ 0 -60 -6"));
        assertEquals("block.cccloud.cloud_mainframe", MapWaypoints.nameKey("mainframe"));
        assertEquals("block.cccloud.advanced_cloud_relay", MapWaypoints.nameKey("advanced_relay"));
        assertEquals("cccloud.journeymap.bridge", MapWaypoints.nameKey("bridge"));
    }
}
