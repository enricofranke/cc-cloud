package ai.ezfox.cccloud.tablet;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.ingame.AlertThrottle;
import ai.ezfox.cccloud.ingame.InGameAccess;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import ai.ezfox.cccloud.web.WebHub;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Alert toasts of the Advanced Cloud Tablet: every alert of a mainframe goes to the online players who carry an advanced
 * tablet linked to it (hands, inventory, Curios; open or closed, any distance), unless that tablet's switch is off.
 * Same filter as the dashboard, with the live rights: {@code view}, restricted sections and the private forms of alerts
 * need {@code view.positions}. At most {@link AlertThrottle#BURST} at once per player, then one every 4 s; the rest is
 * counted in the next toast. Published from any thread, delivered on the server tick.
 */
final class TabletAlerts {
    private record Published(UUID mainframeId, @Nullable String uplinkId, String text, String level, @Nullable String publicText) {
    }

    private static final int MAX_QUEUED = 256;
    private static final int SUMMARY_INTERVAL = 20;
    private static final ConcurrentLinkedQueue<Published> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED = new AtomicInteger();
    /** Server thread: per online player who got an alert (kept, so a steady trickle stays limited). */
    private static final Map<UUID, AlertThrottle> THROTTLES = new HashMap<>();
    private static final Map<UUID, String> LAST_NAME = new HashMap<>();
    /** How a toast reaches the player (GameTests capture it instead). */
    static BiConsumer<ServerPlayer, InGamePayloads.TabletAlert> sender = PacketDistributor::sendToPlayer;
    /** Who may get toasts (GameTests add fake players, which are not in the player list). */
    static Function<MinecraftServer, List<ServerPlayer>> players = server -> server.getPlayerList().getPlayers();
    /** The rate limit's clock (GameTests run faster than real time and move it on). */
    static java.util.function.LongSupplier clock = System::nanoTime;
    private static int ticks;
    /** Some player has held-back alerts: their summary is still to come. */
    private static boolean holding;

    private TabletAlerts() {
    }

    static void init() {
        WebHub.alertTap = (mainframeId, uplinkId, text, level, publicText) -> {
            if (QUEUED.get() >= MAX_QUEUED) return;
            QUEUED.incrementAndGet();
            QUEUE.add(new Published(mainframeId, uplinkId, text, level, publicText));
        };
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            THROTTLES.remove(e.getEntity().getUUID());
            LAST_NAME.remove(e.getEntity().getUUID());
        });
    }

    static void clear() {
        QUEUE.clear();
        QUEUED.set(0);
        THROTTLES.clear();
        LAST_NAME.clear();
        holding = false;
    }

    /** Something to do this tick (a cheap check, it runs every tick). */
    static boolean busy() {
        return !QUEUE.isEmpty() || holding;
    }

    static void tick(MinecraftServer server) {
        if (!QUEUE.isEmpty()) deliver(server);
        if (++ticks % SUMMARY_INTERVAL == 0) summaries(server);
    }

    private static void deliver(MinecraftServer server) {
        var carried = new HashMap<ServerPlayer, Set<UUID>>();
        for (var player : players.apply(server)) {
            var linked = advancedTablets(player);
            if (!linked.isEmpty()) carried.put(player, linked);
        }
        Published alert;
        while ((alert = QUEUE.poll()) != null) {
            QUEUED.decrementAndGet();
            for (var entry : carried.entrySet()) {
                if (entry.getValue().contains(alert.mainframeId())) toast(entry.getKey(), alert);
            }
        }
    }

    /** The mainframes the player's advanced tablets are linked to, without those switched off. */
    private static Set<UUID> advancedTablets(ServerPlayer player) {
        var linked = new HashSet<UUID>();
        for (var stack : TabletService.carried(player)) {
            if (!(stack.getItem() instanceof CloudTabletItem item) || !item.isAdvanced()) continue;
            var link = stack.get(ModRegistry.LINKED_MAINFRAME.get());
            if (link != null && !stack.getOrDefault(ModRegistry.TABLET_ALERTS_OFF.get(), false)) linked.add(link.id());
        }
        return linked;
    }

    private static void toast(ServerPlayer player, Published alert) {
        var perms = InGameAccess.resolve(player, alert.mainframeId()).perms();
        if (!perms.contains(Perm.VIEW)) return;
        if (!WebHub.seesRestricted(perms) && alert.uplinkId() != null && WebHub.INSTANCE.isRestricted(alert.mainframeId(), alert.uplinkId())) return;
        String text = alert.publicText() != null && !WebHub.seesNames(perms) ? alert.publicText() : alert.text();
        var info = WebHub.INSTANCE.info(alert.mainframeId());
        String name = info == null ? "" : info.name();
        LAST_NAME.put(player.getUUID(), name);
        int more = THROTTLES.computeIfAbsent(player.getUUID(), k -> new AlertThrottle(() -> clock.getAsLong())).offer();
        if (more >= 0) sender.accept(player, new InGamePayloads.TabletAlert(name, text, alert.level(), more));
        else holding = true;
    }

    /** Once a second: what the limit held back and no later alert has mentioned. */
    private static void summaries(MinecraftServer server) {
        if (!holding) return;
        var online = new HashMap<UUID, ServerPlayer>();
        for (var player : players.apply(server)) online.put(player.getUUID(), player);
        holding = false;
        for (var entry : THROTTLES.entrySet()) {
            var player = online.get(entry.getKey());
            if (player == null) continue;
            int more = entry.getValue().summary();
            if (more > 0) sender.accept(player, new InGamePayloads.TabletAlert(LAST_NAME.getOrDefault(entry.getKey(), ""), "", "info", more));
            holding |= !entry.getValue().idle();
        }
    }
}
