package ai.ezfox.cccloud.compat.server;

import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.DashboardSection;
import ai.ezfox.cccloud.uplink.ActionValidator;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.Op;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.web.ActionResult;
import ai.ezfox.cccloud.web.QueryResult;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.GlobalPos;
import org.jspecify.annotations.Nullable;

/**
 * One mainframe card as a dashboard section: stable id derived from mainframe id + kind, routed like any section
 * ({@code CloudNetwork.registerSection}), published with {@code "restricted":true} (canControl devices only) and
 * {@code "source":"mainframe"}. Not linked to the mainframe's uplink list, so it costs no power. Server thread only.
 */
public final class MainframeSection implements DashboardSection {
    private final CloudMainframeBlockEntity mainframe;
    private final MainframeCard card;
    private final UUID sectionId;
    private final MainframeLink link;
    private final LinkedHashMap<String, Widget> widgets = new LinkedHashMap<>();
    private final Set<String> dirty = new LinkedHashSet<>();
    private final List<String[]> alerts = new ArrayList<>();
    private boolean structureDirty = true;
    private boolean online;
    private int revision;
    private boolean failed;

    private final DeviceCard.CardOutput output = new DeviceCard.CardOutput() {
        @Override
        public void value(String widgetId, JsonElement value) {
            var widget = widgets.get(widgetId);
            if (widget == null || widget.value().equals(value)) return;
            widget.setValue(value);
            if (widget.type() != WidgetType.CHART) dirty.add(widgetId);
        }

        @Override
        public void alert(String text, String level) {
            if (alerts.size() < 16) alerts.add(new String[]{text.length() > Limits.ALERT_TEXT ? text.substring(0, Limits.ALERT_TEXT) : text, level});
        }
    };

    MainframeSection(CloudMainframeBlockEntity mainframe, MainframeCard card) {
        this.mainframe = mainframe;
        this.card = card;
        this.sectionId = sectionId(mainframe.getMainframeId(), card.kind());
        this.link = new MainframeLink(GlobalPos.of(mainframe.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId());
        rebuild();
    }

    /** Stable section id of a mainframe card (layouts survive restarts). */
    public static UUID sectionId(UUID mainframeId, String kind) {
        return UUID.nameUUIDFromBytes(("cccloud:" + kind + ":" + mainframeId).getBytes(StandardCharsets.UTF_8));
    }

    private void rebuild() {
        var cardWidgets = DeviceCards.widgets(card);
        if (cardWidgets == null) {
            failed = true;
            return;
        }
        var old = new LinkedHashMap<>(widgets);
        widgets.clear();
        for (var widget : cardWidgets) {
            var previous = old.get(widget.id());
            if (previous != null && previous.type() == WidgetType.CHART && widget.type() == WidgetType.CHART) {
                var history = previous.toJson().get("history");
                if (history != null) for (var point : history.getAsJsonArray()) widget.push(point.getAsDouble());
            }
            widgets.put(widget.id(), widget);
        }
        revision = DeviceCards.revision(card);
        structureDirty = true;
    }

    public MainframeCard card() {
        return card;
    }

    public CloudMainframeBlockEntity mainframe() {
        return mainframe;
    }

    boolean failed() {
        return failed;
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
        return card.title();
    }

    /** Every tick: chart sampling and publishing; refresh = read the values (once per second). */
    void tick(long gameTime, boolean refresh) {
        boolean nowOnline = mainframe.status() == CloudStatus.ONLINE;
        if (nowOnline != online) {
            online = nowOnline;
            structureDirty = true;
        }
        if (failed) return;
        if (refresh) {
            int rev = DeviceCards.revision(card);
            if (rev < 0 || !DeviceCards.refresh(card, output)) {
                failed = true;
                return;
            }
            if (rev != revision) {
                rebuild();
                DeviceCards.refresh(card, output);
            }
        }
        for (var widget : widgets.values()) {
            if (widget.type() == WidgetType.CHART && widget.sample(gameTime)) dirty.add(widget.id());
        }
        publish();
    }

    /** Forces the next publish to send the whole section (e.g. the claims overlay changed). */
    void markStructureDirty() {
        structureDirty = true;
    }

    public JsonObject snapshot() {
        var json = new JsonObject();
        json.addProperty("uplinkId", sectionId.toString());
        json.addProperty("title", card.title());
        json.addProperty("online", online);
        var array = new JsonArray();
        for (var widget : widgets.values()) array.add(widget.toJson());
        json.add("widgets", array);
        json.addProperty("mode", "direct");
        json.addProperty("template", card.kind());
        json.addProperty("source", "mainframe");
        json.addProperty("restricted", true);
        card.snapshotExtras(json);
        return json;
    }

    private void publish() {
        var mainframeId = mainframe.getMainframeId();
        String id = sectionId.toString();
        if (structureDirty) {
            structureDirty = false;
            dirty.clear();
            WebHub.INSTANCE.publishUplink(mainframeId, snapshot());
        } else if (!dirty.isEmpty()) {
            for (var widgetId : dirty) {
                var widget = widgets.get(widgetId);
                if (widget != null) WebHub.INSTANCE.publishValue(mainframeId, id, widgetId, widget.value());
            }
            dirty.clear();
        }
        if (online) for (var alert : alerts) WebHub.INSTANCE.publishAlert(mainframeId, id, alert[0], alert[1]);
        alerts.clear();
    }

    void removeFromDashboard() {
        WebHub.INSTANCE.publishUplinkRemoved(mainframe.getMainframeId(), sectionId.toString());
    }

    @Override
    public CompletableFuture<ActionResult> handleAction(String widgetId, JsonElement value, @Nullable Op op, String user, boolean pinVerified) {
        if (!online) return ActionResult.done("uplink_offline");
        var widget = widgets.get(widgetId);
        var result = ActionValidator.validate(widget, value, op, pinVerified);
        if (!result.ok()) return ActionResult.done(result.error());
        if (result.op() != null) return ActionResult.done("invalid_value");
        return ActionResult.done(DeviceCards.action(card, widgetId, result.value(), user));
    }

    @Override
    public QueryResult handleQuery(String widgetId, String query) {
        return QueryResult.fail("unknown_widget");
    }

    /** Test/diagnostics: current values. */
    public Map<String, JsonElement> values() {
        var map = new LinkedHashMap<String, JsonElement>();
        for (var widget : widgets.values()) map.put(widget.id(), widget.value());
        return map;
    }

    public @Nullable Widget widget(String id) {
        return widgets.get(id);
    }
}
