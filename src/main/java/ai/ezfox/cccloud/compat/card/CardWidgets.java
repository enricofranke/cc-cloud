package ai.ezfox.cccloud.compat.card;

import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Builds card widgets through {@link WidgetFactory}, i.e. with exactly the validation a Lua script gets (ids, label
 * lengths, option keys, ranges). A card that builds an invalid widget is a programming error and fails loudly in the
 * unit tests ({@code CardWidgetsTest}) instead of reaching a browser.
 */
public final class CardWidgets {
    /** Energy history: 300 points × 12 s = the last 60 minutes (the chart ring buffer is capped at 300 points). */
    public static final int HISTORY_POINTS = 300;
    public static final double HISTORY_INTERVAL = 12;

    private final List<Widget> widgets = new ArrayList<>();

    /** Widget span that fills a card body row (4 columns; the dashboard maps it to the full width, w = 12). */
    public static final int FULL_ROW = 4;

    /**
     * The widgets, with full rows for lone ones (same rule for every card): a label whose explicit span leaves the
     * rest of its row empty is widened to fill it (an odd "Status" or "Energy" label next to nothing), and a single
     * button gets the whole row instead of a quarter. Widgets with an explicit {@code order} keep their spans.
     */
    public List<Widget> build() {
        fillRows();
        var buttons = widgets.stream().filter(w -> w.type() == WidgetType.BUTTON && !w.opts().has("order")).toList();
        if (buttons.size() == 1 && !buttons.getFirst().opts().has("span")) buttons.getFirst().opts().addProperty("span", FULL_ROW);
        return List.copyOf(widgets);
    }

    /** Packs the labels like the dashboard does (in order, rows of 4 columns, no span = full row) and closes gaps. */
    private void fillRows() {
        int fill = 0;
        @Nullable Widget last = null;
        for (var w : widgets) {
            if (w.type() != WidgetType.LABEL || w.opts().has("order")) continue;
            int span = w.opts().has("span") ? w.opts().get("span").getAsInt() : FULL_ROW;
            if (fill + span > FULL_ROW) {
                widen(last, fill);
                fill = 0;
            }
            fill += span;
            last = w;
        }
        widen(last, fill);
    }

    private static void widen(@Nullable Widget last, int fill) {
        if (last != null && fill > 0 && fill < FULL_ROW && last.opts().has("span")) {
            last.opts().addProperty("span", last.opts().get("span").getAsInt() + FULL_ROW - fill);
        }
    }

    public CardWidgets gauge(String id, String label, double min, double max, @Nullable String unit, Object... opts) {
        return add(WidgetType.GAUGE, id, label, merge(opts, "min", min, "max", max, "unit", unit));
    }

    /** 0–100 % gauge. */
    public CardWidgets percent(String id, String label, Object... opts) {
        return gauge(id, label, 0, 100, "%", opts);
    }

    public CardWidgets progress(String id, String label, double min, double max, @Nullable String unit, Object... opts) {
        return add(WidgetType.PROGRESS, id, label, merge(opts, "min", min, "max", max, "unit", unit));
    }

    /** A text label with a caption (like the Storage template's labels); its value starts as "–". */
    public CardWidgets label(String id, String caption, Object... opts) {
        if (caption.length() > ai.ezfox.cccloud.uplink.Limits.LABEL) throw new IllegalStateException("card label too long: " + caption);
        add(WidgetType.LABEL, id, "–", merge(opts));
        var plain = widgets.removeLast();
        widgets.add(new Widget(plain.id(), WidgetType.LABEL, caption, plain.opts(), plain.value()));
        return this;
    }

    public CardWidgets indicator(String id, String label, Object... opts) {
        return add(WidgetType.INDICATOR, id, label, merge(opts));
    }

    /** 60-minute history chart (300 points, one every 12 s). */
    public CardWidgets history(String id, String label, @Nullable Double min, @Nullable Double max, @Nullable String unit, Object... opts) {
        var o = merge(opts, "points", HISTORY_POINTS, "interval", HISTORY_INTERVAL, "unit", unit);
        if (min != null && max != null) {
            o.put("min", min);
            o.put("max", max);
        }
        return add(WidgetType.CHART, id, label, o);
    }

    public CardWidgets toggle(String id, String label, Object... opts) {
        return add(WidgetType.TOGGLE, id, label, merge(opts));
    }

    public CardWidgets button(String id, String label, Object... opts) {
        return add(WidgetType.BUTTON, id, label, merge(opts));
    }

    public CardWidgets select(String id, String label, List<String> options, Object... opts) {
        // Options as a Lua-style list (1-based table), exactly what a script would pass.
        var table = new LinkedHashMap<Integer, String>();
        for (int i = 0; i < options.size(); i++) table.put(i + 1, options.get(i));
        return add(WidgetType.SELECT, id, label, merge(opts, "options", table));
    }

    public CardWidgets number(String id, String label, double min, double max, double step, Object... opts) {
        return add(WidgetType.NUMBER, id, label, merge(opts, "min", min, "max", max, "step", step));
    }

    /** Item search over a storage card's contents (no crafting: {@code max} 1, rows are never craftable). */
    public CardWidgets itemSearch(String id, String label, Object... opts) {
        return add(WidgetType.ITEM_SEARCH, id, label, merge(opts, "max", 1));
    }

    private CardWidgets add(WidgetType type, String id, String label, Map<String, Object> opts) {
        try {
            widgets.add(WidgetFactory.create(type, id, label, opts));
        } catch (WidgetFactory.WidgetException e) {
            throw new IllegalStateException("invalid card widget " + id + ": " + e.getMessage(), e);
        }
        return this;
    }

    /** Key/value pairs (null values are skipped) followed by the caller's extra pairs. */
    private static Map<String, Object> merge(Object[] extra, Object... base) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < base.length; i += 2) if (base[i + 1] != null) map.put((String) base[i], base[i + 1]);
        for (int i = 0; i + 1 < extra.length; i += 2) if (extra[i + 1] != null) map.put((String) extra[i], extra[i + 1]);
        return map;
    }

    private static Map<String, Object> merge(Object[] extra) {
        return merge(extra, new Object[0]);
    }
}
