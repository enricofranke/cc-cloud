package ai.ezfox.cccloud.compat.kubejs;

import ai.ezfox.cccloud.api.CloudEvents;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** {@code CCCloudEvents.deviceLinked(event => ...)}: an uplink or bridge was bound to a mainframe with a Link Card. */
public final class DeviceLinkedKubeEvent implements KubeEvent {
    private final CloudEvents.DeviceLinked linked;

    DeviceLinkedKubeEvent(CloudEvents.DeviceLinked linked) {
        this.linked = linked;
    }

    public String getMainframeId() {
        return linked.mainframeId().toString();
    }

    public String getDeviceId() {
        return linked.deviceId().toString();
    }

    /** "uplink" or "bridge:me" / "bridge:rs". */
    public String getType() {
        return linked.type();
    }

    public String getDimension() {
        return linked.dimension();
    }

    public BlockPos getPos() {
        return linked.pos();
    }

    public String getPlayerName() {
        return linked.playerName();
    }

    public @Nullable ServerPlayer getPlayer() {
        return KubeEvents.player(linked.player());
    }
}
