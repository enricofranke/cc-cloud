package ai.ezfox.cccloud.production;

import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.DashboardSection;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Op;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.web.ActionResult;
import ai.ezfox.cccloud.web.QueryResult;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.GlobalPos;
import org.jspecify.annotations.Nullable;

/**
 * The Production card of a mainframe (docs/SPEC.md v0.10): one read-only {@code chart} per pinned rate ("Iron Ingot
 * /min"), updated every sample. An ordinary section, so it can be arranged in the layout editor and shows on tablets and
 * displays. Server thread only.
 */
public final class ProductionSection implements DashboardSection {
    public static final String KIND = "production";
    public static final String WIDGET_PREFIX = "p_";
    private final CloudMainframeBlockEntity mainframe;
    private final UUID sectionId;
    private final MainframeLink link;
    private final LinkedHashMap<String, Widget> widgets = new LinkedHashMap<>();
    private List<ProductionData.Pin> pins = List.of();
    private @Nullable JsonObject published;

    ProductionSection(CloudMainframeBlockEntity mainframe) {
        this.mainframe = mainframe;
        this.sectionId = ProductionService.sectionId(mainframe.getMainframeId());
        this.link = new MainframeLink(GlobalPos.of(mainframe.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId());
    }

    CloudMainframeBlockEntity mainframe() {
        return mainframe;
    }

    @Override
    public UUID sectionId() {
        return sectionId;
    }

    @Override
    public @Nullable MainframeLink getLink() {
        return link;
    }

    @Override
    public String getTitle() {
        return "Production";
    }

    /** Rebuilds the charts when the pins changed (keeping the history of the ones that stay) and publishes changes. */
    void update(List<ProductionData.Pin> current, int window, int intervalSeconds) {
        if (!current.equals(pins) || widgets.isEmpty()) {
            pins = List.copyOf(current);
            var old = new LinkedHashMap<>(widgets);
            widgets.clear();
            for (var pin : pins) {
                String id = WIDGET_PREFIX + pin.id();
                try {
                    var widget = WidgetFactory.create(WidgetType.CHART, id, pin.label(), Map.of("unit", pin.kind().equals("energy") ? "FE/t" : "/min",
                        "points", Math.max(10, Math.min(300, window)), "interval", (double) Math.max(1, intervalSeconds), "style", "line"));
                    var previous = old.get(id);
                    if (previous != null) {
                        var history = previous.toJson().get("history");
                        if (history != null) for (var point : history.getAsJsonArray()) widget.push(point.getAsDouble());
                        widget.setValue(previous.value());
                    }
                    widgets.put(id, widget);
                } catch (WidgetFactory.WidgetException e) {
                    // labels are built by the service and always valid
                }
            }
        }
        var structure = snapshot();
        if (structure.equals(published)) return;
        published = structure;
        WebHub.INSTANCE.publishUplink(mainframe.getMainframeId(), fullSnapshot());
    }

    /** One new sample of a pinned rate. */
    void push(String pinId, double value) {
        var widget = widgets.get(WIDGET_PREFIX + pinId);
        if (widget == null || !Double.isFinite(value)) return;
        var number = Json.number(value);
        widget.setValue(number);
        widget.push(value);
        if (published != null) WebHub.INSTANCE.publishValue(mainframe.getMainframeId(), sectionId.toString(), widget.id(), number);
    }

    /** The structure (title, online, widgets without values): a change republishes the whole section. */
    JsonObject snapshot() {
        var json = new JsonObject();
        json.addProperty("uplinkId", sectionId.toString());
        json.addProperty("title", getTitle());
        json.addProperty("online", mainframe.status() == CloudStatus.ONLINE);
        var array = new JsonArray();
        for (var widget : widgets.values()) {
            var w = widget.toJson();
            w.remove("history"); // the hub keeps the history from the value deltas; only the structure decides a republish
            w.remove("value");
            array.add(w);
        }
        json.add("widgets", array);
        json.addProperty("mode", "direct");
        json.addProperty("template", KIND);
        json.addProperty("source", "mainframe");
        return json;
    }

    /** The full snapshot with values and history (what is actually published). */
    private JsonObject fullSnapshot() {
        var json = snapshot();
        var array = new JsonArray();
        for (var widget : widgets.values()) array.add(widget.toJson());
        json.add("widgets", array);
        return json;
    }

    void removeFromDashboard() {
        published = null;
        WebHub.INSTANCE.publishUplinkRemoved(mainframe.getMainframeId(), sectionId.toString());
    }

    @Override
    public CompletableFuture<ActionResult> handleAction(String widgetId, JsonElement value, @Nullable Op op, String user, boolean pinVerified) {
        return ActionResult.done("not_interactive");
    }

    @Override
    public QueryResult handleQuery(String widgetId, String query) {
        return QueryResult.fail("unknown_widget");
    }
}
