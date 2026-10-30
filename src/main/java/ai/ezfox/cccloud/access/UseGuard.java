package ai.ezfox.cccloud.access;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.web.WebAuthData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Enforces {@code cccloud.use} (place/use the blocks) and {@code cccloud.limit.mainframes} (max mainframes per player,
 * ignored with {@code cccloud.bypass.limits}).
 */
public final class UseGuard {
    private UseGuard() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, event -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!isOurs(event.getPlacedBlock().getBlock())) return;
            if (!Access.has(player, ServerPermissions.Node.USE)) {
                deny(player);
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, event -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!isOurs(event.getLevel().getBlockState(event.getPos()).getBlock())) return;
            if (!Access.has(player, ServerPermissions.Node.USE)) {
                deny(player);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
    }

    private static boolean isOurs(net.minecraft.world.level.block.Block block) {
        return CCCloud.MODID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace());
    }

    private static void deny(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("cccloud.access.no_use").withStyle(ChatFormatting.RED));
    }

    /**
     * Server side check before a mainframe is placed: {@code cccloud.use} and the per-player mainframe limit. The item of
     * a removed mainframe ({@code identity}) restores that mainframe for its owner, so the placer's limit does not apply;
     * it is refused while that mainframe still stands somewhere.
     */
    public static boolean mayPlaceMainframe(ServerPlayer player, ai.ezfox.cccloud.item.@org.jspecify.annotations.Nullable MainframeIdentity identity) {
        var server = player.level().getServer();
        if (server == null) return true;
        var permissions = CloudPermissions.of(server);
        if (!permissions.has(player.getUUID(), ServerPermissions.Node.USE)) {
            deny(player);
            return false;
        }
        if (identity != null) {
            var standing = ai.ezfox.cccloud.network.CloudNetwork.mainframe(identity.id());
            if (standing != null) {
                player.sendSystemMessage(Component.translatable("cccloud.mainframe.already_placed", standing.getDisplayName()).withStyle(ChatFormatting.RED));
                return false;
            }
            if (ai.ezfox.cccloud.block.MainframeRetention.get(server).entry(identity.id()) != null) return true;
        }
        if (permissions.has(player.getUUID(), ServerPermissions.Node.BYPASS_LIMITS)) return true;
        int limit = permissions.mainframeLimit(player.getUUID());
        // Removed mainframes keep their data for a while but do not count against the limit.
        int owned = WebAuthData.access(server).ownedBy(player.getUUID()) - ai.ezfox.cccloud.block.MainframeRetention.get(server).ownedBy(player.getUUID());
        if (owned >= limit) {
            player.sendSystemMessage(Component.translatable("cccloud.access.mainframe_limit", limit).withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }
}
