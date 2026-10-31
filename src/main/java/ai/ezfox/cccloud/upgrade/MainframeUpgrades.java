package ai.ezfox.cccloud.upgrade;

import java.util.ArrayList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The mainframe's upgrade inventory: slots 0–3 take upgrade cards and the Chunk Anchor card (one per type counts),
 * slot 4 is the Quantum Core slot. Filled from the mainframe screen or by right-clicking the mainframe with an upgrade
 * ({@link #install}). Not exposed as an item handler, so hoppers and pipes cannot insert.
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

    public enum Outcome { INSTALLED, REPLACED, ALREADY_INSTALLED, FULL }

    /** What {@link #install} did; {@code other} is the replaced card (REPLACED) or the one in the way (ALREADY_INSTALLED). */
    public record Install(Outcome outcome, ItemStack other) {
    }

    /**
     * Puts one of {@code stack} (an {@link UpgradeItem}) in, as a right-click on the mainframe does: into the first empty
     * slot that {@link #allowed} it (the Quantum Core: slot 4, cards and the Chunk Anchor: 0–3). When its type is
     * installed already (cards: same type; the core and the anchor: only one counts), a card of a higher tier replaces
     * the best one of that type and is returned in {@link Install#other}; the same or a lower tier is refused. Does not
     * change {@code stack}.
     */
    public Install install(ItemStack stack) {
        if (!(stack.getItem() instanceof UpgradeItem item)) throw new IllegalArgumentException("not an upgrade: " + stack);
        int same = -1, free = -1;
        for (int i = 0; i < SIZE; i++) {
            var current = getItem(i);
            if (current.isEmpty()) {
                if (free < 0 && allowed(i, stack)) free = i;
            } else if (allowed(i, current) && current.getItem() instanceof UpgradeItem installed
                && installed.kind() == item.kind() && installed.type() == item.type()
                && (same < 0 || tierRank(installed) > tierRank((UpgradeItem) getItem(same).getItem()))) {
                same = i;
            }
        }
        if (same >= 0) {
            var installed = getItem(same);
            if (tierRank(item) <= tierRank((UpgradeItem) installed.getItem())) return new Install(Outcome.ALREADY_INSTALLED, installed.copy());
            setItem(same, stack.copyWithCount(1));
            return new Install(Outcome.REPLACED, installed);
        }
        if (free < 0) return new Install(Outcome.FULL, ItemStack.EMPTY);
        setItem(free, stack.copyWithCount(1));
        return new Install(Outcome.INSTALLED, ItemStack.EMPTY);
    }

    /** Cards by tier; the core and the anchor have none (-1), so a second one never counts as higher. */
    private static int tierRank(UpgradeItem item) {
        return item.tier() == null ? -1 : item.tier().ordinal();
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
