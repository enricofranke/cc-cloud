package ai.ezfox.cccloud.network;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.gui.CloudMenu;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * docs/SPEC.md "Scaling": the mainframe screen's network stays far below the view limit for the biggest network it can
 * show (1000 devices with 48-character names, every link reported), devices with problems come first.
 */
class ScreenNetworkTest {
    private static JsonObject node(String id, String type, String name, String status, String parent, boolean device) {
        var n = new JsonObject();
        n.addProperty("id", id);
        n.addProperty("type", type);
        n.addProperty("name", name);
        n.addProperty("status", status);
        if (parent != null) n.addProperty("parent", parent);
        var pos = new JsonObject();
        pos.addProperty("x", 12_345_678);
        pos.addProperty("y", 64);
        pos.addProperty("z", -12_345_678);
        n.add("pos", pos);
        n.addProperty("dim", "minecraft:overworld");
        n.addProperty("range", 48);
        if (device) n.addProperty("uplinkId", id);
        return n;
    }

    /** A {@code NetworkService.toJson}-like network: a mainframe, relays, devices linked to every node in range. */
    static JsonObject full(int devices, int relays) {
        var json = new JsonObject();
        var nodes = new JsonArray();
        var links = new JsonArray();
        String mf = UUID.randomUUID().toString();
        nodes.add(node(mf, "mainframe", "Mainframe", "online", null, false));
        var relayIds = new String[relays];
        for (int r = 0; r < relays; r++) {
            relayIds[r] = UUID.randomUUID().toString();
            nodes.add(node(relayIds[r], "relay", "Relay " + r, "online", mf, false));
            var l = new JsonObject();
            l.addProperty("a", mf);
            l.addProperty("b", relayIds[r]);
            l.addProperty("signal", 0.5);
            l.addProperty("kind", "radio");
            links.add(l);
        }
        for (int d = 0; d < devices; d++) {
            String id = UUID.randomUUID().toString();
            String parent = relays == 0 ? mf : relayIds[d % relays];
            nodes.add(node(id, "uplink", ("Device " + d + " ").repeat(8).substring(0, 48), d % 50 == 0 ? "no_route" : "online", d % 50 == 0 ? null : parent, true));
            for (String to : relayIds) {
                var l = new JsonObject();
                l.addProperty("a", to);
                l.addProperty("b", id);
                l.addProperty("signal", 0.42);
                l.addProperty("dist", 12.3);
                l.addProperty("kind", "radio");
                links.add(l);
            }
        }
        json.add("nodes", nodes);
        json.add("links", links);
        return json;
    }

    @Test
    void biggestScreenNetworkFitsTheView() {
        var full = full(1500, 8);
        var compact = NetworkService.compactForScreen(full, "minecraft:overworld", NetworkService.SCREEN_MAX_DEVICES);
        int chars = Json.GSON.toJson(compact).length();
        assertTrue(chars < CloudMenu.MAX_VIEW / 2, "compact network " + chars + " chars (full: " + Json.GSON.toJson(full).length() + ")");
        assertEquals(1500 - NetworkService.SCREEN_MAX_DEVICES, compact.get("more").getAsInt());
        assertEquals(1 + 8 + NetworkService.SCREEN_MAX_DEVICES, compact.getAsJsonArray("nodes").size());
        // problems first: all 30 devices without a route are listed
        long noRoute = 0;
        for (var e : compact.getAsJsonArray("nodes")) if ("no_route".equals(e.getAsJsonObject().get("status").getAsString())) noRoute++;
        assertEquals(30, noRoute);
        // only tree links (each listed device to its parent, relays to the mainframe), short ids, no dimension per node
        assertTrue(compact.getAsJsonArray("links").size() <= 8 + NetworkService.SCREEN_MAX_DEVICES);
        var first = compact.getAsJsonArray("nodes").get(0).getAsJsonObject();
        assertEquals("n0", first.get("id").getAsString());
        assertFalse(first.has("dim"));
        assertEquals("minecraft:overworld", compact.get("dim").getAsString());
    }

    @Test
    void probesKeepTheirTypeAndTarget() {
        var full = full(2, 0);
        var probe = full.getAsJsonArray("nodes").get(1).getAsJsonObject();
        probe.addProperty("type", "probe");
        var target = new JsonObject();
        target.addProperty("x", 101);
        target.addProperty("y", 64);
        target.addProperty("z", -7);
        probe.add("target", target);
        var compact = NetworkService.compactForScreen(full, "minecraft:overworld", NetworkService.SCREEN_MAX_DEVICES);
        JsonObject found = null;
        for (var e : compact.getAsJsonArray("nodes")) if ("probe".equals(e.getAsJsonObject().get("type").getAsString())) found = e.getAsJsonObject();
        assertNotNull(found, "the probe keeps its own type");
        assertEquals(101, found.getAsJsonObject("target").get("x").getAsInt());
        assertEquals(-7, found.getAsJsonObject("target").get("z").getAsInt());
        assertFalse(found.getAsJsonObject("target").has("y"), "the mini map only needs x/z");
        for (var e : compact.getAsJsonArray("nodes")) {
            if (!"probe".equals(e.getAsJsonObject().get("type").getAsString())) assertFalse(e.getAsJsonObject().has("target"));
        }
    }
}
