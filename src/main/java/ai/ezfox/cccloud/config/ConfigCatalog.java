package ai.ezfox.cccloud.config;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.Nullable;

/**
 * Every value of the two server config files ({@code cccloud-server.toml}, {@code cccloud-integrations-server.toml}) with
 * its key ({@code web.webPort}), file and kind ({@link Reload}, from the comment's marker).
 */
public final class ConfigCatalog {
    public static final String SERVER_FILE = "cccloud-server.toml";
    public static final String INTEGRATIONS_FILE = "cccloud-integrations-server.toml";

    /** One config value. */
    public record Key(String key, String file, ModConfigSpec spec, ModConfigSpec.ConfigValue<?> value, Reload reload) {
        public @Nullable Object current() {
            return copy(spec.isLoaded() ? value.get() : value.getDefault());
        }

        public @Nullable Object defaultValue() {
            return copy(value.getDefault());
        }
    }

    private static volatile @Nullable List<Key> keys;

    private ConfigCatalog() {
    }

    /** Every value of both files, in file order. */
    public static List<Key> keys() {
        var list = keys;
        if (list == null) {
            var all = new ArrayList<Key>();
            collect(CloudConfig.SPEC, SERVER_FILE, CloudConfig.SPEC.getValues(), all);
            collect(IntegrationConfig.SPEC, INTEGRATIONS_FILE, IntegrationConfig.SPEC.getValues(), all);
            keys = list = List.copyOf(all);
        }
        return list;
    }

    private static void collect(ModConfigSpec spec, String file, UnmodifiableConfig values, List<Key> out) {
        for (var entry : values.valueMap().entrySet()) {
            if (entry.getValue() instanceof ModConfigSpec.ConfigValue<?> value) {
                var kind = Reload.of(value.getSpec().getComment());
                // unmarked values count as restart-only (ConfigMarkersTest makes sure there are none)
                out.add(new Key(String.join(".", value.getPath()), file, spec, value, kind != null ? kind : Reload.RESTART));
            } else if (entry.getValue() instanceof UnmodifiableConfig section) {
                collect(spec, file, section, out);
            }
        }
    }

    /** The current values (defaults where a file is not loaded), key → value. */
    public static Map<String, Object> snapshot() {
        var map = new LinkedHashMap<String, Object>();
        for (var key : keys()) map.put(key.key(), key.current());
        return map;
    }

    public static @Nullable Key key(String key) {
        for (var k : keys()) {
            if (k.key().equals(key)) return k;
        }
        return null;
    }

    /** Entries for {@code /cccloud config show}. */
    public static List<ConfigText.Entry> entries() {
        var list = new ArrayList<ConfigText.Entry>();
        for (var key : keys()) list.add(new ConfigText.Entry(key.key(), key.file(), key.current(), key.defaultValue(), key.reload()));
        return list;
    }

    private static @Nullable Object copy(@Nullable Object value) {
        return value instanceof List<?> list ? List.copyOf(list) : value;
    }
}
