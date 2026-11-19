package ai.ezfox.cccloud.computer.block;

import ai.ezfox.cccloud.terminal.TerminalFrame;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;

/**
 * Draws a terminal picture the way the in-game screen does (CC: Cloud's terminal font, cell backgrounds, tinted glyphs,
 * grey shades on the basic computer), into an image. Used for the screen's evidence PNG in the GameTest; the screen
 * itself draws through Minecraft's GUI renderer with the same font texture and {@link #shade}. Pure Java.
 */
public final class TerminalImage {
    /** The font texture in the mod's resources (drawn by tools/gen_textures.py). */
    public static final String FONT = "/assets/cccloud/textures/gui/terminal_font.png";
    public static final int CELL_W = 6;
    public static final int CELL_H = 9;

    private TerminalImage() {
    }

    /** The basic computer shows grey shades (its programs still name colours). */
    public static int shade(int rgb, boolean colour) {
        if (colour) return rgb & 0xFFFFFF;
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        int v = (r * 299 + g * 587 + b * 114) / 1000;
        return v << 16 | v << 8 | v;
    }

    public static BufferedImage font() throws IOException {
        try (InputStream in = TerminalImage.class.getResourceAsStream(FONT)) {
            if (in == null) throw new IOException("font texture missing: " + FONT);
            return ImageIO.read(in);
        }
    }

    /**
     * The frame at {@code scale} pixels per font pixel, with the cursor drawn when it blinks (always shown here).
     * @param colour false: grey shades (basic computer)
     */
    public static BufferedImage render(TerminalFrame frame, BufferedImage font, boolean colour, int scale) {
        int w = frame.width() * CELL_W, h = frame.height() * CELL_H;
        var image = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++) {
                int bg = shade(frame.palette()[Character.digit(frame.bg()[y].charAt(x), 16)], colour);
                int fg = shade(frame.palette()[Character.digit(frame.fg()[y].charAt(x), 16)], colour);
                char c = frame.text()[y].charAt(x);
                boolean cursor = frame.blink() && x == frame.cursorX() && y == frame.cursorY();
                cell(image, font, x, y, c, cursor, fg, bg, scale);
            }
        }
        return image;
    }

    private static void cell(BufferedImage image, BufferedImage font, int cx, int cy, char c, boolean cursor, int fg, int bg, int scale) {
        int code = c > 0xff ? '?' : c;
        int fx = (code % 16) * CELL_W, fy = (code / 16) * CELL_H;
        int ux = '_' % 16 * CELL_W, uy = '_' / 16 * CELL_H;
        for (int py = 0; py < CELL_H; py++) {
            for (int px = 0; px < CELL_W; px++) {
                boolean lit = (font.getRGB(fx + px, fy + py) >>> 24) > 0x80;
                if (cursor) lit |= (font.getRGB(ux + px, uy + py) >>> 24) > 0x80;
                int rgb = lit ? fg : bg;
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) image.setRGB((cx * CELL_W + px) * scale + sx, (cy * CELL_H + py) * scale + sy, rgb);
                }
            }
        }
    }

    /** How many different colours the picture uses (the test checks that colours arrived). */
    public static int colours(BufferedImage image) {
        var seen = new java.util.HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y += 3) for (int x = 0; x < image.getWidth(); x += 3) seen.add(image.getRGB(x, y) & 0xFFFFFF);
        return seen.size();
    }

    /** All text on the picture, rows joined with newlines (for log lines and asserts). */
    public static String text(@Nullable TerminalFrame frame) {
        return frame == null ? "" : String.join("\n", frame.text());
    }
}
