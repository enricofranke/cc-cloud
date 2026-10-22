package ai.ezfox.cccloud.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Base of the slot-less mainframe and uplink menus. The server uses the open menu to know who looks at which block
 * (live updates, permission context); the client keeps the latest view (JSON) here. The view is replaced by
 * {@link GuiNetwork.ViewPayload}s while the screen is open.
 */
public abstract class CloudMenu extends AbstractContainerMenu {
    /** Max size of a view (JSON) on the wire. */
    public static final int MAX_VIEW = 32 * 1024;
    /** Max distance (blocks) between the player and the block while the screen is open. */
    public static final double RANGE = 8.0;

    private final BlockPos pos;
    private String view;
    private int viewVersion;
    /** Server side: the last view sent, to only send changes. */
    @Nullable String lastSent;

    protected CloudMenu(MenuType<?> type, int id, BlockPos pos, String view) {
        super(type, id);
        this.pos = pos.immutable();
        this.view = view;
        this.lastSent = view;
    }

    protected CloudMenu(MenuType<?> type, int id, RegistryFriendlyByteBuf buf) {
        this(type, id, buf.readBlockPos(), buf.readUtf(MAX_VIEW));
    }

    public BlockPos pos() {
        return pos;
    }

    public String view() {
        return view;
    }

    /** Incremented whenever a new view arrives, so the screen knows to rebuild. */
    public int viewVersion() {
        return viewVersion;
    }

    public void setView(String view) {
        this.view = view;
        viewVersion++;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getCenter()) <= RANGE * RANGE && blockStillThere(player);
    }

    protected abstract boolean blockStillThere(Player player);
}
