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
 * Validates the dashboard layout a browser sends with {@code layout_set} (docs/SPEC.md "Layout schema", reference:
 * {@code layoutDoc} in cloud/dev/protocol.ts). Strict: unknown keys, wrong types and out-of-range values reject the
 * whole document; nothing is silently fixed. Pure Java, unit tested.
 * <p>
 * Card and widget keys of uplinks/widgets that do not exist (any more) are accepted on purpose: the layout must survive
 * script restarts and unloaded chunks, and the dashboard simply skips unknown ids.
 */
public final class LayoutValidator {
    public static final int MAX_BYTES = 64 * 1024;
    public static final int MAX_PAGES = 12;
    public static final int MAX_CARDS = 256;
    public static final int MAX_WIDGETS = 2048;
    public static final int MAX_KEY = 160;
    public static final int MAX_ORDER = 10_000;
    public static final Set<String> ACCENTS = Set.of("gold", "cyan", "green", "yellow", "red", "purple", "orange", "gray");
    private static final Pattern PAGE_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");

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
            if (integer(layout.get("v"), "v", 1, 1) != 1) throw new Invalid("v");

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
                keys(card, path, Set.of("page", "order", "span", "title", "hidden", "color", "icon"), false);
                if (card.has("page") && string(card.get("page"), path + ".page").length() > 32) throw new Invalid(path + ".page");
                common(card, path, "title", Limits.TITLE);
                if (card.has("color") && !ACCENTS.contains(string(card.get("color"), path + ".color"))) throw new Invalid(path + ".color");
                if (card.has("icon")) icon(card.get("icon"), path + ".icon");
            }

            var widgets = map(layout, "widgets", MAX_WIDGETS);
            for (var entry : widgets.entrySet()) {
                String path = "widgets[" + entry.getKey() + "]";
                var widget = object(entry.getValue(), path);
                keys(widget, path, Set.of("order", "span", "label", "hidden"), false);
                common(widget, path, "label", Limits.LABEL);
            }
        } catch (Invalid e) {
            return new Result(null, "invalid_layout", e.getMessage());
        } catch (RuntimeException e) {
            return new Result(null, "invalid_layout", "layout");
        }
        return new Result(layout.deepCopy(), null, null);
    }

    private static void common(JsonObject obj, String path, String textKey, int maxText) throws Invalid {
        if (obj.has("order")) integer(obj.get("order"), path + ".order", 0, MAX_ORDER);
        if (obj.has("span")) integer(obj.get("span"), path + ".span", 1, 4);
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
