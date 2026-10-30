package ai.ezfox.cccloud.sensor;

import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.DashboardSection;
import ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity;
import ai.ezfox.cccloud.uplink.ActionValidator;
import ai.ezfox.cccloud.uplink.Op;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.web.ActionResult;
import ai.ezfox.cccloud.web.QueryResult;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * The dashboard card of one Advanced Cloud Sensor: an ordinary section (layout editor, share links, tablet, display,
 * rules, scenes, history and the public API work unchanged) whose id is the sensor's device id, so the network map,
 * unlinking and the orphan sweep find it like an uplink's card. Widgets come from {@link SensorCards}; the widgets with
 * player names carry a {@code publicValue} that the hub sends to everyone who may not see names. An action is validated
 * like any widget action, then applied to the sensor's settings. Server thread only.
 */
public final class SensorSection implements DashboardSection {
    private final AdvancedSensor sensor;
    private final RsDeviceBlockEntity device;
    private final MainframeLink link;
    private final Map<String, Widget> widgets = new LinkedHashMap<>();
    /** The last snapshot sent to the hub (to send only value deltas when nothing else changed). */
    private @Nullable JsonObject published;

    SensorSection(AdvancedSensor sensor, RsDeviceBlockEntity device, MainframeLink link) {
        this.sensor = sensor;
        this.device = device;
        this.link = link;
    }

    @Override
    public UUID sectionId() {
        return device.deviceId();
    }

    @Override
    public MainframeLink getLink() {
        return link;
    }

    public UUID mainframeId() {
        return link.id();
    }

    @Override
    public String getTitle() {
        return device.record().displayName();
    }

    public boolean isOnline() {
        return device.status() == CloudStatus.ONLINE;
    }

    /**
     * The card as published: widgets with their values, private ones with a {@code publicValue}. While the claim guard
     * blocks the sensor's block: no widgets, the claim instead (docs/SECURITY.md "Claims").
     */
    public JsonObject snapshot() {
        var card = SensorCards.build(sensor.cardState());
        widgets.clear();
        var array = new JsonArray();
        var block = device.claimBlock();
        for (var w : card.widgets()) {
            widgets.put(w.id(), w);
            if (block != null) continue;
            var json = w.toJson();
            var pub = card.publicValues().get(w.id());
            if (pub != null) json.add("publicValue", pub);
            array.add(json);
        }
        var json = new JsonObject();
        json.addProperty("uplinkId", sectionId().toString());
        json.addProperty("title", getTitle());
        json.addProperty("online", isOnline());
        json.add("widgets", array);
        json.addProperty("mode", "direct");
        json.addProperty("template", SensorCards.TEMPLATE);
        json.addProperty("source", "sensor");
        if (block != null) json.add("blocked", ai.ezfox.cccloud.claims.ClaimGuard.json(block));
        return json;
    }

    /** Sends the card: the whole snapshot when its structure changed, else only the changed values. */
    void publish() {
        var snapshot = snapshot();
        var last = published;
        if (snapshot.equals(last)) return;
        published = snapshot;
        if (last == null || !withoutValues(last).equals(withoutValues(snapshot))) {
            WebHub.INSTANCE.publishUplink(link.id(), snapshot);
            return;
        }
        var before = new LinkedHashMap<String, JsonObject>();
        for (var w : last.getAsJsonArray("widgets")) before.put(w.getAsJsonObject().get("id").getAsString(), w.getAsJsonObject());
        for (var e : snapshot.getAsJsonArray("widgets")) {
            var w = e.getAsJsonObject();
            String id = w.get("id").getAsString();
            var old = before.get(id);
            var value = w.get("value");
            var pub = w.get("publicValue");
            if (old != null && java.util.Objects.equals(value, old.get("value")) && java.util.Objects.equals(pub, old.get("publicValue"))) continue;
            if (value != null) WebHub.INSTANCE.publishValue(link.id(), sectionId().toString(), id, value, pub);
        }
    }

    private static JsonObject withoutValues(JsonObject snapshot) {
        var copy = snapshot.deepCopy();
        for (var w : copy.getAsJsonArray("widgets")) {
            w.getAsJsonObject().remove("value");
            w.getAsJsonObject().remove("publicValue");
        }
        return copy;
    }

    @Override
    public CompletableFuture<ActionResult> handleAction(String widgetId, JsonElement value, @Nullable Op op, String user, boolean pinVerified) {
        if (ai.ezfox.cccloud.claims.ClaimGuard.check(device) != null) return ActionResult.done(ai.ezfox.cccloud.claims.ClaimGuard.ERROR);
        if (!isOnline()) return ActionResult.done("uplink_offline");
        if (widgets.isEmpty()) snapshot();
        var result = ActionValidator.validate(widgets.get(widgetId), value, op, pinVerified);
        if (!result.ok()) return ActionResult.done(result.error());
        if (result.op() != null || result.value() == null) return ActionResult.done("invalid_value");
        var next = SensorActions.apply(sensor.settings(), widgetId, result.value());
        if (next.error() != null) return ActionResult.done(next.error());
        if (!device.tryChange()) return ActionResult.done("rate_limited");
        String error = sensor.update(next.settings(), null);
        if (error == null) ai.ezfox.cccloud.CloudLog.LOGGER.info("[cccloud] {} set {} of the Advanced Cloud Sensor {} to {}", user, widgetId, getTitle(), result.value());
        return ActionResult.done(error);
    }

    @Override
    public QueryResult handleQuery(String widgetId, String query) {
        return QueryResult.fail("unknown_widget");
    }
}
