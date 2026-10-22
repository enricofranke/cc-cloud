package ai.ezfox.cccloud.power;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The mainframe's FE buffer, exposed as {@code Capabilities.Energy.BLOCK} on both halves (receive-only). Insertion is
 * limited per tick (not per call), transaction-safe: the allowance is derived from the journaled energy amount, so a
 * rolled-back insert also restores the allowance. Mods that still work with the legacy {@code IEnergyStorage} wrap
 * this handler with {@code IEnergyStorage.of(handler)} and feed it the same way.
 */
public class MainframeEnergy extends SimpleEnergyHandler {
    private final int maxPerTick;
    private int tickStart;

    public MainframeEnergy(int capacity, int maxPerTick) {
        super(capacity, maxPerTick, 0);
        this.maxPerTick = maxPerTick;
    }

    /** Called by the mainframe each tick after drawing its usage. */
    public void startTick() {
        tickStart = energy;
    }

    /** Removes energy (the mainframe's own consumption; not reachable through the capability). */
    public void consume(long amount) {
        set((int) Math.max(0, energy - Math.min(amount, energy)));
    }

    /** Reconfigures the capacity (config change); excess energy is kept up to the new capacity. */
    public void setCapacity(int capacity) {
        this.capacity = Math.max(0, capacity);
        if (energy > this.capacity) set(this.capacity);
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        int allowance = Math.max(0, maxPerTick - Math.max(0, energy - tickStart));
        return super.insert(Math.min(amount, allowance), transaction);
    }

    public int energy() {
        return energy;
    }

    public int capacity() {
        return capacity;
    }
}
