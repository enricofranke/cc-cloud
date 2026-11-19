package ai.ezfox.cccloud.computer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The terminal grid: writing, clipping, colours, blit, scrolling, frames and the change counter. */
class ComputerTerminalTest {
    @Test
    void writesAtTheCursorAndClipsAtTheEdges() {
        var t = new ComputerTerminal(5, 2, true);
        t.setCursor(3, 0);
        t.write("abcdef");
        assertEquals("   ab", t.line(0));
        assertEquals(9, t.cursorX(), "the cursor moves past the edge like the text would");
        t.setCursor(-2, 1);
        t.write("xyz");
        assertEquals("z    ", t.line(1));
        t.setCursor(0, 7);
        t.write("off screen");
        assertEquals("   ab", t.line(0));
    }

    @Test
    void controlAndWideCharactersBecomeQuestionMarks() {
        var t = new ComputerTerminal(4, 1, true);
        t.write("a\n€ä");
        assertEquals("a??ä", t.line(0));
    }

    @Test
    void coloursAreStoredPerCell() {
        var t = new ComputerTerminal(3, 1, true);
        t.setTextColour(14);
        t.setBackgroundColour(11);
        t.write("ab");
        assertEquals("eef", t.foreground(0).substring(0, 2) + "f");
        assertEquals("bb", t.background(0).substring(0, 2));
        assertThrows(IllegalArgumentException.class, () -> t.setTextColour(16));
    }

    @Test
    void blitChecksItsArguments() {
        var t = new ComputerTerminal(4, 1, true);
        t.blit("ab", "01", "fe");
        assertEquals("01", t.foreground(0).substring(0, 2));
        assertEquals("fe", t.background(0).substring(0, 2));
        assertThrows(IllegalArgumentException.class, () -> t.blit("ab", "0", "ff"));
        assertThrows(IllegalArgumentException.class, () -> t.blit("ab", "0g", "ff"));
    }

    @Test
    void scrollMovesLinesAndBlanksNewOnes() {
        var t = new ComputerTerminal(2, 3, true);
        for (int y = 0; y < 3; y++) {
            t.setCursor(0, y);
            t.write("" + y + y);
        }
        t.scroll(1);
        assertEquals("11", t.line(0));
        assertEquals("22", t.line(1));
        assertEquals("  ", t.line(2));
        t.scroll(-2);
        assertEquals("  ", t.line(0));
        assertEquals("  ", t.line(1));
        assertEquals("11", t.line(2));
        t.scroll(100);
        assertEquals("  ", t.line(2));
    }

    @Test
    void frameIsACopyAndChangesAreCounted() {
        var t = new ComputerTerminal();
        long before = t.changes();
        t.write("hi");
        assertTrue(t.changes() > before);
        var frame = t.frame();
        assertEquals(51, frame.width());
        assertEquals(19, frame.height());
        assertTrue(frame.text()[0].startsWith("hi"));
        assertEquals(2, frame.cursorX());
        t.write("!");
        assertTrue(frame.text()[0].startsWith("hi "), "an earlier frame does not change");
        long same = t.changes();
        t.setCursor(t.cursorX(), t.cursorY());
        assertEquals(same, t.changes(), "no change, no new frame");
    }

    @Test
    void resetRestoresPaletteAndColours() {
        var t = new ComputerTerminal(3, 1, true);
        t.setPalette(0, 0x123456);
        t.setTextColour(5);
        t.write("x");
        t.reset();
        assertEquals(ComputerTerminal.defaultPalette(0), t.palette(0));
        assertEquals(ComputerTerminal.WHITE, t.textColour());
        assertEquals("   ", t.line(0));
        assertEquals(0, t.cursorX());
    }
}
