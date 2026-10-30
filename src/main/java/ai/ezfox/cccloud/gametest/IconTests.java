package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.icons.IconService;
import ai.ezfox.cccloud.icons.IconStore;
import ai.ezfox.cccloud.icons.ModAssets;
import ai.ezfox.cccloud.icons.PngCodec;
import ai.ezfox.cccloud.icons.ServerIconPass;
import ai.ezfox.cccloud.icons.ServerIconRenderer;
import ai.ezfox.cccloud.web.CloudWebServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Item icons without a game client: the GameTest server is a dedicated server with nobody online, and still has our
 * own items' icons right after start – drawn by the server renderer in the background and served by
 * {@code GET /api/icon} (docs/SPEC.md "Item icons (as implemented)").
 */
public final class IconTests {
    private static final List<String> FLAT = List.of("cccloud:cloud_core", "cccloud:quantum_core", "cccloud:cloud_link_card", "cccloud:cloud_tablet",
        "cccloud:upgrade_range_vibranium", "cccloud:upgrade_memory_unobtainium");

    private IconTests() {
    }

    @SuppressWarnings("unchecked")
    public static void serverDrawsOwnIcons(GameTestHelper helper) {
        var service = IconService.current();
        helper.assertTrue(service != null, "the icon service runs");
        CompletableFuture<HttpResponse<byte[]>>[] request = new CompletableFuture[1];
        helper.succeedWhen(() -> {
            var progress = service.serverProgress();
            helper.assertTrue(progress != null, "the server renderer started at server start");
            helper.assertTrue(progress.state() == ServerIconPass.State.DONE, "waiting for the server renderer: " + progress);
            var store = service.store();
            for (var id : FLAT) {
                helper.assertTrue(store.source(id) == IconStore.Source.SERVER, id + " was drawn by the server, source " + store.source(id));
                byte[] png = store.get(id);
                helper.assertTrue(png != null && PngCodec.isValid(png, 32, 32), id + " is a valid 32×32 RGBA icon");
            }
            // Every item of ours the renderer can draw has its icon; the rest waits for a game client.
            var renderer = new ServerIconRenderer(ModAssets::read);
            int drawn = 0, left = 0;
            for (var item : BuiltInRegistries.ITEM) {
                var key = BuiltInRegistries.ITEM.getKey(item);
                if (!key.getNamespace().equals(CCCloud.MODID)) continue;
                var result = renderer.render(key.getNamespace(), key.getPath());
                if (result.argb() == null) {
                    left++;
                    continue;
                }
                helper.assertTrue(store.hasIcon(key.toString()), key + " (" + result.kind() + ") has an icon");
                drawn++;
            }
            helper.assertTrue(drawn >= FLAT.size() && drawn >= left, "most of our items are drawn on the server: " + drawn + " drawn, " + left + " left");

            // Served to the dashboard like a client render, but cached only briefly (a client's render replaces it).
            if (request[0] == null) {
                request[0] = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().sendAsync(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + CloudWebServer.port() + "/api/icon/cccloud/cloud_core.png"))
                        .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            }
            helper.assertTrue(request[0].isDone(), "waiting for GET /api/icon/cccloud/cloud_core.png");
            var response = request[0].join();
            helper.assertTrue(response.statusCode() == 200, "GET /api/icon: " + response.statusCode());
            helper.assertTrue(PngCodec.isValid(response.body(), 32, 32), "the stored icon is served");
            helper.assertTrue("public, max-age=600".equals(response.headers().firstValue("Cache-Control").orElse("")),
                "server icons are cached briefly: " + response.headers().firstValue("Cache-Control"));
        });
    }
}
