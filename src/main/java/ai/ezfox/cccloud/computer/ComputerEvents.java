package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.CCCloud;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Stops every Cloud Computer when the server stops, before the script threads go (game bus). */
@EventBusSubscriber(modid = CCCloud.MODID)
final class ComputerEvents {
    private ComputerEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void stopping(ServerStoppingEvent event) {
        ComputerRegistry.stop(event.getServer());
    }
}
