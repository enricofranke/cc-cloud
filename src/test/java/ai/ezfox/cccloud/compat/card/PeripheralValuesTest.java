package ai.ezfox.cccloud.compat.card;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The pure rules of the Peripheral card: method names, captions, result formatting and the settings check. */
class PeripheralValuesTest {
    @Test
    void getterAndSetterNames() {
        for (String name : List.of("getEnergy", "isRunning", "listItems", "list", "size", "tanks", "getFE", "get_x", "getX2")) {
            assertTrue(PeripheralValues.isGetterName(name), name);
        }
        for (String name : List.of("get", "getenergy", "pushItems", "reset", "start", "is", "listing", "setEnabled", "g" + "x".repeat(60))) {
            assertFalse(PeripheralValues.isGetterName(name), name);
        }
        assertTrue(PeripheralValues.isSetterName("setEnabled"));
        assertFalse(PeripheralValues.isSetterName("settle"));
        assertFalse(PeripheralValues.isSetterName("set"));
        assertEquals(List.of("isEnabled", "getEnabled"), PeripheralValues.pairedGetters("setEnabled"));
    }

    @Test
    void captions() {
        assertEquals("Energy capacity", PeripheralValues.caption("getEnergyCapacity"));
        assertEquals("Running", PeripheralValues.caption("isRunning"));
        assertEquals("Items", PeripheralValues.caption("listItems"));
        assertEquals("Size", PeripheralValues.caption("size"));
        assertEquals("FE stored", PeripheralValues.caption("getFEStored"));
        assertEquals("Max burn time", PeripheralValues.caption("getMax_burn_time"));
        assertEquals("Enabled", PeripheralValues.caption("setEnabled"));
        assertTrue(PeripheralValues.caption("get" + "Long".repeat(30)).length() <= 48);
    }

    @Test
    void formatsEveryKindOfResult() {
        assertEquals("–", PeripheralValues.format(null));
        assertEquals("yes", PeripheralValues.format(true));
        assertEquals("27", PeripheralValues.format(27));
        assertEquals("1.23 M", PeripheralValues.format(1_234_567L));
        assertEquals("0.50", PeripheralValues.format(0.5));
        assertEquals("12.3", PeripheralValues.format(12.34));
        assertEquals("–", PeripheralValues.format(Double.NaN));
        assertEquals("monday", PeripheralValues.format(java.time.DayOfWeek.MONDAY));
        assertEquals("42", PeripheralValues.format(new Object[]{42}));
        assertEquals("1 · two", PeripheralValues.format(new Object[]{1, "two"}));
        assertEquals("–", PeripheralValues.format(new Object[0]));
        assertEquals("(object)", PeripheralValues.format(new Object()));
        assertEquals("3 entries", PeripheralValues.format(List.of(1, 2, 3)));
        assertEquals("2 entries", PeripheralValues.format(new int[2]));
        assertEquals("empty", PeripheralValues.format(Map.of()));
    }

    @Test
    void tables() {
        var slots = new TreeMap<Integer, Object>();
        slots.put(1, Map.of("name", "minecraft:stone", "count", 64));
        slots.put(2, Map.of("name", "minecraft:dirt", "count", 1));
        assertEquals("2 entries", PeripheralValues.format(slots), "list-like table");
        assertEquals("64× minecraft:stone", PeripheralValues.format(Map.of("name", "minecraft:stone", "count", 64)), "item detail");
        assertEquals("1000 mB minecraft:water", PeripheralValues.format(Map.of("name", "minecraft:water", "amount", 1000)), "fluid detail");
        var small = new LinkedHashMap<String, Object>();
        small.put("temp", 900.5);
        small.put("active", true);
        assertEquals("active yes, temp 900.5", PeripheralValues.format(small));
        var big = new LinkedHashMap<String, Object>();
        for (int i = 0; i < 10; i++) big.put("k" + i, i);
        assertEquals("10 entries", PeripheralValues.format(big));
        var nested = new LinkedHashMap<String, Object>();
        nested.put("inner", Map.of("a", 1, "b", 2));
        assertEquals("inner 2 entries", PeripheralValues.format(nested), "nested tables are not walked");
    }

    @Test
    void textIsCleanedAndCut() {
        String text = PeripheralValues.format("a\u0000b\nc§4d‮e " + "x".repeat(200));
        assertTrue(text.startsWith("ab cde x"), text);
        assertEquals(64, text.length());
        assertTrue(text.endsWith("…"));
        assertEquals("–", PeripheralValues.format("\u0000\u0001"));
        assertTrue(PeripheralValues.format("y".repeat(10_000)).length() <= 64);
    }

    @Test
    void booleansAndNumbers() {
        assertEquals(Boolean.TRUE, PeripheralValues.bool(new Object[]{true, 3}));
        assertNull(PeripheralValues.bool("true"));
        assertEquals(5.0, PeripheralValues.number(5L));
        assertNull(PeripheralValues.number(Double.POSITIVE_INFINITY));
        assertNull(PeripheralValues.number("5"));
    }

    private static PeripheralValues.Parsed parse(String json) {
        return PeripheralValues.parse(JsonParser.parseString(json).getAsJsonObject(), Set.of("getEnergy", "getEnergyCapacity", "isOn", "getName"),
            Set.of("getEnergy", "getEnergyCapacity"), Set.of("setOn"));
    }

    @Test
    void settingsAreChecked() {
        var ok = parse("{\"show\":[\"getEnergy\",\"isOn\"],\"charts\":[\"getEnergy\"],\"toggle\":\"setOn\",\"pin\":false}");
        assertNull(ok.error());
        assertEquals(List.of("getEnergy", "isOn"), ok.settings().show());
        assertEquals(Set.of("getEnergy"), ok.settings().charts());
        assertEquals("setOn", ok.settings().toggle());
        assertFalse(ok.settings().pin());
        assertTrue(parse("{\"show\":[]}").settings().pin(), "PIN is the default");
        assertEquals("unknown_method", parse("{\"show\":[\"pushItems\"]}").error());
        assertEquals("unknown_method", parse("{\"show\":[\"getName\"],\"charts\":[\"getName\"]}").error(), "charts only for numbers");
        assertEquals("unknown_method", parse("{\"show\":[\"getEnergy\"],\"charts\":[\"getEnergyCapacity\"]}").error(), "a chart needs the value");
        assertEquals("unknown_method", parse("{\"show\":[],\"toggle\":\"reset\"}").error());
        assertEquals("too_many", parse("{\"show\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\"]}").error());
        assertEquals("too_many", parse("{\"show\":[\"getEnergy\",\"getEnergyCapacity\",\"isOn\"],\"charts\":[\"getEnergy\",\"getEnergyCapacity\",\"isOn\"]}").error());
        assertEquals("invalid_settings", parse("{\"show\":[\"getEnergy\",\"getEnergy\"]}").error(), "no duplicates");
        assertEquals("invalid_settings", parse("{\"show\":\"getEnergy\"}").error());
        assertEquals("invalid_settings", parse("{\"show\":[1]}").error());
        assertEquals("invalid_settings", parse("{\"show\":[],\"call\":\"reset\"}").error());
        assertEquals("invalid_settings", parse("{\"show\":[],\"pin\":\"no\"}").error());
        assertEquals("invalid_settings", parse("{\"show\":[],\"toggle\":5}").error());
    }

    @Test
    void savedSettingsAreLenient() {
        var saved = PeripheralValues.load(JsonParser.parseString("{\"show\":[\"getEnergy\",\"gone\",\"getName\"],\"charts\":[\"getEnergy\",\"getName\"],\"toggle\":\"setGone\",\"pin\":false}").getAsJsonObject());
        var restricted = saved.restrictTo(Set.of("getEnergy", "getName"), Set.of("getEnergy"), Set.of("setOn"));
        assertEquals(List.of("getEnergy", "getName"), restricted.show());
        assertEquals(Set.of("getEnergy"), restricted.charts());
        assertNull(restricted.toggle());
        assertFalse(restricted.pin());
        assertEquals(PeripheralValues.Settings.EMPTY.show(), PeripheralValues.load(null).show());
        var round = PeripheralValues.load(restricted.toJson());
        assertEquals(restricted.show(), round.show());
        assertEquals(restricted.charts(), round.charts());
    }
}
