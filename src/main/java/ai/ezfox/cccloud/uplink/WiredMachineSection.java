package ai.ezfox.cccloud.uplink;

import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.DashboardSection;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The ready-made card of a machine an uplink's computer reaches over a CC wired network (docs/SPEC.md "CC wired
 * networks"): the same {@link DeviceCard} an uplink attached to that machine would show, as a section of its own
 * hosted by the uplink. Published like a device card ({@code "mode":"direct"}, {@code "template":<card kind>}), so the
 * dashboard, the layout editor, rules, scenes, the public API and every permission check treat it exactly like one;
 * {@code "source":"wired"} and {@code "peripheral"} (the computer's name for it) say where it comes from. Server thread
 * only.
 */
public final class WiredMachineSection implements DashboardSection, ai.ezfox.cccloud.claims.ClaimGuard.Guarded {
    private final ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity host;
    private final UUID sectionId;
    private final MainframeLink link;
    private final String peripheral;
    private final BlockPos pos;
    private final DeviceCard card;
    private final LinkedHashMap<String, Widget> widgets = new LinkedHashMap<>();
    private final Set<String> dirty = new LinkedHashSet<>();
    private final List<String[]> alerts = new ArrayList<>();
    private String title;
    private boolean structureDirty = true;
    private boolean online;
    private int revision;
    private boolean failed;
    /** Claim guard: the claim the machine is in while that blocks the card, else null. */
    private ai.ezfox.cccloud.claims.ClaimGuard.@Nullable Block claimBlock;
    private boolean removed;

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

    WiredMachineSection(ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity host, String dimension, BlockPos pos, MainframeLink link, String peripheral,
                        DeviceCard card, String title) {
        this.host = host;
        this.sectionId = sectionId(host.getUplinkId(), dimension, pos);
        this.link = link;
        this.peripheral = peripheral;
        this.pos = pos.immutable();
        this.card = card;
        this.title = title;
        rebuild();
    }

    /** Stable id per uplink and machine position (layouts, rules and scenes survive restarts and renamed modems). */
    public static UUID sectionId(UUID uplinkId, String dimension, BlockPos pos) {
        return UUID.nameUUIDFromBytes(("cccloud:wired:" + uplinkId + ":" + dimension + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ())
            .getBytes(StandardCharsets.UTF_8));
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

    public DeviceCard card() {
        return card;
    }

    BlockPos pos() {
        return pos;
    }

    /** The computer's name for the machine ({@code minecraft:furnace_0}). */
    public String peripheral() {
        return peripheral;
    }

    boolean failed() {
        return failed;
    }

    void setTitle(String title) {
        if (this.title.equals(title)) return;
        this.title = title;
        structureDirty = true;
    }

    @Override
    public UUID sectionId() {
        return sectionId;
    }

    @Override
    public MainframeLink getLink() {
        return link;
    }

    @Override
    public String getTitle() {
        return title;
    }

    public boolean isOnline() {
        return online;
    }

    /** Once a second: reads the machine (not while the claim guard blocks it). */
    void refresh() {
        if (failed || claimBlock != null) return;
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

    /** Every tick: chart sampling and publishing. */
    void tick(long gameTime, boolean nowOnline) {
        if (nowOnline != online) {
            online = nowOnline;
            structureDirty = true;
        }
        if (claimBlock != null) {
            dirty.clear();
            alerts.clear();
        } else {
            for (var widget : widgets.values()) {
                if (widget.type() == WidgetType.CHART && widget.sample(gameTime)) dirty.add(widget.id());
            }
        }
        var mainframeId = link.id();
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

    public JsonObject snapshot() {
        var json = new JsonObject();
        json.addProperty("uplinkId", sectionId.toString());
        json.addProperty("title", title);
        json.addProperty("online", online);
        var array = new JsonArray();
        // claim guard: a blocked card shows the claim instead of its widgets and values
        if (claimBlock == null) for (var widget : widgets.values()) array.add(widget.toJson());
        json.add("widgets", array);
        json.addProperty("mode", "direct");
        json.addProperty("template", card.kind());
        json.addProperty("source", "wired");
        json.addProperty("peripheral", peripheral);
        if (claimBlock != null) json.add("blocked", ai.ezfox.cccloud.claims.ClaimGuard.json(claimBlock));
        return json;
    }

    /** A widget of the card (tests). */
    public @Nullable Widget widget(String id) {
        return widgets.get(id);
    }

    @Override
    public CompletableFuture<ActionResult> handleAction(String widgetId, JsonElement value, @Nullable Op op, String user, boolean pinVerified) {
        if (ai.ezfox.cccloud.claims.ClaimGuard.check(this) != null) return ActionResult.done(ai.ezfox.cccloud.claims.ClaimGuard.ERROR);
        if (!online) return ActionResult.done("uplink_offline");
        if (!DeviceCards.isValid(card)) return ActionResult.done("unavailable");
        var result = ActionValidator.validate(widgets.get(widgetId), value, op, pinVerified);
        if (!result.ok()) return ActionResult.done(result.error());
        if (result.op() != null) return ActionResult.done("invalid_value");
        return ActionResult.done(DeviceCards.action(card, widgetId, result.value(), user));
    }

    @Override
    public QueryResult handleQuery(String widgetId, String query) {
        if (ai.ezfox.cccloud.claims.ClaimGuard.check(this) != null) return QueryResult.fail(ai.ezfox.cccloud.claims.ClaimGuard.ERROR);
        var result = DeviceCards.query(card, widgetId, query);
        return result != null ? result : QueryResult.fail("unknown_widget");
    }

    // ---- claim guard (docs/SECURITY.md "Claims") -----------------------------------------------------------------

    @Override
    public net.minecraft.server.level.@Nullable ServerLevel guardLevel() {
        return host.getLevel() instanceof net.minecraft.server.level.ServerLevel level ? level : null;
    }

    /** The machine. */
    @Override
    public BlockPos guardPos() {
        return pos;
    }

    /** Like the uplink whose computer reaches it: who linked that uplink and the mainframe's owner. */
    @Override
    public List<UUID> guardOwners() {
        return ai.ezfox.cccloud.claims.ClaimGuard.owners(host.linkedBy(), link);
    }

    @Override
    public ai.ezfox.cccloud.claims.ClaimGuard.@Nullable Block claimBlock() {
        return claimBlock;
    }

    @Override
    public void applyClaimBlock(ai.ezfox.cccloud.claims.ClaimGuard.@Nullable Block block) {
        claimBlock = block;
        dirty.clear();
        alerts.clear();
        structureDirty = true;
        if (block == null) refresh(); // values at once, not a second later
    }

    @Override
    public String guardLabel() {
        return "wired machine card '" + title + "' of uplink " + host.getUplinkId().toString().substring(0, 8);
    }

    @Override
    public UUID guardMainframe() {
        return link.id();
    }

    @Override
    public boolean guardRemoved() {
        return removed || host.isRemoved();
    }

    /** No longer shown (WiredMachines). */
    void markRemoved() {
        removed = true;
        ai.ezfox.cccloud.claims.ClaimGuard.forget(this);
    }
}
