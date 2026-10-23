package ai.ezfox.cccloud.api;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Server-side notifications for add-ons (the KubeJS bindings use them): a dashboard action that went through, a device
 * linked to a mainframe, an alert sent to a dashboard. Posting is cheap when nobody listens; events from other threads
 * (web server, computer threads) are queued and delivered on the server thread at the end of the tick, so listeners
 * always run on the server thread. Listener errors are logged once per listener and never reach the caller.
 */
public final class CloudEvents {
    /** A dashboard (browser, tablet, in-world display) action that the section accepted. */
    public record Action(UUID mainframeId, UUID sectionId, String sectionTitle, String widgetId, JsonElement value, @Nullable UUID player,
                         String playerName, String device) {
    }

    /** A device (uplink, bridge) bound to a mainframe with a Link Card. */
    public record DeviceLinked(UUID mainframeId, UUID deviceId, String type, String dimension, BlockPos pos, UUID player, String playerName) {
    }

    /** An alert sent to a mainframe's dashboards ({@code level}: info, warn, crit). */
    public record Alert(UUID mainframeId, @Nullable String sectionId, String text, String level) {
    }

    private static final List<Consumer<Action>> ACTION = new CopyOnWriteArrayList<>();
    private static final List<Consumer<DeviceLinked>> LINKED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Alert>> ALERT = new CopyOnWriteArrayList<>();
    private static final Queue<Runnable> PENDING = new ConcurrentLinkedQueue<>();
    private static final int MAX_PENDING = 1024;
    private static volatile boolean ticking;

    private CloudEvents() {
    }

    public static void onAction(Consumer<Action> listener) {
        ACTION.add(listener);
        listen();
    }

    public static void onDeviceLinked(Consumer<DeviceLinked> listener) {
        LINKED.add(listener);
        listen();
    }

    public static void onAlert(Consumer<Alert> listener) {
        ALERT.add(listener);
        listen();
    }

    public static void action(Action event) {
        post(ACTION, event);
    }

    public static void deviceLinked(DeviceLinked event) {
        post(LINKED, event);
    }

    public static void alert(Alert event) {
        post(ALERT, event);
    }

    private static <T> void post(List<Consumer<T>> listeners, T event) {
        if (listeners.isEmpty() || PENDING.size() >= MAX_PENDING) return;
        PENDING.add(() -> {
            for (var listener : listeners) {
                try {
                    listener.accept(event);
                } catch (RuntimeException | LinkageError e) {
                    ai.ezfox.cccloud.CloudLog.LOGGER.warn("[cccloud] Event listener failed on {}: {}", event.getClass().getSimpleName(), e.toString());
                }
            }
        });
    }

    /** Delivers queued events now (server thread; GameTests call it to avoid waiting a tick). */
    public static void flush() {
        Runnable r;
        int budget = MAX_PENDING;
        while (budget-- > 0 && (r = PENDING.poll()) != null) r.run();
    }

    private static synchronized void listen() {
        if (ticking) return;
        ticking = true;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> flush());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> PENDING.clear());
    }
}
