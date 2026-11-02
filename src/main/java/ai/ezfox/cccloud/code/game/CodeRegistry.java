package ai.ezfox.cccloud.code.game;

import ai.ezfox.cccloud.CCCloud;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Hooks Cloud Scripts into the server's life cycle and commands (game bus). */
@EventBusSubscriber(modid = CCCloud.MODID)
final class CodeRegistry {
    private CodeRegistry() {
    }

    @SubscribeEvent
    static void started(ServerStartedEvent event) {
        CodeService.started(event.getServer());
    }

    @SubscribeEvent
    static void stopping(ServerStoppingEvent event) {
        CodeService.stopping();
    }

    @SubscribeEvent
    static void stopped(ServerStoppedEvent event) {
        CodeService.stopped();
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        long cost = ai.ezfox.cccloud.network.TickCost.start();
        try {
            CodeService.tick(event.getServer());
        } finally {
            ai.ezfox.cccloud.network.TickCost.end(ai.ezfox.cccloud.network.TickCost.SCRIPTS, cost);
        }
    }

    @SubscribeEvent
    static void commands(RegisterCommandsEvent event) {
        CodeCommands.register(event.getDispatcher());
    }
}
