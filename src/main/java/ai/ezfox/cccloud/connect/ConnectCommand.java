package ai.ezfox.cccloud.connect;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.web.WebAuthData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /cccloud internet connect [<name>|release]}: CC: Cloud Connect, the dashboard at {@code https://<name>.cccloud.dev}
 * without port forwarding. Claiming or releasing a name changes the server's internet exposure, so it needs the server
 * owner (op level 4 or the console), like the other {@code /cccloud internet} modes; the status is shown to admins
 * (level 2). The node is registered on its own (Brigadier merges it below {@code /cccloud internet}).
 */
public final class ConnectCommand {
    private ConnectCommand() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> register(e.getDispatcher()));
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cccloud")
            .then(Commands.literal("internet").requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(node())));
    }

    /** The {@code connect} node (for {@code /cccloud internet}). */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("connect")
            .executes(ConnectCommand::connectConfigured)
            .then(Commands.literal("release").requires(ConnectCommand::owner).executes(ConnectCommand::release))
            .then(Commands.argument("name", StringArgumentType.word()).requires(ConnectCommand::owner)
                .suggests((c, b) -> {
                    String configured = ConnectSettings.fromConfig().name();
                    if (!configured.isEmpty()) b.suggest(configured);
                    b.suggest("release");
                    return b.buildFuture();
                })
                .executes(c -> claim(c, StringArgumentType.getString(c, "name"))));
    }

    private static boolean owner(CommandSourceStack src) {
        return src.permissions().hasPermission(Permissions.COMMANDS_OWNER);
    }

    // ---- commands -----------------------------------------------------------------------------------------------------

    /** Without a name: the status, and for the owner (re)connect with the configured name or a suggestion. */
    private static int connectConfigured(CommandContext<CommandSourceStack> c) {
        var source = c.getSource();
        var settings = ConnectSettings.fromConfig();
        if (!settings.name().isEmpty() && owner(source) && !settings.enabled()) return claim(c, settings.name());
        for (var line : statusLines(source.getServer())) source.sendSystemMessage(line);
        if (settings.name().isEmpty() && owner(source)) {
            String suggestion = ConnectNames.suggest(source.getServer().getWorldData().getLevelName());
            source.sendSystemMessage(Component.translatable("cccloud.connect.pick", command("/cccloud internet connect " + suggestion)).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int claim(CommandContext<CommandSourceStack> c, String raw) {
        var source = c.getSource();
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (!ConnectNames.valid(name)) {
            source.sendFailure(Component.translatable("cccloud.connect.invalid_name", name));
            return 0;
        }
        if (ConnectNames.reserved(name)) {
            source.sendFailure(Component.translatable("cccloud.connect.reserved_name", name));
            return 0;
        }
        if (!ConnectService.claim(name)) {
            source.sendFailure(Component.translatable("cccloud.connect.not_saved"));
            return 0;
        }
        CCCloud.LOGGER.info("[cccloud] {} set internetAccess = connect with the name {}", source.getTextName(), name);
        var settings = ConnectSettings.fromConfig();
        source.sendSystemMessage(Component.translatable("cccloud.connect.connecting", link(ConnectClient.expectedUrl(settings, name))).withStyle(ChatFormatting.GRAY));
        var server = source.getServer();
        ConnectService.settled(20).thenAccept(status -> server.execute(() -> report(source, status)));
        return 1;
    }

    private static int release(CommandContext<CommandSourceStack> c) {
        var source = c.getSource();
        var server = source.getServer();
        String name = ConnectSettings.fromConfig().name();
        if (name.isEmpty()) {
            source.sendFailure(Component.translatable("cccloud.connect.no_name"));
            return 0;
        }
        source.sendSystemMessage(Component.translatable("cccloud.connect.releasing", name).withStyle(ChatFormatting.GRAY));
        ConnectService.release().thenAccept(result -> server.execute(() -> {
            if (result.equals("released")) {
                CCCloud.LOGGER.info("[cccloud] {} released the Connect name {}", source.getTextName(), name);
                source.sendSystemMessage(Component.translatable("cccloud.connect.released", name).withStyle(ChatFormatting.GREEN));
            } else {
                source.sendFailure(Component.translatable("cccloud.connect.release_failed", name, explain(result)));
            }
        }));
        return 1;
    }

    /** After claiming: online (with the link and the PIN note) or why not. */
    private static void report(CommandSourceStack source, ConnectStatus status) {
        switch (status.state()) {
            case CONNECTED -> {
                source.sendSystemMessage(Component.translatable("cccloud.connect.online", link(status.url())).withStyle(ChatFormatting.GREEN));
                if (!status.bundleKnown()) source.sendSystemMessage(Component.translatable("cccloud.connect.bundle_unknown").withStyle(ChatFormatting.GOLD));
                source.sendSystemMessage(Component.translatable("cccloud.connect.pin_rule").withStyle(ChatFormatting.GRAY));
                var missing = withoutPin(source.getServer());
                if (missing > 0) {
                    source.sendSystemMessage(Component.translatable("cccloud.connect.without_pin", missing, command("/cccloud pin set ")).withStyle(ChatFormatting.GOLD));
                }
                source.sendSystemMessage(Component.translatable("cccloud.connect.switch_off", command("/cccloud internet off"),
                    command("/cccloud internet connect release")).withStyle(ChatFormatting.GRAY));
            }
            case FAILED -> source.sendFailure(Component.translatable("cccloud.connect.refused", status.name(), explain(status.error())));
            default -> source.sendSystemMessage(Component.translatable("cccloud.connect.still_trying", explain(status.error())).withStyle(ChatFormatting.GOLD));
        }
    }

    // ---- status -------------------------------------------------------------------------------------------------------

    /** The Connect lines of {@code /cccloud internet status}: address and state. */
    public static List<MutableComponent> statusLines(MinecraftServer server) {
        var lines = new ArrayList<MutableComponent>();
        var s = ConnectService.status();
        switch (s.state()) {
            case OFF -> lines.add(Component.translatable("cccloud.connect.status.off", command("/cccloud internet connect")).withStyle(ChatFormatting.GRAY));
            case NO_NAME -> lines.add(Component.translatable("cccloud.connect.status.no_name").withStyle(ChatFormatting.GOLD));
            case CONNECTING -> lines.add(Component.translatable("cccloud.connect.status.connecting", link(s.url())).withStyle(ChatFormatting.GRAY));
            case CONNECTED -> {
                lines.add(Component.translatable("cccloud.connect.status.online", link(s.url()), s.browsers()).withStyle(ChatFormatting.GREEN));
                if (!s.bundleKnown()) lines.add(Component.translatable("cccloud.connect.bundle_unknown").withStyle(ChatFormatting.GOLD));
            }
            case RETRYING -> lines.add(Component.translatable("cccloud.connect.status.retrying", link(s.url()), explain(s.error()),
                Math.max(1, (s.retryAt() - System.currentTimeMillis() + 999) / 1000)).withStyle(ChatFormatting.GOLD));
            case FAILED -> lines.add(Component.translatable("cccloud.connect.status.failed", link(s.url()), explain(s.error())).withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    /** A readable reason for a relay or network error code. */
    static Component explain(String code) {
        if (code == null) return Component.translatable("cccloud.connect.error.unknown");
        return switch (code) {
            case "name_taken", "reserved", "invalid_name", "revoked", "key_limit", "ip_limit", "bad_signature", "key_error", "web_off", "rate_limited",
                 "unreachable", "tls", "timeout", "network", "replaced", "not_connected", "no_name", "disconnected", "protocol", "relay_error",
                 "too_many_attempts" ->
                Component.translatable("cccloud.connect.error." + code);
            default -> Component.translatable("cccloud.connect.error.other", code);
        };
    }

    private static int withoutPin(MinecraftServer server) {
        var auth = WebAuthData.get(server);
        var removed = ai.ezfox.cccloud.block.MainframeRetention.get(server).entries();
        int n = 0;
        for (var id : WebAuthData.access(server).mainframes()) if (!removed.containsKey(id) && !auth.hasPin(id)) n++;
        return n;
    }

    private static MutableComponent link(String url) {
        return Component.literal(url).withStyle(style -> {
            var s = style.withColor(ChatFormatting.AQUA).withUnderlined(true);
            try {
                s = s.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)));
            } catch (IllegalArgumentException ignored) {
                // not a URL
            }
            return s;
        });
    }

    private static MutableComponent command(String command) {
        return Component.literal(command.trim()).withStyle(style -> style.withColor(ChatFormatting.GOLD)
            .withClickEvent(new ClickEvent.SuggestCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cccloud.help.click"))));
    }
}
