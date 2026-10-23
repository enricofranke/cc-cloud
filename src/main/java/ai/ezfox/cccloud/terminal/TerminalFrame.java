package ai.ezfox.cccloud.terminal;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * One picture of a CC terminal (docs/SPEC.md v0.5 E "Terminal"): text and blit colours per row ({@code fg}/{@code bg}
 * in CC's hex digits {@code 0-f}), cursor and palette. {@link #diff} turns two frames into the {@code term} message
 * with only the changed rows. Pure Java, unit tested.
 *
 * @param palette 16 colours as 0xRRGGBB, indexed by blit digit ({@code palette[0]} = colour of "0", white by default)
 */
public record TerminalFrame(int width, int height, boolean colour, String[] text, String[] fg, String[] bg, int cursorX, int cursorY, boolean blink,
                            int[] palette) {
    public static final int MAX_W = 164;
    public static final int MAX_H = 81;

    public TerminalFrame {
        if (width < 1 || height < 1 || width > MAX_W || height > MAX_H || text.length != height || fg.length != height || bg.length != height || palette.length != 16) {
            throw new IllegalArgumentException("bad frame");
        }
    }

    /**
     * The {@code term} message for {@code next}: everything when {@code previous} is null or the size changed
     * ({@code "full":true}), else only changed rows, the cursor and (when changed) the palette. Null if nothing changed.
     */
    public static @Nullable JsonObject diff(@Nullable TerminalFrame previous, TerminalFrame next, String uplinkId) {
        boolean full = previous == null || previous.width != next.width || previous.height != next.height || previous.colour != next.colour;
        var rows = new JsonArray();
        for (int y = 0; y < next.height; y++) {
            if (!full && previous.text[y].equals(next.text[y]) && previous.fg[y].equals(next.fg[y]) && previous.bg[y].equals(next.bg[y])) continue;
            var row = new JsonObject();
            row.addProperty("y", y);
            row.addProperty("text", next.text[y]);
            row.addProperty("fg", next.fg[y]);
            row.addProperty("bg", next.bg[y]);
            rows.add(row);
        }
        boolean cursorChanged = full || previous.cursorX != next.cursorX || previous.cursorY != next.cursorY || previous.blink != next.blink;
        boolean paletteChanged = full || !Arrays.equals(previous.palette, next.palette);
        if (!full && rows.isEmpty() && !cursorChanged && !paletteChanged) return null;
        var json = new JsonObject();
        json.addProperty("t", "term");
        json.addProperty("uplinkId", uplinkId);
        json.addProperty("full", full);
        if (full) {
            json.addProperty("w", next.width);
            json.addProperty("h", next.height);
            json.addProperty("colour", next.colour);
        }
        var cursor = new JsonObject();
        cursor.addProperty("x", next.cursorX);
        cursor.addProperty("y", next.cursorY);
        cursor.addProperty("blink", next.blink);
        json.add("cursor", cursor);
        if (paletteChanged) {
            var palette = new JsonArray();
            for (int c : next.palette) palette.add(String.format("#%06x", c & 0xffffff));
            json.add("palette", palette);
        }
        json.add("rows", rows);
        return json;
    }

    /** Text safe for JSON/HTML clients: CC's 8-bit charset, control characters shown as spaces. */
    public static String clean(String line) {
        var out = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            out.append(c < 0x20 || c == 0x7f || c > 0xff ? ' ' : c);
        }
        return out.toString();
    }
}
