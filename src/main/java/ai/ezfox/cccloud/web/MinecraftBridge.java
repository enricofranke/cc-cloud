package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.uplink.Json;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import ai.ezfox.cccloud.layout.LayoutData;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import org.jspecify.annotations.Nullable;

/** The real {@link GameBridge}. All methods except {@link #serverExecutor()} run on the server thread. */
final class MinecraftBridge implements GameBridge {
    private final MinecraftServer server;
    private final AuthStore auth;

    MinecraftBridge(MinecraftServer server, AuthStore auth) {
        this.server = server;
        this.auth = auth;
    }

    @Override
    public Executor serverExecutor() {
        return server;
    }

    /** Owner, trusted player or operator (level 2+) of the mainframe, and not banned. */
    static boolean canManage(MinecraftServer server, AuthStore auth, UUID mainframeId, UUID player, String name) {
        var id = new NameAndId(player, name);
        if (server.getPlayerList().getBans().isBanned(id)) return false;
        if (server.getProfilePermissions(id).hasPermission(Permissions.COMMANDS_GAMEMASTER)) return true;
        var acl = auth.acl(mainframeId);
        return acl != null && acl.allows(player);
    }

    @Override
    public @Nullable String checkRights(AuthStore.Device device) {
        var id = new NameAndId(device.player(), device.playerName());
        if (server.getPlayerList().getBans().isBanned(id)) return "banned";
        if (!canManage(server, auth, device.mainframeId(), device.player(), device.playerName())) return "no_rights";
        auth.touch(device.tokenHash());
        return null;
    }

    @Override
    public void requestApproval(AuthStore.Pending pending) {
        var targets = new ArrayList<ServerPlayer>();
        if (pending.issuer() != null) {
            var issuer = server.getPlayerList().getPlayer(pending.issuer());
            if (issuer != null && canManage(server, auth, pending.mainframeId(), issuer.getUUID(), issuer.getGameProfile().name())) targets.add(issuer);
        }
        if (targets.isEmpty()) {
            var acl = auth.acl(pending.mainframeId());
            if (acl != null && acl.owner() != null) {
                var owner = server.getPlayerList().getPlayer(acl.owner());
                if (owner != null) targets.add(owner);
            }
        }
        String name = mainframeName(pending.mainframeId());
        if (targets.isEmpty()) {
            CloudLog.LOGGER.warn("[cccloud] Device '{}' ({}) waits for approval on {}, but neither the code issuer nor the owner is online",
                pending.deviceName(), pending.ip(), name);
            return;
        }
        CloudLog.LOGGER.info("[cccloud] Device '{}' from {} asks to connect to {} – waiting for in-game approval", pending.deviceName(), pending.ip(), name);
        for (var player : targets) {
            var allow = Component.translatable("cccloud.approval.allow").withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/cccloud approve " + pending.id()))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cccloud.approval.allow_hover"))));
            var deny = Component.translatable("cccloud.approval.deny").withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/cccloud deny " + pending.id()))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cccloud.approval.deny_hover"))));
            player.sendSystemMessage(Component.translatable("cccloud.approval.request",
                Component.literal(pending.deviceName()).withStyle(ChatFormatting.AQUA),
                Component.literal(pending.ip()).withStyle(ChatFormatting.GRAY), name, allow, deny));
        }
    }

    @Override
    public CompletableFuture<ActionResult> dispatchAction(AuthStore.Device device, ClientMessages.Action action, boolean pinVerified, String ip) {
        String where = action.uplinkId() + "/" + action.id();
        CompletableFuture<ActionResult> result;
        try {
            var uplink = CloudNetwork.uplink(action.uplinkId());
            if (uplink != null && !uplink.getTitle().isEmpty()) where = uplink.getTitle() + "/" + action.id();
            String error = checkRights(device);
            if (error == null) error = checkUplink(device, action.uplinkId());
            result = error != null ? ActionResult.done(error)
                : uplink.handleAction(action.id(), action.value(), action.op(), device.name(), pinVerified);
        } catch (RuntimeException e) {
            CloudLog.LOGGER.error("[cccloud] Error while handling action {} from '{}'", where, device.name(), e);
            result = ActionResult.done("internal_error");
        }
        String target = where;
        String value = Json.GSON.toJson(action.value());
        return result.whenComplete((r, ex) -> {
            if (r != null && r.ok()) {
                CloudLog.LOGGER.info("[cccloud] {} pressed {} = {}{}", device.name(), target, value, r.jobId() != null ? " (job " + r.jobId() + ")" : "");
            } else {
                CloudLog.LOGGER.info("[cccloud] Rejected action of {} ({}) on {} = {}: {}", device.name(), ip, target, value, r == null ? ex : r.error());
            }
        });
    }

    /** Mainframe online + enabled, uplink loaded and linked to the device's mainframe. Null if all is fine. */
    private static @Nullable String checkUplink(AuthStore.Device device, UUID uplinkId) {
        var mainframe = CloudNetwork.mainframe(device.mainframeId());
        if (mainframe == null || !mainframe.isOnline()) return "offline";
        if (!mainframe.isEnabled()) return "disabled";
        var uplink = CloudNetwork.uplink(uplinkId);
        if (uplink == null || uplink.getLink() == null || !uplink.getLink().id().equals(device.mainframeId())) return "unknown_uplink";
        return null;
    }

    @Override
    public QueryResult dispatchQuery(AuthStore.Device device, ClientMessages.Query query) {
        String error = checkRights(device);
        if (error == null) error = checkUplink(device, query.uplinkId());
        if (error != null) return QueryResult.fail(error.equals("disabled") || error.equals("unknown_uplink") ? (error.equals("disabled") ? "offline" : "unknown_widget") : error);
        return CloudNetwork.uplink(query.uplinkId()).handleQuery(query.id(), query.q());
    }

    @Override
    public @Nullable String setLayout(AuthStore.Device device, @Nullable JsonObject layout) {
        String error = checkRights(device);
        if (error != null) return error;
        LayoutData.get(server).set(device.mainframeId(), layout);
        CloudLog.LOGGER.info("[cccloud] {} saved the dashboard layout of {}", device.name(), mainframeName(device.mainframeId()));
        return null;
    }

    @Override
    public void onPinLockout(AuthStore.Device device, String ip) {
        String name = mainframeName(device.mainframeId());
        CloudLog.LOGGER.warn("[cccloud] Device '{}' ({}) locked for 5 minutes after {} wrong PINs on {}", device.name(), ip, AuthStore.PIN_MAX_FAILURES, name);
        var acl = auth.acl(device.mainframeId());
        if (acl == null || acl.owner() == null) return;
        var owner = server.getPlayerList().getPlayer(acl.owner());
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("cccloud.pin.locked_notice", device.name(), ip, name).withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public String mainframeName(UUID mainframeId) {
        var mainframe = CloudNetwork.mainframe(mainframeId);
        if (mainframe != null) return mainframe.getDisplayName();
        var info = WebHub.INSTANCE.info(mainframeId);
        return info != null ? info.name() : "Mainframe";
    }
}
