package ai.ezfox.cccloud.code.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Turns what the dashboard hub publishes (section JSON, the network map) into a {@link WorldSnapshot}. Only plain
 * JSON in, so it runs on any thread and in unit tests. The fields are the ones {@code cloud_mainframe.getCard} gives
 * a computer: ids, titles, what a widget is, its value and limits; canvas frames stay out.
 */
public final class SnapshotBuilder {
    public static final int MAX_CARDS = 256;
    /** Structured widget values (lists, jobs) are cut to this many entries per level and this depth. */
    static final int MAX_ENTRIES = 256;
    static final int MAX_DEPTH = 4;

    private SnapshotBuilder() {
    }

    /**
     * @param sections   published sections in the form the script may see (restricted ones already left out or kept)
     * @param restricted section ids that need {@code view.positions}; dropped unless {@code seesRestricted}
     */
    public static List<WorldSnapshot.Card> cards(Iterable<JsonObject> sections, Set<String> restricted, boolean seesRestricted) {
        var out = new ArrayList<WorldSnapshot.Card>();
        for (var section : sections) {
            if (out.size() >= MAX_CARDS) break;
            String id = text(section, "uplinkId");
            if (id == null || (!seesRestricted && restricted.contains(id))) continue;
            out.add(card(section, id));
        }
        return out;
    }

    static WorldSnapshot.Card card(JsonObject section, String id) {
        String title = text(section, "title");
        String template = text(section, "template");
        var widgets = new ArrayList<WorldSnapshot.Widget>();
        if (section.get("widgets") instanceof JsonArray list) {
            for (var element : list) {
                if (element instanceof JsonObject w && text(w, "id") != null) widgets.add(widget(w));
            }
        }
        return new WorldSnapshot.Card(id, title == null ? "" : title, flag(section, "online"), template != null ? template : "lua", List.copyOf(widgets),
            section.has("blocked"));
    }

    static WorldSnapshot.Widget widget(JsonObject w) {
        String type = text(w, "type");
        var opts = w.get("opts") instanceof JsonObject o ? o : new JsonObject();
        Object value = null;
        if (w.get("value") != null && !"canvas".equals(type)) value = toJava(w.get("value"), 0);
        return new WorldSnapshot.Widget(text(w, "id"), type == null ? "label" : type, text(w, "label"), value, text(opts, "unit"), number(opts, "min"),
            number(opts, "max"), flag(opts, "pin"), flag(opts, "confirm"));
    }

    /** The network map's nodes as devices (the mainframe itself left out); uplinks and bridges under their card id. */
    public static List<WorldSnapshot.Device> devices(@Nullable JsonObject network) {
        var out = new ArrayList<WorldSnapshot.Device>();
        if (network == null || !(network.get("nodes") instanceof JsonArray nodes)) return out;
        for (var element : nodes) {
            if (!(element instanceof JsonObject node)) continue;
            String type = text(node, "type");
            if (type == null || type.equals("mainframe")) continue;
            String id = text(node, "uplinkId");
            if (id == null) id = text(node, "id");
            if (id == null) continue;
            String name = text(node, "name");
            String status = text(node, "status");
            out.add(new WorldSnapshot.Device(id, type, name == null ? "" : name, status == null ? "offline" : status));
            if (out.size() >= MAX_CARDS) break;
        }
        return out;
    }

    /** JSON → null, Boolean, Double, String, List, Map (cut to {@link #MAX_ENTRIES}/{@link #MAX_DEPTH}). */
    public static @Nullable Object toJava(@Nullable JsonElement e, int depth) {
        if (e == null || e.isJsonNull()) return null;
        if (e instanceof JsonPrimitive p) {
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsDouble();
            return p.getAsString();
        }
        if (depth >= MAX_DEPTH) return null;
        if (e instanceof JsonArray a) {
            var list = new ArrayList<Object>(Math.min(a.size(), MAX_ENTRIES));
            for (var item : a) {
                if (list.size() >= MAX_ENTRIES) break;
                var v = toJava(item, depth + 1);
                if (v != null) list.add(v);
            }
            return List.copyOf(list);
        }
        var map = new LinkedHashMap<String, Object>();
        for (var entry : e.getAsJsonObject().entrySet()) {
            if (map.size() >= MAX_ENTRIES) break;
            var v = toJava(entry.getValue(), depth + 1);
            if (v != null) map.put(entry.getKey(), v);
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    static @Nullable String text(JsonObject o, String key) {
        return o.get(key) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    static boolean flag(JsonObject o, String key) {
        return o.get(key) instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
    }

    static @Nullable Double number(JsonObject o, String key) {
        return o.get(key) instanceof JsonPrimitive p && p.isNumber() ? p.getAsDouble() : null;
    }
}
