package ai.ezfox.cccloud.relay;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Relay status in the blockstate: like CloudStatus plus {@code no_power} (dark LEDs). */
public enum RelayStatus implements StringRepresentable {
    OFFLINE("offline"),
    CONNECTING("connecting"),
    ONLINE("online"),
    NO_POWER("no_power");

    public static final EnumProperty<RelayStatus> PROPERTY = EnumProperty.create("status", RelayStatus.class);

    private final String name;

    RelayStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Component displayName() {
        return Component.translatable("cccloud.gui.status." + name);
    }
}
