package ai.ezfox.cccloud.config;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

/**
 * Every value of both server config files says in its comment whether it applies live or needs a restart, and the
 * catalog (reload, {@code config show}) knows every value exactly once.
 */
class ConfigMarkersTest {
    private static List<ModConfigSpec.ValueSpec> specs(UnmodifiableConfig config, List<String> paths, String prefix) {
        var list = new ArrayList<ModConfigSpec.ValueSpec>();
        for (var e : config.valueMap().entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() instanceof ModConfigSpec.ValueSpec spec) {
                list.add(spec);
                paths.add(path);
            } else if (e.getValue() instanceof UnmodifiableConfig section) {
                list.addAll(specs(section, paths, path));
            }
        }
        return list;
    }

    @Test
    void everyValueEndsWithItsMarker() {
        for (var spec : List.of(CloudConfig.SPEC, IntegrationConfig.SPEC)) {
            var paths = new ArrayList<String>();
            var values = specs(spec.getSpec(), paths, "");
            assertFalse(values.isEmpty());
            for (int i = 0; i < values.size(); i++) {
                String comment = values.get(i).getComment();
                assertNotNull(comment, paths.get(i) + " has a comment");
                assertNotNull(Reload.of(comment), paths.get(i) + " ends with (applies live) or (restart required): " + comment);
                String last = comment.lines().reduce((a, b) -> b).orElse("");
                assertTrue(last.equals(" " + Reload.LIVE.marker) || last.equals(" " + Reload.RESTART.marker),
                    paths.get(i) + ": the marker is the last line, after NeoForge's Default/Range lines: " + comment);
                assertEquals(1, count(comment, "(applies live)") + count(comment, "(restart required)"), paths.get(i) + ": exactly one marker");
            }
        }
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) n++;
        return n;
    }

    @Test
    void theFileStartsWithTheLiveReloadHeader() {
        String first = CloudConfig.ALLOW_CLOUD.getSpec().getComment();
        assertNotNull(first);
        assertTrue(first.startsWith("CC: Cloud server config. Changes apply while the server runs"), first);
        assertTrue(first.contains("/cccloud config reload"));
        assertTrue(first.endsWith("stays offline.\n (applies live)"), "allowCloud's own text and marker come last: " + first);
        for (var line : first.lines().toList()) assertFalse(line.isBlank(), "no blank comment lines (NeoForge would keep correcting them)");
        assertEquals("allowCloud", ConfigCatalog.keys().getFirst().key(), "allowCloud is the first value, so its comment heads the file");
    }

    @Test
    void catalogListsEveryValueOnceInFileOrder() {
        var keys = ConfigCatalog.keys();
        var paths = new ArrayList<String>();
        specs(CloudConfig.SPEC.getSpec(), paths, "");
        int server = paths.size();
        specs(IntegrationConfig.SPEC.getSpec(), paths, "");
        assertEquals(paths.size(), keys.size(), "both files");
        assertEquals(new HashSet<>(paths), new HashSet<>(keys.stream().map(ConfigCatalog.Key::key).toList()));
        assertEquals(paths.size(), new HashSet<>(paths).size(), "keys are unique across both files");
        assertEquals(server, keys.stream().filter(k -> k.file().equals(ConfigCatalog.SERVER_FILE)).count());
        var web = ConfigCatalog.key("web.webPort");
        assertNotNull(web);
        assertEquals(Reload.LIVE, web.reload());
        assertEquals(8787, web.current(), "the default while no world is loaded");
        assertEquals(ConfigCatalog.INTEGRATIONS_FILE, ConfigCatalog.key("cards.mffsRequirePin").file());
        // every key of the snapshot is in the catalog; values are the defaults here
        var snapshot = ConfigCatalog.snapshot();
        assertEquals(keys.size(), snapshot.size());
        assertEquals(false, snapshot.get("web.webAllowInternet"));
    }

    @Test
    void rangeValuesKeepTheirRangeAndEndWithTheMarker() {
        String port = CloudConfig.WEB_PORT.getSpec().getComment();
        assertTrue(port.contains("Range: 1 ~ 65535"), port);
        assertTrue(port.endsWith("Range: 1 ~ 65535\n (applies live)"), port);
        assertNotNull(CloudConfig.WEB_PORT.getSpec().getRange(), "the range stays for NeoForge's config screen and corrections");
        String hosts = CloudConfig.WEB_ALLOWED_HOSTS.getSpec().getComment();
        assertTrue(hosts.endsWith(" (applies live)"), hosts);
    }

    @Test
    void restartValuesAreMarked() {
        var b = new MarkedBuilder();
        var live = b.comment("Read where used.").define("a", true);
        var start = b.restart().comment("Read at start.").defineInRange("b", 1, 0, 9);
        var after = b.comment("Live again.").define("c", "x");
        b.build();
        assertTrue(live.getSpec().getComment().endsWith("\n (applies live)"));
        assertTrue(start.getSpec().getComment().endsWith("Range: 0 ~ 9\n (restart required)"), start.getSpec().getComment());
        assertEquals(Reload.RESTART, Reload.of(start.getSpec().getComment()));
        assertEquals(Reload.LIVE, Reload.of(after.getSpec().getComment()), "restart() only marks the next value");
    }

    @Test
    void markHelperAppendsToTheLastLine() {
        assertArrayEquals(new String[]{"a", "b (applies live)"}, Reload.LIVE.mark("a", "b"));
        assertArrayEquals(new String[]{"x (restart required)"}, Reload.RESTART.mark("x"));
        assertEquals(Reload.RESTART, Reload.of("Only at start. (restart required)\n"));
        assertNull(Reload.of("no marker"));
        assertNull(Reload.of(null));
    }
}
