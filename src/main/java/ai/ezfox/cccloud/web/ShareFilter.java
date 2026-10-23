package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.access.AccessStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What a share-link viewer may see (docs/SPEC.md v0.6 "Share links"): only the shared cards (by card key, or every
 * card on a shared page, resolved like the dashboard's {@code resolveLayout}), never {@code item_search} widgets, and
 * with {@code hideNumbers} no {@code label}/{@code text}/{@code number} widgets. No network map, no positions.
 * Layout v2: a widget counts for the card it was moved into ({@code widgets[key].card}, if that card exists), and custom
 * cards ({@code c:<id>}) are shared by key or by their page.
 * Pure Java, unit tested.
 */
public final class ShareFilter {
    public static final String DEFAULT_PAGE = "main";
    private static final Set<String> ALWAYS_HIDDEN = Set.of("item_search");
    private static final Set<String> NUMBERS = Set.of("label", "text", "number");
    private static final String CUSTOM_PREFIX = "c:";

    private ShareFilter() {
    }

    /** Card key of a widget: {@code <uplinkId>/<group>}, {@code <uplinkId>~<page>} or {@code <uplinkId>}. */
    public static String cardKeyOf(String uplinkId, JsonObject widget) {
        var opts = widget.get("opts") instanceof JsonObject o ? o : null;
        String group = opts == null ? null : str(opts.get("group"));
        if (group != null) return uplinkId + "/" + group;
        String page = opts == null ? null : str(opts.get("page"));
        if (page != null) return uplinkId + "~" + page;
        return uplinkId;
    }

    /**
     * What a share shows: the shared card keys and the widgets that were moved into another (existing) card.
     *
     * @param moved widget key ({@code <uplinkId>/<widgetId>}) → the card it sits in now
     */
    public record Scope(Set<String> cards, Map<String, String> moved) {
        public static Scope of(Set<String> cards) {
            return new Scope(cards, Map.of());
        }

        public String cardOf(String uplinkId, JsonObject widget) {
            String id = str(widget.get("id"));
            String card = id == null ? null : moved.get(uplinkId + "/" + id);
            return card != null ? card : cardKeyOf(uplinkId, widget);
        }
    }

    public static Scope scope(Collection<JsonObject> uplinks, @Nullable JsonObject layout, AccessStore.Share share) {
        var cards = cardKeys(uplinks, layout, share);
        if (layout == null || !(layout.get("widgets") instanceof JsonObject widgetPatches)) return Scope.of(cards);
        var existing = new HashSet<String>();
        for (var uplink : uplinks) {
            String uplinkId = str(uplink.get("uplinkId"));
            if (uplinkId == null) continue;
            existing.add(uplinkId);
            if (uplink.get("widgets") instanceof JsonArray widgets) {
                for (var e : widgets) if (e instanceof JsonObject w) existing.add(cardKeyOf(uplinkId, w));
            }
        }
        if (layout.get("cards") instanceof JsonObject cardPatches) {
            for (var key : cardPatches.keySet()) if (key.startsWith(CUSTOM_PREFIX)) existing.add(key);
        }
        var moved = new HashMap<String, String>();
        for (var entry : widgetPatches.entrySet()) {
            String card = entry.getValue() instanceof JsonObject patch ? str(patch.get("card")) : null;
            if (card != null && existing.contains(card)) moved.put(entry.getKey(), card);
        }
        return new Scope(cards, moved);
    }

    /** Every card key the share includes: its explicit cards plus all cards placed on its pages (custom cards too). */
    public static Set<String> cardKeys(Collection<JsonObject> uplinks, @Nullable JsonObject layout, AccessStore.Share share) {
        var keys = new LinkedHashSet<>(share.cards());
        if (share.pages().isEmpty()) return keys;
        var pages = pageIds(uplinks, layout);
        String first = pages.isEmpty() ? DEFAULT_PAGE : pages.getFirst();
        var pageSet = new HashSet<>(pages);
        var cardPatches = layout != null && layout.get("cards") instanceof JsonObject c ? c : new JsonObject();
        for (var uplink : uplinks) {
            String uplinkId = str(uplink.get("uplinkId"));
            if (uplinkId == null) continue;
            var groups = new HashMap<String, String>();
            if (uplink.get("groups") instanceof JsonArray array) {
                for (var g : array) {
                    if (g instanceof JsonObject go && str(go.get("id")) != null) groups.put(str(go.get("id")), str(go.get("page")));
                }
            }
            var widgets = uplink.get("widgets") instanceof JsonArray w ? w : new JsonArray();
            var seen = new HashSet<String>();
            for (var e : widgets) {
                if (!(e instanceof JsonObject widget)) continue;
                String key = cardKeyOf(uplinkId, widget);
                if (!seen.add(key)) continue;
                var opts = widget.get("opts") instanceof JsonObject o ? o : null;
                String group = opts == null ? null : str(opts.get("group"));
                String luaPage = group != null ? groups.get(group) : null;
                if (luaPage == null && opts != null) luaPage = str(opts.get("page"));
                String page = pageOf(cardPatches, key, luaPage, first, pageSet);
                if (share.pages().contains(page)) keys.add(key);
            }
            if (widgets.isEmpty() && share.pages().contains(pageOf(cardPatches, uplinkId, null, first, pageSet))) keys.add(uplinkId);
        }
        for (var key : cardPatches.keySet()) {
            if (key.startsWith(CUSTOM_PREFIX) && share.pages().contains(pageOf(cardPatches, key, null, first, pageSet))) keys.add(key);
        }
        return keys;
    }

    private static String pageOf(JsonObject cardPatches, String key, @Nullable String luaPage, String first, Set<String> pageSet) {
        String page = cardPatches.get(key) instanceof JsonObject patch ? str(patch.get("page")) : null;
        if (page == null) page = luaPage != null ? luaPage : first;
        if (!pageSet.contains(page)) page = luaPage != null && pageSet.contains(luaPage) ? luaPage : first;
        return page;
    }

    /** Page ids in tab order: the edited list, else {@code main} + the pages the scripts declared. */
    static List<String> pageIds(Collection<JsonObject> uplinks, @Nullable JsonObject layout) {
        var ids = new ArrayList<String>();
        if (layout != null && layout.get("pages") instanceof JsonArray pages && !pages.isEmpty()) {
            for (var p : pages) if (p instanceof JsonObject po && str(po.get("id")) != null) ids.add(str(po.get("id")));
            if (!ids.isEmpty()) return ids;
        }
        ids.add(DEFAULT_PAGE);
        for (var uplink : uplinks) {
            if (!(uplink.get("pages") instanceof JsonArray declared)) continue;
            for (var p : declared) {
                String id = p instanceof JsonObject po ? str(po.get("id")) : null;
                if (id != null && !ids.contains(id)) ids.add(id);
            }
        }
        return ids;
    }

    public static boolean widgetVisible(String uplinkId, JsonObject widget, Scope scope, boolean hideNumbers) {
        String type = str(widget.get("type"));
        if (type == null || ALWAYS_HIDDEN.contains(type) || (hideNumbers && NUMBERS.contains(type))) return false;
        return scope.cards().contains(scope.cardOf(uplinkId, widget));
    }

    /** A copy of the uplink with only the visible widgets, or null if none is visible. */
    public static @Nullable JsonObject uplink(JsonObject uplink, Scope keys, boolean hideNumbers) {
        String uplinkId = str(uplink.get("uplinkId"));
        if (uplinkId == null || !(uplink.get("widgets") instanceof JsonArray widgets)) return null;
        // v0.5 restricted sections (server, claims, backups) are never shared.
        if (uplink.get("restricted") != null && uplink.get("restricted").isJsonPrimitive() && uplink.get("restricted").getAsBoolean()) return null;
        var visible = new JsonArray();
        for (var e : widgets) {
            if (e instanceof JsonObject w && widgetVisible(uplinkId, w, keys, hideNumbers)) visible.add(w.deepCopy());
        }
        if (visible.isEmpty()) return null;
        var copy = new JsonObject();
        for (var entry : uplink.entrySet()) {
            // Only what the dashboard needs to render cards; script modes and bridge details are not shared.
            switch (entry.getKey()) {
                case "uplinkId", "title", "online", "pages", "groups" -> copy.add(entry.getKey(), entry.getValue().deepCopy());
                default -> {
                }
            }
        }
        copy.add("widgets", visible);
        return copy;
    }

    /**
     * Filters one outgoing hub message for a share session. Returns the (possibly rewritten) message or null to drop it.
     *
     * @param uplinks the hub's current uplink snapshots by id
     */
    public static @Nullable JsonObject message(JsonObject message, Map<String, JsonObject> uplinks, Scope keys, boolean hideNumbers) {
        String t = str(message.get("t"));
        if (t == null) return null;
        switch (t) {
            case "state": {
                var state = new JsonObject();
                state.addProperty("t", "state");
                state.add("online", message.get("online"));
                state.add("enabled", message.get("enabled"));
                if (message.has("name")) state.add("name", message.get("name"));
                return state;
            }
            case "layout":
            case "uplink_removed":
                return message;
            case "uplink": {
                var filtered = uplink(message, keys, hideNumbers);
                if (filtered != null) {
                    filtered.addProperty("t", "uplink");
                    return filtered;
                }
                var removed = new JsonObject();
                removed.addProperty("t", "uplink_removed");
                removed.add("uplinkId", message.get("uplinkId"));
                return removed;
            }
            case "value": {
                String uplinkId = str(message.get("uplinkId"));
                String id = str(message.get("id"));
                var uplink = uplinkId == null ? null : uplinks.get(uplinkId);
                if (uplink == null || id == null || !(uplink.get("widgets") instanceof JsonArray widgets)) return null;
                for (var e : widgets) {
                    if (e instanceof JsonObject w && id.equals(str(w.get("id")))) return widgetVisible(uplinkId, w, keys, hideNumbers) ? message : null;
                }
                return null;
            }
            case "alert": {
                String uplinkId = str(message.get("uplinkId"));
                var uplink = uplinkId == null ? null : uplinks.get(uplinkId);
                return uplink != null && uplink(uplink, keys, hideNumbers) != null ? message : null;
            }
            default:
                return null; // network, role, results … never reach share sessions
        }
    }

    private static @Nullable String str(@Nullable JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && !e.getAsString().isEmpty() ? e.getAsString() : null;
    }
}
