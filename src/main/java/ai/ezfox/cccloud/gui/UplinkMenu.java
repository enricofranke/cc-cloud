package ai.ezfox.cccloud.gui;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** The Cloud Uplink screen's menu. */
public class UplinkMenu extends CloudMenu {
    public UplinkMenu(int id, BlockPos pos, String view) {
        super(ModRegistry.UPLINK_MENU.get(), id, pos, view);
    }

    public UplinkMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(ModRegistry.UPLINK_MENU.get(), id, buf);
    }

    @Override
    protected boolean blockStillThere(Player player) {
        return player.level().getBlockEntity(pos()) instanceof CloudUplinkBlockEntity;
    }
}
