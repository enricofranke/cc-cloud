package ai.ezfox.cccloud.uplink;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionValidatorV2Test {
    private static Map<Object, Object> list(String... values) {
        var map = new HashMap<Object, Object>();
        for (int i = 0; i < values.length; i++) map.put((double) (i + 1), values[i]);
        return map;
    }

    private static ActionValidator.Result act(Widget w, JsonElement value) {
        Op op = value.isJsonObject() ? Op.parse(value.getAsJsonObject()) : null;
        return ActionValidator.validate(w, value, op, false);
    }

    @Test
    void selectAndButtonsAcceptOnlyTheirOptions() throws Exception {
        for (var type : new WidgetType[]{WidgetType.SELECT, WidgetType.BUTTONS}) {
            var w = WidgetFactory.create(type, "s", "S", Map.of("options", list("low", "high")));
            assertEquals(new JsonPrimitive("high"), act(w, new JsonPrimitive("high")).value());
            assertEquals("invalid_value", act(w, new JsonPrimitive("mid")).error());
            assertEquals("invalid_value", act(w, new JsonPrimitive(1)).error());
            assertEquals("invalid_value", act(w, new JsonPrimitive(true)).error());
        }
    }

    @Test
    void numberIsRangeCheckedAndSnapped() throws Exception {
        var w = WidgetFactory.create(WidgetType.NUMBER, "n", "N", Map.of("min", 0.0, "max", 100.0, "step", 5.0));
        assertEquals(new JsonPrimitive(15), act(w, new JsonPrimitive(13.4)).value());
        assertEquals("invalid_value", act(w, new JsonPrimitive(101)).error());
        assertEquals("invalid_value", act(w, new JsonPrimitive("5")).error());
        var free = WidgetFactory.create(WidgetType.NUMBER, "n", "N", Map.of("min", 0.0, "max", 10.0));
        assertEquals(new JsonPrimitive(3.3), act(free, new JsonPrimitive(3.3)).value(), "no step: not snapped");
    }

    @Test
    void textIsLimitedAndControlCharactersAreStripped() throws Exception {
        var w = WidgetFactory.create(WidgetType.TEXT, "t", "T", Map.of("maxLength", 5.0));
        assertEquals(new JsonPrimitive("hello"), act(w, new JsonPrimitive("hello")).value());
        assertEquals(new JsonPrimitive("ab"), act(w, new JsonPrimitive("a\nb\u0000")).value());
        assertEquals("invalid_value", act(w, new JsonPrimitive("toolong")).error());
        assertEquals("invalid_value", act(w, new JsonPrimitive(5)).error());
    }

    @Test
    void readOnlyWidgetsRejectActions() throws Exception {
        for (var type : new WidgetType[]{WidgetType.CHART, WidgetType.PROGRESS, WidgetType.INDICATOR, WidgetType.JOBS}) {
            var w = WidgetFactory.create(type, "r", "R", null);
            assertEquals("not_interactive", act(w, new JsonPrimitive(1)).error(), type.id());
        }
    }

    @Test
    void itemSearchOnlyTakesCraftWithinItsMax() throws Exception {
        var w = WidgetFactory.create(WidgetType.ITEM_SEARCH, "s", "S", Map.of("max", 64.0));
        var ok = act(w, JsonParser.parseString("{\"op\":\"craft\",\"name\":\"minecraft:glass\",\"count\":64}"));
        assertTrue(ok.ok());
        assertEquals(new Op.Craft("minecraft:glass", 64), ok.op());
        assertEquals("too_many", act(w, JsonParser.parseString("{\"op\":\"craft\",\"name\":\"minecraft:glass\",\"count\":65}")).error());
        assertEquals("invalid_value", act(w, JsonParser.parseString("{\"op\":\"add\",\"name\":\"minecraft:glass\"}")).error());
        assertEquals("invalid_value", act(w, new JsonPrimitive(true)).error());
    }

    @Test
    void watchlistTakesOnlyWatchOps() throws Exception {
        var w = WidgetFactory.create(WidgetType.WATCHLIST, "w", "W", null);
        assertTrue(act(w, JsonParser.parseString("{\"op\":\"add\",\"name\":\"minecraft:iron_ingot\",\"min\":64}")).ok());
        assertTrue(act(w, JsonParser.parseString("{\"op\":\"remove\",\"name\":\"minecraft:iron_ingot\"}")).ok());
        assertTrue(act(w, JsonParser.parseString("{\"op\":\"set_min\",\"name\":\"minecraft:iron_ingot\",\"min\":0}")).ok());
        assertEquals("invalid_value", act(w, JsonParser.parseString("{\"op\":\"craft\",\"name\":\"minecraft:glass\",\"count\":1}")).error());
        assertEquals("invalid_value", act(w, new JsonPrimitive("x")).error());
    }

    @Test
    void scalarWidgetsRejectOps() throws Exception {
        var toggle = WidgetFactory.create(WidgetType.TOGGLE, "t", "T", null);
        assertEquals("invalid_value", act(toggle, JsonParser.parseString("{\"op\":\"remove\",\"name\":\"a:b\"}")).error());
    }

    @Test
    void pinAppliesToNewInteractiveWidgets() throws Exception {
        var w = WidgetFactory.create(WidgetType.SELECT, "s", "S", Map.of("options", list("a"), "pin", true));
        assertEquals("pin_required", ActionValidator.validate(w, new JsonPrimitive("a"), null, false).error());
        assertTrue(ActionValidator.validate(w, new JsonPrimitive("a"), null, true).ok());
        var search = WidgetFactory.create(WidgetType.ITEM_SEARCH, "s", "S", Map.of("pin", true));
        var craft = JsonParser.parseString("{\"op\":\"craft\",\"name\":\"a:b\",\"count\":1}");
        assertEquals("pin_required", ActionValidator.validate(search, craft, Op.parse(craft.getAsJsonObject()), false).error());
    }
}
