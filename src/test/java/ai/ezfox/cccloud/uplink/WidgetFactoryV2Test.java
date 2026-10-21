package ai.ezfox.cccloud.uplink;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Lua API v2 widgets, layout options, pages and groups. */
class WidgetFactoryV2Test {
    private static Map<Object, Object> list(String... values) {
        var map = new HashMap<Object, Object>();
        for (int i = 0; i < values.length; i++) map.put((double) (i + 1), values[i]);
        return map;
    }

    private static void bad(WidgetType type, Map<?, ?> opts) {
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(type, "w", "W", opts), type + " " + opts);
    }

    @Test
    void commonLayoutOptionsOnEveryWidget() throws Exception {
        for (var type : new WidgetType[]{WidgetType.BUTTON, WidgetType.GAUGE, WidgetType.CHART, WidgetType.PROGRESS, WidgetType.INDICATOR, WidgetType.JOBS}) {
            var w = WidgetFactory.create(type, "w", "W", Map.of("page", "reactor", "group", "core", "span", 2.0, "order", 5.0, "icon", "flame", "color", "red"));
            assertEquals("reactor", w.opts().get("page").getAsString());
            assertEquals(2, w.opts().get("span").getAsInt());
            assertEquals("flame", w.opts().get("icon").getAsString());
        }
        bad(WidgetType.BUTTON, Map.of("span", 5.0));
        bad(WidgetType.BUTTON, Map.of("span", 1.5));
        bad(WidgetType.BUTTON, Map.of("order", -1.0));
        bad(WidgetType.BUTTON, Map.of("icon", "skull"));
        bad(WidgetType.BUTTON, Map.of("page", "Bad Page"));
        bad(WidgetType.BUTTON, Map.of("group", 3.0));
        var label = WidgetFactory.create(WidgetType.LABEL, "l", "text", Map.of("page", "p"));
        assertEquals("text", label.value().getAsString());
    }

    @Test
    void chartDefaultsAndLimits() throws Exception {
        var chart = WidgetFactory.create(WidgetType.CHART, "c", "Temp", Map.of("unit", "K", "min", 0.0, "max", 1200.0));
        assertEquals(Limits.CHART_POINTS_DEFAULT, chart.chartPoints());
        assertEquals(20, chart.chartIntervalTicks());
        assertEquals(0, chart.historySize());
        assertNotNull(chart.toJson().get("history"));
        bad(WidgetType.CHART, Map.of("points", 5.0));
        bad(WidgetType.CHART, Map.of("points", 301.0));
        bad(WidgetType.CHART, Map.of("interval", 0.1));
        bad(WidgetType.CHART, Map.of("style", "bar"));
        bad(WidgetType.CHART, Map.of("pin", true));
    }

    @Test
    void chartRingBufferIsCappedAndSampledByInterval() throws Exception {
        var chart = WidgetFactory.create(WidgetType.CHART, "c", "C", Map.of("points", 10.0, "interval", 1.0));
        assertFalse(chart.sample(0), "no value yet");
        chart.setValue(new JsonPrimitive(1));
        assertTrue(chart.sample(0));
        assertFalse(chart.sample(10), "interval not elapsed");
        for (int i = 1; i <= 25; i++) {
            chart.setValue(new JsonPrimitive(i));
            assertTrue(chart.sample(i * 20L));
        }
        assertEquals(10, chart.historySize());
        var history = chart.toJson().getAsJsonArray("history");
        assertEquals(16, history.get(0).getAsInt(), "oldest first");
        assertEquals(25, history.get(9).getAsInt());
        var copy = Widget.fromJson(chart.toSaveJson());
        assertNotNull(copy);
        assertEquals(10, copy.historySize(), "history survives save/load");
    }

    @Test
    void progressAndIndicator() throws Exception {
        var p = WidgetFactory.create(WidgetType.PROGRESS, "p", "P", Map.of("min", 0.0, "max", 10.0, "value", 3.0));
        assertEquals(3, p.value().getAsInt());
        assertEquals(new JsonPrimitive(7), WidgetFactory.valueFor(p, 7.0));
        bad(WidgetType.PROGRESS, Map.of("min", 5.0, "max", 5.0));
        var led = WidgetFactory.create(WidgetType.INDICATOR, "i", "I", Map.of("onColor", "green", "offColor", "red"));
        assertFalse(led.value().getAsBoolean());
        assertEquals(new JsonPrimitive(true), WidgetFactory.valueFor(led, true));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.valueFor(led, "on"));
        bad(WidgetType.INDICATOR, Map.of("onColor", "pink"));
    }

    @Test
    void selectAndButtonsNeedValidOptions() throws Exception {
        var select = WidgetFactory.create(WidgetType.SELECT, "s", "Mode", Map.of("options", list("low", "mid", "high")));
        assertEquals("low", select.value().getAsString(), "defaults to the first option");
        assertEquals(new JsonPrimitive("high"), WidgetFactory.valueFor(select, "high"));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.valueFor(select, "max"));
        assertEquals("mid", WidgetFactory.create(WidgetType.SELECT, "s", "S", Map.of("options", list("low", "mid"), "value", "mid")).value().getAsString());
        bad(WidgetType.SELECT, Map.of("options", list("a", "b"), "value", "c"));
        bad(WidgetType.SELECT, Map.of());
        bad(WidgetType.SELECT, Map.of("options", list("a", "a")));
        bad(WidgetType.SELECT, Map.of("options", list("")));
        bad(WidgetType.SELECT, Map.of("options", list("x".repeat(Limits.OPTION + 1))));
        bad(WidgetType.SELECT, Map.of("options", Map.of("a", "b")));
        String[] many = new String[Limits.MAX_OPTIONS + 1];
        for (int i = 0; i < many.length; i++) many[i] = "o" + i;
        bad(WidgetType.BUTTONS, Map.of("options", list(many)));
        var buttons = WidgetFactory.create(WidgetType.BUTTONS, "b", "B", Map.of("options", list("A", "B"), "pin", true));
        assertTrue(ActionValidator.requiresPin(buttons));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.valueFor(buttons, "A"), "buttons have no value");
    }

    @Test
    void numberAndText() throws Exception {
        var number = WidgetFactory.create(WidgetType.NUMBER, "n", "N", Map.of("min", 0.0, "max", 10.0, "step", 0.5, "value", 2.2));
        assertEquals(2.0, number.value().getAsDouble());
        assertEquals(new JsonPrimitive(9.5), WidgetFactory.valueFor(number, 9.6));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.valueFor(number, 11.0));
        var free = WidgetFactory.create(WidgetType.NUMBER, "n", "N", null);
        assertEquals(0, free.value().getAsInt());
        bad(WidgetType.NUMBER, Map.of("step", 0.0));
        bad(WidgetType.NUMBER, Map.of("min", 3.0, "max", 1.0));

        var text = WidgetFactory.create(WidgetType.TEXT, "t", "T", Map.of("maxLength", 8.0, "value", "hi"));
        assertEquals("hi", text.value().getAsString());
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.valueFor(text, "123456789"));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.valueFor(text, "a\nb"));
        bad(WidgetType.TEXT, Map.of("maxLength", 0.0));
        bad(WidgetType.TEXT, Map.of("maxLength", (double) Limits.STRING_VALUE + 1));
        bad(WidgetType.TEXT, Map.of("value", "line\u0007"));
    }

    @Test
    void bridgeWidgets() throws Exception {
        var search = WidgetFactory.create(WidgetType.ITEM_SEARCH, "s", "Search", Map.of("bridge", "me_cloud_bridge_0"), 1000);
        assertEquals(1000, search.opts().get("max").getAsInt(), "defaults to the configured max");
        assertNull(search.opts().get("bridge"), "the peripheral name is not sent to browsers");
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.ITEM_SEARCH, "s", "S", Map.of("max", 2000.0), 1000));
        var jobs = WidgetFactory.create(WidgetType.JOBS, "j", "Jobs", Map.of("bridge", "x"));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.valueFor(jobs, 1.0));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.create(WidgetType.WATCHLIST, "w", "W", Map.of("pin", true)));

        var ref = new BridgeRef("minecraft:overworld", 1, 2, 3, "me_cloud_bridge_0");
        var copy = Widget.fromJson(search.withBridge(ref).toSaveJson());
        assertEquals(ref, copy.bridge());
        assertFalse(search.toJson().has("bridge"), "binding is server-internal");
    }

    @Test
    void pagesAndGroups() throws Exception {
        var page = WidgetFactory.page("reactor", "Reactor", Map.of("icon", "flame"));
        assertEquals("{\"id\":\"reactor\",\"title\":\"Reactor\",\"icon\":\"flame\"}", page.toString());
        var group = WidgetFactory.group("core", "Core", Map.of("page", "reactor", "cols", 2.0));
        assertEquals(2, group.get("cols").getAsInt());
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.page("Bad", "x", null));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.page("p", "x".repeat(Limits.TITLE + 1), null));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.page("p", "P", Map.of("icon", "nope")));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.group("g", "G", Map.of("cols", 5.0)));
        assertThrows(WidgetFactory.WidgetException.class, () -> WidgetFactory.group("g", "G", Map.of("color", "red")));
    }

    @Test
    void maximalOptionsFitIntoTheOptsLimit() {
        var big = new HashMap<Object, Object>();
        for (int i = 0; i < Limits.MAX_OPTIONS; i++) big.put((double) (i + 1), "x".repeat(Limits.OPTION - 2) + (10 + i));
        assertDoesNotThrow(() -> WidgetFactory.create(WidgetType.SELECT, "s", "S", Map.of("options", big, "pin", true, "confirm", true,
            "page", "p".repeat(32), "group", "g".repeat(32), "icon", "warning", "color", "yellow", "span", 4.0, "order", 9999.0)));
    }
}
