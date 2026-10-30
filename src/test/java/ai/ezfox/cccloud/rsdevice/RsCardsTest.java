package ai.ezfox.cccloud.rsdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.ezfox.cccloud.uplink.ActionValidator;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.uplink.WidgetType;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The redstone cards: grouping, the automatic card, widgets per device kind, ids, "All on/off", PIN/confirm. */
class RsCardsTest {
    private static int seq;

    static RsDevice device(RsKind kind, String name, String group) {
        return device(kind, name, group, false, false, false, 0);
    }

    static RsDevice device(RsKind kind, String name, String group, boolean analog, boolean pin, boolean confirm, int level) {
        var id = UUID.nameUUIDFromBytes(("dev" + seq++).getBytes());
        return new RsDevice(id, kind, name, group, "minecraft:overworld", 0, 64, 0, analog, level, level > 0, 15, RsDevice.PULSE_DEFAULT, false, pin, confirm);
    }

    private static List<String> ids(RsCards.Card card) {
        return card.widgets().stream().map(Widget::id).toList();
    }

    @Test
    void groupsMergeCaseInsensitivelyAndUngroupedGoToTheAutomaticCard() {
        var a = device(RsKind.LAMP, "Lamp A", "Halle");
        var b = device(RsKind.LAMP, "Lamp B", " halle");
        var c = device(RsKind.SWITCH, "Gate", "");
        var d = device(RsKind.SENSOR, "", "");
        var cards = RsCards.build(List.of(a, b, c, d));
        assertEquals(2, cards.size());
        var auto = cards.get(0);
        assertEquals("", auto.groupKey());
        assertEquals(RsCards.DEFAULT_TITLE, auto.title(), "ungrouped devices: one automatic card per mainframe");
        assertEquals(List.of(c.id(), d.id()), auto.devices());
        var hall = cards.get(1);
        assertEquals("halle", hall.groupKey());
        assertEquals("Halle", hall.title(), "the title is the group as the first device spells it");
        assertEquals(List.of(a.id(), b.id()), hall.devices());
        assertTrue(ids(hall).containsAll(List.of(RsCards.ALL_ON, RsCards.ALL_OFF)), "two lamps: all on / all off");
        assertFalse(ids(auto).contains(RsCards.ALL_ON), "one switch: no all buttons");
    }

    @Test
    void widgetsPerKind() {
        var toggle = device(RsKind.SWITCH, "T", "");
        var analog = device(RsKind.SWITCH, "A", "", true, false, false, 7);
        var button = device(RsKind.BUTTON, "B", "");
        var lamp = device(RsKind.LAMP, "L", "", false, false, false, 9);
        var sensor = device(RsKind.SENSOR, "S", "", false, false, false, 4);
        assertEquals(WidgetType.TOGGLE, RsCards.widgets(toggle, 10).getFirst().type());
        var slider = RsCards.widgets(analog, 10).getFirst();
        assertEquals(WidgetType.SLIDER, slider.type());
        assertEquals(7, slider.value().getAsInt());
        assertEquals(15, slider.opts().get("max").getAsInt());
        assertEquals(WidgetType.BUTTON, RsCards.widgets(button, 10).getFirst().type());
        var lampWidgets = RsCards.widgets(lamp, 10);
        assertEquals(List.of(WidgetType.TOGGLE, WidgetType.SLIDER), lampWidgets.stream().map(Widget::type).toList());
        assertTrue(lampWidgets.get(0).value().getAsBoolean());
        assertEquals(9, lampWidgets.get(1).value().getAsInt(), "the brightness slider shows the light level");
        assertEquals(RsCards.HISTORY, lampWidgets.get(1).opts().get("history").getAsString(), "lamp light is recorded");
        assertEquals("L brightness", lampWidgets.get(1).label());
        var sensorWidgets = RsCards.widgets(sensor, 10);
        assertEquals(List.of(WidgetType.INDICATOR, WidgetType.GAUGE), sensorWidgets.stream().map(Widget::type).toList());
        assertEquals(RsCards.widgetId(sensor.id()) + RsCards.LEVEL, sensorWidgets.get(1).id());
        assertEquals(RsCards.HISTORY, sensorWidgets.get(1).opts().get("history").getAsString(), "sensor level is recorded");
        assertEquals("sensor", sensorWidgets.get(0).opts().get("icon").getAsString());
        // read-only sensor widgets, interactive controls
        assertEquals("not_interactive", ActionValidator.validate(sensorWidgets.get(0), new JsonPrimitive(true)).error());
        assertTrue(ActionValidator.validate(lampWidgets.get(1), new JsonPrimitive(0)).ok(), "slider 0 = off is valid");
        assertEquals("invalid_value", ActionValidator.validate(lampWidgets.get(1), new JsonPrimitive(16)).error());
    }

    @Test
    void pinAndConfirmFollowTheDevicesAndTheStrictestOneOnTheAllButtons() {
        var a = device(RsKind.LAMP, "A", "g", false, true, false, 0);
        var b = device(RsKind.SWITCH, "B", "g", false, false, true, 0);
        var card = RsCards.build(List.of(a, b)).getFirst();
        Widget lamp = card.widgets().stream().filter(w -> w.id().equals(RsCards.widgetId(a.id()))).findFirst().orElseThrow();
        assertTrue(ActionValidator.requiresPin(lamp));
        assertEquals("pin_required", ActionValidator.validate(lamp, new JsonPrimitive(true)).error());
        Widget all = card.widgets().stream().filter(w -> w.id().equals(RsCards.ALL_ON)).findFirst().orElseThrow();
        assertTrue(ActionValidator.requiresPin(all), "all on asks for the PIN when one of its devices does");
        assertTrue(all.opts().get("confirm").getAsBoolean(), "and confirms when one of them does");
    }

    @Test
    void bigGroupsContinueOnASecondCard() {
        var list = new ArrayList<RsDevice>();
        for (int i = 0; i < RsCards.MAX_DEVICES_PER_CARD + 3; i++) list.add(device(RsKind.SWITCH, String.format("S%02d", i), "Hall"));
        var cards = RsCards.build(list);
        assertEquals(2, cards.size());
        assertEquals(RsCards.MAX_DEVICES_PER_CARD, cards.get(0).devices().size());
        assertEquals("Hall (2)", cards.get(1).title());
        assertNotEquals(RsCards.sectionId(UUID.randomUUID(), "hall", 0), RsCards.sectionId(UUID.randomUUID(), "hall", 1));
    }

    @Test
    void stableIdsAndTargets() {
        var mf = UUID.randomUUID();
        assertEquals(RsCards.sectionId(mf, "halle", 0), RsCards.sectionId(mf, "halle", 0), "stable card ids");
        assertNotEquals(RsCards.sectionId(mf, "halle", 0), RsCards.sectionId(mf, "", 0));
        var id = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");
        assertEquals("d_123456789abc", RsCards.widgetId(id));
        assertEquals(new RsCards.Target("d_123456789abc", RsCards.Control.MAIN), RsCards.target("d_123456789abc"));
        assertEquals(new RsCards.Target("d_123456789abc", RsCards.Control.BRIGHTNESS), RsCards.target("d_123456789abc_b"));
        assertEquals(new RsCards.Target("d_123456789abc", RsCards.Control.LEVEL), RsCards.target("d_123456789abc_l"));
        assertEquals(RsCards.Control.ALL_OFF, RsCards.target(RsCards.ALL_OFF).control());
        assertNull(RsCards.target("lamp"), "not one of ours");
        assertNull(RsCards.target("d_12345"), "malformed");
        assertNull(RsCards.target("d_123456789abX"), "malformed");
    }

    @Test
    void defaultNamesAndTextRules() {
        var d = device(RsKind.LAMP, "", "");
        assertTrue(d.displayName().startsWith("Lamp "), d.displayName());
        assertTrue(RsDevice.validText("Halle 1 – Nord"));
        assertFalse(RsDevice.validText("a\nb"), "no control characters");
        assertFalse(RsDevice.validText("x".repeat(33)), "at most 32 characters");
        assertEquals(RsDevice.PULSE_MAX, RsDevice.clampPulse(99));
        assertEquals(RsDevice.PULSE_MIN, RsDevice.clampPulse(0));
    }
}
