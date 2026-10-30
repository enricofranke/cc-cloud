package ai.ezfox.cccloud.scene;

import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.DashboardSection;
import ai.ezfox.cccloud.uplink.ActionValidator;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.GlobalPos;
import org.jspecify.annotations.Nullable;

/**
 * The Scenes card of a mainframe (docs/SPEC.md v0.10): one button per scene, routed like any section, so the
 * dashboard, the layout editor, the Cloud Tablet and the Cloud Display need no new code. A press is not run here: the
 * web layer calls {@link #press} with the presser's identity ({@code MinecraftBridge.dispatchAction}); calls without
 * one ({@link #handleAction}, e.g. a rule's widget action) are refused. Server thread only.
 */
public final class ScenesSection implements DashboardSection {
    public static final String KIND = "scenes";
    private final CloudMainframeBlockEntity mainframe;
    private final UUID sectionId;
    private final MainframeLink link;
    private final LinkedHashMap<String, Widget> widgets = new LinkedHashMap<>();
    private @Nullable JsonObject published;

    ScenesSection(CloudMainframeBlockEntity mainframe) {
        this.mainframe = mainframe;
        this.sectionId = SceneService.sectionId(mainframe.getMainframeId());
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
        return "Scenes";
    }

    /** Rebuilds the buttons from the scenes and publishes the section when it changed. */
    void update(List<Scene> scenes) {
        widgets.clear();
        for (var scene : scenes) {
            var opts = new HashMap<String, Object>();
            if (scene.color() != null) opts.put("color", scene.color());
            if (scene.icon() != null) opts.put("icon", scene.icon());
            if (scene.confirm()) opts.put("confirm", true);
            if (SceneService.needsPin(mainframe.getMainframeId(), scene)) opts.put("pin", true);
            try {
                widgets.put(scene.widgetId(), WidgetFactory.create(WidgetType.BUTTON, scene.widgetId(), scene.name(), opts));
            } catch (WidgetFactory.WidgetException e) {
                // validated by the codec; cannot happen
            }
        }
        var snapshot = snapshot();
        if (snapshot.equals(published)) return;
        published = snapshot;
        WebHub.INSTANCE.publishUplink(mainframe.getMainframeId(), snapshot);
    }

    JsonObject snapshot() {
        var json = new JsonObject();
        json.addProperty("uplinkId", sectionId.toString());
        json.addProperty("title", getTitle());
        json.addProperty("online", mainframe.status() == CloudStatus.ONLINE && mainframe.isEnabled());
        var array = new JsonArray();
        for (var widget : widgets.values()) array.add(widget.toJson());
        json.add("widgets", array);
        json.addProperty("mode", "direct");
        json.addProperty("template", KIND);
        json.addProperty("source", "mainframe");
        return json;
    }

    void removeFromDashboard() {
        published = null;
        WebHub.INSTANCE.publishUplinkRemoved(mainframe.getMainframeId(), sectionId.toString());
    }

    /**
     * A press of a scene button by a known caller (dashboard, tablet, display, API). The button itself is validated
     * like any button (value {@code true}, its PIN flag); the steps are checked while they run.
     */
    public ActionResult press(String widgetId, JsonElement value, SceneService.Caller caller, boolean pinVerified) {
        var result = ActionValidator.validate(widgets.get(widgetId), value, null, pinVerified);
        if (!result.ok()) return ActionResult.fail(result.error());
        return ActionResult.of(SceneService.start(mainframe, widgetId.substring(SceneService.WIDGET_PREFIX.length()), caller, pinVerified));
    }

    @Override
    public CompletableFuture<ActionResult> handleAction(String widgetId, JsonElement value, @Nullable Op op, String user, boolean pinVerified) {
        return ActionResult.done("unsupported"); // scenes always run as a known player (press)
    }

    @Override
    public QueryResult handleQuery(String widgetId, String query) {
        return QueryResult.fail("unknown_widget");
    }
}
