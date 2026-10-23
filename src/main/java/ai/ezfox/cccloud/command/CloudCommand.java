package ai.ezfox.cccloud.command;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.WebAuthData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /cccloud status|devices|revoke|trust|untrust|role|pin|approve|deny|link|browsers}. Mainframe commands act on the
 * mainframe the player is looking at and need the admin role (AccessResolver: owner, roles, teams, admin nodes).
 */
public final class CloudCommand {
    private static final SimpleCommandExceptionType NOT_OWNER = new SimpleCommandExceptionType(Component.translatable("cccloud.mainframe.not_owner"));
    private static final SimpleCommandExceptionType UNKNOWN_REQUEST = new SimpleCommandExceptionType(Component.translatable("cccloud.approval.unknown"));
    private static final SimpleCommandExceptionType BAD_PIN = new SimpleCommandExceptionType(Component.translatable("cccloud.pin.invalid"));
    private static final SimpleCommandExceptionType NO_MAINFRAME = new SimpleCommandExceptionType(Component.translatable("cccloud.command.no_mainframe"));
    private static final SimpleCommandExceptionType CHOOSE = new SimpleCommandExceptionType(Component.translatable("cccloud.command.choose"));
    private static final SimpleCommandExceptionType BAD_ROLE = new SimpleCommandExceptionType(Component.translatable("cccloud.command.bad_role"));
    private static final SimpleCommandExceptionType NO_USE = new SimpleCommandExceptionType(Component.translatable("cccloud.access.no_use"));
    private static final SimpleCommandExceptionType UNKNOWN_DEVICE = new SimpleCommandExceptionType(Component.translatable("cccloud.gui.notice.unknown_device"));

    private CloudCommand() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, CloudCommand::onRegister);
    }

    private static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cccloud")
            .executes(CloudCommand::help)
            .then(Commands.literal("help").executes(CloudCommand::help))
            .then(Commands.literal("status").executes(CloudCommand::status)
                .then(Commands.argument("mainframe", StringArgumentType.word()).suggests(CloudCommand::suggestMainframes).executes(CloudCommand::select)))
            .then(Commands.literal("test-token").requires(src -> Boolean.getBoolean("cccloud.devCommands") && src.getEntity() == null && src.source == src.getServer())
                .then(Commands.argument("mainframe", StringArgumentType.word()).executes(CloudCommand::testToken)))
            .then(Commands.literal("select")
                .then(Commands.argument("mainframe", StringArgumentType.word()).suggests(CloudCommand::suggestMainframes).executes(CloudCommand::select)))
            .then(Commands.literal("icons").requires(src -> hasNode(src, ai.ezfox.cccloud.access.ServerPermissions.Node.COMMAND_ICONS))
                .then(Commands.literal("status").executes(c -> icons(c, "status")))
                .then(Commands.literal("rebuild").executes(c -> icons(c, "rebuild")))
                .then(Commands.literal("clear").executes(c -> icons(c, "clear"))))
            // v0.6: all browsers of the player; "devices here" lists the browsers limited to the mainframe you look at.
            .then(Commands.literal("devices").executes(CloudCommand::browsers)
                .then(Commands.literal("here").executes(CloudCommand::devices))
                .then(Commands.literal("revoke").then(Commands.argument("id", StringArgumentType.word()).suggests(CloudCommand::suggestBrowsers)
                    .executes(CloudCommand::revokeBrowser))))
            .then(Commands.literal("revoke")
                .then(Commands.argument("device", StringArgumentType.greedyString())
                    .suggests((c, b) -> {
                        try {
                            var mainframe = target(c.getSource(), ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE);
                            var names = WebAuthData.get(c.getSource().getServer()).devices(mainframe.getMainframeId()).stream().map(AuthStore.Device::name).toList();
                            return SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(names.stream(), java.util.stream.Stream.of("all")), b);
                        } catch (CommandSyntaxException e) {
                            return b.buildFuture();
                        }
                    })
                    .executes(c -> revoke(c, StringArgumentType.getString(c, "device")))))
            .then(Commands.literal("trust")
                .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> trust(c, true))))
            .then(Commands.literal("untrust")
                .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> trust(c, false))))
            .then(Commands.literal("role")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                    .then(Commands.argument("role", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.List.of("viewer", "operator", "admin", "none"), b))
                        .executes(CloudCommand::role))))
            .then(Commands.literal("link").executes(CloudCommand::link))
            .then(Commands.literal("pin")
                .then(Commands.literal("set").then(Commands.argument("pin", StringArgumentType.word()).executes(CloudCommand::setPin)))
                .then(Commands.literal("clear").executes(CloudCommand::clearPin)))
            .then(Commands.literal("approve").executes(c -> decideNewest(c, true))
                .then(Commands.argument("request", StringArgumentType.word()).suggests(CloudCommand::suggestPending).executes(c -> decide(c, true))))
            .then(Commands.literal("deny").executes(c -> decideNewest(c, false))
                .then(Commands.argument("request", StringArgumentType.word()).suggests(CloudCommand::suggestPending).executes(c -> decide(c, false)))));
    }

    /** Mainframe chosen with {@code /cccloud select} (per player, until the server stops). */
    private static final java.util.Map<java.util.UUID, java.util.UUID> SELECTED = new java.util.concurrent.ConcurrentHashMap<>();

    /** The mainframe the player is looking at (within 8 blocks), or null. */
    private static @org.jspecify.annotations.Nullable CloudMainframeBlockEntity lookedAt(ServerPlayer player) {
        var eye = player.getEyePosition();
        var end = eye.add(player.getViewVector(1).scale(8));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        var state = player.level().getBlockState(hit.getBlockPos());
        return CloudMainframeBlock.getBlockEntity(player.level(), hit.getBlockPos(), state);
    }

    /**
     * The mainframe a command acts on: the one the player looks at, else the one chosen with {@code /cccloud select},
     * else the only one within 32 blocks, else the only one the player has the needed role on. Otherwise the candidates
     * are listed (click to select) and the command fails.
     */
    private static CloudMainframeBlockEntity target(CommandSourceStack source, ai.ezfox.cccloud.access.Perm needed) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var looked = lookedAt(player);
        if (looked != null) {
            if (!ai.ezfox.cccloud.access.Access.has(player, looked.getMainframeId(), needed)) throw NOT_OWNER.create();
            return looked;
        }
        var candidates = candidates(player, needed);
        var picked = TargetPicker.pick(candidates, SELECTED.get(player.getUUID()));
        if (picked != null) {
            var mainframe = ai.ezfox.cccloud.network.CloudNetwork.mainframe(picked);
            if (mainframe != null) return mainframe;
        }
        if (candidates.isEmpty()) throw NO_MAINFRAME.create();
        listMainframes(source, candidates);
        throw CHOOSE.create();
    }

    private static java.util.List<TargetPicker.Candidate> candidates(ServerPlayer player, ai.ezfox.cccloud.access.Perm needed) {
        var list = new java.util.ArrayList<TargetPicker.Candidate>();
        for (var mainframe : ai.ezfox.cccloud.network.CloudNetwork.mainframes()) {
            if (mainframe.isRemoved() || mainframe.getLevel() == null) continue;
            if (!ai.ezfox.cccloud.access.Access.has(player, mainframe.getMainframeId(), needed)) continue;
            double distance = mainframe.getLevel() == player.level() ? Math.sqrt(mainframe.getBlockPos().getCenter().distanceToSqr(player.position())) : -1;
            list.add(new TargetPicker.Candidate(mainframe.getMainframeId(), distance));
        }
        return list;
    }

    /** One clickable line per mainframe: name, position, distance, status. */
    private static void listMainframes(CommandSourceStack source, java.util.List<TargetPicker.Candidate> candidates) {
        source.sendSystemMessage(Component.translatable("cccloud.command.choose_header").withStyle(ChatFormatting.AQUA));
        for (var c : TargetPicker.sorted(candidates)) {
            var mainframe = ai.ezfox.cccloud.network.CloudNetwork.mainframe(c.id());
            if (mainframe == null) continue;
            var pos = mainframe.getBlockPos();
            String where = pos.getX() + " " + pos.getY() + " " + pos.getZ() + (c.distance() < 0 ? " (" + mainframe.getLevel().dimension().identifier() + ")"
                : " · " + Math.round(c.distance()) + " m");
            String command = "/cccloud select " + c.id();
            source.sendSystemMessage(Component.literal("▸ ").append(Component.literal(mainframe.getDisplayName()).withStyle(style -> style
                    .withColor(ChatFormatting.GOLD).withUnderlined(true)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(command))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.translatable("cccloud.command.select_hover")))))
                .append(Component.literal("  " + where + " · ").withStyle(ChatFormatting.GRAY))
                .append(mainframe.status().displayName()));
        }
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMainframes(
        CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer player)) return b.buildFuture();
        return SharedSuggestionProvider.suggest(candidates(player, ai.ezfox.cccloud.access.Perm.VIEW).stream().map(x -> x.id().toString()), b);
    }

    /** {@code /cccloud select <mainframe>} (also {@code /cccloud status <mainframe>}): remember it and show its status. */
    private static int select(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        java.util.UUID id;
        try {
            id = java.util.UUID.fromString(StringArgumentType.getString(c, "mainframe"));
        } catch (IllegalArgumentException e) {
            throw NO_MAINFRAME.create();
        }
        var mainframe = ai.ezfox.cccloud.network.CloudNetwork.mainframe(id);
        if (mainframe == null || !ai.ezfox.cccloud.access.Access.has(player, id, ai.ezfox.cccloud.access.Perm.VIEW)) throw NO_MAINFRAME.create();
        SELECTED.put(player.getUUID(), id);
        c.getSource().sendSystemMessage(Component.translatable("cccloud.command.selected", mainframe.getDisplayName()).withStyle(ChatFormatting.GRAY));
        return showStatus(c.getSource(), mainframe);
    }

    /** {@code /cccloud} or {@code /cccloud help}: the subcommands, each suggests itself on click. */
    private static int help(CommandContext<CommandSourceStack> c) {
        var source = c.getSource();
        source.sendSystemMessage(Component.translatable("cccloud.help.header").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        String[][] entries = {
            {"status", "/cccloud status"}, {"link", "/cccloud link"}, {"devices", "/cccloud devices"}, {"approve", "/cccloud approve"},
            {"deny", "/cccloud deny"}, {"role", "/cccloud role "}, {"trust", "/cccloud trust "}, {"pin", "/cccloud pin set "},
            {"select", "/cccloud select "}, {"devices_here", "/cccloud devices here"}};
        for (var e : entries) {
            String command = e[1];
            source.sendSystemMessage(Component.literal(command.trim()).withStyle(style -> style.withColor(ChatFormatting.GOLD)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(command))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.translatable("cccloud.help.click"))))
                .append(Component.literal(" – ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("cccloud.help." + e[0]).withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    /** Pending browser requests this player may decide (own account codes; admin of the mainframe), newest first. */
    private static java.util.List<AuthStore.Pending> pendingFor(ServerPlayer player) {
        var server = player.level().getServer();
        var list = new java.util.ArrayList<AuthStore.Pending>();
        if (server == null) return list;
        var resolver = ai.ezfox.cccloud.access.Access.resolver(server);
        for (var p : WebAuthData.get(server).pendingRequests()) {
            boolean mine = p.isAccount() ? player.getUUID().equals(p.account())
                : resolver.resolve(player.getUUID(), p.mainframeId()).has(ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE);
            if (mine) list.add(p);
        }
        list.sort((a, b) -> Long.compare(b.expiresAt(), a.expiresAt()));
        return list;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPending(
        CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer player)) {
            return SharedSuggestionProvider.suggest(WebAuthData.get(c.getSource().getServer()).pendingRequests().stream().map(AuthStore.Pending::id), b);
        }
        return SharedSuggestionProvider.suggest(pendingFor(player).stream().map(AuthStore.Pending::id), b);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBrowsers(
        CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer player)) return b.buildFuture();
        return SharedSuggestionProvider.suggest(WebAuthData.get(c.getSource().getServer()).devicesOf(player.getUUID()).stream().map(AuthStore::deviceId), b);
    }

    /** {@code /cccloud approve|deny} without an id: the newest request for this player; several → clickable list. */
    private static int decideNewest(CommandContext<CommandSourceStack> c, boolean allow) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        var pending = pendingFor(player);
        if (pending.isEmpty()) {
            c.getSource().sendSystemMessage(Component.translatable("cccloud.command.no_pending").withStyle(ChatFormatting.YELLOW));
            c.getSource().sendSystemMessage(Component.translatable("cccloud.command.pairing_howto").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        if (pending.size() > 1) {
            c.getSource().sendSystemMessage(Component.translatable("cccloud.command.several_pending", pending.size()).withStyle(ChatFormatting.AQUA));
            for (var p : pending) {
                var mainframe = p.mainframeId() == null ? null : ai.ezfox.cccloud.network.CloudNetwork.mainframe(p.mainframeId());
                ai.ezfox.cccloud.web.ApprovalPrompt.send(player, p, mainframe != null ? mainframe.getDisplayName() : "Mainframe");
            }
            return 0;
        }
        return decide(c, pending.getFirst().id(), allow);
    }

    /** Permission node check for command requirements (PermissionAPI: LuckPerms / FTB Ranks; default op level 2). */
    private static boolean hasNode(CommandSourceStack src, ai.ezfox.cccloud.access.ServerPermissions.Node node) {
        if (src.getEntity() instanceof ServerPlayer player) return ai.ezfox.cccloud.access.Access.has(player, node);
        return src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER); // console, command blocks
    }

    /** {@code /cccloud role <player> <viewer|operator|admin|none>} on the mainframe you look at. */
    private static int role(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var mainframe = target(c.getSource(), ai.ezfox.cccloud.access.Perm.ACCESS_MANAGE);
        var role = ai.ezfox.cccloud.access.Role.parse(StringArgumentType.getString(c, "role"));
        if (role == null || role == ai.ezfox.cccloud.access.Role.OWNER) throw BAD_ROLE.create();
        var caller = c.getSource().getPlayerOrException();
        var rules = ai.ezfox.cccloud.access.Access.caller(caller, mainframe.getMainframeId());
        int changed = 0;
        for (var profile : GameProfileArgument.getGameProfiles(c, "player")) {
            var current = mainframe.getGrants().get(profile.id());
            if (ai.ezfox.cccloud.access.AccessRules.changeGrant(rules, current == null ? ai.ezfox.cccloud.access.Role.NONE : current.role(),
                profile.id().equals(mainframe.getOwner()), profile.id().equals(caller.getUUID()), role, java.util.Set.of()) != null) throw NOT_OWNER.create();
            if (mainframe.setRole(profile, role)) {
                changed++;
                ai.ezfox.cccloud.web.WebAuthData.access(c.getSource().getServer()).audit(mainframe.getMainframeId(),
                    c.getSource().getTextName() + (role == ai.ezfox.cccloud.access.Role.NONE ? " removed " + profile.name() : " set " + profile.name() + " to " + role.id));
                CCCloud.LOGGER.info("[cccloud] {} set the role of {} on {} to {}", c.getSource().getTextName(), profile.name(), mainframe.getDisplayName(), role.id);
            }
            c.getSource().sendSystemMessage(Component.translatable("cccloud.command.role_set", profile.name(), Component.translatable("cccloud.role." + role.id)));
        }
        return changed;
    }

    /** {@code /cccloud link}: an account code – one login for every mainframe where the player has a role. */
    private static int link(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        if (!ai.ezfox.cccloud.access.Access.has(player, ai.ezfox.cccloud.access.ServerPermissions.Node.USE)) throw NO_USE.create();
        var code = WebAuthData.get(c.getSource().getServer()).accountCode(player.getUUID());
        long minutes = Math.max(1, (code.expiresAt() - System.currentTimeMillis() + 59_999) / 60_000);
        String formatted = AuthStore.formatCode(code.code());
        String url = ai.ezfox.cccloud.web.CloudWebServer.isRunning() ? ai.ezfox.cccloud.web.CloudWebServer.dashboardUrl() : null;
        c.getSource().sendSystemMessage(Component.translatable("cccloud.command.link_code", ai.ezfox.cccloud.web.PairingText.code(formatted), minutes)
            .append(ai.ezfox.cccloud.web.PairingText.pairLink(url, formatted)));
        return 1;
    }

    /** {@code /cccloud browsers}: every browser of the player (account and mainframe-limited tokens). */
    private static int browsers(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        var list = WebAuthData.get(c.getSource().getServer()).devicesOf(player.getUUID());
        if (list.isEmpty()) {
            c.getSource().sendSystemMessage(Component.translatable("cccloud.command.no_devices"));
            return 0;
        }
        var format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        c.getSource().sendSystemMessage(Component.translatable("cccloud.command.browsers_header", list.size()).withStyle(ChatFormatting.AQUA));
        for (var device : list) {
            String id = AuthStore.deviceId(device);
            var revoke = Component.translatable("cccloud.command.revoke_button").withStyle(style -> style.withColor(ChatFormatting.RED)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/cccloud devices revoke " + id)));
            c.getSource().sendSystemMessage(Component.translatable("cccloud.command.browser_line", device.name(), device.type(),
                device.lastIp().isEmpty() ? "-" : device.lastIp(), format.format(new Date(device.lastUsed())),
                device.isAccount() ? Component.translatable("cccloud.command.browser_account") : Component.literal(
                    ai.ezfox.cccloud.web.WebHub.INSTANCE.info(device.mainframeId()) != null ? ai.ezfox.cccloud.web.WebHub.INSTANCE.info(device.mainframeId()).name() : "Mainframe"),
                revoke));
        }
        return list.size();
    }

    private static int revokeBrowser(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "id");
        var removed = WebAuthData.get(c.getSource().getServer()).revokeDevice(id, d -> d.player().equals(player.getUUID()));
        if (removed == null) throw UNKNOWN_DEVICE.create();
        ai.ezfox.cccloud.web.WebHub.INSTANCE.closeDevice(removed.tokenHash());
        CCCloud.LOGGER.info("[cccloud] {} revoked their browser '{}'", player.getGameProfile().name(), removed.name());
        c.getSource().sendSystemMessage(Component.translatable("cccloud.command.revoked", 1));
        return 1;
    }

    /** {@code /cccloud icons status|rebuild|clear} (cccloud.command.icons). */
    private static int icons(CommandContext<CommandSourceStack> c, String what) {
        var service = ai.ezfox.cccloud.icons.IconService.current();
        if (service == null) {
            c.getSource().sendFailure(Component.translatable("cccloud.icons.unavailable"));
            return 0;
        }
        try {
            switch (what) {
                case "rebuild" -> service.rebuild();
                case "clear" -> service.clear();
                default -> {
                }
            }
        } catch (java.io.IOException e) {
            c.getSource().sendFailure(Component.literal(e.toString()));
            return 0;
        }
        if (!what.equals("status")) CCCloud.LOGGER.info("[cccloud] {} ran /cccloud icons {}", c.getSource().getTextName(), what);
        var s = service.status();
        c.getSource().sendSystemMessage(Component.translatable("cccloud.icons.status", String.format(java.util.Locale.ROOT, "%,d", s.done()),
            String.format(java.util.Locale.ROOT, "%,d", s.total()), s.queued() + s.inFlight(), s.renderers(), s.store().failed(),
            String.format(java.util.Locale.ROOT, "%.1f", s.store().bytes() / 1048576.0)));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return showStatus(c.getSource(), target(c.getSource(), ai.ezfox.cccloud.access.Perm.VIEW));
    }

    private static int showStatus(CommandSourceStack source, CloudMainframeBlockEntity mainframe) throws CommandSyntaxException {
        mainframe.showStatus(source.getPlayerOrException());
        if (!mainframe.has(source.getPlayerOrException(), ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE)) return 1;
        var auth = WebAuthData.get(source.getServer());
        var grants = mainframe.getGrants().values().stream().map(g -> g.name() + " (" + g.role().id + ")").toList();
        source.sendSystemMessage(Component.translatable("cccloud.command.owner",
            mainframe.getOwnerName().isEmpty() ? "-" : mainframe.getOwnerName(),
            grants.isEmpty() ? "-" : String.join(", ", grants)).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(Component.translatable(auth.hasPin(mainframe.getMainframeId()) ? "cccloud.pin.is_set" : "cccloud.pin.not_set")
            .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int devices(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var mainframe = target(c.getSource(), ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE);
        var list = WebAuthData.get(c.getSource().getServer()).devices(mainframe.getMainframeId());
        var source = c.getSource();
        if (list.isEmpty()) {
            source.sendSystemMessage(Component.translatable("cccloud.command.no_devices"));
            return 0;
        }
        var format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        source.sendSystemMessage(Component.translatable("cccloud.command.devices_header", list.size()).withStyle(ChatFormatting.AQUA));
        for (var device : list) {
            source.sendSystemMessage(Component.translatable("cccloud.command.device_line", device.name(), device.playerName(),
                format.format(new Date(device.lastUsed())),
                Component.translatable(device.canControl() ? "cccloud.command.control" : "cccloud.command.read_only")));
        }
        return list.size();
    }

    private static int revoke(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        var mainframe = target(c.getSource(), ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE);
        var removed = mainframe.revoke(c.getSource().getLevel(), name, c.getSource().getTextName());
        c.getSource().sendSystemMessage(Component.translatable("cccloud.command.revoked", removed.size()));
        return removed.size();
    }

    private static int trust(CommandContext<CommandSourceStack> c, boolean trust) throws CommandSyntaxException {
        var mainframe = target(c.getSource(), ai.ezfox.cccloud.access.Perm.ACCESS_MANAGE);
        var profiles = GameProfileArgument.getGameProfiles(c, "player");
        var caller = c.getSource().getPlayerOrException();
        var rules = ai.ezfox.cccloud.access.Access.caller(caller, mainframe.getMainframeId());
        var newRole = trust ? ai.ezfox.cccloud.access.AccessRules.trustRole(rules, ai.ezfox.cccloud.access.Role.ADMIN) : ai.ezfox.cccloud.access.Role.NONE;
        int changed = 0;
        for (var profile : profiles) {
            var current = mainframe.getGrants().get(profile.id());
            // the same rank rule as the web and the screen: never above the caller's role, only players below them
            if (ai.ezfox.cccloud.access.AccessRules.changeGrant(rules, current == null ? ai.ezfox.cccloud.access.Role.NONE : current.role(),
                profile.id().equals(mainframe.getOwner()), profile.id().equals(caller.getUUID()), newRole, java.util.Set.of()) != null) throw NOT_OWNER.create();
            boolean ok = trust ? mainframe.trust(profile, newRole) : mainframe.untrust(profile.id());
            if (ok) {
                changed++;
                CCCloud.LOGGER.info("[cccloud] {} {} {} on {}", c.getSource().getTextName(), trust ? "trusted" : "untrusted", profile.name(), mainframe.getDisplayName());
            }
            c.getSource().sendSystemMessage(Component.translatable(
                trust ? (ok ? "cccloud.command.trusted" : "cccloud.command.already_trusted") : (ok ? "cccloud.command.untrusted" : "cccloud.command.not_trusted"),
                profile.name()));
        }
        // Devices of an untrusted player stop working immediately.
        if (!trust && changed > 0) ai.ezfox.cccloud.web.CloudWebServer.serverTick(c.getSource().getServer());
        return changed;
    }

    private static int setPin(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var mainframe = target(c.getSource(), ai.ezfox.cccloud.access.Perm.PIN_MANAGE);
        String pin = StringArgumentType.getString(c, "pin");
        if (!AuthStore.isValidPin(pin)) throw BAD_PIN.create();
        var server = c.getSource().getServer();
        var auth = WebAuthData.get(server);
        var id = mainframe.getMainframeId();
        var source = c.getSource();
        String who = source.getTextName();
        // Hashing is slow on purpose (PBKDF2), so it runs off the server thread.
        CompletableFuture.supplyAsync(() -> auth.hashPin(pin)).thenAccept(hash -> server.execute(() -> {
            auth.setPin(id, hash);
            CCCloud.LOGGER.info("[cccloud] {} set the dashboard PIN of {}", who, mainframe.getDisplayName());
            source.sendSystemMessage(Component.translatable("cccloud.pin.set").withStyle(ChatFormatting.GREEN));
        }));
        return 1;
    }

    private static int clearPin(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var mainframe = target(c.getSource(), ai.ezfox.cccloud.access.Perm.PIN_MANAGE);
        WebAuthData.get(c.getSource().getServer()).setPin(mainframe.getMainframeId(), null);
        CCCloud.LOGGER.info("[cccloud] {} cleared the dashboard PIN of {}", c.getSource().getTextName(), mainframe.getDisplayName());
        c.getSource().sendSystemMessage(Component.translatable("cccloud.pin.cleared"));
        return 1;
    }

    private static int decide(CommandContext<CommandSourceStack> c, boolean allow) throws CommandSyntaxException {
        return decide(c, StringArgumentType.getString(c, "request"), allow);
    }

    private static int decide(CommandContext<CommandSourceStack> c, String id, boolean allow) throws CommandSyntaxException {
        if (!(c.getSource().getEntity() instanceof ServerPlayer)) return decideFromConsole(c.getSource(), id, allow);
        ServerPlayer player = c.getSource().getPlayerOrException();
        var auth = WebAuthData.get(c.getSource().getServer());
        var pending = auth.pending(id);
        if (pending == null || pending.state() != AuthStore.PendingState.PENDING) throw UNKNOWN_REQUEST.create();
        // Account codes: only the player whose code it is. Mainframe codes: admins of that mainframe.
        if (pending.isAccount() ? !player.getUUID().equals(pending.account())
            : !ai.ezfox.cccloud.access.Access.has(player, pending.mainframeId(), ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE)) throw NOT_OWNER.create();
        if (allow) {
            var approved = auth.approve(id, player.getUUID(), player.getGameProfile().name(), true);
            if (approved == null) throw UNKNOWN_REQUEST.create();
            CCCloud.LOGGER.info("[cccloud] {} approved device '{}' from {}", player.getGameProfile().name(), approved.deviceName(), pending.ip());
            c.getSource().sendSystemMessage(Component.translatable("cccloud.approval.approved", approved.deviceName()).withStyle(ChatFormatting.GREEN));
        } else {
            auth.deny(id);
            CCCloud.LOGGER.info("[cccloud] {} denied device '{}' from {}", player.getGameProfile().name(), pending.deviceName(), pending.ip());
            c.getSource().sendSystemMessage(Component.translatable("cccloud.approval.denied", pending.deviceName()).withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    /**
     * Server console / RCON (a trusted operator): approve or deny any request. The browser acts with the role of the
     * mainframe's owner (mainframe codes) or the code's player (account codes). Written to the audit log.
     */
    private static int decideFromConsole(CommandSourceStack source, String id, boolean allow) throws CommandSyntaxException {
        if (!source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_OWNER)) throw NOT_OWNER.create();
        var server = source.getServer();
        var auth = WebAuthData.get(server);
        var pending = auth.pending(id);
        if (pending == null || pending.state() != AuthStore.PendingState.PENDING) throw UNKNOWN_REQUEST.create();
        java.util.UUID player = pending.isAccount() ? pending.account() : null;
        if (player == null) {
            var access = WebAuthData.access(server).access(pending.mainframeId());
            player = access == null ? null : access.owner();
        }
        if (player == null) throw NOT_OWNER.create();
        String name = ai.ezfox.cccloud.access.CloudPermissions.nameOf(server, player);
        if (allow) {
            var approved = auth.approve(id, player, name, true);
            if (approved == null) throw UNKNOWN_REQUEST.create();
            source.sendSystemMessage(Component.translatable("cccloud.approval.approved", approved.deviceName()));
        } else {
            auth.deny(id);
            source.sendSystemMessage(Component.translatable("cccloud.approval.denied", pending.deviceName()));
        }
        CCCloud.LOGGER.info("[cccloud] Console ({}) {} device '{}' from {}", source.getTextName(), allow ? "approved" : "denied", pending.deviceName(), pending.ip());
        if (pending.mainframeId() != null) {
            WebAuthData.access(server).audit(pending.mainframeId(), "Console " + (allow ? "approved" : "denied") + " browser '" + pending.deviceName() + "'");
        }
        return 1;
    }

    /** Dev/E2E only ({@code -Dcccloud.devCommands=true}): a device token for a mainframe, printed to the console. */
    private static int testToken(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var source = c.getSource();
        if (!Boolean.getBoolean("cccloud.devCommands") || source.getEntity() != null) throw NOT_OWNER.create();
        java.util.UUID id;
        try {
            id = java.util.UUID.fromString(StringArgumentType.getString(c, "mainframe"));
        } catch (IllegalArgumentException e) {
            throw NO_MAINFRAME.create();
        }
        var access = WebAuthData.access(source.getServer()).access(id);
        if (access == null || access.owner() == null) throw NO_MAINFRAME.create();
        var auth = WebAuthData.get(source.getServer());
        var pending = auth.pair(auth.currentCode(id, access.owner()).code(), "e2e", "127.0.0.1");
        if (pending == null) throw UNKNOWN_REQUEST.create();
        var approved = auth.approve(pending.id(), access.owner(), access.ownerName(), true);
        if (approved == null) throw UNKNOWN_REQUEST.create();
        WebAuthData.access(source.getServer()).audit(id, "Console issued a test token (dev)");
        source.sendSystemMessage(Component.literal("token " + approved.token()));
        return 1;
    }
}
