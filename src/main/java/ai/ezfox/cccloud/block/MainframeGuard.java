package ai.ezfox.cccloud.block;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.CloudPermissions;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.access.ServerPermissions;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Misclick guard ({@code mainframeBreakProtection}, default on): in creative a Cloud Mainframe only breaks while
 * sneaking; in survival only its owner, one of its admins or a server admin ({@code cccloud.admin.manage}, op) may break
 * it. Claim mods (FTB Chunks, Open Parties and Claims) cancel the same event for foreign claims; this is an extra layer
 * that also covers unclaimed land. Breaking by explosions, machines or commands is not a player break and not affected.
 */
public final class MainframeGuard {
    private MainframeGuard() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, MainframeGuard::onBreak);
    }

    private static void onBreak(BreakBlockEvent event) {
        if (event.isCanceled() || !(event.getState().getBlock() instanceof CloudMainframeBlock)) return;
        if (!CloudConfig.get(CloudConfig.MAINFRAME_BREAK_PROTECTION)) return;
        var player = event.getPlayer();
        if (!(event.getLevel() instanceof net.minecraft.world.level.Level level) || level.isClientSide()) return;
        var mainframe = CloudMainframeBlock.getBlockEntity(level, event.getPos(), event.getState());
        String refusal = refusal(player, mainframe);
        if (refusal == null) return;
        event.setCanceled(true);
        event.setNotifyClient(true); // the client already removed the block (creative) or predicts it: send it back
        player.sendOverlayMessage(Component.translatable(refusal).withStyle(ChatFormatting.YELLOW));
    }

    /** Why this player may not break the mainframe right now (a translation key), or null if they may. */
    public static @org.jspecify.annotations.Nullable String refusal(Player player, @org.jspecify.annotations.Nullable CloudMainframeBlockEntity mainframe) {
        if (player.isCreative()) return player.isShiftKeyDown() ? null : "cccloud.mainframe.sneak_to_break";
        if (mainframe == null || mainframe.getOwner() == null || mainframe.getOwner().equals(player.getUUID())) return null;
        var server = player.level().getServer();
        if (server == null) return null;
        if (CloudPermissions.of(server).has(player.getUUID(), ServerPermissions.Node.ADMIN_MANAGE)) return null;
        var role = Access.resolver(server).resolve(player.getUUID(), mainframe.getMainframeId()).role();
        return role.rank >= Role.ADMIN.rank ? null : "cccloud.mainframe.not_yours_to_break";
    }
}
