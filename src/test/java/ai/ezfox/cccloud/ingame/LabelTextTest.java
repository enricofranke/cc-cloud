package ai.ezfox.cccloud.ingame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LabelTextTest {
    @Test
    void splitsTickTimesWithTps() {
        var e = LabelText.split("overworld 163.0 ms · the_nether 3.1 ms · the_end 0.2 ms");
        assertEquals(3, e.size());
        assertEquals("overworld", e.get(0).name());
        assertEquals("163.0 ms", e.get(0).value());
        assertEquals("6.1", e.get(0).tps());
        assertEquals("20.0", e.get(2).tps());
    }

    @Test
    void colonsSemicolonsAndPlainEntries() {
        var e = LabelText.split("Fuel: 12 %; Water: 80 %\nidle");
        assertEquals(3, e.size());
        assertEquals("Fuel", e.get(0).name());
        assertEquals("12 %", e.get(0).value());
        assertNull(e.get(0).tps());
        assertEquals("idle", e.get(2).name());
        assertEquals("", e.get(2).value());
    }

    @Test
    void stackingRule() {
        assertTrue(LabelText.split("just one value").isEmpty());
        assertFalse(LabelText.stacked("Uptime", "1 min 2 s"));
        assertTrue(LabelText.stacked("Tick time", "overworld 1 ms · nether 2 ms"));
        assertTrue(LabelText.stacked("Note", "a value that is clearly longer than thirty-two chars"));
        assertFalse(LabelText.stacked("", "a value that is clearly longer than thirty-two chars"));
    }
}
