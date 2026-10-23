package ai.ezfox.cccloud.upgrade;

import java.util.ArrayList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The mainframe's upgrade inventory: slots 0–3 take upgrade cards and the Chunk Anchor card (one per type counts),
 * slot 4 is the Quantum Core slot. Not exposed as an item handler, so hoppers and pipes cannot insert.
 */
public class MainframeUpgrades extends SimpleContainer {
    public static final int CARD_SLOTS = 4;
    public static final int CORE_SLOT = 4;
    public static final int SIZE = 5;

    private final Runnable changed;

    public MainframeUpgrades(Runnable changed) {
        super(SIZE);
        this.changed = changed;
    }

    /** Client-side stand-in (the menu syncs the real contents). */
    public static MainframeUpgrades client() {
        return new MainframeUpgrades(() -> {
        });
    }

    public static boolean allowed(int slot, ItemStack stack) {
        if (!(stack.getItem() instanceof UpgradeItem item)) return false;
        return slot == CORE_SLOT ? item.kind() == UpgradeItem.Kind.QUANTUM_CORE : item.kind() != UpgradeItem.Kind.QUANTUM_CORE;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return allowed(slot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        changed.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public UpgradeEffects effects(UpgradeEffects.Base base) {
        var cards = new ArrayList<UpgradeEffects.Card>();
        boolean quantum = false, anchor = false;
        for (int i = 0; i < SIZE; i++) {
            if (!(getItem(i).getItem() instanceof UpgradeItem item)) continue;
            switch (item.kind()) {
                case CARD -> {
                    if (i < CARD_SLOTS) cards.add(new UpgradeEffects.Card(item.type(), item.tier()));
                }
                case QUANTUM_CORE -> quantum |= i == CORE_SLOT;
                case CHUNK_ANCHOR -> anchor |= i < CARD_SLOTS;
            }
        }
        return UpgradeEffects.of(base, cards, quantum, anchor);
    }
}
