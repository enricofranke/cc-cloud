package ai.ezfox.cccloud.peripheral;

import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Turns published sections into the Lua tables of {@code cloud_mainframe.getCards()} and {@code getCard(id)}. It gets
 * what {@code WebHub.viewerSections} hands out (a viewer's view) and only passes on a fixed set of fields: ids, titles,
 * what a widget is and its value. Canvas frames stay out (no use for a program, and big).
 */
final class CardReader {
    static final int MAX_CARDS = 256;
    private static final Set<String> OPTIONS = Set.of("unit", "min", "max", "warn", "crit");

    private CardReader() {
    }

    static @Nullable String id(JsonObject section) {
        return text(section, "uplinkId");
    }

    static Map<Integer, Object> list(List<JsonObject> sections) {
        var out = new LinkedHashMap<Integer, Object>();
        for (var section : sections) {
            if (out.size() >= MAX_CARDS) break;
            var row = head(section);
            row.put("widgets", section.get("widgets") instanceof JsonArray w ? (double) w.size() : 0d);
            out.put(out.size() + 1, row);
        }
        return out;
    }

    static Map<String, Object> card(JsonObject section) {
        var card = head(section);
        var widgets = new LinkedHashMap<Integer, Object>();
        if (section.get("widgets") instanceof JsonArray list) {
            for (var element : list) {
                if (element instanceof JsonObject w) widgets.put(widgets.size() + 1, widget(w));
            }
        }
        card.put("widgets", widgets);
        return card;
    }

    private static Map<String, Object> head(JsonObject section) {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", id(section));
        String title = text(section, "title");
        row.put("title", title == null ? "" : title);
        row.put("online", section.get("online") instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean());
        String template = text(section, "template");
        row.put("kind", template != null ? template : "lua");
        if (section.has("blocked")) row.put("blocked", true);
        return row;
    }

    private static Map<String, Object> widget(JsonObject w) {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", text(w, "id"));
        String type = text(w, "type");
        row.put("type", type);
        if (text(w, "label") != null) row.put("label", text(w, "label"));
        if (w.get("opts") instanceof JsonObject opts) {
            for (var key : OPTIONS) {
                if (opts.get(key) instanceof JsonPrimitive) row.put(key, Json.toLua(opts.get(key)));
            }
        }
        JsonElement value = w.get("value");
        if (value != null && !"canvas".equals(type)) {
            var lua = Json.toLua(value);
            if (lua != null) row.put("value", lua);
        }
        return row;
    }

    private static @Nullable String text(JsonObject o, String key) {
        return o.get(key) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }
}
