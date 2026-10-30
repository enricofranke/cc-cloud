package ai.ezfox.cccloud.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Draws the live card preview of the uplink and bridge screens ({@code ai.ezfox.cccloud.gui.CardPreview}): the card's
 * title bar and one compact, read-only row per widget (label left, a small pixel version of the widget right), in the
 * CC terminal look. Nothing here is clickable.
 */
final class CardPainter {
    static final int TITLE_H = 13;
    static final int ROW_H = 11;
    private static final int CARD_BG = 0xFF181818;
    private static final int TRACK = 0xFF333333;
    private static final int FIELD = 0xFF050505;

    private CardPainter() {
    }

    static int height(JsonObject preview) {
        int rows = preview.get("widgets") instanceof JsonArray a ? a.size() : 0;
        return TITLE_H + Math.max(1, rows) * ROW_H + (preview.has("more") ? ROW_H : 0) + 3;
    }

    static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, String title, int accent, JsonObject preview) {
        int h = height(preview);
        g.fill(x, y, x + w, y + h, accent);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, CARD_BG);
        g.fill(x + 1, y + 1, x + w - 1, y + TITLE_H - 1, 0xFF222222);
        g.fill(x + 1, y + TITLE_H - 1, x + w - 1, y + TITLE_H, dim(accent));
        g.text(font, fit(font, title.isEmpty() ? "-" : title, w - 10), x + 5, y + 3, accent, false);
        var widgets = preview.get("widgets") instanceof JsonArray a ? a : new JsonArray();
        int ry = y + TITLE_H + 1;
        if (widgets.isEmpty()) {
            boolean blocked = preview.has("blocked");
            g.text(font, fit(font, Component.translatable(blocked ? "cccloud.gui.state.claim_blocked" : "cccloud.gui.card.empty").getString(), w - 10), x + 5, ry + 2,
                blocked ? CloudGui.RED : CloudGui.TEXT_DIM, false);
        }
        for (var e : widgets) {
            if (e instanceof JsonObject widget) row(g, font, x + 4, ry, w - 8, widget);
            ry += ROW_H;
        }
        if (preview.has("more")) {
            g.text(font, Component.translatable("cccloud.gui.card.more", preview.get("more").getAsInt()), x + 5, ry + 2, CloudGui.TEXT_DIM, false);
        }
    }

    private static void row(GuiGraphicsExtractor g, Font font, int x, int y, int w, JsonObject p) {
        String type = str(p, "t");
        String label = str(p, "l");
        int labelW = Math.min(font.width(label), (int) (w * 0.48));
        int vx = x + labelW + 6, right = x + w;
        int ty = y + 2;
        switch (type) {
            case "button" -> {
                // A button fills the row like on the dashboard.
                button(g, font, x, y, w, label, buttonColor(str(p, "color")));
                return;
            }
            case "buttons" -> {
                g.text(font, fit(font, label, labelW), x, ty, CloudGui.TEXT_DIM, false);
                int bx = right;
                var options = p.get("o") instanceof JsonArray o ? o : new JsonArray();
                for (int i = options.size() - 1; i >= 0; i--) {
                    String option = options.get(i).getAsString();
                    int bw = Math.min(font.width(option) + 6, 50);
                    if (bx - bw < vx) break;
                    button(g, font, bx - bw, y, bw, option, 0xFF5A5A5A);
                    bx -= bw + 2;
                }
                return;
            }
            case "label" -> {
                if (label.isEmpty()) {
                    // just the text, across the row
                    g.text(font, fit(font, str(p, "s"), w), x, ty, CloudGui.TEXT, false);
                    return;
                }
                g.text(font, fit(font, label, labelW), x, ty, CloudGui.TEXT_DIM, false);
            }
            default -> g.text(font, fit(font, label, labelW), x, ty, CloudGui.TEXT_DIM, false);
        }
        int area = right - vx;
        switch (type) {
            case "gauge", "slider", "progress" -> {
                double n = num(p, "n", 0), min = num(p, "min", 0), max = num(p, "max", type.equals("progress") ? 1 : 100);
                String text = number(n) + (p.has("u") ? " " + str(p, "u") : "");
                int textW = Math.min(font.width(text), area / 2);
                int bw = Math.max(0, Math.min(70, area - textW - 4));
                int color = CloudGui.CYAN;
                if (p.has("crit") && n >= num(p, "crit", 0)) color = CloudGui.RED;
                else if (p.has("warn") && n >= num(p, "warn", 0)) color = CloudGui.YELLOW;
                if (bw > 6) {
                    int bx = right - textW - 4 - bw;
                    g.fill(bx, y + 4, bx + bw, y + 7, TRACK);
                    double f = max > min ? Math.max(0, Math.min(1, (n - min) / (max - min))) : 0;
                    g.fill(bx, y + 4, bx + (int) Math.round(bw * f), y + 7, color);
                    if (type.equals("slider")) {
                        int kx = bx + (int) Math.round((bw - 2) * f);
                        g.fill(kx, y + 2, kx + 2, y + 9, 0xFFC6C6C6);
                    }
                }
                rightText(g, font, text, right, ty, CloudGui.TEXT, textW);
            }
            case "number" -> field(g, font, vx, right, y, number(num(p, "n", 0)) + (p.has("u") ? " " + str(p, "u") : ""));
            case "text" -> field(g, font, vx, right, y, str(p, "s"));
            case "select" -> {
                String text = str(p, "s");
                int fw = Math.min(area, font.width(text) + 14);
                g.fill(right - fw, y, right, y + 10, 0xFF4A4A4A);
                g.fill(right - fw + 1, y + 1, right - 1, y + 9, FIELD);
                g.text(font, fit(font, text, fw - 12), right - fw + 3, ty, CloudGui.TEXT, false);
                // a small down arrow
                int ax = right - 7;
                g.fill(ax - 2, y + 4, ax + 3, y + 5, CloudGui.TEXT_DIM);
                g.fill(ax - 1, y + 5, ax + 2, y + 6, CloudGui.TEXT_DIM);
                g.fill(ax, y + 6, ax + 1, y + 7, CloudGui.TEXT_DIM);
            }
            case "toggle" -> {
                boolean on = p.has("b") && p.get("b").getAsBoolean();
                int tx = right - 16;
                g.fill(tx, y + 2, tx + 16, y + 9, on ? CloudGui.CYAN : TRACK);
                int kx = on ? tx + 9 : tx + 1;
                g.fill(kx, y + 1, kx + 6, y + 10, 0xFFC6C6C6);
            }
            case "indicator" -> {
                boolean on = p.has("b") && p.get("b").getAsBoolean();
                String text = Component.translatable(on ? "cccloud.gui.card.on" : "cccloud.gui.card.off").getString();
                int textW = font.width(text);
                int lx = right - textW - 9;
                g.fill(lx, y + 2, lx + 6, y + 8, 0xFF000000);
                g.fill(lx + 1, y + 3, lx + 5, y + 7, on ? lamp(str(p, "color")) : 0xFF4A4A4A);
                g.text(font, text, right - textW, ty, on ? CloudGui.TEXT : CloudGui.TEXT_DIM, false);
            }
            case "chart" -> {
                var h = p.get("h") instanceof JsonArray a ? a : new JsonArray();
                String text = h.isEmpty() ? "-" : number(h.get(h.size() - 1).getAsDouble()) + (p.has("u") ? " " + str(p, "u") : "");
                int textW = Math.min(font.width(text), area / 2);
                int sw = Math.max(0, area - textW - 4);
                sparkline(g, right - textW - 4 - sw, y + 1, sw, 8, h);
                rightText(g, font, text, right, ty, CloudGui.TEXT, textW);
            }
            case "item_search" -> field(g, font, vx, right, y, Component.translatable("cccloud.gui.card.search").getString());
            case "watchlist", "jobs" -> rightText(g, font, Component.translatable("cccloud.gui.card.entries", p.has("c") ? p.get("c").getAsInt() : 0).getString(),
                right, ty, CloudGui.TEXT, area);
            case "redstone_sides" -> {
                var sides = p.get("rs") instanceof JsonArray a ? a : new JsonArray();
                int sx = right - sides.size() * 8;
                for (var side : sides) {
                    var pair = side.getAsJsonArray();
                    boolean out = pair.get(0).getAsInt() > 0, in = pair.get(1).getAsInt() > 0;
                    g.fill(sx, y + 1, sx + 7, y + 8, in ? 0xFFE06666 : 0xFF3A3A3A);
                    g.fill(sx + 1, y + 2, sx + 6, y + 7, out ? 0xFFFF4040 : 0xFF1E1E1E);
                    sx += 8;
                }
            }
            default -> rightText(g, font, str(p, "s"), right, ty, CloudGui.TEXT, area);
        }
    }

    private static void field(GuiGraphicsExtractor g, Font font, int vx, int right, int y, String text) {
        int fw = Math.min(right - vx, Math.max(30, font.width(text) + 6));
        g.fill(right - fw, y, right, y + 10, 0xFF4A4A4A);
        g.fill(right - fw + 1, y + 1, right - 1, y + 9, FIELD);
        g.text(font, fit(font, text, fw - 5), right - fw + 3, y + 2, CloudGui.TEXT, false);
    }

    private static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, int body) {
        g.fill(x, y, x + w, y + 10, 0xFF000000);
        g.fill(x, y, x + w - 1, y + 9, lighten(body));
        g.fill(x + 1, y + 1, x + w - 1, y + 9, body);
        g.centeredText(font, fit(font, label, w - 4), x + w / 2, y + 1, 0xFFFFFFFF);
    }

    private static void sparkline(GuiGraphicsExtractor g, int x, int y, int w, int h, JsonArray points) {
        if (w < 8 || points.size() < 2) {
            if (w >= 8) g.fill(x, y + h - 1, x + w, y + h, TRACK);
            return;
        }
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (var e : points) {
            min = Math.min(min, e.getAsDouble());
            max = Math.max(max, e.getAsDouble());
        }
        if (max - min < 1e-9) max = min + 1;
        int n = points.size();
        int prevY = -1;
        for (int px = 0; px < w; px++) {
            double v = points.get((int) Math.round((double) px * (n - 1) / Math.max(1, w - 1))).getAsDouble();
            int py = y + h - 1 - (int) Math.round((v - min) / (max - min) * (h - 1));
            int top = prevY < 0 ? py : Math.min(py, prevY), bottom = prevY < 0 ? py : Math.max(py, prevY);
            g.fill(x + px, top, x + px + 1, bottom + 1, CloudGui.CYAN);
            prevY = py;
        }
    }

    private static void rightText(GuiGraphicsExtractor g, Font font, String text, int right, int y, int color, int maxW) {
        String t = fit(font, text, maxW);
        g.text(font, t, right - font.width(t), y, color, false);
    }

    static String number(double v) {
        double a = Math.abs(v);
        if (a >= 1e9) return String.format(Locale.ROOT, "%.1fG", v / 1e9);
        if (a >= 1e6) return String.format(Locale.ROOT, "%.1fM", v / 1e6);
        if (a >= 1e4) return String.format(Locale.ROOT, "%.1fk", v / 1e3);
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, a >= 100 ? "%.0f" : "%.1f", v);
    }

    private static int buttonColor(String color) {
        return switch (color) {
            case "red" -> 0xFFB03A36;
            case "green" -> 0xFF3E8E41;
            case "yellow" -> 0xFFA88A1E;
            case "cyan" -> 0xFF2C8A9A;
            default -> 0xFF5A5A5A;
        };
    }

    private static int lamp(String color) {
        return switch (color) {
            case "red" -> CloudGui.RED;
            case "yellow", "orange" -> CloudGui.YELLOW;
            case "cyan" -> CloudGui.CYAN;
            default -> CloudGui.GREEN;
        };
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 40), gr = Math.min(255, ((c >> 8) & 0xFF) + 40), b = Math.min(255, (c & 0xFF) + 40);
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    private static int dim(int c) {
        return 0xFF000000 | (c & 0xFEFEFE) >> 1;
    }

    private static double num(JsonObject o, String key, double fallback) {
        var e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? fallback : e.getAsDouble();
    }

    private static String str(JsonObject o, String key) {
        var e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? "" : e.getAsString();
    }

    static String fit(Font font, String s, int maxW) {
        if (maxW <= 0) return "";
        if (font.width(s) <= maxW) return s;
        return font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("…"))) + "…";
    }
}
