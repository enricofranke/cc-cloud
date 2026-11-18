package ai.ezfox.cccloud.computer.block;

import ai.ezfox.cccloud.terminal.TerminalFrame;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * The client's copy of a Cloud Computer terminal, kept up to date with the {@code term} messages of
 * {@link TerminalFrame#diff} (the same format the browser terminal gets). Every message is checked: a row that does not
 * fit the size, colours that are not blit digits or a delta without a full frame first are ignored, so a broken packet
 * can never break the screen. Pure Java, unit tested.
 */
public final class TerminalMirror {
    private int width;
    private int height;
    private boolean colour;
    private String[] text = new String[0];
    private String[] fg = new String[0];
    private String[] bg = new String[0];
    private int cursorX;
    private int cursorY;
    private boolean blink;
    private final int[] palette = new int[16];
    private boolean ready;

    /** Applies one {@code term} message. Returns false (and changes nothing) for a message that does not fit. */
    public boolean apply(@Nullable JsonObject msg) {
        if (msg == null || !"term".equals(str(msg.get("t")))) return false;
        boolean full = bool(msg.get("full"));
        int w = width, h = height;
        boolean c = colour;
        if (full) {
            Integer nw = integer(msg.get("w"), 1, TerminalFrame.MAX_W), nh = integer(msg.get("h"), 1, TerminalFrame.MAX_H);
            if (nw == null || nh == null) return false;
            w = nw;
            h = nh;
            c = bool(msg.get("colour"));
        } else if (!ready) {
            return false;
        }
        String[] nt = full ? blank(h, w, ' ') : text.clone();
        String[] nf = full ? blank(h, w, '0') : fg.clone();
        String[] nb = full ? blank(h, w, 'f') : bg.clone();
        if (msg.get("rows") instanceof JsonArray rows) {
            for (JsonElement e : rows) {
                if (!(e instanceof JsonObject row)) return false;
                Integer y = integer(row.get("y"), 0, h - 1);
                String t = str(row.get("text")), f = str(row.get("fg")), b = str(row.get("bg"));
                if (y == null || t == null || f == null || b == null || t.length() != w || f.length() != w || b.length() != w || !digits(f) || !digits(b)) {
                    return false;
                }
                nt[y] = TerminalFrame.clean(t);
                nf[y] = f;
                nb[y] = b;
            }
        } else if (msg.has("rows")) {
            return false;
        }
        int[] np = palette.clone();
        if (msg.get("palette") instanceof JsonArray colours) {
            if (colours.size() != 16) return false;
            for (int i = 0; i < 16; i++) {
                String s = str(colours.get(i));
                if (s == null || s.length() != 7 || s.charAt(0) != '#') return false;
                try {
                    np[i] = Integer.parseInt(s.substring(1), 16);
                } catch (NumberFormatException ex) {
                    return false;
                }
            }
        } else if (full) {
            return false;
        }
        int cx = cursorX, cy = cursorY;
        boolean cb = blink;
        if (msg.get("cursor") instanceof JsonObject cursor) {
            // The cursor may be outside the terminal (programs move it freely); it is only drawn when inside.
            Integer x = integer(cursor.get("x"), -1_000_000, 1_000_000), y = integer(cursor.get("y"), -1_000_000, 1_000_000);
            if (x == null || y == null) return false;
            cx = x;
            cy = y;
            cb = bool(cursor.get("blink"));
        }
        width = w;
        height = h;
        colour = c;
        text = nt;
        fg = nf;
        bg = nb;
        System.arraycopy(np, 0, palette, 0, 16);
        cursorX = cx;
        cursorY = cy;
        blink = cb;
        ready = true;
        return true;
    }

    /** The current picture as a frame (for tests and the screenshot), or null before the first full message. */
    public @Nullable TerminalFrame frame() {
        if (!ready) return null;
        return new TerminalFrame(width, height, colour, text.clone(), fg.clone(), bg.clone(), cursorX, cursorY, blink, palette.clone());
    }

    public boolean ready() {
        return ready;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean colour() {
        return colour;
    }

    public char charAt(int x, int y) {
        return text[y].charAt(x);
    }

    /** The text colour of a cell as 0xRRGGBB. */
    public int foreground(int x, int y) {
        return palette[Character.digit(fg[y].charAt(x), 16)];
    }

    /** The background colour of a cell as 0xRRGGBB. */
    public int background(int x, int y) {
        return palette[Character.digit(bg[y].charAt(x), 16)];
    }

    public int cursorX() {
        return cursorX;
    }

    public int cursorY() {
        return cursorY;
    }

    public boolean blink() {
        return blink;
    }

    /** A palette colour by blit digit index (0 = "0"). */
    public int palette(int index) {
        return palette[index];
    }

    private static String[] blank(int h, int w, char c) {
        var rows = new String[h];
        Arrays.fill(rows, String.valueOf(c).repeat(w));
        return rows;
    }

    private static boolean digits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) return false;
        }
        return true;
    }

    private static @Nullable String str(@Nullable JsonElement e) {
        return e instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    private static boolean bool(@Nullable JsonElement e) {
        return e instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
    }

    private static @Nullable Integer integer(@Nullable JsonElement e, int min, int max) {
        if (!(e instanceof JsonPrimitive p) || !p.isNumber()) return null;
        double d = p.getAsDouble();
        if (d != Math.rint(d) || d < min || d > max) return null;
        return (int) d;
    }
}
