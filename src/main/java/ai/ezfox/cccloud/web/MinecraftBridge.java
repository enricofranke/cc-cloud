package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.AccessResolver;
import ai.ezfox.cccloud.access.CloudPermissions;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.access.ServerPermissions;
import ai.ezfox.cccloud.layout.LayoutData;
import ai.ezfox.cccloud.layout.LayoutValidator;
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
public final class MinecraftBridge implements GameBridge {
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
    public AccessResolver.Resolution access(AuthStore.Device device, UUID mainframeId) {
        return resolver().resolveDevice(device, mainframeId);
    }

    private @Nullable String check(AuthStore.Device device, UUID mainframeId, ai.ezfox.cccloud.access.Perm perm) {
        var r = access(device, mainframeId);
        return AccessGate.check(r.role(), r.perms(), perm);
    }

    @Override
    public AccessResolver.Resolution authorize(AuthStore.Device device, UUID mainframeId, String ip) {
        var resolution = resolver().resolveDevice(device, mainframeId);
        if (resolution.role() == Role.NONE) return resolution;
        auth.touch(device.tokenHash(), ip);
        if (resolution.source() == AccessResolver.Source.SERVER_ADMIN) {
            Access.auditAdmin(server, mainframeId, device.playerName(), "Dashboard viewed by admin");
        }
        return resolution;
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
                if (issuer != null && Access.has(issuer, mainframeId, ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE)) targets.add(issuer);
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
            if (pending.mainframeId() != null) pushApproval(pending.mainframeId(), pending.deviceName());
            return;
        }
        CloudLog.LOGGER.info("[cccloud] Device '{}' from {} asks to connect to {} – waiting for in-game approval", pending.deviceName(), pending.ip(), what);
        if (pending.mainframeId() != null) pushApproval(pending.mainframeId(), pending.deviceName());
        for (var player : targets) {
            // Always in chat (clickable, with the time left); an open mainframe screen shows it on top as well.
            ApprovalPrompt.send(player, pending, name);
            if (pending.mainframeId() != null && ai.ezfox.cccloud.gui.GuiNetwork.hasScreenOpen(player, pending.mainframeId())) {
                ai.ezfox.cccloud.gui.GuiNetwork.push(player, "approval_request");
            }
        }
    }

    @Override
    public @Nullable String terminalOpen(AuthStore.Device device, UUID mainframeId, UUID uplinkId, ai.ezfox.cccloud.terminal.TerminalService.Viewer viewer) {
        String error = check(device, mainframeId, ai.ezfox.cccloud.access.Perm.TERMINAL);
        if (error != null) return error;
        boolean already = ai.ezfox.cccloud.terminal.TerminalService.isOpen(mainframeId, uplinkId, viewer);
        error = ai.ezfox.cccloud.terminal.TerminalService.open(mainframeId, uplinkId, viewer);
        if (error == null && !already) {
            var section = ai.ezfox.cccloud.network.CloudNetwork.section(uplinkId);
            String text = device.name() + " (" + device.playerName() + ") opened the terminal behind " + (section != null ? section.getTitle() : uplinkId);
            WebAuthData.access(server).audit(mainframeId, text);
            CloudLog.LOGGER.info("[cccloud] {} on {}", text, mainframeName(mainframeId));
        }
        return error;
    }

    @Override
    public @Nullable String terminalInput(AuthStore.Device device, UUID mainframeId, UUID uplinkId, ai.ezfox.cccloud.terminal.TerminalService.Viewer viewer,
                                          com.google.gson.JsonElement events) {
        String error = check(device, mainframeId, ai.ezfox.cccloud.access.Perm.TERMINAL);
        if (error != null) return error;
        var frame = ai.ezfox.cccloud.terminal.TerminalService.lastFrame(mainframeId, uplinkId);
        var parsed = ai.ezfox.cccloud.terminal.TerminalInput.parse(events, frame != null && frame.colour(), frame != null ? frame.width() : 51,
            frame != null ? frame.height() : 19);
        if (parsed.events() == null) return parsed.error();
        return ai.ezfox.cccloud.terminal.TerminalService.input(mainframeId, uplinkId, viewer, parsed.events());
    }

    @Override
    public void terminalClose(UUID mainframeId, @Nullable UUID uplinkId, ai.ezfox.cccloud.terminal.TerminalService.Viewer viewer) {
        if (uplinkId == null) ai.ezfox.cccloud.terminal.TerminalService.closeAll(viewer);
        else ai.ezfox.cccloud.terminal.TerminalService.close(mainframeId, uplinkId, viewer);
    }

    /** v0.5 D: push "approval needed" (server thread). */
    private void pushApproval(UUID mainframeId, String deviceName) {
        if (server.isSameThread()) ai.ezfox.cccloud.push.PushService.approvalPending(mainframeId, deviceName);
        else server.execute(() -> ai.ezfox.cccloud.push.PushService.approvalPending(mainframeId, deviceName));
    }

    @Override
    public CompletableFuture<ActionResult> dispatchAction(AuthStore.Device device, UUID mainframeId, ClientMessages.Action action, boolean pinVerified, String ip) {
        String where = action.uplinkId() + "/" + action.id();
        CompletableFuture<ActionResult> result;
        try {
            var uplink = CloudNetwork.section(action.uplinkId());
            if (uplink != null && !uplink.getTitle().isEmpty()) where = uplink.getTitle() + "/" + action.id();
            // Live role check on the server thread (the session's cached role may be up to 5 s old).
            String error = check(device, mainframeId, WebHub.INSTANCE.actionPerm(mainframeId, action.uplinkId().toString(), action.id()));
            if (error == null) error = checkUplink(mainframeId, action.uplinkId());
            result = error != null ? ActionResult.done(error)
                : uplink.handleAction(action.id(), action.value(), action.op(), device.name(), pinVerified);
        } catch (RuntimeException e) {
            CloudLog.LOGGER.error("[cccloud] Error while handling action {} from '{}'", where, device.name(), e);
            result = ActionResult.done("internal_error");
        }
        String target = where;
        String value = Json.GSON.toJson(action.value());
        var section = CloudNetwork.section(action.uplinkId());
        String sectionTitle = section == null ? "" : section.getTitle();
        return result.whenComplete((r, ex) -> {
            if (r != null && r.ok()) {
                ai.ezfox.cccloud.api.CloudEvents.action(new ai.ezfox.cccloud.api.CloudEvents.Action(mainframeId, action.uplinkId(), sectionTitle, action.id(),
                    action.value(), device.player(), device.playerName(), device.name()));
                CloudLog.LOGGER.info("[cccloud] {} ({}) pressed {} = {}{}", device.name(), device.playerName(), target, value, r.jobId() != null ? " (job " + r.jobId() + ")" : "");
            } else {
                CloudLog.LOGGER.info("[cccloud] Rejected action of {} ({}) on {} = {}: {}", device.name(), ip, target, value, r == null ? ex : r.error());
            }
        });
    }

    /** Mainframe online + enabled, uplink loaded and linked to that mainframe. Null if all is fine. */
    private static @Nullable String checkUplink(UUID mainframeId, UUID uplinkId) {
        return SectionChecks.check(mainframeId, uplinkId);
    }

    @Override
    public QueryResult dispatchQuery(AuthStore.Device device, UUID mainframeId, ClientMessages.Query query) {
        String error = check(device, mainframeId, AccessGate.Kind.QUERY.perm);
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
        String error = check(device, mainframeId, AccessGate.Kind.LAYOUT.perm);
        if (error != null) return error;
        if (layout != null && LayoutValidator.widgetMinimums(layout, widgetTypes(mainframeId)::get) != null) return "invalid_layout";
        LayoutData.get(server).set(mainframeId, layout);
        CloudLog.LOGGER.info("[cccloud] {} ({}) saved the dashboard layout of {}", device.name(), device.playerName(), mainframeName(mainframeId));
        return null;
    }

    /** Widget key ({@code <uplinkId>/<widgetId>}) → type of every published widget of a mainframe. */
    private static java.util.Map<String, String> widgetTypes(UUID mainframeId) {
        var types = new java.util.HashMap<String, String>();
        for (var section : WebHub.INSTANCE.sectionSnapshots(mainframeId)) {
            if (!(section.get("uplinkId") instanceof com.google.gson.JsonPrimitive id) || !(section.get("widgets") instanceof com.google.gson.JsonArray widgets)) continue;
            for (var w : widgets) {
                if (w instanceof JsonObject wo && wo.get("id") instanceof com.google.gson.JsonPrimitive wid && wo.get("type") instanceof com.google.gson.JsonPrimitive type) {
                    types.put(id.getAsString() + "/" + wid.getAsString(), type.getAsString());
                }
            }
        }
        return types;
    }

    @Override
    public @Nullable String unlinkNode(AuthStore.Device device, UUID mainframeId, String nodeId) {
        String error = check(device, mainframeId, AccessGate.Kind.NET_UNLINK.perm);
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
    public @Nullable String redstoneOverride(AuthStore.Device device, UUID mainframeId, ClientMessages.RedstoneOverride override) {
        String error = check(device, mainframeId, AccessGate.Kind.REDSTONE.perm);
        if (error != null) return error;
        error = ai.ezfox.cccloud.redstone.RedstoneService.override(mainframeId, override.channel(), override.level(), override.seconds());
        if (error == null) {
            WebAuthData.access(server).audit(mainframeId, device.playerName() + (override.level() == null ? " cleared the override of redstone channel "
                : " set redstone channel ") + override.channel() + (override.level() == null ? "" : " to " + override.level()
                + (override.seconds() == null ? " until reset" : " for " + override.seconds() + " s")));
        }
        return error;
    }

    @Override
    public GameBridge.RuleResult ruleCommand(AuthStore.Device device, UUID mainframeId, ClientMessages.RuleCommand command) {
        String error = check(device, mainframeId, (command.edits() ? AccessGate.Kind.RULE_EDIT : AccessGate.Kind.RULE_OPERATE).perm);
        if (error != null) return new GameBridge.RuleResult(error, null, null);
        String who = device.playerName() + " (" + device.name() + ")";
        switch (command.op()) {
            case "rule_set" -> {
                var edit = ai.ezfox.cccloud.rules.RuleService.set(server, mainframeId, command.rule());
                if (edit.error() != null) {
                    String e = edit.error();
                    int colon = e.indexOf(':');
                    return colon < 0 ? new GameBridge.RuleResult(e, null, null) : new GameBridge.RuleResult(e.substring(0, colon), e.substring(colon + 1), null);
                }
                WebAuthData.access(server).audit(mainframeId, who + " saved rule " + edit.id());
                return new GameBridge.RuleResult(null, null, edit.id());
            }
            case "rule_delete" -> {
                String e = ai.ezfox.cccloud.rules.RuleService.delete(server, mainframeId, command.id());
                if (e == null) WebAuthData.access(server).audit(mainframeId, who + " deleted rule " + command.id());
                return new GameBridge.RuleResult(e, null, null);
            }
            case "rule_enable" -> {
                return new GameBridge.RuleResult(ai.ezfox.cccloud.rules.RuleService.setEnabled(server, mainframeId, command.id(), command.enabled()), null, null);
            }
            case "rule_run" -> {
                return new GameBridge.RuleResult(ai.ezfox.cccloud.rules.RuleService.runNow(server, mainframeId, command.id()), null, null);
            }
            default -> {
                return new GameBridge.RuleResult("invalid_message", null, null);
            }
        }
    }

    @Override
    public GameBridge.StorageResult storage(AuthStore.Device device, UUID mainframeId, ClientMessages.Storage request) {
        String error = check(device, mainframeId, AccessGate.storagePerm(request.op()));
        if (error == null) error = SectionChecks.check(mainframeId, request.uplinkId());
        if (error != null) return GameBridge.StorageResult.fail(error);
        var result = ai.ezfox.cccloud.bridge.StorageService.handle(mainframeId, request.uplinkId(), request.widgetId(), request.op(), request.args());
        if (result.error() == null && !ClientMessages.STORAGE_READ_OPS.contains(request.op())) {
            CloudLog.LOGGER.info("[cccloud] {} ({}) storage {} on {}: {}", device.name(), device.playerName(), request.op(), mainframeName(mainframeId), request.args());
        }
        return new GameBridge.StorageResult(result.error(), result.data());
    }

    @Override
    public void craftPreview(AuthStore.Device device, UUID mainframeId, ClientMessages.CraftPreview request, java.util.function.Consumer<GameBridge.StorageResult> done) {
        String error = check(device, mainframeId, ai.ezfox.cccloud.access.Perm.STORAGE_CRAFT);
        if (error == null) error = SectionChecks.check(mainframeId, request.uplinkId());
        if (error != null) {
            done.accept(GameBridge.StorageResult.fail(error));
            return;
        }
        ai.ezfox.cccloud.bridge.StorageService.preview(mainframeId, request.uplinkId(), request.widgetId(), request.name(), request.count(),
            r -> done.accept(new GameBridge.StorageResult(r.error(), r.data())));
    }

    @Override
    public CompletableFuture<GameBridge.MapResult> mapTile(AuthStore.Device device, UUID mainframeId, String dim, boolean slice, int y, int tx, int tz) {
        return mapTile(server, access(device, mainframeId), mainframeId, dim, slice, y, tx, tz);
    }

    @Override
    public CompletableFuture<GameBridge.VoxelResult> voxels(AuthStore.Device device, UUID mainframeId, String dim, int cx, int cy, int cz) {
        return voxels(server, access(device, mainframeId), mainframeId, dim, cx, cy, cz);
    }

    /** The voxel endpoint after authentication (public for the GameTests). */
    public static CompletableFuture<GameBridge.VoxelResult> voxels(MinecraftServer server, AccessResolver.Resolution access, UUID mainframeId, String dim,
                                                                   int cx, int cy, int cz) {
        if (!ai.ezfox.cccloud.CloudConfig.get(ai.ezfox.cccloud.CloudConfig.VOXEL_VIEW)) return CompletableFuture.completedFuture(new GameBridge.VoxelResult(404, null, "disabled"));
        if (access.role() == Role.NONE) return CompletableFuture.completedFuture(new GameBridge.VoxelResult(403, null, "no_access"));
        if (AccessGate.check(access.role(), access.perms(), ai.ezfox.cccloud.access.Perm.VIEW_POSITIONS) != null) {
            return CompletableFuture.completedFuture(new GameBridge.VoxelResult(403, null, "not_allowed"));
        }
        var level = ai.ezfox.cccloud.map.MapTileService.level(server, dim);
        if (level == null) return CompletableFuture.completedFuture(new GameBridge.VoxelResult(404, null, "unknown_dimension"));
        if (!ai.ezfox.cccloud.voxel.VoxelService.allowed(mainframeId, dim, cx, cy, cz)) {
            return CompletableFuture.completedFuture(new GameBridge.VoxelResult(403, null, "out_of_range"));
        }
        return ai.ezfox.cccloud.voxel.VoxelService.section(level, cx, cy, cz)
            .thenApply(json -> new GameBridge.VoxelResult(json == null ? 204 : 200, json, null));
    }

    /** The map tile endpoint after authentication (public for the GameTests). */
    public static CompletableFuture<GameBridge.MapResult> mapTile(MinecraftServer server, AccessResolver.Resolution access, UUID mainframeId, String dim,
                                                                  boolean slice, int y, int tx, int tz) {
        if (access.role() == Role.NONE) return CompletableFuture.completedFuture(new GameBridge.MapResult(403, null, null, "no_access"));
        String error = AccessGate.check(access.role(), access.perms(), ai.ezfox.cccloud.access.Perm.VIEW_POSITIONS);
        if (error != null) return CompletableFuture.completedFuture(new GameBridge.MapResult(403, null, null, "not_allowed"));
        if (ai.ezfox.cccloud.map.MapTileService.level(server, dim) == null) return CompletableFuture.completedFuture(new GameBridge.MapResult(404, null, null, "unknown_dimension"));
        if (!ai.ezfox.cccloud.map.MapAccess.allowed(mainframeId, dim, tx, tz)) {
            return CompletableFuture.completedFuture(new GameBridge.MapResult(403, null, null, "out_of_range"));
        }
        var key = new ai.ezfox.cccloud.map.MapTileService.Key(dim, slice ? ai.ezfox.cccloud.map.MapTileService.Layer.SLICE : ai.ezfox.cccloud.map.MapTileService.Layer.SURFACE,
            slice ? y : 0, tx, tz);
        return ai.ezfox.cccloud.map.MapTileService.tile(server, key)
            .thenApply(tile -> new GameBridge.MapResult(tile.png() == null ? 204 : 200, tile.png(), tile.etag(), null));
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
    public AccessApi.Response script(GameBridge.ScriptRequest request) {
        auth.touch(request.device().tokenHash(), request.ip());
        return ScriptApi.handle(server, request);
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
        if (route == Router.Id.HISTORY) return history(allowed.getFirst(), path);
        var api = new AccessApi(auth, resolver(), players(), hooks());
        if (route == Router.Id.PUSH_GET || route == Router.Id.PUSH_POST) api.withPush(pushApi());
        return api.handle(route, allowed, path, body);
    }

    /** v0.5 F: {@code /api/mainframes/<id>/history/…} (permissions resolved here, the rest in HistoryApi). */
    private AccessApi.Response history(AuthStore.Device device, String path) {
        var m = ai.ezfox.cccloud.history.HistoryApi.PATH.matcher(path);
        if (!m.matches()) return AccessApi.error(404, "not_found");
        UUID mainframeId = ClientMessages.parseUuid(path.split("/")[3]);
        if (mainframeId == null) return AccessApi.error(404, "not_found");
        var resolution = resolver().resolveDevice(device, mainframeId);
        if (resolution.role() == Role.NONE) return AccessApi.error(403, "no_access");
        String uplinkId = m.group(4), id = m.group(5);
        return ai.ezfox.cccloud.history.HistoryApi.handle(ai.ezfox.cccloud.history.HistoryData.get(server).store(), mainframeId, resolution.perms(),
            WebHub.INSTANCE.isRestricted(mainframeId, uplinkId), m.group(1), m.group(2), m.group(3), uplinkId, id,
            m.group(3).equals("w") ? WebHub.INSTANCE.historyRange(mainframeId, uplinkId, id) : null,
            ai.ezfox.cccloud.history.HistoryService.retentionHours(mainframeId), ai.ezfox.cccloud.history.HistoryService.now());
    }

    private ai.ezfox.cccloud.push.PushApi pushApi() {
        return new ai.ezfox.cccloud.push.PushApi(ai.ezfox.cccloud.push.PushData.get(server).store(), new ai.ezfox.cccloud.push.PushApi.Hooks() {
            @Override
            public boolean enabled() {
                return ai.ezfox.cccloud.push.PushService.enabled();
            }

            @Override
            public java.util.Set<String> allowedHosts() {
                return ai.ezfox.cccloud.push.PushService.allowedHosts();
            }

            @Override
            public @Nullable String test(UUID mainframeId, ai.ezfox.cccloud.push.PushTarget target) {
                return ai.ezfox.cccloud.push.PushService.test(mainframeId, target);
            }

            @Override
            public com.google.gson.@Nullable JsonObject status(String targetId) {
                return ai.ezfox.cccloud.push.PushService.status(targetId);
            }

            @Override
            public void audit(UUID mainframeId, String message) {
                WebAuthData.access(server).audit(mainframeId, message);
                CloudLog.LOGGER.info("[cccloud] {}", message);
            }
        });
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
