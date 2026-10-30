package ai.ezfox.cccloud.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code /cccloud config show} and the reload report never print secrets; changes are compared by value. */
class ConfigTextTest {
    static final String KEY_FILE = "/srv/mc/privkey-pkcs8.pem";

    static List<ConfigText.Entry> entries() {
        return List.of(
            new ConfigText.Entry("allowCloud", ConfigCatalog.SERVER_FILE, true, true, Reload.LIVE),
            new ConfigText.Entry("web.webPort", ConfigCatalog.SERVER_FILE, 8788, 8787, Reload.LIVE),
            new ConfigText.Entry("web.publicUrl", ConfigCatalog.SERVER_FILE, "https://admin:hunter2@dash.example.com", "", Reload.LIVE),
            new ConfigText.Entry("web.tlsCertPath", ConfigCatalog.SERVER_FILE, "/srv/mc/fullchain.pem", "", Reload.LIVE),
            new ConfigText.Entry("web.tlsKeyPath", ConfigCatalog.SERVER_FILE, KEY_FILE, "", Reload.LIVE),
            new ConfigText.Entry("push.pushAllowedHosts", ConfigCatalog.SERVER_FILE, List.of("ntfy.example.org"), List.of(), Reload.LIVE),
            new ConfigText.Entry("cards.mffsRequirePin", ConfigCatalog.INTEGRATIONS_FILE, true, true, Reload.LIVE),
            new ConfigText.Entry("icons.vanillaAssetsJar", ConfigCatalog.SERVER_FILE, "", "", Reload.RESTART));
    }

    @Test
    void showNeverPrintsSecrets() {
        var all = String.join("\n", ConfigText.show(entries(), null));
        assertFalse(all.contains(KEY_FILE), "the TLS key path is hidden");
        assertFalse(all.contains("privkey"), all);
        assertTrue(all.contains("web.tlsKeyPath = (set, hidden)"), all);
        assertFalse(all.contains("hunter2"), "user info in URLs is masked");
        assertFalse(all.contains("admin:"), all);
        assertTrue(all.contains("web.publicUrl = \"https://***@dash.example.com\""), all);
        assertTrue(all.contains("web.tlsCertPath = \"/srv/mc/fullchain.pem\""), "not a secret");
        // asking for the secret key by name shows it hidden too
        var one = ConfigText.show(entries(), "tlsKeyPath");
        assertEquals(List.of("== cccloud-server.toml ==", "web.tlsKeyPath = (set, hidden)  [live]"), one);
        assertFalse(String.join("\n", ConfigText.show(entries(), "web.tlsKeyPath")).contains(KEY_FILE));
        assertEquals("(not set)", ConfigText.display("web.tlsKeyPath", ""));
        for (String key : new String[]{"x.apiToken", "x.secret", "x.adminPassword", "x.privateKey", "web.tlsKeyPath"}) {
            assertTrue(ConfigText.secret(key), key);
            assertFalse(ConfigText.display(key, "s3cr3t").contains("s3cr3t"), key);
            assertFalse(ConfigText.display(key, List.of("s3cr3t")).contains("s3cr3t"), key);
        }
        assertFalse(ConfigText.secret("store.scriptStoreKey"), "the store's public key is public (GET /api/config)");
    }

    @Test
    void showFiltersAndMarksKinds() {
        var port = ConfigText.show(entries(), "webPort");
        assertEquals(List.of("== cccloud-server.toml ==", "web.webPort = 8788  (default 8787)  [live]"), port);
        var web = ConfigText.show(entries(), "web.");
        assertEquals(5, web.size(), "header + four web values: " + web);
        var cards = ConfigText.show(entries(), "cards");
        assertEquals("== cccloud-integrations-server.toml ==", cards.getFirst());
        assertTrue(ConfigText.show(entries(), "vanilla").get(1).endsWith("[restart]"));
        assertEquals(List.of("No config value matches 'nothing'."), ConfigText.show(entries(), "nothing"));
        assertEquals("[\"ntfy.example.org\"]", ConfigText.display("push.pushAllowedHosts", List.of("ntfy.example.org")));
        assertTrue(ConfigText.display("web.webAllowedHosts", List.of("x".repeat(500))).length() <= ConfigText.MAX_VALUE);
    }

    @Test
    void changesAreComparedByValue() {
        var before = new LinkedHashMap<String, Object>(Map.of("web.webPort", 8787, "api.apiEnabled", false, "push.pushAllowedHosts", List.of("a")));
        var after = new LinkedHashMap<String, Object>(before);
        assertEquals(List.of(), ConfigText.changes(before, after));
        after.put("web.webPort", 8788);
        after.put("push.pushAllowedHosts", List.of("a", "b"));
        assertEquals(List.of("web.webPort", "push.pushAllowedHosts").stream().sorted().toList(), ConfigText.changes(before, after).stream().sorted().toList());
        after.put("push.pushAllowedHosts", List.of("a"));
        after.put("web.webPort", 8787L);
        assertEquals(List.of(), ConfigText.changes(before, after), "same number, other box");
    }
}
