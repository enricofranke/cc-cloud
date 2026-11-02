package ai.ezfox.cccloud.code.game;

import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.ServerPermissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /cccloud scripts list}, {@code /cccloud scripts stop all} and {@code /cccloud scripts stop <name>}: the
 * server's emergency brake for Cloud Scripts. Listing needs {@code cccloud.admin.view}, stopping
 * {@code cccloud.admin.manage} (op level 2 by default; the console always). A stopped autostart script stays stopped
 * until someone starts it again or the server restarts.
 */
final class CodeCommands {
    private CodeCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cccloud").then(Commands.literal("scripts")
            .requires(src -> has(src, ServerPermissions.Node.ADMIN_VIEW))
            .then(Commands.literal("list").executes(CodeCommands::list))
            .then(Commands.literal("stop").requires(src -> has(src, ServerPermissions.Node.ADMIN_MANAGE))
                .then(Commands.literal("all").executes(c -> stop(c, null)))
                .then(Commands.argument("name", StringArgumentType.greedyString()).suggests(CodeCommands::suggest)
                    .executes(c -> stop(c, StringArgumentType.getString(c, "name")))))));
    }

    private static boolean has(CommandSourceStack src, ServerPermissions.Node node) {
        if (src.getEntity() instanceof ServerPlayer player) return Access.has(player, node);
        return src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        var running = CodeService.runningInfo();
        var source = c.getSource();
        if (running.isEmpty()) {
            source.sendSystemMessage(Component.translatable("cccloud.command.scripts.none").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        source.sendSystemMessage(Component.translatable("cccloud.command.scripts.header", running.size()).withStyle(ChatFormatting.AQUA));
        for (var r : running) {
            source.sendSystemMessage(Component.literal("▸ " + r.name()).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(String.format(Locale.ROOT, "  %s · %s · %.1f ms/s · %d handlers · %d timers", r.mainframe(), r.author(), r.cpuMs(),
                    r.handlers(), r.timers())).withStyle(ChatFormatting.GRAY)));
        }
        return running.size();
    }

    private static int stop(CommandContext<CommandSourceStack> c, @org.jspecify.annotations.Nullable String name) {
        int n = CodeService.stopMatching(name == null ? null : name.strip(), "command");
        var source = c.getSource();
        if (n == 0) {
            source.sendFailure(name == null ? Component.translatable("cccloud.command.scripts.none") : Component.translatable("cccloud.command.scripts.unknown", name));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("cccloud.command.scripts.stopped", n), true);
        ai.ezfox.cccloud.CCCloud.LOGGER.info("[cccloud] {} stopped {} Cloud Script(s) ({})", source.getTextName(), n, name == null ? "all" : name);
        return n;
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> c, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (var r : CodeService.runningInfo()) if (r.name().toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(r.name());
        return builder.buildFuture();
    }
}
