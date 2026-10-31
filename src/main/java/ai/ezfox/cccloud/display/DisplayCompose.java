package ai.ezfox.cccloud.display;

import ai.ezfox.cccloud.layout.LayoutValidator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a Cloud Display shows besides one original section: a custom card ({@code c:<id>}) or a whole page
 * ({@code p:<id>}) of the dashboard layout, composed on the server as one synthetic section snapshot, so the painter,
 * the click hit-test and the size limit stay the same. Follows {@code resolveLayout} in cloud/web/src/lib/layout.ts for
 * what belongs where and in which order, without the grid: the display flows its widgets itself. Input objects are
 * shared with the hub and are never changed. Pure and unit tested.
 * <p>
 * Composed widgets get the layout key as {@code id} and {@code src} = {@code <uplinkId>/<widgetId>} (where clicks go);
 * a page puts a {@code heading} widget above each card.
 */
public final class DisplayCompose {
    /** More widgets than the biggest display can show (8×6 blocks); the size limit of the display applies as well. */
    static final int MAX_WIDGETS = 128;
    private static final String PAGE_PREFIX = "p:";
    private static final String DEFAULT_PAGE = "main";
    private static final Pattern SECTION = Pattern.compile("[0-9a-fA-F-]{36}");
    private static final Pattern CUSTOM = Pattern.compile("c:[A-Za-z0-9_-]{1,24}");
    private static final Pattern PAGE = Pattern.compile("p:[A-Za-z0-9_-]{1,32}");
    /** Default order inside a card (TYPE_RANK in layout.ts). */
    private static final Map<String, Integer> TYPE_RANK = Map.ofEntries(Map.entry("gauge", 0), Map.entry("indicator", 1), Map.entry("chart", 2),
        Map.entry("sensor_log", 2), Map.entry("progress", 3), Map.entry("label", 4), Map.entry("item_search", 5), Map.entry("watchlist", 6),
        Map.entry("jobs", 7), Map.entry("toggle", 8), Map.entry("slider", 8), Map.entry("select", 8), Map.entry("number", 8), Map.entry("text", 8),
        Map.entry("buttons", 9), Map.entry("button", 10), Map.entry("redstone_sides", 11));

    /** @param filtered widgets left out because their section may not be shown here */
    public record Result(@Nullable JsonObject section, int filtered) {
    }

    private record Item(String key, String src, String uplinkId, JsonObject widget, @Nullable String label, int y, int x, int order) {
    }

    private static final class Card {
        final String key;
        String title;
        final boolean custom;
        final boolean hidden;
        final String page;
        final int y;
        final int x;
        final int order;
        final List<Item> items = new ArrayList<>();
        /** Widgets of sections that may not be shown here. */
        int filtered;

        Card(String key, String title, boolean custom, boolean hidden, String page, int y, int x, int order) {
            this.key = key;
            this.title = title;
            this.custom = custom;
            this.hidden = hidden;
            this.page = page;
            this.y = y;
            this.x = x;
            this.order = order;
        }
    }

    private record PageRow(String id, String title) {
    }

    private record Model(List<PageRow> pages, Map<String, Card> cards) {
    }

    private DisplayCompose() {
    }

    public static boolean isComposite(String target) {
        return target.startsWith(LayoutValidator.CUSTOM_PREFIX) || target.startsWith(PAGE_PREFIX);
    }

    /** "" (nothing), a section id, {@code c:<card>} or {@code p:<page>}. */
    public static boolean validTarget(String target) {
        return target.isEmpty() || SECTION.matcher(target).matches() || CUSTOM.matcher(target).matches() || PAGE.matcher(target).matches();
    }

    /** Whether a custom card or page exists in this layout. */
    public static boolean exists(String target, @Nullable JsonObject layout, Map<String, JsonObject> sections) {
        return compose(target, layout, sections, id -> true).section() != null;
    }

    /**
     * The synthetic section of a custom card or page, or a null section when it does not exist.
     *
     * @param allowed may a section's widgets be shown here ({@code uplinkId})
     */
    public static Result compose(String target, @Nullable JsonObject layout, Map<String, JsonObject> sections, Predicate<String> allowed) {
        var model = model(layout, sections, allowed);
        var section = new JsonObject();
        section.addProperty("uplinkId", target);
        var widgets = new JsonArray();
        var blocked = new JsonObject();
        boolean[] online = {true};
        int filtered = 0;
        if (target.startsWith(PAGE_PREFIX)) {
            String id = target.substring(PAGE_PREFIX.length());
            var page = model.pages().stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
            if (page == null) return new Result(null, 0);
            section.addProperty("title", page.title());
            var all = model.cards().values().stream().filter(c -> c.page.equals(id) && !c.hidden).toList();
            for (var card : all) filtered += card.filtered;
            var cards = all.stream().filter(c -> !c.items.isEmpty()).sorted(CARD_ORDER).toList();
            for (var card : cards) {
                if (cards.size() > 1) {
                    var heading = new JsonObject();
                    heading.addProperty("id", "h:" + card.key);
                    heading.addProperty("type", "heading");
                    heading.addProperty("label", card.title);
                    widgets.add(heading);
                }
                add(card, sections, widgets, blocked, online);
            }
        } else {
            var card = model.cards().get(target);
            if (card == null || !card.custom) return new Result(null, 0);
            section.addProperty("title", card.title);
            filtered = card.filtered;
            add(card, sections, widgets, blocked, online);
        }
        section.addProperty("online", online[0]);
        section.add("widgets", widgets);
        if (!blocked.isEmpty()) section.add("blockedWidgets", blocked);
        return new Result(section, filtered);
    }

    /** The custom cards and pages as picker rows: {@code {uplinkId, title, kind, restricted}}; {@code restricted} = shows a restricted section. */
    public static JsonArray targets(@Nullable JsonObject layout, Map<String, JsonObject> sections, java.util.Set<String> restricted) {
        var model = model(layout, sections, id -> true);
        var rows = new JsonArray();
        for (var card : model.cards().values()) {
            if (card.custom) rows.add(row(card.key, card.title, "card", touches(List.of(card), restricted)));
        }
        for (var page : model.pages()) {
            var cards = model.cards().values().stream().filter(c -> c.page.equals(page.id()) && !c.hidden).toList();
            rows.add(row(PAGE_PREFIX + page.id(), page.title(), "page", touches(cards, restricted)));
        }
        return rows;
    }

    private static boolean touches(List<Card> cards, java.util.Set<String> restricted) {
        for (var card : cards) for (var item : card.items) if (restricted.contains(item.uplinkId())) return true;
        return false;
    }

    private static JsonObject row(String id, String title, String kind, boolean restricted) {
        var row = new JsonObject();
        row.addProperty("uplinkId", id);
        row.addProperty("title", title);
        row.addProperty("kind", kind);
        row.addProperty("restricted", restricted);
        return row;
    }

    private static final Comparator<Card> CARD_ORDER = Comparator.<Card>comparingInt(c -> c.y).thenComparingInt(c -> c.x).thenComparingInt(c -> c.order);

    private static void add(Card card, Map<String, JsonObject> sections, JsonArray widgets, JsonObject blocked, boolean[] online) {
        card.items.sort(Comparator.comparingInt(Item::y).thenComparingInt(Item::x).thenComparingInt(Item::order));
        for (var item : card.items) {
            if (widgets.size() >= MAX_WIDGETS) return;
            var copy = new JsonObject();
            for (var e : item.widget().entrySet()) copy.add(e.getKey(), e.getValue());
            copy.addProperty("id", item.key());
            copy.addProperty("src", item.src());
            if (item.label() != null) copy.addProperty("label", item.label());
            widgets.add(copy);
            var section = sections.get(item.uplinkId());
            if (section == null) continue;
            if (!bool(section, "online")) online[0] = false;
            String widgetId = item.src().substring(item.src().indexOf('/') + 1);
            if (section.get("blockedWidgets") instanceof JsonObject b && b.has(widgetId)) blocked.add(item.key(), b.get(widgetId));
        }
    }

    /** Cards with their visible widgets, like {@code resolveLayout}: natural cards, custom cards, views, then moved widgets. */
    private static Model model(@Nullable JsonObject layout, Map<String, JsonObject> sections, Predicate<String> allowed) {
        var cardPatches = object(layout, "cards");
        var widgetPatches = object(layout, "widgets");
        var pages = pages(layout, sections);
        var cards = new LinkedHashMap<String, Card>();
        // natural cards: every widget in the card its script or device put it in (restricted ones too: they keep their place)
        record Home(String section, JsonObject widget, int index, String card) {
        }
        var homes = new ArrayList<Home>();
        for (var entry : sections.entrySet()) {
            String sectionId = entry.getKey();
            var section = entry.getValue();
            var list = section.get("widgets") instanceof JsonArray a ? a : new JsonArray();
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof JsonObject w)) continue;
                var o = w.get("opts") instanceof JsonObject opts ? opts : new JsonObject();
                String group = str(o, "group"), page = str(o, "page");
                String key = group != null ? sectionId + "/" + group : page != null ? sectionId + "~" + page : sectionId;
                if (!cards.containsKey(key)) {
                    var luaGroup = group == null ? null : group(section, group);
                    String title = luaGroup != null && str(luaGroup, "title") != null ? str(luaGroup, "title") : orEmpty(str(section, "title"));
                    String luaPage = luaGroup != null && str(luaGroup, "page") != null ? str(luaGroup, "page") : page;
                    cards.put(key, card(key, title, false, cardPatches, luaPage, pages, 1000 + cards.size()));
                }
                homes.add(new Home(sectionId, w, i, key));
            }
        }
        for (var key : cardPatches.keySet()) {
            if (key.startsWith(LayoutValidator.CUSTOM_PREFIX) && !cards.containsKey(key)) cards.put(key, card(key, "New card", true, cardPatches, null, pages, 1000 + cards.size()));
        }
        for (var home : homes) {
            String widgetId = str(home.widget(), "id");
            if (widgetId == null) continue;
            String key = home.section() + "/" + widgetId;
            var patch = widgetPatches.get(key) instanceof JsonObject p ? p : new JsonObject();
            if (bool(patch, "hidden")) continue;
            var card = cards.get(str(patch, "card") != null && cards.containsKey(str(patch, "card")) ? str(patch, "card") : home.card());
            if (!allowed.test(home.section())) {
                card.filtered++;
                continue;
            }
            var opts = home.widget().get("opts") instanceof JsonObject o ? o : new JsonObject();
            int order = integer(patch, "order", integer(opts, "order", TYPE_RANK.getOrDefault(orEmpty(str(home.widget(), "type")), 5) * 100 + home.index()));
            card.items.add(new Item(key, key, home.section(), home.widget(), emptyToNull(str(patch, "label")), integer(patch, "y", Integer.MAX_VALUE),
                integer(patch, "x", 0), order));
        }
        for (var entry : widgetPatches.entrySet()) {
            if (!entry.getKey().startsWith(LayoutValidator.VIEW_PREFIX) || !(entry.getValue() instanceof JsonObject patch) || bool(patch, "hidden")) continue;
            String src = str(patch, "src"), as = str(patch, "as"), target = str(patch, "card");
            if (src == null || as == null || target == null || !cards.containsKey(target) || src.indexOf('/') < 1) continue;
            String sectionId = src.substring(0, src.indexOf('/')), widgetId = src.substring(src.indexOf('/') + 1);
            var source = widget(sections.get(sectionId), widgetId);
            if (source == null) continue; // shown as an offline widget in the dashboard; a display has no room for that
            if (!allowed.test(sectionId)) {
                cards.get(target).filtered++;
                continue;
            }
            cards.get(target).items.add(new Item(entry.getKey(), src, sectionId, view(source, as), emptyToNull(str(patch, "label")), integer(patch, "y", Integer.MAX_VALUE),
                integer(patch, "x", 0), 5000));
        }
        return new Model(pages, cards);
    }

    private static Card card(String key, String title, boolean custom, JsonObject patches, @Nullable String luaPage, List<PageRow> pages, int order) {
        var patch = patches.get(key) instanceof JsonObject p ? p : new JsonObject();
        String patched = str(patch, "title");
        return new Card(key, patched == null || patched.isEmpty() ? title : patched, custom, bool(patch, "hidden"),
            pageOf(str(patch, "page") != null ? str(patch, "page") : luaPage, luaPage, pages), integer(patch, "y", Integer.MAX_VALUE), integer(patch, "x", 0),
            integer(patch, "order", order));
    }

    /** The edited list wins; otherwise Overview plus the pages scripts declared. */
    private static List<PageRow> pages(@Nullable JsonObject layout, Map<String, JsonObject> sections) {
        var pages = new ArrayList<PageRow>();
        if (layout != null && layout.get("pages") instanceof JsonArray list) {
            for (var p : list) if (p instanceof JsonObject page && str(page, "id") != null) pages.add(new PageRow(str(page, "id"), orEmpty(str(page, "title"))));
        }
        if (!pages.isEmpty()) return pages;
        pages.add(new PageRow(DEFAULT_PAGE, "Overview"));
        for (var section : sections.values()) {
            if (!(section.get("pages") instanceof JsonArray list)) continue;
            for (var p : list) {
                if (!(p instanceof JsonObject page) || str(page, "id") == null || pages.stream().anyMatch(x -> x.id().equals(str(page, "id")))) continue;
                pages.add(new PageRow(str(page, "id"), str(page, "title") != null ? str(page, "title") : str(page, "id")));
            }
        }
        return pages;
    }

    private static String pageOf(@Nullable String want, @Nullable String fallback, List<PageRow> pages) {
        for (var page : pages) if (page.id().equals(want)) return page.id();
        for (var page : pages) if (page.id().equals(fallback)) return page.id();
        return pages.getFirst().id();
    }

    /** The published widget as a view of another type (viewWidget in layout.ts), on a copy. */
    static JsonObject view(JsonObject source, String as) {
        String type = str(source, "type");
        if (as.equals(type)) return source;
        var w = new JsonObject();
        for (var e : source.entrySet()) if (!e.getKey().equals("value")) w.add(e.getKey(), e.getValue());
        w.addProperty("type", as);
        JsonElement last = source.get("value");
        if ("chart".equals(type) && source.get("history") instanceof JsonArray h && !h.isEmpty()) last = h.get(h.size() - 1);
        if (last instanceof JsonPrimitive p) {
            if (as.equals("label") && p.isNumber()) {
                double d = p.getAsDouble();
                String unit = source.get("opts") instanceof JsonObject o ? orEmpty(str(o, "unit")) : "";
                w.addProperty("value", (d == Math.rint(d) ? String.valueOf((long) d) : String.format(java.util.Locale.ROOT, "%.1f", d)) + (unit.isEmpty() ? "" : " " + unit));
            } else {
                w.add("value", p);
            }
        }
        return w;
    }

    private static @Nullable JsonObject widget(@Nullable JsonObject section, String id) {
        if (section == null || !(section.get("widgets") instanceof JsonArray list)) return null;
        for (var w : list) if (w instanceof JsonObject o && id.equals(str(o, "id"))) return o;
        return null;
    }

    private static @Nullable JsonObject group(JsonObject section, String id) {
        if (!(section.get("groups") instanceof JsonArray list)) return null;
        for (var g : list) if (g instanceof JsonObject o && id.equals(str(o, "id"))) return o;
        return null;
    }

    private static JsonObject object(@Nullable JsonObject o, String key) {
        return o != null && o.get(key) instanceof JsonObject v ? v : new JsonObject();
    }

    private static @Nullable String str(JsonObject o, String key) {
        return o.get(key) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    private static String orEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }

    private static @Nullable String emptyToNull(@Nullable String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static boolean bool(JsonObject o, String key) {
        return o.get(key) instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
    }

    private static int integer(JsonObject o, String key, int fallback) {
        return o.get(key) instanceof JsonPrimitive p && p.isNumber() ? p.getAsInt() : fallback;
    }
}
