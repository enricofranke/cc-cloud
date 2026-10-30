package ai.ezfox.cccloud.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which cached state a reload re-applies for which changed keys (feature toggles, limiters, the web server). */
class ConfigPlanTest {
    @Test
    void nothingChangedNothingToDo() {
        assertTrue(ConfigPlan.of(List.of()).isEmpty());
    }

    @Test
    void webSettingsRestartTheWebServer() {
        for (String key : List.of("web.webPort", "web.webBind", "web.tlsCertPath", "web.tlsKeyPath", "web.webTrustedProxies", "web.webAllowInternet",
            "web.webCompression", "web.webEnabled", "web.publicUrl", "web.webAllowedHosts", "web.webTrustCloudflareHeader")) {
            assertEquals(EnumSet.of(ConfigPlan.WEB, ConfigPlan.NETWORK), ConfigPlan.of(List.of(key)), key);
        }
    }

    @Test
    void featureTogglesReachTheDashboards() {
        assertEquals(EnumSet.of(ConfigPlan.API, ConfigPlan.FEATURES, ConfigPlan.NETWORK), ConfigPlan.of(List.of("api.apiEnabled")));
        assertEquals(EnumSet.of(ConfigPlan.API, ConfigPlan.NETWORK), ConfigPlan.of(List.of("api.apiRequestsPerMinute")), "limits: no feature change");
        for (String key : List.of("push.pushEnabled", "terminal.terminalEnabled", "rules.rulesEnabled", "scenes.scenesEnabled", "map.voxelView",
            "production.productionEnabled")) {
            assertTrue(ConfigPlan.of(List.of(key)).contains(ConfigPlan.FEATURES), key);
        }
        assertEquals(EnumSet.of(ConfigPlan.STORE, ConfigPlan.FEATURES, ConfigPlan.NETWORK), ConfigPlan.of(List.of("store.scriptStoreKey")));
    }

    @Test
    void cachedStateIsRebuilt() {
        assertEquals(EnumSet.of(ConfigPlan.UPGRADES, ConfigPlan.NETWORK), ConfigPlan.of(List.of("upgrades.baseBrowsers")));
        assertEquals(EnumSet.of(ConfigPlan.NETWORK), ConfigPlan.of(List.of("upgrades.chunkAnchor")), "read live in the mainframe tick");
        assertEquals(EnumSet.of(ConfigPlan.CARDS, ConfigPlan.NETWORK), ConfigPlan.of(List.of("cards.mffsRequirePin")));
        assertEquals(EnumSet.of(ConfigPlan.MAP_SWEEP, ConfigPlan.NETWORK), ConfigPlan.of(List.of("map.mapTileMaxAgeDays")));
        assertEquals(EnumSet.of(ConfigPlan.ACCESS, ConfigPlan.NETWORK), ConfigPlan.of(List.of("teams.disabledTeamProviders")));
        assertEquals(EnumSet.of(ConfigPlan.NETWORK), ConfigPlan.of(List.of("network.uplinkRange", "power.mainframeMaxInputFE",
            "redstoneDevices.redstoneDeviceChangesPerSecond")), "read where used (buckets and buffers follow the config on their next use)");
    }

    @Test
    void everyCatalogKeyIsKnown() {
        for (var key : ConfigCatalog.keys()) {
            var plan = ConfigPlan.of(List.of(key.key()));
            assertTrue(plan.contains(ConfigPlan.NETWORK), key.key());
            if (key.key().startsWith("web.")) assertTrue(plan.contains(ConfigPlan.WEB), key.key());
        }
        for (String feature : ConfigPlan.FEATURE_KEYS) assertNotNull(ConfigCatalog.key(feature), feature + " exists");
    }
}
