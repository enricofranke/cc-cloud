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
 * {@code /cccloud status|devices|revoke|trust|untrust|pin|approve|deny}. All mainframe commands act on the mainframe
 * the player is looking at and need owner, trusted or operator rights.
 */
public final class CloudCommand {
    private static final SimpleCommandExceptionType NOT_LOOKING = new SimpleCommandExceptionType(Component.translatable("cccloud.command.not_looking"));
    private static final SimpleCommandExceptionType NOT_OWNER = new SimpleCommandExceptionType(Component.translatable("cccloud.mainframe.not_owner"));
    private static final SimpleCommandExceptionType UNKNOWN_REQUEST = new SimpleCommandExceptionType(Component.translatable("cccloud.approval.unknown"));
    private static final SimpleCommandExceptionType BAD_PIN = new SimpleCommandExceptionType(Component.translatable("cccloud.pin.invalid"));

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
            .then(Commands.literal("status").executes(CloudCommand::status))
            .then(Commands.literal("devices").executes(CloudCommand::devices))
            .then(Commands.literal("revoke")
                .then(Commands.argument("device", StringArgumentType.greedyString())
                    .suggests((c, b) -> {
                        try {
                            var mainframe = target(c.getSource());
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
            .then(Commands.literal("pin")
                .then(Commands.literal("set").then(Commands.argument("pin", StringArgumentType.word()).executes(CloudCommand::setPin)))
                .then(Commands.literal("clear").executes(CloudCommand::clearPin)))
            .then(Commands.literal("approve")
                .then(Commands.argument("request", StringArgumentType.word()).executes(c -> decide(c, true))))
            .then(Commands.literal("deny")
                .then(Commands.argument("request", StringArgumentType.word()).executes(c -> decide(c, false)))));
    }

    /** The mainframe the player is looking at (within 8 blocks), if the player may manage it. */
    private static CloudMainframeBlockEntity target(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var eye = player.getEyePosition();
        var end = eye.add(player.getViewVector(1).scale(8));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) throw NOT_LOOKING.create();
        var state = player.level().getBlockState(hit.getBlockPos());
        var mainframe = CloudMainframeBlock.getBlockEntity(player.level(), hit.getBlockPos(), state);
        if (mainframe == null) throw NOT_LOOKING.create();
        if (!mainframe.canManage(player)) throw NOT_OWNER.create();
        return mainframe;
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var mainframe = target(c.getSource());
        mainframe.showStatus(c.getSource().getPlayerOrException());
        var auth = WebAuthData.get(c.getSource().getServer());
        c.getSource().sendSystemMessage(Component.translatable("cccloud.command.owner",
            mainframe.getOwnerName().isEmpty() ? "-" : mainframe.getOwnerName(),
            mainframe.getTrusted().isEmpty() ? "-" : String.join(", ", mainframe.getTrusted().values())).withStyle(ChatFormatting.GRAY));
        c.getSource().sendSystemMessage(Component.translatable(auth.hasPin(mainframe.getMainframeId()) ? "cccloud.pin.is_set" : "cccloud.pin.not_set")
            .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int devices(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var mainframe = target(c.getSource());
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
        var mainframe = target(c.getSource());
        var removed = mainframe.revoke(c.getSource().getLevel(), name, c.getSource().getTextName());
        c.getSource().sendSystemMessage(Component.translatable("cccloud.command.revoked", removed.size()));
        return removed.size();
    }

    private static int trust(CommandContext<CommandSourceStack> c, boolean trust) throws CommandSyntaxException {
        var mainframe = target(c.getSource());
        var profiles = GameProfileArgument.getGameProfiles(c, "player");
        int changed = 0;
        for (var profile : profiles) {
            boolean ok = trust ? mainframe.trust(profile) : mainframe.untrust(profile.id());
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
        var mainframe = target(c.getSource());
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
        var mainframe = target(c.getSource());
        WebAuthData.get(c.getSource().getServer()).setPin(mainframe.getMainframeId(), null);
        CCCloud.LOGGER.info("[cccloud] {} cleared the dashboard PIN of {}", c.getSource().getTextName(), mainframe.getDisplayName());
        c.getSource().sendSystemMessage(Component.translatable("cccloud.pin.cleared"));
        return 1;
    }

    private static int decide(CommandContext<CommandSourceStack> c, boolean allow) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        var auth = WebAuthData.get(c.getSource().getServer());
        String id = StringArgumentType.getString(c, "request");
        var pending = auth.pending(id);
        if (pending == null || pending.state() != AuthStore.PendingState.PENDING) throw UNKNOWN_REQUEST.create();
        var acl = auth.acl(pending.mainframeId());
        boolean op = player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        if (!op && (acl == null || !acl.allows(player.getUUID()))) throw NOT_OWNER.create();
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
}
