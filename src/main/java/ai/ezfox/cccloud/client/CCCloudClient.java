package ai.ezfox.cccloud.client;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client entry point: block entity renderers and the standalone models of the moving parts. */
@Mod(value = CCCloud.MODID, dist = Dist.CLIENT)
public class CCCloudClient {
    public CCCloudClient(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        ai.ezfox.cccloud.client.icons.ClientIcons.init(modEventBus, container);
        ai.ezfox.cccloud.client.voxel.ClientBlockTextures.init();
        ai.ezfox.cccloud.client.voxel.DevTextureCheck.init();
        modEventBus.addListener(CloudModels::register);
        DevGuiScreenshot.init();
        ai.ezfox.cccloud.client.dev.ScreenShots.init();
        modEventBus.addListener(CCCloudClient::registerRenderers);
        modEventBus.addListener(CCCloudClient::registerScreens);
        ai.ezfox.cccloud.gui.GuiNetwork.clientViewHandler = payload -> {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof ai.ezfox.cccloud.gui.CloudMenu menu && menu.containerId == payload.containerId()) {
                menu.setView(payload.view());
            }
        };
    }

    private static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(ModRegistry.MAINFRAME_MENU.get(), ai.ezfox.cccloud.client.gui.MainframeScreen::new);
        event.register(ModRegistry.UPLINK_MENU.get(), ai.ezfox.cccloud.client.gui.UplinkScreen::new);
    }


    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.MAINFRAME_BE.get(), MainframeRenderer::new);
        event.registerBlockEntityRenderer(ModRegistry.UPLINK_BE.get(), UplinkRenderer::new);
        event.registerBlockEntityRenderer(ModRegistry.RELAY_BE.get(), RelayRenderer::new);
    }
}
