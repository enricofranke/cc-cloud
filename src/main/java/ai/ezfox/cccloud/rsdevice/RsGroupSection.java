package ai.ezfox.cccloud.rsdevice;

import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.DashboardSection;
import ai.ezfox.cccloud.uplink.ActionValidator;
import ai.ezfox.cccloud.uplink.Op;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.web.ActionResult;
import ai.ezfox.cccloud.web.QueryResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * One Cloud Redstone card of a mainframe (a group, or the automatic "Redstone" card): an ordinary section, so the
 * dashboard, the layout editor, rules, scenes, the public API, the Cloud Tablet and the Cloud Display need no new
 * code. Owned by the mainframe (stable id from mainframe id + group), rebuilt by {@link RsDevices}; an action is
 * validated like any widget action and then applied to the device. Server thread only.
 */
public final class RsGroupSection implements DashboardSection {
    public static final String TEMPLATE = "redstone";

    private final UUID mainframeId;
    private final UUID sectionId;
    private final MainframeLink link;
    private RsCards.Card card;
    private final LinkedHashMap<String, Widget> widgets = new LinkedHashMap<>();
    private boolean online;
    /** The last snapshot sent to the hub (to send only value deltas when nothing else changed). */
    @Nullable JsonObject published;

    RsGroupSection(UUID mainframeId, MainframeLink link, RsCards.Card card) {
        this.mainframeId = mainframeId;
        this.sectionId = RsCards.sectionId(mainframeId, card.groupKey(), card.part());
        this.link = link;
        this.card = card;
        update(card, false);
    }

    void update(RsCards.Card card, boolean online) {
        this.card = card;
        this.online = online;
        widgets.clear();
        for (var widget : card.widgets()) widgets.put(widget.id(), widget);
    }

    RsCards.Card card() {
        return card;
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

    public UUID mainframeId() {
        return mainframeId;
    }

    public boolean isOnline() {
        return online;
    }

    JsonObject snapshot() {
        var json = new JsonObject();
        json.addProperty("uplinkId", sectionId.toString());
        json.addProperty("title", card.title());
        json.addProperty("online", online);
        var array = new JsonArray();
        for (var widget : widgets.values()) array.add(widget.toJson());
        json.add("widgets", array);
        json.addProperty("mode", "direct");
        json.addProperty("template", TEMPLATE);
        json.addProperty("source", "mainframe");
        json.addProperty("group", card.groupKey().isEmpty() ? "" : card.title().replaceAll(" \\(\\d+\\)$", ""));
        if (!card.blocked().isEmpty()) {
            // claim guard: the widgets of blocked devices show the claim instead of their value
            var blocked = new JsonObject();
            for (var e : card.blocked().entrySet()) {
                blocked.add(e.getKey(), ai.ezfox.cccloud.claims.ClaimGuard.json(new ai.ezfox.cccloud.claims.ClaimGuard.Block(new UUID(0, 0), e.getValue())));
            }
            json.add("blockedWidgets", blocked);
        }
        return json;
    }

    @Override
    public CompletableFuture<ActionResult> handleAction(String widgetId, JsonElement value, @Nullable Op op, String user, boolean pinVerified) {
        if (!online) return ActionResult.done("uplink_offline");
        var result = ActionValidator.validate(widgets.get(widgetId), value, op, pinVerified);
        if (!result.ok()) return ActionResult.done(result.error());
        if (result.op() != null || result.value() == null) return ActionResult.done("invalid_value");
        return ActionResult.done(RsDevices.action(this, widgetId, result.value(), user));
    }

    @Override
    public QueryResult handleQuery(String widgetId, String query) {
        return QueryResult.fail("unknown_widget");
    }
}
