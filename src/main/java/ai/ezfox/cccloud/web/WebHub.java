package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.Role;
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
        /** v0.5: sections only sent to devices with canControl (snapshot {@code "restricted":true}, e.g. the Server card). */
        final Set<String> restricted = new java.util.HashSet<>();
        Map<String, JsonObject> outbox = new LinkedHashMap<>();
        int alertSeq;
        /** Power of the mainframe: {stored, capacity, usage, ok}; reason why it is offline (or null). */
        @Nullable JsonObject power;
        @Nullable String reason;
        /** v0.4 network map: full (with positions, canControl) and without positions; sent at most 2x per second. */
        @Nullable String networkFull;
        @Nullable String networkStripped;
        @Nullable JsonObject networkFullJson;
        @Nullable JsonObject networkStrippedJson;
        boolean networkDirty;
        long networkSentAt;
        /** v0.5 rules of the mainframe ({rules, log, limit}), null = none yet; never sent to share sessions. */
        @Nullable JsonObject rules;
        /** v0.7 wireless redstone channels ({channels, limit}), null = none yet; never sent to share sessions. */
        @Nullable JsonObject redstone;
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

    /** Network map of a mainframe; snapshots include it and changes go out as {"t":"network",...} (max 2/s). */
    public void publishNetwork(UUID mainframeId, JsonObject full, JsonObject stripped) {
        var view = view(mainframeId);
        synchronized (view) {
            if (full.equals(view.networkFullJson)) return;
            view.networkFullJson = full;
            view.networkStrippedJson = stripped;
            var a = new JsonObject();
            a.addProperty("t", "network");
            a.add("network", full);
            var b = new JsonObject();
            b.addProperty("t", "network");
            b.add("network", stripped);
            view.networkFull = Json.GSON.toJson(a);
            view.networkStripped = Json.GSON.toJson(b);
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

    public void publishUplink(UUID mainframeId, JsonObject snapshot) {
        var view = view(mainframeId);
        String uplinkId = snapshot.get("uplinkId").getAsString();
        var copy = snapshot.deepCopy();
        // Live sections are "loaded"; cached copies of unloaded ones carry loaded=false and lastSeen.
        if (!copy.has("loaded")) copy.addProperty("loaded", true);
        Map<String, JsonObject> index = new ConcurrentHashMap<>();
        for (var w : copy.getAsJsonArray("widgets")) {
            var widget = w.getAsJsonObject();
            index.put(widget.get("id").getAsString(), widget);
        }
        synchronized (view) {
            view.uplinks.put(uplinkId, copy);
            view.widgetIndex.put(uplinkId, index);
            if (copy.has("restricted") && copy.get("restricted").getAsBoolean()) view.restricted.add(uplinkId);
            else view.restricted.remove(uplinkId);
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

    /** Copies of all published section snapshots of a mainframe, in publish order (layout migration and checks). */
    public java.util.List<JsonObject> sectionSnapshots(UUID mainframeId) {
        var list = new ArrayList<JsonObject>();
        var view = views.get(mainframeId);
        if (view == null) return list;
        synchronized (view) {
            for (var snapshot : view.uplinks.values()) list.add(snapshot.deepCopy());
        }
        return list;
    }

    /** v0.7 in-game display: a copy of a section's published snapshot, or null. */
    public @Nullable JsonObject sectionSnapshot(UUID mainframeId, String uplinkId) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            var snapshot = view.uplinks.get(uplinkId);
            return snapshot == null ? null : snapshot.deepCopy();
        }
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
                row.addProperty("title", entry.getValue().has("title") ? entry.getValue().get("title").getAsString() : "");
                row.addProperty("restricted", view.restricted.contains(entry.getKey()));
                list.add(row);
            }
        }
        return list;
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

    /** Called when a section is removed for good (unlinked/broken), e.g. to drop its cached snapshot. */
    public static volatile java.util.function.BiConsumer<UUID, String> onSectionRemoved = (m, u) -> {
    };

    public void publishUplinkRemoved(UUID mainframeId, String uplinkId) {
        onSectionRemoved.accept(mainframeId, uplinkId);
        var view = views.get(mainframeId);
        if (view == null) return;
        synchronized (view) {
            if (view.uplinks.remove(uplinkId) == null) return;
            view.widgetIndex.remove(uplinkId);
            view.restricted.remove(uplinkId);
            view.outbox.keySet().removeIf(k -> k.startsWith("v:" + uplinkId + ":"));
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
            var message = rules.deepCopy();
            message.addProperty("t", "rules");
            put(view, "rules", message);
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
            var message = redstone.deepCopy();
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
            var index = view.widgetIndex.get(uplinkId);
            var widget = index == null ? null : index.get(widgetId);
            return widget == null || !widget.has("value") ? null : widget.get("value").deepCopy();
        }
    }

    /** Id of the first widget of a type in a published section (e.g. the item_search of a storage card), or null. */
    public @Nullable String firstWidgetOfType(UUID mainframeId, String uplinkId, String type) {
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            var uplink = view.uplinks.get(uplinkId);
            if (uplink == null || !(uplink.get("widgets") instanceof JsonArray widgets)) return null;
            for (var w : widgets) {
                if (w instanceof JsonObject o && o.has("type") && type.equals(o.get("type").getAsString())) return o.get("id").getAsString();
            }
            return null;
        }
    }

    /** True if the section is published and online. */
    public boolean uplinkOnline(UUID mainframeId, String uplinkId) {
        var view = views.get(mainframeId);
        if (view == null) return false;
        synchronized (view) {
            var uplink = view.uplinks.get(uplinkId);
            return uplink != null && uplink.has("online") && uplink.get("online").getAsBoolean();
        }
    }

    public void publishAlert(UUID mainframeId, @Nullable String uplinkId, String text, String level) {
        ai.ezfox.cccloud.api.CloudEvents.alert(new ai.ezfox.cccloud.api.CloudEvents.Alert(mainframeId, uplinkId, text, level));
        var view = views.get(mainframeId);
        if (view == null) return;
        synchronized (view) {
            var message = new JsonObject();
            message.addProperty("t", "alert");
            if (uplinkId != null) message.addProperty("uplinkId", uplinkId);
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
        var view = views.get(mainframeId);
        if (view == null) return null;
        synchronized (view) {
            var uplink = view.uplinks.get(uplinkId);
            return uplink == null ? null : uplink.deepCopy();
        }
    }

    public boolean hasUplink(UUID mainframeId, String uplinkId) {
        var view = views.get(mainframeId);
        return view != null && view.widgetIndex.containsKey(uplinkId);
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
        for (var s : view.sessions) if (s.share() == null) browsers++;
        return browsers >= browserLimit.applyAsInt(mainframeId);
    }

    /**
     * Registers a session and sends it the snapshot (role-dependent; filtered for share sessions). Returns false (and
     * does not register) if the session's share link already has {@code maxViewers} viewers.
     */
    public boolean tryAddSession(HubSession session) {
        var view = view(session.mainframeId());
        String snapshot;
        synchronized (view) {
            var share = session.share();
            if (share != null) {
                int viewers = 0;
                for (var s : view.sessions) if (s.share() != null && s.share().id().equals(share.id())) viewers++;
                if (viewers >= share.maxViewers()) return false;
            }
            view.sessions.add(session);
            snapshot = Json.GSON.toJson(share != null ? shareSnapshot(view, share) : snapshot(view, session));
        }
        session.send(List.of(snapshot));
        return true;
    }

    private static JsonObject snapshot(View view, HubSession session) {
        var json = new JsonObject();
        json.addProperty("t", "snapshot");
        var mainframe = view.mainframeJson();
        boolean canControl = session.canControl();
        mainframe.addProperty("canControl", canControl);
        json.add("mainframe", mainframe);
        json.addProperty("canControl", canControl);
        json.addProperty("role", session.role().id);
        json.add("perms", permsJson(session.perms()));
        var device = session.device();
        if (device != null) {
            var account = new JsonObject();
            account.addProperty("player", device.playerName());
            json.add("account", account);
        } else {
            json.add("account", com.google.gson.JsonNull.INSTANCE);
        }
        var uplinks = new JsonArray();
        boolean restricted = seesRestricted(session.perms());
        for (var entry : view.uplinks.entrySet()) {
            if (restricted || !view.restricted.contains(entry.getKey())) uplinks.add(entry.getValue());
        }
        json.add("uplinks", uplinks);
        json.add("layout", view.layout == null ? com.google.gson.JsonNull.INSTANCE : view.layout);
        // Positions only with view.positions.
        var network = session.seesPositions() ? view.networkFullJson : view.networkStrippedJson;
        if (network != null) json.add("network", network);
        if (view.rules != null) json.add("rules", view.rules);
        if (view.redstone != null) json.add("redstone", view.redstone);
        return json;
    }

    /** v0.6 share snapshot: only the shared cards, no network, no positions, no item search. */
    private static JsonObject shareSnapshot(View view, AccessStore.Share share) {
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
        mainframe.addProperty("id", view.id.toString());
        mainframe.addProperty("name", view.name);
        mainframe.addProperty("online", view.online);
        mainframe.addProperty("enabled", view.enabled);
        json.add("mainframe", mainframe);
        json.addProperty("canControl", false);
        var keys = ShareFilter.scope(view.uplinks.values(), view.layout, share);
        var uplinks = new JsonArray();
        for (var uplink : view.uplinks.values()) {
            var filtered = ShareFilter.uplink(uplink, keys, share.hideNumbers());
            if (filtered != null) uplinks.add(filtered);
        }
        json.add("uplinks", uplinks);
        json.add("layout", view.layout == null ? com.google.gson.JsonNull.INSTANCE : view.layout.deepCopy());
        return json;
    }

    /**
     * v0.5 {@code "restricted"} sections (mainframe cards: server, claims, backups) are shown from the operator role up;
     * viewers and share links never get them.
     */
    public static boolean seesRestricted(java.util.Set<ai.ezfox.cccloud.access.Perm> perms) {
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
            var index = view.widgetIndex.get(uplinkId);
            var widget = index == null ? null : index.get(widgetId);
            String type = widget == null || !widget.has("type") ? null : widget.get("type").getAsString();
            String mode = uplink == null || !uplink.has("mode") ? null : uplink.get("mode").getAsString();
            return AccessGate.actionPerm(type, mode, widgetId);
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
        if (view != null) {
            synchronized (view) {
                String network = session.seesPositions() ? view.networkFull : view.networkStripped;
                if (network != null) messages.add(network);
                // Restricted sections appear or disappear: resend the snapshot for the new role.
                if (restrictedChanged) messages.add(Json.GSON.toJson(snapshot(view, session)));
            }
        }
        session.send(messages);
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
        long now = System.currentTimeMillis();
        for (var view : views.values()) {
            Map<String, JsonObject> pending;
            String networkFull = null, networkStripped = null;
            Set<String> restricted;
            synchronized (view) {
                if (view.networkDirty && now - view.networkSentAt >= 500) {
                    view.networkDirty = false;
                    view.networkSentAt = now;
                    networkFull = view.networkFull;
                    networkStripped = view.networkStripped;
                }
                if (view.outbox.isEmpty() && networkFull == null) continue;
                pending = view.outbox;
                view.outbox = new LinkedHashMap<>();
                restricted = view.restricted.isEmpty() ? Set.of() : Set.copyOf(view.restricted);
            }
            if (networkFull != null && !view.sessions.isEmpty()) {
                for (var session : view.sessions) {
                    if (session.share() != null) continue; // share viewers never get the network map
                    session.send(List.of(session.seesPositions() ? networkFull : networkStripped));
                }
            }
            if (pending.isEmpty()) continue;
            if (view.sessions.isEmpty()) continue;
            List<String> messages = null;
            List<String> readOnly = null;
            Map<String, List<String>> shareMessages = null;
            for (var session : view.sessions) {
                var share = session.share();
                if (share == null) {
                    if (messages == null) {
                        messages = new ArrayList<>(pending.size());
                        readOnly = restricted.isEmpty() ? messages : new ArrayList<>(pending.size());
                        for (var message : pending.values()) {
                            String json = Json.GSON.toJson(message);
                            messages.add(json);
                            if (readOnly != messages && !(message.has("uplinkId") && restricted.contains(message.get("uplinkId").getAsString()))) readOnly.add(json);
                        }
                    }
                    session.send(seesRestricted(session.perms()) ? messages : readOnly);
                    continue;
                }
                if (shareMessages == null) shareMessages = new java.util.HashMap<>();
                var filtered = shareMessages.get(share.id());
                if (filtered == null) {
                    filtered = new ArrayList<>();
                    synchronized (view) {
                        var keys = ShareFilter.scope(view.uplinks.values(), view.layout, share);
                        for (var message : pending.values()) {
                            var m = ShareFilter.message(message, view.uplinks, keys, share.hideNumbers());
                            if (m != null) filtered.add(Json.GSON.toJson(m));
                        }
                    }
                    shareMessages.put(share.id(), filtered);
                }
                if (!filtered.isEmpty()) session.send(filtered);
            }
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
