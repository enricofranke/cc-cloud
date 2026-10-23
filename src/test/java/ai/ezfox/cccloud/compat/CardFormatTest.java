package ai.ezfox.cccloud.compat;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.ActionValidator;
import ai.ezfox.cccloud.uplink.WidgetType;
import com.google.gson.JsonPrimitive;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Card number formatting and the widget builder (which validates exactly like Lua widgets). */
class CardFormatTest {
    @Test
    void formatsEnergy() {
        assertEquals("0", Fmt.si(0));
        assertEquals("999", Fmt.si(999));
        assertEquals("1.23 k", Fmt.si(1234));
        assertEquals("12.3 M", Fmt.si(12_345_678));
        assertEquals("123 G", Fmt.si(123_456_789_012L));
        assertEquals("-1.50 k", Fmt.si(-1500));
        assertEquals("1.00 M / 5.00 M FE", Fmt.energy(1_000_000, 5_000_000));
        assertEquals("+120 FE/t", Fmt.rate(120));
        assertEquals("-3.50 k FE/t", Fmt.rate(-3500));
        assertEquals("0 FE/t", Fmt.rate(0.01));
        assertEquals("200 FE/t", Fmt.flow(-200));
        assertEquals(25.0, Fmt.percent(1, 4));
        assertEquals(0.0, Fmt.percent(5, 0));
        assertEquals(100.0, Fmt.percent(9, 4));
        assertEquals("2 h 5 min", Fmt.duration(7500));
        assertEquals("3 d 1 h", Fmt.duration(3 * 86400 + 3600));
        assertEquals("12 s", Fmt.duration(12));
        assertEquals("Energy Cell Hardened", Fmt.title("energy_cell_hardened"));
    }

    @Test
    void buildsValidatedWidgets() {
        var widgets = new CardWidgets()
            .percent("stored", "Stored", "color", "green")
            .label("energy", "Energy", "span", 2)
            .history("history", "Stored (60 min)", 0.0, 100.0, "%")
            .select("redstone", "Redstone mode", List.of("Ignore redstone", "Active with signal"), "confirm", true)
            .toggle("field", "Force field", "pin", Boolean.TRUE, "confirm", true)
            .number("amount", "Keep in stock", 1, Integer.MAX_VALUE, 1)
            .build();
        assertEquals(6, widgets.size());
        var label = widgets.get(1);
        assertEquals(WidgetType.LABEL, label.type());
        assertEquals("Energy", label.label(), "card labels have a caption");
        assertEquals("–", label.value().getAsString());
        var chart = widgets.get(2);
        assertEquals(CardWidgets.HISTORY_POINTS, chart.chartPoints());
        assertEquals(240, chart.chartIntervalTicks(), "one point per 12 s = 60 minutes in 300 points");
        var select = widgets.get(3);
        assertTrue(ActionValidator.validate(select, new JsonPrimitive("Active with signal")).ok());
        assertFalse(ActionValidator.validate(select, new JsonPrimitive("Other")).ok());
        var toggle = widgets.get(4);
        assertTrue(ActionValidator.requiresPin(toggle), "MFFS-style PIN toggle");
        assertEquals("pin_required", ActionValidator.validate(toggle, new JsonPrimitive(true)).error());
        assertTrue(ActionValidator.validate(toggle, new JsonPrimitive(true), true).ok());
        assertEquals("invalid_value", ActionValidator.validate(widgets.get(5), new JsonPrimitive(0)).error(), "amounts start at 1");
    }

    @Test
    void invalidWidgetsFailLoudly() {
        assertThrows(IllegalStateException.class, () -> new CardWidgets().select("bad", "x", List.of()));
        assertThrows(IllegalStateException.class, () -> new CardWidgets().label("Bad-Id", "x"));
    }
}
