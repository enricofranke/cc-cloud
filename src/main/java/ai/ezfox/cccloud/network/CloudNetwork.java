package ai.ezfox.cccloud.network;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.web.CloudWebServer;
import ai.ezfox.cccloud.web.WebHub;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Server-side registry of all loaded mainframes and uplinks, plus the server lifecycle of the web server.
 * Everything except {@link #server()} must only be used from the server thread.
 */
public final class CloudNetwork {
    private static volatile @Nullable MinecraftServer server;
    private static final Map<UUID, CloudMainframeBlockEntity> MAINFRAMES = new HashMap<>();
    private static final Map<UUID, CloudUplinkBlockEntity> UPLINKS = new HashMap<>();
    /** Linked ME/RS Cloud Bridges (their own dashboard sections). */
    private static final Map<UUID, DashboardSection> BRIDGE_SECTIONS = new HashMap<>();
    /** Loaded relays (v0.4 network nodes). */
    private static final Map<UUID, ai.ezfox.cccloud.relay.CloudRelayBlockEntity> RELAYS = new HashMap<>();

    private CloudNetwork() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(CloudNetwork::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(CloudNetwork::onServerStarted);
        NeoForge.EVENT_BUS.addListener(CloudNetwork::onServerStopping);
        NeoForge.EVENT_BUS.addListener(CloudNetwork::onServerStopped);
        NeoForge.EVENT_BUS.addListener(CloudNetwork::onServerTick);
    }

    public static @Nullable MinecraftServer server() {
        return server;
    }

    /** Runs a task on the server thread, if a server is running. */
    public static boolean runOnServer(Runnable task) {
        var s = server;
        if (s == null || !s.isRunning()) return false;
        s.execute(task);
        return true;
    }

    /** @return false if a different, still loaded mainframe already uses this id. */
    public static boolean registerMainframe(CloudMainframeBlockEntity mainframe) {
        var existing = MAINFRAMES.get(mainframe.getMainframeId());
        if (existing != null && existing != mainframe && !existing.isRemoved()) return false;
        MAINFRAMES.put(mainframe.getMainframeId(), mainframe);
        return true;
    }

    public static void unregisterMainframe(CloudMainframeBlockEntity mainframe) {
        MAINFRAMES.remove(mainframe.getMainframeId(), mainframe);
    }

    public static @Nullable CloudMainframeBlockEntity mainframe(@Nullable UUID id) {
        if (id == null) return null;
        var mainframe = MAINFRAMES.get(id);
        return mainframe == null || mainframe.isRemoved() ? null : mainframe;
    }

    public static Collection<CloudMainframeBlockEntity> mainframes() {
        return MAINFRAMES.values();
    }

    public static boolean registerUplink(CloudUplinkBlockEntity uplink) {
        var existing = UPLINKS.get(uplink.getUplinkId());
        if (existing != null && existing != uplink && !existing.isRemoved()) return false;
        UPLINKS.put(uplink.getUplinkId(), uplink);
        return true;
    }

    public static void unregisterUplink(CloudUplinkBlockEntity uplink) {
        UPLINKS.remove(uplink.getUplinkId(), uplink);
    }

    public static @Nullable CloudUplinkBlockEntity uplink(@Nullable UUID id) {
        if (id == null) return null;
        var uplink = UPLINKS.get(id);
        return uplink == null || uplink.isRemoved() ? null : uplink;
    }

    public static boolean registerRelay(ai.ezfox.cccloud.relay.CloudRelayBlockEntity relay) {
        var existing = RELAYS.get(relay.relayId());
        if (existing != null && existing != relay && !existing.isRemoved()) return false;
        RELAYS.put(relay.relayId(), relay);
        return true;
    }

    public static void unregisterRelay(ai.ezfox.cccloud.relay.CloudRelayBlockEntity relay) {
        RELAYS.remove(relay.relayId(), relay);
    }

    public static Collection<ai.ezfox.cccloud.relay.CloudRelayBlockEntity> relays() {
        return RELAYS.values();
    }

    public static Collection<CloudUplinkBlockEntity> uplinks() {
        return UPLINKS.values();
    }

    /** Linked bridge sections (loaded). */
    public static Collection<DashboardSection> bridgeSections() {
        return BRIDGE_SECTIONS.values();
    }

    /** @return false if another loaded section already uses this id. */
    public static boolean registerSection(DashboardSection section) {
        var existing = BRIDGE_SECTIONS.get(section.sectionId());
        if (existing != null && existing != section) return false;
        if (UPLINKS.containsKey(section.sectionId())) return false;
        BRIDGE_SECTIONS.put(section.sectionId(), section);
        return true;
    }

    public static void unregisterSection(DashboardSection section) {
        BRIDGE_SECTIONS.remove(section.sectionId(), section);
    }

    /** Every loaded section: uplinks and bridges (v0.5 F statistics). Server thread. */
    public static java.util.List<DashboardSection> sections() {
        var list = new java.util.ArrayList<DashboardSection>(UPLINKS.values());
        list.addAll(BRIDGE_SECTIONS.values());
        return list;
    }

    /** An uplink or a linked bridge by section id. */
    public static @Nullable DashboardSection section(@Nullable UUID id) {
        var uplink = uplink(id);
        if (uplink != null) return uplink;
        return id == null ? null : BRIDGE_SECTIONS.get(id);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        long cost = TickCost.start();
        NetworkService.tick(event.getServer());
        ai.ezfox.cccloud.redstone.RedstoneService.tick(event.getServer());
        if (event.getServer().getTickCount() % 100 == 0) CloudWebServer.serverTick(event.getServer());
        TickCost.end(TickCost.GLOBAL, cost);
        // GameTests only: the hub flush runs on the server tick (normally the web server's thread), not counted
        if (ai.ezfox.cccloud.web.WebHub.FLUSH_EVERY_TICK) ai.ezfox.cccloud.web.WebHub.INSTANCE.flush();
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        server = event.getServer();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        var srv = event.getServer();
        ai.ezfox.cccloud.web.WebHub.onSectionRemoved = (mainframeId, sectionId) -> {
            if (srv.isSameThread()) SectionCache.get(srv).forget(mainframeId, sectionId);
            else srv.execute(() -> SectionCache.get(srv).forget(mainframeId, sectionId));
        };
        try {
            SectionCache.get(srv).publishAll();
        } catch (RuntimeException e) {
            CCCloud.LOGGER.error("[cccloud] Could not load cached dashboard sections", e);
        }
        try {
            ai.ezfox.cccloud.layout.LayoutData.get(event.getServer()).publishAll();
        } catch (RuntimeException e) {
            CCCloud.LOGGER.error("[cccloud] Could not load dashboard layouts", e);
        }
        CloudWebServer.start(event.getServer());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        try {
            SectionCache.get(event.getServer()).captureAll();
        } catch (RuntimeException e) {
            CCCloud.LOGGER.error("[cccloud] Could not cache dashboard sections", e);
        }
        CloudWebServer.stop();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        MAINFRAMES.clear();
        UPLINKS.clear();
        BRIDGE_SECTIONS.clear();
        RELAYS.clear();
        NetworkService.clear();
        ai.ezfox.cccloud.upgrade.Upgrades.clear();
        ai.ezfox.cccloud.upgrade.ChunkAnchor.clear();
        ai.ezfox.cccloud.redstone.RedstoneService.clear();
        WebHub.INSTANCE.clear();
        server = null;
        CCCloud.LOGGER.debug("CC: Cloud network state cleared");
    }
}
