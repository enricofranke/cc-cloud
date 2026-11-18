package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.terminal.TerminalFrame;
import java.util.Arrays;

/**
 * The text screen of a Cloud Computer: a grid of characters with a text and a background colour per cell (16 colours,
 * blit digits {@code 0}–{@code f}: {@code 0} white … {@code f} black, colour value {@code 2^digit} in the {@code colors}
 * API), a cursor and a palette. Coordinates here are 0-based; the {@code term} API adds 1. Writes outside the grid are
 * clipped; the cursor may stand outside it. Thread-safe: programs write from a worker, the server thread reads
 * {@link #frame()} and {@link #changes()}.
 */
public final class ComputerTerminal {
    public static final int DEFAULT_WIDTH = 51;
    public static final int DEFAULT_HEIGHT = 19;
    public static final int WHITE = 0;
    public static final int BLACK = 15;
    private static final String DIGITS = "0123456789abcdef";
    /** Our default colours (0xRRGGBB) by blit digit. */
    private static final int[] DEFAULT_PALETTE = {
        0xF0F0F0, 0xF0A23C, 0xDC6ED4, 0x8CB4F5, 0xF2D84E, 0x7ED65A, 0xF0A0B8, 0x4A4A4A,
        0x9C9C9C, 0x4AA2B4, 0xA05ED8, 0x3E6ED0, 0x7E5A3A, 0x5A8A3A, 0xD04A44, 0x121212,
    };

    private final int width;
    private final int height;
    private final boolean colour;
    private final char[][] text;
    private final byte[][] fg;
    private final byte[][] bg;
    private final int[] palette = DEFAULT_PALETTE.clone();
    private int cursorX;
    private int cursorY;
    private boolean blink;
    private int textColour = WHITE;
    private int backgroundColour = BLACK;
    private long changes;

    public ComputerTerminal() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT, true);
    }

    public ComputerTerminal(int width, int height, boolean colour) {
        if (width < 1 || height < 1 || width > TerminalFrame.MAX_W || height > TerminalFrame.MAX_H) throw new IllegalArgumentException("bad size");
        this.width = width;
        this.height = height;
        this.colour = colour;
        this.text = new char[height][width];
        this.fg = new byte[height][width];
        this.bg = new byte[height][width];
        for (int y = 0; y < height; y++) blankLine(y);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Whether the screen shows colours (an advanced computer); without, programs may still set them. */
    public boolean colour() {
        return colour;
    }

    /** Grows by one with every visible change; the screen and browser send a new frame when it moved. */
    public synchronized long changes() {
        return changes;
    }

    /** Writes text at the cursor in the current colours and moves the cursor behind it (no line wrap). */
    public synchronized void write(String s) {
        int y = cursorY;
        if (y >= 0 && y < height) {
            for (int i = 0; i < s.length(); i++) {
                int x = cursorX + i;
                if (x < 0) continue;
                if (x >= width) break;
                text[y][x] = printable(s.charAt(i));
                fg[y][x] = (byte) textColour;
                bg[y][x] = (byte) backgroundColour;
            }
        }
        cursorX += s.length();
        changes++;
    }

    /**
     * Writes text with a colour per character ({@code fgDigits}, {@code bgDigits}: blit digits, same length as
     * {@code s}). Throws IllegalArgumentException for a length mismatch or a bad digit.
     */
    public synchronized void blit(String s, String fgDigits, String bgDigits) {
        if (fgDigits.length() != s.length() || bgDigits.length() != s.length()) throw new IllegalArgumentException("arguments must be the same length");
        byte[] f = digits(fgDigits), b = digits(bgDigits);
        int y = cursorY;
        if (y >= 0 && y < height) {
            for (int i = 0; i < s.length(); i++) {
                int x = cursorX + i;
                if (x < 0) continue;
                if (x >= width) break;
                text[y][x] = printable(s.charAt(i));
                fg[y][x] = f[i];
                bg[y][x] = b[i];
            }
        }
        cursorX += s.length();
        changes++;
    }

    public synchronized void clear() {
        for (int y = 0; y < height; y++) blankLine(y);
        changes++;
    }

    public synchronized void clearLine() {
        if (cursorY >= 0 && cursorY < height) blankLine(cursorY);
        changes++;
    }

    /** Moves the content up by {@code n} lines (down when negative); new lines are blank in the background colour. */
    public synchronized void scroll(int n) {
        if (n == 0) return;
        if (Math.abs(n) >= height) {
            for (int y = 0; y < height; y++) blankLine(y);
        } else if (n > 0) {
            for (int y = 0; y < height - n; y++) copyLine(y + n, y);
            for (int y = height - n; y < height; y++) blankLine(y);
        } else {
            int m = -n;
            for (int y = height - 1; y >= m; y--) copyLine(y - m, y);
            for (int y = 0; y < m; y++) blankLine(y);
        }
        changes++;
    }

    public synchronized void setCursor(int x, int y) {
        if (x == cursorX && y == cursorY) return;
        cursorX = x;
        cursorY = y;
        changes++;
    }

    public synchronized int cursorX() {
        return cursorX;
    }

    public synchronized int cursorY() {
        return cursorY;
    }

    public synchronized void setBlink(boolean on) {
        if (blink == on) return;
        blink = on;
        changes++;
    }

    public synchronized boolean blink() {
        return blink;
    }

    /** Colour index 0–15 (blit digit). */
    public synchronized void setTextColour(int index) {
        textColour = check(index);
    }

    public synchronized int textColour() {
        return textColour;
    }

    public synchronized void setBackgroundColour(int index) {
        backgroundColour = check(index);
    }

    public synchronized int backgroundColour() {
        return backgroundColour;
    }

    /** Sets a palette entry (0xRRGGBB). */
    public synchronized void setPalette(int index, int rgb) {
        check(index);
        if (palette[index] == (rgb & 0xFFFFFF)) return;
        palette[index] = rgb & 0xFFFFFF;
        changes++;
    }

    public synchronized int palette(int index) {
        return palette[check(index)];
    }

    /** Everything back to a blank screen: default colours and palette, cursor top left, no blink. */
    public synchronized void reset() {
        textColour = WHITE;
        backgroundColour = BLACK;
        System.arraycopy(DEFAULT_PALETTE, 0, palette, 0, 16);
        cursorX = 0;
        cursorY = 0;
        blink = false;
        for (int y = 0; y < height; y++) blankLine(y);
        changes++;
    }

    /** The text of one line (tests, screen readers). */
    public synchronized String line(int y) {
        return new String(text[y]);
    }

    /** The text colours of one line as blit digits. */
    public synchronized String foreground(int y) {
        return digitString(fg[y]);
    }

    public synchronized String background(int y) {
        return digitString(bg[y]);
    }

    /** A copy for the screen and the browser terminal. */
    public synchronized TerminalFrame frame() {
        var t = new String[height];
        var f = new String[height];
        var b = new String[height];
        for (int y = 0; y < height; y++) {
            t[y] = new String(text[y]);
            f[y] = digitString(fg[y]);
            b[y] = digitString(bg[y]);
        }
        return new TerminalFrame(width, height, colour, t, f, b, cursorX, cursorY, blink, palette.clone());
    }

    /** The blit digit for a colour index. */
    public static char digit(int index) {
        return DIGITS.charAt(check(index));
    }

    /** The colour index of a blit digit, or -1. */
    public static int index(char digit) {
        return DIGITS.indexOf(Character.toLowerCase(digit));
    }

    public static int defaultPalette(int index) {
        return DEFAULT_PALETTE[check(index)];
    }

    private void blankLine(int y) {
        Arrays.fill(text[y], ' ');
        Arrays.fill(fg[y], (byte) textColour);
        Arrays.fill(bg[y], (byte) backgroundColour);
    }

    private void copyLine(int from, int to) {
        System.arraycopy(text[from], 0, text[to], 0, width);
        System.arraycopy(fg[from], 0, fg[to], 0, width);
        System.arraycopy(bg[from], 0, bg[to], 0, width);
    }

    private static byte[] digits(String s) {
        var out = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            int v = index(s.charAt(i));
            if (v < 0) throw new IllegalArgumentException("invalid colour '" + s.charAt(i) + "'");
            out[i] = (byte) v;
        }
        return out;
    }

    private static String digitString(byte[] row) {
        var chars = new char[row.length];
        for (int i = 0; i < row.length; i++) chars[i] = DIGITS.charAt(row[i]);
        return new String(chars);
    }

    /** One terminal cell holds an 8-bit character; control and wider characters show as '?'. */
    private static char printable(char c) {
        return c < 0x20 || c == 0x7f || c > 0xff ? '?' : c;
    }

    private static int check(int index) {
        if (index < 0 || index > 15) throw new IllegalArgumentException("colour index out of range");
        return index;
    }
}
