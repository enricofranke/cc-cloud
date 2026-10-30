package ai.ezfox.cccloud.layout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.text.Collator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Layout v1 → v2 (docs/SPEC.md "Layout schema v2", "Migration v1 → v2"). A line-by-line port of the dashboard's
 * {@code migrateLayout} ({@code resolveLayout} + {@code snapshotLayout} in cloud/web/src/lib/layout.ts and the grid
 * functions in grid.ts), so a world migrated on the server looks exactly like one migrated by the first browser save.
 * The shared fixture {@code src/test/resources/layout/migration_fixture.json} is checked by both test suites.
 * <p>
 * One addition: entries of sections that are not known (any more) are kept without their v1 {@code order}/{@code span}
 * instead of being dropped, so nothing the user set is lost while a section is missing. Pure Java, unit tested.
 */
public final class LayoutMigration {
    private static final int COLS = LayoutValidator.COLS;
    private static final String DEFAULT_PAGE = "main";
    private static final Set<String> ICONS = ai.ezfox.cccloud.uplink.Icons.ALL;
    private static final Map<String, List<String>> DISPLAYS_FOR = Map.of(
        "gauge", List.of("dial", "bar", "number"),
        "progress", List.of("bar", "number"),
        "label", List.of("row", "big"),
        "chart", List.of("line", "sparkline"),
        "indicator", List.of("chip", "lamp"));
    private static final Map<String, Integer> TYPE_RANK = Map.ofEntries(
        Map.entry("gauge", 0), Map.entry("indicator", 1), Map.entry("chart", 2), Map.entry("progress", 3), Map.entry("label", 4),
        Map.entry("item_search", 5), Map.entry("watchlist", 6), Map.entry("jobs", 7), Map.entry("toggle", 8), Map.entry("slider", 8),
        Map.entry("select", 8), Map.entry("number", 8), Map.entry("text", 8), Map.entry("buttons", 9), Map.entry("button", 10),
        Map.entry("redstone_sides", 11));
    private static final Map<String, Integer> DEFAULT_H = Map.ofEntries(
        Map.entry("progress", 2), Map.entry("item_search", 3), Map.entry("watchlist", 4), Map.entry("jobs", 3), Map.entry("toggle", 1),
        Map.entry("slider", 2), Map.entry("select", 1), Map.entry("number", 1), Map.entry("text", 1), Map.entry("buttons", 1),
        Map.entry("button", 2), Map.entry("redstone_sides", 6));
    private static final int CARD_CHROME_H = 2;
    private static final Collator COLLATOR = Collator.getInstance(Locale.ROOT);
    private static final List<Map.Entry<Pattern, String>> ICON_GUESSES = List.of(
        Map.entry(Pattern.compile("reactor|fission|fusion|core|heat|boiler"), "flame"),
        Map.entry(Pattern.compile("power|energy|grid|matrix|battery|generator|turbine|solar"), "bolt"),
        Map.entry(Pattern.compile("storage|me network|rs network|chest|item|ae2|refined"), "box"),
        Map.entry(Pattern.compile("farm|tree|crop|garden|green|bee"), "leaf"),
        Map.entry(Pattern.compile("computer|server|cpu|terminal"), "cpu"),
        Map.entry(Pattern.compile("door|gate|lock|security|tür|tuer"), "lock"),
        Map.entry(Pattern.compile("water|fluid|tank|pump"), "drop"),
        Map.entry(Pattern.compile("mine|quarry|drill"), "pick"),
        Map.entry(Pattern.compile("control|kontroll|room|base|home"), "gear"),
        Map.entry(Pattern.compile("fan|cool|vent"), "fan"));

    private LayoutMigration() {
    }

    /** A box on a grid; mutable like the TS objects it mirrors. */
    static final class Item {
        final String id;
        int x;
        int y;
        int w;
        int h;
        boolean placed = true;

        Item(String id, int x, int y, int w, int h) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        Item copy() {
            var c = new Item(id, x, y, w, h);
            c.placed = placed;
            return c;
        }
    }

    // ------------------------------------------------------------------ grid.ts

    static boolean collides(Item a, Item b) {
        return !a.id.equals(b.id) && a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
    }

    private static final Comparator<Item> BY_POS = (a, b) -> a.y != b.y ? Integer.compare(a.y, b.y)
        : a.x != b.x ? Integer.compare(a.x, b.x) : COLLATOR.compare(a.id, b.id);

    private static Item clampItem(Item it, int cols, int maxH) {
        var c = it.copy();
        c.w = Math.max(1, Math.min(cols, it.w));
        c.h = Math.max(1, Math.min(maxH, it.h));
        c.x = Math.max(0, Math.min(cols - c.w, it.x));
        c.y = Math.max(0, it.y);
        return c;
    }

    private static boolean anyCollides(Item c, List<Item> others) {
        for (var o : others) if (collides(c, o)) return true;
        return false;
    }

    /** Same result as moving each item up row by row, but O(n²) (big bases: thousands of rows), like grid.ts. */
    static List<Item> compact(List<Item> items) {
        var out = new ArrayList<Item>();
        var sorted = new ArrayList<>(items);
        sorted.sort(BY_POS);
        for (var it : sorted) {
            var c = it.copy();
            int top = 0;
            for (var o : out) {
                if (o.id.equals(c.id) || !(c.x < o.x + o.w && o.x < c.x + c.w)) continue;
                int hi = Math.min(c.y - 1, o.y + o.h - 1);
                if (hi >= Math.max(o.y - c.h + 1, 0)) top = Math.max(top, hi + 1);
            }
            c.y = Math.min(c.y, top);
            int start = c.y;
            while (true) {
                int next = Integer.MAX_VALUE;
                for (var o : out) if (collides(c, o)) next = Math.min(next, o.y + o.h);
                if (next == Integer.MAX_VALUE) break;
                c.y = Math.min(next, start + 1000);
                if (c.y == start + 1000) break;
            }
            out.add(c);
        }
        out.sort(BY_POS);
        return out;
    }

    private static List<Item> pushDown(List<Item> items, Item moved) {
        var map = new LinkedHashMap<String, Item>();
        for (var i : items) map.put(i.id, i.copy());
        map.put(moved.id, moved.copy());
        var queue = new ArrayDeque<String>();
        queue.add(moved.id);
        for (int guard = 0; !queue.isEmpty() && guard < 10_000; guard++) {
            var cur = map.get(queue.poll());
            var sorted = new ArrayList<>(map.values());
            sorted.sort(BY_POS);
            for (var o : sorted) {
                if (o.id.equals(cur.id) || !collides(cur, o)) continue;
                o.y = cur.y + cur.h;
                queue.add(o.id);
            }
        }
        return new ArrayList<>(map.values());
    }

    /** First free spot, trying only rows/columns where something ends (same result as every cell), like grid.ts. */
    private static int[] freeSpot(List<Item> items, int w, int h, int cols) {
        int ww = Math.min(cols, w);
        var ys = new java.util.TreeSet<Integer>();
        ys.add(0);
        for (var o : items) if (o.y + o.h < 10_000) ys.add(o.y + o.h);
        for (int y : ys) {
            var band = new ArrayList<Item>();
            for (var o : items) if (o.y < y + h && y < o.y + o.h) band.add(o);
            var xs = new java.util.TreeSet<Integer>();
            xs.add(0);
            for (var o : band) xs.add(o.x + o.w);
            for (int x : xs) {
                if (x + ww > cols) continue;
                if (!anyCollides(new Item("\u0000", x, y, ww, h), band)) return new int[]{x, y};
            }
        }
        return new int[]{0, bottom(items)};
    }

    static int bottom(List<Item> items) {
        int m = 0;
        for (var i : items) m = Math.max(m, i.y + i.h);
        return m;
    }

    static List<Item> placeAll(List<Item> items, int cols) {
        List<Item> out = new ArrayList<>();
        for (var it : items) if (it.placed) out = pushDown(out, clampItem(it, cols, 200));
        for (var it : items) {
            if (it.placed) continue;
            var c = clampItem(it, cols, 200);
            int[] spot = freeSpot(out, c.w, c.h, cols);
            c.x = spot[0];
            c.y = spot[1];
            out.add(c);
        }
        return compact(out);
    }

    static List<Item> flowPack(List<Item> items, int cols) {
        var out = new ArrayList<Item>();
        int x = 0;
        int y = 0;
        int rowH = 0;
        for (var it : items) {
            int w = Math.min(cols, it.w);
            if (x + w > cols) {
                x = 0;
                y += rowH;
                rowH = 0;
            }
            var c = it.copy();
            c.x = x;
            c.y = y;
            c.w = w;
            out.add(c);
            x += w;
            rowH = Math.max(rowH, it.h);
        }
        return compact(out);
    }

    // ------------------------------------------------------------------ layout.ts

    static int defaultWidgetW(String type) {
        return switch (type) {
            case "gauge", "indicator" -> 6;
            case "button" -> 4;
            default -> 12;
        };
    }

    static int defaultWidgetH(String type, String display) {
        return switch (type) {
            case "gauge" -> display.equals("bar") ? 2 : display.equals("number") ? 3 : 5;
            case "label" -> display.equals("big") ? 3 : 1;
            case "chart" -> display.equals("sparkline") ? 3 : 5;
            case "indicator" -> display.equals("lamp") ? 3 : 2;
            default -> DEFAULT_H.getOrDefault(type, 2);
        };
    }

    private static final class Card {
        String key;
        boolean custom;
        String defaultTitle;
        String title;
        String page;
        Item box;
        boolean hidden;
        String color;
        @Nullable String icon;
        String style;
        double order;
        final List<Widget> widgets = new ArrayList<>();
    }

    private static final class Widget {
        String key;
        String type;
        String rawLabel;
        String home;
        String label;
        Item box;
        boolean hidden;
        @Nullable String color;
        @Nullable String icon;
        String display;
        double order;
        @Nullable Integer spanHint;
    }

    private record Page(String id, String title, @Nullable String icon, List<Card> cards) {
    }

    /**
     * Migrates a validated v1 document for the given section snapshots (in the hub's order). Returns a v2 document; it
     * still has to pass {@link LayoutValidator#validate}.
     */
    public static JsonObject migrate(List<JsonObject> uplinks, JsonObject v1) {
        var cardPatches = obj(v1.get("cards"));
        var widgetPatches = obj(v1.get("widgets"));
        var pages = resolve(uplinks, v1, cardPatches, widgetPatches);
        var doc = snapshot(pages);
        // keep entries of sections that are not published now (the dashboard would drop them)
        var cards = doc.getAsJsonObject("cards");
        for (var e : cardPatches.entrySet()) {
            if (cards.has(e.getKey()) || !(e.getValue() instanceof JsonObject patch)) continue;
            var copy = patch.deepCopy();
            copy.remove("order");
            copy.remove("span");
            cards.add(e.getKey(), copy);
        }
        var widgets = doc.getAsJsonObject("widgets");
        for (var e : widgetPatches.entrySet()) {
            if (widgets.has(e.getKey()) || !(e.getValue() instanceof JsonObject patch)) continue;
            var copy = patch.deepCopy();
            copy.remove("order");
            copy.remove("span");
            widgets.add(e.getKey(), copy);
        }
        return doc;
    }

    private static String cardKeyOf(String uplinkId, JsonObject w) {
        var o = obj(w.get("opts"));
        String group = truthy(o.get("group"));
        if (group != null) return uplinkId + "/" + group;
        String page = truthy(o.get("page"));
        if (page != null) return uplinkId + "~" + page;
        return uplinkId;
    }

    private static List<Page> resolve(List<JsonObject> uplinks, JsonObject doc, JsonObject cardPatches, JsonObject widgetPatches) {
        var pages = new ArrayList<Page>();
        if (doc.get("pages") instanceof JsonArray docPages && !docPages.isEmpty()) {
            for (var p : docPages) {
                var po = obj(p);
                pages.add(new Page(str(po.get("id")), str(po.get("title")) != null ? str(po.get("title")) : str(po.get("id")), icon(po.get("icon")),
                    new ArrayList<>()));
            }
        } else {
            pages.add(new Page(DEFAULT_PAGE, "Overview", "home", new ArrayList<>()));
            for (var p : luaPages(uplinks)) if (!p.id().equals(DEFAULT_PAGE)) pages.add(p);
        }
        var pageIds = new LinkedHashSet<String>();
        for (var p : pages) pageIds.add(p.id());
        String firstPage = pages.getFirst().id();

        var cards = new LinkedHashMap<String, Card>();
        var widgets = new ArrayList<Widget>();
        int[] natural = {0};

        for (var u : uplinks) {
            String uplinkId = str(u.get("uplinkId"));
            if (uplinkId == null) continue;
            var groups = new HashMap<String, JsonObject>();
            if (u.get("groups") instanceof JsonArray ga) {
                for (var g : ga) if (g instanceof JsonObject go && str(go.get("id")) != null) groups.put(str(go.get("id")), go);
            }
            var list = u.get("widgets") instanceof JsonArray wa ? wa : new JsonArray();
            String uTitle = str(u.get("title"));
            for (int wi = 0; wi < list.size(); wi++) {
                if (!(list.get(wi) instanceof JsonObject w)) continue;
                String key = cardKeyOf(uplinkId, w);
                var o = obj(w.get("opts"));
                if (!cards.containsKey(key)) {
                    JsonObject group = str(o.get("group")) != null ? groups.get(str(o.get("group"))) : null;
                    boolean wide = false;
                    for (var x : list) {
                        if (x instanceof JsonObject xo && cardKeyOf(uplinkId, xo).equals(key)
                            && ("item_search".equals(str(xo.get("type"))) || "chart".equals(str(xo.get("type"))))) wide = true;
                    }
                    String gTitle = group == null ? null : str(group.get("title"));
                    String defaultTitle = gTitle != null ? gTitle : uTitle != null ? uTitle : "Uplink";
                    String gPage = group == null ? null : str(group.get("page"));
                    String luaPage = gPage != null ? gPage : str(o.get("page"));
                    Integer cols = group != null && group.get("cols") instanceof JsonPrimitive cp && cp.isNumber() ? (int) Math.round(cp.getAsDouble()) : null;
                    cards.put(key, makeCard(cardPatches, key, defaultTitle, luaPage, cols, wide, o, pageIds, firstPage, natural));
                }
                String id = str(w.get("id"));
                String type = str(w.get("type"));
                if (id == null || type == null) continue;
                String wkey = uplinkId + "/" + id;
                var wp = obj(widgetPatches.get(wkey));
                var offered = DISPLAYS_FOR.get(type);
                String luaDisplay = type.equals("chart") && "sparkline".equals(str(o.get("style"))) ? "sparkline" : null;
                String wpDisplay = truthy(wp.get("display"));
                String display = wpDisplay != null && offered != null && offered.contains(wpDisplay) ? wpDisplay
                    : luaDisplay != null ? luaDisplay : offered != null ? offered.getFirst() : "auto";
                Integer spanHint = integer(wp.get("span"));
                if (spanHint == null && o.get("span") instanceof JsonPrimitive sp && sp.isNumber()) {
                    spanHint = (int) Math.max(1, Math.min(4, Math.round(sp.getAsDouble())));
                }
                var rw = new Widget();
                rw.key = wkey;
                rw.type = type;
                String wl = truthy(w.get("label"));
                rw.rawLabel = wl != null ? wl : id;
                rw.home = key;
                String pl = truthy(wp.get("label"));
                rw.label = pl != null ? pl : rw.rawLabel;
                Integer pw = integer(wp.get("w"));
                Integer ph = integer(wp.get("h"));
                rw.box = new Item(wkey, orZero(integer(wp.get("x"))), orZero(integer(wp.get("y"))),
                    pw != null ? pw : spanHint != null && spanHint != 0 ? spanHint * 3 : defaultWidgetW(type),
                    ph != null ? ph : defaultWidgetH(type, display));
                rw.box.placed = hasBox(wp);
                rw.spanHint = spanHint != null && spanHint != 0 && pw == null ? spanHint : null;
                rw.hidden = bool(wp.get("hidden"));
                rw.color = str(wp.get("color"));
                rw.icon = str(wp.get("icon"));
                rw.display = display;
                Double wpOrder = number(wp.get("order"));
                Double oOrder = number(o.get("order"));
                rw.order = wpOrder != null ? wpOrder : oOrder != null ? oOrder : TYPE_RANK.getOrDefault(type, 5) * 100 + wi;
                widgets.add(rw);
            }
            if (list.isEmpty() && !cards.containsKey(uplinkId)) {
                String t = uTitle != null && !uTitle.isEmpty() ? uTitle : "Uplink";
                cards.put(uplinkId, makeCard(cardPatches, uplinkId, t, null, null, false, new JsonObject(), pageIds, firstPage, natural));
            }
        }
        for (var key : cardPatches.keySet()) {
            if (!key.startsWith(LayoutValidator.CUSTOM_PREFIX) || cards.containsKey(key)) continue;
            var c = makeCard(cardPatches, key, "New card", null, null, false, new JsonObject(), pageIds, firstPage, natural);
            c.custom = true;
            cards.put(key, c);
        }
        for (var w : widgets) {
            String target = truthy(obj(widgetPatches.get(w.key)).get("card"));
            cards.get(target != null && cards.containsKey(target) ? target : w.home).widgets.add(w);
        }

        for (var c : cards.values()) {
            var visible = new ArrayList<Widget>();
            for (var w : c.widgets) if (!w.hidden) visible.add(w);
            visible.sort(Comparator.comparingDouble(w -> w.order));
            for (var w : visible) if (w.spanHint != null && !w.box.placed) w.box.w = c.box.w <= 3 ? Math.min(COLS, w.spanHint * 6) : w.spanHint * 3;
            for (var w : visible) w.box.w = Math.min(COLS, Math.max(LayoutValidator.minWidgetW(w.type), w.box.w));
            for (var w : visible) {
                if (!w.box.placed && w.spanHint == null && w.type.equals("indicator") && c.box.w <= 3) w.box.w = 12;
            }
            boolean anyPlaced = visible.stream().anyMatch(w -> w.box.placed);
            var items = new ArrayList<Item>();
            for (var w : visible) items.add(w.box.copy());
            var boxes = anyPlaced ? placeAll(items, COLS) : flowPack(items, COLS);
            var at = new HashMap<String, Item>();
            for (var b : boxes) at.put(b.id, b);
            for (var w : c.widgets) {
                var b = at.get(w.key);
                if (b != null) w.box = b.copy();
            }
            var all = new ArrayList<>(visible);
            for (var w : c.widgets) if (w.hidden) all.add(w);
            all.sort(Comparator.<Widget>comparingInt(w -> w.hidden ? 1 : 0).thenComparingInt(w -> w.box.y).thenComparingInt(w -> w.box.x));
            c.widgets.clear();
            c.widgets.addAll(all);
            if (c.box.h == 0) c.box.h = Math.max(3, Math.min(LayoutValidator.MAX_CARD_H, CARD_CHROME_H + bottom(boxes)));
            if (c.icon == null) c.icon = guessIcon(c.title, c.widgets);
        }
        for (var c : cards.values()) c.box.w = Math.min(COLS, Math.max(LayoutValidator.MIN_CARD_W, c.box.w));
        for (var page : pages) {
            var list = new ArrayList<Card>();
            for (var c : cards.values()) if (c.page.equals(page.id())) list.add(c);
            var shown = new ArrayList<Card>();
            for (var c : list) if (!c.hidden) shown.add(c);
            shown.sort(Comparator.comparingDouble(c -> c.order));
            boolean anyPlaced = shown.stream().anyMatch(c -> c.box.placed);
            var items = new ArrayList<Item>();
            for (var c : shown) items.add(c.box.copy());
            var boxes = anyPlaced ? placeAll(items, COLS) : flowPack(items, COLS);
            var at = new HashMap<String, Item>();
            for (var b : boxes) at.put(b.id, b);
            for (var c : shown) c.box = at.get(c.key).copy();
            var ordered = new ArrayList<>(shown);
            for (var c : list) if (!shown.contains(c)) ordered.add(c);
            ordered.sort(Comparator.<Card>comparingInt(c -> c.hidden ? 1 : 0).thenComparingInt(c -> c.box.y).thenComparingInt(c -> c.box.x));
            page.cards().addAll(ordered);
        }
        return pages;
    }

    private static Card makeCard(JsonObject cardPatches, String key, String defaultTitle, @Nullable String luaPage, @Nullable Integer cols, boolean wide,
                                 JsonObject o, Set<String> pageIds, String firstPage, int[] natural) {
        var patch = obj(cardPatches.get(key));
        Integer span = integer(patch.get("span"));
        Integer spanHint = span != null ? span : cols;
        Integer pw = integer(patch.get("w"));
        int w = pw != null ? pw : spanHint != null && spanHint != 0 ? spanHint * 3 : wide ? 6 : 3;
        var c = new Card();
        c.key = key;
        c.defaultTitle = defaultTitle;
        String title = truthy(patch.get("title"));
        c.title = title != null ? title : defaultTitle;
        String want = str(patch.get("page"));
        c.page = pageOf(want != null ? want : luaPage, luaPage, pageIds, firstPage);
        Integer ph = integer(patch.get("h"));
        c.box = new Item(key, orZero(integer(patch.get("x"))), orZero(integer(patch.get("y"))), w, ph != null ? ph : 0);
        c.box.placed = hasBox(patch);
        c.hidden = bool(patch.get("hidden"));
        String pc = str(patch.get("color"));
        String oc = str(o.get("color"));
        c.color = pc != null ? pc : oc != null && LayoutValidator.ACCENTS.contains(oc) && !oc.equals("gray") ? oc : "gold";
        String pi = str(patch.get("icon"));
        c.icon = pi != null ? pi : icon(o.get("icon"));
        String ps = str(patch.get("style"));
        c.style = ps != null ? ps : "comfortable";
        Integer order = integer(patch.get("order"));
        c.order = order != null ? order : 1000 + natural[0]++;
        return c;
    }

    private static String pageOf(@Nullable String want, @Nullable String fallback, Set<String> pageIds, String first) {
        if (want != null && !want.isEmpty() && pageIds.contains(want)) return want;
        if (fallback != null && !fallback.isEmpty() && pageIds.contains(fallback)) return fallback;
        return first;
    }

    private static List<Page> luaPages(List<JsonObject> uplinks) {
        var pages = new ArrayList<Page>();
        for (var u : uplinks) {
            if (!(u.get("pages") instanceof JsonArray declared)) continue;
            for (var p : declared) {
                if (!(p instanceof JsonObject po) || str(po.get("id")) == null) continue;
                String id = str(po.get("id"));
                if (pages.stream().anyMatch(x -> x.id().equals(id))) continue;
                String t = str(po.get("title"));
                String title = t != null ? t : id;
                pages.add(new Page(id, title.length() > 48 ? title.substring(0, 48) : title, icon(po.get("icon")), new ArrayList<>()));
            }
        }
        return pages;
    }

    private static @Nullable String guessIcon(String title, List<Widget> widgets) {
        String t = title.toLowerCase(Locale.ROOT);
        for (var g : ICON_GUESSES) if (g.getKey().matcher(t).find()) return g.getValue();
        for (var w : widgets) if (w.type.equals("chart")) return "chart";
        return null;
    }

    private static JsonObject snapshot(List<Page> pages) {
        var doc = new JsonObject();
        doc.addProperty("v", 2);
        var pageArray = new JsonArray();
        var cards = new JsonObject();
        var widgets = new JsonObject();
        for (var p : pages) {
            var po = new JsonObject();
            po.addProperty("id", p.id());
            po.addProperty("title", p.title());
            if (p.icon() != null) po.addProperty("icon", p.icon());
            pageArray.add(po);
            for (var c : p.cards()) {
                var co = new JsonObject();
                co.addProperty("page", p.id());
                box(co, c.box);
                if (!c.title.equals(c.defaultTitle) || c.custom) co.addProperty("title", c.title);
                if (c.hidden) co.addProperty("hidden", true);
                if (!c.color.equals("gold")) co.addProperty("color", c.color);
                if (c.icon != null) co.addProperty("icon", c.icon);
                if (!c.style.equals("comfortable")) co.addProperty("style", c.style);
                cards.add(c.key, co);
                for (var w : c.widgets) {
                    var wo = new JsonObject();
                    if (!c.key.equals(w.home)) wo.addProperty("card", c.key);
                    box(wo, w.box);
                    if (!w.label.equals(w.rawLabel)) wo.addProperty("label", w.label);
                    if (w.hidden) wo.addProperty("hidden", true);
                    if (w.color != null) wo.addProperty("color", w.color);
                    if (w.icon != null) wo.addProperty("icon", w.icon);
                    var offered = DISPLAYS_FOR.get(w.type);
                    if (!w.display.equals(offered != null ? offered.getFirst() : "auto")) wo.addProperty("display", w.display);
                    widgets.add(w.key, wo);
                }
            }
        }
        doc.add("pages", pageArray);
        doc.add("cards", cards);
        doc.add("widgets", widgets);
        return doc;
    }

    private static void box(JsonObject o, Item b) {
        o.addProperty("x", b.x);
        o.addProperty("y", b.y);
        o.addProperty("w", b.w);
        o.addProperty("h", b.h);
    }

    // ------------------------------------------------------------------ JSON helpers

    private static boolean hasBox(JsonObject p) {
        return p.has("x") && p.has("y") && p.has("w") && p.has("h");
    }

    private static JsonObject obj(@Nullable JsonElement e) {
        return e instanceof JsonObject o ? o : new JsonObject();
    }

    private static int orZero(@Nullable Integer i) {
        return i == null ? 0 : i;
    }

    /** A string value (empty allowed), like the dashboard's {@code str}. */
    private static @Nullable String str(@Nullable JsonElement e) {
        return e instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    /** A non-empty string (JS truthiness). */
    private static @Nullable String truthy(@Nullable JsonElement e) {
        String s = str(e);
        return s == null || s.isEmpty() ? null : s;
    }

    private static @Nullable String icon(@Nullable JsonElement e) {
        String s = str(e);
        return s != null && ICONS.contains(s) ? s : null;
    }

    private static boolean bool(@Nullable JsonElement e) {
        return e instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
    }

    private static @Nullable Double number(@Nullable JsonElement e) {
        return e instanceof JsonPrimitive p && p.isNumber() ? p.getAsDouble() : null;
    }

    private static @Nullable Integer integer(@Nullable JsonElement e) {
        return e instanceof JsonPrimitive p && p.isNumber() ? (int) Math.round(p.getAsDouble()) : null;
    }
}
