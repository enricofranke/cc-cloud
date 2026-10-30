package ai.ezfox.cccloud.rsdevice;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.SectionCache;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Game side of the Cloud Redstone devices (docs/SPEC.md "Cloud Redstone devices"): the loaded devices, their copies per
 * mainframe ({@link RsDeviceData}) and the mainframes' redstone cards ({@link RsGroupSection}, built by
 * {@link RsCards}). A device that changes marks its mainframe dirty; the cards are rebuilt at the end of the tick and
 * sent as value deltas when only values changed. Cards live while their mainframe is loaded (when it unloads they stay
 * on the dashboard greyed out, like an unloaded uplink). Server thread only.
 */
public final class RsDevices {
    private static final int REFRESH_INTERVAL = 20;

    private static final Map<UUID, RsDeviceBlockEntity> LOADED = new HashMap<>();
    /** mainframe → section id → card. Only for loaded mainframes. */
    private static final Map<UUID, LinkedHashMap<UUID, RsGroupSection>> SECTIONS = new HashMap<>();
    private static final Set<UUID> DIRTY = new HashSet<>();
    /** What each mainframe's cards were last built from (see refresh). */
    private static final Map<UUID, Object> LAST_INPUT = new HashMap<>();
    private static boolean listening;

    private RsDevices() {
    }

    public static void init() {
        if (listening) return;
        listening = true;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> cacheAll(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> clear());
    }

    // ---- loaded devices ------------------------------------------------------------------------------------------------

    /** @return false if another loaded device already uses this id (a copied block). */
    static boolean register(RsDeviceBlockEntity device) {
        var existing = LOADED.get(device.deviceId());
        if (existing != null && existing != device && !existing.isRemoved()) return false;
        LOADED.put(device.deviceId(), device);
        var link = device.getLink();
        if (link != null) DIRTY.add(link.id());
        return true;
    }

    static void unregister(RsDeviceBlockEntity device) {
        LOADED.remove(device.deviceId(), device);
    }

    /** Every loaded device (network graph). */
    public static java.util.Collection<RsDeviceBlockEntity> loadedDevices() {
        return LOADED.values();
    }

    /** The loaded device with this id, or null. */
    public static @Nullable RsDeviceBlockEntity loaded(UUID deviceId) {
        var device = LOADED.get(deviceId);
        return device == null || device.isRemoved() ? null : device;
    }

    /** A device's state, name or link changed: update the dashboard copy (the cards follow at the end of the tick). */
    static void changed(RsDeviceBlockEntity device) {
        var link = device.getLink();
        var server = CloudNetwork.server();
        if (link == null || server == null) return;
        var data = RsDeviceData.get(server);
        // Only for mainframes that exist (a purged mainframe's devices do not bring it back).
        if (CloudNetwork.mainframe(link.id()) == null && data.devices(link.id()).isEmpty()) return;
        if (data.put(link.id(), device.record())) DIRTY.add(link.id());
    }

    /**
     * Puts a loaded device's copy on its mainframe's cards (first status pass after loading, once the mainframe is
     * there). Also refreshes a copy that differs from the block (moved with its data, changed while the copy was
     * missing). @return true when the mainframe now lists the copy
     */
    static boolean list(RsDeviceBlockEntity device) {
        changed(device);
        var link = device.getLink();
        return link != null && device(link.id(), device.deviceId()) != null;
    }

    /** The device was unlinked or broken. */
    static void forget(UUID mainframeId, UUID deviceId) {
        var server = CloudNetwork.server();
        if (server != null && RsDeviceData.get(server).remove(mainframeId, deviceId)) DIRTY.add(mainframeId);
    }

    /** Mainframe purged (docs/SPEC.md "Mainframe removal"): its devices' copies and cards go. */
    public static void purge(MinecraftServer server, UUID mainframeId) {
        RsDeviceData.get(server).forget(mainframeId);
        var sections = SECTIONS.remove(mainframeId);
        LAST_INPUT.remove(mainframeId);
        if (sections != null) for (var section : sections.values()) CloudNetwork.unregisterSection(section);
    }

    /** The dashboard copy of a device (also for unloaded ones), or null. */
    public static @Nullable RsDevice device(UUID mainframeId, UUID deviceId) {
        var server = CloudNetwork.server();
        return server == null ? null : RsDeviceData.get(server).device(mainframeId, deviceId);
    }

    /** All devices of a mainframe (copies, in link order). */
    public static List<RsDevice> devices(UUID mainframeId) {
        var server = CloudNetwork.server();
        return server == null ? List.of() : List.copyOf(RsDeviceData.get(server).devices(mainframeId).values());
    }

    /** The mainframe's redstone cards (tests, in-game screens). */
    public static List<RsGroupSection> sections(UUID mainframeId) {
        var map = SECTIONS.get(mainframeId);
        return map == null ? List.of() : List.copyOf(map.values());
    }

    /** The card that shows a device right now, or null. */
    public static @Nullable RsGroupSection sectionOfDevice(UUID mainframeId, UUID deviceId) {
        var map = SECTIONS.get(mainframeId);
        if (map == null) return null;
        for (var section : map.values()) if (section.card().devices().contains(deviceId)) return section;
        return null;
    }

    /**
     * Rules and scenes point at {@code (card, widget)}. A device keeps its widget id when it moves to another card
     * (new group), so a reference to a device widget that is no longer on that card follows the device to its current
     * card. Anything that is not a device widget (or still where it was) is returned unchanged.
     */
    public static String sectionOf(UUID mainframeId, String uplinkId, String widgetId) {
        var target = RsCards.target(widgetId);
        if (target == null || target.deviceKey() == null) return uplinkId;
        if (WebHub.INSTANCE.widgetType(mainframeId, uplinkId, widgetId) != null) return uplinkId;
        var map = SECTIONS.get(mainframeId);
        if (map == null) return uplinkId;
        for (var section : map.values()) {
            for (var widget : section.card().widgets()) if (widget.id().equals(widgetId)) return section.sectionId().toString();
        }
        return uplinkId;
    }

    // ---- actions -------------------------------------------------------------------------------------------------------

    /** An action on a card's widget (validated already): applied to the device(s). Null = ok, else the error. */
    static @Nullable String action(RsGroupSection section, String widgetId, JsonElement value, String user) {
        var target = RsCards.target(widgetId);
        if (target == null) return "unknown_widget";
        var card = section.card();
        if (target.deviceKey() == null) {
            // All on / All off: every switch and lamp of the card; fails only when none could be switched.
            String first = null;
            int done = 0;
            for (var id : card.devices()) {
                var copy = device(section.mainframeId(), id);
                if (copy == null || !copy.kind().switchable()) continue;
                var device = loaded(id);
                String error = device == null ? "unloaded" : device.apply(target.control(), value, user);
                if (error == null) done++;
                else if (first == null) first = error;
            }
            return done > 0 || first == null ? null : first;
        }
        for (var id : card.devices()) {
            if (!RsCards.widgetId(id).equals(target.deviceKey())) continue;
            var device = loaded(id);
            if (device == null) return "unloaded";
            String error = device.apply(target.control(), value, user);
            if (error == null) DIRTY.add(section.mainframeId());
            return error;
        }
        return "unknown_widget";
    }

    // ---- cards -------------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        long cost = ai.ezfox.cccloud.network.TickCost.start();
        try {
            tickCards(server);
        } finally {
            ai.ezfox.cccloud.network.TickCost.end(ai.ezfox.cccloud.network.TickCost.RS_CARDS, cost);
        }
    }

    private static void tickCards(MinecraftServer server) {
        var data = RsDeviceData.get(server);
        int tick = server.getTickCount();
        var ids = new HashSet<UUID>(DIRTY);
        DIRTY.clear();
        // Every mainframe once per REFRESH_INTERVAL, spread over the ticks by its id: a server with many mainframes
        // does not rebuild all their cards in the same tick (docs/SPEC.md "Scaling").
        for (var id : data.mainframes()) if (due(id, tick)) ids.add(id);
        for (var id : SECTIONS.keySet()) if (due(id, tick)) ids.add(id);
        if (ids.isEmpty()) return;
        for (var id : ids) {
            try {
                refresh(server, data, id);
            } catch (RuntimeException e) {
                CloudLog.LOGGER.error("[cccloud] Could not update the redstone cards of {}", id, e);
            }
        }
    }

    private static boolean due(UUID mainframeId, int tick) {
        return Math.floorMod(mainframeId.hashCode() + tick, REFRESH_INTERVAL) == 0;
    }

    /** Rebuilds a mainframe's cards and publishes what changed. */
    private static void refresh(MinecraftServer server, RsDeviceData data, UUID mainframeId) {
        var mainframe = CloudNetwork.mainframe(mainframeId);
        var current = SECTIONS.get(mainframeId);
        if (mainframe == null || mainframe.getLevel() == null) {
            // Not loaded (or broken): keep the cards on the dashboard greyed out, like unloaded uplinks.
            if (current != null) {
                SECTIONS.remove(mainframeId);
                for (var section : current.values()) cache(server, section);
            }
            return;
        }
        // Devices the mainframe no longer lists (unlinked from the map, orphan sweep) are dropped.
        var linked = mainframe.getLinkedUplinks().keySet();
        for (var id : List.copyOf(data.devices(mainframeId).keySet())) {
            if (!linked.contains(id)) data.remove(mainframeId, id);
        }
        boolean online = mainframe.status() == CloudStatus.ONLINE && mainframe.isEnabled();
        var link = new MainframeLink(GlobalPos.of(mainframe.getLevel().dimension(), mainframe.getBlockPos()), mainframeId);
        // The cards are a pure function of the device copies, online and the link: nothing to do when those are the same
        // as last time (the periodic pass on a big base rebuilt hundreds of widgets every second for nothing).
        var input = List.of(List.copyOf(data.devices(mainframeId).values()), online, link);
        if (current != null && input.equals(LAST_INPUT.get(mainframeId))) return;
        LAST_INPUT.put(mainframeId, input);
        var cards = RsCards.build(data.devices(mainframeId).values());
        var next = new LinkedHashMap<UUID, RsGroupSection>();
        for (var card : cards) {
            var id = RsCards.sectionId(mainframeId, card.groupKey(), card.part());
            var section = current == null ? null : current.get(id);
            if (section == null || !section.getLink().equals(link)) {
                if (section != null) CloudNetwork.unregisterSection(section);
                section = new RsGroupSection(mainframeId, link, card);
                CloudNetwork.registerSection(section);
            }
            section.update(card, online);
            next.put(id, section);
            publish(mainframeId, section);
        }
        if (current != null) {
            for (var old : current.values()) {
                if (next.containsKey(old.sectionId())) continue;
                CloudNetwork.unregisterSection(old);
                WebHub.INSTANCE.publishUplinkRemoved(mainframeId, old.sectionId().toString());
            }
        }
        if (next.isEmpty()) SECTIONS.remove(mainframeId);
        else SECTIONS.put(mainframeId, next);
    }

    /** Sends the card: the whole snapshot when its structure changed, else only the changed values. */
    private static void publish(UUID mainframeId, RsGroupSection section) {
        var snapshot = section.snapshot();
        var last = section.published;
        if (snapshot.equals(last)) return;
        section.published = snapshot;
        if (last == null || !withoutValues(last).equals(withoutValues(snapshot))) {
            WebHub.INSTANCE.publishUplink(mainframeId, snapshot);
            return;
        }
        var before = values(last);
        for (var e : values(snapshot).entrySet()) {
            if (!e.getValue().equals(before.get(e.getKey()))) WebHub.INSTANCE.publishValue(mainframeId, section.sectionId().toString(), e.getKey(), e.getValue());
        }
    }

    private static JsonObject withoutValues(JsonObject snapshot) {
        var copy = snapshot.deepCopy();
        for (var w : copy.getAsJsonArray("widgets")) w.getAsJsonObject().remove("value");
        return copy;
    }

    private static Map<String, JsonElement> values(JsonObject snapshot) {
        var map = new LinkedHashMap<String, JsonElement>();
        for (var w : snapshot.getAsJsonArray("widgets")) {
            var o = w.getAsJsonObject();
            map.put(o.get("id").getAsString(), o.has("value") ? o.get("value") : com.google.gson.JsonNull.INSTANCE);
        }
        return map;
    }

    /** The mainframe unloads: the card stays greyed out (also across a restart, through the section cache). */
    private static void cache(MinecraftServer server, RsGroupSection section) {
        CloudNetwork.unregisterSection(section);
        var cached = SectionCache.unloaded(section.snapshot());
        WebHub.INSTANCE.publishUplink(section.mainframeId(), cached);
        SectionCache.get(server).store(section.mainframeId(), cached);
    }

    private static void cacheAll(MinecraftServer server) {
        try {
            for (var map : SECTIONS.values()) {
                for (var section : map.values()) SectionCache.get(server).store(section.mainframeId(), SectionCache.unloaded(section.snapshot()));
            }
        } catch (RuntimeException e) {
            CloudLog.LOGGER.error("[cccloud] Could not cache the redstone cards", e);
        }
    }

    static void clear() {
        LOADED.clear();
        SECTIONS.clear();
        DIRTY.clear();
        LAST_INPUT.clear();
    }

    // ---- network map, analyzer ----------------------------------------------------------------------------------------

    /** Network map type of a device ({@code cloud_switch}, …), or null if it is not a redstone device we know. */
    public static @Nullable String typeId(UUID mainframeId, UUID deviceId) {
        var device = loaded(deviceId);
        if (device != null) return device.kind().block;
        var copy = device(mainframeId, deviceId);
        return copy == null ? null : copy.kind().block;
    }

    /** Display name of a redstone device ("Hall lights", "Lamp 3f2a"), or null. */
    public static @Nullable String name(UUID mainframeId, UUID deviceId) {
        var device = loaded(deviceId);
        if (device != null) return device.record().displayName();
        var copy = device(mainframeId, deviceId);
        return copy == null ? null : copy.displayName();
    }

    /** Group of a redstone device ("" = none), or null if unknown. */
    public static @Nullable String group(UUID mainframeId, UUID deviceId) {
        var device = loaded(deviceId);
        if (device != null) return device.group();
        var copy = device(mainframeId, deviceId);
        return copy == null ? null : copy.group();
    }

    /** {@code [{"id","kind","name","group"}]} of a mainframe's devices (network detail panel). */
    public static JsonArray summary(UUID mainframeId) {
        var array = new JsonArray();
        for (var d : devices(mainframeId)) {
            var o = new JsonObject();
            o.addProperty("id", d.id().toString());
            o.addProperty("kind", d.kind().id);
            o.addProperty("name", d.displayName());
            o.addProperty("group", d.group());
            array.add(o);
        }
        return array;
    }

    /** Dashboard rename / regroup (validated by the caller); null keeps that part. */
    public static void rename(RsDeviceBlockEntity device, @Nullable String name, @Nullable String group) {
        device.setNameAndGroup(name == null ? device.name() : name, group == null ? device.group() : group);
    }

    /** Mark a mainframe's cards for a rebuild (tests, settings changed elsewhere). */
    public static void markDirty(UUID mainframeId) {
        DIRTY.add(mainframeId);
    }

    /** Ids of the loaded devices (tests). */
    static List<UUID> loadedIds() {
        return new ArrayList<>(LOADED.keySet());
    }
}
