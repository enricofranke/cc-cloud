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

    /** {@code label(id, text)} – a read-only text line. */
    @LuaFunction
    public final void label(IArguments args) throws LuaException {
        String id = wrap(() -> WidgetFactory.checkId(args.get(0)));
        put(wrap(() -> WidgetFactory.create(WidgetType.LABEL, id, args.get(1), null)));
    }

    /** Updates the value of a gauge, label, toggle or slider. Cheap: only marks the value dirty. */
    @LuaFunction
    public final void set(IArguments args) throws LuaException {
        String id = wrap(() -> WidgetFactory.checkId(args.get(0)));
        var widget = uplink.getWidget(id);
        if (widget == null) throw new LuaException("no widget with id '" + id + "'");
        uplink.setValue(id, wrap(() -> WidgetFactory.valueFor(widget, args.get(1))));
    }

    @LuaFunction
    public final boolean remove(IArguments args) throws LuaException {
        return uplink.removeWidget(wrap(() -> WidgetFactory.checkId(args.get(0))));
    }

    @LuaFunction
    public final void clear() {
        uplink.clearWidgets();
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
        String id = wrap(() -> WidgetFactory.checkId(args.get(0)));
        Map<?, ?> opts = args.count() > 2 && args.get(2) != null ? args.getTable(2) : null;
        var widget = wrap(() -> WidgetFactory.create(type, id, args.get(1), opts));
        put(widget);
    }

    private void put(ai.ezfox.cccloud.uplink.Widget widget) throws LuaException {
        if (!uplink.putWidget(widget)) {
            throw new LuaException("too many widgets (max " + CloudConfig.get(CloudConfig.MAX_WIDGETS_PER_UPLINK) + " per uplink)");
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
