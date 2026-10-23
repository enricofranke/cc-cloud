package ai.ezfox.cccloud.client.display;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Draws rectangles and text into a {@link NativeImage} on the CPU, so a display's content is a plain cached texture
 * (one quad per frame). Text uses the glyphs of Minecraft's own {@code font/ascii.png} (the resource pack's font);
 * characters outside ASCII get a close ASCII replacement or '?'.
 */
final class GlyphCanvas {
    private static final Identifier ASCII = Identifier.withDefaultNamespace("textures/font/ascii.png");
    private static @Nullable NativeImage font;
    private static int cell = 8;
    private static final int[] WIDTHS = new int[256];

    private final NativeImage image;
    private int clipX0, clipY0, clipX1, clipY1;

    GlyphCanvas(NativeImage image) {
        this.image = image;
        resetClip();
    }

    static void loadFont() {
        if (font != null) return;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(ASCII);
            if (resource.isEmpty()) return;
            try (var in = resource.get().open()) {
                font = NativeImage.read(in);
            }
            cell = font.getWidth() / 16;
            for (int c = 0; c < 256; c++) {
                int gx = (c % 16) * cell, gy = (c / 16) * cell, w = 0;
                for (int x = cell - 1; x >= 0 && w == 0; x--) {
                    for (int y = 0; y < cell; y++) {
                        if ((font.getPixel(gx + x, gy + y) >>> 24) != 0) {
                            w = x + 1;
                            break;
                        }
                    }
                }
                WIDTHS[c] = c == ' ' ? cell / 2 : w;
            }
        } catch (Exception e) {
            font = null;
        }
    }

    /** Clears the font (resource reload). */
    static void reset() {
        if (font != null) font.close();
        font = null;
    }

    void clip(int x, int y, int w, int h) {
        clipX0 = Math.max(0, x);
        clipY0 = Math.max(0, y);
        clipX1 = Math.min(image.getWidth(), x + w);
        clipY1 = Math.min(image.getHeight(), y + h);
    }

    void resetClip() {
        clip(0, 0, image.getWidth(), image.getHeight());
    }

    void fill(int x, int y, int w, int h, int argb) {
        int x0 = Math.max(clipX0, x), y0 = Math.max(clipY0, y), x1 = Math.min(clipX1, x + w), y1 = Math.min(clipY1, y + h);
        for (int py = y0; py < y1; py++) {
            for (int px = x0; px < x1; px++) image.setPixel(px, py, argb);
        }
    }

    static char ascii(char c) {
        if (c < 256 && c >= 32 && c != 127) return c;
        return switch (c) {
            case '–', '—' -> '-';
            case '·', '•' -> '.';
            case '…' -> '_';
            case '°' -> 'o';
            case '∞' -> '8';
            case '≥' -> '>';
            case '≤' -> '<';
            default -> '?';
        };
    }

    /** Width of a text in pixels (glyph width + 1 px spacing, 8 px font). */
    static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) w += WIDTHS[ascii(text.charAt(i))] + 1;
        return w;
    }

    /** Cuts a text to fit {@code maxW}, with ".." at the end if it was cut. */
    static String fit(String text, int maxW) {
        if (width(text) <= maxW) return text;
        var sb = new StringBuilder();
        int w = width("..");
        for (int i = 0; i < text.length(); i++) {
            int cw = WIDTHS[ascii(text.charAt(i))] + 1;
            if (w + cw > maxW) break;
            w += cw;
            sb.append(text.charAt(i));
        }
        return sb + "..";
    }

    /** Draws text with its top-left at (x, y); returns the x after the text. */
    int text(String text, int x, int y, int argb) {
        if (font == null) return x;
        for (int i = 0; i < text.length(); i++) {
            char c = ascii(text.charAt(i));
            int gx = (c % 16) * cell, gy = (c / 16) * cell, w = WIDTHS[c];
            for (int py = 0; py < 8; py++) {
                for (int px = 0; px < w; px++) {
                    int sx = gx + px * cell / 8, sy = gy + py * cell / 8;
                    if ((font.getPixel(sx, sy) >>> 24) < 128) continue;
                    int tx = x + px, ty = y + py;
                    if (tx >= clipX0 && tx < clipX1 && ty >= clipY0 && ty < clipY1) image.setPixel(tx, ty, argb);
                }
            }
            x += w + 1;
        }
        return x;
    }
}
