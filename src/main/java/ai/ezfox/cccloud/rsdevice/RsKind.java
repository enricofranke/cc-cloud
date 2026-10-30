package ai.ezfox.cccloud.rsdevice;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The four Cloud Redstone devices (docs/SPEC.md "Cloud Redstone devices"). {@code id} is the saved/protocol name,
 * {@code block} the block id, {@code icon} the bundled dashboard icon of their widgets.
 */
public enum RsKind {
    SWITCH("switch", "cloud_switch", "lever", "Switch"),
    BUTTON("button", "cloud_button", "button", "Button"),
    LAMP("lamp", "cloud_lamp", "lamp", "Lamp"),
    SENSOR("sensor", "cloud_sensor", "sensor", "Sensor");

    public final String id;
    public final String block;
    public final String icon;
    /** Default name of an unnamed device ("Lamp 3f2a"). */
    public final String label;

    RsKind(String id, String block, String icon, String label) {
        this.id = id;
        this.block = block;
        this.icon = icon;
        this.label = label;
    }

    /** Switches and lamps can be turned on and off (the card's "All on" / "All off" buttons). */
    public boolean switchable() {
        return this == SWITCH || this == LAMP;
    }

    public static @Nullable RsKind byId(String id) {
        for (var kind : values()) if (kind.id.equals(id.toLowerCase(Locale.ROOT))) return kind;
        return null;
    }
}
