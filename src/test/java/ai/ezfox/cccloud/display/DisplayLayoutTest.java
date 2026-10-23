package ai.ezfox.cccloud.display;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class DisplayLayoutTest {
    private static final String SECTION = """
        {"uplinkId":"u","title":"Reactor","widgets":[
          {"id":"temp","type":"gauge","label":"Temp","opts":{"min":0,"max":100}},
          {"id":"lamp","type":"toggle","label":"Lamp","opts":{}},
          {"id":"scram","type":"button","label":"SCRAM","opts":{"confirm":true}},
          {"id":"secret","type":"toggle","label":"Secret","opts":{"pin":true}},
          {"id":"hist","type":"chart","label":"History","opts":{"points":60}},
          {"id":"go","type":"button","label":"Go","opts":{}}
        ]}""";

    @Test
    void boxesStayInsideAndDoNotOverlap() {
        var section = JsonParser.parseString(SECTION).getAsJsonObject();
        var layout = DisplayLayout.layout(section, 4, 3);
        assertEquals(256, layout.width());
        assertEquals(192, layout.height());
        assertEquals(6, layout.boxes().size() + layout.hidden());
        for (var a : layout.boxes()) {
            assertTrue(a.x() >= DisplayLayout.BORDER && a.y() >= DisplayLayout.BORDER, a.toString());
            assertTrue(a.x() + a.w() <= layout.width() - DisplayLayout.BORDER && a.y() + a.h() <= layout.height() - DisplayLayout.BORDER, a.toString());
            for (var b : layout.boxes()) {
                if (a == b) continue;
                boolean overlap = a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
                assertFalse(overlap, a + " overlaps " + b);
            }
        }
        var chart = layout.boxes().stream().filter(b -> b.id().equals("hist")).findFirst().orElseThrow();
        assertEquals(layout.width() - 2 * DisplayLayout.BORDER, chart.w(), "charts take the full width");
    }

    @Test
    void onlyPlainButtonsAndTogglesAreClickable() {
        var section = JsonParser.parseString(SECTION).getAsJsonObject();
        var layout = DisplayLayout.layout(section, 4, 3);
        for (var box : layout.boxes()) {
            boolean expected = box.id().equals("lamp") || box.id().equals("go");
            assertEquals(expected, box.clickable(), box.id());
        }
        var lamp = layout.boxes().stream().filter(b -> b.id().equals("lamp")).findFirst().orElseThrow();
        assertEquals("lamp", layout.hit(lamp.x() + 1, lamp.y() + 1).id());
        assertNull(layout.hit(1, 1), "the border is not a widget");
    }

    @Test
    void smallDisplaysHideWhatDoesNotFit() {
        var section = JsonParser.parseString(SECTION).getAsJsonObject();
        var layout = DisplayLayout.layout(section, 1, 1);
        assertTrue(layout.hidden() > 0);
        assertTrue(layout.boxes().size() >= 1);
    }

    @Test
    void clicksMapToTexturePixelsLikeCcMonitors() {
        // Front facing north, upright: the viewer looks south; x from the viewer's left (east), y from the top.
        double[] topLeft = DisplayLayout.toPixels(0.99f, 0.99f, 0f, Direction.NORTH, Direction.NORTH, 0, 1, 2);
        assertEquals(0.01 * 64, topLeft[0], 0.01);
        assertEquals(0.01 * 64, topLeft[1], 0.01);
        // Bottom row (yIndex 0) of a 2-high display, second column, bottom-right corner of that block.
        double[] bottomRight = DisplayLayout.toPixels(0.01f, 0.01f, 0f, Direction.NORTH, Direction.NORTH, 1, 0, 2);
        assertEquals(1.99 * 64, bottomRight[0], 0.01);
        assertEquals(1.99 * 64, bottomRight[1], 0.01);
        double[] south = DisplayLayout.toPixels(0.25f, 0.5f, 1f, Direction.SOUTH, Direction.NORTH, 0, 0, 1);
        assertEquals(16, south[0], 0.01);
        assertEquals(32, south[1], 0.01);
    }
}
