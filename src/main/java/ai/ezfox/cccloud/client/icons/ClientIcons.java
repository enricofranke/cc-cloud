package ai.ezfox.cccloud.client.icons;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.icons.IconService;
import ai.ezfox.cccloud.icons.PngCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * Client part of the item icon store: when the server asks, render the requested items in the background (≤ 2 ms per
 * frame) and upload them. Can be switched off with the client config {@code shareIconRenders}.
 * <p>
 * Dev only: {@code -Dcccloud.iconBenchmark=<dir>} (with {@code ./gradlew runClient -PiconBenchmark=<dir>}) joins a
 * singleplayer world, lets the normal flow render every item, logs time and size, writes {@code <dir>/icons_sample.png}
 * and quits.
 */
public final class ClientIcons {
    private static final long FRAME_BUDGET_NANOS = 2_000_000L;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHARE;

    static {
        var b = new ModConfigSpec.Builder();
        SHARE = b.comment("Render item icons for the CC: Cloud dashboard in the background and send them to the server (≤ 2 ms per frame).")
            .translation("cccloud.configuration.shareIconRenders")
            .define("shareIconRenders", true);
        SPEC = b.build();
    }

    private static @Nullable IconRenderer renderer;
    private static @Nullable Benchmark benchmark;

    private ClientIcons() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        IconService.clientRequestHandler = payload -> renderer().enqueue(payload.ids());
        NeoForge.EVENT_BUS.addListener(ClientIcons::onLogin);
        NeoForge.EVENT_BUS.addListener(ClientIcons::onLogout);
        NeoForge.EVENT_BUS.addListener(ClientIcons::onFrame);
        String dir = System.getProperty("cccloud.iconBenchmark");
        if (dir != null && !dir.isBlank()) benchmark = new Benchmark(Path.of(dir));
    }

    private static IconRenderer renderer() {
        if (renderer == null) renderer = new IconRenderer(ClientIcons::upload);
        return renderer;
    }

    private static boolean sharing() {
        return SPEC.isLoaded() ? SHARE.get() : SHARE.getDefault();
    }

    private static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(IconService.ClientStatePayload.TYPE)) {
            ClientPacketDistributor.sendToServer(new IconService.ClientStatePayload(sharing()));
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
    }

    private static void upload(List<IconRenderer.Result> results) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(IconService.UploadPayload.TYPE)) return;
        for (var payload : IconRenderer.pack(results)) ClientPacketDistributor.sendToServer(payload);
    }

    private static void onFrame(RenderFrameEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (benchmark != null) benchmark.frame(mc);
        var r = renderer;
        if (r == null || !r.busy() || !sharing() || mc.getOverlay() != null) return;
        r.renderFrame(FRAME_BUDGET_NANOS);
    }

    /**
     * Dev-only end-to-end measurement in singleplayer: the integrated server's {@link IconService} hands out batches
     * through the real payloads and this client renders them with the real 2 ms budget. Once the store is complete the
     * timings and sizes are logged, a sample sheet is written and the game quits.
     */
    private static final class Benchmark {
        private final Path dir;
        private long startNanos;
        private int startDone = -1;
        private boolean finished;

        Benchmark(Path dir) {
            this.dir = dir;
        }

        void frame(Minecraft mc) {
            if (finished || mc.level == null || mc.player == null) return;
            var service = IconService.current();
            if (service == null) return;
            var status = service.status();
            if (startDone < 0) {
                startDone = status.done();
                startNanos = System.nanoTime();
                CCCloud.LOGGER.info("[cccloud] Icon benchmark: {} of {} icons stored at start", status.done(), status.total());
            }
            if (status.queued() + status.inFlight() > 0 || (renderer != null && renderer.busy())) return;
            finished = true;
            long wallMs = (System.nanoTime() - startNanos) / 1_000_000;
            try {
                service.store().flush();
                var after = service.status();
                var sample = new ArrayList<byte[]>();
                int i = 0;
                for (var item : BuiltInRegistries.ITEM) {
                    if (item == Items.AIR || i++ % 9 != 0 || sample.size() >= 256) continue;
                    var png = service.store().get(BuiltInRegistries.ITEM.getKey(item).toString());
                    if (png != null) sample.add(png);
                }
                Files.createDirectories(dir);
                Files.write(dir.resolve("icons_sample.png"), sheet(sample));
                var r = renderer;
                CCCloud.LOGGER.info("[cccloud] Icon benchmark: {} icons rendered this run ({} stored, {} could not be rendered, of {} items); "
                        + "wall time {} ms; render time {} ms in {}-ms frame slices; store {} bytes in {}",
                    after.done() - startDone, after.store().stored(), after.store().failed(), after.total(), wallMs,
                    r == null ? 0 : r.renderNanos() / 1_000_000, FRAME_BUDGET_NANOS / 1_000_000, after.store().bytes(), service.store().dir());
            } catch (Exception e) {
                CCCloud.LOGGER.error("[cccloud] Icon benchmark failed", e);
            }
            mc.stop();
        }

        /** 16 icons per row on a dark background. */
        private static byte[] sheet(List<byte[]> sample) {
            int cols = 16, rows = Math.max(1, (sample.size() + cols - 1) / cols);
            int w = cols * 34, h = rows * 34;
            var pixels = new int[w * h];
            java.util.Arrays.fill(pixels, 0xFF1A1A1A);
            for (int i = 0; i < sample.size(); i++) {
                var icon = PngCodec.decode(sample.get(i), 32, 32);
                if (icon == null) continue;
                int ox = (i % cols) * 34 + 1, oy = (i / cols) * 34 + 1;
                for (int y = 0; y < 32; y++) {
                    for (int x = 0; x < 32; x++) {
                        int p = icon[y * 32 + x];
                        int a = p >>> 24;
                        if (a == 0) continue;
                        int bg = 0x1A;
                        int r = (((p >> 16) & 0xFF) * a + bg * (255 - a)) / 255;
                        int g = (((p >> 8) & 0xFF) * a + bg * (255 - a)) / 255;
                        int b = ((p & 0xFF) * a + bg * (255 - a)) / 255;
                        pixels[(oy + y) * w + ox + x] = 0xFF000000 | r << 16 | g << 8 | b;
                    }
                }
            }
            return PngCodec.encode(w, h, pixels);
        }
    }
}
