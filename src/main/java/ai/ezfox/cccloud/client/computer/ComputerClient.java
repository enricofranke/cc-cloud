package ai.ezfox.cccloud.client.computer;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.computer.block.ComputerBlocks;
import ai.ezfox.cccloud.computer.block.ComputerMenu;
import ai.ezfox.cccloud.computer.block.ComputerScreens;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client side of the Cloud Computer: its screen and the live terminal picture. */
@EventBusSubscriber(modid = CCCloud.MODID, value = Dist.CLIENT)
public final class ComputerClient {
    private ComputerClient() {
    }

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        ComputerScreens.clientViewHandler = payload -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof ComputerMenu menu && menu.containerId == payload.containerId()) menu.applyView(payload.json());
        };
    }

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(ComputerBlocks.COMPUTER_MENU.get(), ComputerScreen::new);
    }
}
