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
