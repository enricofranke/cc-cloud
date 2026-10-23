package ai.ezfox.cccloud.compat.rs;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.bridge.StorageAdapter;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.autocrafting.preview.Preview;
import com.refinedmods.refinedstorage.api.autocrafting.preview.PreviewType;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskState;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.GraphNetworkComponent;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.storage.StorageNetworkNode;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/** {@link StorageAdapter} for a Refined Storage 2 network. Server thread only. */
final class RsAdapter implements StorageAdapter {
    private static final int CALC_TIMEOUT_TICKS = 600;
    /** startTask plans synchronously on the calling (server) thread; cap that at 250 ms. */
    private static final long START_BUDGET_NANOS = 250_000_000L;

    private final RsBridgeBlockEntity be;
    private final List<Pending> pending = new ArrayList<>();
    private long tick;
    private long countsTick = Long.MIN_VALUE;
    private Map<Item, Long> counts = Map.of();

    private record Pending(CompletableFuture<Optional<Preview>> future, DeadlineToken token, ItemResource resource, long count, String user,
                           Consumer<CraftOutcome> done, long started) {
    }

    /** Cancellation token with a deadline. */
    static final class DeadlineToken implements CancellationToken {
        private volatile long deadline;
        private volatile boolean cancelled;

        DeadlineToken(long nanos) {
            this.deadline = System.nanoTime() + nanos;
        }

        @Override
        public boolean isCancelled() {
            return cancelled || System.nanoTime() > deadline;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private record PendingPreview(CompletableFuture<Optional<Preview>> future, DeadlineToken token, Consumer<Plan> done, long started) {
    }

    private final List<PendingPreview> previews = new ArrayList<>();

    RsAdapter(RsBridgeBlockEntity be) {
        this.be = be;
    }

    private @Nullable Network network() {
        var node = be.node();
        return node.isActive() ? node.getNetwork() : null;
    }

    @Override
    public CloudStatus status() {
        var node = be.node();
        if (node.isActive() && node.getNetwork() != null) return CloudStatus.ONLINE;
        var network = node.getNetwork();
        return network != null && network.getComponent(GraphNetworkComponent.class).getContainers().size() > 1 ? CloudStatus.CONNECTING : CloudStatus.OFFLINE;
    }

    @Override
    public double energy() {
        var network = network();
        return network == null ? 0 : network.getComponent(EnergyNetworkComponent.class).getStored();
    }

    @Override
    public double maxEnergy() {
        var network = network();
        return network == null ? 0 : network.getComponent(EnergyNetworkComponent.class).getCapacity();
    }

    @Override
    public double energyUsage() {
        var network = network();
        if (network == null) return 0;
        long usage = 0;
        for (var container : network.getComponent(GraphNetworkComponent.class).getContainers()) {
            if (container.getNode() instanceof AbstractNetworkNode node && node.isActive()) usage += node.getEnergyUsage();
        }
        return usage;
    }

    @Override
    public String energyUnit() {
        return "FE";
    }

    @Override
    public Totals totals() {
        var network = network();
        if (network == null) return new Totals(0, null, 0, null, 0, null);
        long items = 0, types = 0, fluids = 0;
        for (var amount : network.getComponent(StorageNetworkComponent.class).getAll()) {
            if (amount.resource() instanceof ItemResource) {
                items += amount.amount();
                types++;
            } else if (amount.resource() instanceof FluidResource) {
                fluids += amount.amount();
            }
        }
        // Capacity of storage blocks and disk drives (StorageNetworkNode). RS reports item and fluid storage together,
        // so with fluid disks/blocks on the network this total also contains their capacity (in mB).
        long capacity = 0;
        boolean any = false;
        for (var container : network.getComponent(GraphNetworkComponent.class).getContainers()) {
            if (container.getNode() instanceof StorageNetworkNode node) {
                capacity += node.getCapacity();
                any = true;
            }
        }
        return new Totals(items, any ? capacity : null, types, null, fluids, null);
    }

    @Override
    public void forEachItem(ItemSink sink) {
        var network = network();
        if (network == null) return;
        for (var amount : network.getComponent(StorageNetworkComponent.class).getAll()) {
            if (amount.resource() instanceof ItemResource item) sink.accept(item.item(), amount.amount());
        }
    }

    @Override
    public void forEachCraftable(Consumer<Item> sink) {
        var network = network();
        if (network == null) return;
        for (var output : network.getComponent(AutocraftingNetworkComponent.class).getOutputs()) {
            if (output instanceof ItemResource item) sink.accept(item.item());
        }
    }

    @Override
    public void forEachFluid(FluidSink sink) {
        var network = network();
        if (network == null) return;
        for (var amount : network.getComponent(StorageNetworkComponent.class).getAll()) {
            if (amount.resource() instanceof FluidResource fluid) {
                Fluid f = fluid.fluid();
                String id = BuiltInRegistries.FLUID.getKey(f).toString();
                String name;
                try {
                    name = f.getFluidType().getDescription().getString();
                } catch (RuntimeException e) {
                    name = id;
                }
                sink.accept(id, name, amount.amount());
            }
        }
    }

    @Override
    public long itemCount(Item item) {
        if (countsTick != tick) {
            countsTick = tick;
            var map = new HashMap<Item, Long>();
            forEachItem((i, count) -> map.merge(i, count, Long::sum));
            counts = map;
        }
        return counts.getOrDefault(item, 0L);
    }

    @Override
    public boolean isCraftable(Item item) {
        var network = network();
        return network != null && network.getComponent(AutocraftingNetworkComponent.class).getOutputs().contains(new ItemResource(item));
    }

    @Override
    public void startCraft(Item item, long count, String user, Consumer<CraftOutcome> done) {
        var network = network();
        if (network == null) {
            done.accept(CraftOutcome.failed("offline"));
            return;
        }
        var resource = new ItemResource(item);
        var token = new DeadlineToken(30_000_000_000L);
        var future = network.getComponent(AutocraftingNetworkComponent.class).getPreview(resource, count, token);
        pending.add(new Pending(future, token, resource, count, user, done, tick));
    }

    @Override
    public void preview(Item item, long count, Consumer<Plan> done) {
        var network = network();
        if (network == null) {
            done.accept(Plan.failed("offline"));
            return;
        }
        var token = new DeadlineToken(30_000_000_000L);
        var future = network.getComponent(AutocraftingNetworkComponent.class).getPreview(new ItemResource(item), count, token);
        previews.add(new PendingPreview(future, token, done, tick));
    }

    private static Plan toPlan(Preview preview) {
        if (preview.type() == PreviewType.CANCELLED) return Plan.failed("timeout");
        var list = new ArrayList<PlanEntry>();
        for (var entry : preview.items()) {
            if (entry.resource() instanceof ItemResource item) list.add(new PlanEntry(item.item(), entry.available(), entry.toCraft(), entry.missing()));
        }
        boolean missing = preview.type() != PreviewType.SUCCESS;
        return new Plan(null, missing, list, -1, -1);
    }

    @Override
    public boolean cancelNetworkJob(String id) {
        var network = network();
        if (network == null || id.length() < 8) return false;
        var component = network.getComponent(AutocraftingNetworkComponent.class);
        for (var status : component.getStatuses()) {
            if (status.info().id().id().toString().startsWith(id)) {
                component.cancel(status.info().id());
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        tick++;
        for (Iterator<PendingPreview> it = previews.iterator(); it.hasNext(); ) {
            var p = it.next();
            if (!p.future().isDone()) {
                if (tick - p.started() > CALC_TIMEOUT_TICKS) {
                    p.token().cancel();
                    it.remove();
                    p.done().accept(Plan.failed("timeout"));
                }
                continue;
            }
            it.remove();
            try {
                var preview = p.future().get();
                p.done().accept(preview.isEmpty() ? Plan.failed("not_craftable") : toPlan(preview.get()));
            } catch (Exception e) {
                p.done().accept(Plan.failed("not_craftable"));
            }
        }
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            var p = it.next();
            if (!p.future().isDone()) {
                if (tick - p.started() > CALC_TIMEOUT_TICKS) {
                    p.token().cancel();
                    it.remove();
                    p.done().accept(CraftOutcome.failed("timeout"));
                }
                continue;
            }
            it.remove();
            p.done().accept(start(p));
        }
    }

    private CraftOutcome start(Pending p) {
        Optional<Preview> preview;
        try {
            preview = p.future().get();
        } catch (Exception e) {
            CCCloud.LOGGER.warn("[cccloud] RS autocrafting preview failed", e);
            return CraftOutcome.failed("missing_resources");
        }
        if (preview.isEmpty()) return CraftOutcome.failed("not_craftable");
        var type = preview.get().type();
        if (type == PreviewType.MISSING_RESOURCES) return CraftOutcome.failed("missing_resources");
        if (type != PreviewType.SUCCESS) return CraftOutcome.failed(type == PreviewType.CANCELLED ? "timeout" : "not_craftable");
        var network = network();
        if (network == null) return CraftOutcome.failed("offline");
        Optional<TaskId> task = network.getComponent(AutocraftingNetworkComponent.class)
            .startTask(p.resource(), p.count(), new PlayerActor(p.user()), false, new DeadlineToken(START_BUDGET_NANOS));
        return task.<CraftOutcome>map(CraftOutcome::started).orElseGet(() -> CraftOutcome.failed("missing_resources"));
    }

    @Override
    public JobState jobState(Object handle) {
        var network = network();
        if (network == null) return new JobState("running", 0);
        for (var status : network.getComponent(AutocraftingNetworkComponent.class).getStatuses()) {
            if (!status.info().id().equals(handle)) continue;
            if (status.state() == TaskState.COMPLETED) return new JobState("done", 1);
            double pct = status.percentageCompleted();
            return new JobState("running", pct > 1 ? pct / 100 : pct);
        }
        return new JobState("done", 1);
    }

    @Override
    public boolean cancel(Object handle) {
        var network = network();
        if (network == null || !(handle instanceof TaskId id)) return false;
        network.getComponent(AutocraftingNetworkComponent.class).cancel(id);
        return true;
    }

    @Override
    public List<NetworkJob> networkJobs(List<Object> ownHandles) {
        var network = network();
        var list = new ArrayList<NetworkJob>();
        if (network == null) return list;
        var own = new HashSet<>(ownHandles);
        for (var status : network.getComponent(AutocraftingNetworkComponent.class).getStatuses()) {
            if (own.contains(status.info().id()) || !(status.info().resource() instanceof ItemResource item)) continue;
            double pct = status.percentageCompleted();
            list.add(new NetworkJob(status.info().id().id().toString().substring(0, 8), item.item(), status.info().amount(), pct > 1 ? pct / 100 : pct));
        }
        return list;
    }
}
