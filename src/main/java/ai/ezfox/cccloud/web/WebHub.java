package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Thread-safe bridge between the server thread and the web clients.
 * <p>
 * The server thread calls the {@code publish*} methods. They only update an in-memory model and put the change into a
 * per-mainframe outbox (coalesced by key, so a widget that changes every tick produces one message per flush). They
 * never block and never touch the network. {@link #flush()} runs on a Netty thread every 100 ms, serialises the outbox
 * and hands the messages to the sessions, so every uplink is broadcast at most 10 times per second.
 */
public final class WebHub {
    public static final WebHub INSTANCE = new WebHub();
    private static final int MAX_PENDING_ALERTS = 16;

    public record MainframeInfo(UUID id, String name, boolean online, boolean enabled) {
    }

    private static final class View {
        final UUID id;
        String name = "";
        boolean online;
        boolean enabled;
        /** uplinkId → snapshot ({uplinkId,title,online,widgets[]}) */
        final Map<String, JsonObject> uplinks = new LinkedHashMap<>();
        /** uplinkId → widget id → widget json (points into the snapshot above) */
        final Map<String, Map<String, JsonObject>> widgetIndex = new ConcurrentHashMap<>();
        Map<String, JsonObject> outbox = new LinkedHashMap<>();
        int alertSeq;
        /** Power of the mainframe: {stored, capacity, usage, ok}; reason why it is offline (or null). */
        @Nullable JsonObject power;
        @Nullable String reason;
        /** Shared dashboard layout (validated), null = never edited. */
        @Nullable JsonObject layout;
        /** 5 layout saves per 10 s per mainframe. */
        final TokenBucket layoutSaves = new TokenBucket(5, 0.5);
        /** Millis when the last client left, 0 = never had a client. */
        volatile long lastClientSeen;
        final Set<HubSession> sessions = ConcurrentHashMap.newKeySet();

        View(UUID id) {
            this.id = id;
        }

        JsonObject mainframeJson() {
            var json = new JsonObject();
            json.addProperty("id", id.toString());
            json.addProperty("name", name);
            json.addProperty("online", online);
            json.addProperty("enabled", enabled);
            if (power != null) json.add("power", power.deepCopy());
            if (reason != null) json.addProperty("reason", reason);
            return json;
        }
    }

    private final Map<UUID, View> views = new ConcurrentHashMap<>();

    private View view(UUID id) {
        return views.computeIfAbsent(id, View::new);
    }

    // ---- server thread ---------------------------------------------------------------------------------------------

    public void publishMainframe(UUID id, String name, boolean online, boolean enabled) {
        var view = view(id);
        synchronized (view) {
            if (view.name.equals(name) && view.online == online && view.enabled == enabled) return;
            boolean renamed = !view.name.equals(name);
            view.name = name;
            view.online = online;
            view.enabled = enabled;
            var state = new JsonObject();
            state.addProperty("t", "state");
            state.addProperty("online", online);
            state.addProperty("enabled", enabled);
            if (renamed) state.addProperty("name", name);
            put(view, "state", state);
            if (!online || !enabled) {
                // Uplinks cannot be online without their mainframe (also covers uplinks in unloaded chunks).
                for (var entry : view.uplinks.entrySet()) {
                    var uplink = entry.getValue();
                    if (!uplink.has("online") || !uplink.get("online").getAsBoolean()) continue;
                    uplink.addProperty("online", false);
                    var message = uplink.deepCopy();
                    message.addProperty("t", "uplink");
                    view.outbox.keySet().removeIf(k -> k.startsWith("v:" + entry.getKey() + ":"));
                    put(view, "u:" + entry.getKey(), message);
                }
            }
        }
    }

    /** True if the power ok flag differs from what the hub last published (so the change goes out immediately). */
    public boolean powerOkChanged(UUID mainframeId, boolean ok) {
        var view = views.get(mainframeId);
        if (view == null) return true;
        synchronized (view) {
            return view.power == null || view.power.get("ok").getAsBoolean() != ok;
        }
    }

    /** Test hook: the published power object. */
    public @Nullable JsonObject powerForTest(UUID mainframeId) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            return view.power == null ? null : view.power.deepCopy();
        }
    }

    /** Mainframe power for the dashboard header: {@code {"t":"state", ..., "power":{stored,capacity,usage,ok}, "reason"}}. */
    public void publishPower(UUID mainframeId, long stored, long capacity, long usage, boolean ok, @Nullable String reason) {
        var view = view(mainframeId);
        synchronized (view) {
            var power = new JsonObject();
            power.addProperty("stored", stored);
            power.addProperty("capacity", capacity);
            power.addProperty("usage", usage);
            power.addProperty("ok", ok);
            if (power.equals(view.power) && java.util.Objects.equals(reason, view.reason)) return;
            view.power = power;
            view.reason = reason;
            var state = new JsonObject();
            state.addProperty("t", "state");
            state.addProperty("online", view.online);
            state.addProperty("enabled", view.enabled);
            state.add("power", power.deepCopy());
            state.add("reason", reason == null ? com.google.gson.JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(reason));
            put(view, "state", state);
        }
    }

    public void publishUplink(UUID mainframeId, JsonObject snapshot) {
        var view = view(mainframeId);
        String uplinkId = snapshot.get("uplinkId").getAsString();
        var copy = snapshot.deepCopy();
        Map<String, JsonObject> index = new ConcurrentHashMap<>();
        for (var w : copy.getAsJsonArray("widgets")) {
            var widget = w.getAsJsonObject();
            index.put(widget.get("id").getAsString(), widget);
        }
        synchronized (view) {
            view.uplinks.put(uplinkId, copy);
            view.widgetIndex.put(uplinkId, index);
            // The snapshot already contains the latest values.
            view.outbox.keySet().removeIf(k -> k.startsWith("v:" + uplinkId + ":"));
            var message = copy.deepCopy();
            message.addProperty("t", "uplink");
            put(view, "u:" + uplinkId, message);
        }
    }

    public void publishValue(UUID mainframeId, String uplinkId, String widgetId, JsonElement value) {
        var view = views.get(mainframeId);
        if (view == null) return;
        synchronized (view) {
            var index = view.widgetIndex.get(uplinkId);
            var widget = index == null ? null : index.get(widgetId);
            if (widget == null) return;
            widget.add("value", value);
            appendHistory(widget, value);
            var message = new JsonObject();
            message.addProperty("t", "value");
            message.addProperty("uplinkId", uplinkId);
            message.addProperty("id", widgetId);
            message.add("value", value);
            view.outbox.put("v:" + uplinkId + ":" + widgetId, message);
        }
    }

    /**
     * Charts: a number appends one point to the snapshot history (trimmed to opts.points), an array replaces it.
     */
    static void appendHistory(JsonObject widget, JsonElement value) {
        var history = widget.get("history");
        if (history == null || !history.isJsonArray()) return;
        if (value.isJsonArray()) {
            widget.add("history", value.deepCopy());
            return;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return;
        var array = history.getAsJsonArray();
        array.add(value);
        int points = 60;
        var opts = widget.get("opts");
        if (opts != null && opts.isJsonObject() && opts.getAsJsonObject().has("points")) points = opts.getAsJsonObject().get("points").getAsInt();
        while (array.size() > points) array.remove(0);
    }

    /** Loads a stored layout without broadcasting it (server start). */
    public void setLayout(UUID mainframeId, @Nullable JsonObject layout) {
        var view = view(mainframeId);
        synchronized (view) {
            view.layout = layout == null ? null : layout.deepCopy();
        }
    }

    /** Stores a new layout and sends {"t":"layout"} to every client of the mainframe. */
    public void publishLayout(UUID mainframeId, @Nullable JsonObject layout) {
        var view = view(mainframeId);
        synchronized (view) {
            view.layout = layout == null ? null : layout.deepCopy();
            var message = new JsonObject();
            message.addProperty("t", "layout");
            message.add("layout", layout == null ? com.google.gson.JsonNull.INSTANCE : layout.deepCopy());
            put(view, "layout", message);
        }
    }

    /** Rate limit for layout_set: 5 saves per 10 s per mainframe. */
    public boolean tryLayoutSave(UUID mainframeId) {
        return view(mainframeId).layoutSaves.tryAcquire();
    }

    public @Nullable JsonObject layout(UUID mainframeId) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            return view.layout == null ? null : view.layout.deepCopy();
        }
    }

    /** Type id of a widget as published to the browsers, or null. */
    public @Nullable String widgetType(UUID mainframeId, String uplinkId, String widgetId) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            var index = view.widgetIndex.get(uplinkId);
            var widget = index == null ? null : index.get(widgetId);
            return widget == null || !widget.has("type") ? null : widget.get("type").getAsString();
        }
    }

    /** Number of connected dashboard clients of a mainframe. */
    public int clientCount(UUID mainframeId) {
        var view = views.get(mainframeId);
        return view == null ? 0 : view.sessions.size();
    }

    public void publishUplinkRemoved(UUID mainframeId, String uplinkId) {
        var view = views.get(mainframeId);
        if (view == null) return;
        synchronized (view) {
            if (view.uplinks.remove(uplinkId) == null) return;
            view.widgetIndex.remove(uplinkId);
            view.outbox.keySet().removeIf(k -> k.startsWith("v:" + uplinkId + ":"));
            var message = new JsonObject();
            message.addProperty("t", "uplink_removed");
            message.addProperty("uplinkId", uplinkId);
            put(view, "u:" + uplinkId, message);
        }
    }

    public void publishAlert(UUID mainframeId, String uplinkId, String text, String level) {
        var view = views.get(mainframeId);
        if (view == null) return;
        synchronized (view) {
            var message = new JsonObject();
            message.addProperty("t", "alert");
            message.addProperty("uplinkId", uplinkId);
            message.addProperty("text", text);
            message.addProperty("level", level);
            message.addProperty("ts", System.currentTimeMillis());
            long alerts = view.outbox.keySet().stream().filter(k -> k.startsWith("a:")).count();
            if (alerts >= MAX_PENDING_ALERTS) return;
            view.outbox.put("a:" + (view.alertSeq++), message);
        }
    }

    /** Forgets a mainframe (it was broken) and disconnects its clients. */
    public void removeMainframe(UUID mainframeId) {
        var view = views.remove(mainframeId);
        if (view == null) return;
        for (var session : view.sessions) session.close(4404, "mainframe removed");
    }

    /** Disconnects the sessions of a mainframe; if tokenHash is non-null only that device. */
    public void closeSessions(UUID mainframeId, @Nullable String tokenHash) {
        var view = views.get(mainframeId);
        if (view == null) return;
        for (var session : view.sessions) {
            var device = session.device();
            if (tokenHash == null || (device != null && device.tokenHash().equals(tokenHash))) session.close(4401, "revoked");
        }
    }

    // ---- any thread --------------------------------------------------------------------------------------------------

    public @Nullable MainframeInfo info(UUID mainframeId) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            return new MainframeInfo(view.id, view.name, view.online, view.enabled);
        }
    }

    public boolean hasUplink(UUID mainframeId, String uplinkId) {
        var view = views.get(mainframeId);
        return view != null && view.widgetIndex.containsKey(uplinkId);
    }

    /** Registers a session and sends it the full snapshot. */
    public void addSession(HubSession session) {
        var view = view(session.mainframeId());
        String snapshot;
        synchronized (view) {
            view.sessions.add(session);
            var json = new JsonObject();
            json.addProperty("t", "snapshot");
            var mainframe = view.mainframeJson();
            var device = session.device();
            boolean canControl = device == null || device.canControl();
            mainframe.addProperty("canControl", canControl);
            json.add("mainframe", mainframe);
            json.addProperty("canControl", canControl);
            var uplinks = new JsonArray();
            for (var uplink : view.uplinks.values()) uplinks.add(uplink);
            json.add("uplinks", uplinks);
            json.add("layout", view.layout == null ? com.google.gson.JsonNull.INSTANCE : view.layout);
            snapshot = Json.GSON.toJson(json);
        }
        session.send(List.of(snapshot));
    }

    public void removeSession(HubSession session) {
        var view = views.get(session.mainframeId());
        if (view != null && view.sessions.remove(session)) view.lastClientSeen = System.currentTimeMillis();
    }

    /** All connected sessions (for periodic rights checks). */
    public List<HubSession> sessions() {
        var list = new ArrayList<HubSession>();
        for (var view : views.values()) list.addAll(view.sessions);
        return list;
    }

    /**
     * Seconds since an authorized dashboard client was last connected to this mainframe: 0 while one is connected,
     * null if none ever was (since the server started).
     */
    public @Nullable Long secondsSinceClient(UUID mainframeId) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        if (!view.sessions.isEmpty()) return 0L;
        long last = view.lastClientSeen;
        return last == 0 ? null : (System.currentTimeMillis() - last) / 1000;
    }

    /** True if the widget exists and requires a PIN (opts.pin = true). */
    public boolean widgetNeedsPin(UUID mainframeId, String uplinkId, String widgetId) {
        var view = views.get(mainframeId);
        if (view == null) return false;
        synchronized (view) {
            var index = view.widgetIndex.get(uplinkId);
            var widget = index == null ? null : index.get(widgetId);
            if (widget == null || !widget.has("opts")) return false;
            var pin = widget.getAsJsonObject("opts").get("pin");
            return pin != null && pin.isJsonPrimitive() && pin.getAsJsonPrimitive().isBoolean() && pin.getAsBoolean();
        }
    }

    public int sessionCount() {
        int count = 0;
        for (var view : views.values()) count += view.sessions.size();
        return count;
    }

    /** Sends all pending changes. Called periodically from a Netty thread. */
    public void flush() {
        for (var view : views.values()) {
            Map<String, JsonObject> pending;
            synchronized (view) {
                if (view.outbox.isEmpty()) continue;
                pending = view.outbox;
                view.outbox = new LinkedHashMap<>();
            }
            if (view.sessions.isEmpty()) continue;
            var messages = new ArrayList<String>(pending.size());
            for (var message : pending.values()) messages.add(Json.GSON.toJson(message));
            for (var session : view.sessions) session.send(messages);
        }
    }

    public void clear() {
        for (var view : views.values()) {
            for (var session : view.sessions) session.close(1001, "server stopping");
        }
        views.clear();
    }

    private static void put(View view, String key, JsonObject message) {
        view.outbox.remove(key);
        view.outbox.put(key, message);
    }
}
