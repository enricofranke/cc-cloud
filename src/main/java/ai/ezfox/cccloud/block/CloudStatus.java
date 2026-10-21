package ai.ezfox.cccloud.block;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Connection status shown in the {@code status} blockstate property of mainframes and uplinks. */
public enum CloudStatus implements StringRepresentable {
    OFFLINE("offline"),
    CONNECTING("connecting"),
    ONLINE("online");

    public static final EnumProperty<CloudStatus> PROPERTY = EnumProperty.create("status", CloudStatus.class);

    private final String name;

    CloudStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Component displayName() {
        return Component.translatable("cccloud.status." + name);
    }
}
