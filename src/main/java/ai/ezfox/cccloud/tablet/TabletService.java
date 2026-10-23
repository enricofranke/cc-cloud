package ai.ezfox.cccloud.tablet;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.ingame.InGameAccess;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import ai.ezfox.cccloud.ingame.InGameRange;
import ai.ezfox.cccloud.ingame.InGameSession;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.web.AccessGate;
import ai.ezfox.cccloud.web.ActionResult;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.ClientMessages;
import ai.ezfox.cccloud.web.InGameClients;
import ai.ezfox.cccloud.web.TokenBucket;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the Cloud Tablet: one {@link InGameSession} per open tablet, registered with the hub like a browser.
 * Every tick the queued hub messages go to the player; every second the tablet must still be carried (inventory or
 * Curios), linked to the same mainframe, in coverage (normal tablet) and the player must still have rights – otherwise
 * the screen is closed with a message. Requests are browser-format messages and run through
 * {@link InGameClients} (same validation, rights and audit log as browsers); PINs use the same lockout. Server thread.
 */
public final class TabletService {
    private static final int CHECK_INTERVAL = 20;

    /** One open tablet. */
    static final class Open {
        final ServerPlayer player;
        final int containerId;
        final UUID mainframeId;
        final boolean advanced;
        final InGameSession session;
        final TokenBucket actions = new TokenBucket(10, 10);
        final TokenBucket queries = new TokenBucket(5, 5);
        Role role;
        java.util.Set<ai.ezfox.cccloud.access.Perm> perms;
        boolean covered = true;

        Open(ServerPlayer player, int containerId, UUID mainframeId, boolean advanced, InGameSession session, Role role,
             java.util.Set<ai.ezfox.cccloud.access.Perm> perms) {
            this.perms = perms;
            this.player = player;
            this.containerId = containerId;
            this.mainframeId = mainframeId;
            this.advanced = advanced;
            this.session = session;
            this.role = role;
        }
    }

    private static final Map<UUID, Open> OPEN = new HashMap<>();
    /** How data reaches the player (GameTests capture it instead). */
    static java.util.function.BiConsumer<ServerPlayer, InGamePayloads.TabletData> sender = PacketDistributor::sendToPlayer;
    /** PBKDF2 PIN checks run here, never on the server thread. */
    private static final ThreadPoolExecutor PIN_EXECUTOR = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), r -> {
        var t = new Thread(r, "cccloud-tablet-pin");
        t.setDaemon(true);
        return t;
    });

    private TabletService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TabletService::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> OPEN.clear());
    }

    /** Test/diagnostics: the session of an open tablet. */
    public static @Nullable InGameSession session(ServerPlayer player) {
        var open = OPEN.get(player.getUUID());
        return open == null ? null : open.session;
    }

    // ---- opening ---------------------------------------------------------------------------------------------------

    /** Opens the tablet the player holds (right-click). */
    public static void open(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof CloudTabletItem item)) return;
        var link = stack.get(ModRegistry.LINKED_MAINFRAME.get());
        if (link == null) {
            player.sendSystemMessage(Component.translatable("cccloud.tablet.unlinked_hint").withStyle(ChatFormatting.YELLOW));
            return;
        }
        String error = check(player, link.id(), item.isAdvanced());
        if (error != null) {
            player.sendSystemMessage(Component.translatable("cccloud.tablet.error." + error).withStyle(ChatFormatting.RED));
            return;
        }
        var access = InGameAccess.resolve(player, link.id());
        String name = WebHub.INSTANCE.info(link.id()) != null ? WebHub.INSTANCE.info(link.id()).name() : link.describe();
        var pos = player.blockPosition();
        boolean advanced = item.isAdvanced();
        boolean control = access.perms().contains(ai.ezfox.cccloud.access.Perm.ACT_BUTTONS);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TabletMenu(id, pos, name, advanced, control), stack.getHoverName()),
            buf -> TabletMenu.writeOpen(buf, pos, name, advanced, control));
        if (player.containerMenu instanceof TabletMenu menu) attach(player, menu, link.id(), advanced);
    }

    /** Starts the session of an open tablet menu (also used by the GameTests with a fake player). */
    static void attach(ServerPlayer player, TabletMenu menu, UUID mainframeId, boolean advanced) {
        var access = InGameAccess.resolve(player, mainframeId);
        var session = new InGameSession(mainframeId, InGameClients.device(player, mainframeId, "tablet"), access.role(), access.perms());
        var old = OPEN.put(player.getUUID(), new Open(player, menu.containerId, mainframeId, advanced, session, access.role(), access.perms()));
        if (old != null) WebHub.INSTANCE.removeSession(old.session);
        WebHub.INSTANCE.addSession(session);
        flushNow(player);
    }

    /** The "open tablet" key: the first linked tablet in the hands, the inventory, then the Curios slots. */
    public static void openFromKey(ServerPlayer player) {
        var stack = findTablet(player, null);
        if (stack == null) {
            player.sendSystemMessage(Component.translatable("cccloud.tablet.none").withStyle(ChatFormatting.YELLOW));
            return;
        }
        open(player, stack);
    }

    /** A carried tablet (linked to {@code mainframeId} if not null), linked tablets first. */
    static @Nullable ItemStack findTablet(ServerPlayer player, @Nullable UUID mainframeId) {
        var candidates = new ArrayList<ItemStack>();
        candidates.add(player.getMainHandItem());
        candidates.add(player.getOffhandItem());
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) candidates.add(inventory.getItem(i));
        candidates.addAll(CuriosTablets.stacks(player));
        ItemStack unlinked = null;
        for (var stack : candidates) {
            if (!(stack.getItem() instanceof CloudTabletItem)) continue;
            var link = stack.get(ModRegistry.LINKED_MAINFRAME.get());
            if (link == null) {
                if (unlinked == null && mainframeId == null) unlinked = stack;
                continue;
            }
            if (mainframeId == null || link.id().equals(mainframeId)) return stack;
        }
        return unlinked;
    }

    /** Null if the player may use a tablet on this mainframe from where they stand, else an error code. */
    static @Nullable String check(ServerPlayer player, UUID mainframeId, boolean advanced) {
        if (!InGameAccess.canView(player, mainframeId)) return "no_rights";
        if (!advanced && !InGameRange.covered(mainframeId, player.level(), player.position())) {
            return CloudNetwork.mainframe(mainframeId) == null ? "mainframe_unloaded" : "out_of_range";
        }
        return null;
    }

    /** The menu was closed (client closed the screen, or we closed it). */
    static void closed(ServerPlayer player, int containerId) {
        var open = OPEN.get(player.getUUID());
        if (open == null || open.containerId != containerId) return;
        OPEN.remove(player.getUUID());
        WebHub.INSTANCE.removeSession(open.session);
        open.session.close(1000, "closed");
    }

    // ---- ticking ---------------------------------------------------------------------------------------------------

    private static void onServerTick(ServerTickEvent.Post event) {
        if (OPEN.isEmpty()) return;
        InGameClients.flushIfNoWebServer();
        var server = event.getServer();
        boolean check = server.getTickCount() % CHECK_INTERVAL == 0;
        for (var entry : List.copyOf(OPEN.entrySet())) {
            var open = entry.getValue();
            var player = open.player;
            if (player.hasDisconnected() || player.isRemoved() || !(player.containerMenu instanceof TabletMenu menu) || menu.containerId != open.containerId) {
                OPEN.remove(entry.getKey());
                WebHub.INSTANCE.removeSession(open.session);
                continue;
            }
            if (check) {
                String error = recheck(player, open);
                if (error != null) {
                    player.sendSystemMessage(Component.translatable("cccloud.tablet.error." + error).withStyle(ChatFormatting.RED));
                    player.closeContainer();
                    continue;
                }
            }
            flush(player, open);
        }
    }

    private static @Nullable String recheck(ServerPlayer player, Open open) {
        if (findTablet(player, open.mainframeId) == null) return "no_tablet";
        var access = InGameAccess.resolve(player, open.mainframeId);
        if (!access.perms().contains(ai.ezfox.cccloud.access.Perm.VIEW)) return "no_rights";
        if (!open.advanced) {
            open.covered = InGameRange.covered(open.mainframeId, player.level(), player.position());
            if (!open.covered) return "out_of_range";
        }
        if (access.role() != open.role || !access.perms().equals(open.perms)) {
            // Rights changed: the hub pushes the new role and (if needed) a snapshot with/without restricted sections.
            open.role = access.role();
            open.perms = access.perms();
            WebHub.INSTANCE.pushRole(open.session, access.role(), access.perms());
        }
        return null;
    }

    private static void flushNow(ServerPlayer player) {
        var open = OPEN.get(player.getUUID());
        if (open != null) flush(player, open);
    }

    private static void flush(ServerPlayer player, Open open) {
        var messages = open.session.drainMessages();
        if (messages == null) {
            // The player fell behind: start over with a fresh snapshot.
            WebHub.INSTANCE.removeSession(open.session);
            WebHub.INSTANCE.addSession(open.session);
            messages = open.session.drainMessages();
            if (messages == null) return;
        }
        if (messages.isEmpty()) return;
        var batch = new StringBuilder("[");
        for (var message : messages) {
            if (batch.length() + message.length() + 2 > InGamePayloads.MAX_DATA) {
                send(player, open, batch);
                batch = new StringBuilder("[");
            }
            if (batch.length() > 1) batch.append(',');
            batch.append(message);
        }
        send(player, open, batch);
    }

    private static void send(ServerPlayer player, Open open, StringBuilder batch) {
        if (batch.length() <= 1) return;
        batch.append(']');
        if (batch.length() > InGamePayloads.MAX_DATA) {
            CCCloud.LOGGER.warn("[cccloud] Dropped a {} KB dashboard message for {}'s tablet (limit {} KB)", batch.length() / 1024,
                player.getGameProfile().name(), InGamePayloads.MAX_DATA / 1024);
            return;
        }
        sender.accept(player, new InGamePayloads.TabletData(open.containerId, batch.toString()));
    }

    private static void reply(ServerPlayer player, int containerId, JsonObject json) {
        sender.accept(player, new InGamePayloads.TabletData(containerId, "[" + Json.GSON.toJson(json) + "]"));
    }

    private static JsonObject actionResult(@Nullable String reqId, @Nullable ActionResult result) {
        var json = new JsonObject();
        json.addProperty("t", "action_result");
        if (reqId != null) json.addProperty("reqId", reqId);
        String error = result == null ? "internal_error" : result.error();
        json.addProperty("ok", error == null);
        if (error != null) json.addProperty("error", error);
        if (result != null && result.jobId() != null) json.addProperty("jobId", result.jobId());
        return json;
    }

    // ---- requests --------------------------------------------------------------------------------------------------

    /** A browser-format request from an open tablet. */
    public static void request(ServerPlayer player, InGamePayloads.TabletRequest payload) {
        var open = OPEN.get(player.getUUID());
        if (open == null || open.containerId != payload.containerId() || !(player.containerMenu instanceof TabletMenu)) return;
        var message = ClientMessages.parse(payload.message());
        switch (message) {
            case ClientMessages.Action action -> action(player, open, action);
            case ClientMessages.Query query -> query(player, open, query);
            default -> {
            }
        }
    }

    private static void action(ServerPlayer player, Open open, ClientMessages.Action action) {
        var server = player.level().getServer();
        // The bridge checks the per-widget-kind permission live (AccessGate), exactly as for browsers.
        if (!open.advanced && !open.covered) {
            reply(player, open.containerId, actionResult(action.reqId(), ActionResult.fail("out_of_range")));
            return;
        }
        if (!open.actions.tryAcquire()) {
            reply(player, open.containerId, actionResult(action.reqId(), ActionResult.fail("rate_limited")));
            return;
        }
        var device = InGameClients.device(player, open.mainframeId, "tablet");
        boolean needsPin = WebHub.INSTANCE.widgetNeedsPin(open.mainframeId, action.uplinkId().toString(), action.id());
        if (!needsPin) {
            dispatch(player, open, device, action, false);
            return;
        }
        if (action.pin() == null) {
            reply(player, open.containerId, actionResult(action.reqId(), ActionResult.fail("pin_required")));
            return;
        }
        int containerId = open.containerId;
        try {
            PIN_EXECUTOR.execute(() -> {
                var check = InGameClients.checkPin(server, device, action.pin());
                server.execute(() -> {
                    var current = OPEN.get(player.getUUID());
                    if (current == null || current.containerId != containerId) return;
                    switch (check.result()) {
                        case OK -> dispatch(player, current, device, action, true);
                        case NOT_SET -> reply(player, containerId, actionResult(action.reqId(), ActionResult.fail("pin_not_set")));
                        case WRONG -> {
                            var json = actionResult(action.reqId(), ActionResult.fail("wrong_pin"));
                            json.addProperty("attemptsLeft", check.attemptsLeft());
                            reply(player, containerId, json);
                        }
                        case LOCKED -> {
                            var json = actionResult(action.reqId(), ActionResult.fail("pin_locked"));
                            json.addProperty("retryAfter", check.retryAfterSeconds());
                            reply(player, containerId, json);
                        }
                    }
                });
            });
        } catch (RejectedExecutionException e) {
            reply(player, containerId, actionResult(action.reqId(), ActionResult.fail("busy")));
        }
    }

    private static void dispatch(ServerPlayer player, Open open, AuthStore.Device device, ClientMessages.Action action, boolean pinVerified) {
        var server = player.level().getServer();
        int containerId = open.containerId;
        InGameClients.action(server, device, action, pinVerified).whenComplete((result, ex) ->
            server.execute(() -> reply(player, containerId, actionResult(action.reqId(), ex != null ? null : result))));
    }

    private static void query(ServerPlayer player, Open open, ClientMessages.Query query) {
        var json = new JsonObject();
        json.addProperty("t", "query_result");
        json.addProperty("reqId", query.reqId());
        if (!open.queries.tryAcquire()) {
            json.add("rows", new com.google.gson.JsonArray());
            json.addProperty("truncated", false);
            json.addProperty("error", "rate_limited");
        } else {
            var device = InGameClients.device(player, open.mainframeId, "tablet");
            var result = InGameClients.query(player.level().getServer(), device, query);
            json.add("rows", result.rows());
            json.addProperty("truncated", result.truncated());
            if (result.error() != null) json.addProperty("error", result.error());
        }
        reply(player, open.containerId, json);
    }
}
