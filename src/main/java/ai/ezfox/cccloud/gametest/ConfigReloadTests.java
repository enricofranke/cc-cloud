package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.config.ConfigReload;
import ai.ezfox.cccloud.web.CloudWebServer;
import ai.ezfox.cccloud.web.WebHub;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Live config (docs/SPEC.md "Live config"): values that were cached when the server started change through the real
 * reload event NeoForge fires for an edited file, and {@code /cccloud config reload}'s re-read brings back what the file
 * says. Its own test environment ({@code cccloud:config}, a batch of its own): it switches web server settings.
 */
public final class ConfigReloadTests {
    private ConfigReloadTests() {
    }

    /** Fires {@link ModConfigEvent.Reloading} for cccloud-server.toml on the mod bus, as NeoForge's file watcher does. */
    private static void fireReloadEvent() {
        var bus = ModList.get().getModContainerById(CCCloud.MODID).orElseThrow().getEventBus();
        for (ModConfig config : ModConfigs.getModConfigs(CCCloud.MODID)) {
            if (config.getType() == ModConfig.Type.SERVER && config.getFileName().endsWith("cccloud-server.toml")) {
                bus.post(new ModConfigEvent.Reloading(config));
            }
        }
    }

    public static void liveReload(GameTestHelper helper) {
        var linking = BridgeTestSupport.placeMainframe(helper);
        var id = linking.mainframe.getMainframeId();
        var server = helper.getLevel().getServer();
        int[] port = new int[1];
        int perKey = CloudConfig.get(CloudConfig.API_REQUESTS_PER_MINUTE);
        int browsers = CloudConfig.get(CloudConfig.BASE_BROWSERS);
        boolean compression = CloudConfig.get(CloudConfig.WEB_COMPRESSION);
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                helper.assertTrue(CloudWebServer.isRunning(), "the dashboard runs in GameTests");
                port[0] = CloudWebServer.port();
                var api = CloudWebServer.apiSettings();
                helper.assertTrue(api != null && api.perKeyPerMinute() == perKey, "API settings from the start: " + api);
                helper.assertTrue(WebHub.browserLimit.applyAsInt(id) == browsers, "base browsers from the start");
                // Edit three values that were cached at start (web server, API limiter, published upgrade effects) ...
                CloudConfig.API_REQUESTS_PER_MINUTE.set(perKey + 7);
                CloudConfig.BASE_BROWSERS.set(browsers + 5);
                CloudConfig.WEB_COMPRESSION.set(!compression);
                // ... and fire the reload NeoForge fires when the file changes.
                fireReloadEvent();
            })
            .thenWaitUntil(() -> {
                var api = CloudWebServer.apiSettings();
                helper.assertTrue(api != null && api.perKeyPerMinute() == perKey + 7, "API limit applied: " + api);
            })
            .thenExecute(() -> {
                helper.assertTrue(WebHub.browserLimit.applyAsInt(id) == browsers + 5, "upgrade base limit applied to a loaded mainframe: "
                    + WebHub.browserLimit.applyAsInt(id));
                helper.assertTrue(linking.mainframe.effects().browsers() == browsers + 5, "the mainframe's own effects too");
                helper.assertTrue(CloudWebServer.compression() == !compression, "compression for new connections");
                helper.assertTrue(CloudWebServer.isRunning() && CloudWebServer.port() == port[0], "compression alone keeps the listener and its port");
                var report = ConfigReload.lastReport();
                helper.assertTrue(report != null, "a report");
                var live = report.liveKeys();
                helper.assertTrue(live.containsAll(List.of("api.apiRequestsPerMinute", "upgrades.baseBrowsers", "web.webCompression")),
                    "reported as applied live: " + report.lines());
                helper.assertTrue(report.restartKeys().isEmpty() && report.errors().isEmpty(), "nothing needs a restart: " + report.lines());
                // /cccloud config reload re-reads the file, which still has the old values: everything goes back.
                var back = ConfigReload.reloadNow(server);
                helper.assertTrue(back.errors().isEmpty(), "re-read without errors: " + back.lines());
                helper.assertTrue(back.liveKeys().containsAll(live), "the re-read applies the file's values again: " + back.lines());
            })
            .thenExecute(() -> {
                helper.assertTrue(CloudConfig.get(CloudConfig.API_REQUESTS_PER_MINUTE) == perKey, "value from the file again");
                var api = CloudWebServer.apiSettings();
                helper.assertTrue(api != null && api.perKeyPerMinute() == perKey, "API limit back: " + api);
                helper.assertTrue(WebHub.browserLimit.applyAsInt(id) == browsers, "base browsers back");
                helper.assertTrue(CloudWebServer.compression() == compression, "compression back");
                // nothing left to apply: another reload changes nothing
                helper.assertTrue(ConfigReload.apply(server, "test").changes().isEmpty(), "idempotent");
            })
            .thenSucceed();
    }
}
