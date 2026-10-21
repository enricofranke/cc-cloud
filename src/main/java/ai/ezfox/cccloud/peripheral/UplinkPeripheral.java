package ai.ezfox.cccloud.peripheral;

import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetFactory.WidgetException;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.CloudConfig;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import ai.ezfox.cccloud.bridge.BridgeCore;
import ai.ezfox.cccloud.bridge.BridgePeripheral;
import ai.ezfox.cccloud.uplink.BridgeRef;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The {@code cloud_uplink} peripheral. All methods are thread-safe and do not need the main thread: they validate the
 * arguments, update the widget model of the block entity and mark it dirty. The server tick pushes the changes to the
 * dashboard (coalesced, max. 10 updates per second).
 */
public final class UplinkPeripheral implements IPeripheral {
    private final CloudUplinkBlockEntity uplink;

    public UplinkPeripheral(CloudUplinkBlockEntity uplink) {
        this.uplink = uplink;
    }

    @Override
    public String getType() {
        return "cloud_uplink";
    }

    @Override
    public void attach(IComputerAccess computer) {
        uplink.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        uplink.detach(computer);
    }

    @Override
    public Object getTarget() {
        return uplink;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof UplinkPeripheral o && o.uplink == uplink;
    }

    /** Sets the section title shown on the dashboard (max. 48 characters). */
    @LuaFunction
    public final void setTitle(IArguments args) throws LuaException {
        checkEditable();
        uplink.setTitle(wrap(() -> WidgetFactory.checkText("title", args.get(0), Limits.TITLE)));
    }

    @LuaFunction
    public final void button(IArguments args) throws LuaException {
        add(WidgetType.BUTTON, args);
    }

    @LuaFunction
    public final void toggle(IArguments args) throws LuaException {
        add(WidgetType.TOGGLE, args);
    }

    @LuaFunction
    public final void slider(IArguments args) throws LuaException {
        add(WidgetType.SLIDER, args);
    }

    @LuaFunction
    public final void gauge(IArguments args) throws LuaException {
        add(WidgetType.GAUGE, args);
    }

    /** {@code label(id, text, [opts])} – a read-only text line. */
    @LuaFunction
    public final void label(IArguments args) throws LuaException {
        checkEditable();
        String id = wrap(() -> WidgetFactory.checkId(args.get(0)));
        Map<?, ?> opts = opts(args, 2);
        put(wrap(() -> WidgetFactory.create(WidgetType.LABEL, id, args.get(1), opts)));
    }

    // ---- Lua API v2: layout ------------------------------------------------------------------------------------------

    /** {@code page(id, title, [{icon=}])} – declares a dashboard page (tab). Raises the widget limit to 64. */
    @LuaFunction
    public final void page(IArguments args) throws LuaException {
        checkEditable();
        var page = wrap(() -> WidgetFactory.page(args.get(0), args.get(1), opts(args, 2)));
        if (!uplink.putPage(page)) throw new LuaException("too many pages (max " + ai.ezfox.cccloud.uplink.Limits.MAX_PAGES + " per uplink)");
    }

    /** {@code group(id, title, [{page=, cols=1..4}])} – a card that widgets join with opts.group. */
    @LuaFunction
    public final void group(IArguments args) throws LuaException {
        checkEditable();
        var group = wrap(() -> WidgetFactory.group(args.get(0), args.get(1), opts(args, 2)));
        if (!uplink.putGroup(group)) throw new LuaException("too many groups (max " + ai.ezfox.cccloud.uplink.Limits.MAX_GROUPS + " per uplink)");
    }

    // ---- Lua API v2: widgets -----------------------------------------------------------------------------------------

    /** {@code chart(id, label, {min, max, unit, points=60, interval=1, style="line"|"sparkline"})} */
    @LuaFunction
    public final void chart(IArguments args) throws LuaException {
        add(WidgetType.CHART, args);
    }

    @LuaFunction
    public final void progress(IArguments args) throws LuaException {
        add(WidgetType.PROGRESS, args);
    }

    /** {@code indicator(id, label, {value=false, onColor, offColor})} – a read-only LED. */
    @LuaFunction
    public final void indicator(IArguments args) throws LuaException {
        add(WidgetType.INDICATOR, args);
    }

    /** {@code select(id, label, {options={...}, value})} */
    @LuaFunction
    public final void select(IArguments args) throws LuaException {
        add(WidgetType.SELECT, args);
    }

    /** {@code number(id, label, {min, max, step, value})} – a validated number input. */
    @LuaFunction
    public final void number(IArguments args) throws LuaException {
        add(WidgetType.NUMBER, args);
    }

    /** {@code text(id, label, {maxLength=64, value})} – a plain text input. */
    @LuaFunction
    public final void text(IArguments args) throws LuaException {
        add(WidgetType.TEXT, args);
    }

    /** {@code buttons(id, label, {options={"A","B"}})} – a segmented button group; the event value is the option. */
    @LuaFunction
    public final void buttons(IArguments args) throws LuaException {
        add(WidgetType.BUTTONS, args);
    }

    /** {@code itemSearch(id, label, {bridge="me_cloud_bridge_0", max=4096})} – search + craft on a Cloud Bridge. */
    @LuaFunction
    public final void itemSearch(IComputerAccess computer, IArguments args) throws LuaException {
        addBridgeWidget(WidgetType.ITEM_SEARCH, computer, args);
    }

    /** {@code watchlist(id, label, {bridge=...})} – the bridge's watchlist (items with a minimum amount). */
    @LuaFunction
    public final void watchlist(IComputerAccess computer, IArguments args) throws LuaException {
        addBridgeWidget(WidgetType.WATCHLIST, computer, args);
    }

    /** {@code jobs(id, label, {bridge=...})} – recent crafting jobs of the bridge. */
    @LuaFunction
    public final void jobs(IComputerAccess computer, IArguments args) throws LuaException {
        addBridgeWidget(WidgetType.JOBS, computer, args);
    }

    /** Updates the value of a widget. Cheap: only marks the value dirty (charts sample it every opts.interval). */
    @LuaFunction
    public final void set(IArguments args) throws LuaException {
        String id = wrap(() -> WidgetFactory.checkId(args.get(0)));
        var widget = uplink.getWidget(id);
        if (widget == null) throw new LuaException("no widget with id '" + id + "'");
        uplink.setValue(id, wrap(() -> WidgetFactory.valueFor(widget, args.get(1))));
    }

    /** {@code setMany({id=value, ...})} – validates every entry first, then applies all of them. */
    @LuaFunction
    public final void setMany(IArguments args) throws LuaException {
        var table = args.getTable(0);
        if (table.size() > ai.ezfox.cccloud.uplink.Limits.SET_MANY) {
            throw new LuaException("too many values (max " + ai.ezfox.cccloud.uplink.Limits.SET_MANY + " per setMany call)");
        }
        var values = new LinkedHashMap<String, JsonElement>();
        for (var entry : table.entrySet()) {
            String id = wrap(() -> WidgetFactory.checkId(entry.getKey()));
            var widget = uplink.getWidget(id);
            if (widget == null) throw new LuaException("no widget with id '" + id + "'");
            values.put(id, wrap(() -> WidgetFactory.valueFor(widget, entry.getValue())));
        }
        uplink.setValues(values);
    }

    @LuaFunction
    public final boolean remove(IArguments args) throws LuaException {
        checkEditable();
        return uplink.removeWidget(wrap(() -> WidgetFactory.checkId(args.get(0))));
    }

    /** Removes all widgets, pages and groups. */
    @LuaFunction
    public final void clear() throws LuaException {
        checkEditable();
        uplink.clearWidgets();
    }

    /** Alias of alert(). */
    @LuaFunction
    public final boolean notify(IArguments args) throws LuaException {
        return alert(args);
    }

    /** Number of dashboards currently connected to this uplink's mainframe. */
    @LuaFunction
    public final int getClients() {
        var link = uplink.getLink();
        return link == null ? 0 : ai.ezfox.cccloud.web.WebHub.INSTANCE.clientCount(link.id());
    }

    /**
     * Sends a toast/push to the dashboard. Level is "info" (default), "warn" or "crit". Limited to one alert per second
     * per uplink; returns false if the alert was dropped because of that limit.
     */
    @LuaFunction
    public final boolean alert(IArguments args) throws LuaException {
        String text = wrap(() -> WidgetFactory.checkText("text", args.get(0), Limits.ALERT_TEXT));
        String level = args.optString(1, "info");
        if (!WidgetFactory.ALERT_LEVELS.contains(level)) throw new LuaException("bad alert level '" + level + "' (expected info, warn or crit)");
        return uplink.alert(text, level);
    }

    /** True if the uplink is linked, in range, and its mainframe is online with the dashboard enabled. */
    @LuaFunction
    public final boolean isOnline() {
        return uplink.isOnline();
    }

    /**
     * Seconds since an authorized dashboard client was last connected to this uplink's mainframe: 0 while one is
     * connected, nil if none was connected since the server started (or the uplink is not linked).
     */
    @LuaFunction
    public final @Nullable Long getLastClientSeen() {
        var link = uplink.getLink();
        return link == null ? null : ai.ezfox.cccloud.web.WebHub.INSTANCE.secondsSinceClient(link.id());
    }

    private void add(WidgetType type, IArguments args) throws LuaException {
        checkEditable();
        String id = wrap(() -> WidgetFactory.checkId(args.get(0)));
        Map<?, ?> opts = opts(args, 2);
        var widget = wrap(() -> WidgetFactory.create(type, id, args.get(1), opts, BridgeCore.maxCraftAmount()));
        put(widget);
    }

    /** Bridge widgets are bound to a bridge peripheral the computer can reach (side or wired network name). */
    private void addBridgeWidget(WidgetType type, IComputerAccess computer, IArguments args) throws LuaException {
        checkEditable();
        String id = wrap(() -> WidgetFactory.checkId(args.get(0)));
        Map<?, ?> opts = opts(args, 2);
        if (opts == null || !(opts.get("bridge") instanceof String name) || name.isEmpty() || name.length() > 64) {
            throw new LuaException(type.id() + " needs {bridge=\"<peripheral name>\"}, e.g. peripheral.getName(peripheral.find(\"me_cloud_bridge\"))");
        }
        var peripheral = computer.getAvailablePeripheral(name);
        if (!(peripheral instanceof BridgePeripheral bridge)) throw new LuaException("no Cloud Bridge named '" + name + "'");
        var be = bridge.host().asBlockEntity();
        if (be.getLevel() == null) throw new LuaException("the Cloud Bridge is not loaded");
        var ref = new BridgeRef(be.getLevel().dimension().identifier().toString(), be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ(), name);
        var widget = wrap(() -> WidgetFactory.create(type, id, args.get(1), opts, BridgeCore.maxCraftAmount())).withBridge(ref);
        put(widget);
    }

    private static @Nullable Map<?, ?> opts(IArguments args, int index) throws LuaException {
        return args.count() > index && args.get(index) != null ? args.getTable(index) : null;
    }

    private void checkEditable() throws LuaException {
        if (uplink.isTemplate()) throw new LuaException("this uplink is attached directly to a Cloud Bridge and shows the Storage template");
    }

    private void put(ai.ezfox.cccloud.uplink.Widget widget) throws LuaException {
        if (!uplink.putWidget(widget)) {
            throw new LuaException("too many widgets (max " + uplink.maxWidgets() + " per uplink" + (uplink.maxWidgets() < 64 ? "; declare pages with cloud.page() for up to 64" : "") + ")");
        }
    }

    private interface Action<T> {
        T run() throws WidgetException, LuaException;
    }

    private static <T> T wrap(Action<T> action) throws LuaException {
        try {
            return action.run();
        } catch (WidgetException e) {
            throw new LuaException(e.getMessage());
        }
    }
}
