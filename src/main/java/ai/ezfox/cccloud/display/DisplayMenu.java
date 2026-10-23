package ai.ezfox.cccloud.display;

import ai.ezfox.cccloud.gui.CloudMenu;
import ai.ezfox.cccloud.ingame.InGameRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** The Cloud Display screen (status, section picker, in-world clicks switch). The view is built by {@link DisplayService}. */
public class DisplayMenu extends CloudMenu {
    public DisplayMenu(int id, BlockPos pos, String view) {
        super(InGameRegistry.DISPLAY_MENU.get(), id, pos, view);
    }

    public DisplayMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(InGameRegistry.DISPLAY_MENU.get(), id, buf);
    }

    @Override
    protected boolean blockStillThere(Player player) {
        return player.level().getBlockEntity(pos()) instanceof CloudDisplayBlockEntity;
    }
}
