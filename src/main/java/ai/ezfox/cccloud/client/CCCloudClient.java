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
    public CCCloudClient(IEventBus modEventBus) {
        modEventBus.addListener(CloudModels::register);
        modEventBus.addListener(CCCloudClient::registerRenderers);
        modEventBus.addListener(CCCloudClient::registerParticles);
    }

    private static void registerParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModRegistry.DATA_PACKET.get(), DataPacketParticle.Provider::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.MAINFRAME_BE.get(), MainframeRenderer::new);
        event.registerBlockEntityRenderer(ModRegistry.UPLINK_BE.get(), UplinkRenderer::new);
    }
}
