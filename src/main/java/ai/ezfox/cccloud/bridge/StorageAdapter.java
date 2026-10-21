package ai.ezfox.cccloud.bridge;

import ai.ezfox.cccloud.block.CloudStatus;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * The small, mod-specific part of a Cloud Bridge (implemented in compat/ae2 and compat/rs). Everything else – search,
 * limits, watchlist, job tracking, Lua and dashboard plumbing – lives in {@link BridgeCore} and does not know AE2 or RS.
 * <p>
 * All methods are called on the server thread only. Asynchronous work (crafting calculations) must be polled in
 * {@link #tick()} so that callbacks also run on the server thread.
 */
public interface StorageAdapter {
    CloudStatus status();

    /** Stored energy in {@link #energyUnit()}. */
    double energy();

    double maxEnergy();

    /** Average energy usage per tick, or NaN if the mod does not expose it. */
    double energyUsage();

    /** "AE" or "FE". */
    String energyUnit();

    Totals totals();

    /** Every stored item with its amount (several entries per item are allowed, e.g. different components). */
    void forEachItem(ItemSink sink);

    /** Every item the network has a pattern for. */
    void forEachCraftable(Consumer<Item> sink);

    void forEachFluid(FluidSink sink);

    /** Stored amount of an item (all component variants). */
    long itemCount(Item item);

    boolean isCraftable(Item item);

    /**
     * Plans and starts a crafting job. {@code done} must be called exactly once, from {@link #tick()} or directly, with
     * either a job handle or an error code ({@code not_craftable}, {@code no_cpu}, {@code missing_resources},
     * {@code offline}, {@code timeout}, {@code busy}).
     */
    void startCraft(Item item, long count, String user, Consumer<CraftOutcome> done);

    /** Current state of a job started by {@link #startCraft}. */
    JobState jobState(Object handle);

    boolean cancel(Object handle);

    /** Jobs running on the network that were not started through this bridge (e.g. from a terminal). */
    List<NetworkJob> networkJobs(List<Object> ownHandles);

    /** Polls asynchronous work. */
    void tick();

    @FunctionalInterface
    interface ItemSink {
        void accept(Item item, long count);
    }

    @FunctionalInterface
    interface FluidSink {
        void accept(String name, String displayName, long amount);
    }

    /**
     * Storage totals. Item amounts are item counts, fluid amounts are millibuckets. {@code null} = not exposed by the mod.
     */
    record Totals(long itemsUsed, @Nullable Long itemsTotal, long typesUsed, @Nullable Long typesTotal, long fluidsUsed, @Nullable Long fluidsTotal) {
    }

    record CraftOutcome(@Nullable Object handle, @Nullable String error) {
        public static CraftOutcome started(Object handle) {
            return new CraftOutcome(handle, null);
        }

        public static CraftOutcome failed(String error) {
            return new CraftOutcome(null, error);
        }
    }

    /** @param status running, done, failed or cancelled; progress 0..1. */
    record JobState(String status, double progress) {
    }

    record NetworkJob(String id, Item item, long count, double progress) {
    }
}
