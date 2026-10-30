package ai.ezfox.cccloud.cable;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredElementCapability;
import dan200.computercraft.api.network.wired.WiredNetworkChange;
import dan200.computercraft.api.network.wired.WiredNode;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The CC wired networks a computer is on, as an uplink on that computer sees them (docs/SPEC.md "CC wired networks"):
 * the peripherals the computer reaches through its wired modems, by the names the computer uses
 * ({@code peripheral.getNames()}).
 * <p>
 * Only CC's public API is used: for every wired modem next to the computer, a listener node of ours is connected to
 * the modem's node, and CC reports the network's peripherals to it ({@link WiredElement#networkChanged}), like it does
 * for the modems themselves. The listener puts no peripheral on the network, so computers on it see nothing new. Server
 * thread only.
 */
public final class WiredReach {
    private final Supplier owner;
    /** One listener per modem position the computer uses. */
    private final Map<BlockPos, Listener> listeners = new ConcurrentHashMap<>();

    /** Where the listener nodes say they are (CC uses it for modem message distances; we send nothing). */
    public interface Supplier {
        @Nullable Level level();

        BlockPos pos();

        String id();
    }

    public WiredReach(Supplier owner) {
        this.owner = owner;
    }

    /**
     * Follows the computer's wired modems: listeners for new ones, none for removed ones. Call about once a second.
     *
     * @param computer the computer block, or null when the uplink is not on a computer (everything is released)
     * @param skip     the uplink itself (it is next to the computer, but not the computer's modem)
     */
    public void update(ServerLevel level, @Nullable BlockPos computer, BlockPos skip) {
        var modems = new HashSet<BlockPos>();
        var nodes = new java.util.HashMap<BlockPos, WiredNode>();
        if (computer != null) {
            for (var dir : Direction.values()) {
                var at = computer.relative(dir);
                if (at.equals(skip) || !level.isLoaded(at)) continue;
                // a modem gives the computer a "modem" peripheral on that side and is a member of a wired network
                var peripheral = level.getCapability(PeripheralCapability.get(), at, dir.getOpposite());
                if (peripheral == null || !"modem".equals(peripheral.getType())) continue;
                var element = level.getCapability(WiredElementCapability.get(), at, null);
                if (element == null) continue;
                modems.add(at.immutable());
                nodes.put(at.immutable(), element.getNode());
            }
        }
        for (var e : List.copyOf(listeners.entrySet())) {
            var node = nodes.get(e.getKey());
            if (node == null || e.getValue().modem != node) {
                listeners.remove(e.getKey());
                e.getValue().close();
            }
        }
        for (var pos : modems) {
            if (listeners.containsKey(pos)) continue;
            var listener = new Listener(nodes.get(pos));
            listeners.put(pos, listener);
            listener.open();
        }
    }

    /** A peripheral the computer reaches over its wired networks, by its network name; null if there is none. */
    public @Nullable IPeripheral peripheral(String name) {
        for (var listener : listeners.values()) {
            var p = listener.peripherals.get(name);
            if (p != null) return p;
        }
        return null;
    }

    /** Every peripheral on the computer's wired networks (name → peripheral). */
    public Map<String, IPeripheral> peripherals() {
        if (listeners.isEmpty()) return Map.of();
        var all = new java.util.TreeMap<String, IPeripheral>();
        for (var listener : listeners.values()) all.putAll(listener.peripherals);
        return all;
    }

    /** Positions of the computer's wired modems that are followed (tests, screen). */
    public Set<BlockPos> modems() {
        return Set.copyOf(listeners.keySet());
    }

    /** Leaves every network (the uplink unloads or is removed). */
    public void close() {
        var all = new ArrayList<>(listeners.values());
        listeners.clear();
        for (var listener : all) listener.close();
    }

    /** Our node on one modem's network. */
    private final class Listener implements WiredElement {
        final WiredNode modem;
        final WiredNode node;
        final Map<String, IPeripheral> peripherals = new ConcurrentHashMap<>();

        Listener(WiredNode modem) {
            this.modem = modem;
            this.node = ComputerCraftAPI.createWiredNodeForElement(this);
        }

        void open() {
            node.connectTo(modem);
        }

        void close() {
            node.remove();
            peripherals.clear();
        }

        @Override
        public WiredNode getNode() {
            return node;
        }

        @Override
        public String getSenderID() {
            return "cccloud:reach:" + owner.id();
        }

        @Override
        public Level getLevel() {
            return owner.level();
        }

        @Override
        public Vec3 getPosition() {
            return Vec3.atCenterOf(owner.pos());
        }

        @Override
        public void networkChanged(WiredNetworkChange change) {
            for (var name : change.peripheralsRemoved().keySet()) peripherals.remove(name);
            peripherals.putAll(change.peripheralsAdded());
        }
    }
}
