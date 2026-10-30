package ai.ezfox.cccloud.command;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.web.CloudWebServer;
import ai.ezfox.cccloud.web.LanAddresses;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.internet.InternetAdvice;
import ai.ezfox.cccloud.web.internet.InternetMode;
import ai.ezfox.cccloud.web.internet.InternetSettings;
import ai.ezfox.cccloud.web.internet.PublicIp;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * {@code /cccloud internet [status|off|http [domain]]}: internet access to the dashboard in one setting
 * ({@code internetAccess}, docs/HOSTING.md). Switching needs the server owner (op level 4, or the console); the status
 * is shown to admins (level 2). The command writes the config, applies it at once (live config) and prints the link,
 * the port to forward and how to test it. Plain HTTP is refused while a mainframe has no PIN.
 */
public final class InternetCommand {
    private static final DynamicCommandExceptionType BAD_DOMAIN = new DynamicCommandExceptionType(
        d -> Component.translatable("cccloud.internet.bad_domain", String.valueOf(d)));

    private InternetCommand() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, InternetCommand::onLogin);
    }

    /** The {@code internet} node below {@code /cccloud}. */
    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("internet")
            .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .executes(InternetCommand::status)
            .then(Commands.literal("status").executes(InternetCommand::status))
            .then(Commands.literal("off").requires(InternetCommand::owner).executes(c -> switchMode(c, InternetMode.OFF, null)))
            .then(Commands.literal("http").requires(InternetCommand::owner).executes(c -> switchMode(c, InternetMode.HTTP, null))
                .then(Commands.argument("domain", StringArgumentType.greedyString())
                    .executes(c -> switchMode(c, InternetMode.HTTP, StringArgumentType.getString(c, "domain")))));
    }

    /** Changing the server's internet exposure is for the server owner: op level 4 or the console. */
    private static boolean owner(CommandSourceStack src) {
        return src.permissions().hasPermission(Permissions.COMMANDS_OWNER);
    }

    // ---- facts --------------------------------------------------------------------------------------------------------

    /**
     * Standing mainframes (removed ones in their retention time are left out) without a dashboard PIN, by name and
     * position when loaded, else by owner and id.
     */
    public static List<String> mainframesWithoutPin(MinecraftServer server) {
        var auth = WebAuthData.get(server);
        var list = new ArrayList<String>();
        for (var id : standingMainframes(server)) {
            if (auth.hasPin(id)) continue;
            list.add(describe(server, id));
        }
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    static List<java.util.UUID> standingMainframes(MinecraftServer server) {
        var access = WebAuthData.access(server);
        var removed = ai.ezfox.cccloud.block.MainframeRetention.get(server).entries();
        var list = new ArrayList<java.util.UUID>();
        for (var id : access.mainframes()) if (!removed.containsKey(id)) list.add(id);
        return list;
    }

    private static String describe(MinecraftServer server, java.util.UUID id) {
        var mainframe = ai.ezfox.cccloud.network.CloudNetwork.mainframe(id);
        if (mainframe != null && mainframe.getLevel() != null) {
            var pos = mainframe.getBlockPos();
            return mainframe.getDisplayName() + " (" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ")";
        }
        var entry = WebAuthData.access(server).access(id);
        String owner = entry == null || entry.ownerName().isBlank() ? "?" : entry.ownerName();
        return owner + "/" + id.toString().substring(0, 8);
    }

    private static InternetAdvice.Facts facts(MinecraftServer server, InternetMode configured, String domain, @org.jspecify.annotations.Nullable String publicIp) {
        var lan = LanAddresses.select(LanAddresses.scan());
        int port = CloudWebServer.isRunning() ? CloudWebServer.port() : CloudConfig.get(CloudConfig.WEB_PORT);
        return new InternetAdvice.Facts(configured, CloudWebServer.internetMode(), domain, port, lan.isEmpty() ? null : lan.getFirst(), publicIp,
            mainframesWithoutPin(server), CloudConfig.get(CloudConfig.PUBLIC_TOKEN_IDLE_HOURS),
            configured == InternetMode.OFF && CloudConfig.get(CloudConfig.WEB_ALLOW_INTERNET));
    }

    private static InternetMode configured() {
        var mode = InternetMode.parse(CloudConfig.get(CloudConfig.INTERNET_ACCESS));
        return mode == null ? InternetMode.OFF : mode;
    }

    // ---- commands -----------------------------------------------------------------------------------------------------

    private static int status(CommandContext<CommandSourceStack> c) {
        var source = c.getSource();
        String domain = CloudConfig.get(CloudConfig.INTERNET_DOMAIN).trim();
        String ip = domain.isEmpty() ? PublicIp.last() : null;
        send(source, InternetAdvice.status(facts(source.getServer(), configured(), domain, ip), CloudWebServer.publicUrl()));
        return 1;
    }

    /**
     * Writes {@code internetAccess} (and {@code internetDomain} when given; {@code auto} clears it) and prints the next
     * steps. Plain HTTP needs a PIN on every mainframe, so the PIN can guard every control action from the internet.
     */
    static int switchMode(CommandContext<CommandSourceStack> c, InternetMode mode, @org.jspecify.annotations.Nullable String domainArg) throws CommandSyntaxException {
        var source = c.getSource();
        var server = source.getServer();
        String domain = CloudConfig.get(CloudConfig.INTERNET_DOMAIN).trim();
        if (domainArg != null) {
            String d = domainArg.trim();
            if (d.equalsIgnoreCase("auto")) domain = "";
            else if (!InternetSettings.validDomain(d)) throw BAD_DOMAIN.create(d);
            else domain = d.toLowerCase(Locale.ROOT);
        }
        if (mode == InternetMode.HTTP) {
            var missing = mainframesWithoutPin(server);
            int standing = standingMainframes(server).size();
            if (standing == 0 || !missing.isEmpty()) {
                send(source, InternetAdvice.refusedNoPin(missing, standing));
                CCCloud.LOGGER.info("[cccloud] {} tried to switch internetAccess to http: refused, {} mainframe(s) without a PIN",
                    source.getTextName(), standing == 0 ? "no mainframe yet," : String.valueOf(missing.size()));
                return 0;
            }
        }
        if (!write(mode, domain)) {
            source.sendFailure(Component.translatable("cccloud.internet.not_saved"));
            return 0;
        }
        CCCloud.LOGGER.info("[cccloud] {} set internetAccess = {}{}", source.getTextName(), mode.id(), domain.isEmpty() ? "" : " (internetDomain " + domain + ")");
        // Live config: the web server switches now (all or nothing; the report says why if it could not).
        var report = ai.ezfox.cccloud.config.ConfigReload.apply(server, "internet");
        for (String error : report.errors()) source.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED));
        if (mode == InternetMode.OFF) {
            send(source, InternetAdvice.switched(facts(server, mode, domain, null)));
            return 1;
        }
        String finalDomain = domain;
        if (!domain.isEmpty() || !PublicIp.enabled()) {
            send(source, InternetAdvice.switched(facts(server, mode, domain, PublicIp.last())));
            return 1;
        }
        source.sendSystemMessage(Component.translatable("cccloud.internet.looking_up").withStyle(ChatFormatting.GRAY));
        PublicIp.lookup().whenComplete((ip, err) -> server.execute(() -> send(source, InternetAdvice.switched(facts(server, mode, finalDomain, ip)))));
        return 1;
    }

    /** Writes the two keys and saves the config file (the reload event that follows finds nothing new to apply). */
    private static boolean write(InternetMode mode, String domain) {
        try {
            CloudConfig.INTERNET_ACCESS.set(mode.id());
            CloudConfig.INTERNET_DOMAIN.set(domain);
            CloudConfig.SPEC.save();
            return true;
        } catch (RuntimeException e) {
            CCCloud.LOGGER.error("[cccloud] Could not save internetAccess to the server config", e);
            return false;
        }
    }

    // ---- warnings on join ---------------------------------------------------------------------------------------------

    /** Plain-HTTP internet access: every admin (level 2) gets the warning when joining. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (CloudWebServer.internetMode() != InternetMode.HTTP || !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return;
        var server = player.level().getServer();
        if (server == null) return;
        var lines = new ArrayList<InternetAdvice.Line>();
        lines.add(new InternetAdvice.Line(InternetAdvice.Style.ERROR, "cccloud.internet.join.title"));
        lines.add(new InternetAdvice.Line(InternetAdvice.Style.ERROR, "cccloud.internet.http.warning", String.valueOf(CloudConfig.get(CloudConfig.PUBLIC_TOKEN_IDLE_HOURS))));
        var missing = mainframesWithoutPin(server);
        if (!missing.isEmpty()) lines.add(new InternetAdvice.Line(InternetAdvice.Style.WARN, "cccloud.internet.http.without_pin", String.join(", ", missing.subList(0, Math.min(5, missing.size())))));
        lines.add(new InternetAdvice.Line(InternetAdvice.Style.COMMAND, "cccloud.internet.join.switch", "/cccloud internet off"));
        for (var line : lines) player.sendSystemMessage(component(line));
    }

    // ---- chat ---------------------------------------------------------------------------------------------------------

    private static void send(CommandSourceStack source, List<InternetAdvice.Line> lines) {
        for (var line : lines) source.sendSystemMessage(component(line));
    }

    static MutableComponent component(InternetAdvice.Line line) {
        Object[] args = line.args().toArray();
        if (line.style() == InternetAdvice.Style.LINK && args.length > 0) {
            String url = String.valueOf(args[0]);
            var link = Component.literal(url).withStyle(style -> {
                var s = style.withColor(ChatFormatting.AQUA).withUnderlined(true);
                try {
                    if (!url.contains("<")) s = s.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)));
                } catch (IllegalArgumentException ignored) {
                    // not a URL (placeholder): plain text
                }
                return s;
            });
            args[0] = link;
        }
        if (line.style() == InternetAdvice.Style.COMMAND) {
            for (int i = 0; i < args.length; i++) {
                String command = String.valueOf(args[i]);
                args[i] = Component.literal(command.trim()).withStyle(style -> style.withColor(ChatFormatting.GOLD)
                    .withClickEvent(new ClickEvent.SuggestCommand(command))
                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cccloud.help.click"))));
            }
        }
        var text = Component.translatable(line.key(), args);
        return switch (line.style()) {
            case TITLE -> text.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            case OK -> text.withStyle(ChatFormatting.GREEN);
            case WARN -> text.withStyle(ChatFormatting.GOLD);
            case ERROR -> text.withStyle(ChatFormatting.RED);
            case INFO, COMMAND -> text.withStyle(ChatFormatting.GRAY);
            case STEP, LINK -> text.withStyle(ChatFormatting.WHITE);
        };
    }
}
