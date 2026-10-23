package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.AccessResolver;
import ai.ezfox.cccloud.access.CloudPermissions;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.access.ServerPermissions;
import ai.ezfox.cccloud.layout.LayoutData;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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

    private AccessResolver resolver() {
        return Access.resolver(server);
    }

    @Override
    public Role role(AuthStore.Device device, UUID mainframeId) {
        return resolver().deviceRole(device, mainframeId);
    }

    @Override
    public Role authorize(AuthStore.Device device, UUID mainframeId, String ip) {
        var resolution = resolver().resolveDevice(device, mainframeId);
        if (resolution.role() == Role.NONE) return Role.NONE;
        auth.touch(device.tokenHash(), ip);
        if (resolution.source() == AccessResolver.Source.SERVER_ADMIN) {
            Access.auditAdmin(server, mainframeId, device.playerName(), "Dashboard viewed by admin");
        }
        return resolution.role();
    }

    @Override
    public void requestApproval(AuthStore.Pending pending) {
        var targets = new ArrayList<ServerPlayer>();
        String name;
        if (pending.isAccount()) {
            // Account codes: only the player whose code it is may approve.
            var player = server.getPlayerList().getPlayer(pending.account());
            if (player != null) targets.add(player);
            name = null;
        } else {
            UUID mainframeId = pending.mainframeId();
            if (pending.issuer() != null) {
                var issuer = server.getPlayerList().getPlayer(pending.issuer());
                if (issuer != null && Access.canManage(issuer, mainframeId)) targets.add(issuer);
            }
            if (targets.isEmpty()) {
                var access = WebAuthData.access(server).access(mainframeId);
                if (access != null && access.owner() != null) {
                    var owner = server.getPlayerList().getPlayer(access.owner());
                    if (owner != null) targets.add(owner);
                }
            }
            name = mainframeName(mainframeId);
        }
        String what = name != null ? name : "your account";
        if (targets.isEmpty()) {
            CloudLog.LOGGER.warn("[cccloud] Device '{}' ({}) waits for approval on {}, but nobody who may approve is online", pending.deviceName(), pending.ip(), what);
            return;
        }
        CloudLog.LOGGER.info("[cccloud] Device '{}' from {} asks to connect to {} – waiting for in-game approval", pending.deviceName(), pending.ip(), what);
        for (var player : targets) {
            // Players with the mainframe screen open see the request there (live update), no chat needed.
            if (pending.mainframeId() != null && ai.ezfox.cccloud.gui.GuiNetwork.hasScreenOpen(player, pending.mainframeId())) {
                ai.ezfox.cccloud.gui.GuiNetwork.push(player, "approval_request");
                continue;
            }
            var allow = Component.translatable("cccloud.approval.allow").withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/cccloud approve " + pending.id()))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cccloud.approval.allow_hover"))));
            var deny = Component.translatable("cccloud.approval.deny").withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/cccloud deny " + pending.id()))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cccloud.approval.deny_hover"))));
            if (pending.isAccount()) {
                player.sendSystemMessage(Component.translatable("cccloud.approval.account_request",
                    Component.literal(pending.deviceName()).withStyle(ChatFormatting.AQUA),
                    Component.literal(pending.ip()).withStyle(ChatFormatting.GRAY), allow, deny));
            } else {
                player.sendSystemMessage(Component.translatable("cccloud.approval.request",
                    Component.literal(pending.deviceName()).withStyle(ChatFormatting.AQUA),
                    Component.literal(pending.ip()).withStyle(ChatFormatting.GRAY), name, allow, deny));
            }
        }
    }

    @Override
    public CompletableFuture<ActionResult> dispatchAction(AuthStore.Device device, UUID mainframeId, ClientMessages.Action action, boolean pinVerified, String ip) {
        String where = action.uplinkId() + "/" + action.id();
        CompletableFuture<ActionResult> result;
        try {
            var uplink = CloudNetwork.section(action.uplinkId());
            if (uplink != null && !uplink.getTitle().isEmpty()) where = uplink.getTitle() + "/" + action.id();
            // Live role check on the server thread (the session's cached role may be up to 5 s old).
            String error = AccessGate.check(role(device, mainframeId), AccessGate.Kind.ACTION);
            if (error == null) error = checkUplink(mainframeId, action.uplinkId());
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
                CloudLog.LOGGER.info("[cccloud] {} ({}) pressed {} = {}{}", device.name(), device.playerName(), target, value, r.jobId() != null ? " (job " + r.jobId() + ")" : "");
            } else {
                CloudLog.LOGGER.info("[cccloud] Rejected action of {} ({}) on {} = {}: {}", device.name(), ip, target, value, r == null ? ex : r.error());
            }
        });
    }

    /** Mainframe online + enabled, uplink loaded and linked to that mainframe. Null if all is fine. */
    private static @Nullable String checkUplink(UUID mainframeId, UUID uplinkId) {
        var mainframe = CloudNetwork.mainframe(mainframeId);
        if (mainframe == null || !mainframe.isOnline()) return mainframe != null && "no_power".equals(mainframe.reason()) ? "no_power" : "offline";
        if (!mainframe.isEnabled()) return "disabled";
        var uplink = CloudNetwork.section(uplinkId);
        if (uplink == null || uplink.getLink() == null || !uplink.getLink().id().equals(mainframeId)) return "unknown_uplink";
        return null;
    }

    @Override
    public QueryResult dispatchQuery(AuthStore.Device device, UUID mainframeId, ClientMessages.Query query) {
        String error = AccessGate.check(role(device, mainframeId), AccessGate.Kind.QUERY);
        if (error == null) error = checkUplink(mainframeId, query.uplinkId());
        if (error != null) {
            return QueryResult.fail(switch (error) {
                case "disabled", "no_power" -> "offline";
                case "unknown_uplink" -> "unknown_widget";
                default -> error;
            });
        }
        return CloudNetwork.section(query.uplinkId()).handleQuery(query.id(), query.q());
    }

    @Override
    public @Nullable String setLayout(AuthStore.Device device, UUID mainframeId, @Nullable JsonObject layout) {
        String error = AccessGate.check(role(device, mainframeId), AccessGate.Kind.LAYOUT);
        if (error != null) return error;
        LayoutData.get(server).set(mainframeId, layout);
        CloudLog.LOGGER.info("[cccloud] {} ({}) saved the dashboard layout of {}", device.name(), device.playerName(), mainframeName(mainframeId));
        return null;
    }

    @Override
    public @Nullable String unlinkNode(AuthStore.Device device, UUID mainframeId, String nodeId) {
        String error = AccessGate.check(role(device, mainframeId), AccessGate.Kind.NET_UNLINK);
        if (error != null) return error;
        UUID id = ClientMessages.parseUuid(nodeId);
        if (id == null) return "unknown_node";
        if (id.equals(mainframeId)) return "not_allowed"; // the mainframe cannot be unlinked
        var section = CloudNetwork.section(id);
        if (section == null || section.getLink() == null || !section.getLink().id().equals(mainframeId)) {
            for (var relay : CloudNetwork.relays()) if (relay.relayId().equals(id)) return "not_allowed"; // relays are not linked
            return "unknown_node";
        }
        String by = device.playerName() + " (web)";
        if (section instanceof ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity uplink) uplink.unlink(by);
        else if (section instanceof ai.ezfox.cccloud.bridge.BridgeSection bridge) bridge.unlink(by);
        else return "not_allowed";
        WebAuthData.access(server).audit(mainframeId, device.playerName() + " unlinked " + (section.getTitle().isEmpty() ? nodeId : section.getTitle()) + " (network map)");
        ai.ezfox.cccloud.network.NetworkService.markDirty();
        return null;
    }

    @Override
    public void onPinLockout(AuthStore.Device device, UUID mainframeId, String ip) {
        String name = mainframeName(mainframeId);
        CloudLog.LOGGER.warn("[cccloud] Device '{}' ({}) locked for 5 minutes after {} wrong PINs on {}", device.name(), ip, AuthStore.PIN_MAX_FAILURES, name);
        var access = WebAuthData.access(server).access(mainframeId);
        if (access == null || access.owner() == null) return;
        var owner = server.getPlayerList().getPlayer(access.owner());
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

    @Override
    public AccessApi.Response api(Router.Id route, List<AuthStore.Device> devices, String path, String body, String ip) {
        var permissions = CloudPermissions.of(server);
        // Banned players and players without cccloud.use cannot use any API.
        var allowed = new ArrayList<AuthStore.Device>();
        for (var d : devices) {
            if (!permissions.banned(d.player()) && permissions.has(d.player(), ServerPermissions.Node.USE)) allowed.add(d);
        }
        if (allowed.isEmpty()) return AccessApi.error(403, "forbidden");
        for (var d : allowed) auth.touch(d.tokenHash(), ip.isEmpty() ? null : ip);
        return new AccessApi(auth, resolver(), players(), hooks()).handle(route, allowed, path, body);
    }

    private AccessApi.Players players() {
        return new AccessApi.Players() {
            @Override
            public AccessApi.@Nullable Known byName(String name) {
                var online = server.getPlayerList().getPlayerByName(name);
                if (online != null) return new AccessApi.Known(online.getUUID(), online.getGameProfile().name());
                // Players the mod already knows (roles, owners, devices): no blocking profile lookups from the web.
                var access = WebAuthData.access(server);
                for (var id : access.mainframes()) {
                    var a = access.access(id);
                    if (a == null) continue;
                    if (a.owner() != null && a.ownerName().equalsIgnoreCase(name)) return new AccessApi.Known(a.owner(), a.ownerName());
                    for (var g : a.grants().entrySet()) if (g.getValue().name().equalsIgnoreCase(name)) return new AccessApi.Known(g.getKey(), g.getValue().name());
                }
                for (var d : auth.deviceMap().values()) if (d.playerName().equalsIgnoreCase(name)) return new AccessApi.Known(d.player(), d.playerName());
                return null;
            }

            @Override
            public String name(UUID uuid) {
                return CloudPermissions.nameOf(server, uuid);
            }
        };
    }

    private AccessApi.Hooks hooks() {
        return new AccessApi.Hooks() {
            @Override
            public void accessChanged(UUID mainframeId) {
                CloudWebServer.recheck(server, mainframeId);
            }

            @Override
            public void closeDevice(String tokenHash) {
                WebHub.INSTANCE.closeDevice(tokenHash);
            }

            @Override
            public void closeShare(String shareId, String reason) {
                WebHub.INSTANCE.closeShare(shareId, reason);
            }

            @Override
            public int shareViewers(String shareId) {
                return WebHub.INSTANCE.shareViewers(shareId);
            }

            @Override
            public WebHub.@Nullable MainframeInfo info(UUID mainframeId) {
                var info = WebHub.INSTANCE.info(mainframeId);
                if (info != null) return info;
                var access = WebAuthData.access(server).access(mainframeId);
                return access == null ? null : new WebHub.MainframeInfo(mainframeId, mainframeName(mainframeId), false, true);
            }

            @Override
            public Set<UUID> mainframes() {
                var set = new LinkedHashSet<UUID>();
                for (var info : WebHub.INSTANCE.mainframes()) set.add(info.id());
                return set;
            }

            @Override
            public void log(String message) {
                CloudLog.LOGGER.info("[cccloud] {}", message);
            }
        };
    }
}
