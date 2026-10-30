package ai.ezfox.cccloud.rsdevice;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.gui.CloudMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** The screen of a Cloud Switch / Button / Lamp / Sensor (slot-less, like the uplink screen). */
public class RsDeviceMenu extends CloudMenu {
    /** Server side: the last view sent (only changes are sent). */
    private @org.jspecify.annotations.Nullable String lastView;

    public RsDeviceMenu(int id, BlockPos pos, String view) {
        super(ModRegistry.RS_DEVICE_MENU.get(), id, pos, view);
    }

    public RsDeviceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(ModRegistry.RS_DEVICE_MENU.get(), id, buf);
    }

    @org.jspecify.annotations.Nullable String lastSent() {
        return lastView;
    }

    void setLastSent(String view) {
        lastView = view;
    }

    @Override
    protected boolean blockStillThere(Player player) {
        return player.level().getBlockEntity(pos()) instanceof RsDeviceBlockEntity;
    }
}
