package ai.ezfox.cccloud.bridge;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.DashboardSection;
import ai.ezfox.cccloud.uplink.ActionValidator;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.Op;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.web.ActionResult;
import ai.ezfox.cccloud.web.QueryResult;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A linked ME/RS Cloud Bridge as its own dashboard section: the bridge is bound with the Cloud Link Card (owner/trusted,
 * unlimited range, any dimension) and publishes the ready-made Storage section itself, with a stable section id.
 * Same widgets as direct mode ({@link StorageTemplate}). Owned by the bridge block entity; server thread only.
 */
public final class BridgeSection implements DashboardSection {
    private static final int STATUS_INTERVAL = 10;
    private static final int VALUE_INTERVAL = 20;

    private final BridgeHost host;
    private UUID sectionId = UUID.randomUUID();
    private @Nullable MainframeLink link;
    private CloudStatus status = CloudStatus.OFFLINE;
    private final LinkedHashMap<String, Widget> widgets = new LinkedHashMap<>();
    private boolean structureDirty = true;
    private final Set<String> dirtyValues = new LinkedHashSet<>();
    private final List<String[]> pendingAlerts = new ArrayList<>();
    private int alertSeq = -1;
    private boolean registered;
    private boolean broken;
    private int ticks;
    private boolean legacyLink;

    public BridgeSection(BridgeHost host) {
        this.host = host;
        for (var widget : StorageTemplate.widgets(WidgetFactoryMaxCraft.get())) widgets.put(widget.id(), widget);
    }

    /** Indirection so the template's craft limit follows the config once it is loaded. */
    private static final class WidgetFactoryMaxCraft {
        static int get() {
            try {
                return BridgeCore.maxCraftAmount();
            } catch (RuntimeException e) {
                return 4096;
            }
        }
    }

    @Override
    public UUID sectionId() {
        return sectionId;
    }

    @Override
    public @Nullable MainframeLink getLink() {
        return link;
    }

    public BridgeHost host() {
        return host;
    }

    /** Linked before v0.4 (no "net_v" marker). */
    public boolean isLegacyLink() {
        return legacyLink;
    }

    /** Connected to its mainframe over the network (or by the legacy grace). */
    public boolean hasRoute() {
        var route = ai.ezfox.cccloud.network.NetworkService.route(sectionId);
        return route != null && route.connected();
    }

    public boolean isLinked() {
        return link != null;
    }

    public CloudStatus status() {
        return status;
    }

    @Override
    public String getTitle() {
        return StorageTemplate.title(host.bridgeCore().kind());
    }

    public int widgetCount() {
        return widgets.size();
    }

    // ---- persistence -------------------------------------------------------------------------------------------------

    public void load(ValueInput input) {
        input.read("section_id", UUIDUtil.STRING_CODEC).ifPresent(id -> sectionId = id);
        link = input.read("link", MainframeLink.CODEC).orElse(null);
        legacyLink = link != null && (input.getIntOr("net_v", 0) < 1 || input.getBooleanOr("legacy_link", false));
        structureDirty = true;
    }

    public void save(ValueOutput output) {
        output.store("section_id", UUIDUtil.STRING_CODEC, sectionId);
        output.storeNullable("link", MainframeLink.CODEC, link);
        output.putInt("net_v", 1);
        output.putBoolean("legacy_link", legacyLink);
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------------

    public void tick(ServerLevel level) {
        if (!registered) {
            if (!CloudNetwork.registerSection(this)) {
                // A copy of this block (e.g. structure placement) uses the same id: become a new section.
                sectionId = UUID.randomUUID();
                link = null;
                host.asBlockEntity().setChanged();
                CloudNetwork.registerSection(this);
            }
            registered = true;
            ai.ezfox.cccloud.network.NetworkService.markDirty();
        }
        if (ticks % STATUS_INTERVAL == 0) updateStatus(level);
        if (ticks % VALUE_INTERVAL == 0 && link != null) refreshValues();
        ticks++;
        publish();
    }

    /** Chunk unload or block removal. */
    public void removed() {
        if (!registered) return;
        registered = false;
        CloudNetwork.unregisterSection(this);
        ai.ezfox.cccloud.network.NetworkService.markDirty();
        status = CloudStatus.OFFLINE;
        if (!broken && link != null) {
            var cached = ai.ezfox.cccloud.network.SectionCache.unloaded(snapshot());
            WebHub.INSTANCE.publishUplink(link.id(), cached);
            var server = CloudNetwork.server();
            if (server != null) ai.ezfox.cccloud.network.SectionCache.get(server).store(link.id(), cached);
        }
    }

    /** The block is being broken: remove the section from the dashboard. */
    public void broken() {
        broken = true;
        if (link != null) unlinkFrom(link);
    }

    private void unlinkFrom(MainframeLink old) {
        var mainframe = CloudNetwork.mainframe(old.id());
        if (mainframe != null) mainframe.unlinkUplink(sectionId);
        else WebHub.INSTANCE.publishUplinkRemoved(old.id(), sectionId.toString());
    }

    private void updateStatus(ServerLevel level) {
        var newStatus = CloudStatus.OFFLINE;
        if (link != null) {
            var mainframe = CloudNetwork.mainframe(link.id());
            if (mainframe != null) {
                mainframe.linkUplink(sectionId, GlobalPos.of(level.dimension(), host.asBlockEntity().getBlockPos()));
                var route = ai.ezfox.cccloud.network.NetworkService.route(sectionId);
                newStatus = route == null ? CloudStatus.CONNECTING : route.connected() ? mainframe.status() : CloudStatus.OFFLINE;
            }
        }
        if (newStatus != status) {
            status = newStatus;
            structureDirty = true;
        }
    }

    // ---- binding -----------------------------------------------------------------------------------------------------

    /** Link Card: binds the bridge to a mainframe (owner/trusted only; unlimited range, any dimension). */
    public void bind(MainframeLink newLink, Player player) {
        var be = host.asBlockEntity();
        if (!(be.getLevel() instanceof ServerLevel level)) return;
        var mainframe = CloudNetwork.mainframe(newLink.id());
        if (mainframe == null || mainframe.getLevel() == null || !mainframe.getBlockPos().equals(newLink.pos().pos())) {
            player.sendSystemMessage(Component.translatable("cccloud.uplink.mainframe_missing").withStyle(ChatFormatting.RED));
            return;
        }
        if (!mainframe.canManage(player)) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.not_owner").withStyle(ChatFormatting.RED));
            return;
        }
        var error = ai.ezfox.cccloud.network.NetworkService.checkLink(mainframe, level, be.getBlockPos(),
            ai.ezfox.cccloud.network.graph.NetworkGraph.DeviceType.BRIDGE, sectionId.toString(), host.cloudWired());
        if (error != null) {
            player.sendSystemMessage(error.copy().withStyle(ChatFormatting.RED));
            return;
        }
        if (link != null && !link.id().equals(newLink.id())) unlinkFrom(link);
        link = newLink;
        legacyLink = false;
        ai.ezfox.cccloud.network.NetworkService.markDirty();
        mainframe.linkUplink(sectionId, GlobalPos.of(level.dimension(), be.getBlockPos()));
        structureDirty = true;
        be.setChanged();
        updateStatus(level);
        refreshValues();
        CCCloud.LOGGER.info("[cccloud] {} bound the {} bridge at {} to {}", player.getGameProfile().name(), host.bridgeCore().kind(), be.getBlockPos(),
            mainframe.getDisplayName());
        player.sendSystemMessage(Component.translatable("cccloud.bridge.bound", newLink.describe()).withStyle(ChatFormatting.GREEN));
    }

    public void unlink(String by) {
        if (link == null) return;
        var old = link;
        unlinkFrom(old);
        link = null;
        legacyLink = false;
        ai.ezfox.cccloud.network.NetworkService.markDirty();
        status = CloudStatus.OFFLINE;
        host.asBlockEntity().setChanged();
        CCCloud.LOGGER.info("[cccloud] {} unlinked the {} bridge at {} from {}", by, host.bridgeCore().kind(), host.asBlockEntity().getBlockPos(), old.describe());
    }

    // ---- content -----------------------------------------------------------------------------------------------------

    private void setValue(String id, JsonElement value) {
        var widget = widgets.get(id);
        if (widget == null || widget.value().equals(value)) return;
        widget.setValue(value);
        dirtyValues.add(id);
    }

    private void refreshValues() {
        var core = host.bridgeCore();
        var stats = core.stats();
        for (var id : List.of("storage", "energy", "items", "types", "usage")) {
            Object v = stats.get(id);
            setValue(id, v instanceof Number n ? Json.number(n.doubleValue()) : v instanceof String s ? new JsonPrimitive(s)
                : id.equals("storage") || id.equals("energy") ? JsonNull.INSTANCE : new JsonPrimitive("–"));
        }
        setValue(StorageTemplate.WATCH, core.watchRows());
        setValue(StorageTemplate.JOBS, core.jobRows(BridgeCore.JOB_ROWS));
        if (alertSeq < 0 || alertSeq > core.alertSeq()) {
            alertSeq = core.alertSeq();
        } else {
            for (var alert : core.alertsSince(alertSeq)) if (pendingAlerts.size() < 16) pendingAlerts.add(new String[]{alert.text(), alert.level()});
            alertSeq = core.alertSeq();
        }
    }

    public JsonObject snapshot() {
        var json = new JsonObject();
        json.addProperty("uplinkId", sectionId.toString());
        json.addProperty("title", getTitle());
        json.addProperty("online", status == CloudStatus.ONLINE);
        var array = new JsonArray();
        for (var widget : widgets.values()) array.add(widget.toJson());
        json.add("widgets", array);
        json.addProperty("mode", "direct");
        json.addProperty("template", "storage");
        json.addProperty("bridge", host.bridgeCore().kind());
        json.addProperty("source", "bridge");
        return json;
    }

    private void publish() {
        if (link == null) {
            dirtyValues.clear();
            pendingAlerts.clear();
            return;
        }
        UUID mainframeId = link.id();
        String id = sectionId.toString();
        if (structureDirty) {
            structureDirty = false;
            dirtyValues.clear();
            WebHub.INSTANCE.publishUplink(mainframeId, snapshot());
        } else if (!dirtyValues.isEmpty()) {
            for (var widgetId : dirtyValues) {
                var widget = widgets.get(widgetId);
                if (widget != null) WebHub.INSTANCE.publishValue(mainframeId, id, widgetId, widget.value());
            }
            dirtyValues.clear();
        }
        if (!pendingAlerts.isEmpty() && status == CloudStatus.ONLINE) {
            for (var alert : pendingAlerts) WebHub.INSTANCE.publishAlert(mainframeId, id, alert[0], alert[1]);
        }
        pendingAlerts.clear();
    }

    // ---- dashboard actions -------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<ActionResult> handleAction(String widgetId, JsonElement value, @Nullable Op op, String user, boolean pinVerified) {
        if (status != CloudStatus.ONLINE) return ActionResult.done("uplink_offline");
        var widget = widgets.get(widgetId);
        var result = ActionValidator.validate(widget, value, op, pinVerified);
        if (!result.ok()) return ActionResult.done(result.error());
        var core = host.bridgeCore();
        if (result.op() instanceof Op.Craft craft) {
            var ticket = core.craft(craft.name(), craft.count(), user);
            if (ticket.error() == null) setValue(StorageTemplate.JOBS, core.jobRows(BridgeCore.JOB_ROWS));
            return ticket.result();
        }
        if (result.op() == null) return ActionResult.done("invalid_value");
        String error = core.watchOp(result.op());
        if (error == null) setValue(StorageTemplate.WATCH, core.watchRows());
        return ActionResult.done(error);
    }

    @Override
    public QueryResult handleQuery(String widgetId, String query) {
        if (status != CloudStatus.ONLINE) return QueryResult.fail("uplink_offline");
        var widget = widgets.get(widgetId);
        if (widget == null || widget.type() != WidgetType.ITEM_SEARCH) return QueryResult.fail("unknown_widget");
        var core = host.bridgeCore();
        if (!core.isConnected()) return QueryResult.fail("offline");
        if (!core.trySearch()) return QueryResult.fail("rate_limited");
        var page = core.index().search(query, Limits.QUERY_ROWS);
        var rows = new JsonArray(page.rows().size());
        for (var row : page.rows()) rows.add(row.toJson());
        return new QueryResult(rows, page.truncated(), null);
    }

    /** Test/diagnostics: the current values. */
    public Map<String, JsonElement> values() {
        var map = new LinkedHashMap<String, JsonElement>();
        for (var widget : widgets.values()) map.put(widget.id(), widget.value());
        return map;
    }
}
