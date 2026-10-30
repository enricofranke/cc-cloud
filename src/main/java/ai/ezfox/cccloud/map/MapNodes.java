package ai.ezfox.cccloud.map;

import ai.ezfox.cccloud.network.graph.NetworkGraph;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The network nodes a client map mod may show as waypoints (v0.8 wave 3, JourneyMap): mainframes, relays and devices
 * of every network where the player has {@code view.positions}. Pure Java, unit tested. Payload:
 * {@code {"nodes":[{"id","mf","mfName","name","type","dim","x","y","z"}],"truncated"}}; {@code name} is the mainframe's
 * name, a device's card title or empty. The client filters by type ({@link MapWaypoints}).
 */
public final class MapNodes {
    /** Waypoints per player at most. */
    public static final int MAX_NODES = 512;

    /** One network: mainframe id, its name, the routing result and display names by node/device id. */
    public record Network(UUID mainframeId, String name, NetworkGraph.Result result, Map<String, String> names) {
    }

    private MapNodes() {
    }

    /**
     * @param viewPositions whether the player has {@code view.positions} on a mainframe; other networks are left out
     */
    public static JsonObject build(List<Network> networks, Predicate<UUID> viewPositions) {
        var array = new JsonArray();
        boolean truncated = false;
        outer:
        for (var net : networks) {
            if (!viewPositions.test(net.mainframeId())) continue;
            for (var node : net.result().nodes()) {
                if (array.size() >= MAX_NODES) {
                    truncated = true;
                    break outer;
                }
                array.add(entry(net, node.id(), node.type().id, node.dim(), node.x(), node.y(), node.z()));
            }
            for (var device : net.result().devices()) {
                if (array.size() >= MAX_NODES) {
                    truncated = true;
                    break outer;
                }
                array.add(entry(net, device.id(), device.type().id, device.dim(), device.x(), device.y(), device.z()));
            }
        }
        var json = new JsonObject();
        json.add("nodes", array);
        json.addProperty("truncated", truncated);
        return json;
    }

    private static JsonObject entry(Network net, String id, String type, String dim, double x, double y, double z) {
        var o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("mf", net.mainframeId().toString());
        o.addProperty("mfName", net.name());
        // devices: their card title (computer label, Lua title, machine card) or "" - the client names it after the block
        o.addProperty("name", net.names().getOrDefault(id, type.equals("mainframe") ? net.name() : ""));
        o.addProperty("type", type);
        o.addProperty("dim", dim);
        o.addProperty("x", (int) Math.floor(x));
        o.addProperty("y", (int) Math.floor(y));
        o.addProperty("z", (int) Math.floor(z));
        return o;
    }
}
