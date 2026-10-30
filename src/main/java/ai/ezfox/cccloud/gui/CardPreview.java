package ai.ezfox.cccloud.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/**
 * The live card preview of the uplink and bridge screens: a section snapshot's widgets in a small, bounded, read-only
 * form, so the screen can draw the card the way it appears on the dashboard. Widgets the layout hides are left out and
 * renamed widgets carry their dashboard label. Pure Java (unit tested).
 * <p>
 * Each entry: {@code t} type, {@code l} label, then by type {@code n} number with {@code min}/{@code max}/{@code u}
 * (gauge, slider, number, progress), {@code s} text (label, text, select), {@code b} on/off (toggle, indicator),
 * {@code h} the last chart points, {@code c} rows (item_search, watchlist, jobs), {@code rs} redstone sides as
 * {@code [out, in]} pairs, {@code o} options (buttons) and {@code color}.
 */
public final class CardPreview {
    /** At most this many widgets (a card rarely has more; the rest is counted in {@code more}). */
    public static final int MAX_WIDGETS = 24;
    static final int MAX_TEXT = 40;
    static final int MAX_LABEL = 32;
    static final int HISTORY = 32;
    static final int MAX_OPTIONS = 4;

    private CardPreview() {
    }

    /**
     * @param snapshot the section snapshot ({@code {"widgets":[...]}}) as sent to browsers
     * @param layout   the mainframe's dashboard layout, or null (never edited)
     * @param section  the section id (widget keys in the layout are {@code <section>/<widget>})
     */
    public static JsonObject of(JsonObject snapshot, @Nullable JsonObject layout, String section) {
        var out = new JsonObject();
        var list = new JsonArray();
        int more = 0;
        var widgets = snapshot.get("widgets");
        JsonObject layoutWidgets = layout != null && layout.get("widgets") instanceof JsonObject w ? w : null;
        if (widgets instanceof JsonArray array) {
            for (var e : array) {
                if (!(e instanceof JsonObject w)) continue;
                String id = str(w, "id");
                JsonObject patch = layoutWidgets != null && layoutWidgets.get(section + "/" + id) instanceof JsonObject p ? p : null;
                if (patch != null && patch.get("hidden") != null && patch.get("hidden").isJsonPrimitive() && patch.get("hidden").getAsBoolean()) continue;
                if (list.size() >= MAX_WIDGETS) {
                    more++;
                    continue;
                }
                // A label widget without a label is just its text (like on the dashboard).
                String fallback = "label".equals(str(w, "type")) ? "" : id;
                String label = patch != null && !str(patch, "label").isEmpty() ? str(patch, "label") : str(w, "label").isEmpty() ? fallback : str(w, "label");
                list.add(widget(w, label));
            }
        }
        out.add("widgets", list);
        if (more > 0) out.addProperty("more", more);
        return out;
    }

    private static JsonObject widget(JsonObject w, String label) {
        String type = str(w, "type");
        var opts = w.get("opts") instanceof JsonObject o ? o : new JsonObject();
        var value = w.get("value");
        var p = new JsonObject();
        p.addProperty("t", type);
        p.addProperty("l", cut(label, MAX_LABEL));
        if (opts.has("color") && opts.get("color").isJsonPrimitive()) p.addProperty("color", cut(opts.get("color").getAsString(), 12));
        switch (type) {
            case "gauge", "slider", "number", "progress" -> {
                if (number(value) != null) p.addProperty("n", number(value));
                copyNumber(opts, p, "min");
                copyNumber(opts, p, "max");
                copyNumber(opts, p, "warn");
                copyNumber(opts, p, "crit");
                if (opts.has("unit") && opts.get("unit").isJsonPrimitive()) p.addProperty("u", cut(opts.get("unit").getAsString(), 8));
            }
            case "label", "text", "select" -> p.addProperty("s", cut(value != null && value.isJsonPrimitive() ? value.getAsString() : "", MAX_TEXT));
            case "toggle", "indicator" -> p.addProperty("b", value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean());
            case "chart" -> {
                var history = new JsonArray();
                if (w.get("history") instanceof JsonArray h) {
                    for (int i = Math.max(0, h.size() - HISTORY); i < h.size(); i++) {
                        Double v = number(h.get(i));
                        history.add(v == null ? 0 : v);
                    }
                }
                p.add("h", history);
                if (opts.has("unit") && opts.get("unit").isJsonPrimitive()) p.addProperty("u", cut(opts.get("unit").getAsString(), 8));
            }
            case "item_search", "watchlist", "jobs" -> p.addProperty("c", value instanceof JsonArray rows ? rows.size() : 0);
            case "redstone_sides" -> {
                var sides = new JsonArray();
                if (value instanceof JsonArray rows) {
                    for (var row : rows) {
                        if (!(row instanceof JsonObject r) || sides.size() >= 6) continue;
                        var pair = new JsonArray();
                        pair.add(intOf(r.get("out")));
                        pair.add(intOf(r.get("in")));
                        sides.add(pair);
                    }
                }
                p.add("rs", sides);
            }
            case "buttons" -> {
                var options = new JsonArray();
                if (opts.get("options") instanceof JsonArray o) {
                    for (int i = 0; i < Math.min(MAX_OPTIONS, o.size()); i++) options.add(cut(o.get(i).getAsString(), 16));
                }
                p.add("o", options);
            }
            default -> {
            }
        }
        return p;
    }

    private static void copyNumber(JsonObject from, JsonObject to, String key) {
        Double v = number(from.get(key));
        if (v != null) to.addProperty(key, v);
    }

    private static @Nullable Double number(@Nullable JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        double v = e.getAsDouble();
        return Double.isFinite(v) ? v : null;
    }

    private static int intOf(@Nullable JsonElement e) {
        Double v = number(e);
        return v == null ? 0 : (int) Math.max(0, Math.min(15, Math.round(v)));
    }

    private static String str(JsonObject o, String key) {
        var e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? "" : e.getAsString();
    }

    static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /**
     * What the card offers in words: the labels of the widgets that only show something and of the ones a player can
     * use, each list cut to {@code max} entries.
     */
    public record Summary(java.util.List<String> shows, int moreShows, java.util.List<String> controls, int moreControls) {
    }

    public static Summary summary(JsonObject preview, int max) {
        var shows = new java.util.ArrayList<String>();
        var controls = new java.util.ArrayList<String>();
        int moreShows = 0, moreControls = 0;
        if (preview.get("widgets") instanceof JsonArray widgets) {
            for (var e : widgets) {
                if (!(e instanceof JsonObject w)) continue;
                boolean control = switch (str(w, "t")) {
                    case "toggle", "slider", "select", "number", "text", "button", "buttons", "item_search", "watchlist", "redstone_sides" -> true;
                    default -> false;
                };
                var list = control ? controls : shows;
                String label = str(w, "l");
                if (label.isEmpty() || list.contains(label)) continue;
                if (list.size() < max) list.add(label);
                else if (control) moreControls++;
                else moreShows++;
            }
        }
        return new Summary(java.util.List.copyOf(shows), moreShows, java.util.List.copyOf(controls), moreControls);
    }
}
