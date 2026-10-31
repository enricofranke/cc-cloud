package ai.ezfox.cccloud.uplink;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.uplink.WidgetFactory.WidgetException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A frame is the only way script data reaches a canvas: everything hostile has to stop here (the dashboard checks again). */
class CanvasFrameTest {
    /** A Lua list as CC hands it over: double keys, doubles for numbers. */
    private static Map<Object, Object> lua(Object... entries) {
        var map = new HashMap<Object, Object>();
        for (int i = 0; i < entries.length; i++) if (entries[i] != null) map.put((double) (i + 1), entries[i] instanceof Integer n ? (Object) (double) n : entries[i]);
        return map;
    }

    private static Map<Object, Object> frame(List<Map<Object, Object>> commands) {
        var map = new HashMap<Object, Object>();
        for (int i = 0; i < commands.size(); i++) map.put((double) (i + 1), commands.get(i));
        return map;
    }

    private static CanvasFrame.Parsed one(Object... command) throws WidgetException {
        return CanvasFrame.parse(lua((Object) lua(command)));
    }

    private static void rejected(String why, Object... command) {
        assertThrows(WidgetException.class, () -> one(command), why);
    }

    @Test
    void acceptsEveryOperation() throws Exception {
        var frame = CanvasFrame.parse(frame(List.of(
            lua("c", "panel"), lua("r", 0, 0, 10, 10, "red"), lua("R", 1, 1, 8, 8, "#336699", 2), lua("l", 0, 0, 5.25, 5, 16384.0),
            lua("o", 5, 5, 4, "cyan"), lua("O", 5, 5, 4, "green", 1), lua("p", "gold", 1, 1, 2, 2), lua("t", 4, 4, "Hallo <b>", "text", 12, "c"),
            lua("g", 0, 0, 10, 10, "red", "#00000080", "v"), lua("i", 2, 2, "flame", 16, "orange"), lua("s", 2, 2, "minecraft:iron_ingot", 16),
            lua("b", 0, 0, 2, "0f f", 4))));
        assertEquals(12, frame.value().getAsJsonArray("c").size());
        assertEquals("[\"l\",0,0,5.3,5,\"red\"]", frame.value().getAsJsonArray("c").get(3).toString(), "numbers rounded, colors.red named");
        assertTrue(frame.bytes() > 100);
    }

    @Test
    void textStaysPlainDataNotMarkup() throws Exception {
        // text may say anything: the dashboard writes it with fillText. Control characters never pass.
        assertEquals("<script>alert(1)</script>", one("t", 0, 0, "<script>alert(1)</script>", "text").value().getAsJsonArray("c").get(0).getAsJsonArray().get(3).getAsString());
        rejected("control characters", "t", 0, 0, "a\nb", "text");
        rejected("too long", "t", 0, 0, "x".repeat(Limits.CANVAS_TEXT + 1), "text");
    }

    @Test
    void rejectsAnythingThatCouldBecomeCssOrAUrl() {
        for (var color : new String[]{"url(http://evil)", "red;background:url(x)", "#12", "#1234567", "rgb(1,2,3)", "linear-gradient(red,blue)", "var(--x)", ""}) {
            rejected("color " + color, "r", 0, 0, 1, 1, color);
        }
        rejected("number that is no colors.* value", "r", 0, 0, 1, 1, 3.0);
        rejected("icon", "i", 0, 0, "../../evil");
        rejected("item with a url", "s", 0, 0, "http://evil/x.png");
        rejected("item with a path", "s", 0, 0, "minecraft:../../x");
        rejected("unknown operation", "x", 0, 0);
        rejected("script as operation", "<script>", 0, 0);
    }

    @Test
    void rejectsBadNumbersAndShapes() {
        rejected("NaN", "r", Double.NaN, 0, 1, 1, "red");
        rejected("infinity", "r", Double.POSITIVE_INFINITY, 0, 1, 1, "red");
        rejected("huge", "r", 1e9, 0, 1, 1, "red");
        rejected("negative size", "r", 0, 0, -1, 1, "red");
        rejected("too few arguments", "r", 0, 0, 1);
        rejected("too many arguments", "r", 0, 0, 1, 1, "red", 3);
        rejected("line width", "l", 0, 0, 1, 1, "red", 0);
        rejected("font size", "t", 0, 0, "x", "red", 500);
        rejected("odd pixels", "p", "red", 1, 2, 3);
        rejected("bitmap characters", "b", 0, 0, 2, "<>");
        rejected("broken rows", "b", 0, 0, 2, "012");
        assertThrows(WidgetException.class, () -> CanvasFrame.parse("not a table"));
        assertThrows(WidgetException.class, () -> CanvasFrame.parse(lua("not a command")));
        assertThrows(WidgetException.class, () -> CanvasFrame.parse(null));
    }

    @Test
    void limitsFrameSize() throws Exception {
        var many = new java.util.ArrayList<Map<Object, Object>>();
        for (int i = 0; i < Limits.CANVAS_COMMANDS + 1; i++) many.add(lua("r", 0, 0, 1, 1, "red"));
        assertThrows(WidgetException.class, () -> CanvasFrame.parse(frame(many)), "too many commands");

        var big = new java.util.ArrayList<Map<Object, Object>>();
        for (int i = 0; i < Limits.CANVAS_COMMANDS; i++) big.add(lua("t", 0, 0, "x".repeat(Limits.CANVAS_TEXT), "text"));
        assertThrows(WidgetException.class, () -> CanvasFrame.parse(frame(big)), "too many bytes");

        var items = new java.util.ArrayList<Map<Object, Object>>();
        for (int i = 0; i < Limits.CANVAS_ITEMS + 1; i++) items.add(lua("s", 0, 0, "minecraft:stone"));
        assertThrows(WidgetException.class, () -> CanvasFrame.parse(frame(items)), "too many item icons");
        assertEquals(Limits.CANVAS_ITEMS, CanvasFrame.parse(frame(items.subList(0, Limits.CANVAS_ITEMS))).value().getAsJsonArray("c").size());
    }

    @Test
    void widgetOptions() throws Exception {
        var w = WidgetFactory.create(WidgetType.CANVAS, "map", "Map", Map.of("width", 320.0, "height", 200.0, "bg", "panel", "input", "drag"));
        assertEquals(320, w.opts().get("width").getAsInt());
        assertEquals(90, WidgetFactory.create(WidgetType.CANVAS, "map", "Map", null).opts().get("height").getAsInt());
        assertThrows(WidgetException.class, () -> WidgetFactory.create(WidgetType.CANVAS, "m", "M", Map.of("width", 100000.0)));
        assertThrows(WidgetException.class, () -> WidgetFactory.create(WidgetType.CANVAS, "m", "M", Map.of("bg", "url(x)")));
        assertThrows(WidgetException.class, () -> WidgetFactory.create(WidgetType.CANVAS, "m", "M", Map.of("input", "hover")));
        assertThrows(WidgetException.class, () -> WidgetFactory.create(WidgetType.CANVAS, "m", "M", Map.of("pin", true)), "a PIN needs input");
        assertThrows(WidgetException.class, () -> WidgetFactory.create(WidgetType.CANVAS, "m", "M", Map.of("pin", true, "input", "drag")), "a dialog cannot follow a drag");
        assertThrows(WidgetException.class, () -> WidgetFactory.valueFor(w, "x"), "drawn with draw(), not set()");
    }

    private static JsonObject action(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void inputOnlyReachesLuaWhenTheCanvasAskedForIt() throws Exception {
        var none = WidgetFactory.create(WidgetType.CANVAS, "c", "C", null);
        var click = WidgetFactory.create(WidgetType.CANVAS, "c", "C", Map.of("input", "click", "pin", true));
        var drag = WidgetFactory.create(WidgetType.CANVAS, "c", "C", Map.of("input", "drag", "width", 100.0, "height", 50.0));
        var clickOp = Op.parse(action("{\"op\":\"canvas\",\"kind\":\"click\",\"x\":5,\"y\":6}"));
        var downOp = Op.parse(action("{\"op\":\"canvas\",\"kind\":\"down\",\"x\":5,\"y\":6}"));
        assertEquals(new Op.CanvasInput("click", 5, 6), clickOp);
        assertEquals("not_interactive", ActionValidator.validate(none, null, clickOp, false).error());
        assertEquals("pin_required", ActionValidator.validate(click, null, clickOp, false).error());
        assertTrue(ActionValidator.validate(click, null, clickOp, true).ok());
        assertEquals("invalid_value", ActionValidator.validate(click, null, downOp, true).error(), "no drag on a click canvas");
        assertTrue(ActionValidator.validate(drag, null, downOp, false).ok());
        assertEquals("invalid_value", ActionValidator.validate(drag, null, clickOp, false).error());
        assertEquals("invalid_value", ActionValidator.validate(drag, null, new Op.CanvasInput("drag", 100, 0), false).error(), "outside the surface");
        assertEquals("invalid_value", ActionValidator.validate(drag, new com.google.gson.JsonPrimitive(true), null, false).error(), "no scalars");
        for (var bad : new String[]{"{\"op\":\"canvas\",\"kind\":\"hover\",\"x\":1,\"y\":1}", "{\"op\":\"canvas\",\"kind\":\"click\",\"x\":-1,\"y\":1}",
            "{\"op\":\"canvas\",\"kind\":\"click\",\"x\":1.5,\"y\":1}", "{\"op\":\"canvas\",\"kind\":\"click\",\"x\":1,\"y\":99999}",
            "{\"op\":\"canvas\",\"kind\":\"click\",\"x\":1,\"y\":1,\"extra\":true}"}) {
            assertNull(Op.parse(action(bad)), bad);
        }
    }

    @Test
    void framesStayOffInGameClients() {
        String frame = "{\"canvas\":1,\"c\":[[\"c\",\"red\"]]}";
        assertEquals("{\"t\":\"values\",\"s\":{\"u\":{\"temp\":1}}}", CanvasFrame.withoutFrames("{\"t\":\"values\",\"s\":{\"u\":{\"temp\":1}}}"));
        assertNull(CanvasFrame.withoutFrames("{\"t\":\"values\",\"s\":{\"u\":{\"map\":" + frame + "}}}"), "nothing left");
        assertEquals("{\"t\":\"values\",\"s\":{\"u\":{\"temp\":1}}}", CanvasFrame.withoutFrames("{\"t\":\"values\",\"s\":{\"u\":{\"map\":" + frame + ",\"temp\":1}}}"));
        String section = CanvasFrame.withoutFrames("{\"t\":\"uplink\",\"widgets\":[{\"id\":\"map\",\"type\":\"canvas\",\"value\":" + frame + "},{\"id\":\"t\",\"type\":\"gauge\",\"value\":1}]}");
        assertEquals("{\"t\":\"uplink\",\"widgets\":[{\"id\":\"map\",\"type\":\"canvas\"},{\"id\":\"t\",\"type\":\"gauge\",\"value\":1}]}", section);
        String snapshot = CanvasFrame.withoutFrames("{\"t\":\"snapshot\",\"uplinks\":[{\"widgets\":[{\"id\":\"map\",\"value\":" + frame + "}]}]}");
        assertEquals("{\"t\":\"snapshot\",\"uplinks\":[{\"widgets\":[{\"id\":\"map\"}]}]}", snapshot);
    }

    @Test
    void framesAreNotSaved() throws Exception {
        var w = WidgetFactory.create(WidgetType.CANVAS, "map", "Map", null);
        w.setValue(one("c", "red").value());
        assertFalse(w.toSaveJson().has("value"));
        assertTrue(w.toJson().has("value"), "the dashboard does get it");
    }
}
