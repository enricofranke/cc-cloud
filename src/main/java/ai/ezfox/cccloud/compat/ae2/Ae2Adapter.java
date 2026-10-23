package ai.ezfox.cccloud.compat.ae2;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.bridge.StorageAdapter;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.IBasicCellItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** {@link StorageAdapter} for an AE2 grid, using only the public AE2 API. Server thread only. */
final class Ae2Adapter implements StorageAdapter {
    /** Give up on a crafting calculation after 30 s. */
    private static final int CALC_TIMEOUT_TICKS = 600;

    private final MeBridgeBlockEntity be;
    private final List<Pending> pending = new ArrayList<>();
    private long tick;
    private long countsTick = Long.MIN_VALUE;
    private Map<Item, Long> counts = Map.of();

    /** A submitted job: the CPU running it (found right after submission) and the link, if AE2 returned one. */
    record Handle(AEKey key, long count, @Nullable ICraftingCPU cpu, @Nullable ICraftingLink link) {
    }

    private record Pending(Future<ICraftingPlan> future, AEItemKey key, long count, Consumer<CraftOutcome> done, long started) {
    }

    /** A craft preview being calculated (storage v2). */
    private record PendingPreview(Future<ICraftingPlan> future, Consumer<Plan> done, long started) {
    }

    private final List<PendingPreview> previews = new ArrayList<>();

    Ae2Adapter(MeBridgeBlockEntity be) {
        this.be = be;
    }

    private @Nullable IGrid grid() {
        var node = be.managedNode().getNode();
        return node == null ? null : node.getGrid();
    }

    @Override
    public CloudStatus status() {
        var managed = be.managedNode();
        if (managed.isActive()) return CloudStatus.ONLINE;
        var grid = grid();
        return grid != null && grid.size() > 1 ? CloudStatus.CONNECTING : CloudStatus.OFFLINE;
    }

    @Override
    public double energy() {
        var grid = grid();
        return grid == null ? 0 : grid.getEnergyService().getStoredPower();
    }

    @Override
    public double maxEnergy() {
        var grid = grid();
        return grid == null ? 0 : grid.getEnergyService().getMaxStoredPower();
    }

    @Override
    public double energyUsage() {
        var grid = grid();
        return grid == null ? 0 : grid.getEnergyService().getAvgPowerUsage();
    }

    @Override
    public String energyUnit() {
        return "AE";
    }

    @Override
    public Totals totals() {
        var grid = grid();
        if (grid == null) return new Totals(0, null, 0, null, 0, null);
        long items = 0, types = 0, fluids = 0;
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (entry.getKey() instanceof AEItemKey) {
                items += entry.getLongValue();
                types++;
            } else if (entry.getKey() instanceof AEFluidKey) {
                fluids += entry.getLongValue();
            }
        }
        // Capacity of the basic cells in drives and ME chests (approximate: ignores the per-type byte overhead).
        long itemBytes = 0, itemTypes = 0, fluidBytes = 0;
        boolean anyCell = false;
        for (var machineClass : grid.getMachineClasses()) {
            if (!IChestOrDrive.class.isAssignableFrom(machineClass)) continue;
            for (var machine : grid.getMachines(machineClass)) {
                var drive = (IChestOrDrive) machine;
                for (int i = 0; i < drive.getCellCount(); i++) {
                    var item = drive.getCellItem(i);
                    if (!(item instanceof IBasicCellItem cell)) continue;
                    var stack = new ItemStack(item);
                    anyCell = true;
                    if (cell.getKeyType() == AEKeyType.items()) {
                        itemBytes += cell.getBytes(stack);
                        itemTypes += cell.getTotalTypes(stack);
                    } else if (cell.getKeyType() == AEKeyType.fluids()) {
                        fluidBytes += cell.getBytes(stack);
                    }
                }
            }
        }
        Long itemsTotal = anyCell ? itemBytes * AEKeyType.items().getAmountPerByte() : null;
        Long fluidsTotal = anyCell ? fluidBytes * AEKeyType.fluids().getAmountPerByte() : null;
        return new Totals(items, itemsTotal, types, anyCell ? itemTypes : null, fluids, fluidsTotal);
    }

    @Override
    public void forEachItem(ItemSink sink) {
        var grid = grid();
        if (grid == null) return;
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (entry.getKey() instanceof AEItemKey key) sink.accept(key.getItem(), entry.getLongValue());
        }
    }

    @Override
    public void forEachCraftable(Consumer<Item> sink) {
        var grid = grid();
        if (grid == null) return;
        for (var key : grid.getCraftingService().getCraftables(AEItemKey.filter())) {
            if (key instanceof AEItemKey itemKey) sink.accept(itemKey.getItem());
        }
    }

    @Override
    public void forEachFluid(FluidSink sink) {
        var grid = grid();
        if (grid == null) return;
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (entry.getKey() instanceof AEFluidKey key) sink.accept(key.getId().toString(), key.getDisplayName().getString(), entry.getLongValue());
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
        var grid = grid();
        return grid != null && grid.getCraftingService().isCraftable(AEItemKey.of(item));
    }

    @Override
    public void startCraft(Item item, long count, String user, Consumer<CraftOutcome> done) {
        var grid = grid();
        if (grid == null || be.getLevel() == null) {
            done.accept(CraftOutcome.failed("offline"));
            return;
        }
        var key = AEItemKey.of(item);
        IActionSource source = IActionSource.ofMachine(be);
        var future = grid.getCraftingService().beginCraftingCalculation(be.getLevel(), () -> source, key, count, CalculationStrategy.REPORT_MISSING_ITEMS);
        pending.add(new Pending(future, key, count, done, tick));
    }

    @Override
    public void preview(Item item, long count, Consumer<Plan> done) {
        var grid = grid();
        if (grid == null || be.getLevel() == null) {
            done.accept(Plan.failed("offline"));
            return;
        }
        IActionSource source = IActionSource.ofMachine(be);
        var future = grid.getCraftingService().beginCraftingCalculation(be.getLevel(), () -> source, AEItemKey.of(item), count,
            CalculationStrategy.REPORT_MISSING_ITEMS);
        previews.add(new PendingPreview(future, done, tick));
    }

    /** Turns an AE2 plan into the neutral plan: used (from storage), crafted (pattern outputs × times), missing. */
    private Plan toPlan(ICraftingPlan plan) {
        var entries = new java.util.LinkedHashMap<Item, long[]>();
        for (var e : plan.usedItems()) {
            if (e.getKey() instanceof AEItemKey key) entries.computeIfAbsent(key.getItem(), k -> new long[3])[0] += e.getLongValue();
        }
        for (var e : plan.patternTimes().entrySet()) {
            for (var output : e.getKey().getOutputs()) {
                if (output.what() instanceof AEItemKey key) entries.computeIfAbsent(key.getItem(), k -> new long[3])[1] += output.amount() * e.getValue();
            }
        }
        for (var e : plan.missingItems()) {
            if (e.getKey() instanceof AEItemKey key) entries.computeIfAbsent(key.getItem(), k -> new long[3])[2] += e.getLongValue();
        }
        var list = new ArrayList<PlanEntry>();
        for (var e : entries.entrySet()) list.add(new PlanEntry(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]));
        int idle = 0;
        var grid = grid();
        if (grid != null) {
            for (var cpu : grid.getCraftingService().getCpus()) if (!cpu.isBusy() && cpu.getAvailableStorage() >= plan.bytes()) idle++;
        }
        return new Plan(null, plan.simulation() || !plan.missingItems().isEmpty(), list, plan.bytes(), idle);
    }

    @Override
    public List<Cpu> cpus() {
        var grid = grid();
        var list = new ArrayList<Cpu>();
        if (grid == null) return list;
        int i = 0;
        for (var cpu : grid.getCraftingService().getCpus()) {
            i++;
            var status = cpu.isBusy() ? cpu.getJobStatus() : null;
            Item item = status != null && status.crafting().what() instanceof AEItemKey key ? key.getItem() : null;
            String name = cpu.getName() != null ? cpu.getName().getString() : "CPU " + i;
            list.add(new Cpu("cpu-" + i, name, cpu.getCoProcessors(), cpu.getAvailableStorage(), cpu.isBusy(), item,
                status == null ? 0 : status.crafting().amount(),
                status == null || status.totalItems() <= 0 ? 0 : (double) status.progress() / status.totalItems(),
                status == null ? 0 : status.elapsedTimeNanos() / 1_000_000));
        }
        return list;
    }

    @Override
    public boolean cancelNetworkJob(String id) {
        var grid = grid();
        if (grid == null || !id.startsWith("cpu-")) return false;
        int index;
        try {
            index = Integer.parseInt(id.substring(4));
        } catch (NumberFormatException e) {
            return false;
        }
        int i = 0;
        for (var cpu : grid.getCraftingService().getCpus()) {
            if (++i != index) continue;
            if (!cpu.isBusy()) return false;
            cpu.cancelJob();
            return true;
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
                    p.future().cancel(true);
                    it.remove();
                    p.done().accept(Plan.failed("timeout"));
                }
                continue;
            }
            it.remove();
            try {
                var plan = p.future().get();
                p.done().accept(plan == null ? Plan.failed("not_craftable") : toPlan(plan));
            } catch (Exception e) {
                p.done().accept(Plan.failed("not_craftable"));
            }
        }
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            var p = it.next();
            if (!p.future().isDone()) {
                if (tick - p.started() > CALC_TIMEOUT_TICKS) {
                    p.future().cancel(true);
                    it.remove();
                    p.done().accept(CraftOutcome.failed("timeout"));
                }
                continue;
            }
            it.remove();
            p.done().accept(submit(p));
        }
    }

    private CraftOutcome submit(Pending p) {
        ICraftingPlan plan;
        try {
            plan = p.future().get();
        } catch (Exception e) {
            CCCloud.LOGGER.warn("[cccloud] AE2 crafting calculation failed", e);
            return CraftOutcome.failed("missing_resources");
        }
        if (plan == null || plan.simulation()) return CraftOutcome.failed("missing_resources");
        var grid = grid();
        if (grid == null) return CraftOutcome.failed("offline");
        var cs = grid.getCraftingService();
        var busyBefore = new HashSet<ICraftingCPU>();
        for (var cpu : cs.getCpus()) if (cpu.isBusy()) busyBefore.add(cpu);
        var result = cs.submitJob(plan, null, null, false, IActionSource.ofMachine(be));
        if (!result.successful()) {
            var code = result.errorCode();
            return CraftOutcome.failed(code == CraftingSubmitErrorCode.MISSING_INGREDIENT || code == CraftingSubmitErrorCode.INCOMPLETE_PLAN
                ? "missing_resources" : "no_cpu");
        }
        ICraftingCPU cpu = null;
        for (var candidate : cs.getCpus()) {
            if (!candidate.isBusy() || busyBefore.contains(candidate)) continue;
            var status = candidate.getJobStatus();
            if (status != null && status.crafting().what().equals(p.key())) {
                cpu = candidate;
                break;
            }
        }
        return CraftOutcome.started(new Handle(p.key(), p.count(), cpu, result.link()));
    }

    @Override
    public JobState jobState(Object handle) {
        var h = (Handle) handle;
        if (h.link() != null) {
            if (h.link().isCanceled()) return new JobState("cancelled", 0);
            if (h.link().isDone()) return new JobState("done", 1);
        }
        if (h.cpu() != null && h.cpu().isBusy()) {
            var status = h.cpu().getJobStatus();
            if (status != null && status.crafting().what().equals(h.key())) {
                return new JobState("running", status.totalItems() <= 0 ? 0 : (double) status.progress() / status.totalItems());
            }
        }
        return new JobState("done", 1);
    }

    @Override
    public boolean cancel(Object handle) {
        var h = (Handle) handle;
        if (h.link() != null) {
            h.link().cancel();
            return true;
        }
        if (h.cpu() != null && h.cpu().isBusy()) {
            var status = h.cpu().getJobStatus();
            if (status != null && status.crafting().what().equals(h.key())) {
                h.cpu().cancelJob();
                return true;
            }
        }
        return false;
    }

    @Override
    public List<NetworkJob> networkJobs(List<Object> ownHandles) {
        var grid = grid();
        var list = new ArrayList<NetworkJob>();
        if (grid == null) return list;
        var own = new HashSet<ICraftingCPU>();
        for (var h : ownHandles) if (h instanceof Handle handle && handle.cpu() != null) own.add(handle.cpu());
        int i = 0;
        for (var cpu : grid.getCraftingService().getCpus()) {
            i++;
            if (!cpu.isBusy() || own.contains(cpu)) continue;
            var status = cpu.getJobStatus();
            if (status == null || !(status.crafting().what() instanceof AEItemKey key)) continue;
            double progress = status.totalItems() <= 0 ? 0 : (double) status.progress() / status.totalItems();
            list.add(new NetworkJob("cpu-" + i, key.getItem(), status.crafting().amount(), progress));
        }
        return list;
    }
}
