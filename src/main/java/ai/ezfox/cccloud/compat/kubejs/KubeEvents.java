package ai.ezfox.cccloud.compat.kubejs;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

final class KubeEvents {
    private KubeEvents() {
    }

    static @Nullable ServerPlayer player(java.util.@Nullable UUID id) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null || id == null ? null : server.getPlayerList().getPlayer(id);
    }
}
