package ai.ezfox.cccloud.client;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.client.display.DisplayRenderer;
import ai.ezfox.cccloud.client.gui.DisplayScreen;
import ai.ezfox.cccloud.client.display.DisplayTextures;
import ai.ezfox.cccloud.client.gui.TabletScreen;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import ai.ezfox.cccloud.ingame.InGameRegistry;
import ai.ezfox.cccloud.tablet.TabletMenu;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client side of the v0.7 in-game clients: the tablet and display screens, the display renderer, the "open tablet"
 * key (unbound by default) and the tablet data handler.
 */
@EventBusSubscriber(modid = CCCloud.MODID, value = Dist.CLIENT)
public final class InGameClient {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(CCCloud.MODID, "keys"));
    public static final KeyMapping OPEN_TABLET = new KeyMapping("key.cccloud.open_tablet", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping ANALYZER_MODE = new KeyMapping("key.cccloud.analyzer_mode", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static int ticks;

    private InGameClient() {
    }

    @SubscribeEvent
    static void onRenderLevel(net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterWeather event) {
        ai.ezfox.cccloud.client.analyzer.AnalyzerOverlay.projectLabel(event);
    }

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        ai.ezfox.cccloud.client.dev.DemoCapture.init();
        InGamePayloads.clientAnalyzerHandler = payload -> ai.ezfox.cccloud.client.analyzer.AnalyzerOverlay.accept(payload.json());
        InGamePayloads.clientTabletHandler = payload -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof TabletMenu menu && menu.containerId == payload.containerId()) {
                menu.apply(payload.messages());
            }
        };
    }

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(InGameRegistry.TABLET_MENU.get(), TabletScreen::new);
        event.register(InGameRegistry.DISPLAY_MENU.get(), DisplayScreen::new);
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(InGameRegistry.DISPLAY_BE.get(), DisplayRenderer::new);
    }

    @SubscribeEvent
    static void keys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_TABLET);
        event.register(ANALYZER_MODE);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        while (OPEN_TABLET.consumeClick()) {
            if (mc.player != null && mc.screen == null) ClientPacketDistributor.sendToServer(new InGamePayloads.OpenTablet());
        }
        while (ANALYZER_MODE.consumeClick()) ai.ezfox.cccloud.client.analyzer.AnalyzerOverlay.cycleMode();
        ai.ezfox.cccloud.client.analyzer.AnalyzerOverlay.tick();
        if (++ticks % 20 == 0) DisplayTextures.cleanup();
    }

    @SubscribeEvent
    static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DisplayTextures.clear();
        ai.ezfox.cccloud.client.analyzer.AnalyzerOverlay.clear();
    }

    @SubscribeEvent
    static void hud(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR, Identifier.fromNamespaceAndPath(CCCloud.MODID, "analyzer"),
            ai.ezfox.cccloud.client.analyzer.AnalyzerOverlay::renderHud);
    }

    /** Sneak + scroll while holding the analyzer cycles the bubbles (rings, spheres, off). */
    @SubscribeEvent
    static void scroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isShiftKeyDown() || Minecraft.getInstance().screen != null) return;
        if (!(player.getMainHandItem().getItem() instanceof ai.ezfox.cccloud.analyzer.CloudAnalyzerItem)) return;
        ai.ezfox.cccloud.client.analyzer.AnalyzerOverlay.cycleMode();
        event.setCanceled(true);
    }
}
