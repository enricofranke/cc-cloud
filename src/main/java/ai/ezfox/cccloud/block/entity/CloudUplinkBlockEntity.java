package ai.ezfox.cccloud.block.entity;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.peripheral.UplinkPeripheral;
import ai.ezfox.cccloud.bridge.BridgeCore;
import ai.ezfox.cccloud.compat.cc.CcComputers;
import ai.ezfox.cccloud.uplink.ComputerCard;
import ai.ezfox.cccloud.bridge.BridgeHost;
import ai.ezfox.cccloud.bridge.StorageTemplate;
import ai.ezfox.cccloud.uplink.ActionValidator;
import ai.ezfox.cccloud.uplink.BridgeRef;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.Op;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.web.ActionResult;
import ai.ezfox.cccloud.web.QueryResult;
import ai.ezfox.cccloud.web.TokenBucket;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dan200.computercraft.api.peripheral.IComputerAccess;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Block entity of both uplink tiers. Holds the dashboard section (title + ordered widgets) that the attached computers
 * build through the {@code cloud_uplink} peripheral.
 * <p>
 * Threading: the Lua methods run on computer threads and only mutate the widget model under {@code synchronized(this)}
 * and mark it dirty. {@link #serverTick} (server thread) pushes dirty state into the {@link WebHub}.
 */
public class CloudUplinkBlockEntity extends BlockEntity implements ai.ezfox.cccloud.network.DashboardSection {
    /** Max pending cloud_action events per computer (token bucket, refilled at 16/s). */
    public static final int MAX_PENDING_EVENTS = 64;
    private static final int STATUS_INTERVAL = 10;

    private UUID uplinkId = UUID.randomUUID();
    private @Nullable MainframeLink link;
    private String title = "";
    private final LinkedHashMap<String, Widget> widgets = new LinkedHashMap<>();
    /** Lua API v2: cloud.page() / cloud.group() declarations. */
    private final LinkedHashMap<String, JsonObject> pages = new LinkedHashMap<>();
    private final LinkedHashMap<String, JsonObject> groups = new LinkedHashMap<>();
    /** Direct mode: attached to a Cloud Bridge, the section is the ready-made Storage template. */
    private boolean template;
    private String templateKind = "";
    /** In direct mode on a bridge that is linked itself: the bridge's own section is shown, this one is hidden. */
    private volatile boolean suppressed;
    /** Auto mode: attached to a computer whose programs defined no widgets → the automatic Computer card. */
    private boolean auto;
    /** Set when the last computer detached (shutdown/reboot): Lua widgets are dropped so the card comes back. */
    private volatile boolean computerDetached;
    /** Linked before v0.4 (no "net_v" marker): keeps working out of range while legacyRangeGrace is on. */
    private boolean legacyLink;
    /** "no_route", "legacy_out_of_range" or null (for the screen). */
    private volatile @Nullable String routeState;
    /** v0.5 device card (compat/card): direct mode on a supported machine. {@code deviceKind} is saved; both guarded by this. */
    private ai.ezfox.cccloud.compat.card.@Nullable DeviceCard deviceCard;
    private String deviceKind = "";
    private int deviceRevision;
    /** Last forwarded watchlist alert per bridge (key = bridge position). */
    private final Map<String, Integer> bridgeAlertSeq = new java.util.HashMap<>();

    // Dirty tracking (guarded by this)
    private boolean structureDirty = true;
    private final Set<String> dirtyValues = new LinkedHashSet<>();
    private final List<String[]> pendingAlerts = new ArrayList<>();
    private long lastAlertNanos;

    private final Map<IComputerAccess, TokenBucket> computers = new ConcurrentHashMap<>();
    private final UplinkPeripheral peripheral = new UplinkPeripheral(this);
    private volatile CloudStatus status = CloudStatus.OFFLINE;
    private boolean registered;
    private final SpinAnimation clientAnimation = new SpinAnimation();
    private boolean broken;
    private int tickCounter;
    private long droppedEvents;

    public CloudUplinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.UPLINK_BE.get(), pos, state);
    }

    public UUID getUplinkId() {
        return uplinkId;
    }

    @Override
    public UUID sectionId() {
        return uplinkId;
    }

    @Override
    public @Nullable MainframeLink getLink() {
        return link;
    }

    public UplinkPeripheral peripheral() {
        return peripheral;
    }

    public boolean isAdvanced() {
        return getBlockState().getBlock() instanceof CloudUplinkBlock b && b.isAdvanced();
    }

    public CloudStatus status() {
        return status;
    }

    public boolean isOnline() {
        return status == CloudStatus.ONLINE;
    }

    // ---- persistence -------------------------------------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.read("uplink_id", UUIDUtil.STRING_CODEC).ifPresent(id -> uplinkId = id);
        link = input.read("link", MainframeLink.CODEC).orElse(null);
        legacyLink = link != null && (input.getIntOr("net_v", 0) < 1 || input.getBooleanOr("legacy_link", false));
        synchronized (this) {
            title = input.getStringOr("title", "");
            template = input.getBooleanOr("template", false);
            auto = input.getBooleanOr("auto", false);
            templateKind = input.getStringOr("template_kind", "");
            deviceKind = input.getStringOr("device_kind", "");
            readDeclarations(input.getStringOr("pages", "[]"), pages);
            readDeclarations(input.getStringOr("groups", "[]"), groups);
            widgets.clear();
            try {
                var array = JsonParser.parseString(input.getStringOr("widgets", "[]")).getAsJsonArray();
                for (var element : array) {
                    var widget = element.isJsonObject() ? Widget.fromJson(element.getAsJsonObject()) : null;
                    if (widget != null) widgets.put(widget.id(), widget);
                }
            } catch (RuntimeException e) {
                CCCloud.LOGGER.warn("[cccloud] Could not read widgets of uplink at {}", getBlockPos(), e);
            }
            structureDirty = true;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("uplink_id", UUIDUtil.STRING_CODEC, uplinkId);
        output.storeNullable("link", MainframeLink.CODEC, link);
        output.putInt("net_v", 1);
        output.putBoolean("legacy_link", legacyLink);
        synchronized (this) {
            output.putString("title", title);
            output.putBoolean("template", template);
            output.putBoolean("auto", auto);
            output.putString("template_kind", templateKind);
            output.putString("device_kind", deviceKind);
            output.putString("pages", Json.GSON.toJson(declarations(pages)));
            output.putString("groups", Json.GSON.toJson(declarations(groups)));
            var array = new JsonArray();
            for (var widget : widgets.values()) array.add(widget.toSaveJson());
            output.putString("widgets", Json.GSON.toJson(array));
        }
    }

    private static void readDeclarations(String json, Map<String, JsonObject> into) {
        into.clear();
        try {
            for (var element : JsonParser.parseString(json).getAsJsonArray()) {
                var obj = element.getAsJsonObject();
                String id = obj.get("id").getAsString();
                if (Json.isValidId(id)) into.put(id, obj);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static JsonArray declarations(Map<String, JsonObject> map) {
        var array = new JsonArray(map.size());
        for (var obj : map.values()) array.add(obj.deepCopy());
        return array;
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide()) return;
        if (!CloudNetwork.registerUplink(this)) {
            uplinkId = UUID.randomUUID();
            link = null;
            setChanged();
            CloudNetwork.registerUplink(this);
        }
        registered = true;
        tickCounter = 0;
        ai.ezfox.cccloud.network.NetworkService.markDirty();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!registered) return;
        registered = false;
        CloudNetwork.unregisterUplink(this);
        ai.ezfox.cccloud.network.NetworkService.markDirty();
        status = CloudStatus.OFFLINE;
        // Chunk unload: keep the section on the dashboard but show it as offline.
        if (!broken && !suppressed && link != null) {
            var cached = ai.ezfox.cccloud.network.SectionCache.unloaded(snapshot());
            WebHub.INSTANCE.publishUplink(link.id(), cached);
            if (level instanceof ServerLevel serverLevel) ai.ezfox.cccloud.network.SectionCache.get(serverLevel.getServer()).store(link.id(), cached);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        broken = true;
        if (link != null) unlinkFrom(link);
    }

    private void unlinkFrom(MainframeLink old) {
        var mainframe = CloudNetwork.mainframe(old.id());
        if (mainframe != null) {
            mainframe.unlinkUplink(uplinkId);
        } else {
            WebHub.INSTANCE.publishUplinkRemoved(old.id(), uplinkId.toString());
        }
    }

    // ---- server tick -------------------------------------------------------------------------------------------------

    public void serverTick(ServerLevel level) {
        long cost = ai.ezfox.cccloud.network.TickCost.start();
        try {
            tick(level);
        } finally {
            ai.ezfox.cccloud.network.TickCost.end(cost);
        }
    }

    private void tick(ServerLevel level) {
        if (tickCounter % STATUS_INTERVAL == 0) updateStatus(level);
        if (tickCounter % BRIDGE_INTERVAL == 0) updateBridges(level);
        if (tickCounter % STATUS_INTERVAL == 5) updateComputer(level);
        if (tickCounter % BRIDGE_INTERVAL == 15) updateDeviceCard(level);
        tickCounter++;
        sampleCharts(level.getGameTime());
        if (link == null || suppressed) {
            synchronized (this) {
                dirtyValues.clear();
                pendingAlerts.clear();
            }
            return;
        }

        boolean changed = false;
        JsonObject snapshot = null;
        List<Map.Entry<String, JsonElement>> values = null;
        List<String[]> alerts = null;
        synchronized (this) {
            if (structureDirty) {
                structureDirty = false;
                dirtyValues.clear();
                snapshot = snapshot();
                changed = true;
            } else if (!dirtyValues.isEmpty()) {
                values = new ArrayList<>(dirtyValues.size());
                for (var id : dirtyValues) {
                    var widget = widgets.get(id);
                    if (widget != null) values.add(Map.entry(id, widget.value()));
                }
                dirtyValues.clear();
                changed = true;
            }
            if (!pendingAlerts.isEmpty()) {
                alerts = new ArrayList<>(pendingAlerts);
                pendingAlerts.clear();
            }
        }

        UUID mainframeId = link.id();
        String id = uplinkId.toString();
        if (snapshot != null) WebHub.INSTANCE.publishUplink(mainframeId, snapshot);
        if (values != null) {
            for (var entry : values) WebHub.INSTANCE.publishValue(mainframeId, id, entry.getKey(), entry.getValue());
        }
        if (alerts != null && isOnline()) {
            for (var alert : alerts) WebHub.INSTANCE.publishAlert(mainframeId, id, alert[0], alert[1]);
        }
        if (changed) setChanged();
    }

    private static final int BRIDGE_INTERVAL = 20;

    private synchronized void sampleCharts(long gameTime) {
        for (var widget : widgets.values()) {
            if (widget.type() == WidgetType.CHART && widget.sample(gameTime)) dirtyValues.add(widget.id());
        }
    }

    /** The bridge this uplink is attached to (direct mode), or null. */
    private @Nullable BridgeHost attachedBridge(ServerLevel level) {
        var pos = getBlockPos().relative(getBlockState().getValue(CloudUplinkBlock.FACING));
        if (!level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof BridgeHost host ? host : null;
    }

    private @Nullable BridgeHost resolveBridge(@Nullable BridgeRef ref) {
        if (ref == null || !(level instanceof ServerLevel serverLevel)) return null;
        var id = Identifier.tryParse(ref.dimension());
        if (id == null) return null;
        var target = serverLevel.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        var pos = new BlockPos(ref.x(), ref.y(), ref.z());
        if (target == null || !target.isLoaded(pos)) return null;
        return target.getBlockEntity(pos) instanceof BridgeHost host ? host : null;
    }

    /** The storage (bridge) of a direct-mode Storage card, or null (v0.5 rules: item triggers and craft actions). */
    public @Nullable BridgeHost storageBridge() {
        return template && level instanceof ServerLevel serverLevel ? attachedBridge(serverLevel) : null;
    }

    /** Bridge serving a widget: the attached one in direct mode, otherwise the one the Lua widget was bound to. */
    private @Nullable BridgeHost bridgeFor(Widget widget) {
        if (template) return level instanceof ServerLevel serverLevel ? attachedBridge(serverLevel) : null;
        return resolveBridge(widget.bridge());
    }

    /** Enters/leaves direct mode and refreshes all bridge-fed values (every second). */
    private void updateBridges(ServerLevel level) {
        var attached = attachedBridge(level);
        boolean suppress = attached != null && attached.bridgeSection().isLinked();
        if (suppress != suppressed) {
            suppressed = suppress;
            if (suppress && link != null) WebHub.INSTANCE.publishUplinkRemoved(link.id(), uplinkId.toString());
            synchronized (this) {
                structureDirty = true;
            }
        }
        if (attached != null && !template) enterTemplate(attached.bridgeCore().kind());
        else if (attached == null && template) leaveTemplate();
        else if (attached != null && !templateKind.equals(attached.bridgeCore().kind())) enterTemplate(attached.bridgeCore().kind());

        List<Widget> bridgeWidgets = new ArrayList<>();
        synchronized (this) {
            for (var widget : widgets.values()) if (widget.type().isBridgeBound()) bridgeWidgets.add(widget);
        }
        if (template && attached != null) {
            var stats = attached.bridgeCore().stats();
            synchronized (this) {
                for (var id : List.of("storage", "energy", "items", "types", "usage")) {
                    Object v = stats.get(id);
                    JsonElement json = v instanceof Number n ? Json.number(n.doubleValue()) : v instanceof String str ? new com.google.gson.JsonPrimitive(str)
                        : id.equals("storage") || id.equals("energy") ? com.google.gson.JsonNull.INSTANCE : new com.google.gson.JsonPrimitive("–");
                    setValue(id, json);
                }
            }
        }
        var forwarded = new java.util.HashSet<BridgeHost>();
        for (var widget : bridgeWidgets) {
            var host = bridgeFor(widget);
            if (host == null) continue;
            var core = host.bridgeCore();
            switch (widget.type()) {
                case WATCHLIST -> setValue(widget.id(), core.watchRows());
                case JOBS -> setValue(widget.id(), core.jobRows(BridgeCore.JOB_ROWS));
                default -> {
                }
            }
            if (widget.type() == WidgetType.WATCHLIST && forwarded.add(host)) forwardAlerts(host);
        }
    }

    private void forwardAlerts(BridgeHost host) {
        var pos = host.asBlockEntity().getBlockPos();
        String key = pos.asLong() + "";
        var core = host.bridgeCore();
        Integer last = bridgeAlertSeq.get(key);
        if (last == null || last > core.alertSeq()) {
            bridgeAlertSeq.put(key, core.alertSeq());
            return;
        }
        for (var alert : core.alertsSince(last)) {
            synchronized (this) {
                if (pendingAlerts.size() < 16) pendingAlerts.add(new String[]{alert.text(), alert.level()});
            }
        }
        bridgeAlertSeq.put(key, core.alertSeq());
    }

    private synchronized void enterTemplate(String kind) {
        template = true;
        auto = false;
        deviceCard = null;
        deviceKind = "";
        templateKind = kind;
        title = StorageTemplate.title(kind);
        widgets.clear();
        pages.clear();
        groups.clear();
        for (var widget : StorageTemplate.widgets(BridgeCore.maxCraftAmount())) widgets.put(widget.id(), widget);
        structureDirty = true;
        setChanged();
    }

    private synchronized void leaveTemplate() {
        template = false;
        templateKind = "";
        title = "";
        widgets.clear();
        structureDirty = true;
        setChanged();
    }

    public synchronized boolean isTemplate() {
        return template;
    }

    /** True while a linked bridge below shows its own section instead of this uplink. */
    public boolean isSuppressed() {
        return suppressed;
    }

    public synchronized boolean isAuto() {
        return auto;
    }

    /** "direct" (Storage template or device card), "auto" (Computer card) or "lua". */
    public synchronized String mode() {
        return template || !deviceKind.isEmpty() ? "direct" : auto ? "auto" : "lua";
    }

    // ---- device cards (v0.5, compat/card) ----------------------------------------------------------------------------

    /** The device card's kind ("energy", "powah_reactor", ...) or "" (tests, in-game screen). */
    public synchronized String deviceKind() {
        return deviceKind;
    }

    /**
     * Shows the ready-made card of the machine the uplink sticks to, once per second. Like the Computer card it only
     * takes a free section: a Lua program's widgets replace it ({@link #beginLua}) and it comes back after clear().
     */
    private void updateDeviceCard(ServerLevel level) {
        ai.ezfox.cccloud.compat.card.DeviceCard card;
        boolean free;
        synchronized (this) {
            card = deviceCard;
            free = !deviceKind.isEmpty() || (!template && !auto && widgets.isEmpty() && pages.isEmpty() && groups.isEmpty());
        }
        if (card != null && !ai.ezfox.cccloud.compat.card.DeviceCards.isValid(card)) card = null;
        if (card == null && free && ai.ezfox.cccloud.compat.Integrations.deviceCardsEnabled()) {
            var facing = getBlockState().getValue(CloudUplinkBlock.FACING);
            card = ai.ezfox.cccloud.compat.card.DeviceCards.detect(level, getBlockPos().relative(facing), facing.getOpposite());
        }
        synchronized (this) {
            // Re-check under the lock: a Lua program may have taken the section in the meantime.
            boolean stillFree = !deviceKind.isEmpty() || (!template && !auto && widgets.isEmpty() && pages.isEmpty() && groups.isEmpty());
            if (!stillFree) card = null;
            else if (deviceCard != card || (card == null && !deviceKind.isEmpty())) showDeviceCard(card);
        }
        if (card == null) return;
        int revision = ai.ezfox.cccloud.compat.card.DeviceCards.revision(card);
        boolean ok = revision >= 0 && ai.ezfox.cccloud.compat.card.DeviceCards.refresh(card, deviceOutput);
        synchronized (this) {
            if (deviceCard != card) return;
            if (!ok) showDeviceCard(null);
            else if (revision != deviceRevision) {
                showDeviceCard(card); // the machine's configuration changed the widget set
                deviceRevision = revision;
                ai.ezfox.cccloud.compat.card.DeviceCards.refresh(card, deviceOutput);
            }
        }
    }

    /** Replaces the section by the card (null: back to an empty section). Keeps the history of charts with the same id. */
    private void showDeviceCard(ai.ezfox.cccloud.compat.card.@Nullable DeviceCard card) {
        if (card == null) {
            if (deviceKind.isEmpty() && deviceCard == null) return;
            deviceCard = null;
            deviceKind = "";
            title = "";
            widgets.clear();
        } else {
            var cardWidgets = ai.ezfox.cccloud.compat.card.DeviceCards.widgets(card);
            if (cardWidgets == null) {
                showDeviceCard(null);
                return;
            }
            var old = new LinkedHashMap<>(widgets);
            boolean restore = card.kind().equals(deviceKind);
            deviceCard = card;
            deviceKind = card.kind();
            deviceRevision = ai.ezfox.cccloud.compat.card.DeviceCards.revision(card);
            String t = ai.ezfox.cccloud.compat.card.DeviceCards.title(card);
            title = t.length() > Limits.TITLE ? t.substring(0, Limits.TITLE) : t;
            widgets.clear();
            pages.clear();
            groups.clear();
            for (var widget : cardWidgets) {
                var previous = restore ? old.get(widget.id()) : null;
                if (previous != null && previous.type() == WidgetType.CHART && widget.type() == WidgetType.CHART) {
                    var history = previous.toJson().get("history");
                    if (history != null) for (var point : history.getAsJsonArray()) widget.push(point.getAsDouble());
                }
                widgets.put(widget.id(), widget);
            }
        }
        structureDirty = true;
        setChanged();
    }

    private final ai.ezfox.cccloud.compat.card.DeviceCard.CardOutput deviceOutput = new ai.ezfox.cccloud.compat.card.DeviceCard.CardOutput() {
        @Override
        public void value(String widgetId, JsonElement value) {
            setValue(widgetId, value);
        }

        @Override
        public void alert(String text, String level) {
            synchronized (CloudUplinkBlockEntity.this) {
                if (pendingAlerts.size() < 16) pendingAlerts.add(new String[]{text.length() > Limits.ALERT_TEXT ? text.substring(0, Limits.ALERT_TEXT) : text, level});
            }
        }
    };

    // ---- auto mode (Computer card) -------------------------------------------------------------------------------

    private CcComputers.@Nullable Handle attachedComputer(ServerLevel level) {
        if (!CloudConfig.get(CloudConfig.AUTO_COMPUTER_CARD)) return null;
        return CcComputers.find(level, getBlockPos().relative(getBlockState().getValue(CloudUplinkBlock.FACING)));
    }

    /** Enters/leaves auto mode and refreshes the card (every 10 ticks). */
    private void updateComputer(ServerLevel level) {
        if (isTemplate()) return;
        var computer = attachedComputer(level);
        boolean detached = computerDetached;
        computerDetached = false;
        synchronized (this) {
            if (computer == null) {
                if (auto) leaveAuto();
                return;
            }
            // The program that defined the widgets is gone (shutdown/reboot): show the card again.
            if (detached && !auto && computers.isEmpty()) {
                widgets.clear();
                pages.clear();
                groups.clear();
                structureDirty = true;
            }
            if (!auto && widgets.isEmpty() && pages.isEmpty() && groups.isEmpty()) enterAuto();
            if (!auto) return;
        }
        refreshAuto(computer);
    }

    private synchronized void enterAuto() {
        auto = true;
        widgets.clear();
        pages.clear();
        groups.clear();
        for (var widget : ComputerCard.widgets()) widgets.put(widget.id(), widget);
        structureDirty = true;
        setChanged();
    }

    private synchronized void leaveAuto() {
        auto = false;
        widgets.clear();
        groups.clear();
        title = "";
        structureDirty = true;
        setChanged();
    }

    private void refreshAuto(CcComputers.Handle computer) {
        String newTitle = computer.title();
        boolean on = computer.isOn();
        int[] out = new int[ComputerCard.SIDES.length];
        int[] in = new int[ComputerCard.SIDES.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = computer.output(ComputerCard.SIDES[i]);
            in[i] = computer.input(ComputerCard.SIDES[i]);
        }
        synchronized (this) {
            if (!auto) return;
            if (!title.equals(newTitle)) {
                title = newTitle.length() > Limits.TITLE ? newTitle.substring(0, Limits.TITLE) : newTitle;
                structureDirty = true;
            }
            setValue(ComputerCard.POWER, new com.google.gson.JsonPrimitive(on));
            setValue(ComputerCard.REDSTONE, ComputerCard.sides(out, in));
        }
    }

    /**
     * Called before a Lua program changes the section: the automatic card makes room for the program's widgets.
     * Returns false if the uplink is in direct mode (Lua may not change it).
     */
    public synchronized boolean beginLua() {
        if (template) return false;
        if (!deviceKind.isEmpty()) {
            deviceCard = null;
            deviceKind = "";
            widgets.clear();
            title = "";
            structureDirty = true;
        }
        if (auto) {
            auto = false;
            widgets.clear();
            pages.clear();
            groups.clear();
            title = "";
            structureDirty = true;
        }
        return true;
    }

    /** Runs an action on the Computer card (server thread). */
    private @Nullable String runComputerAction(String widgetId, @Nullable JsonElement value, @Nullable Op op, String user) {
        if (!(level instanceof ServerLevel serverLevel)) return "unavailable";
        var computer = attachedComputer(serverLevel);
        if (computer == null) return "unavailable";
        var action = ComputerCard.action(widgetId, value, op);
        if (action == null) return "not_interactive";
        switch (action) {
            case ComputerCard.Power power -> {
                switch (power.what()) {
                    case ComputerCard.TURN_ON -> computer.turnOn();
                    case ComputerCard.REBOOT -> computer.reboot();
                    default -> computer.shutdown();
                }
                CCCloud.LOGGER.info("[cccloud] {}: {} on computer #{}", user, power.what(), computer.id());
            }
            case ComputerCard.SetOutput set -> {
                if (!computer.setOutput(set.side(), set.level())) return "unsupported";
            }
        }
        refreshAuto(computer);
        return null;
    }

    private void updateStatus(ServerLevel level) {
        CloudStatus newStatus = CloudStatus.OFFLINE;
        if (link != null) {
            var mainframe = CloudNetwork.mainframe(link.id());
            if (mainframe != null) {
                mainframe.linkUplink(uplinkId, GlobalPos.of(level.dimension(), getBlockPos()));
                var route = ai.ezfox.cccloud.network.NetworkService.route(uplinkId);
                if (route == null) {
                    newStatus = CloudStatus.CONNECTING; // network not computed yet
                    routeState = null;
                } else if (!route.connected()) {
                    routeState = "no_route";
                } else {
                    routeState = route.legacyOutOfRange() ? "legacy_out_of_range" : null;
                    newStatus = switch (mainframe.status()) {
                        case ONLINE -> CloudStatus.ONLINE;
                        case CONNECTING -> CloudStatus.CONNECTING;
                        case OFFLINE -> CloudStatus.OFFLINE;
                    };
                }
            }
        }
        if (newStatus != status) {
            status = newStatus;
            synchronized (this) {
                structureDirty = true;
            }
        }
        BlockState state = getBlockState();
        if (state.getValue(CloudUplinkBlock.STATUS) != newStatus) {
            level.setBlock(getBlockPos(), state.setValue(CloudUplinkBlock.STATUS, newStatus), Block.UPDATE_CLIENTS);
        }
    }

    /** Connected to its mainframe over the network (or by the legacy grace). */
    public boolean hasRoute() {
        var route = ai.ezfox.cccloud.network.NetworkService.route(uplinkId);
        return route != null && route.connected();
    }

    public @Nullable String routeState() {
        return routeState;
    }

    public boolean isLegacyLink() {
        return legacyLink;
    }

    /** Test hook: link without the range check (like a world from v0.3). */
    public void forceLinkForTest(MainframeLink newLink) {
        link = newLink;
        var mainframe = CloudNetwork.mainframe(newLink.id());
        if (mainframe != null && level != null) mainframe.linkUplink(uplinkId, GlobalPos.of(level.dimension(), getBlockPos()));
        synchronized (this) {
            structureDirty = true;
        }
        ai.ezfox.cccloud.network.NetworkService.markDirty();
    }

    /** Test hook: behave like a device linked before v0.4. */
    public void markLegacyForTest() {
        legacyLink = true;
        ai.ezfox.cccloud.network.NetworkService.markDirty();
    }

    // ---- binding (link card) -----------------------------------------------------------------------------------------

    public void bind(MainframeLink newLink, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var mainframe = CloudNetwork.mainframe(newLink.id());
        if (mainframe == null || mainframe.getLevel() == null || !mainframe.getBlockPos().equals(newLink.pos().pos())) {
            player.sendSystemMessage(Component.translatable("cccloud.uplink.mainframe_missing").withStyle(ChatFormatting.RED));
            return;
        }
        if (!mainframe.has(player, ai.ezfox.cccloud.access.Perm.NETWORK_LINK)) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.not_owner").withStyle(ChatFormatting.RED));
            return;
        }
        var here = GlobalPos.of(serverLevel.dimension(), getBlockPos());
        // Range is checked against the whole network (mainframe and relays), not only the mainframe.
        var error = ai.ezfox.cccloud.network.NetworkService.checkLink(mainframe, serverLevel, getBlockPos(),
            ai.ezfox.cccloud.network.NetworkService.deviceType(this));
        if (error != null) {
            player.sendSystemMessage(error.copy().withStyle(ChatFormatting.RED));
            return;
        }
        if (link != null && !link.id().equals(newLink.id())) unlinkFrom(link);
        link = newLink;
        legacyLink = false;
        ai.ezfox.cccloud.network.NetworkService.markDirty();
        mainframe.linkUplink(uplinkId, here);
        synchronized (this) {
            structureDirty = true;
        }
        setChanged();
        updateStatus(serverLevel);
        CCCloud.LOGGER.info("[cccloud] {} bound uplink {} to {}", player.getGameProfile().name(), uplinkId, mainframe.getDisplayName());
        player.sendSystemMessage(Component.translatable("cccloud.uplink.bound", newLink.describe()).withStyle(ChatFormatting.GREEN));
    }

    public synchronized int widgetCount() {
        return widgets.size();
    }

    /** Unlinks the uplink from its mainframe (in-game screen; rights are checked by the caller). */
    public void unlink(String by) {
        if (link == null || !(level instanceof ServerLevel serverLevel)) return;
        var old = link;
        unlinkFrom(old);
        link = null;
        legacyLink = false;
        ai.ezfox.cccloud.network.NetworkService.markDirty();
        synchronized (this) {
            structureDirty = true;
        }
        setChanged();
        updateStatus(serverLevel);
        CCCloud.LOGGER.info("[cccloud] {} unlinked uplink {} from {}", by, uplinkId, old.describe());
    }

    public void showStatus(Player player) {
        player.sendSystemMessage(Component.translatable(isAdvanced() ? "block.cccloud.advanced_cloud_uplink" : "block.cccloud.cloud_uplink")
            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (link == null) {
            player.sendSystemMessage(Component.translatable("cccloud.uplink.unlinked").withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.translatable("cccloud.uplink.linked", link.describe(), status.displayName()));
        }
        int count;
        synchronized (this) {
            count = widgets.size();
        }
        player.sendSystemMessage(Component.translatable("cccloud.uplink.widgets", count, computers.size()).withStyle(ChatFormatting.GRAY));
    }

    // ---- widget model (any thread) -----------------------------------------------------------------------------------

    public synchronized JsonObject snapshot() {
        var json = new JsonObject();
        json.addProperty("uplinkId", uplinkId.toString());
        json.addProperty("title", title);
        json.addProperty("online", status == CloudStatus.ONLINE);
        var array = new JsonArray();
        for (var widget : widgets.values()) array.add(widget.toJson());
        json.add("widgets", array);
        if (!pages.isEmpty()) json.add("pages", declarations(pages));
        if (!groups.isEmpty()) json.add("groups", declarations(groups));
        json.addProperty("mode", template || !deviceKind.isEmpty() ? "direct" : auto ? "auto" : "lua");
        if (template) {
            json.addProperty("template", "storage");
            json.addProperty("bridge", templateKind);
        } else if (!deviceKind.isEmpty()) {
            json.addProperty("template", deviceKind);
        }
        return json;
    }

    public synchronized void setTitle(String title) {
        if (this.title.equals(title)) return;
        this.title = title;
        structureDirty = true;
    }

    @Override
    public synchronized String getTitle() {
        return title;
    }

    /** Adds or replaces a widget (a replaced widget keeps its position). Returns false if the widget limit is reached. */
    public synchronized boolean putWidget(Widget widget) {
        if (!widgets.containsKey(widget.id()) && widgets.size() >= maxWidgets()) return false;
        widgets.put(widget.id(), widget);
        structureDirty = true;
        return true;
    }

    /** 32 widgets per uplink, 64 once the script declares pages (both configurable). */
    public synchronized int maxWidgets() {
        return pages.isEmpty() ? CloudConfig.get(CloudConfig.MAX_WIDGETS_PER_UPLINK) : CloudConfig.get(CloudConfig.MAX_WIDGETS_WITH_PAGES);
    }

    public synchronized boolean putPage(JsonObject page) {
        String id = page.get("id").getAsString();
        if (!pages.containsKey(id) && pages.size() >= Limits.MAX_PAGES) return false;
        if (page.equals(pages.get(id))) return true;
        pages.put(id, page);
        structureDirty = true;
        return true;
    }

    public synchronized boolean putGroup(JsonObject group) {
        String id = group.get("id").getAsString();
        if (!groups.containsKey(id) && groups.size() >= Limits.MAX_GROUPS) return false;
        if (group.equals(groups.get(id))) return true;
        groups.put(id, group);
        structureDirty = true;
        return true;
    }

    /** Sets several values at once; all are validated before any is applied. */
    public synchronized void setValues(Map<String, JsonElement> values) {
        for (var entry : values.entrySet()) setValue(entry.getKey(), entry.getValue());
    }

    public synchronized @Nullable Widget getWidget(String id) {
        return widgets.get(id);
    }

    /** Widget id → time of the last dashboard action on it (echo of identical values, see {@link #setValue}). */
    private final java.util.Map<String, Long> lastActionAt = new java.util.HashMap<>();
    static final long ECHO_WINDOW_MS = 2000;

    public synchronized void setValue(String id, JsonElement value) {
        var widget = widgets.get(id);
        if (widget == null) return;
        if (widget.value().equals(value)) {
            // Unchanged values are not re-sent, except right after a dashboard action: the browser waits for the
            // script's confirmation of its optimistic value, so an identical set() still goes out as a delta.
            Long at = lastActionAt.remove(id);
            if (at != null && System.currentTimeMillis() - at <= ECHO_WINDOW_MS && widget.type() != WidgetType.CHART) dirtyValues.add(id);
            return;
        }
        lastActionAt.remove(id);
        widget.setValue(value);
        // Charts are broadcast when a point is sampled (each value delta appends one point in the browser).
        if (widget.type() != WidgetType.CHART) dirtyValues.add(id);
    }

    public synchronized boolean removeWidget(String id) {
        if (widgets.remove(id) == null) return false;
        structureDirty = true;
        return true;
    }

    public synchronized void clearWidgets() {
        if (widgets.isEmpty() && pages.isEmpty() && groups.isEmpty()) return;
        widgets.clear();
        pages.clear();
        groups.clear();
        structureDirty = true;
    }

    /** Queues an alert, at most one per second per uplink. */
    public synchronized boolean alert(String text, String level) {
        long now = System.nanoTime();
        if (lastAlertNanos != 0 && now - lastAlertNanos < 1_000_000_000L) return false;
        lastAlertNanos = now;
        pendingAlerts.add(new String[]{text, level});
        return true;
    }

    // ---- computers & actions -----------------------------------------------------------------------------------------

    public void attach(IComputerAccess computer) {
        computers.put(computer, new TokenBucket(MAX_PENDING_EVENTS, 16));
    }

    public void detach(IComputerAccess computer) {
        computers.remove(computer);
        if (computers.isEmpty()) computerDetached = true;
    }

    public int computerCount() {
        return computers.size();
    }

    /**
     * Handles an action from the dashboard (server thread). Validates it against the widget definition, updates stored
     * values and queues {@code cloud_action} on every attached computer. Bridge operations (craft, watchlist) are run by
     * the bridge; craft requests complete once the storage mod planned the job.
     */
    @Override
    public CompletableFuture<ActionResult> handleAction(String widgetId, JsonElement rawValue, @Nullable Op op, String user, boolean pinVerified) {
        if (!isOnline()) return ActionResult.done("uplink_offline");
        ActionValidator.Result result;
        Widget widget;
        synchronized (this) {
            widget = widgets.get(widgetId);
            result = ActionValidator.validate(widget, rawValue, op, pinVerified);
            if (!result.ok()) return ActionResult.done(result.error());
            var type = widget.type();
            if (result.op() == null && type != WidgetType.BUTTON && type != WidgetType.BUTTONS) {
                widget.setValue(result.value());
                dirtyValues.add(widgetId);
                lastActionAt.put(widgetId, System.currentTimeMillis());
            }
        }
        ai.ezfox.cccloud.compat.card.DeviceCard card;
        synchronized (this) {
            card = deviceCard;
        }
        if (card != null) return ActionResult.done(ai.ezfox.cccloud.compat.card.DeviceCards.action(card, widgetId, result.value(), user));
        if (isAuto()) return ActionResult.done(runComputerAction(widgetId, result.value(), result.op(), user));
        if (result.op() != null) return handleOp(widget, result.op(), user);
        queueAction(widgetId, Json.toLua(result.value()), user);
        return ActionResult.done(null);
    }

    private CompletableFuture<ActionResult> handleOp(Widget widget, Op op, String user) {
        var host = bridgeFor(widget);
        if (host == null) return ActionResult.done("unavailable");
        var core = host.bridgeCore();
        if (op instanceof Op.Craft craft) {
            var ticket = core.craft(craft.name(), craft.count(), user);
            if (ticket.error() == null) {
                setValueLater(core);
                if (!template) {
                    var event = new java.util.LinkedHashMap<String, Object>();
                    event.put("op", "craft");
                    event.put("name", craft.name());
                    event.put("count", craft.count());
                    event.put("jobId", ticket.jobId());
                    queueAction(widget.id(), event, user);
                }
            }
            return ticket.result();
        }
        String error = core.watchOp(op);
        if (error == null) {
            synchronized (this) {
                for (var w : widgets.values()) {
                    if (w.type() == WidgetType.WATCHLIST && bridgeFor(w) == host) setValue(w.id(), core.watchRows());
                }
            }
        }
        return ActionResult.done(error);
    }

    /** Shows a new job right away instead of after the next refresh. */
    private void setValueLater(BridgeCore core) {
        synchronized (this) {
            for (var w : widgets.values()) {
                if (w.type() == WidgetType.JOBS) setValue(w.id(), core.jobRows(BridgeCore.JOB_ROWS));
            }
        }
    }

    /** Item search on an item_search widget (server thread). */
    @Override
    public QueryResult handleQuery(String widgetId, String query) {
        if (!isOnline()) return QueryResult.fail("uplink_offline");
        Widget widget;
        synchronized (this) {
            widget = widgets.get(widgetId);
        }
        if (widget == null || widget.type() != WidgetType.ITEM_SEARCH) return QueryResult.fail("unknown_widget");
        ai.ezfox.cccloud.compat.card.DeviceCard card;
        synchronized (this) {
            card = deviceCard;
        }
        if (card != null) {
            // v0.8 storage cards (Functional Storage, Sophisticated Storage, ...) search their own inventory.
            var result = ai.ezfox.cccloud.compat.card.DeviceCards.query(card, widgetId, query);
            return result != null ? result : QueryResult.fail("unknown_widget");
        }
        var host = bridgeFor(widget);
        if (host == null || !host.bridgeCore().isConnected()) return QueryResult.fail("offline");
        var core = host.bridgeCore();
        if (!core.trySearch()) return QueryResult.fail("rate_limited");
        var page = core.index().search(query, Limits.QUERY_ROWS);
        var rows = new JsonArray(page.rows().size());
        for (var row : page.rows()) rows.add(row.toJson());
        return new QueryResult(rows, page.truncated(), null);
    }

    private void queueAction(String widgetId, @Nullable Object luaValue, String user) {
        for (var entry : computers.entrySet()) {
            var computer = entry.getKey();
            if (!entry.getValue().tryAcquire()) {
                if (droppedEvents++ % 100 == 0) {
                    CCCloud.LOGGER.warn("[cccloud] Dropping cloud_action for computer #{} on uplink {}: more than {} pending events",
                        computer.getID(), uplinkId, MAX_PENDING_EVENTS);
                }
                continue;
            }
            try {
                computer.queueEvent("cloud_action", widgetId, luaValue, user, computer.getAttachmentName());
            } catch (RuntimeException e) {
                // The computer was detached in the meantime.
                computers.remove(computer);
            }
        }
    }

    /** Client-side animation state for the block entity renderer. */
    public SpinAnimation clientAnimation() {
        return clientAnimation;
    }
}
