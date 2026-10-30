package ai.ezfox.cccloud.history;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.bridge.BridgeCore;
import ai.ezfox.cccloud.bridge.BridgeSection;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.upgrade.Upgrades;
import ai.ezfox.cccloud.web.WebHub;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Samples long-term statistics (docs/SPEC.md v0.5 F): every second the widgets with {@code opts.history}, every minute
 * the item counts of the storage bridges' watchlists and favourites (range 24 h). Retention per mainframe from the Memory
 * upgrade. Server thread.
 */
public final class HistoryService {
    /** Item series keep 24 h (if the retention allows it). */
    public static final HistorySeries.Range ITEM_RANGE = HistorySeries.Range.H24;
    private static LongSupplier clock = System::currentTimeMillis;
    private static final Set<String> CAPPED = new HashSet<>();

    private HistoryService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
    }

    /** GameTests: a manual clock. */
    public static void setClockForTest(LongSupplier testClock) {
        clock = testClock;
    }

    public static long now() {
        return clock.getAsLong();
    }

    public static String widgetKey(String uplinkId, String widgetId) {
        return "w:" + uplinkId + "/" + widgetId;
    }

    public static String itemKey(String uplinkId, String item) {
        return "i:" + uplinkId + "/" + item;
    }

    public static int retentionHours(UUID mainframeId) {
        return Upgrades.effects(mainframeId).historyHours();
    }

    private static void tick(MinecraftServer server) {
        long tickCost = ai.ezfox.cccloud.network.TickCost.start();
        try {
            int t = server.getTickCount();
            if (t % 20 != 0) return;
            var store = HistoryData.get(server).store();
            store.setMaxTotalBytes(CloudConfig.get(CloudConfig.HISTORY_MAX_TOTAL_MB) * 1024L * 1024L);
            long now = now();
            for (var sample : WebHub.INSTANCE.historySamples()) {
                var range = HistorySeries.Range.byId(sample.range());
                if (range == null) continue;
                String key = widgetKey(sample.uplinkId(), sample.widgetId());
                if (!store.record(sample.mainframeId(), key, range, retentionHours(sample.mainframeId()), now, sample.value()) && CAPPED.add(sample.mainframeId() + key)) {
                    CCCloud.LOGGER.warn("[cccloud] Long-term statistics of mainframe {} are full (2 MB per mainframe, historyMaxTotalMB for all): {} is not recorded", sample.mainframeId(), key);
                }
            }
            if (t % 1200 == 0) {
                sampleItems(store, now);
                store.prune(now);
            }
        } finally {
            ai.ezfox.cccloud.network.TickCost.end(ai.ezfox.cccloud.network.TickCost.HISTORY, tickCost);
        }
    }

    /** Item counts of every loaded storage card (watchlist + favourites). */
    public static void sampleItems(HistoryStore store, long now) {
        for (var section : CloudNetwork.sections()) {
            var link = section.getLink();
            if (link == null) continue;
            var core = core(section);
            if (core == null || !core.isConnected()) continue;
            for (String name : core.trackedItems()) {
                var item = ai.ezfox.cccloud.bridge.ItemNames.byName(name);
                if (item == null) continue;
                store.record(link.id(), itemKey(section.sectionId().toString(), name), ITEM_RANGE, retentionHours(link.id()), now, core.adapter().itemCount(item));
            }
        }
    }

    private static @Nullable BridgeCore core(ai.ezfox.cccloud.network.DashboardSection section) {
        if (section instanceof BridgeSection bridge) return bridge.readableCore();
        if (section instanceof CloudUplinkBlockEntity uplink && uplink.storageBridge() != null) return uplink.storageBridge().bridgeCore();
        return null;
    }

    /** 24 h of an item's count for the storage item details (≤ 288 points, 5-min averages, oldest first, gaps null). */
    public static com.google.gson.JsonArray itemSparkline(MinecraftServer server, UUID mainframeId, String uplinkId, String item) {
        var points = HistoryData.get(server).store().points(mainframeId, itemKey(uplinkId, item), ITEM_RANGE, now());
        var out = new com.google.gson.JsonArray();
        // 1-min buckets → groups of 5
        for (int i = 0; i < points.size(); i += 5) {
            double sum = 0;
            int n = 0;
            for (int j = i; j < Math.min(points.size(), i + 5); j++) {
                double v = points.get(j).value();
                if (Double.isFinite(v)) {
                    sum += v;
                    n++;
                }
            }
            if (n == 0) out.add(com.google.gson.JsonNull.INSTANCE);
            else out.add(Math.round(sum / n * 100) / 100.0);
        }
        return out;
    }
}
