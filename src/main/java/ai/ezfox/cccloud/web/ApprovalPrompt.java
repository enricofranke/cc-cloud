package ai.ezfox.cccloud.web;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** The clickable in-game approval request ([Allow] [Deny]) for a browser that wants to connect, with the time left. */
public final class ApprovalPrompt {
    private record Sent(long expiresAt, Set<UUID> players) {
    }

    /** Players who already have a request in chat, per pending id (closing the mainframe screen must not repeat it). */
    private static final Map<String, Sent> SENT = new ConcurrentHashMap<>();

    private ApprovalPrompt() {
    }

    /** Like {@link #send}, but only to a player who has not had this request in chat yet. */
    public static void sendIfNew(ServerPlayer player, AuthStore.Pending pending, @Nullable String mainframeName) {
        if (!alreadySent(pending.id(), player.getUUID())) send(player, pending, mainframeName);
    }

    static boolean alreadySent(String pendingId, UUID player) {
        var sent = SENT.get(pendingId);
        return sent != null && sent.players().contains(player);
    }

    /** Remembers that {@code player} has the request in chat; forgets expired requests. */
    static void markSent(String pendingId, long expiresAt, UUID player, long now) {
        SENT.values().removeIf(sent -> sent.expiresAt() < now);
        SENT.computeIfAbsent(pendingId, id -> new Sent(expiresAt, new HashSet<>())).players().add(player);
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
        markSent(pending.id(), pending.expiresAt(), player.getUUID(), System.currentTimeMillis());
        // A browser from outside the LAN (internet access on): say so, so nobody clicks [Allow] for a stranger.
        if (!IpUtil.isLan(pending.ip())) {
            player.sendSystemMessage(Component.translatable("cccloud.approval.internet", pending.ip()).withStyle(ChatFormatting.GOLD));
        }
    }
}
