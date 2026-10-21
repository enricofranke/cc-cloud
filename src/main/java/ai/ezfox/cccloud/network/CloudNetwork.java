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

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 == 0) CloudWebServer.serverTick(event.getServer());
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        server = event.getServer();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        try {
            ai.ezfox.cccloud.layout.LayoutData.get(event.getServer()).publishAll();
        } catch (RuntimeException e) {
            CCCloud.LOGGER.error("[cccloud] Could not load dashboard layouts", e);
        }
        CloudWebServer.start(event.getServer());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        CloudWebServer.stop();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        MAINFRAMES.clear();
        UPLINKS.clear();
        WebHub.INSTANCE.clear();
        server = null;
        CCCloud.LOGGER.debug("CC: Cloud network state cleared");
    }
}
