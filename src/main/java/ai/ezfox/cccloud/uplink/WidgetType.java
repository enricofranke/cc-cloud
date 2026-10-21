package ai.ezfox.cccloud.uplink;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public enum WidgetType {
    BUTTON(true),
    TOGGLE(true),
    SLIDER(true),
    GAUGE(false),
    LABEL(false);

    private final boolean interactive;

    WidgetType(boolean interactive) {
        this.interactive = interactive;
    }

    public boolean isInteractive() {
        return interactive;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable WidgetType byId(String id) {
        for (var type : values()) if (type.id().equals(id)) return type;
        return null;
    }
}
