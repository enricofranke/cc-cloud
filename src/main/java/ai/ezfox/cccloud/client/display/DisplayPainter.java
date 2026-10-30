package ai.ezfox.cccloud.client.display;

import ai.ezfox.cccloud.display.DisplayLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import net.minecraft.client.resources.language.I18n;
import org.jspecify.annotations.Nullable;

/** Paints one section onto a display canvas, widget by widget in the boxes of {@link DisplayLayout}. */
final class DisplayPainter {
    static final int BG = 0xFF111111;
    static final int TEXT = 0xFFE6E6E6;
    static final int DIM = 0xFF8C8C8C;
    static final int GOLD = 0xFFE0B03A;
    static final int CYAN = 0xFF4FC3D7;
    static final int GREEN = 0xFF7FD67F;
    static final int YELLOW = 0xFFE8D25A;
    static final int RED = 0xFFE06666;
    static final int TRACK = 0xFF2A2A2A;

    private DisplayPainter() {
    }

    static void paint(GlyphCanvas g, @Nullable JsonObject section, int blocksW, int blocksH, boolean interactive) {
        int w = blocksW * DisplayLayout.PX_PER_BLOCK, h = blocksH * DisplayLayout.PX_PER_BLOCK, b = DisplayLayout.BORDER;
        g.fill(0, 0, w, h, 0); // transparent: the frame of the block model shows around the screen
        g.fill(b - 2, b - 2, w - 2 * b + 4, h - 2 * b + 4, BG);
        if (section == null) {
            g.text(I18n.get("cccloud.display.status.missing"), b, b, DIM);
            return;
        }
        if (section.has("error")) {
            String code = section.get("error").getAsString();
            g.text(GlyphCanvas.fit(I18n.get("cccloud.display.status." + code), w - 2 * b), b, b, YELLOW);
            return;
        }
        boolean loaded = !section.has("loaded") || section.get("loaded").getAsBoolean();
        boolean online = loaded && (!section.has("online") || section.get("online").getAsBoolean());
        String title = str(section, "title");
        g.fill(b, b + 3, 4, 4, online ? GREEN : RED);
        if (!loaded) {
            // Cached copy of an unloaded section: greyed title and when it was last seen.
            long seen = section.has("lastSeen") ? section.get("lastSeen").getAsLong() : 0;
            String note = I18n.get("cccloud.display.cached", seen > 0 ? (System.currentTimeMillis() - seen) / 60_000 + " min" : "?");
            int nw = GlyphCanvas.width(note);
            g.text(note, w - b - nw, b + 1, DIM);
            g.text(GlyphCanvas.fit(title.isEmpty() ? I18n.get("cccloud.tablet.untitled") : title, w - 2 * b - 12 - nw), b + 7, b + 1, DIM);
        } else {
            g.text(GlyphCanvas.fit(title.isEmpty() ? I18n.get("cccloud.tablet.untitled") : title, w - 2 * b - 8), b + 7, b + 1, GOLD);
        }
        var layout = DisplayLayout.layout(section, blocksW, blocksH);
        for (var box : layout.boxes()) {
            JsonObject widget = null;
            for (var e : section.getAsJsonArray("widgets")) if (str(e.getAsJsonObject(), "id").equals(box.id())) widget = e.getAsJsonObject();
            if (widget == null) continue;
            g.clip(box.x(), box.y(), box.w(), box.h());
            widget(g, box, widget, interactive && box.clickable());
            g.resetClip();
        }
        if (layout.hidden() > 0) g.text("+" + layout.hidden(), w - b - GlyphCanvas.width("+" + layout.hidden()), h - b - 8, DIM);
    }

    private static void widget(GlyphCanvas g, DisplayLayout.Box box, JsonObject widget, boolean clickable) {
        String label = str(widget, "label");
        var opts = widget.has("opts") && widget.get("opts").isJsonObject() ? widget.getAsJsonObject("opts") : new JsonObject();
        var value = widget.get("value");
        int x = box.x(), y = box.y(), w = box.w();
        switch (box.type()) {
            case "label" -> {
                if (!label.isEmpty()) g.text(GlyphCanvas.fit(label, w), x, y, DIM);
                g.text(GlyphCanvas.fit(text(value), w), x, y + 11, TEXT);
            }
            case "gauge", "progress" -> {
                double min = num(opts, "min", 0), max = num(opts, "max", 100), v = number(value);
                g.text(GlyphCanvas.fit(label, w), x, y, DIM);
                String t = value == null || value.isJsonNull() ? "-" : fmt(v) + str(opts, "unit");
                int tw = GlyphCanvas.width(t);
                int barW = Math.max(4, w - tw - 4);
                g.fill(x, y + 12, barW, 7, TRACK);
                double f = max > min ? Math.max(0, Math.min(1, (v - min) / (max - min))) : 0;
                int color = color(str(opts, "color"), CYAN);
                if (opts.has("crit") && v >= opts.get("crit").getAsDouble()) color = RED;
                else if (opts.has("warn") && v >= opts.get("warn").getAsDouble()) color = YELLOW;
                g.fill(x, y + 12, (int) (barW * f), 7, color);
                g.text(t, x + barW + 4, y + 11, TEXT);
            }
            case "indicator" -> {
                boolean on = bool(value);
                g.fill(x, y + 6, 8, 8, on ? color(str(opts, "onColor"), GREEN) : color(str(opts, "offColor"), 0xFF505050));
                g.text(GlyphCanvas.fit(label, w - 12), x + 12, y + 6, TEXT);
            }
            case "toggle" -> {
                boolean on = bool(value);
                g.fill(x, y + 5, 18, 10, clickable ? 0xFF3A3A3A : TRACK);
                g.fill(on ? x + 9 : x + 1, y + 6, 8, 8, on ? GREEN : 0xFF6A6A6A);
                g.text(GlyphCanvas.fit(label, w - 22), x + 22, y + 6, TEXT);
            }
            case "button" -> {
                int color = "red".equals(str(opts, "color")) ? 0xFF7A2E2E : clickable ? 0xFF3C5A60 : 0xFF333333;
                g.fill(x, y + 2, w, 18, color);
                g.text(GlyphCanvas.fit(label, w - 6), x + 3, y + 7, clickable ? TEXT : DIM);
            }
            case "chart" -> {
                var history = widget.has("history") && widget.get("history").isJsonArray() ? widget.getAsJsonArray("history") : new JsonArray();
                String last = history.isEmpty() ? "-" : fmt(history.get(history.size() - 1).getAsDouble()) + str(opts, "unit");
                g.text(GlyphCanvas.fit(label, w - GlyphCanvas.width(last) - 4), x, y, DIM);
                g.text(last, x + w - GlyphCanvas.width(last), y, TEXT);
                chart(g, history, opts, x, y + 11, w, box.h() - 12);
            }
            case "watchlist", "jobs" -> {
                g.text(GlyphCanvas.fit(label, w), x, y, DIM);
                var list = value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
                int ry = y + 11;
                for (int i = 0; i < Math.min(5, list.size()); i++) {
                    var row = list.get(i).getAsJsonObject();
                    String name = row.has("displayName") ? str(row, "displayName") : str(row, "name");
                    String right = box.type().equals("jobs") ? str(row, "status") : (long) number(row.get("count")) + "/" + (long) number(row.get("min"));
                    g.text(GlyphCanvas.fit(name, w - GlyphCanvas.width(right) - 6), x + 2, ry, TEXT);
                    g.text(right, x + w - GlyphCanvas.width(right), ry, DIM);
                    ry += 10;
                }
                if (list.isEmpty()) g.text("-", x + 2, ry, DIM);
            }
            case "sensor_log" -> {
                // the public form (displays never get player names): summary, then the newest events
                var o = value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
                if (!label.isEmpty()) g.text(GlyphCanvas.fit(label, w), x, y, DIM);
                g.text(GlyphCanvas.fit(str(o, "summary"), w), x, y + 11, TEXT);
            }
            case "redstone_sides" -> {
                g.text(GlyphCanvas.fit(label, w), x, y, DIM);
                var sides = value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
                int sx = x;
                for (var s : sides) {
                    var side = s.getAsJsonObject();
                    String name = str(side, "side").substring(0, 1).toUpperCase(Locale.ROOT);
                    boolean out = number(side.get("out")) > 0, in = number(side.get("in")) > 0;
                    g.fill(sx, y + 12, 6, 6, out ? RED : 0xFF4A2020);
                    g.fill(sx + 7, y + 12, 6, 6, in ? YELLOW : 0xFF403A20);
                    g.text(name, sx + 15, y + 11, TEXT);
                    sx += 26;
                }
            }
            default -> {
                // select, number, text, slider, buttons, item_search: show the current value (input stays in tablet/web)
                g.text(GlyphCanvas.fit(label, w), x, y, DIM);
                g.text(GlyphCanvas.fit(value == null || value.isJsonNull() ? "-" : text(value) + " " + str(opts, "unit"), w), x, y + 11, TEXT);
            }
        }
    }

    private static void chart(GlyphCanvas g, JsonArray history, JsonObject opts, int x, int y, int w, int h) {
        g.fill(x, y, w, h, 0xFF181818);
        if (history.isEmpty()) return;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (var p : history) {
            min = Math.min(min, p.getAsDouble());
            max = Math.max(max, p.getAsDouble());
        }
        if (opts.has("min")) min = opts.get("min").getAsDouble();
        if (opts.has("max")) max = opts.get("max").getAsDouble();
        if (max <= min) max = min + 1;
        int color = color(str(opts, "color"), CYAN);
        int n = history.size();
        for (int i = 0; i < w; i++) {
            int index = n == 1 ? 0 : (int) ((long) i * (n - 1) / Math.max(1, w - 1));
            double f = Math.max(0, Math.min(1, (history.get(index).getAsDouble() - min) / (max - min)));
            int bar = (int) Math.round(f * (h - 1));
            g.fill(x + i, y + h - 1 - bar, 1, bar + 1, (color & 0x00FFFFFF) | 0x50000000);
            g.fill(x + i, y + h - 1 - bar, 1, 1, color);
        }
    }

    static String str(JsonObject o, String key) {
        var e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    static double num(JsonObject o, String key, double fallback) {
        var e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : fallback;
    }

    static double number(@Nullable JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : 0;
    }

    static boolean bool(@Nullable JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    static String text(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) return "-";
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return fmt(e.getAsDouble());
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.1f", v);
    }

    static int color(String name, int fallback) {
        return switch (name) {
            case "green" -> GREEN;
            case "yellow" -> YELLOW;
            case "red" -> RED;
            case "cyan" -> CYAN;
            case "gray" -> DIM;
            default -> fallback;
        };
    }
}
