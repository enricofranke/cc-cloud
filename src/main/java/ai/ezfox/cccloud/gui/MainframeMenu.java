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

    @Override
    protected boolean blockStillThere(Player player) {
        return player.level().getBlockEntity(pos()) instanceof CloudMainframeBlockEntity;
    }
}
