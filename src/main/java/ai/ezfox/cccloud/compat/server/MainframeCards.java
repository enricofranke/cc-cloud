package ai.ezfox.cccloud.compat.server;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.network.CloudNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the mainframe cards (Server, Claims, Backups) of every loaded mainframe: creates their sections, refreshes them
 * once per second, samples their charts every tick and removes them when the mainframe goes away or the card is
 * switched off in the config. Listens to the server tick itself, so the mainframe block entity needs no changes.
 */
public final class MainframeCards {
    private record Entry(String kind, BooleanSupplier enabled, Function<CloudMainframeBlockEntity, MainframeCard> factory) {
    }

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Map<String, MainframeSection>> SECTIONS = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static boolean listening;

    private MainframeCards() {
    }

    /** Registers a card kind every mainframe shows while {@code enabled} (config) is true. Call during mod construction. */
    public static synchronized void register(String kind, BooleanSupplier enabled, Function<CloudMainframeBlockEntity, MainframeCard> factory) {
        ENTRIES.add(new Entry(kind, enabled, factory));
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(MainframeCards::onServerTick);
            NeoForge.EVENT_BUS.addListener(MainframeCards::onServerStopped);
        }
    }

    /** The section of a mainframe card, or null (tests, diagnostics). */
    public static @Nullable MainframeSection section(UUID mainframeId, String kind) {
        var map = SECTIONS.get(mainframeId);
        return map == null ? null : map.get(kind);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        long tickCost = ai.ezfox.cccloud.network.TickCost.start();
        try {
            tick(event.getServer());
        } finally {
            ai.ezfox.cccloud.network.TickCost.end(ai.ezfox.cccloud.network.TickCost.CARDS, tickCost);
        }
    }

    static void tick(MinecraftServer server) {
        boolean refresh = server.getTickCount() % 20 == 0;
        var seen = new HashSet<UUID>();
        for (var mainframe : new ArrayList<>(CloudNetwork.mainframes())) {
            if (mainframe.isRemoved() || mainframe.getLevel() == null) continue;
            seen.add(mainframe.getMainframeId());
            var map = SECTIONS.computeIfAbsent(mainframe.getMainframeId(), id -> new LinkedHashMap<>());
            for (var entry : ENTRIES) {
                var section = map.get(entry.kind());
                boolean enabled = !FAILED.contains(entry.kind()) && enabled(entry);
                if (section != null && (!enabled || section.mainframe() != mainframe)) {
                    remove(section, !enabled);
                    map.remove(entry.kind());
                    section = null;
                }
                if (section == null && enabled) {
                    section = create(entry, mainframe);
                    if (section == null) continue;
                    map.put(entry.kind(), section);
                }
                if (section == null) continue;
                section.tick(mainframe.getLevel().getGameTime(), refresh);
                if (section.failed()) {
                    FAILED.add(entry.kind());
                    remove(section, true);
                    map.remove(entry.kind());
                }
            }
        }
        // Unloaded or removed mainframes: stop routing to their sections (the hub shows them offline / forgot them).
        var it = SECTIONS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (seen.contains(e.getKey())) continue;
            for (var section : e.getValue().values()) CloudNetwork.unregisterSection(section);
            it.remove();
        }
    }

    private static boolean enabled(Entry entry) {
        try {
            return entry.enabled().getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static @Nullable MainframeSection create(Entry entry, CloudMainframeBlockEntity mainframe) {
        try {
            var card = entry.factory().apply(mainframe);
            if (card == null) return null;
            var section = new MainframeSection(mainframe, card);
            if (!CloudNetwork.registerSection(section)) return null;
            return section;
        } catch (LinkageError | RuntimeException e) {
            FAILED.add(entry.kind());
            CloudLog.LOGGER.warn("[cccloud] The '{}' mainframe card is disabled: {}", entry.kind(), e.toString());
            return null;
        }
    }

    private static void remove(MainframeSection section, boolean fromDashboard) {
        CloudNetwork.unregisterSection(section);
        if (fromDashboard) section.removeFromDashboard();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        SECTIONS.clear();
        FAILED.clear();
    }
}
