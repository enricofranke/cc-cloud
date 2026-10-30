package ai.ezfox.cccloud.gui;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The live card preview of the uplink/bridge screens: compact, bounded, follows the dashboard layout. */
class CardPreviewTest {
    private static JsonObject snapshot(List<Widget> widgets) {
        var json = new JsonObject();
        json.addProperty("title", "Reactor");
        var array = new JsonArray();
        for (var w : widgets) array.add(w.toJson());
        json.add("widgets", array);
        return json;
    }

    @Test
    void compactValuesPerType() throws Exception {
        var gauge = WidgetFactory.create(WidgetType.GAUGE, "temp", "Core temp", Map.of("min", 0.0, "max", 1200.0, "unit", "K", "warn", 900.0));
        gauge.setValue(Json.number(842));
        var chart = WidgetFactory.create(WidgetType.CHART, "out", "Output", Map.of("points", 100.0));
        for (int i = 0; i < 50; i++) chart.push(i);
        var select = WidgetFactory.create(WidgetType.SELECT, "mode", "Mode", Map.of("options", Map.of(1, "Split", 2, "Transfer")));
        var note = WidgetFactory.create(WidgetType.LABEL, "note", "Output stable", null);
        var preview = CardPreview.of(snapshot(List.of(gauge, chart, select, note,
            WidgetFactory.create(WidgetType.TOGGLE, "pump", "Pump", Map.of("value", true)))), null, "u1");
        var widgets = preview.getAsJsonArray("widgets");
        assertEquals(5, widgets.size());
        var g = widgets.get(0).getAsJsonObject();
        assertEquals("gauge", g.get("t").getAsString());
        assertEquals(842, g.get("n").getAsDouble());
        assertEquals(1200, g.get("max").getAsDouble());
        assertEquals("K", g.get("u").getAsString());
        assertEquals(900, g.get("warn").getAsDouble());
        var h = widgets.get(1).getAsJsonObject().getAsJsonArray("h");
        assertEquals(CardPreview.HISTORY, h.size(), "only the last points");
        assertEquals(49, h.get(h.size() - 1).getAsDouble());
        assertEquals("Split", widgets.get(2).getAsJsonObject().get("s").getAsString());
        var label = widgets.get(3).getAsJsonObject();
        assertEquals("", label.get("l").getAsString(), "a label widget is just its text");
        assertEquals("Output stable", label.get("s").getAsString());
        assertTrue(widgets.get(4).getAsJsonObject().get("b").getAsBoolean());
    }

    @Test
    void boundedAndFollowsTheLayout() throws Exception {
        var many = new java.util.ArrayList<Widget>();
        for (int i = 0; i < 30; i++) many.add(WidgetFactory.create(WidgetType.LABEL, "l" + i, "x".repeat(200), null));
        var layout = JsonParser.parseString("{\"v\":2,\"pages\":[],\"cards\":{},\"widgets\":{\"u1/l0\":{\"hidden\":true},\"u1/l1\":{\"label\":\"Renamed\"}}}")
            .getAsJsonObject();
        var preview = CardPreview.of(snapshot(many), layout, "u1");
        var widgets = preview.getAsJsonArray("widgets");
        assertEquals(CardPreview.MAX_WIDGETS, widgets.size());
        assertEquals(30 - 1 - CardPreview.MAX_WIDGETS, preview.get("more").getAsInt(), "hidden widgets are left out, the rest is counted");
        assertEquals("Renamed", widgets.get(0).getAsJsonObject().get("l").getAsString(), "the dashboard label");
        assertTrue(widgets.get(1).getAsJsonObject().get("s").getAsString().length() <= CardPreview.MAX_TEXT);
        assertTrue(preview.toString().length() < 8 * 1024, "small on the wire: " + preview.toString().length());
    }

    @Test
    void summarySplitsShowsAndControls() throws Exception {
        var preview = CardPreview.of(snapshot(List.of(
            WidgetFactory.create(WidgetType.GAUGE, "a", "Flow", null),
            WidgetFactory.create(WidgetType.SELECT, "b", "Transfer mode", Map.of("options", Map.of(1, "A", 2, "B"))),
            WidgetFactory.create(WidgetType.BUTTON, "c", "SCRAM", null),
            WidgetFactory.create(WidgetType.INDICATOR, "d", "Running", null))), null, "u1");
        var summary = CardPreview.summary(preview, 1);
        assertEquals(List.of("Flow"), summary.shows());
        assertEquals(1, summary.moreShows());
        assertEquals(List.of("Transfer mode"), summary.controls());
        assertEquals(1, summary.moreControls());
    }
}
