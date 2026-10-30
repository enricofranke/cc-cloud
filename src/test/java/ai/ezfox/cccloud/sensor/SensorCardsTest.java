package ai.ezfox.cccloud.sensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.uplink.ActionValidator;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.web.AccessGate;
import com.google.gson.JsonPrimitive;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The sensor's card: widgets, the public form without player names, names switched off, permissions and actions. */
class SensorCardsTest {
    static final SensorEntry STEVE = new SensorEntry(SensorKind.PLAYER, "minecraft:player", "Steve");
    static final SensorEntry ZOMBIE = new SensorEntry(SensorKind.HOSTILE, "minecraft:zombie", "Zombie");

    static SensorCards.State state(boolean names, SensorSettings settings) {
        var events = List.of(new SensorLog.Event(1, 1000, true, STEVE), new SensorLog.Event(2, 2000, true, ZOMBIE), new SensorLog.Event(3, 3000, false, STEVE));
        return new SensorCards.State(settings, List.of(STEVE, ZOMBIE, ZOMBIE), events, 2, null, names, 9, 5, 9);
    }

    static Widget widget(SensorCards.Card card, String id) {
        return card.widgets().stream().filter(w -> w.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void widgetsAndTheirOrder() {
        var card = SensorCards.build(state(true, SensorSettings.DEFAULT));
        assertEquals(List.of("activity", "occupied", "armed", "detect", "notify", "team", "output", "allow", "deny", "zone_x", "zone_y", "zone_z", "place"),
            card.widgets().stream().map(Widget::id).toList());
        assertEquals("sensor_log", widget(card, "activity").type().id());
        assertEquals(true, widget(card, "occupied").value().getAsBoolean());
        assertEquals("Everyone", widget(card, "detect").value().getAsString());
        assertEquals("Warning", widget(card, "notify").value().getAsString());
        var noZoneY = SensorCards.build(new SensorCards.State(SensorSettings.DEFAULT, List.of(), List.of(), 0, null, true, 9, 1, 9));
        assertTrue(noZoneY.widgets().stream().noneMatch(w -> w.id().equals("zone_y")), "a fixed height of 1: no field");
    }

    @Test
    void namesOnlyInTheFullForm() {
        var settings = SensorSettings.DEFAULT.withFilter(SensorFilter.DEFAULT.withAllow(List.of("Steve")).withDeny(List.of("Alex")));
        var card = SensorCards.build(state(true, settings));
        var full = widget(card, "activity").value().toString();
        assertTrue(full.contains("\"n\":\"Steve\""), full);
        assertTrue(full.contains("Steve and 2 Zombies inside"), full);
        var pub = card.publicValues().get("activity").toString();
        assertFalse(pub.contains("Steve"), "the public form has no names: " + pub);
        assertTrue(pub.contains("A player and 2 Zombies inside"), pub);
        assertTrue(pub.contains("\"Zombie\""), "mobs keep their type");
        assertEquals(new JsonPrimitive(""), card.publicValues().get("allow"), "the lists are private too");
        assertEquals(new JsonPrimitive(""), card.publicValues().get("deny"));
        assertEquals("Steve", widget(card, "allow").value().getAsString());

        var off = SensorCards.build(state(false, SensorSettings.DEFAULT));
        var offText = widget(off, "activity").value().toString();
        assertFalse(offText.contains("Steve"), "names off on the server: not even operators see them: " + offText);
        assertNull(off.publicValues().get("activity"), "then the full and the public form are the same");
    }

    @Test
    void permissionsOfTheCard() {
        assertEquals(Perm.ACT_REDSTONE, AccessGate.actionPerm("toggle", "direct", "armed", "sensor"), "arming is operating it");
        for (var id : List.of("detect", "notify", "team", "output", "allow", "deny", "zone_x", "place")) {
            assertEquals(Perm.NETWORK_LINK, AccessGate.actionPerm("select", "direct", id, "sensor"), "settings need network.link: " + id);
        }
    }

    @Test
    void actionsChangeTheSettings() {
        var s = SensorSettings.DEFAULT;
        var card = SensorCards.build(state(true, s));
        assertFalse(ActionValidator.validate(widget(card, "activity"), new JsonPrimitive(true)).ok(), "the activity is read-only");
        assertEquals(SensorFilter.Mode.HOSTILE, SensorActions.apply(s, "detect", new JsonPrimitive("Hostile mobs only")).settings().filter().mode());
        assertEquals(SensorSettings.Alert.OFF, SensorActions.apply(s, "notify", new JsonPrimitive("Off")).settings().alert());
        assertFalse(SensorActions.apply(s, "armed", new JsonPrimitive(false)).settings().armed());
        assertFalse(SensorActions.apply(s, "team", new JsonPrimitive(false)).settings().filter().ignoreTeam());
        assertFalse(SensorActions.apply(s, "output", new JsonPrimitive(false)).settings().redstone());
        assertEquals(List.of("Steve", "Alex"), SensorActions.apply(s, "allow", new JsonPrimitive("Steve, Alex")).settings().filter().allow());
        assertEquals("invalid_value", SensorActions.apply(s, "deny", new JsonPrimitive("not a name!")).error());
        assertEquals(7, SensorActions.apply(s, "zone_z", new JsonPrimitive(7)).settings().sizeZ());
        assertEquals(SensorSettings.Placement.AROUND, SensorActions.apply(s, "place", new JsonPrimitive("Around")).settings().placement());
        assertEquals("not_interactive", SensorActions.apply(s, "occupied", new JsonPrimitive(true)).error());
        assertEquals("unknown_widget", SensorActions.apply(s, "nope", new JsonPrimitive(true)).error());

        var r = SensorActions.screen(s, "zone", "5,3,7", 9, 5, 9);
        assertNotNull(r.settings());
        assertEquals("5×3×7 in front", r.settings().zoneText());
        assertEquals("invalid_value", SensorActions.screen(s, "zone", "10,3,3", 9, 5, 9).error(), "beyond the server's maximum");
        assertEquals("invalid_value", SensorActions.screen(s, "zone", "1,2", 9, 5, 9).error());
        assertEquals(SensorFilter.Mode.PLAYERS, SensorActions.screen(s, "detect", "players", 9, 5, 9).settings().filter().mode());
        assertEquals(SensorSettings.Alert.CRIT, SensorActions.screen(s, "notify", "crit", 9, 5, 9).settings().alert());
        assertEquals("invalid_value", SensorActions.screen(s, "armed", "maybe", 9, 5, 9).error());
    }
}
