package ai.ezfox.cccloud.bridge;

import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Limits;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The {@code me_cloud_bridge} / {@code rs_cloud_bridge} peripheral. Every method touches the storage network, so all of
 * them run on the server thread ({@code mainThread = true}, budgeted by CC). Listings are served from a short-lived
 * cached snapshot and limited to 20 per second per bridge.
 */
public final class BridgePeripheral implements IPeripheral {
    public static final int LIST_DEFAULT = 256;
    public static final int LIST_MAX = 1024;
    public static final int FIND_DEFAULT = 50;
    public static final int FIND_MAX = 256;

    private final String type;
    private final BridgeHost host;

    public BridgePeripheral(String type, BridgeHost host) {
        this.type = type;
        this.host = host;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Object getTarget() {
        return host;
    }

    public BridgeHost host() {
        return host;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof BridgePeripheral o && o.host == host;
    }

    private BridgeCore core() {
        return host.bridgeCore();
    }

    /** True if the bridge is part of a powered network. */
    @LuaFunction(mainThread = true)
    public final boolean isConnected() {
        return core().isConnected();
    }

    /** Stored network energy (AE for ME, FE for RS). */
    @LuaFunction(mainThread = true)
    public final double getEnergy() {
        return core().isConnected() ? core().adapter().energy() : 0;
    }

    @LuaFunction(mainThread = true)
    public final double getMaxEnergy() {
        return core().isConnected() ? core().adapter().maxEnergy() : 0;
    }

    /** Average energy usage per tick, nil if the mod does not expose it. */
    @LuaFunction(mainThread = true)
    public final @Nullable Double getEnergyUsage() {
        if (!core().isConnected()) return 0.0;
        double usage = core().adapter().energyUsage();
        return Double.isNaN(usage) ? null : usage;
    }

    /** {itemsUsed, itemsTotal?, typesUsed, typesTotal?, fluidsUsed, fluidsTotal?} – items in items, fluids in mB. */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getStorage() {
        var map = new LinkedHashMap<String, Object>();
        for (var e : core().storage().entrySet()) map.put(e.getKey(), e.getValue().getAsDouble());
        return map;
    }

    /** {@code listItems([filter], [limit=256])} – items sorted by count, filter as in findItems. */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> listItems(IArguments args) throws LuaException {
        String filter = args.optString(0, "");
        int limit = limit(args.optInt(1, LIST_DEFAULT), LIST_MAX);
        return search(filter, limit);
    }

    /** {@code findItems(query, [limit=50])} – case-insensitive match on id and display name; "@modid" filters by mod. */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> findItems(IArguments args) throws LuaException {
        String query = args.getString(0);
        int limit = limit(args.optInt(1, FIND_DEFAULT), FIND_MAX);
        return search(query, limit);
    }

    /** {@code getItem(name)} – {name, displayName, count, craftable} or nil. */
    @LuaFunction(mainThread = true)
    public final @Nullable Map<String, Object> getItem(String name) {
        var row = core().getItem(name);
        return row == null ? null : row.toLua();
    }

    @LuaFunction(mainThread = true)
    public final boolean isCraftable(String name) {
        return core().isCraftable(name);
    }

    /**
     * {@code craft(name, count)} → {ok=true, jobId} or {ok=false, error}. Planning happens in the background; a job that
     * cannot be planned (missing_resources, no_cpu) shows up as failed in getCraftingJobs().
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> craft(String name, long count) {
        var ticket = core().craft(name, count, "computer", false, "lua");
        var map = new LinkedHashMap<String, Object>();
        map.put("ok", ticket.error() == null);
        if (ticket.error() != null) map.put("error", ticket.error());
        if (ticket.jobId() != null) map.put("jobId", ticket.jobId());
        return map;
    }

    /**
     * {@code setAutoStock(name, min, target, enabled)} – level maintainer: keeps at least {@code min}, crafting up to
     * {@code target} (in batches of autoStockBatch). Returns true, or nil and an error.
     */
    @LuaFunction(mainThread = true)
    public final Object[] setAutoStock(String name, long min, long target, boolean enabled) {
        String error = core().setAutoStock(name, min, target, enabled);
        return error == null ? new Object[]{true} : new Object[]{null, error};
    }

    /** Recent jobs, newest first: {jobId, name, displayName, count, status, progress, error?}. */
    @LuaFunction(mainThread = true)
    public final Object getCraftingJobs() {
        return Optional.ofNullable(Json.toLua(core().jobRows(BridgeCore.MAX_JOBS + 16))).orElse(Map.of());
    }

    @LuaFunction(mainThread = true)
    public final boolean cancelCraft(String jobId) {
        return core().cancelCraft(jobId);
    }

    /** {@code listFluids([limit=256])} – {name, displayName, amount (mB)}. */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> listFluids(IArguments args) throws LuaException {
        return core().listFluids(limit(args.optInt(0, LIST_DEFAULT), LIST_MAX));
    }

    private List<Map<String, Object>> search(String query, int limit) throws LuaException {
        if (query.length() > Limits.QUERY) throw new LuaException("filter is too long (max " + Limits.QUERY + " characters)");
        if (!core().trySearch()) throw new LuaException("too many listings on this bridge, slow down (max 20 per second)");
        var page = core().index().search(query, limit);
        var list = new ArrayList<Map<String, Object>>(page.rows().size());
        for (var row : page.rows()) list.add(row.toLua());
        return list;
    }

    private static int limit(int requested, int max) throws LuaException {
        if (requested < 1) throw new LuaException("limit must be at least 1");
        return Math.min(requested, max);
    }
}
