package ai.ezfox.cccloud.redstone;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Wireless redstone mode of an uplink (docs/SPEC.md "Wireless redstone over the network"). */
public enum RedstoneMode {
    OFF, TRANSMIT, RECEIVE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable RedstoneMode byId(String id) {
        for (var m : values()) if (m.id().equals(id)) return m;
        return null;
    }
}
