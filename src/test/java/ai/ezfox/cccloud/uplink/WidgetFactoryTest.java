package ai.ezfox.cccloud.uplink;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonPrimitive;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WidgetFactoryTest {
    @Test
    void validatesIds() {
        assertDoesNotThrow(() -> WidgetFactory.checkId("temp_1"));
        for (Object bad : new Object[]{"Temp", "a-b", "", "x".repeat(33), 5, null}) {
            assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.checkId(bad), String.valueOf(bad));
        }
    }

    @Test
    void enforcesLengthLimits() {
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.BUTTON, "b", "x".repeat(Limits.LABEL + 1), null));
        assertDoesNotThrow(() -> WidgetFactory.create(WidgetType.BUTTON, "b", "x".repeat(Limits.LABEL), null));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.LABEL, "l", "x".repeat(Limits.STRING_VALUE + 1), null));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.checkText("title", "x".repeat(Limits.TITLE + 1), Limits.TITLE));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.GAUGE, "g", "G", Map.of("unit", "x".repeat(Limits.UNIT + 1))));
    }

    @Test
    void rejectsUnknownOptionsAndBadValues() {
        var e = assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.BUTTON, "b", "B", Map.of("colour", "red")));
        assertTrue(e.getMessage().contains("unknown option"));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.BUTTON, "b", "B", Map.of("color", "pink")));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.SLIDER, "s", "S", Map.of("min", 10.0, "max", 5.0)));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.SLIDER, "s", "S", Map.of("step", 0.0)));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.SLIDER, "s", "S", Map.of("value", 500.0)));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.GAUGE, "g", "G", Map.of("min", Double.NaN)));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.GAUGE, "g", "G", Map.of("pin", true)));
    }

    @Test
    void setValidatesValuesPerType() throws Exception {
        var toggle = WidgetFactory.create(WidgetType.TOGGLE, "t", "T", Map.of("value", true));
        assertEquals(new JsonPrimitive(true), toggle.value());
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.valueFor(toggle, 1.0));
        var button = WidgetFactory.create(WidgetType.BUTTON, "b", "B", null);
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.valueFor(button, true));
        var gauge = WidgetFactory.create(WidgetType.GAUGE, "g", "G", Map.of("min", 0.0, "max", 1200.0, "unit", "K", "warn", 900.0, "crit", 1100.0));
        assertEquals(new JsonPrimitive(512), WidgetFactory.valueFor(gauge, 512.0));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.valueFor(gauge, Double.POSITIVE_INFINITY));
        var label = WidgetFactory.create(WidgetType.LABEL, "l", "hello", null);
        assertEquals(new JsonPrimitive("12"), WidgetFactory.valueFor(label, 12.0));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.valueFor(label, "x".repeat(Limits.STRING_VALUE + 1)));
    }

    @Test
    void widgetsRoundTripThroughJson() throws Exception {
        var slider = WidgetFactory.create(WidgetType.SLIDER, "s", "Power", Map.of("min", 0.0, "max", 100.0, "step", 1.0, "unit", "%", "pin", true));
        var copy = Widget.fromJson(slider.toJson());
        assertNotNull(copy);
        assertEquals(slider.toJson(), copy.toJson());
        assertNull(Widget.fromJson(new com.google.gson.JsonObject()));
    }

    @org.junit.jupiter.api.Test
    void historyOption() throws Exception {
        var gauge = WidgetFactory.create(WidgetType.GAUGE, "g", "G", Map.of("history", "24h"));
        assertEquals("24h", gauge.toJson().getAsJsonObject("opts").get("history").getAsString());
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.GAUGE, "g", "G", Map.of("history", "1y")));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.BUTTON, "b", "B", Map.of("history", "1h")), "only numeric widgets");
    }
}
