package ai.ezfox.cccloud.client.dev;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.display.CloudDisplayBlockEntity;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev only (see {@code DemoScene}): once the demo display shows its card, takes a screenshot of it, opens the tablet,
 * takes a second screenshot, then one of the Cloud Analyzer overlay with a relay in hand, and quits. Files go to {@code <cccloud.demo>/screenshots}.
 */
public final class DemoCapture {
    private static long readyAt;
    private static int step;

    private DemoCapture() {
    }

    public static void init() {
        if (System.getProperty("cccloud.demo") == null) return;
        NeoForge.EVENT_BUS.addListener(DemoCapture::tick);
    }

    private static boolean displayReady(Minecraft mc) {
        var origin = mc.player.blockPosition();
        for (var pos : BlockPos.betweenClosed(origin.offset(-3, -1, 1), origin.offset(3, 3, 6))) {
            if (mc.level.getBlockEntity(pos) instanceof CloudDisplayBlockEntity d && d.isOrigin() && d.content().contains("\"widgets\"")) return true;
        }
        return false;
    }

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // The demo window may be in the background: never pause the integrated server.
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        long now = System.currentTimeMillis();
        var dir = new File(System.getProperty("cccloud.demo"));
        switch (step) {
            case 0 -> {
                if (displayReady(mc)) {
                    readyAt = now;
                    step = 1;
                }
            }
            case 1 -> {
                if (now - readyAt < 8000) return;
                mc.options.tutorialStep = TutorialSteps.NONE;
                mc.getTutorial().setStep(TutorialSteps.NONE);
                mc.gui.getChat().clearMessages(false);
                mc.player.setYRot(0);
                mc.player.setXRot(12);
                mc.options.hideGui = true;
                step = 2;
            }
            case 2 -> {
                if (now - readyAt < 9000) return;
                shot(mc, dir, "cccloud-display.png");
                step = 3;
            }
            case 3 -> {
                if (now - readyAt < 10000) return;
                mc.options.hideGui = false;
                ClientPacketDistributor.sendToServer(new InGamePayloads.OpenTablet());
                step = 4;
            }
            case 4 -> {
                if (now - readyAt < 17000) return;
                shot(mc, dir, "cccloud-tablet.png");
                step = 5;
            }
            case 5 -> {
                if (now - readyAt < 19000) return;
                mc.setScreen(null);
                // Hold the relay (next hotbar slot) for the placement preview; the server puts the analyzer in the off hand.
                mc.player.getInventory().setSelectedSlot((mc.player.getInventory().getSelectedSlot() + 1) % 9);
                mc.player.setYRot(-30);
                mc.player.setXRot(30);
                step = 6;
            }
            case 6 -> {
                if (now - readyAt < 27000) return;
                shot(mc, dir, "cccloud-analyzer.png");
                step = 7;
            }
            case 7 -> {
                if (now - readyAt < 29000) return;
                step = 8;
                mc.stop();
            }
            default -> {
            }
        }
    }

    private static void shot(Minecraft mc, File dir, String name) {
        dir.mkdirs();
        Screenshot.grab(dir, name, mc.getMainRenderTarget(), 1, message -> CCCloud.LOGGER.info("[cccloud] Demo screenshot: {}", message.getString()));
    }
}
