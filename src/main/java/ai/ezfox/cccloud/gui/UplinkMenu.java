package ai.ezfox.cccloud.gui;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** The Cloud Uplink screen's menu (also used for the ME/RS Cloud Bridges and the Cloud Relays). */
public class UplinkMenu extends CloudMenu {
    public UplinkMenu(int id, BlockPos pos, String view) {
        super(ModRegistry.UPLINK_MENU.get(), id, pos, view);
    }

    public UplinkMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(ModRegistry.UPLINK_MENU.get(), id, buf);
    }

    @Override
    protected boolean blockStillThere(Player player) {
        var be = player.level().getBlockEntity(pos());
        return be instanceof CloudUplinkBlockEntity || be instanceof ai.ezfox.cccloud.bridge.BridgeHost
            || be instanceof ai.ezfox.cccloud.relay.CloudRelayBlockEntity;
    }
}
