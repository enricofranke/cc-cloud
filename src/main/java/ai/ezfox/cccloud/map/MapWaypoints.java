package ai.ezfox.cccloud.map;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which {@link MapNodes} become client map waypoints and how they are named and shown (JourneyMap plugin, client config
 * {@code journeyMapWaypointTypes}). Pure Java, unit tested; the client turns the translation keys into text.
 */
public final class MapWaypoints {
    /** The waypoint group every CC: Cloud waypoint goes into (one switch for all of them in the map mod). */
    public static final String GROUP = "CC: Cloud";

    private MapWaypoints() {
    }

    /** Config category of a node type: {@code relay} covers both radio relay tiers, {@code uplink} both uplink tiers and the Cloud Probe. */
    public static String category(String type) {
        return switch (type) {
            case "relay", "advanced_relay" -> "relay";
            case "uplink", "advanced_uplink", "probe" -> "uplink";
            default -> type;
        };
    }

    public static boolean shown(String type, Set<String> types) {
        return types.contains(category(type));
    }

    /** Only mainframes get an in-world marker and beam; everything else stays on the map. */
    public static boolean inWorld(String type) {
        return type.equals("mainframe");
    }

    /**
     * The waypoint's own name: the device's card title (computer label, Lua title, machine card) when it has one,
     * otherwise null and the client uses {@link #nameKey}. Mainframes always use the block name: the server's
     * "Mainframe @ x y z" only repeats the coordinates the map shows anyway.
     */
    public static @Nullable String title(String type, @Nullable String name) {
        if (type.equals("mainframe") || name == null || name.isBlank()) return null;
        return name.strip();
    }

    /** Translation key of the fallback name (the block's name). */
    public static String nameKey(String type) {
        return switch (type) {
            case "mainframe" -> "block.cccloud.cloud_mainframe";
            case "relay" -> "block.cccloud.cloud_relay";
            case "advanced_relay" -> "block.cccloud.advanced_cloud_relay";
            case "ender_relay" -> "block.cccloud.ender_relay";
            case "advanced_uplink" -> "block.cccloud.advanced_cloud_uplink";
            case "probe" -> "block.cccloud.cloud_probe";
            case "bridge" -> "cccloud.journeymap.bridge";
            default -> "block.cccloud.cloud_uplink";
        };
    }
}
