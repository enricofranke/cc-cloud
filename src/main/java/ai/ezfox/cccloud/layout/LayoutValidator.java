package ai.ezfox.cccloud.layout;

import ai.ezfox.cccloud.uplink.Icons;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Limits;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Validates the dashboard layout a browser sends with {@code layout_set} (docs/SPEC.md "Layout schema" and "Layout schema
 * v2", reference: {@code layoutDoc} in cloud/dev/protocol.ts). Both versions are accepted: v1 (order/span) and v2 (x/y/w/h
 * on 12-column grids, custom cards {@code c:<id>}, display styles). Strict: unknown keys, wrong types and out-of-range
 * values reject the whole document; nothing is silently fixed. Pure Java, unit tested.
 * <p>
 * Card and widget keys of uplinks/widgets that do not exist (any more) are accepted on purpose: the layout must survive
 * script restarts and unloaded chunks, and the dashboard simply skips unknown ids.
 */
public final class LayoutValidator {
    /**
     * Document limits, raised for big bases in 0.10 (docs/SPEC.md "Scaling"): a page of 500 cards needs more than 256
     * card entries and rows beyond 999; the dashboard leaves out the widget entries of untouched cards, so 256 KB holds
     * the cards of a 2000-section base. Saving is still limited to 5 per 10 s per mainframe and needs layout.edit.
     */
    public static final int MAX_BYTES = 256 * 1024;
    public static final int MAX_PAGES = 12;
    public static final int MAX_CARDS = 2048;
    public static final int MAX_WIDGETS = 8192;
    public static final int MAX_KEY = 160;
    public static final int MAX_ORDER = 10_000;
    public static final Set<String> ACCENTS = Set.of("gold", "cyan", "green", "yellow", "red", "purple", "orange", "gray");
    public static final Set<String> STYLES = Set.of("comfortable", "compact");
    public static final Set<String> DISPLAYS = Set.of("dial", "bar", "number", "row", "big", "line", "sparkline", "chip", "lamp");
    /** v2: columns of the page grid and of every card's widget grid. */
    public static final int COLS = 12;
    public static final int MAX_CARD_H = 60;
    public static final int MAX_WIDGET_H = 40;
    public static final int MAX_Y = 99_999;
    /** v2: the smallest readable card width (of 12). */
    public static final int MIN_CARD_W = 3;
    public static final String CUSTOM_PREFIX = "c:";
    private static final Pattern PAGE_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Pattern CUSTOM_CARD = Pattern.compile("c:[A-Za-z0-9_-]{1,24}");

    /** @param error {@code too_large} or {@code invalid_layout}; {@code detail} is the JSON path of the first problem. */
    public record Result(@Nullable JsonObject layout, @Nullable String error, @Nullable String detail) {
        public boolean ok() {
            return error == null;
        }
    }

    private static final class Invalid extends Exception {
        Invalid(String path) {
            super(path, null, false, false);
        }
    }

    private LayoutValidator() {
    }

    public static Result validate(@Nullable JsonElement element) {
        if (element != null && Json.GSON.toJson(element).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return new Result(null, "too_large", null);
        }
        if (element == null || !element.isJsonObject()) return new Result(null, "invalid_layout", "layout");
        var layout = element.getAsJsonObject();
        try {
            keys(layout, "layout", Set.of("v", "pages", "cards", "widgets"), true);
            int version = integer(layout.get("v"), "v", 1, 2);

            var pages = layout.get("pages");
            if (pages == null || !pages.isJsonArray() || pages.getAsJsonArray().size() > MAX_PAGES) throw new Invalid("pages");
            var pageIds = new HashSet<String>();
            int i = 0;
            for (var p : pages.getAsJsonArray()) {
                String path = "pages[" + i++ + "]";
                var page = object(p, path);
                keys(page, path, Set.of("id", "title", "icon"), false);
                String id = string(page.get("id"), path + ".id");
                if (!PAGE_ID.matcher(id).matches() || !pageIds.add(id)) throw new Invalid(path + ".id");
                String title = string(page.get("title"), path + ".title");
                if (title.isEmpty() || title.length() > Limits.TITLE) throw new Invalid(path + ".title");
                if (page.has("icon")) icon(page.get("icon"), path + ".icon");
            }

            var cards = map(layout, "cards", MAX_CARDS);
            for (var entry : cards.entrySet()) {
                String path = "cards[" + entry.getKey() + "]";
                var card = object(entry.getValue(), path);
                if (version == 1) {
                    keys(card, path, Set.of("page", "order", "span", "title", "hidden", "color", "icon"), false);
                    v1(card, path);
                } else {
                    if (entry.getKey().startsWith(CUSTOM_PREFIX) && !CUSTOM_CARD.matcher(entry.getKey()).matches()) throw new Invalid(path);
                    keys(card, path, Set.of("page", "x", "y", "w", "h", "title", "hidden", "color", "icon", "style"), false);
                    box(card, path, MIN_CARD_W, MAX_CARD_H);
                    if (card.has("style") && !STYLES.contains(string(card.get("style"), path + ".style"))) throw new Invalid(path + ".style");
                }
                if (card.has("page") && string(card.get("page"), path + ".page").length() > 32) throw new Invalid(path + ".page");
                common(card, path, "title", Limits.TITLE);
                if (card.has("color") && !ACCENTS.contains(string(card.get("color"), path + ".color"))) throw new Invalid(path + ".color");
                if (card.has("icon")) icon(card.get("icon"), path + ".icon");
            }

            var widgets = map(layout, "widgets", MAX_WIDGETS);
            for (var entry : widgets.entrySet()) {
                String path = "widgets[" + entry.getKey() + "]";
                var widget = object(entry.getValue(), path);
                if (version == 1) {
                    keys(widget, path, Set.of("order", "span", "label", "hidden"), false);
                    v1(widget, path);
                } else {
                    keys(widget, path, Set.of("card", "x", "y", "w", "h", "label", "hidden", "color", "icon", "display"), false);
                    box(widget, path, 1, MAX_WIDGET_H);
                    if (widget.has("card")) {
                        String card = string(widget.get("card"), path + ".card");
                        if (card.isEmpty() || card.length() > MAX_KEY) throw new Invalid(path + ".card");
                    }
                    if (widget.has("color") && !ACCENTS.contains(string(widget.get("color"), path + ".color"))) throw new Invalid(path + ".color");
                    if (widget.has("icon")) icon(widget.get("icon"), path + ".icon");
                    if (widget.has("display") && !DISPLAYS.contains(string(widget.get("display"), path + ".display"))) throw new Invalid(path + ".display");
                }
                common(widget, path, "label", Limits.LABEL);
            }
        } catch (Invalid e) {
            return new Result(null, "invalid_layout", e.getMessage());
        } catch (RuntimeException e) {
            return new Result(null, "invalid_layout", "layout");
        }
        return new Result(layout.deepCopy(), null, null);
    }

    /**
     * v2 widget minimum widths by type (docs/SPEC.md "Layout schema v2", same as {@code minWidgetW} in
     * cloud/web/src/lib/layout.ts).
     */
    public static int minWidgetW(@Nullable String type) {
        if (type == null) return 1;
        return switch (type) {
            case "item_search", "watchlist", "jobs", "redstone_sides", "sensor_log" -> 6;
            case "chart" -> 4;
            case "gauge" -> 2;
            default -> 3;
        };
    }

    /**
     * v2: the first visible widget narrower than its type allows ({@link #minWidgetW}), as a JSON path, or null. Widgets
     * whose type is unknown (uplink not published) and hidden widgets are not checked.
     *
     * @param typeOf widget key ({@code <uplinkId>/<widgetId>}) → widget type, or null when unknown
     */
    public static @Nullable String widgetMinimums(JsonObject layout, java.util.function.Function<String, @Nullable String> typeOf) {
        if (!(layout.get("v") instanceof com.google.gson.JsonPrimitive v) || v.getAsInt() != 2) return null;
        if (!(layout.get("widgets") instanceof JsonObject widgets)) return null;
        for (var entry : widgets.entrySet()) {
            if (!(entry.getValue() instanceof JsonObject widget) || !widget.has("w")) continue;
            if (widget.get("hidden") instanceof com.google.gson.JsonPrimitive h && h.isBoolean() && h.getAsBoolean()) continue;
            String type = typeOf.apply(entry.getKey());
            if (type != null && widget.get("w").getAsInt() < minWidgetW(type)) return "widgets[" + entry.getKey() + "].w";
        }
        return null;
    }

    private static void v1(JsonObject obj, String path) throws Invalid {
        if (obj.has("order")) integer(obj.get("order"), path + ".order", 0, MAX_ORDER);
        if (obj.has("span")) integer(obj.get("span"), path + ".span", 1, 4);
    }

    /** v2 grid box: x 0–11, y 0–999, w minW–12, h 1–maxH, x + w ≤ 12 when both are set. */
    private static void box(JsonObject obj, String path, int minW, int maxH) throws Invalid {
        Integer x = obj.has("x") ? integer(obj.get("x"), path + ".x", 0, COLS - 1) : null;
        if (obj.has("y")) integer(obj.get("y"), path + ".y", 0, MAX_Y);
        Integer w = obj.has("w") ? integer(obj.get("w"), path + ".w", minW, COLS) : null;
        if (obj.has("h")) integer(obj.get("h"), path + ".h", 1, maxH);
        if (x != null && w != null && x + w > COLS) throw new Invalid(path + ".w");
    }

    private static void common(JsonObject obj, String path, String textKey, int maxText) throws Invalid {
        if (obj.has("hidden")) {
            var h = obj.get("hidden");
            if (!h.isJsonPrimitive() || !h.getAsJsonPrimitive().isBoolean()) throw new Invalid(path + ".hidden");
        }
        if (obj.has(textKey) && string(obj.get(textKey), path + "." + textKey).length() > maxText) throw new Invalid(path + "." + textKey);
    }

    private static JsonObject map(JsonObject parent, String key, int max) throws Invalid {
        var e = parent.get(key);
        if (e == null || !e.isJsonObject()) throw new Invalid(key);
        var obj = e.getAsJsonObject();
        if (obj.size() > max) throw new Invalid(key);
        for (var k : obj.keySet()) if (k.isEmpty() || k.length() > MAX_KEY) throw new Invalid(key);
        return obj;
    }

    private static void keys(JsonObject obj, String path, Set<String> allowed, boolean required) throws Invalid {
        for (var key : obj.keySet()) if (!allowed.contains(key)) throw new Invalid(path + "." + key);
        if (required) for (var key : allowed) if (!obj.has(key)) throw new Invalid(path + "." + key);
    }

    private static JsonObject object(JsonElement e, String path) throws Invalid {
        if (e == null || !e.isJsonObject()) throw new Invalid(path);
        return e.getAsJsonObject();
    }

    private static void icon(JsonElement e, String path) throws Invalid {
        if (!Icons.ALL.contains(string(e, path))) throw new Invalid(path);
    }

    private static int integer(@Nullable JsonElement e, String path, int min, int max) throws Invalid {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new Invalid(path);
        double v = e.getAsDouble();
        if (v != Math.rint(v) || v < min || v > max) throw new Invalid(path);
        return (int) v;
    }

    private static String string(@Nullable JsonElement e, String path) throws Invalid {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new Invalid(path);
        return e.getAsString();
    }
}
