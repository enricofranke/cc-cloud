package ai.ezfox.cccloud.tablet;

import ai.ezfox.cccloud.gui.CloudMenu;
import ai.ezfox.cccloud.ingame.DashboardModel;
import ai.ezfox.cccloud.ingame.InGameRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * The open Cloud Tablet (slot-less menu). Server side it marks the session as open ({@link TabletService} checks range,
 * rights and the tablet itself every second and closes the menu when one fails). Client side it keeps the
 * {@link DashboardModel} built from the hub messages; its JSON is the screen's view.
 */
public class TabletMenu extends CloudMenu {
    private final String mainframeName;
    private final boolean advanced;
    private final boolean canControl;
    /** Client side: the dashboard as the browser would see it. */
    public final DashboardModel model = new DashboardModel();

    public TabletMenu(int id, BlockPos pos, String mainframeName, boolean advanced, boolean canControl) {
        super(InGameRegistry.TABLET_MENU.get(), id, pos, "{}");
        this.mainframeName = mainframeName;
        this.advanced = advanced;
        this.canControl = canControl;
    }

    public TabletMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(InGameRegistry.TABLET_MENU.get(), id, buf);
        this.mainframeName = buf.readUtf(128);
        this.advanced = buf.readBoolean();
        this.canControl = buf.readBoolean();
    }

    static void writeOpen(RegistryFriendlyByteBuf buf, BlockPos pos, String mainframeName, boolean advanced, boolean canControl) {
        buf.writeBlockPos(pos);
        buf.writeUtf("{}", MAX_VIEW);
        buf.writeUtf(mainframeName, 128);
        buf.writeBoolean(advanced);
        buf.writeBoolean(canControl);
    }

    public String mainframeName() {
        return mainframeName;
    }

    public boolean isAdvanced() {
        return advanced;
    }

    public boolean canControl() {
        return canControl;
    }

    /** Client: applies hub messages (JSON array) and publishes the new state to the screen. */
    public void apply(String messages) {
        if (model.apply(messages)) setView(model.toJson());
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // range, rights and the tablet are checked by TabletService
    }

    @Override
    protected boolean blockStillThere(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) TabletService.closed(serverPlayer, containerId);
    }
}
