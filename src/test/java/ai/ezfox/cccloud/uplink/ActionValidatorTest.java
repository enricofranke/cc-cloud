package ai.ezfox.cccloud.uplink;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionValidatorTest {
    private static Widget widget(WidgetType type, Map<String, Object> opts) throws Exception {
        return WidgetFactory.create(type, "w", type == WidgetType.LABEL ? "text" : "Label", opts);
    }

    @Test
    void unknownWidgetIsRejected() {
        assertEquals("unknown_widget", ActionValidator.validate(null, new JsonPrimitive(true)).error());
    }

    @Test
    void buttonAcceptsOnlyTrue() throws Exception {
        var button = widget(WidgetType.BUTTON, Map.of());
        assertTrue(ActionValidator.validate(button, new JsonPrimitive(true)).ok());
        assertEquals(new JsonPrimitive(true), ActionValidator.validate(button, new JsonPrimitive(true)).value());
        for (var bad : new Object[]{false, 1, "true"}) {
            assertEquals("invalid_value", ActionValidator.validate(button, Json.fromLua(bad)).error(), String.valueOf(bad));
        }
        assertEquals("invalid_value", ActionValidator.validate(button, JsonNull.INSTANCE).error());
        assertEquals("invalid_value", ActionValidator.validate(button, null).error());
        assertEquals("invalid_value", ActionValidator.validate(button, new JsonObject()).error());
    }

    @Test
    void toggleAcceptsOnlyBooleans() throws Exception {
        var toggle = widget(WidgetType.TOGGLE, Map.of());
        assertTrue(ActionValidator.validate(toggle, new JsonPrimitive(false)).ok());
        assertTrue(ActionValidator.validate(toggle, new JsonPrimitive(true)).ok());
        assertFalse(ActionValidator.validate(toggle, new JsonPrimitive(1)).ok());
        assertFalse(ActionValidator.validate(toggle, new JsonPrimitive("false")).ok());
    }

    @Test
    void sliderAcceptsNumbersInRangeSnappedToStep() throws Exception {
        var slider = widget(WidgetType.SLIDER, Map.of("min", 0.0, "max", 100.0, "step", 5.0));
        assertEquals(new JsonPrimitive(45), ActionValidator.validate(slider, new JsonPrimitive(43)).value());
        assertEquals(new JsonPrimitive(100), ActionValidator.validate(slider, new JsonPrimitive(100)).value());
        assertEquals(new JsonPrimitive(0), ActionValidator.validate(slider, new JsonPrimitive(0)).value());
        assertEquals("invalid_value", ActionValidator.validate(slider, new JsonPrimitive(-0.1)).error());
        assertEquals("invalid_value", ActionValidator.validate(slider, new JsonPrimitive(100.1)).error());
        assertEquals("invalid_value", ActionValidator.validate(slider, new JsonPrimitive("50")).error());
        assertEquals("invalid_value", ActionValidator.validate(slider, new JsonPrimitive(true)).error());
        assertEquals("invalid_value", ActionValidator.validate(slider, JsonParser.parseString("NaN")).error());

        var fine = widget(WidgetType.SLIDER, Map.of("min", 0.0, "max", 1.0, "step", 0.1));
        assertEquals(new JsonPrimitive(0.3), ActionValidator.validate(fine, new JsonPrimitive(0.30000000000000004)).value());
        var offGrid = widget(WidgetType.SLIDER, Map.of("min", 0.0, "max", 10.0, "step", 4.0));
        assertEquals(new JsonPrimitive(8), ActionValidator.validate(offGrid, new JsonPrimitive(10)).value(), "snapping never leaves min..max");
    }

    @Test
    void gaugesAndLabelsAcceptNoActions() throws Exception {
        assertEquals("not_interactive", ActionValidator.validate(widget(WidgetType.GAUGE, Map.of()), new JsonPrimitive(5)).error());
        assertEquals("not_interactive", ActionValidator.validate(widget(WidgetType.LABEL, Map.of()), new JsonPrimitive("x")).error());
    }

    @Test
    void pinWidgetsNeedAVerifiedPin() throws Exception {
        var button = widget(WidgetType.BUTTON, Map.of("pin", true, "color", "red"));
        assertEquals("pin_required", ActionValidator.validate(button, new JsonPrimitive(true)).error());
        assertTrue(ActionValidator.validate(button, new JsonPrimitive(true), true).ok());
    }
}
