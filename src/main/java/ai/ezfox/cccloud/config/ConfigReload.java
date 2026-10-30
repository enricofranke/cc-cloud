package ai.ezfox.cccloud.config;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.web.CloudWebServer;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Applies server config changes while the server runs. NeoForge watches the files and fires
 * {@link ModConfigEvent.Reloading} (on its own thread); {@code /cccloud config reload} re-reads them on demand. Either way
 * the values are compared with what was last applied, on the server thread, and whatever was cached is re-applied
 * ({@link ConfigPlan}): the web server is reconfigured (rebind first, all or nothing), API limits and switches, upgrade
 * base limits, card detection, store keys and so on. Values that could not be applied (a port in use, unreadable TLS
 * files) keep their old state and are tried again by the next reload.
 */
public final class ConfigReload {
    /** What was applied last (key → value); empty until the server has started. Server thread. */
    private static Map<String, Object> applied = Map.of();
    private static volatile @Nullable ConfigReport last;

    private ConfigReload() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ModConfigEvent.Reloading.class, ConfigReload::onReloading);
        // after the web server started (CloudNetwork, normal priority): what runs now is the baseline
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerStartedEvent.class, e -> baseline());
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> reset());
    }

    private static synchronized void reset() {
        applied = Map.of();
        last = null;
    }

    /** The values the server runs with now are the ones to compare the next reload with. */
    static synchronized void baseline() {
        applied = ConfigCatalog.snapshot();
    }

    private static void onReloading(ModConfigEvent.Reloading event) {
        var config = event.getConfig();
        if (config.getType() != ModConfig.Type.SERVER || !CCCloud.MODID.equals(config.getModId())) return;
        var server = CloudNetwork.server();
        if (server == null) return;
        // NeoForge fires this on its file watcher thread: everything happens on the server thread.
        server.execute(() -> apply(server, "file"));
    }

    /** The last report with changes (GameTests, {@code config show}). */
    public static @Nullable ConfigReport lastReport() {
        return last;
    }

    /**
     * Compares the config with what was applied last and applies the difference. Server thread. Logs the report when
     * something changed.
     */
    public static synchronized ConfigReport apply(MinecraftServer server, String source) {
        if (applied.isEmpty()) return ConfigReport.none(source); // not started yet: the start reads the config anyway
        var before = applied;
        var now = ConfigCatalog.snapshot();
        var changed = ConfigText.changes(before, now);
        var plan = ConfigPlan.of(changed);
        // The command also re-applies [web] without a changed key: renewed TLS certificate files (same paths).
        boolean command = source.equals("command");
        if (changed.isEmpty() && !command) return ConfigReport.none(source);

        var notes = new ArrayList<String>();
        var errors = new ArrayList<String>();
        var failed = new ArrayList<String>();
        if (plan.contains(ConfigPlan.WEB) || command) {
            var change = CloudWebServer.reconfigure(server);
            if (!change.ok()) {
                errors.add("[web] " + change.message());
                for (var key : changed) if (key.startsWith("web.")) failed.add(key);
            } else if (change.changed()) {
                notes.add("Dashboard web server: " + change.message());
            }
        }
        if (plan.contains(ConfigPlan.API)) {
            CloudWebServer.applyApi();
            notes.add("Public API: new settings in use (rate limits keep what each key and IP already used)");
        }
        if (plan.contains(ConfigPlan.UPGRADES)) {
            ai.ezfox.cccloud.upgrade.Upgrades.rebase(ai.ezfox.cccloud.upgrade.Upgrades.base());
            for (var mainframe : List.copyOf(CloudNetwork.mainframes())) {
                if (!mainframe.isRemoved()) mainframe.refreshEffects();
            }
            notes.add("Upgrade base limits recomputed for every mainframe");
        }
        if (plan.contains(ConfigPlan.CARDS)) {
            for (var uplink : List.copyOf(CloudNetwork.uplinks())) {
                if (!uplink.isRemoved()) uplink.redetectCards();
            }
            notes.add("Ready-made cards are detected again within a second");
        }
        if (plan.contains(ConfigPlan.STORE)) ai.ezfox.cccloud.script.StoreTrust.clearCache();
        if (plan.contains(ConfigPlan.MAP_SWEEP)) ai.ezfox.cccloud.map.MapTileService.sweepNow(server);
        if (plan.contains(ConfigPlan.FEATURES)) {
            ai.ezfox.cccloud.web.WebHub.INSTANCE.broadcastConfigChanged();
            notes.add("Open dashboards reload the server's feature switches");
        }
        if (plan.contains(ConfigPlan.ACCESS)) CloudWebServer.recheckAll();
        if (plan.contains(ConfigPlan.NETWORK)) ai.ezfox.cccloud.network.NetworkService.markDirty();

        // What failed keeps its old value in the baseline, so the next reload tries it again.
        var next = new HashMap<String, Object>(now);
        for (var key : failed) next.put(key, before.get(key));
        applied = ordered(now, next);

        var changes = new ArrayList<ConfigReport.Change>();
        for (var key : changed) {
            var k = ConfigCatalog.key(key);
            changes.add(new ConfigReport.Change(key, ConfigText.display(key, before.get(key)), ConfigText.display(key, now.get(key)),
                k != null ? k.reload() : Reload.RESTART, !failed.contains(key)));
        }
        var report = new ConfigReport(source, changes, notes, errors);
        if (!changed.isEmpty() || !errors.isEmpty() || !notes.isEmpty()) {
            last = report;
            for (var line : report.lines()) {
                if (line.startsWith("Error") || line.startsWith("Not applied")) CCCloud.LOGGER.error("[cccloud] {}", line);
                else CCCloud.LOGGER.info("[cccloud] {}", line);
            }
        }
        return report;
    }

    private static Map<String, Object> ordered(Map<String, Object> order, Map<String, Object> values) {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (var key : order.keySet()) map.put(key, values.get(key));
        return map;
    }

    /**
     * {@code /cccloud config reload}: reads both files again (also when NeoForge's file watcher is off or missed the
     * change), applies the difference and returns the report. Invalid values are corrected to their default in memory,
     * as NeoForge does when it loads a file. Server thread.
     */
    public static ConfigReport reloadNow(MinecraftServer server) {
        var errors = new ArrayList<String>();
        var reread = new ArrayList<ModConfig>();
        for (var modConfig : ModConfigs.getModConfigs(CCCloud.MODID)) {
            if (modConfig.getType() != ModConfig.Type.SERVER) continue;
            try {
                if (reread(modConfig)) reread.add(modConfig);
            } catch (Exception e) {
                errors.add(modConfig.getFileName() + " could not be read (" + e.getMessage() + "); its previous values stay in use");
            }
        }
        var report = apply(server, "command").withErrors(errors);
        // Tell NeoForge (and everyone else listening) like a file reload does: it syncs the new values to the clients.
        // Our own listener finds nothing left to apply.
        var container = ModList.get().getModContainerById(CCCloud.MODID).orElse(null);
        var bus = container == null ? null : container.getEventBus();
        if (bus != null) for (var modConfig : reread) bus.post(new ModConfigEvent.Reloading(modConfig));
        if (!errors.isEmpty()) last = report;
        return report;
    }

    /** Reads one file and copies its (corrected) values into the loaded config. True if a value changed. */
    private static boolean reread(ModConfig modConfig) throws Exception {
        var path = modConfig.getFullPath();
        var loaded = modConfig.getLoadedConfig();
        if (path == null || loaded == null || !(modConfig.getSpec() instanceof ModConfigSpec spec)) return false;
        CommentedConfig parsed = TomlFormat.newConfig();
        try (var reader = Files.newBufferedReader(path)) {
            new TomlParser().parse(reader, parsed, ParsingMode.REPLACE);
        }
        spec.correct(parsed, (action, key, incorrect, corrected) -> CCCloud.LOGGER.warn("[cccloud] {}: {} = {} is not valid, using {}",
            modConfig.getFileName(), String.join(".", key), incorrect, corrected));
        boolean changed = false;
        for (var key : ConfigCatalog.keys()) {
            if (key.spec() != spec) continue;
            var keyPath = key.value().getPath();
            Object value = parsed.get(keyPath);
            if (value != null && !Objects.equals(value, loaded.config().get(keyPath))) {
                loaded.config().set(keyPath, value);
                changed = true;
            }
        }
        if (changed) spec.afterReload(); // clears the cached values
        return changed;
    }
}
