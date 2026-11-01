package ai.ezfox.cccloud.command;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.reports.ReportConsent;
import ai.ezfox.cccloud.reports.ReportConsent.Channel;
import ai.ezfox.cccloud.reports.ReportConsent.State;
import ai.ezfox.cccloud.reports.ReportPayload;
import ai.ezfox.cccloud.reports.ReportService;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.permissions.Permissions;

/**
 * {@code /cccloud reports [status|on|off|show]} for the server owner (op level 4, the console, or the singleplayer host)
 * and {@code /cccloud feedback <text>} for everybody, confirmed message by message (docs/PRIVACY.md).
 */
public final class ReportCommand {
    private static final long FEEDBACK_VALID_MS = 5 * 60_000L;
    private static final int MAX_PENDING = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Pending(String sender, JsonObject payload, long expires) {
    }

    /** Feedback that was shown and waits for its [Send]. Literals would shadow the text, so sending has its own command. */
    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();

    private ReportCommand() {
    }

    /** The {@code reports} node below {@code /cccloud}. */
    static LiteralArgumentBuilder<CommandSourceStack> reports() {
        return Commands.literal("reports")
            .requires(ReportCommand::owner)
            .executes(ReportCommand::status)
            .then(Commands.literal("status").executes(ReportCommand::status))
            .then(Commands.literal("show").executes(ReportCommand::show))
            .then(Commands.literal("on").executes(c -> decide(c, null, true))
                .then(Commands.literal("crash").executes(c -> decide(c, Channel.CRASH, true)))
                .then(Commands.literal("stats").executes(c -> decide(c, Channel.STATS, true))))
            .then(Commands.literal("off").executes(c -> decide(c, null, false))
                .then(Commands.literal("crash").executes(c -> decide(c, Channel.CRASH, false)))
                .then(Commands.literal("stats").executes(c -> decide(c, Channel.STATS, false))));
    }

    /** {@code feedback <text>}. */
    static LiteralArgumentBuilder<CommandSourceStack> feedback() {
        return Commands.literal("feedback")
            .then(Commands.argument("text", StringArgumentType.greedyString()).executes(ReportCommand::feedback));
    }

    /** {@code feedback-send <id>}: the [Send] of a message that was shown. */
    static LiteralArgumentBuilder<CommandSourceStack> feedbackSend() {
        return Commands.literal("feedback-send")
            .then(Commands.argument("id", StringArgumentType.word()).executes(ReportCommand::feedbackSend));
    }

    /** {@code feedback-discard <id>}: its [Discard]. */
    static LiteralArgumentBuilder<CommandSourceStack> feedbackDiscard() {
        return Commands.literal("feedback-discard")
            .then(Commands.argument("id", StringArgumentType.word()).executes(ReportCommand::feedbackDiscard));
    }

    private static boolean owner(CommandSourceStack src) {
        if (src.permissions().hasPermission(Permissions.COMMANDS_OWNER)) return true;
        var player = src.getPlayer();
        return player != null && src.getServer().isSingleplayerOwner(player.nameAndId());
    }

    private static String sender(CommandSourceStack src) {
        var player = src.getPlayer();
        return player != null ? player.getUUID().toString() : "console";
    }

    private static String language(CommandSourceStack src) {
        var player = src.getPlayer();
        return player != null ? player.clientInformation().language() : "en_us";
    }

    private static MutableComponent stateText(State state) {
        return Component.translatable("cccloud.reports.state." + state.id())
            .withStyle(state == State.ACTIVE ? ChatFormatting.GREEN : state == State.DECLINED ? ChatFormatting.RED : ChatFormatting.YELLOW);
    }

    private static MutableComponent command(String command) {
        return Component.literal(command).withStyle(style -> style.withColor(ChatFormatting.GOLD)
            .withClickEvent(new ClickEvent.SuggestCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cccloud.help.click"))));
    }

    private static MutableComponent action(String key, String command, ChatFormatting color) {
        return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(style -> style.withColor(color)
            .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static MutableComponent privacyLink(CommandSourceStack src) {
        String url = ReportConsent.privacyUrl(language(src));
        var link = Component.literal(url).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
            .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url))));
        return Component.translatable("cccloud.reports.privacy", link).withStyle(ChatFormatting.GRAY);
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        var src = c.getSource();
        src.sendSystemMessage(Component.translatable("cccloud.reports.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        for (var channel : Channel.values()) {
            src.sendSystemMessage(Component.translatable("cccloud.reports." + channel.id, stateText(ReportService.state(channel))).withStyle(ChatFormatting.GRAY));
        }
        src.sendSystemMessage(Component.translatable("cccloud.reports.intro").withStyle(ChatFormatting.GRAY));
        src.sendSystemMessage(Component.translatable("cccloud.reports.operator").withStyle(ChatFormatting.GRAY));
        src.sendSystemMessage(privacyLink(src));
        src.sendSystemMessage(Component.translatable("cccloud.reports.commands", command("/cccloud reports show"), command("/cccloud reports on"), command("/cccloud reports off"))
            .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> c) {
        var src = c.getSource();
        var preview = ReportService.preview(src.getServer());
        src.sendSystemMessage(Component.translatable("cccloud.reports.show.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        for (var channel : Channel.values()) {
            var entry = preview.getAsJsonObject(channel.id);
            boolean example = entry.has("example") && entry.get("example").getAsBoolean();
            src.sendSystemMessage(Component.translatable(example ? "cccloud.reports.show.example" : "cccloud.reports.show." + channel.id).withStyle(ChatFormatting.YELLOW));
            src.sendSystemMessage(entry.has("payload") ? Component.literal(entry.get("payload").toString()).withStyle(ChatFormatting.GRAY)
                : Component.translatable("cccloud.reports.show.dropped").withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int decide(CommandContext<CommandSourceStack> c, ai.ezfox.cccloud.reports.ReportConsent.@org.jspecify.annotations.Nullable Channel only, boolean yes) {
        var src = c.getSource();
        for (var channel : Channel.values()) {
            if (only != null && channel != only) continue;
            if (!ReportService.decide(channel, yes, src.getTextName())) {
                src.sendFailure(Component.translatable("cccloud.reports.failed"));
                return 0;
            }
            src.sendSystemMessage(Component.translatable("cccloud.reports." + channel.id, stateText(ReportService.state(channel))).withStyle(ChatFormatting.GRAY));
        }
        if (yes) src.sendSystemMessage(privacyLink(src));
        return 1;
    }

    private static int feedback(CommandContext<CommandSourceStack> c) {
        var src = c.getSource();
        String text = StringArgumentType.getString(c, "text");
        if (text.length() > ReportPayload.MAX_FEEDBACK_TEXT) {
            src.sendFailure(Component.translatable("cccloud.feedback.too_long", ReportPayload.MAX_FEEDBACK_TEXT));
            return 0;
        }
        var payload = ReportService.feedbackPayload(src.getServer(), "other", text, null, false);
        if (payload == null) {
            src.sendFailure(Component.translatable("cccloud.feedback.empty"));
            return 0;
        }
        String id = HexFormat.of().toHexDigits(RANDOM.nextInt());
        long now = System.currentTimeMillis();
        synchronized (PENDING) {
            PENDING.values().removeIf(p -> p.expires() < now);
            while (PENDING.size() >= MAX_PENDING) PENDING.remove(PENDING.keySet().iterator().next());
            PENDING.put(id, new Pending(sender(src), payload, now + FEEDBACK_VALID_MS));
        }
        src.sendSystemMessage(Component.translatable("cccloud.feedback.preview").withStyle(ChatFormatting.AQUA));
        src.sendSystemMessage(Component.literal(payload.toString()).withStyle(ChatFormatting.GRAY));
        src.sendSystemMessage(Component.translatable("cccloud.feedback.warning").withStyle(ChatFormatting.YELLOW));
        src.sendSystemMessage(privacyLink(src));
        src.sendSystemMessage(action("cccloud.feedback.send", "/cccloud feedback-send " + id, ChatFormatting.GREEN)
            .append(Component.literal(" "))
            .append(action("cccloud.feedback.discard", "/cccloud feedback-discard " + id, ChatFormatting.RED)));
        return 1;
    }

    /** The waiting message of this sender, taken out so that a second click sends nothing. */
    private static @org.jspecify.annotations.Nullable Pending take(CommandSourceStack src, String id) {
        synchronized (PENDING) {
            var pending = PENDING.get(id);
            if (pending == null || !pending.sender().equals(sender(src))) return null;
            PENDING.remove(id);
            return pending.expires() < System.currentTimeMillis() ? null : pending;
        }
    }

    private static int feedbackSend(CommandContext<CommandSourceStack> c) {
        var src = c.getSource();
        var pending = take(src, StringArgumentType.getString(c, "id"));
        if (pending == null) {
            src.sendFailure(Component.translatable("cccloud.feedback.gone"));
            return 0;
        }
        var server = src.getServer();
        boolean queued = ReportService.sendFeedback(pending.sender(), pending.payload(), sent -> server.execute(() -> {
            if (sent) src.sendSystemMessage(Component.translatable("cccloud.feedback.sent").withStyle(ChatFormatting.GREEN));
            else src.sendFailure(Component.translatable("cccloud.feedback.failed"));
        }));
        if (!queued) src.sendFailure(Component.translatable("cccloud.feedback.limit"));
        else CCCloud.LOGGER.info("[cccloud] {} sent feedback", src.getTextName());
        return queued ? 1 : 0;
    }

    private static int feedbackDiscard(CommandContext<CommandSourceStack> c) {
        var src = c.getSource();
        boolean found = take(src, StringArgumentType.getString(c, "id")) != null;
        src.sendSystemMessage(Component.translatable(found ? "cccloud.feedback.discarded" : "cccloud.feedback.gone").withStyle(ChatFormatting.GRAY));
        return found ? 1 : 0;
    }
}
