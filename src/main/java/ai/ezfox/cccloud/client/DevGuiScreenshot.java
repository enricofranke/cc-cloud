package ai.ezfox.cccloud.client;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.WebAuthData;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev only ({@code ./gradlew runClient -PguiScreenshot=<dir>}): joins {@code run/saves/cccloud-bench}, places a
 * mainframe next to the player, opens its screen, pairs a browser over HTTP ({@code POST /api/pair} against the dev web
 * port) while the screen is open, and saves screenshots of the screen with the pending approval, then quits. Checks the
 * live approval UI end to end in a real client.
 */
public final class DevGuiScreenshot {
    private static String dir = "";
    private static int phase;
    private static int frames;
    private static BlockPos lower = BlockPos.ZERO;
    private static volatile boolean paired;

    private DevGuiScreenshot() {
    }

    public static void init() {
        dir = System.getProperty("cccloud.guiScreenshot", "");
        if (dir.isBlank()) return;
        new File(dir, Screenshot.SCREENSHOT_DIR).mkdirs(); // Screenshot.grab writes into <dir>/screenshots
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, e -> frame(Minecraft.getInstance()));
    }

    private static void frame(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false; // the window may not have focus during the scripted run
        frames++;
        var server = mc.getSingleplayerServer();
        switch (phase) {
            case 0 -> {
                if (frames < 60) return;
                phase = 1;
                var playerId = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) return;
                    var level = player.level();
                    lower = player.blockPosition().relative(player.getDirection(), 3);
                    var state = ModRegistry.CLOUD_MAINFRAME.get().defaultBlockState().setValue(CloudMainframeBlock.FACING, player.getDirection().getOpposite());
                    level.setBlock(lower, state.setValue(CloudMainframeBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
                    level.setBlock(lower.above(), state.setValue(CloudMainframeBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
                    if (level.getBlockEntity(lower) instanceof CloudMainframeBlockEntity mainframe) {
                        mainframe.setOwner(player);
                        mainframe.energy().set(mainframe.energy().capacity());
                        ai.ezfox.cccloud.gui.GuiNetwork.openMainframe(player, mainframe);
                    }
                });
                frames = 0;
            }
            case 1 -> {
                if (frames < 80) return;
                phase = 2;
                var playerId = mc.player.getUUID();
                // Pair over HTTP like a browser would, while the screen is open.
                server.execute(() -> {
                    if (!(server.overworld().getBlockEntity(lower) instanceof CloudMainframeBlockEntity mainframe)) {
                        CCCloud.LOGGER.error("[cccloud] GUI screenshot: mainframe missing");
                        return;
                    }
                    String code = AuthStore.formatCode(WebAuthData.get(server).currentCode(mainframe.getMainframeId(), playerId).code());
                    int port = Integer.getInteger("cccloud.devWebPort", 18787);
                    Thread.ofVirtual().start(() -> {
                        try {
                            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/pair"))
                                .timeout(Duration.ofSeconds(5))
                                .header("Content-Type", "application/json")
                                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Mobile")
                                .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"" + code + "\",\"name\":\"Scripted Phone\"}"))
                                .build();
                            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                            CCCloud.LOGGER.info("[cccloud] GUI screenshot: POST /api/pair -> {} {}", response.statusCode(), response.body());
                            paired = true;
                        } catch (Exception e) {
                            CCCloud.LOGGER.error("[cccloud] GUI screenshot: pairing request failed", e);
                        }
                    });
                });
                frames = 0;
            }
            case 2 -> {
                if (!paired) frames = 0;
                if (frames < 60) return;
                phase = 3;
                CCCloud.LOGGER.info("[cccloud] GUI screenshot: screen = {}", mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
                Screenshot.grab(new File(dir), "cccloud_pending_approval.png", mc.getMainRenderTarget(), 1,
                    msg -> CCCloud.LOGGER.info("[cccloud] GUI screenshot: {}", msg.getString()));
                frames = 0;
            }
            case 3 -> {
                if (frames < 20) return;
                phase = 4;
                // Close the screen: the request is re-sent in chat (bonus fix); a last shot shows the chat.
                mc.player.closeContainer();
                frames = 0;
            }
            case 4 -> {
                if (frames < 40) return;
                phase = 5;
                Screenshot.grab(new File(dir), "cccloud_chat_after_close.png", mc.getMainRenderTarget(), 1,
                    msg -> CCCloud.LOGGER.info("[cccloud] GUI screenshot: {}", msg.getString()));
                frames = 0;
            }
            case 5 -> {
                if (frames < 20) return;
                phase = 6;
                mc.stop();
            }
            default -> {
            }
        }
    }
}
