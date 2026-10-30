package ai.ezfox.cccloud.web;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** The clickable in-game approval request ([Allow] [Deny]) for a browser that wants to connect, with the time left. */
public final class ApprovalPrompt {
    private ApprovalPrompt() {
    }

    /**
     * @param mainframeName null for account requests ("your account")
     */
    public static void send(ServerPlayer player, AuthStore.Pending pending, @Nullable String mainframeName) {
        long seconds = Math.max(0, (pending.expiresAt() - System.currentTimeMillis() + 999) / 1000);
        var allow = Component.translatable("cccloud.approval.allow").withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)
            .withClickEvent(new ClickEvent.RunCommand("/cccloud approve " + pending.id()))
            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cccloud.approval.allow_hover"))));
        var deny = Component.translatable("cccloud.approval.deny").withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)
            .withClickEvent(new ClickEvent.RunCommand("/cccloud deny " + pending.id()))
            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cccloud.approval.deny_hover"))));
        var device = Component.literal(pending.deviceName()).withStyle(ChatFormatting.AQUA);
        var ip = Component.literal(pending.ip()).withStyle(ChatFormatting.GRAY);
        var message = pending.isAccount()
            ? Component.translatable("cccloud.approval.account_request", device, ip, allow, deny)
            : Component.translatable("cccloud.approval.request", device, ip, mainframeName == null ? "" : mainframeName, allow, deny);
        message.append(Component.literal(" ")).append(Component.translatable("cccloud.approval.expires", String.format("%d:%02d", seconds / 60, seconds % 60))
            .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(message);
        // A browser from outside the LAN (internet access on): say so, so nobody clicks [Allow] for a stranger.
        if (!IpUtil.isLan(pending.ip())) {
            player.sendSystemMessage(Component.translatable("cccloud.approval.internet", pending.ip()).withStyle(ChatFormatting.GOLD));
        }
    }
}
