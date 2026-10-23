package ai.ezfox.cccloud.peripheral;

import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.relay.CloudRelayBlockEntity;
import ai.ezfox.cccloud.relay.RelayStatus;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jspecify.annotations.Nullable;

/**
 * The {@code cloud_relay} peripheral: a small read-only view of a relay. It also identifies the relay on CC wired
 * networks (docs/SPEC.md "Cables"), so computers on the same cable see it, like the mainframe.
 */
public final class RelayPeripheral implements IPeripheral {
    private final CloudRelayBlockEntity relay;

    public RelayPeripheral(CloudRelayBlockEntity relay) {
        this.relay = relay;
    }

    @Override
    public String getType() {
        return "cloud_relay";
    }

    @Override
    public Object getTarget() {
        return relay;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof RelayPeripheral o && o.relay == relay;
    }

    /** "relay", "advanced_relay" or "ender_relay". */
    @LuaFunction
    public final String getRelayType() {
        return relay.type().nodeType.id;
    }

    /** "online", "offline", "connecting" or "no_power". */
    @LuaFunction(mainThread = true)
    public final String getStatus() {
        return relay.getBlockState().getValue(RelayStatus.PROPERTY).getSerializedName();
    }

    /** Whether the relay carries traffic of a mainframe's network right now. */
    @LuaFunction(mainThread = true)
    public final boolean isServing() {
        return NetworkService.isServing(relay.relayId());
    }

    @LuaFunction(mainThread = true)
    public final long getEnergy() {
        return relay.energy().energy();
    }

    @LuaFunction(mainThread = true)
    public final long getEnergyCapacity() {
        return relay.energy().capacity();
    }
}
