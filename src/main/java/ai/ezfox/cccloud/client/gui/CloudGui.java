package ai.ezfox.cccloud.client.gui;

import ai.ezfox.cccloud.CCCloud;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Drawing helpers for the CC-style screens, following docs/GUI_LAYOUT.md (256×256 atlases, 9-/3-slice regions).
 * If a texture is missing, everything is drawn with flat fills in the same palette.
 */
public final class CloudGui {
    public static final Identifier MAINFRAME = Identifier.fromNamespaceAndPath(CCCloud.MODID, "textures/gui/mainframe.png");
    public static final Identifier UPLINK = Identifier.fromNamespaceAndPath(CCCloud.MODID, "textures/gui/uplink.png");
    public static final Identifier WIDGETS = Identifier.fromNamespaceAndPath(CCCloud.MODID, "textures/gui/widgets.png");
    private static final int TEX = 256;

    // Palette (ARGB)
    public static final int TEXT = 0xFFE6E6E6;
    public static final int TEXT_DIM = 0xFF8C8C8C;
    public static final int TEXT_DARK = 0xFF202020;
    public static final int CYAN = 0xFF4FC3D7;
    public static final int GOLD = 0xFFE0B03A;
    public static final int GREEN = 0xFF7FD67F;
    public static final int YELLOW = 0xFFE8D25A;
    public static final int RED = 0xFFE06666;
    private static final int BEVEL_LIGHT = 0xFFE8E8E8;
    private static final int BEVEL_DARK = 0xFF5A5A5A;
    private static final int BODY = 0xFFC6C6C6;
    private static final int INSET = 0xFF111111;

    public enum ButtonStyle { NORMAL, DANGER, ACCENT }

    private static final Map<Identifier, Boolean> PRESENT = new HashMap<>();

    private CloudGui() {
    }

    /** Whether a texture exists (checked once per screen open, see {@link #refresh()}). */
    public static boolean has(Identifier texture) {
        return PRESENT.computeIfAbsent(texture, t -> Minecraft.getInstance().getResourceManager().getResource(t).isPresent());
    }

    public static void refresh() {
        PRESENT.clear();
    }

    // ---- slicing -----------------------------------------------------------------------------------------------------

    private static void region(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int h, int u, int v, int uw, int vh) {
        if (w <= 0 || h <= 0 || uw <= 0 || vh <= 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, uw, vh, TEX, TEX);
    }

    /** An 18×18 inventory slot (v0.7 upgrades): 0 = plain, 1 = empty card hint, 2 = Quantum Core slot. */
    public static void slot(GuiGraphicsExtractor g, Identifier tex, int x, int y, int kind) {
        if (has(tex)) {
            region(g, tex, x, y, 18, 18, kind * 18, 80, 18, 18);
            return;
        }
        g.fill(x, y, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x, y, x + 17, y + 17, 0xFF373737);
        g.fill(x + 1, y + 1, x + 17, y + 17, kind == 2 ? 0xFF7A6A30 : 0xFF8B8B8B);
    }

    /** 9-slice: corners 1:1, edges and center stretched. */
    public static void nine(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int h, int u, int v, int uw, int vh, int b) {
        int bw = Math.min(b, w / 2), bh = Math.min(b, h / 2);
        region(g, tex, x, y, bw, bh, u, v, b, b);
        region(g, tex, x + w - bw, y, bw, bh, u + uw - b, v, b, b);
        region(g, tex, x, y + h - bh, bw, bh, u, v + vh - b, b, b);
        region(g, tex, x + w - bw, y + h - bh, bw, bh, u + uw - b, v + vh - b, b, b);
        region(g, tex, x + bw, y, w - 2 * bw, bh, u + b, v, uw - 2 * b, b);
        region(g, tex, x + bw, y + h - bh, w - 2 * bw, bh, u + b, v + vh - b, uw - 2 * b, b);
        region(g, tex, x, y + bh, bw, h - 2 * bh, u, v + b, b, vh - 2 * b);
        region(g, tex, x + w - bw, y + bh, bw, h - 2 * bh, u + uw - b, v + b, b, vh - 2 * b);
        region(g, tex, x + bw, y + bh, w - 2 * bw, h - 2 * bh, u + b, v + b, uw - 2 * b, vh - 2 * b);
    }

    /** Horizontal 3-slice with a fixed height. */
    public static void three(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int h, int u, int v, int uw, int cap) {
        int c = Math.min(cap, w / 2);
        region(g, tex, x, y, c, h, u, v, cap, h);
        region(g, tex, x + c, y, w - 2 * c, h, u + cap, v, uw - 2 * cap, h);
        region(g, tex, x + w - c, y, c, h, u + uw - cap, v, cap, h);
    }

    /** Vertical 3-slice with a fixed width. */
    public static void threeV(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int h, int u, int v, int vh, int cap) {
        int c = Math.min(cap, h / 2);
        region(g, tex, x, y, w, c, u, v, w, cap);
        region(g, tex, x, y + c, w, h - 2 * c, u, v + cap, w, vh - 2 * cap);
        region(g, tex, x, y + h - c, w, c, u, v + vh - cap, w, cap);
    }

    // ---- parts -------------------------------------------------------------------------------------------------------

    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int body, int light, int dark) {
        g.fill(x, y, x + w, y + h, dark);
        g.fill(x, y, x + w - 1, y + h - 1, light);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
    }

    public static void panel(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int h, int accent) {
        if (has(tex)) {
            nine(g, tex, x, y, w, h, 0, 0, 48, 48, 8);
        } else {
            g.fill(x, y, x + w, y + h, 0xFF000000);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, accent);
            bevel(g, x + 3, y + 3, w - 6, h - 6, BODY, BEVEL_LIGHT, BEVEL_DARK);
        }
    }

    public static void titleBar(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int accent) {
        if (has(tex)) three(g, tex, x, y, w, 16, 48, 0, 48, 6);
        else bevel(g, x, y, w, 16, accent, 0xFFFFF3C4 & accent | 0xFF000000, 0xFF000000 | (accent & 0xFF7F7F7F));
    }

    public static void inset(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int h) {
        if (has(tex)) {
            nine(g, tex, x, y, w, h, 0, 48, 24, 24, 4);
        } else {
            g.fill(x, y, x + w, y + h, BEVEL_DARK);
            g.fill(x + 1, y + 1, x + w, y + h, BEVEL_LIGHT);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, INSET);
        }
    }

    public static void tab(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, boolean active, int accent) {
        if (has(tex)) {
            three(g, tex, x, y, w, 14, 48, active ? 30 : 16, 32, 5);
        } else {
            g.fill(x, y, x + w, y + 14, BEVEL_DARK);
            g.fill(x + 1, y + 1, x + w - 1, y + 14, active ? INSET : 0xFFA8A8A8);
            if (active) g.fill(x + 1, y, x + w - 1, y + 1, accent);
        }
    }

    /** 0 offline, 1 connecting, 2 online, 3 killed. */
    public static void led(GuiGraphicsExtractor g, Identifier tex, int x, int y, int state) {
        if (has(tex)) {
            region(g, tex, x, y, 8, 8, 96 + 8 * state, 0, 8, 8);
        } else {
            int color = switch (state) {
                case 2 -> 0xFF3FD13F;
                case 1 -> 0xFFE8C83A;
                case 3 -> 0xFF7A1010;
                default -> 0xFFD03A3A;
            };
            g.fill(x + 1, y + 1, x + 7, y + 7, color);
        }
    }

    public static void badge(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w) {
        if (has(tex)) three(g, tex, x, y, w, 14, 96, 8, 64, 5);
        else g.fill(x, y, x + w, y + 14, 0xFF1A1A1A);
    }

    public static void button(GuiGraphicsExtractor g, int x, int y, int w, ButtonStyle style, boolean hover, boolean enabled) {
        if (has(WIDGETS)) {
            int u = style == ButtonStyle.NORMAL ? 0 : 48;
            int v = switch (style) {
                case NORMAL -> !enabled ? 42 : hover ? 14 : 0;
                case DANGER -> hover && enabled ? 14 : 0;
                case ACCENT -> hover && enabled ? 42 : 28;
            };
            three(g, WIDGETS, x, y, w, 14, u, v, 48, 4);
            if (!enabled && style != ButtonStyle.NORMAL) g.fill(x, y, x + w, y + 14, 0x88000000);
        } else {
            int body = switch (style) {
                case NORMAL -> enabled ? (hover ? 0xFFD8D8D8 : 0xFFC6C6C6) : 0xFF8A8A8A;
                case DANGER -> hover ? 0xFFE56D69 : 0xFFD9534F;
                case ACCENT -> hover ? 0xFF6BC56B : 0xFF4CAF50;
            };
            bevel(g, x, y, w, 14, body, 0xFFF0F0F0, 0xFF404040);
        }
    }

    public static int buttonText(ButtonStyle style, boolean enabled) {
        if (!enabled) return 0xFF505050;
        return style == ButtonStyle.NORMAL ? TEXT_DARK : 0xFFFFFFFF;
    }

    public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        if (has(WIDGETS)) {
            region(g, WIDGETS, x, y, 24, 12, 96, on ? 12 : 0, 24, 12);
        } else {
            g.fill(x, y + 2, x + 24, y + 10, on ? CYAN : 0xFF333333);
            int kx = on ? x + 13 : x + 1;
            g.fill(kx, y + 1, kx + 10, y + 11, 0xFFC6C6C6);
        }
    }

    public static void textField(GuiGraphicsExtractor g, int x, int y, int w, boolean focused) {
        if (has(WIDGETS)) {
            three(g, WIDGETS, x, y, w, 14, 0, focused ? 70 : 56, 48, 3);
        } else {
            g.fill(x, y, x + w, y + 14, focused ? CYAN : 0xFF404040);
            g.fill(x + 1, y + 1, x + w - 1, y + 13, 0xFF000000);
        }
    }

    public static void row(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover) {
        if (has(WIDGETS)) {
            // The row region is 18 px tall; taller rows stretch its middle.
            if (h == 18) three(g, WIDGETS, x, y, w, 18, 48, hover ? 74 : 56, 48, 3);
            else nine(g, WIDGETS, x, y, w, h, 48, hover ? 74 : 56, 48, 18, 3);
        } else {
            g.fill(x, y, x + w, y + h, hover ? 0xFF242424 : 0xFF1A1A1A);
        }
    }

    public static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int thumbY, int thumbH) {
        if (has(WIDGETS)) {
            threeV(g, WIDGETS, x, y, 6, h, 96, 24, 32, 3);
            threeV(g, WIDGETS, x, thumbY, 6, thumbH, 102, 24, 16, 3);
        } else {
            g.fill(x, y, x + 6, y + h, 0xFF222222);
            g.fill(x + 1, thumbY, x + 5, thumbY + thumbH, CYAN);
        }
    }

    /** 0 = copy, 1 = warning; 9×9. */
    public static void icon(GuiGraphicsExtractor g, int x, int y, int which) {
        if (has(WIDGETS)) {
            region(g, WIDGETS, x, y, 9, 9, which == 0 ? 112 : 121, 24, 9, 9);
        } else if (which == 0) {
            g.outline(x + 2, y, 6, 6, 0xFF202020);
            g.outline(x, y + 3, 6, 6, 0xFF202020);
        } else {
            g.fill(x + 4, y + 1, x + 5, y + 6, YELLOW);
            g.fill(x + 4, y + 7, x + 5, y + 8, YELLOW);
        }
    }
}
