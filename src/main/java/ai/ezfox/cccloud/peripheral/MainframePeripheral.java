package ai.ezfox.cccloud.peripheral;

import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.WebAuthData;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** The {@code cloud_mainframe} peripheral. */
public final class MainframePeripheral implements IPeripheral {
    private final CloudMainframeBlockEntity mainframe;

    public MainframePeripheral(CloudMainframeBlockEntity mainframe) {
        this.mainframe = mainframe;
    }

    @Override
    public String getType() {
        return "cloud_mainframe";
    }

    @Override
    public Object getTarget() {
        return mainframe;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof MainframePeripheral o && o.mainframe == mainframe;
    }

    @LuaFunction
    public final boolean isEnabled() {
        return mainframe.isEnabled();
    }

    /** True if the mainframe is loaded, not redstone-killed and the dashboard is enabled. */
    @LuaFunction
    public final boolean isOnline() {
        return mainframe.isOnline() && mainframe.isEnabled();
    }

    /** Stored FE in the mainframe's buffer. */
    @LuaFunction
    public final long getEnergy() {
        return mainframe.energy().energy();
    }

    /** Buffer size in FE. */
    @LuaFunction
    public final long getEnergyCapacity() {
        return mainframe.energy().capacity();
    }

    /** Current consumption in FE per tick (base + linked uplinks/bridges + connected dashboards). */
    @LuaFunction
    public final long getEnergyUsage() {
        return mainframe.energyUsage();
    }

    /**
     * The network: {nodes={{id,type,name,pos={x,y,z},dim,status,power,range,deviceRange,parent,hops},...},
     * links={{a,b,signal,dist,kind},...}, routes={[deviceId]={nodeIds...}}}. Positions included (in-world access).
     */
    @LuaFunction(mainThread = true)
    public final Object getNetwork() {
        var result = ai.ezfox.cccloud.network.NetworkService.result(mainframe.getMainframeId());
        if (result == null) return java.util.Map.of();
        return ai.ezfox.cccloud.uplink.Json.toLua(ai.ezfox.cccloud.network.NetworkService.toJson(mainframe, result, true));
    }

    /** What the network costs in FE/t: {base, uplinks, advanced_uplinks, bridges, clients, hops, total}. */
    @LuaFunction(mainThread = true)
    public final java.util.Map<String, Long> getPowerBreakdown() {
        return mainframe.powerBreakdown();
    }

    @LuaFunction(mainThread = true)
    public final void setEnabled(boolean enabled) {
        mainframe.setEnabled(enabled);
    }

    /** List of linked uplinks: {id, title, online, x, y, z, dimension}. */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getUplinks() {
        var result = new ArrayList<Map<String, Object>>();
        for (var entry : mainframe.getLinkedUplinks().entrySet()) {
            var info = new HashMap<String, Object>();
            info.put("id", entry.getKey().toString());
            var pos = entry.getValue();
            info.put("x", pos.pos().getX());
            info.put("y", pos.pos().getY());
            info.put("z", pos.pos().getZ());
            info.put("dimension", pos.dimension().identifier().toString());
            var uplink = CloudNetwork.uplink(entry.getKey());
            info.put("loaded", uplink != null);
            info.put("online", uplink != null && uplink.isOnline());
            if (uplink != null) info.put("title", uplink.getTitle());
            result.add(info);
        }
        return result;
    }
}
