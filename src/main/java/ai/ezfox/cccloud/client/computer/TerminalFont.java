package ai.ezfox.cccloud.client.computer;

import ai.ezfox.cccloud.CCCloud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * CC: Cloud's own terminal font ({@code textures/gui/terminal_font.png}, drawn by tools/gen_textures.py): 256 cells of
 * {@value #CELL_W}x{@value #CELL_H} pixels in a 16x16 grid, white glyphs on transparent, tinted with the cell's colour.
 */
public final class TerminalFont {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(CCCloud.MODID, "textures/gui/terminal_font.png");
    public static final int CELL_W = 6;
    public static final int CELL_H = 9;
    public static final int TEX_W = 16 * CELL_W;
    public static final int TEX_H = 16 * CELL_H;

    private TerminalFont() {
    }

    /** Draws one character (CC's 8-bit charset; anything above 0xff shows as '?'). */
    public static void glyph(GuiGraphicsExtractor g, char c, int x, int y, int argb) {
        int code = c > 0xff ? '?' : c;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, (code % 16) * CELL_W, (code / 16) * CELL_H, CELL_W, CELL_H, TEX_W, TEX_H, argb);
    }
}
