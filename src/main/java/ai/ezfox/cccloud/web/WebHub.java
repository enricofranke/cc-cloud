package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * per-mainframe outbox (values are coalesced per widget, so a widget that changes every tick produces one entry per
 * flush). They never block on the network and never serialise big documents. {@link #flush()} runs on a Netty thread
 * every 100 ms, serialises the outbox once and hands the messages to the sessions: structural messages in order, then
 * one {@code {"t":"values","s":{uplinkId:{widgetId:value}}}} with the latest value of every changed widget.
 * <p>
 * Scaling (docs/SPEC.md "Scaling"): published sections and widgets are copy-on-write, so a snapshot only collects
 * references under the lock and is serialised outside it (cached per section), and a session that is waiting for its
 * snapshot buffers the deltas of later flushes, so it never sees a delta before its snapshot.
 */
public final class WebHub {
    public static final WebHub INSTANCE = new WebHub();
    private static final int MAX_PENDING_ALERTS = 16;

    public record MainframeInfo(UUID id, String name, boolean online, boolean enabled) {
    }

    /**
     * A published widget: its definition (never mutated, serialised once), its current value and a chart's history.
     * Immutable: a new value is a new {@code W} (no JSON tree is copied or changed), so snapshots can be serialised on
     * another thread while the server thread keeps publishing.
     */
    static final class W {
        final String id;
        final JsonObject base;
        private final String baseText;
        final @Nullable JsonElement value;
        /**
         * Private values (Advanced Cloud Sensor: player names): what sessions get that may not see them (viewers, share
         * links, Cloud Displays, see {@link #seesNames}); null = the value is not private.
         */
        final @Nullable JsonElement pub;
        final @Nullable JsonArray history;
        final int points;
        private @Nullable String historyText;

        private W(String id, JsonObject base, String baseText, @Nullable JsonElement value, @Nullable JsonElement pub, @Nullable JsonArray history,
                  int points) {
            this.id = id;
            this.base = base;
            this.baseText = baseText;
            this.value = value;
            this.pub = pub;
            this.history = history;
            this.points = points;
        }

        /** From a published widget object (taken over: it must not change any more). */
        static W of(JsonObject widget) {
            var base = new JsonObject();
            JsonElement value = null;
            JsonElement pub = null;
            JsonArray history = null;
            for (var e : widget.entrySet()) {
                switch (e.getKey()) {
                    case "value" -> value = e.getValue();
                    case "publicValue" -> pub = e.getValue();
                    case "history" -> {
                        if (e.getValue() instanceof JsonArray a) history = a;
                        else base.add(e.getKey(), e.getValue());
                    }
                    default -> base.add(e.getKey(), e.getValue());
                }
            }
            int points = 60;
            if (base.get("opts") instanceof JsonObject o && o.get("points") instanceof com.google.gson.JsonPrimitive p && p.isNumber()) points = p.getAsInt();
            String text = Json.GSON.toJson(base);
            return new W(base.get("id").getAsString(), base, text.substring(0, text.length() - 1), value, pub, history, points);
        }

        @Nullable String type() {
            return base.get("type") instanceof com.google.gson.JsonPrimitive p ? p.getAsString() : null;
        }

        /** With a new value: a number appends one point to a chart's history (trimmed to opts.points), an array replaces it. */
        W with(JsonElement newValue) {
            return with(newValue, null);
        }

        /** As above, with the public form of a private value (null = not private). */
        W with(JsonElement newValue, @Nullable JsonElement newPub) {
            var h = history;
            if (h != null) {
                if (newValue.isJsonArray()) {
                    h = newValue.getAsJsonArray().deepCopy();
                } else if (newValue.isJsonPrimitive() && newValue.getAsJsonPrimitive().isNumber()) {
                    int from = Math.max(0, h.size() + 1 - points);
                    var array = new JsonArray(Math.max(1, Math.min(points, h.size() + 1)));
                    for (int i = from; i < h.size(); i++) array.add(h.get(i));
                    array.add(newValue);
                    h = array;
                }
            }
            return new W(id, base, baseText, newValue, newPub, h, points);
        }

        /** The value a session gets: the public form of a private value unless it may see names. */
        @Nullable JsonElement value(boolean names) {
            return names || pub == null ? value : pub;
        }

        void write(StringBuilder sb) {
            write(sb, true);
        }

        void write(StringBuilder sb, boolean names) {
            sb.append(baseText);
            var v = value(names);
            if (v != null) sb.append(",\"value\":").append(Json.GSON.toJson(v));
            if (history != null) {
                var text = historyText;
                if (text == null) historyText = text = Json.GSON.toJson(history);
                sb.append(",\"history\":").append(text);
            }
            sb.append('}');
        }

        /** As a widget object (shares the immutable parts; callers that hand it out copy it). */
        JsonObject json() {
            return json(true);
        }

        JsonObject json(boolean names) {
            var o = new JsonObject();
            for (var e : base.entrySet()) o.add(e.getKey(), e.getValue());
            var v = value(names);
            if (v != null) o.add("value", v);
            if (history != null) o.add("history", history);
            return o;
        }
    }

    /**
     * A published section: head (everything but the widgets, never mutated) and its widgets. Immutable, see {@link W}.
     * <p>
     * Private parts have a public form for sessions without {@link #seesNames} (viewers, share links, Cloud Displays, the
     * tablet, API keys without {@code view.positions}): widget values with player names (Advanced Cloud Sensor) and the
     * team of a claim in the head (claim guard: {@code blocked.team}, {@code blockedWidgets.*.team}).
     */
    static final class Section {
        final JsonObject head;
        private final String headText;
        /** The head without claim team names, or null when the head names none. */
        private final @Nullable JsonObject publicHead;
        private final String publicHeadText;
        final W[] widgets;
        /** widget id → index; shared by every version of the same structure. */
        final Map<String, Integer> pos;
        /** widget id → {@code opts.history} range of the widgets with long-term statistics (shared, never changed). */
        final Map<String, String> history;
        private volatile @Nullable String text;
        private volatile @Nullable JsonObject json;
        /** The public form (private values replaced), built once per version; only for sections with private values. */
        private volatile @Nullable String publicText;
        private volatile @Nullable JsonObject publicJson;

        private Section(JsonObject head, String headText, @Nullable JsonObject publicHead, String publicHeadText, W[] widgets, Map<String, Integer> pos,
                        Map<String, String> history) {
            this.head = head;
            this.headText = headText;
            this.publicHead = publicHead;
            this.publicHeadText = publicHeadText;
            this.widgets = widgets;
            this.pos = pos;
            this.history = history;
        }

        private static Section of(JsonObject head, W[] widgets, Map<String, Integer> pos, Map<String, String> history) {
            var publicHead = ai.ezfox.cccloud.claims.ClaimGuard.withoutTeams(head);
            String headText = open(head);
            return new Section(head, headText, publicHead, publicHead == null ? headText : open(publicHead), widgets, pos, history);
        }

        private static String open(JsonObject head) {
            String text = Json.GSON.toJson(head);
            return text.substring(0, text.length() - 1);
        }

        /** From a published section snapshot (taken over: it must not change any more). */
        static Section of(JsonObject snapshot) {
            var head = new JsonObject();
            for (var e : snapshot.entrySet()) if (!e.getKey().equals("widgets")) head.add(e.getKey(), e.getValue());
            var list = snapshot.get("widgets") instanceof JsonArray a ? a : new JsonArray();
            var widgets = new W[list.size()];
            var pos = new HashMap<String, Integer>();
            var history = new LinkedHashMap<String, String>();
            for (int i = 0; i < widgets.length; i++) {
                var w = W.of(list.get(i).getAsJsonObject());
                widgets[i] = w;
                pos.put(w.id, i);
                if (w.base.get("opts") instanceof JsonObject opts && opts.get("history") instanceof com.google.gson.JsonPrimitive h && h.isString()) {
                    history.put(w.id, h.getAsString());
                }
            }
            return of(head, widgets, Map.copyOf(pos), history.isEmpty() ? Map.of() : java.util.Collections.unmodifiableMap(history));
        }

        Section withWidgets(W[] widgets) {
            return new Section(head, headText, publicHead, publicHeadText, widgets, pos, history);
        }

        Section withHead(JsonObject head) {
            return of(head, widgets, pos, history);
        }

        @Nullable W widget(String id) {
            var i = pos.get(id);
            return i == null ? null : widgets[i];
        }

        boolean flag(String key) {
            return head.get(key) instanceof com.google.gson.JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
        }

        @Nullable String str(String key) {
            return head.get(key) instanceof com.google.gson.JsonPrimitive p ? p.getAsString() : null;
        }

        /** The section as JSON text, built once per version and shared by every snapshot. */
        String text() {
            var t = text;
            if (t == null) text = t = build(true);
            return t;
        }

        /**
         * Has private parts (widget values with player names, a claim's team in the head): sessions that may not see
         * them get {@link #text(boolean)} false.
         */
        boolean hasPrivate() {
            if (publicHead != null) return true;
            for (var w : widgets) if (w.pub != null) return true;
            return false;
        }

        /** The text a session gets: with names, or the public form (the same text for sections without private values). */
        String text(boolean names) {
            if (names || !hasPrivate()) return text();
            var t = publicText;
            if (t == null) publicText = t = build(false);
            return t;
        }

        private String build(boolean names) {
            String headPart = names ? headText : publicHeadText;
            var sb = new StringBuilder(headPart.length() + widgets.length * 160);
            sb.append(headPart).append(head.size() > 0 ? "," : "").append("\"widgets\":[");
            for (int i = 0; i < widgets.length; i++) {
                if (i > 0) sb.append(',');
                widgets[i].write(sb, names);
            }
            return sb.append("]}").toString();
        }

        /** The JSON a session gets (see {@link #text(boolean)}); shared, never change it. */
        JsonObject json(boolean names) {
            if (names || !hasPrivate()) return json();
            var j = publicJson;
            if (j == null) {
                j = new JsonObject();
                for (var e : (publicHead != null ? publicHead : head).entrySet()) j.add(e.getKey(), e.getValue());
                var array = new JsonArray(widgets.length);
                for (var w : widgets) array.add(w.json(false));
                j.add("widgets", array);
                publicJson = j;
            }
            return j;
        }

        /** The section as a JSON object, built once per version (shared: never change it, copy it to hand it out). */
        JsonObject json() {
            var j = json;
            if (j == null) {
                j = new JsonObject();
                for (var e : head.entrySet()) j.add(e.getKey(), e.getValue());
                var array = new JsonArray(widgets.length);
                for (var w : widgets) array.add(w.json());
                j.add("widgets", array);
                json = j;
            }
            return j;
        }
    }

    private static final class View {
        final UUID id;
        String name = "";
        boolean online;
        boolean enabled;
        /** uplinkId → published section (immutable; replaced on every change). */
        final Map<String, Section> uplinks = new LinkedHashMap<>();
        /** Ids of the published sections; read without the lock by hasUplink. */
        final Set<String> sectionIds = ConcurrentHashMap.newKeySet();
        /** uplinkIds of the sections with long-term statistics (their widgets are in {@link Section#history}). */
        final Set<String> historySections = new LinkedHashSet<>();
        /** v0.5: sections only sent to devices with canControl (snapshot {@code "restricted":true}, e.g. the Server card). */
        final Set<String> restricted = new java.util.HashSet<>();
        /** Structural messages (sections, state, layout, alerts …), coalesced by key, in order. */
        Map<String, JsonObject> outbox = new LinkedHashMap<>();
        /** Public forms of outbox messages with private values (alerts with player names), by outbox key. */
        Map<String, JsonObject> publicOutbox = new LinkedHashMap<>();
        /** uplinkId → ids of widgets whose value changed since the last flush. */
        Map<String, Set<String>> values = new LinkedHashMap<>();
        int pendingAlerts;
        int alertSeq;
        /** Power of the mainframe: {stored, capacity, usage, ok}; reason why it is offline (or null). */
        @Nullable JsonObject power;
        @Nullable String reason;
        /** Removed mainframe (block broken, data kept): millis when its data is deleted, 0 = not removed. */
        long removedUntil;
        /** v0.4 network map: full (with positions, canControl) and without positions; sent at most 2x per second. */
        @Nullable JsonObject networkFullJson;
        @Nullable JsonObject networkStrippedJson;
        /** Serialised network messages of the objects above (built lazily on a Netty thread). */
        volatile @Nullable String networkFull;
        volatile @Nullable String networkStripped;
        boolean networkDirty;
        long networkSentAt;
        /** v0.5 rules of the mainframe ({rules, log, limit}), null = none yet; never sent to share sessions. */
        @Nullable JsonObject rules;
        /** v0.7 wireless redstone channels ({channels, limit}), null = none yet; never sent to share sessions. */
        @Nullable JsonObject redstone;
        /** v0.10 scenes of the mainframe ({scenes, log, limit, sectionId}), null = none yet; never sent to share sessions. */
        @Nullable JsonObject scenes;
        /** Shared dashboard layout (validated), null = never edited. */
        @Nullable JsonObject layout;
        /** 5 layout saves per 10 s per mainframe. */
        final TokenBucket layoutSaves = new TokenBucket(5, 0.5);
        /** Millis when the last client left, 0 = never had a client. */
        volatile long lastClientSeen;
        final Set<HubSession> sessions = ConcurrentHashMap.newKeySet();
        /** Sessions whose snapshot is being serialised: deltas of later flushes wait here (guarded by {@link #sendLock}). */
        final Map<HubSession, List<String>> awaiting = new ConcurrentHashMap<>();
        /** Orders the sends to this mainframe's sessions (snapshot before the deltas that follow it). Never held with the view lock. */
        final Object sendLock = new Object();

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
            if (removedUntil > 0) json.addProperty("removedUntil", removedUntil);
            return json;
        }

        /** Drops a section's pending value deltas (a fresh section snapshot or its removal replaces them). */
        void dropValues(String uplinkId) {
            values.remove(uplinkId);
        }
    }

    private final Map<UUID, View> views = new ConcurrentHashMap<>();

    private View view(UUID id) {
        return views.computeIfAbsent(id, View::new);
    }

    // ---- copy on write -------------------------------------------------------------------------------------------------

    private static JsonObject shallowCopy(JsonObject source) {
        var copy = new JsonObject();
        for (var e : source.entrySet()) copy.add(e.getKey(), e.getValue());
        return copy;
    }

    /**
     * Sets values of one section: a new section version with new widget states (snapshots being serialised on another
     * thread keep the old version). No JSON is copied. Under the view's lock.
     */
    private static boolean setValues(View view, String uplinkId, String[] widgetIds, JsonElement[] values, int count, boolean queue) {
        return setValues(view, uplinkId, widgetIds, values, null, count, queue);
    }

    /** As above; {@code pubs} = the public forms of private values (null entries: not private). */
    private static boolean setValues(View view, String uplinkId, String[] widgetIds, JsonElement[] values, JsonElement @Nullable [] pubs, int count,
                                     boolean queue) {
        var section = view.uplinks.get(uplinkId);
        if (section == null) return false;
        W[] copy = null;
        Set<String> pending = null;
        for (int i = 0; i < count; i++) {
            var pos = section.pos.get(widgetIds[i]);
            if (pos == null || values[i] == null) continue;
            if (copy == null) copy = section.widgets.clone();
            copy[pos] = copy[pos].with(values[i], pubs == null ? null : pubs[i]);
            if (queue) {
                if (pending == null) pending = view.values.computeIfAbsent(uplinkId, k -> new LinkedHashSet<>());
                pending.add(widgetIds[i]);
            }
        }
        if (copy == null) return false;
        view.uplinks.put(uplinkId, section.withWidgets(copy));
        return true;
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
                for (var entry : new ArrayList<>(view.uplinks.entrySet())) {
                    var uplink = entry.getValue();
                    if (!uplink.flag("online")) continue;
                    var head = shallowCopy(uplink.head);
                    head.addProperty("online", false);
                    var offline = uplink.withHead(head);
                    view.uplinks.put(entry.getKey(), offline);
                    view.dropValues(entry.getKey());
                    put(view, "u:" + entry.getKey(), SECTION_PENDING);
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

    /** Network map of a mainframe; snapshots include it and changes go out as {"t":"network",...} (max 2/s). */
    public void publishNetwork(UUID mainframeId, JsonObject full, JsonObject stripped) {
        var view = view(mainframeId);
        synchronized (view) {
            if (full.equals(view.networkFullJson)) return;
            // The objects are owned by the hub from now on (never mutated); the messages are serialised at flush time.
            view.networkFullJson = full;
            view.networkStrippedJson = stripped;
            view.networkFull = null;
            view.networkStripped = null;
            view.networkDirty = true;
        }
    }

    public @Nullable JsonObject networkForTest(UUID mainframeId, boolean positions) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            return positions ? view.networkFullJson : view.networkStrippedJson;
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
    public void publishPower(UUID mainframeId, long stored, long capacity, long usage, boolean ok, @Nullable String reason, @Nullable JsonArray breakdown) {
        var view = view(mainframeId);
        synchronized (view) {
            var power = new JsonObject();
            power.addProperty("stored", stored);
            power.addProperty("capacity", capacity);
            power.addProperty("usage", usage);
            power.addProperty("ok", ok);
            if (breakdown != null) power.add("breakdown", breakdown);
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

    /**
     * A mainframe whose block was removed while its data is kept ({@code reason:"removed"}, {@code removedUntil}: millis
     * when the data is deleted), or with {@code until = 0} placed again. Dashboards stay connected and show it offline.
     */
    public void publishRemoved(UUID mainframeId, String name, long until) {
        var view = view(mainframeId);
        synchronized (view) {
            if (view.removedUntil == until && (until == 0 || "removed".equals(view.reason))) return;
            view.removedUntil = until;
            if (view.name.isEmpty()) view.name = name;
            if (until > 0) {
                view.online = false;
                view.reason = "removed";
            } else if ("removed".equals(view.reason)) {
                view.reason = null;
            }
            var state = new JsonObject();
            state.addProperty("t", "state");
            state.addProperty("online", view.online);
            state.addProperty("enabled", view.enabled);
            state.add("reason", view.reason == null ? com.google.gson.JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(view.reason));
            state.addProperty("removedUntil", until);
            put(view, "state", state);
        }
    }

    public void publishUplink(UUID mainframeId, JsonObject snapshot) {
        var view = view(mainframeId);
        String uplinkId = snapshot.get("uplinkId").getAsString();
        var copy = snapshot.deepCopy();
        // Live sections are "loaded"; cached copies of unloaded ones carry loaded=false and lastSeen.
        if (!copy.has("loaded")) copy.addProperty("loaded", true);
        var section = Section.of(copy);
        synchronized (view) {
            view.uplinks.put(uplinkId, section);
            view.sectionIds.add(uplinkId);
            if (section.history.isEmpty()) view.historySections.remove(uplinkId);
            else view.historySections.add(uplinkId);
            if (copy.has("restricted") && copy.get("restricted").getAsBoolean()) view.restricted.add(uplinkId);
            else view.restricted.remove(uplinkId);
            // The section message (built at flush time from the then current version) has the latest values.
            view.dropValues(uplinkId);
            put(view, "u:" + uplinkId, SECTION_PENDING);
        }
    }

    /** Several values of one section under one lock (block entity ticks). */
    public void publishValues(UUID mainframeId, String uplinkId, String[] widgetIds, JsonElement[] values, int count) {
        var view = views.get(mainframeId);
        if (view == null) return;
        synchronized (view) {
            setValues(view, uplinkId, widgetIds, values, count, !view.sessions.isEmpty());
        }
    }

    public void publishValue(UUID mainframeId, String uplinkId, String widgetId, JsonElement value) {
        publishValue(mainframeId, uplinkId, widgetId, value, null);
    }

    /**
     * A private value (Advanced Cloud Sensor: player names) with its public form {@code pub}, which sessions that may
     * not see names get instead ({@link #seesNames}); {@code pub} null = not private.
     */
    public void publishValue(UUID mainframeId, String uplinkId, String widgetId, JsonElement value, @Nullable JsonElement pub) {
        var view = views.get(mainframeId);
        if (view == null) return;
        synchronized (view) {
            // Nobody subscribed: new sessions get the value with their snapshot, nothing to queue.
            setValues(view, uplinkId, new String[]{widgetId}, new JsonElement[]{value}, new JsonElement[]{pub}, 1, !view.sessions.isEmpty());
        }
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
            message.add("layout", view.layout == null ? com.google.gson.JsonNull.INSTANCE : view.layout);
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

    /** Copies of all published section snapshots of a mainframe, in publish order (layout migration and checks). */
    public java.util.List<JsonObject> sectionSnapshots(UUID mainframeId) {
        return sectionSnapshots(mainframeId, true);
    }

    /** As above; {@code names} false = private values in their public form (see {@link #seesNames}). */
    public java.util.List<JsonObject> sectionSnapshots(UUID mainframeId, boolean names) {
        var list = new ArrayList<JsonObject>();
        var view = views.get(mainframeId);
        if (view == null) return list;
        List<Section> refs;
        synchronized (view) {
            refs = new ArrayList<>(view.uplinks.values());
        }
        for (var section : refs) list.add(section.json(names).deepCopy());
        return list;
    }

    /** Widget key ({@code <uplinkId>/<widgetId>}) → type of every published widget of a mainframe (without copying sections). */
    public Map<String, String> widgetTypes(UUID mainframeId) {
        var types = new HashMap<String, String>();
        var view = views.get(mainframeId);
        if (view == null) return types;
        synchronized (view) {
            for (var e : view.uplinks.entrySet()) {
                for (var w : e.getValue().widgets) {
                    var type = w.type();
                    if (type != null) types.put(e.getKey() + "/" + w.id, type);
                }
            }
        }
        return types;
    }

    /**
     * A section as JSON text (built once per version and shared; Cloud Displays poll it), or null. Public form: a display
     * stands in the world, anyone walking by reads it, so private values (player names) are never on it.
     */
    public @Nullable String sectionText(UUID mainframeId, String uplinkId) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        Section section;
        synchronized (view) {
            section = view.uplinks.get(uplinkId);
        }
        return section == null ? null : section.text(false);
    }

    /** v0.5 F: the current value of a widget that asked for long-term statistics ({@code opts.history}). */
    public record HistorySample(UUID mainframeId, String uplinkId, String widgetId, String range, double value) {
    }

    /** Numeric (or boolean as 0/1) values of every published widget with {@code opts.history}. Any thread. */
    public List<HistorySample> historySamples() {
        var out = new ArrayList<HistorySample>();
        for (var entry : views.entrySet()) {
            var view = entry.getValue();
            synchronized (view) {
                // Only the sections with widgets that asked for statistics (indexed on publish), not every widget.
                for (var uplinkId : view.historySections) {
                    var section = view.uplinks.get(uplinkId);
                    if (section == null) continue;
                    for (var widget : section.history.entrySet()) {
                        var w = section.widget(widget.getKey());
                        if (w == null || !(w.value instanceof com.google.gson.JsonPrimitive v)) continue;
                        double d = v.isNumber() ? v.getAsDouble() : v.isBoolean() ? (v.getAsBoolean() ? 1 : 0) : Double.NaN;
                        if (Double.isFinite(d)) out.add(new HistorySample(entry.getKey(), uplinkId, widget.getKey(), widget.getValue(), d));
                    }
                }
            }
        }
        return out;
    }

    /** v0.5 F: the {@code opts.history} range of a published widget, or null. */
    public @Nullable String historyRange(UUID mainframeId, String uplinkId, String widgetId) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            var section = view.uplinks.get(uplinkId);
            return section == null ? null : section.history.get(widgetId);
        }
    }

    /** Whether a published section is restricted (needs {@code view.positions}). */
    public boolean isRestricted(UUID mainframeId, String uplinkId) {
        var view = views.get(mainframeId);
        if (view == null) return false;
        synchronized (view) {
            return view.restricted.contains(uplinkId);
        }
    }

    /** v0.7 in-game display: a copy of a section's published snapshot, or null. */
    public @Nullable JsonObject sectionSnapshot(UUID mainframeId, String uplinkId) {
        return sectionSnapshot(mainframeId, uplinkId, true);
    }

    /** As above; {@code names} false = private values in their public form (see {@link #seesNames}). */
    public @Nullable JsonObject sectionSnapshot(UUID mainframeId, String uplinkId, boolean names) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        Section section;
        synchronized (view) {
            section = view.uplinks.get(uplinkId);
        }
        return section == null ? null : section.json(names).deepCopy();
    }

    /** v0.7 display picker: [{uplinkId, title, restricted}] of the published sections. */
    public JsonArray sectionList(UUID mainframeId) {
        var list = new JsonArray();
        var view = views.get(mainframeId);
        if (view == null) return list;
        synchronized (view) {
            for (var entry : view.uplinks.entrySet()) {
                var row = new JsonObject();
                row.addProperty("uplinkId", entry.getKey());
                var title = entry.getValue().str("title");
                row.addProperty("title", title == null ? "" : title);
                row.addProperty("restricted", view.restricted.contains(entry.getKey()));
                list.add(row);
            }
        }
        return list;
    }

    /** Number of published sections of a mainframe. */
    public int sectionCount(UUID mainframeId) {
        var view = views.get(mainframeId);
        if (view == null) return 0;
        synchronized (view) {
            return view.uplinks.size();
        }
    }

    /** Type id of a widget as published to the browsers, or null. */
    public @Nullable String widgetType(UUID mainframeId, String uplinkId, String widgetId) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            var section = view.uplinks.get(uplinkId);
            var widget = section == null ? null : section.widget(widgetId);
            return widget == null ? null : widget.type();
        }
    }

    /** Number of connected dashboard clients of a mainframe. */
    public int clientCount(UUID mainframeId) {
        var view = views.get(mainframeId);
        return view == null ? 0 : view.sessions.size();
    }

    /** Called when a section is removed for good (unlinked/broken), e.g. to drop its cached snapshot. */
    public static volatile java.util.function.BiConsumer<UUID, String> onSectionRemoved = (m, u) -> {
    };

    public void publishUplinkRemoved(UUID mainframeId, String uplinkId) {
        onSectionRemoved.accept(mainframeId, uplinkId);
        var view = views.get(mainframeId);
        if (view == null) return;
        synchronized (view) {
            if (view.uplinks.remove(uplinkId) == null) return;
            view.sectionIds.remove(uplinkId);
            view.historySections.remove(uplinkId);
            view.restricted.remove(uplinkId);
            view.dropValues(uplinkId);
            var message = new JsonObject();
            message.addProperty("t", "uplink_removed");
            message.addProperty("uplinkId", uplinkId);
            put(view, "u:" + uplinkId, message);
        }
    }

    /** Rules document of a mainframe ({@code {rules:[…], log:[…], limit}}): snapshot field {@code rules}, delta {@code {"t":"rules",…}}. */
    public void publishRules(UUID mainframeId, JsonObject rules) {
        var view = view(mainframeId);
        synchronized (view) {
            if (rules.equals(view.rules)) return;
            view.rules = rules.deepCopy();
            var message = shallowCopy(view.rules);
            message.addProperty("t", "rules");
            put(view, "rules", message);
        }
    }

    /** Scenes document of a mainframe (v0.10): snapshot field {@code scenes}, delta {@code {"t":"scenes",…}}; never to share sessions. */
    public void publishScenes(UUID mainframeId, JsonObject scenes) {
        var view = view(mainframeId);
        synchronized (view) {
            if (scenes.equals(view.scenes)) return;
            view.scenes = scenes.deepCopy();
            var message = shallowCopy(view.scenes);
            message.addProperty("t", "scenes");
            put(view, "scenes", message);
        }
    }

    /** Test hook: the published scenes document. */
    public @Nullable JsonObject scenesForTest(UUID mainframeId) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            return view.scenes == null ? null : view.scenes.deepCopy();
        }
    }

    /**
     * v0.7 wireless redstone channels of a mainframe network ({@code {channels:[…], limit}}): snapshot field
     * {@code redstone}, delta {@code {"t":"redstone", channels, limit}}; never sent to share sessions.
     */
    public void publishRedstone(UUID mainframeId, JsonObject redstone) {
        var view = view(mainframeId);
        synchronized (view) {
            if (redstone.equals(view.redstone)) return;
            view.redstone = redstone.deepCopy();
            var message = shallowCopy(view.redstone);
            message.addProperty("t", "redstone");
            put(view, "redstone", message);
        }
    }

    /** Test hook: the published redstone channels. */
    public @Nullable JsonObject redstoneForTest(UUID mainframeId) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            return view.redstone == null ? null : view.redstone.deepCopy();
        }
    }

    /** Current value of a widget as published to the browsers, or null. */
    public @Nullable JsonElement widgetValue(UUID mainframeId, String uplinkId, String widgetId) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            var section = view.uplinks.get(uplinkId);
            var widget = section == null ? null : section.widget(widgetId);
            return widget == null || widget.value == null ? null : widget.value.deepCopy();
        }
    }

    /** Id of the first widget of a type in a published section (e.g. the item_search of a storage card), or null. */
    public @Nullable String firstWidgetOfType(UUID mainframeId, String uplinkId, String type) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            var uplink = view.uplinks.get(uplinkId);
            if (uplink == null) return null;
            for (var w : uplink.widgets) if (type.equals(w.type())) return w.id;
            return null;
        }
    }

    /** v0.5 D: every published alert (push notifications). {@code source} = the section's title, or null. */
    public interface AlertListener {
        void alert(UUID mainframeId, @Nullable String source, String text, String level);
    }

    public static volatile @Nullable AlertListener onAlert;

    /** True if the section is published and online. */
    public boolean uplinkOnline(UUID mainframeId, String uplinkId) {
        var view = views.get(mainframeId);
        if (view == null) return false;
        synchronized (view) {
            var uplink = view.uplinks.get(uplinkId);
            return uplink != null && uplink.flag("online");
        }
    }

    public void publishAlert(UUID mainframeId, @Nullable String uplinkId, String text, String level) {
        publishAlert(mainframeId, uplinkId, text, level, null);
    }

    /**
     * An alert whose text has private values (Advanced Cloud Sensor: player names): {@code publicText} is what sessions
     * get that may not see names ({@link #seesNames}) and share links; the push listener gets {@code text} (push targets
     * are set up by admins). {@code publicText} null = not private.
     */
    public void publishAlert(UUID mainframeId, @Nullable String uplinkId, String text, String level, @Nullable String publicText) {
        ai.ezfox.cccloud.api.CloudEvents.alert(new ai.ezfox.cccloud.api.CloudEvents.Alert(mainframeId, uplinkId, text, level));
        var listener = onAlert;
        if (listener != null) {
            String source = null;
            var v = views.get(mainframeId);
            if (v != null && uplinkId != null) {
                synchronized (v) {
                    var snapshot = v.uplinks.get(uplinkId);
                    if (snapshot != null) source = snapshot.str("title");
                }
            }
            try {
                listener.alert(mainframeId, source, text, level);
            } catch (RuntimeException e) {
                ai.ezfox.cccloud.CloudLog.LOGGER.warn("[cccloud] Alert listener failed", e);
            }
        }
        var view = views.get(mainframeId);
        if (view == null) return;
        synchronized (view) {
            if (view.pendingAlerts >= MAX_PENDING_ALERTS) return;
            var message = new JsonObject();
            message.addProperty("t", "alert");
            if (uplinkId != null) message.addProperty("uplinkId", uplinkId);
            message.addProperty("text", text);
            message.addProperty("level", level);
            message.addProperty("ts", System.currentTimeMillis());
            view.pendingAlerts++;
            String key = "a:" + (view.alertSeq++);
            view.outbox.put(key, message);
            if (publicText != null && !publicText.equals(text)) {
                var pub = shallowCopy(message);
                pub.addProperty("text", publicText);
                view.publicOutbox.put(key, pub);
            }
        }
    }

    /** Forgets a mainframe (it was broken) and disconnects its clients. */
    public void removeMainframe(UUID mainframeId) {
        var view = views.remove(mainframeId);
        if (view == null) return;
        for (var session : view.sessions) session.close(4404, "mainframe removed");
    }

    /** Disconnects the device sessions of a mainframe (share viewers stay); if tokenHash is non-null only that device. */
    public void closeSessions(UUID mainframeId, @Nullable String tokenHash) {
        var view = views.get(mainframeId);
        if (view == null) return;
        for (var session : view.sessions) {
            var device = session.device();
            if (device == null) continue;
            if (tokenHash == null || device.tokenHash().equals(tokenHash)) session.close(4401, "revoked");
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

    /** Test hook: the published snapshot of a section. */
    public @Nullable JsonObject uplinkForTest(UUID mainframeId, String uplinkId) {
        return sectionSnapshot(mainframeId, uplinkId);
    }

    public boolean hasUplink(UUID mainframeId, String uplinkId) {
        var view = views.get(mainframeId);
        return view != null && view.sectionIds.contains(uplinkId);
    }

    /** v0.7 Bandwidth upgrade: concurrent browsers per mainframe (set by the game side; share viewers do not count). */
    public static volatile java.util.function.ToIntFunction<UUID> browserLimit = id -> Integer.MAX_VALUE;

    /** Registers a session and sends it the full snapshot. */
    public void addSession(HubSession session) {
        if (session.share() == null && browsersFull(session.mainframeId())) {
            session.close(4429, "browser_limit");
            return;
        }
        if (!tryAddSession(session)) session.close(4429, "share_full");
    }

    /** All browser slots of the mainframe are taken (Bandwidth upgrade raises the limit). */
    public boolean browsersFull(UUID mainframeId) {
        var view = views.get(mainframeId);
        if (view == null) return false;
        int browsers = 0;
        for (var s : view.sessions) if (s.countsAsBrowser()) browsers++;
        return browsers >= browserLimit.applyAsInt(mainframeId);
    }

    /** What a snapshot is built from: references collected under the view's lock (all immutable), serialised outside it. */
    private record SnapshotParts(JsonObject head, List<Section> sections, @Nullable JsonObject layout, @Nullable JsonObject network,
                                 @Nullable JsonObject rules, @Nullable JsonObject redstone, @Nullable JsonObject scenes, boolean names) {
    }

    /**
     * Registers a session and sends it the snapshot (role-dependent; filtered for share sessions). Returns false (and
     * does not register) if the session's share link already has {@code maxViewers} viewers.
     * <p>
     * The session is registered and the references for its snapshot are taken under the view's lock (so every later
     * change reaches it as a delta); the snapshot is serialised outside the lock, and deltas of flushes in between wait
     * until the snapshot is sent.
     */
    public boolean tryAddSession(HubSession session) {
        var view = view(session.mainframeId());
        var share = session.share();
        SnapshotParts parts = null;
        ShareParts shareParts = null;
        synchronized (view) {
            if (share != null) {
                int viewers = 0;
                for (var s : view.sessions) if (s.share() != null && s.share().id().equals(share.id())) viewers++;
                if (viewers >= share.maxViewers()) return false;
                // Share snapshots are filtered copies of the shared cards, built outside the lock from these references.
                shareParts = new ShareParts(view.id, view.name, view.online, view.enabled, new LinkedHashMap<>(view.uplinks), view.layout);
            } else {
                parts = snapshotParts(view, session);
            }
            view.awaiting.put(session, new ArrayList<>());
            view.sessions.add(session);
        }
        var share0 = shareParts;
        var parts0 = parts;
        Runnable finish = () -> {
            List<String> snapshot = share0 != null ? List.of(Json.GSON.toJson(shareSnapshot(share0, share))) : session.inGame() ? serializeSplit(view, parts0)
                : List.of(serialize(view, parts0));
            synchronized (view.sendLock) {
                var later = view.awaiting.remove(session);
                if (later == null && !view.sessions.contains(session)) return; // closed meanwhile
                var messages = new ArrayList<String>(snapshot.size() + (later == null ? 0 : later.size()));
                messages.addAll(snapshot);
                if (later != null) messages.addAll(later);
                session.send(messages);
            }
        };
        if (session.inGame()) {
            // Opened on the server thread (tablet): a big base's snapshot is serialised on the snapshot thread instead.
            try {
                SNAPSHOTS.execute(finish);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                finish.run();
            }
        } else {
            finish.run();
        }
        return true;
    }

    /** Serialises the snapshots of in-game sessions (they are opened on the server thread). */
    private static final java.util.concurrent.ExecutorService SNAPSHOTS = new java.util.concurrent.ThreadPoolExecutor(1, 1, 30, java.util.concurrent.TimeUnit.SECONDS,
        new java.util.concurrent.ArrayBlockingQueue<>(64), r -> {
            var t = new Thread(r, "cccloud-snapshots");
            t.setDaemon(true);
            return t;
        });

    /** The snapshot without sections, then one {@code uplink} message per section (each fits an in-game payload). */
    private static List<String> serializeSplit(View view, SnapshotParts parts) {
        var out = new ArrayList<String>(parts.sections().size() + 1);
        out.add(serialize(view, new SnapshotParts(parts.head(), List.of(), parts.layout(), parts.network(), parts.rules(), parts.redstone(), parts.scenes(),
            parts.names())));
        for (var section : parts.sections()) {
            String text = section.text(parts.names());
            out.add("{\"t\":\"uplink\"," + text.substring(1));
        }
        return out;
    }

    private static SnapshotParts snapshotParts(View view, HubSession session) {
        var json = new JsonObject();
        json.addProperty("t", "snapshot");
        var mainframe = view.mainframeJson();
        boolean canControl = session.canControl();
        mainframe.addProperty("canControl", canControl);
        json.add("mainframe", mainframe);
        json.addProperty("canControl", canControl);
        json.addProperty("role", session.role().id);
        json.add("perms", permsJson(session.perms()));
        // Script Store (docs/SPEC.md): the URL for sessions that may upload scripts, when the store is enabled
        String storeUrl = session.perms().contains(ai.ezfox.cccloud.access.Perm.SCRIPTS_UPLOAD) ? PublicConfig.storeUrl() : null;
        if (storeUrl != null) {
            var store = new JsonObject();
            store.addProperty("url", storeUrl);
            String storeKey = PublicConfig.storeKey();
            if (storeKey != null) store.addProperty("key", storeKey);
            else store.add("key", com.google.gson.JsonNull.INSTANCE);
            json.add("store", store);
        } else {
            json.add("store", com.google.gson.JsonNull.INSTANCE);
        }
        var device = session.device();
        if (device != null) {
            var account = new JsonObject();
            account.addProperty("player", device.playerName());
            json.add("account", account);
        } else {
            json.add("account", com.google.gson.JsonNull.INSTANCE);
        }
        var sections = new ArrayList<Section>(view.uplinks.size());
        boolean restricted = seesRestricted(session.perms());
        for (var entry : view.uplinks.entrySet()) {
            if (restricted || !view.restricted.contains(entry.getKey())) sections.add(entry.getValue());
        }
        // Positions only with view.positions.
        var network = session.seesPositions() ? view.networkFullJson : view.networkStrippedJson;
        return new SnapshotParts(json, sections, view.layout, network, view.rules, view.redstone, view.scenes, seesNames(session.perms()));
    }

    /** The snapshot text: the head, then the sections (each serialised once per change and shared), then the rest. */
    private static String serialize(View view, SnapshotParts parts) {
        var sb = new StringBuilder(64 * 1024);
        String head = Json.GSON.toJson(parts.head());
        sb.append(head, 0, head.length() - 1).append(",\"uplinks\":[");
        boolean first = true;
        for (var section : parts.sections()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(section.text(parts.names()));
        }
        sb.append("],\"layout\":").append(parts.layout() == null ? "null" : Json.GSON.toJson(parts.layout()));
        if (parts.network() != null) sb.append(",\"network\":").append(Json.GSON.toJson(parts.network()));
        if (parts.rules() != null) sb.append(",\"rules\":").append(Json.GSON.toJson(parts.rules()));
        if (parts.redstone() != null) sb.append(",\"redstone\":").append(Json.GSON.toJson(parts.redstone()));
        if (parts.scenes() != null) sb.append(",\"scenes\":").append(Json.GSON.toJson(parts.scenes()));
        return sb.append('}').toString();
    }


    /** What a share snapshot is built from (immutable references taken under the view's lock). */
    private record ShareParts(UUID id, String name, boolean online, boolean enabled, Map<String, Section> sections, @Nullable JsonObject layout) {
    }

    /** v0.6 share snapshot: only the shared cards, no network, no positions, no item search. */
    private static JsonObject shareSnapshot(ShareParts view, AccessStore.Share share) {
        var json = new JsonObject();
        json.addProperty("t", "snapshot");
        json.addProperty("role", Role.SHARE.id);
        var info = new JsonObject();
        info.addProperty("id", share.id());
        info.addProperty("label", share.label());
        info.addProperty("sharedBy", share.createdBy());
        if (share.expires() == 0) info.add("expires", com.google.gson.JsonNull.INSTANCE);
        else info.addProperty("expires", share.expires());
        info.addProperty("hideNumbers", share.hideNumbers());
        json.add("share", info);
        var mainframe = new JsonObject();
        mainframe.addProperty("id", view.id().toString());
        mainframe.addProperty("name", view.name());
        mainframe.addProperty("online", view.online());
        mainframe.addProperty("enabled", view.enabled());
        json.add("mainframe", mainframe);
        json.addProperty("canControl", false);
        var sections = jsonSections(view.sections(), false).values();
        var keys = ShareFilter.scope(sections, view.layout(), share);
        var uplinks = new JsonArray();
        for (var uplink : sections) {
            var filtered = ShareFilter.uplink(uplink, keys, share.hideNumbers());
            if (filtered != null) uplinks.add(filtered);
        }
        json.add("uplinks", uplinks);
        var layout = ShareFilter.layout(view.layout(), sections, keys);
        json.add("layout", layout == null ? com.google.gson.JsonNull.INSTANCE : layout);
        return json;
    }

    /** The sections as JSON objects (cached per section version; shared, never change them). Any thread. */
    private static Map<String, JsonObject> jsonSections(Map<String, Section> sections, boolean names) {
        var map = new LinkedHashMap<String, JsonObject>(sections.size() * 2);
        for (var e : sections.entrySet()) map.put(e.getKey(), e.getValue().json(names));
        return map;
    }

    /**
     * v0.5 {@code "restricted"} sections (mainframe cards: server, claims, backups) are shown from the operator role up;
     * viewers and share links never get them.
     */
    public static boolean seesRestricted(java.util.Set<ai.ezfox.cccloud.access.Perm> perms) {
        return perms.contains(ai.ezfox.cccloud.access.Perm.VIEW_POSITIONS);
    }

    /**
     * Private values (Advanced Cloud Sensor: player names) are shown from the operator role up, the same right as
     * positions and the restricted cards ({@code view.positions}); viewers, share links and displays get the public form.
     */
    public static boolean seesNames(java.util.Set<ai.ezfox.cccloud.access.Perm> perms) {
        return perms.contains(ai.ezfox.cccloud.access.Perm.VIEW_POSITIONS);
    }

    static JsonArray permsJson(java.util.Set<ai.ezfox.cccloud.access.Perm> perms) {
        var array = new JsonArray();
        for (var p : ai.ezfox.cccloud.access.Perm.values()) if (perms.contains(p)) array.add(p.id);
        return array;
    }

    /** The permission an action on this widget needs (see {@link AccessGate#actionPerm}). */
    public ai.ezfox.cccloud.access.Perm actionPerm(UUID mainframeId, String uplinkId, String widgetId) {
        var view = views.get(mainframeId);
        if (view == null) return ai.ezfox.cccloud.access.Perm.ACT_BUTTONS;
        synchronized (view) {
            var uplink = view.uplinks.get(uplinkId);
            var widget = uplink == null ? null : uplink.widget(widgetId);
            String type = widget == null ? null : widget.type();
            String mode = uplink == null ? null : uplink.str("mode");
            String template = uplink == null ? null : uplink.str("template");
            return AccessGate.actionPerm(type, mode, widgetId, template);
        }
    }

    /** Sends a role/permission change ({@code {"t":"role","role","canControl","perms"}}) and the matching network view. */
    public void pushRole(HubSession session, Role role, java.util.Set<ai.ezfox.cccloud.access.Perm> perms) {
        var view = views.get(session.mainframeId());
        boolean restrictedChanged = seesRestricted(session.perms()) != seesRestricted(perms);
        session.setAccess(role, perms);
        var json = new JsonObject();
        json.addProperty("t", "role");
        json.addProperty("role", role.id);
        json.addProperty("canControl", session.canControl());
        json.add("perms", permsJson(perms));
        var messages = new ArrayList<String>();
        messages.add(Json.GSON.toJson(json));
        if (view == null) {
            session.send(messages);
            return;
        }
        JsonObject network;
        SnapshotParts parts = null;
        synchronized (view) {
            network = session.seesPositions() ? view.networkFullJson : view.networkStrippedJson;
            // Restricted sections appear or disappear: resend the snapshot for the new role.
            if (restrictedChanged && view.sessions.contains(session)) {
                parts = snapshotParts(view, session);
                view.awaiting.putIfAbsent(session, new ArrayList<>());
            }
        }
        if (network != null) messages.add(networkMessage(network));
        if (parts != null) messages.add(serialize(view, parts));
        synchronized (view.sendLock) {
            var later = parts == null ? null : view.awaiting.remove(session);
            if (later != null) messages.addAll(later);
            session.send(messages);
        }
    }

    private static String networkMessage(JsonObject network) {
        var message = new JsonObject();
        message.addProperty("t", "network");
        message.add("network", network);
        return Json.GSON.toJson(message);
    }

    /** Number of open sessions of a share link. */
    public int shareViewers(String shareId) {
        int count = 0;
        for (var view : views.values()) {
            for (var s : view.sessions) if (s.share() != null && s.share().id().equals(shareId)) count++;
        }
        return count;
    }

    /** Closes every session of a share link (revoked or expired). */
    public void closeShare(String shareId, String reason) {
        for (var view : views.values()) {
            for (var s : view.sessions) {
                if (s.share() == null || !s.share().id().equals(shareId)) continue;
                s.send(List.of("{\"t\":\"error\",\"error\":\"" + reason + "\"}"));
                s.close(4401, reason);
            }
        }
    }

    /** Closes every session of a device on any mainframe (device revoked). */
    public void closeDevice(String tokenHash) {
        for (var view : views.values()) {
            for (var s : view.sessions) {
                var device = s.device();
                if (device == null || !device.tokenHash().equals(tokenHash)) continue;
                s.send(List.of("{\"t\":\"error\",\"error\":\"revoked\"}"));
                s.close(4401, "revoked");
            }
        }
    }

    /** All mainframes the hub knows (published since the server started). */
    public List<MainframeInfo> mainframes() {
        var list = new ArrayList<MainframeInfo>();
        for (var view : views.values()) {
            synchronized (view) {
                list.add(new MainframeInfo(view.id, view.name, view.online, view.enabled));
            }
        }
        return list;
    }

    public void removeSession(HubSession session) {
        var view = views.get(session.mainframeId());
        if (view == null) return;
        view.awaiting.remove(session);
        if (view.sessions.remove(session)) view.lastClientSeen = System.currentTimeMillis();
    }

    /**
     * A feature switch of {@code GET /api/config} changed (config reload): open dashboards fetch it again
     * ({@code {"t":"config"}}). Only browser WebSockets; tablets and API streams do not use it.
     */
    public void broadcastConfigChanged() {
        var message = List.of("{\"t\":\"config\"}");
        for (var session : sessions()) {
            if (session instanceof WebSocketSession) session.send(message);
        }
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
            var section = view.uplinks.get(uplinkId);
            var widget = section == null ? null : section.widget(widgetId);
            if (widget == null || !(widget.base.get("opts") instanceof JsonObject opts)) return false;
            var pin = opts.get("pin");
            return pin != null && pin.isJsonPrimitive() && pin.getAsJsonPrimitive().isBoolean() && pin.getAsBoolean();
        }
    }

    public int sessionCount() {
        int count = 0;
        for (var view : views.values()) count += view.sessions.size();
        return count;
    }

    /** Serialises flushes (the web server's 100 ms timer, the server tick in GameTests, the tablet without web server): messages keep their order. */
    private final Object flushLock = new Object();

    /** GameTests ({@code -Dcccloud.gametest=true}): flush on every server tick, so delivery does not depend on wall-clock timers. */
    public static volatile boolean FLUSH_EVERY_TICK = Boolean.getBoolean("cccloud.gametest");

    /** Sends all pending changes. Called periodically from a Netty thread. */
    public void flush() {
        synchronized (flushLock) {
            long now = System.currentTimeMillis();
            for (var view : views.values()) flush(view, now);
        }
    }

    private void flush(View view, long now) {
        Map<String, JsonObject> pending;
        Map<String, JsonObject> publicPending;
        Map<String, Set<String>> changedValues;
        Map<String, Section> changedSections = new HashMap<>();
        JsonObject networkFull = null, networkStripped = null;
        Set<String> restricted;
        List<HubSession> targets;
        Map<String, Section> shareSections = null;
        @Nullable JsonObject layout = null;
        synchronized (view) {
            if (view.networkDirty && now - view.networkSentAt >= 500) {
                view.networkDirty = false;
                view.networkSentAt = now;
                networkFull = view.networkFullJson;
                networkStripped = view.networkStrippedJson;
            }
            if (view.outbox.isEmpty() && view.values.isEmpty() && networkFull == null) return;
            pending = view.outbox;
            view.outbox = new LinkedHashMap<>();
            publicPending = view.publicOutbox.isEmpty() ? new LinkedHashMap<>() : view.publicOutbox;
            if (!view.publicOutbox.isEmpty()) view.publicOutbox = new LinkedHashMap<>();
            view.pendingAlerts = 0;
            resolveSections(view, pending, publicPending);
            // only references under the lock (the server thread publishes to this view every tick); the value
            // messages are built outside it from the immutable section versions
            changedValues = view.values;
            view.values = new LinkedHashMap<>();
            for (var id : changedValues.keySet()) {
                var section = view.uplinks.get(id);
                if (section != null) changedSections.put(id, section);
            }
            restricted = view.restricted.isEmpty() ? Set.of() : Set.copyOf(view.restricted);
            // Only the sessions registered now: a session added later has these changes in its snapshot already.
            targets = List.copyOf(view.sessions);
            for (var s : targets) {
                if (s.share() != null) {
                    // Share filtering needs the sections and the layout (immutable objects; the map is copied).
                    shareSections = new LinkedHashMap<>(view.uplinks);
                    layout = view.layout;
                    break;
                }
            }
        }
        if (targets.isEmpty()) return;
        var valueSections = valuesOf(changedValues, changedSections, true);
        // Private values (player names): sessions that may not see them get the public forms.
        boolean anyPrivate = !publicPending.isEmpty();
        for (var section : changedSections.values()) anyPrivate |= section.hasPrivate();
        var publicValueSections = anyPrivate ? valuesOf(changedValues, changedSections, false) : valueSections;
        Map<String, JsonObject> uplinks = shareSections == null ? Map.of() : jsonSections(shareSections, false);
        String fullNet = null, strippedNet = null;
        if (networkFull != null) {
            // Serialised once per change, outside the view's lock and off the server thread.
            fullNet = view.networkFull;
            if (fullNet == null) view.networkFull = fullNet = networkMessage(networkFull);
            strippedNet = view.networkStripped;
            if (strippedNet == null) view.networkStripped = strippedNet = networkMessage(networkStripped);
        }
        List<String> messages = null;
        List<String> readOnly = null;
        Map<String, List<String>> shareMessages = null;
        for (var session : targets) {
            var share = session.share();
            List<String> out = new ArrayList<>();
            if (share == null) {
                if (fullNet != null) out.add(session.seesPositions() ? fullNet : strippedNet);
                if (messages == null) {
                    messages = new ArrayList<>(pending.size() + 1);
                    readOnly = restricted.isEmpty() && !anyPrivate ? messages : new ArrayList<>(pending.size() + 1);
                    for (var entry : pending.entrySet()) {
                        var message = entry.getValue();
                        String json = Json.GSON.toJson(message);
                        messages.add(json);
                        if (readOnly == messages || message.has("uplinkId") && restricted.contains(message.get("uplinkId").getAsString())) continue;
                        var pub = publicPending.get(entry.getKey());
                        readOnly.add(pub == null ? json : Json.GSON.toJson(pub));
                    }
                    if (!valueSections.isEmpty()) {
                        messages.add(valuesMessage(valueSections, null));
                        if (readOnly != messages) {
                            var visible = valuesMessage(publicValueSections, restricted);
                            if (visible != null) readOnly.add(visible);
                        }
                    }
                }
                // seesNames and seesRestricted are the same right (view.positions), so one split serves both
                out.addAll(seesRestricted(session.perms()) ? messages : readOnly);
            } else {
                if (shareMessages == null) shareMessages = new HashMap<>();
                var filtered = shareMessages.get(share.id());
                if (filtered == null) {
                    filtered = new ArrayList<>();
                    var keys = ShareFilter.scope(uplinks.values(), layout, share);
                    for (var entry : pending.entrySet()) {
                        var m = ShareFilter.message(publicPending.getOrDefault(entry.getKey(), entry.getValue()), uplinks, keys, share.hideNumbers());
                        if (m != null) filtered.add(Json.GSON.toJson(m));
                    }
                    var values = ShareFilter.values(publicValueSections, uplinks, keys, share.hideNumbers());
                    if (values != null) filtered.add(valuesMessage(values, null));
                    shareMessages.put(share.id(), filtered);
                }
                out.addAll(filtered);
            }
            if (out.isEmpty()) continue;
            synchronized (view.sendLock) {
                var waiting = view.awaiting.get(session);
                if (waiting != null) waiting.addAll(out); // its snapshot is being serialised: these follow it
                else session.send(out);
            }
        }
    }

    /** Marker in the outbox for "send this section as it is at flush time" ({@code u:<uplinkId>}); never serialised. */
    private static final JsonObject SECTION_PENDING = new JsonObject();

    /**
     * Replaces section markers by {@code {"t":"uplink",…}} with the section's current version; sections with private
     * values also get their public form in {@code publicPending}. Under the view's lock.
     */
    private static void resolveSections(View view, Map<String, JsonObject> pending, Map<String, JsonObject> publicPending) {
        for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (entry.getValue() != SECTION_PENDING) continue;
            var section = view.uplinks.get(entry.getKey().substring(2));
            if (section == null) {
                it.remove();
                continue;
            }
            var message = shallowCopy(section.json());
            message.addProperty("t", "uplink");
            entry.setValue(message);
            if (section.hasPrivate()) {
                var pub = shallowCopy(section.json(false));
                pub.addProperty("t", "uplink");
                publicPending.put(entry.getKey(), pub);
            }
        }
    }

    /**
     * uplinkId → {widgetId: latest value} of the changed widgets (from immutable section versions; any thread);
     * {@code names} false = private values in their public form.
     */
    private static Map<String, JsonObject> valuesOf(Map<String, Set<String>> changedValues, Map<String, Section> sections, boolean names) {
        if (changedValues.isEmpty()) return Map.of();
        var out = new LinkedHashMap<String, JsonObject>();
        for (var entry : changedValues.entrySet()) {
            var section = sections.get(entry.getKey());
            if (section == null) continue;
            var changed = new JsonObject();
            for (var widgetId : entry.getValue()) {
                var widget = section.widget(widgetId);
                var value = widget == null ? null : widget.value(names);
                if (value == null) continue;
                changed.add(widgetId, value);
            }
            if (changed.size() > 0) out.put(entry.getKey(), changed);
        }
        return out;
    }

    /** {@code {"t":"values","s":{uplinkId:{widgetId:value}}}} without the sections in {@code skip}, or null if nothing is left. */
    static @Nullable String valuesMessage(Map<String, JsonObject> sections, @Nullable Set<String> skip) {
        var s = new JsonObject();
        for (var entry : sections.entrySet()) {
            if (skip != null && skip.contains(entry.getKey())) continue;
            s.add(entry.getKey(), entry.getValue());
        }
        if (s.size() == 0) return null;
        var message = new JsonObject();
        message.addProperty("t", "values");
        message.add("s", s);
        return Json.GSON.toJson(message);
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
