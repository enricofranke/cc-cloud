package ai.ezfox.cccloud.gui;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** The Cloud Mainframe screen's menu; {@link #pos()} is the lower half (the block entity). */
public class MainframeMenu extends CloudMenu {
    public MainframeMenu(int id, BlockPos pos, String view) {
        super(ModRegistry.MAINFRAME_MENU.get(), id, pos, view);
    }

    public MainframeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(ModRegistry.MAINFRAME_MENU.get(), id, buf);
    }

    /** Closing the screen while a browser waits for approval: the chat request is sent again (with the time left). */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) || serverPlayer.level().getServer() == null) return;
        if (!(player.level().getBlockEntity(pos()) instanceof CloudMainframeBlockEntity mainframe) || !mainframe.canManage(player)) return;
        var auth = ai.ezfox.cccloud.web.WebAuthData.get(serverPlayer.level().getServer());
        for (var pending : auth.pendingFor(mainframe.getMainframeId())) {
            ai.ezfox.cccloud.web.ApprovalPrompt.send(serverPlayer, pending, mainframe.getDisplayName());
        }
    }

    @Override
    protected boolean blockStillThere(Player player) {
        return player.level().getBlockEntity(pos()) instanceof CloudMainframeBlockEntity;
    }
}
