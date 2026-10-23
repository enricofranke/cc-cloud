package ai.ezfox.cccloud.history;

import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.web.AccessApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * {@code GET /api/mainframes/<id>/history/<range>/<json|csv>/<w|i>/<uplinkId>/<widgetId or item>} (docs/SPEC.md v0.5 F).
 * The caller resolved the session's permissions; this checks the rest: {@code view} (+ {@code view.positions} for
 * restricted sections), items need {@code storage.search}, CSV export needs {@code act.buttons}. Pure Java, unit tested.
 */
public final class HistoryApi {
    public static final Pattern PATH = Pattern.compile(
        "/api/mainframes/[0-9a-f-]{36}/history/(1h|24h|7d|30d)/(json|csv)/(w|i)/([0-9a-f-]{36})/([A-Za-z0-9_.:/-]{1,192})");

    private HistoryApi() {
    }

    /**
     * @param widgetRange the widget's {@code opts.history} (null: the widget is not published or did not ask for it)
     */
    public static AccessApi.Response handle(HistoryStore store, UUID mainframeId, Set<Perm> perms, boolean restricted, String rangeId, String format,
                                            String kind, String uplinkId, String id, @Nullable String widgetRange, int retentionHours, long now) {
        if (!perms.contains(Perm.VIEW)) return AccessApi.error(403, "forbidden");
        if (restricted && !perms.contains(Perm.VIEW_POSITIONS)) return AccessApi.error(403, "not_allowed");
        if (kind.equals("i") && !perms.contains(Perm.STORAGE_SEARCH)) return AccessApi.error(403, "not_allowed");
        boolean csv = format.equals("csv");
        if (csv && !perms.contains(Perm.ACT_BUTTONS)) return AccessApi.error(403, "not_allowed");
        var range = HistorySeries.Range.byId(rangeId);
        if (range == null) return AccessApi.error(400, "invalid");
        String key = kind.equals("w") ? HistoryService.widgetKey(uplinkId, id) : HistoryService.itemKey(uplinkId, id);
        var stored = store.range(mainframeId, key);
        if (stored == null && widgetRange == null) return AccessApi.error(404, "no_history");
        long step = store.step(mainframeId, key, range);
        var points = store.points(mainframeId, key, range, now);
        if (csv) {
            var text = new StringBuilder("time,value\n");
            for (var p : points) {
                text.append(Instant.ofEpochMilli(p.time())).append(',');
                if (Double.isFinite(p.value())) text.append(round(p.value()));
                text.append('\n');
            }
            return new AccessApi.Response(200, new JsonObject(), text.toString().getBytes(StandardCharsets.UTF_8));
        }
        var json = new JsonObject();
        json.addProperty("key", key);
        json.addProperty("range", range.id);
        json.addProperty("kept", stored != null ? stored.id : HistorySeries.Range.limit(range, retentionHours).id);
        json.addProperty("retentionHours", retentionHours);
        json.addProperty("step", step);
        var array = new JsonArray();
        for (var p : points) {
            var row = new JsonArray();
            row.add(p.time());
            if (Double.isFinite(p.value())) row.add(round(p.value()));
            else row.add(JsonNull.INSTANCE);
            array.add(row);
        }
        json.add("points", array);
        return new AccessApi.Response(200, json);
    }

    static double round(double v) {
        return Math.abs(v) >= 1e12 ? v : Math.round(v * 1000) / 1000.0;
    }
}
