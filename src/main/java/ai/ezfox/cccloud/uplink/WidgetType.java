package ai.ezfox.cccloud.uplink;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public enum WidgetType {
    BUTTON(true),
    TOGGLE(true),
    SLIDER(true),
    GAUGE(false),
    LABEL(false),
    // v0.2
    CHART(false),
    PROGRESS(false),
    INDICATOR(false),
    SELECT(true),
    NUMBER(true),
    TEXT(true),
    BUTTONS(true),
    /** Bound to a storage bridge: query → query_result, action {op:"craft"}. */
    ITEM_SEARCH(true),
    /** Bound to a storage bridge: value = rows, actions {op:"add"|"remove"|"set_min"}. */
    WATCHLIST(true),
    /** Bound to a storage bridge: value = crafting job rows, read-only. */
    JOBS(false);

    private final boolean interactive;

    WidgetType(boolean interactive) {
        this.interactive = interactive;
    }

    public boolean isInteractive() {
        return interactive;
    }

    /** Widgets whose data comes from a storage bridge instead of {@code set()}. */
    public boolean isBridgeBound() {
        return this == ITEM_SEARCH || this == WATCHLIST || this == JOBS;
    }

    /** Interactive widgets whose actions are structured operations ({@code {"op":...}}) rather than scalars. */
    public boolean takesOps() {
        return this == ITEM_SEARCH || this == WATCHLIST;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable WidgetType byId(String id) {
        for (var type : values()) if (type.id().equals(id)) return type;
        return null;
    }
}
