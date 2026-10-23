package ai.ezfox.cccloud.gui;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** The Cloud Mainframe screen's menu; {@link #pos()} is the lower half (the block entity). */
public class MainframeMenu extends CloudMenu {
    /** Slot positions relative to the screen's leftPos/topPos (topPos = panel top + header height), see MainframeScreen. */
    public static final int CARD_X = 13, CARD_Y = 55, CARD_STEP = 20, CORE_X = 101, INV_X = 13, INV_Y = 81;

    /** Client: the Upgrades tab is shown (the slots are only active there). */
    public boolean upgradesVisible;
    private final java.util.function.Predicate<Player> canEdit;
    private final net.minecraft.world.Container upgrades;
    private final boolean client;

    /** Server side. {@code canEdit}: devices.manage on the mainframe (checked live on every slot change). */
    public MainframeMenu(int id, Inventory inventory, BlockPos pos, String view, net.minecraft.world.Container upgrades,
                         java.util.function.Predicate<Player> canEdit) {
        super(ModRegistry.MAINFRAME_MENU.get(), id, pos, view);
        this.canEdit = canEdit;
        this.upgrades = upgrades;
        this.client = false;
        addSlots(inventory);
    }

    public MainframeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(ModRegistry.MAINFRAME_MENU.get(), id, buf);
        this.canEdit = p -> true;
        this.upgrades = ai.ezfox.cccloud.upgrade.MainframeUpgrades.client();
        this.client = true;
        addSlots(inventory);
    }

    private void addSlots(Inventory inventory) {
        for (int i = 0; i < ai.ezfox.cccloud.upgrade.MainframeUpgrades.SIZE; i++) {
            int x = i == ai.ezfox.cccloud.upgrade.MainframeUpgrades.CORE_SLOT ? CORE_X : CARD_X + i * CARD_STEP;
            addSlot(new UpgradeSlot(upgrades, i, x, CARD_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new TabSlot(inventory, 9 + row * 9 + col, INV_X + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new TabSlot(inventory, col, INV_X + col * 18, INV_Y + 58));
    }

    private class TabSlot extends net.minecraft.world.inventory.Slot {
        TabSlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !client || upgradesVisible;
        }
    }

    private class UpgradeSlot extends net.minecraft.world.inventory.Slot {
        UpgradeSlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(net.minecraft.world.item.ItemStack stack) {
            return ai.ezfox.cccloud.upgrade.MainframeUpgrades.allowed(getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return canEdit.test(player);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            return !client || upgradesVisible;
        }
    }

    /** Upgrade slots: only players with devices.manage may change them (checked on the server for every click). */
    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput input, Player player) {
        if (slotId >= 0 && slotId < ai.ezfox.cccloud.upgrade.MainframeUpgrades.SIZE && !canEdit.test(player)) return;
        super.clicked(slotId, button, input, player);
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return net.minecraft.world.item.ItemStack.EMPTY;
        var stack = slot.getItem();
        var copy = stack.copy();
        int size = ai.ezfox.cccloud.upgrade.MainframeUpgrades.SIZE;
        if (index < size) {
            if (!canEdit.test(player) || !moveItemStackTo(stack, size, slots.size(), true)) return net.minecraft.world.item.ItemStack.EMPTY;
        } else {
            if (!canEdit.test(player)) return net.minecraft.world.item.ItemStack.EMPTY;
            boolean moved = false;
            for (int i = 0; i < size && !stack.isEmpty(); i++) {
                var target = slots.get(i);
                if (!target.hasItem() && target.mayPlace(stack)) {
                    target.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) return net.minecraft.world.item.ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(net.minecraft.world.item.ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    /** Closing the screen while a browser waits for approval: the chat request is sent again (with the time left). */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) || serverPlayer.level().getServer() == null) return;
        if (!(player.level().getBlockEntity(pos()) instanceof CloudMainframeBlockEntity mainframe) || !mainframe.has(player, ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE)) return;
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
