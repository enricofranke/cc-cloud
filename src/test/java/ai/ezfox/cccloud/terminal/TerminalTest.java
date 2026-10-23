package ai.ezfox.cccloud.terminal;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Frame diffs (only changed rows, cursor, palette) and strict input validation. */
class TerminalTest {
    static TerminalFrame frame(String... rows) {
        int h = rows.length, w = rows[0].length();
        var fg = new String[h];
        var bg = new String[h];
        Arrays.fill(fg, "0".repeat(w));
        Arrays.fill(bg, "f".repeat(w));
        var palette = new int[16];
        for (int i = 0; i < 16; i++) palette[i] = i * 0x111111;
        return new TerminalFrame(w, h, true, rows, fg, bg, 1, 0, true, palette);
    }

    @Test
    void diffsOnlyWhatChanged() {
        var a = frame("hello", "     ");
        var full = TerminalFrame.diff(null, a, "u");
        assertTrue(full.get("full").getAsBoolean());
        assertEquals(5, full.get("w").getAsInt());
        assertEquals(2, full.getAsJsonArray("rows").size());
        assertEquals(16, full.getAsJsonArray("palette").size());
        assertEquals("#111111", full.getAsJsonArray("palette").get(1).getAsString());
        assertNull(TerminalFrame.diff(a, frame("hello", "     "), "u"), "nothing changed");
        var b = frame("hello", "> x  ");
        var delta = TerminalFrame.diff(a, b, "u");
        assertFalse(delta.get("full").getAsBoolean());
        assertEquals(1, delta.getAsJsonArray("rows").size());
        assertEquals(1, delta.getAsJsonArray("rows").get(0).getAsJsonObject().get("y").getAsInt());
        assertFalse(delta.has("palette"), "palette only when it changed");
        assertTrue(TerminalFrame.diff(a, frame("hi", "  "), "u").get("full").getAsBoolean(), "resize → full");
        assertEquals("a b", TerminalFrame.clean("a\u0007b"));
        assertThrows(IllegalArgumentException.class, () -> new TerminalFrame(0, 1, true, new String[1], new String[1], new String[1], 0, 0, false, new int[16]));
    }

    static TerminalInput.Parsed parse(String json, boolean advanced) {
        return TerminalInput.parse(JsonParser.parseString(json), advanced, 51, 19);
    }

    @Test
    void inputBecomesCcEvents() {
        var ok = parse("[{\"type\":\"char\",\"c\":\"a\"},{\"type\":\"key\",\"key\":257,\"held\":false},{\"type\":\"key_up\",\"key\":257},"
            + "{\"type\":\"paste\",\"text\":\"ls\\nrm -rf\"},{\"type\":\"terminate\"},{\"type\":\"mouse_click\",\"button\":1,\"x\":3,\"y\":4},"
            + "{\"type\":\"mouse_scroll\",\"dir\":-1,\"x\":1,\"y\":1}]", true);
        assertNull(ok.error());
        var events = ok.events();
        assertEquals("char", events.get(0).name());
        assertArrayEquals(new Object[]{257, false}, events.get(1).args());
        assertArrayEquals(new Object[]{"ls"}, events.get(3).args(), "paste stops at the line break");
        assertArrayEquals(new Object[]{1, 3, 4}, events.get(5).args());
        String[] bad = {
            "[]", "{}", "[{\"type\":\"char\",\"c\":\"ab\"}]", "[{\"type\":\"char\",\"c\":\"\\n\"}]", "[{\"type\":\"char\",\"c\":\"€\"}]",
            "[{\"type\":\"key\",\"key\":0}]", "[{\"type\":\"key\",\"key\":999}]", "[{\"type\":\"key\",\"key\":1.5}]",
            "[{\"type\":\"paste\",\"text\":\"" + "x".repeat(600) + "\"}]", "[{\"type\":\"exec\",\"cmd\":\"x\"}]",
            "[{\"type\":\"mouse_click\",\"button\":1,\"x\":52,\"y\":1}]", "[{\"type\":\"char\",\"c\":\"a\",\"extra\":1}]",
            "[" + "{\"type\":\"char\",\"c\":\"a\"},".repeat(32) + "{\"type\":\"char\",\"c\":\"a\"}]",
        };
        for (String b : bad) assertEquals("invalid_input", parse(b, true).error(), b);
        assertEquals("invalid_input", parse("[{\"type\":\"mouse_click\",\"button\":1,\"x\":1,\"y\":1}]", false).error(), "no mouse on basic computers");
    }
}
