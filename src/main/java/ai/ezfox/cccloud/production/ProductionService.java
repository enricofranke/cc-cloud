package ai.ezfox.cccloud.production;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.bridge.BridgeCore;
import ai.ezfox.cccloud.bridge.BridgeSection;
import ai.ezfox.cccloud.bridge.ItemNames;
import ai.ezfox.cccloud.compat.card.EnergyCard;
import ai.ezfox.cccloud.compat.server.MainframeSection;
import ai.ezfox.cccloud.history.HistoryService;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.DashboardSection;
import ai.ezfox.cccloud.web.ClientMessages;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Game side of the v0.10 production statistics: samples the AE2/RS storages behind every mainframe's cards (at most
 * one storage per tick, each every {@code sampleSeconds}, from the bridge's item index snapshot) and the energy cards,
 * answers the dashboard ({@code production} messages) and the public API, and keeps the pinned charts of the Production
 * section. Everything is in memory except the pins. Server thread only.
 */
public final class ProductionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final LongSupplier CLOCK = System::currentTimeMillis;
    /** section id → storage statistics. */
    private static final Map<UUID, Storage> STORAGES = new HashMap<>();
    /** mainframe id → energy statistics. */
    private static final Map<UUID, Energy> ENERGY = new HashMap<>();
    private static final Map<UUID, ProductionSection> SECTIONS = new HashMap<>();
    private static long tick;
    private static int window = -1;

    private static final class Storage {
        final UUID mainframeId;
        final ProductionTracker tracker;
        long nextTick;

        Storage(UUID mainframeId, ProductionTracker tracker, long nextTick) {
            this.mainframeId = mainframeId;
            this.tracker = tracker;
            this.nextTick = nextTick;
        }
    }

    private static final class Energy {
        final EnergyTotals totals;
        long nextTick;

        Energy(EnergyTotals totals, long nextTick) {
            this.totals = totals;
            this.nextTick = nextTick;
        }
    }

    /** The result of a request: {@code data} on success, else {@code error}. */
    public record Result(@Nullable String error, @Nullable JsonObject data) {
        static Result fail(String error) {
            return new Result(error, null);
        }
    }

    private ProductionService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            STORAGES.clear();
            ENERGY.clear();
            SECTIONS.clear();
            window = -1;
        });
    }

    public static boolean enabled() {
        return CloudConfig.get(CloudConfig.PRODUCTION_ENABLED);
    }

    static int sampleSeconds() {
        return CloudConfig.get(CloudConfig.PRODUCTION_SAMPLE_SECONDS);
    }

    /** Stable id of a mainframe's Production section. */
    public static UUID sectionId(UUID mainframeId) {
        return MainframeSection.sectionId(mainframeId, ProductionSection.KIND);
    }

    /** The storage behind a section: a linked bridge, or an uplink in direct mode on a bridge. */
    static @Nullable BridgeCore core(DashboardSection section) {
        if (section instanceof BridgeSection bridge) return bridge.host().bridgeCore();
        if (section instanceof CloudUplinkBlockEntity uplink && uplink.storageBridge() != null) return uplink.storageBridge().bridgeCore();
        return null;
    }

    // ---- sampling ----------------------------------------------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        long tickCost = ai.ezfox.cccloud.network.TickCost.start();
        try {
            tick++;
            if (!enabled()) {
                if (!STORAGES.isEmpty() || !ENERGY.isEmpty() || !SECTIONS.isEmpty()) {
                    STORAGES.clear();
                    ENERGY.clear();
                    for (var section : SECTIONS.values()) {
                        CloudNetwork.unregisterSection(section);
                        section.removeFromDashboard();
                    }
                    SECTIONS.clear();
                }
                return;
            }
            int w = CloudConfig.get(CloudConfig.PRODUCTION_WINDOW);
            if (w != window) { // a new window size starts every series fresh
                window = w;
                STORAGES.clear();
                ENERGY.clear();
            }
            if (tick % 20 == 0) refresh(server);
            sampleOneStorage(server);
            sampleEnergy(server);
        } finally {
            ai.ezfox.cccloud.network.TickCost.end(ai.ezfox.cccloud.network.TickCost.GLOBAL, tickCost);
        }
    }

    /** Every second: which storages exist (one per bridge, only published sections) and the Production sections. */
    private static void refresh(MinecraftServer server) {
        var seen = new HashSet<UUID>();
        var cores = new IdentityHashMap<BridgeCore, Boolean>();
        // Bridge sections first: an uplink on a linked bridge shows the bridge's section, not its own.
        var sections = new ArrayList<>(CloudNetwork.sections());
        sections.sort((a, b) -> Boolean.compare(!(a instanceof BridgeSection), !(b instanceof BridgeSection)));
        for (var section : sections) {
            var link = section.getLink();
            if (link == null) continue;
            var core = core(section);
            if (core == null || cores.put(core, Boolean.TRUE) != null) continue;
            if (!WebHub.INSTANCE.hasUplink(link.id(), section.sectionId().toString())) continue;
            seen.add(section.sectionId());
            var storage = STORAGES.get(section.sectionId());
            if (storage == null || !storage.mainframeId.equals(link.id())) {
                STORAGES.put(section.sectionId(), new Storage(link.id(), new ProductionTracker(window, CloudConfig.get(CloudConfig.PRODUCTION_TRACKED_ITEMS)),
                    tick + 1 + STORAGES.size()));
            }
        }
        STORAGES.keySet().retainAll(seen);
        var mainframes = new HashSet<UUID>();
        for (var mainframe : new ArrayList<>(CloudNetwork.mainframes())) {
            if (mainframe.isRemoved() || mainframe.getLevel() == null) continue;
            mainframes.add(mainframe.getMainframeId());
            updateSection(server, mainframe.getMainframeId());
        }
        SECTIONS.entrySet().removeIf(e -> {
            if (mainframes.contains(e.getKey())) return false;
            CloudNetwork.unregisterSection(e.getValue());
            return true;
        });
    }

    /** At most one storage per tick: the most overdue one. */
    private static void sampleOneStorage(MinecraftServer server) {
        Map.Entry<UUID, Storage> due = null;
        for (var e : STORAGES.entrySet()) {
            if (e.getValue().nextTick <= tick && (due == null || e.getValue().nextTick < due.getValue().nextTick)) due = e;
        }
        if (due == null) return;
        due.getValue().nextTick = tick + sampleSeconds() * 20L;
        sample(server, due.getKey(), due.getValue(), CLOCK.getAsLong());
    }

    private static void sample(MinecraftServer server, UUID sectionId, Storage storage, long now) {
        var section = CloudNetwork.section(sectionId);
        var core = section == null ? null : core(section);
        if (core == null) return;
        if (!core.isConnected()) {
            storage.tracker.pause();
            return;
        }
        try {
            if (!storage.tracker.sample(core.index(), core.takeCrafted(), now)) return;
        } catch (RuntimeException e) {
            CCCloud.LOGGER.warn("[cccloud] Production statistics: sampling {} failed", section.getTitle(), e);
            return;
        }
        var prod = SECTIONS.get(storage.mainframeId);
        if (prod == null) return;
        for (var pin : ProductionData.get(server).pins(storage.mainframeId)) {
            if (!pin.uplinkId().equals(sectionId.toString())) continue;
            if (pin.kind().equals("storage")) prod.push(pin.id(), storage.tracker.lastNetRate());
            else if (pin.kind().equals("item")) prod.push(pin.id(), storage.tracker.lastItemRate(pin.name()));
        }
    }

    /** Every {@code sampleSeconds}: the energy cards of every mainframe (cheap: the cards' last refresh). */
    private static void sampleEnergy(MinecraftServer server) {
        if (tick % (sampleSeconds() * 20L) != 7) return;
        var readings = new HashMap<UUID, List<EnergyTotals.Card>>();
        for (var uplink : CloudNetwork.uplinks()) {
            var link = uplink.getLink();
            if (link == null || !(uplink.deviceCard() instanceof EnergyCard card)) continue;
            var reading = card.reading();
            if (reading == null) continue;
            readings.computeIfAbsent(link.id(), k -> new ArrayList<>()).add(new EnergyTotals.Card(uplink.getUplinkId().toString(), uplink.getTitle(),
                reading.stored(), reading.capacity(), reading.in(), reading.out(), reading.measured()));
        }
        ENERGY.keySet().retainAll(readings.keySet());
        for (var e : readings.entrySet()) {
            var energy = ENERGY.computeIfAbsent(e.getKey(), k -> new Energy(new EnergyTotals(window), 0));
            energy.totals.sample(e.getValue());
            var prod = SECTIONS.get(e.getKey());
            if (prod == null) continue;
            for (var pin : ProductionData.get(server).pins(e.getKey())) if (pin.kind().equals("energy")) prod.push(pin.id(), energy.totals.lastNet());
        }
    }

    /**
     * GameTests: samples one storage at {@code now}, whatever its schedule. The storage is taken out of the automatic
     * sampling (so parallel tests and the tick never mix their own samples in); {@code fresh} starts its windows anew.
     */
    public static boolean sampleForTest(MinecraftServer server, UUID sectionId, long now, boolean fresh) {
        refresh(server);
        var storage = STORAGES.get(sectionId);
        if (storage == null) return false;
        if (fresh) {
            storage = new Storage(storage.mainframeId, new ProductionTracker(window, CloudConfig.get(CloudConfig.PRODUCTION_TRACKED_ITEMS)), Long.MAX_VALUE);
            STORAGES.put(sectionId, storage);
        }
        storage.nextTick = Long.MAX_VALUE;
        sample(server, sectionId, storage, now);
        return true;
    }

    // ---- section -----------------------------------------------------------------------------------------------------

    private static void updateSection(MinecraftServer server, UUID mainframeId) {
        var mainframe = CloudNetwork.mainframe(mainframeId);
        var pins = ProductionData.get(server).pins(mainframeId);
        var section = SECTIONS.get(mainframeId);
        if (section != null && (pins.isEmpty() || mainframe == null || section.mainframe() != mainframe)) {
            CloudNetwork.unregisterSection(section);
            SECTIONS.remove(mainframeId);
            if (pins.isEmpty()) section.removeFromDashboard();
            section = null;
        }
        if (pins.isEmpty() || mainframe == null || mainframe.getLevel() == null) return;
        if (section == null) {
            section = new ProductionSection(mainframe);
            if (!CloudNetwork.registerSection(section)) return;
            SECTIONS.put(mainframeId, section);
        }
        section.update(pins, window, sampleSeconds());
    }

    /** The mainframe was broken. */
    public static void forget(MinecraftServer server, UUID mainframeId) {
        STORAGES.values().removeIf(s -> s.mainframeId.equals(mainframeId));
        ENERGY.remove(mainframeId);
        var section = SECTIONS.remove(mainframeId);
        if (section != null) CloudNetwork.unregisterSection(section);
        ProductionData.get(server).remove(mainframeId);
    }

    // ---- requests ----------------------------------------------------------------------------------------------------

    /** A storage the session may see: published on this mainframe, not restricted (or with view.positions), storage.search. */
    private static boolean visible(UUID mainframeId, UUID sectionId, Storage storage, Set<Perm> perms) {
        if (!storage.mainframeId.equals(mainframeId) || !perms.contains(Perm.STORAGE_SEARCH)) return false;
        return !WebHub.INSTANCE.isRestricted(mainframeId, sectionId.toString()) || perms.contains(Perm.VIEW_POSITIONS);
    }

    /** The overview (dashboard and API): storages and energy the permissions allow; pins only for the dashboard. */
    public static JsonObject overview(MinecraftServer server, UUID mainframeId, Set<Perm> perms, boolean withPins) {
        var json = new JsonObject();
        json.addProperty("interval", sampleSeconds());
        json.addProperty("window", Math.max(window, 0));
        long since = 0;
        var storages = new JsonArray();
        for (var e : STORAGES.entrySet()) {
            var storage = e.getValue();
            if (!visible(mainframeId, e.getKey(), storage, perms)) continue;
            var section = CloudNetwork.section(e.getKey());
            var core = section == null ? null : core(section);
            var row = storage.tracker.overview();
            row.addProperty("uplinkId", e.getKey().toString());
            row.addProperty("title", section == null ? "" : section.getTitle());
            row.addProperty("bridge", core == null ? "" : core.kind());
            row.addProperty("online", core != null && core.isConnected());
            storages.add(row);
            if (storage.tracker.since() != 0 && (since == 0 || storage.tracker.since() < since)) since = storage.tracker.since();
        }
        if (since == 0) json.add("since", JsonNull.INSTANCE);
        else json.addProperty("since", since);
        json.add("storages", storages);
        var energy = ENERGY.get(mainframeId);
        json.add("energy", energy == null || energy.totals.empty() || !perms.contains(Perm.VIEW) ? JsonNull.INSTANCE : energy.totals.json());
        if (withPins) {
            var pins = new JsonArray();
            for (var pin : ProductionData.get(server).pins(mainframeId)) {
                var p = new JsonObject();
                p.addProperty("id", pin.id());
                p.addProperty("kind", pin.kind());
                if (!pin.uplinkId().isEmpty()) p.addProperty("uplinkId", pin.uplinkId());
                if (!pin.name().isEmpty()) p.addProperty("name", pin.name());
                p.addProperty("label", pin.label());
                pins.add(p);
            }
            json.add("pins", pins);
            json.addProperty("pinLimit", ProductionData.MAX_PINS);
        }
        return json;
    }

    /**
     * A dashboard request (the session's permissions were checked by the web layer and are re-checked here).
     *
     * @param who for the audit log of pin changes
     */
    public static Result handle(MinecraftServer server, UUID mainframeId, Set<Perm> perms, ClientMessages.Production request, String who) {
        if (!enabled()) return Result.fail("disabled");
        return switch (request.op()) {
            case "overview" -> new Result(null, overview(server, mainframeId, perms, true));
            case "item" -> item(server, mainframeId, perms, request.uplinkId(), request.name());
            case "pin" -> pin(server, mainframeId, perms, request, who);
            case "unpin" -> unpin(server, mainframeId, request.id(), who);
            default -> Result.fail("invalid_message");
        };
    }

    private static Result item(MinecraftServer server, UUID mainframeId, Set<Perm> perms, @Nullable UUID sectionId, @Nullable String name) {
        if (sectionId == null || name == null) return Result.fail("invalid_message");
        var storage = STORAGES.get(sectionId);
        if (storage == null || !visible(mainframeId, sectionId, storage, perms)) return Result.fail("unknown_storage");
        var item = ItemNames.byName(name);
        if (item == null) return Result.fail("unknown_item");
        var section = CloudNetwork.section(sectionId);
        var core = section == null ? null : core(section);
        var json = storage.tracker.item(name);
        json.addProperty("name", name);
        var row = storage.tracker.row(name);
        json.addProperty("displayName", row != null ? row.displayName() : ItemNames.displayName(item));
        json.addProperty("count", row != null ? row.count() : 0);
        json.add("jobs", core == null ? new JsonArray() : core.jobRowsFor(name));
        json.add("tasks", core == null || !core.isConnected() ? new JsonArray() : core.runningTasks(16));
        boolean tracked = core != null && core.trackedItems().contains(name);
        json.add("history", tracked ? HistoryService.itemSparkline(server, mainframeId, sectionId.toString(), name) : JsonNull.INSTANCE);
        return new Result(null, json);
    }

    private static Result pin(MinecraftServer server, UUID mainframeId, Set<Perm> perms, ClientMessages.Production request, String who) {
        String kind = request.kind();
        if (kind == null) return Result.fail("invalid_message");
        String uplinkId = "", name = "", label;
        if (kind.equals("energy")) {
            label = "Energy net";
        } else {
            if (!perms.contains(Perm.STORAGE_SEARCH)) return Result.fail(perms.contains(Perm.ACT_BUTTONS) ? "not_allowed" : "read_only");
            var sectionId = request.uplinkId();
            var storage = sectionId == null ? null : STORAGES.get(sectionId);
            if (storage == null || !visible(mainframeId, sectionId, storage, perms)) return Result.fail("unknown_storage");
            uplinkId = sectionId.toString();
            var section = CloudNetwork.section(sectionId);
            String title = section == null || section.getTitle().isEmpty() ? "Storage" : section.getTitle();
            if (kind.equals("item")) {
                var item = request.name() == null ? null : ItemNames.byName(request.name());
                if (item == null) return Result.fail("unknown_item");
                name = request.name();
                label = ItemNames.displayName(item) + " /min";
            } else {
                label = title + " net /min";
            }
        }
        if (label.length() > ai.ezfox.cccloud.uplink.Limits.LABEL) label = label.substring(0, ai.ezfox.cccloud.uplink.Limits.LABEL);
        var data = ProductionData.get(server);
        var list = new ArrayList<>(data.pins(mainframeId));
        for (var pin : list) {
            if (pin.kind().equals(kind) && pin.uplinkId().equals(uplinkId) && pin.name().equals(name)) return new Result(null, idJson(pin.id()));
        }
        if (list.size() >= ProductionData.MAX_PINS) return Result.fail("too_many");
        String id;
        do {
            byte[] raw = new byte[4];
            RANDOM.nextBytes(raw);
            id = HexFormat.of().formatHex(raw);
        } while (idTaken(list, id));
        list.add(new ProductionData.Pin(id, kind, uplinkId, name, label));
        data.setPins(mainframeId, list);
        WebAuthData.access(server).audit(mainframeId, who + " pinned the production chart '" + label + "'");
        updateSection(server, mainframeId);
        return new Result(null, idJson(id));
    }

    private static boolean idTaken(List<ProductionData.Pin> list, String id) {
        for (var pin : list) if (pin.id().equals(id)) return true;
        return false;
    }

    private static Result unpin(MinecraftServer server, UUID mainframeId, @Nullable String id, String who) {
        var data = ProductionData.get(server);
        var list = new ArrayList<>(data.pins(mainframeId));
        ProductionData.Pin removed = null;
        for (var pin : list) if (pin.id().equals(id)) removed = pin;
        if (removed == null) return Result.fail("unknown_pin");
        list.remove(removed);
        data.setPins(mainframeId, list);
        WebAuthData.access(server).audit(mainframeId, who + " removed the production chart '" + removed.label() + "'");
        updateSection(server, mainframeId);
        return new Result(null, new JsonObject());
    }

    private static JsonObject idJson(String id) {
        var json = new JsonObject();
        json.addProperty("id", id);
        return json;
    }
}
