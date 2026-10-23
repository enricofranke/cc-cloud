package ai.ezfox.cccloud.cable;

import ai.ezfox.cccloud.network.NetworkService;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredElementCapability;
import dan200.computercraft.api.network.wired.WiredNetworkChange;
import dan200.computercraft.api.network.wired.WiredNode;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A cloud block as a member of CC: Tweaked's wired network (docs/SPEC.md "Cables", part A): mainframe, relays, uplinks
 * and bridges expose this {@link WiredElement} on their faces, so CC Networking Cable and Wired Modems connect them.
 * <p>
 * Network membership is found with CC's public API only: nodes (mainframe, relays) put their peripheral on the wired
 * network under a unique name; every element learns from {@link #networkChanged} which cloud node peripherals share its
 * network and marks the {@link NetworkService} dirty. The graph is then recomputed on CC network changes only.
 * Side effect (intended, documented): CC computers on the same cable see these peripherals too.
 */
public final class CloudWired implements WiredElement {
    /** Peripherals of cloud nodes on wired networks → the node id. Identity, because peripherals override equals. */
    private static final Map<IPeripheral, String> NODES = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Elements that (re)connect to their neighbours on the next server tick. */
    private static final Set<CloudWired> PENDING = ConcurrentHashMap.newKeySet();

    private final BlockEntity owner;
    private final Supplier<String> id;
    private final boolean node;
    private final Supplier<@Nullable IPeripheral> peripheral;
    private final Supplier<List<BlockPos>> positions;
    private final WiredNode wiredNode;
    /** Peripheral name on the wired network → cloud node id (a node can be visible under two names, e.g. via a modem). */
    private final Map<String, String> visible = new ConcurrentHashMap<>();
    private @Nullable IPeripheral exposed;
    private volatile boolean attached;

    /**
     * @param id         the element's network id (mainframe id, relay id, uplink id, bridge section id)
     * @param node       mainframe or relay: its peripheral identifies it to the other members
     * @param peripheral put on the wired network (null = nothing, e.g. uplinks)
     * @param positions  the blocks of the element (the mainframe has two)
     */
    public CloudWired(BlockEntity owner, Supplier<String> id, boolean node, Supplier<@Nullable IPeripheral> peripheral,
                      Supplier<List<BlockPos>> positions) {
        this.owner = owner;
        this.id = id;
        this.node = node;
        this.peripheral = peripheral;
        this.positions = positions;
        this.wiredNode = ComputerCraftAPI.createWiredNodeForElement(this);
    }

    public static CloudWired single(BlockEntity owner, Supplier<String> id, boolean node, Supplier<@Nullable IPeripheral> peripheral) {
        return new CloudWired(owner, id, node, peripheral, () -> List.of(owner.getBlockPos()));
    }

    // ---- WiredElement ------------------------------------------------------------------------------------------------

    @Override
    public WiredNode getNode() {
        return wiredNode;
    }

    @Override
    public Level getLevel() {
        return owner.getLevel();
    }

    @Override
    public Vec3 getPosition() {
        return Vec3.atCenterOf(owner.getBlockPos());
    }

    @Override
    public String getSenderID() {
        return "cccloud:" + id.get();
    }

    @Override
    public void networkChanged(WiredNetworkChange change) {
        boolean changed = false;
        for (var name : change.peripheralsRemoved().keySet()) changed |= visible.remove(name) != null;
        for (var e : change.peripheralsAdded().entrySet()) {
            var nodeId = NODES.get(e.getValue());
            if (nodeId == null || nodeId.equals(id.get())) continue;
            visible.put(e.getKey(), nodeId);
            changed = true;
        }
        if (changed) NetworkService.markDirty();
    }

    // ---- lifecycle (server side) -------------------------------------------------------------------------------------

    /** From the block entity's {@code onLoad}: connect to neighbouring wired elements on the next tick. */
    public void onLoad() {
        if (owner.getLevel() == null || owner.getLevel().isClientSide()) return;
        attached = true;
        PENDING.add(this);
        NetworkService.markFiberDirty();
    }

    /** From the block entity's {@code setRemoved}: leave the wired network (CC splits it and notifies the others). */
    public void onRemoved() {
        if (!attached) return;
        attached = false;
        PENDING.remove(this);
        wiredNode.remove();
        var p = exposed;
        if (p != null) NODES.remove(p);
        exposed = null;
        visible.clear();
        NetworkService.markFiberDirty();
    }

    /** Re-checks the neighbours and the exposed peripheral (e.g. after the element's id changed). */
    public void refresh() {
        if (attached) PENDING.add(this);
    }

    /** Server tick: connects pending elements to their neighbours. */
    public static void tick() {
        if (PENDING.isEmpty()) return;
        var batch = new HashSet<>(PENDING);
        PENDING.removeAll(batch);
        for (var element : batch) {
            try {
                element.connect();
            } catch (RuntimeException e) {
                ai.ezfox.cccloud.CCCloud.LOGGER.warn("[cccloud] Could not join the CC wired network at {}", element.owner.getBlockPos(), e);
            }
        }
    }

    public static void clear() {
        PENDING.clear();
        NODES.clear();
    }

    private void connect() {
        var level = owner.getLevel();
        if (!attached || level == null || owner.isRemoved()) return;
        var own = positions.get();
        for (var pos : own) {
            for (var dir : Direction.values()) {
                var other = pos.relative(dir);
                if (own.contains(other) || !level.isLoaded(other)) continue;
                var element = level.getCapability(WiredElementCapability.get(), other, dir.getOpposite());
                if (element == null || element == this) continue;
                wiredNode.connectTo(element.getNode());
            }
        }
        var p = peripheral.get();
        if (p != exposed) {
            if (exposed != null) NODES.remove(exposed);
            exposed = p;
        }
        if (p != null) {
            if (node) NODES.put(p, id.get());
            wiredNode.updatePeripherals(Map.of(peripheralName(p), p));
        } else {
            wiredNode.updatePeripherals(Map.of());
        }
        NetworkService.markDirty();
    }

    private String peripheralName(IPeripheral p) {
        String raw = id.get().replace("-", "");
        return p.getType() + "_" + raw.substring(0, Math.min(8, raw.length()));
    }

    // ---- membership --------------------------------------------------------------------------------------------------

    /** Ids of the cloud nodes (mainframe, relays) on the same CC wired network, without this element. */
    public Set<String> visibleNodes() {
        return Set.copyOf(visible.values());
    }

    /**
     * The id of this element's CC wired network as seen by the graph ({@code cc:<smallest node id>}); every member of
     * one CC network computes the same id. Empty for a device when no cloud node shares its network.
     */
    public Set<String> cableBuses() {
        if (!attached) return Set.of();
        var ids = new TreeSet<>(visible.values());
        // A node is on its own network even if it sees no other node (its members only see it).
        if (node) ids.add(id.get());
        if (ids.isEmpty()) return Set.of();
        return Set.of("cc:" + ids.first());
    }

    public boolean attached() {
        return attached;
    }
}
