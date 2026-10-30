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
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
public class CloudUplinkBlockEntity extends BlockEntity implements ai.ezfox.cccloud.network.DashboardSection, ai.ezfox.cccloud.claims.ClaimGuard.Guarded {
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
    /** v0.7 wireless redstone: mode, channel (per mainframe network), last input (transmitter) and output (receiver). */
    private ai.ezfox.cccloud.redstone.RedstoneMode rsMode = ai.ezfox.cccloud.redstone.RedstoneMode.OFF;
    private String rsChannel = "";
    private int rsInput;
    private int rsOutput;
    private long rsLastChange = Long.MIN_VALUE / 2;
    /** The input is not saved: a transmitter samples it on its first tick after loading (neighbours are loaded then). */
    private boolean rsInputPending;
    /**
     * Re-check the {@code mount} (plate / compact puck) on the first tick after loading: uplinks from before the
     * compact variant, {@code /setblock} and rotated structures get the right model without a neighbour update.
     */
    private boolean mountPending;
    /** "no_route", "over_capacity", "legacy_out_of_range" or null (for the screen). */
    private volatile @Nullable String routeState;
    /** v0.5 device card (compat/card): direct mode on a supported machine. {@code deviceKind} is saved; both guarded by this. */
    private ai.ezfox.cccloud.compat.card.@Nullable DeviceCard deviceCard;
    /** Set by a config reload: the next card update detects the device card again (server thread). */
    private boolean redetectCards;
    private String deviceKind = "";
    private int deviceRevision;
    /**
     * Wave 4: the owner's settings of the device card (the Peripheral card's picked methods), saved with the uplink, and
     * the block they were made for (settings never carry over to another block). Guarded by this.
     */
    private com.google.gson.@Nullable JsonObject cardSettings;
    private String cardSettingsFor = "";
    /**
     * Cloud Probe (docs/SPEC.md "Cloud Probe"): the machine it reads instead of the block it sticks to, and the face
     * it reads it through (the face that was clicked with the Link Card). Null = no target yet. Only for probes.
     */
    private volatile @Nullable BlockPos remoteTarget;
    private volatile Direction remoteSide = Direction.UP;
    /**
     * Where the probe stood, in which dimension, and who set the target (security review 2026-10-30): a probe moved with
     * its data (contraptions, block movers) forgets the target instead of reading the same coordinates elsewhere, and
     * the target is only read while that player may use its chunk (claims of other teams).
     */
    private volatile @Nullable BlockPos remoteOrigin;
    private volatile String remoteDim = "";
    private volatile @Nullable UUID remoteBy;
    /** Who linked it with the Link Card (claim guard: their team may use claimed land); null for links made before 0.11. */
    private volatile @Nullable UUID linkedBy;
    /** Who placed it (claim guard, until it is linked); null when placed by a command or before 0.11. */
    private volatile @Nullable UUID placedBy;
    /** Claim guard (docs/SECURITY.md "Claims"): the claim the block it reads is in, while that blocks it; else null. */
    private volatile ai.ezfox.cccloud.claims.ClaimGuard.@Nullable Block claimBlock;
    /** The card's title when it was blocked (the blocked card keeps showing it). */
    private String blockedTitle = "";
    /** Unblocked: look for the card, computer and bridge again on the next tick instead of within a second. */
    private volatile boolean claimRefresh;
    /**
     * The block it reads has no card although another face of it exposes energy, items or fluids: that face (screen
     * hint "this side exposes nothing"), else null. Updated with the card detection, once per second.
     */
    private volatile @Nullable Direction exposedElsewhere;
    /** Last forwarded watchlist alert per bridge (key = bridge position). */
    private final Map<String, Integer> bridgeAlertSeq = new java.util.HashMap<>();

    // Dirty tracking (guarded by this)
    private boolean structureDirty = true;
    /** The widget set changed: the list of charts to sample is built again (structureDirty is cleared by the publish). */
    private boolean chartsDirty = true;
    private Widget[] charts = new Widget[0];
    private final Set<String> dirtyValues = new LinkedHashSet<>();
    private final List<String[]> pendingAlerts = new ArrayList<>();
    private long lastAlertNanos;

    private final Map<IComputerAccess, TokenBucket> computers = new ConcurrentHashMap<>();
    private final UplinkPeripheral peripheral = new UplinkPeripheral(this);
    /** CC wired network membership (a member: it puts no peripheral on the cable). */
    private final ai.ezfox.cccloud.cable.CloudWired wired = ai.ezfox.cccloud.cable.CloudWired.single(this, () -> uplinkId.toString(), false, () -> null);
    /** Cards of the machines the attached computer reaches over CC wired networks (docs/SPEC.md "CC wired networks"). */
    private final ai.ezfox.cccloud.uplink.WiredMachines wiredMachines = new ai.ezfox.cccloud.uplink.WiredMachines(this);
    private volatile CloudStatus status = CloudStatus.OFFLINE;
    private boolean registered;
    private final SpinAnimation clientAnimation = new SpinAnimation();
    private boolean broken;
    private int tickCounter;
    private boolean statusPending = true;
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

    /** Config reload ({@code [cards]} of the integrations config): the device card and wired machine cards are detected again. */
    public void redetectCards() {
        redetectCards = true;
        wiredMachines.redetect();
    }

    public ai.ezfox.cccloud.uplink.WiredMachines wiredMachines() {
        return wiredMachines;
    }

    public ai.ezfox.cccloud.cable.CloudWired wired() {
        return wired;
    }

    public UplinkPeripheral peripheral() {
        return peripheral;
    }

    public boolean isAdvanced() {
        return getBlockState().getBlock() instanceof CloudUplinkBlock b && b.isAdvanced();
    }

    public boolean isProbe() {
        return getBlockState().getBlock() instanceof CloudUplinkBlock b && b.isProbe();
    }

    /**
     * The block this uplink reads (cards, bridge template, Computer card): the block it sticks to, or for a Cloud Probe
     * its remote target (null without one).
     */
    public @Nullable BlockPos readPos() {
        if (claimBlock != null) return null; // claim guard: nothing is read
        if (isProbe()) return usableTarget();
        return getBlockPos().relative(getBlockState().getValue(CloudUplinkBlock.FACING));
    }

    /** The face of {@link #readPos()} it reads through (capability lookups). */
    public Direction readSide() {
        return isProbe() ? remoteSide : getBlockState().getValue(CloudUplinkBlock.FACING).getOpposite();
    }

    /** Cloud Probe: its target (null = none). */
    public @Nullable BlockPos remoteTarget() {
        return remoteTarget;
    }

    /** For the screen: another face of the machine that exposes something when this one shows no card, else null. */
    public @Nullable Direction exposedElsewhere() {
        return exposedElsewhere;
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
        var mode = ai.ezfox.cccloud.redstone.RedstoneMode.byId(input.getStringOr("rs_mode", "off"));
        rsMode = mode == null ? ai.ezfox.cccloud.redstone.RedstoneMode.OFF : mode;
        rsChannel = input.getStringOr("rs_channel", "");
        rsOutput = Math.max(0, Math.min(15, input.getIntOr("rs_output", 0)));
        remoteTarget = input.read("remote_target", BlockPos.CODEC).orElse(null);
        remoteSide = input.read("remote_side", Direction.CODEC).orElse(Direction.UP);
        remoteOrigin = input.read("remote_origin", BlockPos.CODEC).orElse(null);
        remoteDim = input.getStringOr("remote_dim", "");
        remoteBy = input.read("remote_by", UUIDUtil.STRING_CODEC).orElse(null);
        linkedBy = input.read("linked_by", UUIDUtil.STRING_CODEC).orElse(null);
        placedBy = input.read("placed_by", UUIDUtil.STRING_CODEC).orElse(null);
        wiredMachines.load(input.read("wired_cards", UUIDUtil.STRING_CODEC.listOf()).orElse(java.util.List.of()));
        synchronized (this) {
            title = input.getStringOr("title", "");
            blockedTitle = input.getStringOr("blocked_title", "");
            template = input.getBooleanOr("template", false);
            auto = input.getBooleanOr("auto", false);
            templateKind = input.getStringOr("template_kind", "");
            deviceKind = input.getStringOr("device_kind", "");
            cardSettingsFor = input.getStringOr("card_settings_for", "");
            try {
                String saved = input.getStringOr("card_settings", "");
                cardSettings = saved.isEmpty() ? null : JsonParser.parseString(saved).getAsJsonObject();
            } catch (RuntimeException e) {
                cardSettings = null;
            }
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
            structureDirty = chartsDirty = true;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("uplink_id", UUIDUtil.STRING_CODEC, uplinkId);
        output.storeNullable("link", MainframeLink.CODEC, link);
        output.putInt("net_v", 1);
        output.putBoolean("legacy_link", legacyLink);
        output.putString("rs_mode", rsMode.id());
        output.putString("rs_channel", rsChannel);
        output.putInt("rs_output", rsOutput);
        output.storeNullable("remote_target", BlockPos.CODEC, remoteTarget);
        output.store("remote_side", Direction.CODEC, remoteSide);
        output.storeNullable("remote_origin", BlockPos.CODEC, remoteOrigin);
        output.putString("remote_dim", remoteDim);
        var wiredCards = wiredMachines.ids();
        if (!wiredCards.isEmpty()) output.store("wired_cards", UUIDUtil.STRING_CODEC.listOf(), wiredCards);
        output.storeNullable("remote_by", UUIDUtil.STRING_CODEC, remoteBy);
        output.storeNullable("linked_by", UUIDUtil.STRING_CODEC, linkedBy);
        output.storeNullable("placed_by", UUIDUtil.STRING_CODEC, placedBy);
        synchronized (this) {
            output.putString("title", title);
            if (claimBlock != null && !blockedTitle.isEmpty()) output.putString("blocked_title", blockedTitle);
            output.putBoolean("template", template);
            output.putBoolean("auto", auto);
            output.putString("template_kind", templateKind);
            output.putString("device_kind", deviceKind);
            if (cardSettings != null) {
                output.putString("card_settings", Json.GSON.toJson(cardSettings));
                output.putString("card_settings_for", cardSettingsFor);
            }
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
        chunkUnloading = false;
        if (level == null || level.isClientSide()) return;
        if (!CloudNetwork.registerUplink(this)) {
            uplinkId = UUID.randomUUID();
            link = null;
            setChanged();
            CloudNetwork.registerUplink(this);
        }
        registered = true;
        // Staggered by position: many uplinks loaded together do not all run their checks in the same tick.
        tickCounter = Math.floorMod(Long.hashCode(getBlockPos().asLong()), 2 * BRIDGE_INTERVAL);
        statusPending = true;
        rsInputPending = true;
        mountPending = true;
        wired.onLoad();
        // claim guard: a device in a chunk another team claimed meanwhile reads nothing from the first tick on
        ai.ezfox.cccloud.claims.ClaimGuard.check(this);
        // only this device's network is computed again (a chunk of a big base loading must not recompute every network)
        ai.ezfox.cccloud.network.NetworkService.markDirty(link == null ? null : link.id());
    }

    /** Set by NeoForge before {@link #setRemoved()} when the chunk unloads (not when the block is removed). */
    private boolean chunkUnloading;

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        chunkUnloading = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        wired.onRemoved();
        ai.ezfox.cccloud.claims.ClaimGuard.forget(this);
        if (level instanceof ServerLevel serverLevel && !serverLevel.isClientSide()) {
            // chunk unload: the wired machines' cards stay greyed out like this uplink's card; otherwise they go
            if (chunkUnloading && registered) wiredMachines.unloaded(serverLevel);
            else wiredMachines.removed();
        }
        if (!registered) return;
        registered = false;
        // Removed without the break path (/setblock, /fill, explosions without side effects, block movers): the
        // section goes away once the block stays missing (OrphanSections).
        if (!chunkUnloading && !broken && link != null && level instanceof ServerLevel serverLevel) {
            ai.ezfox.cccloud.network.OrphanSections.removedInPlace(GlobalPos.of(serverLevel.dimension(), getBlockPos()), uplinkId, link.id(),
                serverLevel.getServer().getTickCount());
        }
        CloudNetwork.unregisterUplink(this);
        ai.ezfox.cccloud.network.NetworkService.markDirty(link == null ? null : link.id());
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
        if (mountPending) {
            mountPending = false;
            var state = getBlockState();
            var fitted = CloudUplinkBlock.withMount(state, level, getBlockPos());
            if (fitted != state) level.setBlock(getBlockPos(), fitted, Block.UPDATE_CLIENTS);
        }
        if (rsInputPending) {
            // A lever that was already on when the chunk loaded sends no neighbour update; afterwards neighborChanged does.
            rsInputPending = false;
            onNeighborChanged();
        }
        if (statusPending || tickCounter % STATUS_INTERVAL == 0) {
            statusPending = false; // right after loading, then every STATUS_INTERVAL ticks
            updateStatus(level);
        }
        if (claimRefresh) {
            // unblocked by the claim guard: the card, the computer and the bridge come back at once
            claimRefresh = false;
            if (claimBlock == null) {
                updateBridges(level);
                updateComputer(level);
                updateDeviceCard(level);
            }
        }
        if (tickCounter % BRIDGE_INTERVAL == 0) updateBridges(level);
        if (tickCounter % STATUS_INTERVAL == 5) updateComputer(level);
        if (tickCounter % BRIDGE_INTERVAL == 15) updateDeviceCard(level);
        if (tickCounter % BRIDGE_INTERVAL == 10) wiredMachines.refresh(level);
        wiredMachines.tick(level.getGameTime());
        tickCounter++;
        sampleCharts(level.getGameTime());
        if (claimBlock != null) {
            // claim guard: values and alerts of a Lua program are not sent (the blocked card has no widgets)
            synchronized (this) {
                dirtyValues.clear();
                pendingAlerts.clear();
            }
        }
        if (link == null || suppressed) {
            synchronized (this) {
                dirtyValues.clear();
                pendingAlerts.clear();
            }
            return;
        }

        boolean changed = false;
        JsonObject snapshot = null;
        int valueCount = 0;
        List<String[]> alerts = null;
        synchronized (this) {
            if (structureDirty) {
                structureDirty = false;
                dirtyValues.clear();
                snapshot = snapshot();
                changed = true;
            } else if (!dirtyValues.isEmpty() && (tickCounter & 1) == 0) {
                // Every other tick (staggered by position): the hub sends at most 10 times a second anyway, so a value
                // that changes every tick is handed over once per 2 ticks (docs/SPEC.md "Scaling").
                // reused buffers: no per-tick allocation for the changed values
                if (valueIds.length < dirtyValues.size()) {
                    valueIds = new String[dirtyValues.size() * 2];
                    valueData = new JsonElement[dirtyValues.size() * 2];
                }
                for (var id : dirtyValues) {
                    var widget = widgets.get(id);
                    if (widget == null) continue;
                    valueIds[valueCount] = id;
                    valueData[valueCount++] = widget.value();
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
        if (valueCount > 0) {
            WebHub.INSTANCE.publishValues(mainframeId, id, valueIds, valueData, valueCount);
            java.util.Arrays.fill(valueData, 0, valueCount, null);
        }
        if (alerts != null && isOnline()) {
            for (var alert : alerts) WebHub.INSTANCE.publishAlert(mainframeId, id, alert[0], alert[1]);
        }
        // Saving: at most every 5 s while values keep changing (the chunk is marked unsaved, not written)
        if (changed) saveDirty = true;
        if (saveDirty && (snapshot != null || tickCounter % SAVE_INTERVAL == 0)) {
            saveDirty = false;
            // Only the chunk is marked unsaved: setChanged() would also update comparators around the uplink (it has no
            // comparator output), which on a big base was a measurable part of the uplink tick.
            level.blockEntityChanged(getBlockPos());
        }
    }

    private String[] valueIds = new String[0];
    private JsonElement[] valueData = new JsonElement[0];
    private boolean saveDirty;

    private static final int BRIDGE_INTERVAL = 20;
    /** Changed values mark the chunk unsaved at most every 5 s (the world is written on autosave anyway). */
    private static final int SAVE_INTERVAL = 100;

    private synchronized void sampleCharts(long gameTime) {
        if (chartsDirty) {
            chartsDirty = false;
            charts = widgets.values().stream().filter(w -> w.type() == WidgetType.CHART).toArray(Widget[]::new);
        }
        for (var widget : charts) if (widget.sample(gameTime)) dirtyValues.add(widget.id());
    }

    /** The bridge this uplink is attached to (direct mode), or null. */
    private @Nullable BridgeHost attachedBridge(ServerLevel level) {
        var pos = readPos();
        if (pos == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof BridgeHost host ? host : null;
    }

    private @Nullable BridgeHost resolveBridge(@Nullable BridgeRef ref) {
        if (ref == null || !(level instanceof ServerLevel serverLevel)) return null;
        var id = Identifier.tryParse(ref.dimension());
        if (id == null) return null;
        var target = serverLevel.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        var pos = new BlockPos(ref.x(), ref.y(), ref.z());
        if (target == null || !target.isLoaded(pos)) return null;
        // claim guard: a blocked uplink reads no bridge, and no bridge in a chunk another team claimed
        if (claimBlock != null || ai.ezfox.cccloud.claims.ClaimGuard.blockedAt(target, pos, guardOwners())) return null;
        return target.getBlockEntity(pos) instanceof BridgeHost host ? host : null;
    }

    /** The bridge behind a widget (Lua-bound item_search/watchlist/jobs, or the template's bridge), or null. */
    public synchronized @Nullable BridgeHost bridgeForWidget(String widgetId) {
        var widget = widgets.get(widgetId);
        return widget == null ? null : bridgeFor(widget);
    }

    /** The device card this uplink shows (direct mode on a machine), or null (v0.10 production statistics). */
    public synchronized ai.ezfox.cccloud.compat.card.@Nullable DeviceCard deviceCard() {
        return claimBlock != null ? null : deviceCard;
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
                structureDirty = chartsDirty = true;
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
        structureDirty = chartsDirty = true;
        setChanged();
    }

    private synchronized void leaveTemplate() {
        template = false;
        templateKind = "";
        title = "";
        widgets.clear();
        structureDirty = chartsDirty = true;
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
        boolean again = redetectCards;
        redetectCards = false;
        synchronized (this) {
            // after a config reload ([cards]): detect again, so switched-off kinds disappear and widgets (PIN) are rebuilt
            card = again ? null : deviceCard;
            free = !deviceKind.isEmpty() || (!template && !auto && widgets.isEmpty() && pages.isEmpty() && groups.isEmpty());
        }
        if (isProbe() && !checkRemoteTarget(level)) card = null;
        if (card != null && !ai.ezfox.cccloud.compat.card.DeviceCards.isValid(card)) card = null;
        var readPos = readPos();
        Direction hint = null;
        if (card == null && free && readPos != null && ai.ezfox.cccloud.compat.Integrations.deviceCardsEnabled()) {
            card = ai.ezfox.cccloud.compat.card.DeviceCards.detect(level, readPos, readSide(), true);
            if (card == null) hint = exposedElsewhere(level, readPos, readSide());
        }
        exposedElsewhere = card == null ? hint : null;
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

    /**
     * Another face of the block at {@code pos} that exposes energy, items or fluids, when the given one does not and
     * no card was found (screen hint); null if there is none, or if the block is a computer, a bridge or ours.
     */
    private static @Nullable Direction exposedElsewhere(ServerLevel level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) return null;
        var state = level.getBlockState(pos);
        if (state.isAir() || ai.ezfox.cccloud.CCCloud.MODID.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) {
            return null;
        }
        if (CcComputers.find(level, pos) != null || exposes(level, pos, side)) return null;
        for (var d : Direction.values()) if (d != side && exposes(level, pos, d)) return d;
        return null;
    }

    private static boolean exposes(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, pos, side) != null
            || level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, side) != null
            || level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, pos, side) != null;
    }

    // ---- Cloud Probe ---------------------------------------------------------------------------------------------

    /**
     * Why a probe target is refused, or null if it may be read: same dimension, within {@code probeRange}, loaded, a
     * block entity, not a block of this mod and not the block the probe sticks to.
     */
    public static @Nullable String targetProblem(Level probeLevel, BlockPos probe, Level targetLevel, BlockPos target) {
        if (probeLevel != targetLevel) return "cccloud.probe.other_dimension";
        if (target.equals(probe)) return "cccloud.probe.not_a_machine";
        double distance = Math.sqrt(probe.distSqr(target));
        if (distance > CloudConfig.get(CloudConfig.PROBE_RANGE) + 1e-9) return "cccloud.probe.too_far";
        if (!targetLevel.isLoaded(target)) return "cccloud.probe.unloaded";
        var state = targetLevel.getBlockState(target);
        if (targetLevel.getBlockEntity(target) == null
            || ai.ezfox.cccloud.CCCloud.MODID.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) {
            return "cccloud.probe.not_a_machine";
        }
        return null;
    }

    /**
     * Points this Cloud Probe at a machine (Link Card). The player needs the uplink rights of the probe's network
     * ({@code network.link}) when it is linked; picking the machine already went through the normal interaction
     * checks for that player (see {@link ai.ezfox.cccloud.item.CloudLinkCardItem}).
     *
     * @return true if the target was set
     */
    public boolean setRemoteTarget(ai.ezfox.cccloud.item.ProbeTarget target, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !isProbe()) return false;
        if (!target.player().equals(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("cccloud.probe.other_player").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!target.fresh(System.currentTimeMillis())) {
            player.sendSystemMessage(Component.translatable("cccloud.probe.stale").withStyle(ChatFormatting.RED));
            return false;
        }
        if (link != null) {
            var mainframe = CloudNetwork.mainframe(link.id());
            if (mainframe == null || !mainframe.has(player, ai.ezfox.cccloud.access.Perm.NETWORK_LINK)) {
                player.sendSystemMessage(Component.translatable(mainframe == null ? "cccloud.uplink.mainframe_missing" : "cccloud.mainframe.not_owner")
                    .withStyle(ChatFormatting.RED));
                return false;
            }
        }
        var targetLevel = serverLevel.getServer().getLevel(target.pos().dimension());
        var pos = target.pos().pos();
        String problem = targetLevel == null ? "cccloud.probe.other_dimension" : targetProblem(serverLevel, getBlockPos(), targetLevel, pos);
        // The rights of the pick are checked again now (claims change, team members leave; a creative client can make a
        // card without picking): spawn protection and the world border, and claims of other teams.
        if (problem == null && (!serverLevel.mayInteract(player, pos)
            || ai.ezfox.cccloud.map.XrayGuard.claimedByOther(serverLevel, pos.getX() >> 4, pos.getZ() >> 4, player.getUUID()))) {
            problem = "cccloud.probe.no_access";
        }
        if (problem != null) {
            player.sendSystemMessage(Component.translatable(problem, CloudConfig.get(CloudConfig.PROBE_RANGE)).withStyle(ChatFormatting.RED));
            return false;
        }
        remoteTarget = pos.immutable();
        remoteSide = target.side();
        remoteOrigin = getBlockPos().immutable();
        remoteDim = serverLevel.dimension().identifier().toString();
        remoteBy = player.getUUID();
        synchronized (this) {
            showDeviceCard(null);
        }
        ai.ezfox.cccloud.claims.ClaimGuard.check(this);
        setDirect(serverLevel, true);
        setChanged();
        // the network map draws a line to the target
        if (link != null) ai.ezfox.cccloud.network.NetworkService.markDirty(link.id());
        String name = serverLevel.getBlockState(pos).getBlock().getName().getString();
        CCCloud.LOGGER.info("[cccloud] {} pointed the Cloud Probe at {} to {} at {}", player.getGameProfile().name(), getBlockPos(), name, pos);
        player.sendSystemMessage(Component.translatable("cccloud.probe.target_set", serverLevel.getBlockState(pos).getBlock().getName(),
            (int) Math.round(Math.sqrt(getBlockPos().distSqr(pos)))).withStyle(ChatFormatting.GREEN));
        return true;
    }

    /** Forgets the probe's target (screen button, or its machine was removed): the card goes like on detaching. */
    public void clearRemoteTarget(String by) {
        if (remoteTarget == null || !(level instanceof ServerLevel serverLevel)) return;
        var old = remoteTarget;
        remoteTarget = null;
        remoteOrigin = null;
        remoteDim = "";
        remoteBy = null;
        exposedElsewhere = null;
        synchronized (this) {
            showDeviceCard(null);
        }
        ai.ezfox.cccloud.claims.ClaimGuard.check(this);
        setDirect(serverLevel, false);
        setChanged();
        if (link != null) ai.ezfox.cccloud.network.NetworkService.markDirty(link.id());
        CCCloud.LOGGER.info("[cccloud] {} cleared the Cloud Probe target at {} (was {})", by, getBlockPos(), old);
    }

    /**
     * A probe's target must still be there and in range. A removed machine (no block entity any more) clears the
     * target like an uplink falling off its block; an unloaded one or a shrunken range just reads nothing for now.
     */
    private boolean checkRemoteTarget(ServerLevel level) {
        var target = remoteTarget;
        if (target == null) return false;
        if (remoteOrigin != null && "moved".equals(remoteProblem(getBlockPos(), level.dimension().identifier().toString(), remoteOrigin, remoteDim, target,
            Integer.MAX_VALUE))) {
            clearRemoteTarget("moved probe");
            return false;
        }
        if (!level.isLoaded(target)) return false;
        if (level.getBlockEntity(target) == null || level.getBlockState(target).isAir()) {
            clearRemoteTarget("removed machine");
            return false;
        }
        return usableTarget() != null;
    }

    /**
     * A probe's target if it may be read now, else null (security review 2026-10-30). Every read and action of a probe
     * goes through {@link #readPos()} and so through here: the machine card, the Computer card, the Storage card and
     * the screens. The probe still stands where the target was set, in that dimension; the target is within the
     * current {@code probeRange}, loaded and not a CC: Cloud block. Claims of other teams are the claim guard's part
     * ({@link #readPos()} is null while it blocks the probe). Server only.
     */
    private @Nullable BlockPos usableTarget() {
        var target = remoteTarget;
        if (target == null || !(level instanceof ServerLevel serverLevel)) return null;
        String dim = serverLevel.dimension().identifier().toString();
        if (remoteOrigin == null) {
            // set before the origin was stored (development worlds): adopted where the probe stands
            remoteOrigin = getBlockPos().immutable();
            remoteDim = dim;
        }
        if (remoteProblem(getBlockPos(), dim, remoteOrigin, remoteDim, target, CloudConfig.get(CloudConfig.PROBE_RANGE)) != null) return null;
        if (!serverLevel.isLoaded(target)) return null;
        var state = serverLevel.getBlockState(target);
        if (ai.ezfox.cccloud.CCCloud.MODID.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) return null;
        return target;
    }

    /**
     * The position part of a probe's read check (pure, unit tested): {@code moved} when the probe no longer stands where
     * its target was set or is in another dimension, {@code too_far} beyond the range, else null.
     */
    public static @Nullable String remoteProblem(BlockPos probe, String dim, BlockPos origin, String originDim, BlockPos target, int range) {
        if (!probe.equals(origin) || !dim.equals(originDim)) return "moved";
        if (Math.sqrt(probe.distSqr(target)) > range + 1e-9) return "too_far";
        return null;
    }

    private void setDirect(ServerLevel level, boolean direct) {
        var state = getBlockState();
        if (state.getValue(CloudUplinkBlock.DIRECT) != direct) level.setBlock(getBlockPos(), state.setValue(CloudUplinkBlock.DIRECT, direct), Block.UPDATE_CLIENTS);
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
            if (deviceCard != card && cardSettings != null && cardSettingsFor.equals(readBlockId())) {
                ai.ezfox.cccloud.compat.card.DeviceCards.applySettings(card, cardSettings, true);
            }
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
        structureDirty = chartsDirty = true;
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
        var pos = readPos();
        return pos == null ? null : CcComputers.find(level, pos);
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
                structureDirty = chartsDirty = true;
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
        structureDirty = chartsDirty = true;
        setChanged();
    }

    private synchronized void leaveAuto() {
        auto = false;
        widgets.clear();
        groups.clear();
        title = "";
        structureDirty = chartsDirty = true;
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
                structureDirty = chartsDirty = true;
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
            structureDirty = chartsDirty = true;
        }
        if (auto) {
            auto = false;
            widgets.clear();
            pages.clear();
            groups.clear();
            title = "";
            structureDirty = chartsDirty = true;
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
                var at = GlobalPos.of(mainframe.getLevel().dimension(), mainframe.getBlockPos());
                if (!at.equals(link.pos())) {
                    // The mainframe was restored from its item somewhere else: follow it (the link is by id).
                    link = new MainframeLink(at, link.id());
                    setChanged();
                }
                var route = ai.ezfox.cccloud.network.NetworkService.route(uplinkId);
                if (route == null) {
                    newStatus = CloudStatus.CONNECTING; // network not computed yet
                    routeState = null;
                } else if (!route.connected()) {
                    routeState = ai.ezfox.cccloud.network.NetworkService.overCapacity(uplinkId) ? "over_capacity" : "no_route";
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
                structureDirty = chartsDirty = true;
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

    // ---- wireless redstone (v0.7) -------------------------------------------------------------------------------------

    public ai.ezfox.cccloud.redstone.RedstoneMode redstoneMode() {
        return rsMode;
    }

    public String redstoneChannel() {
        return rsChannel;
    }

    /** Transmitter: the redstone signal this uplink receives from its neighbours (0–15). */
    public int redstoneInput() {
        return rsInput;
    }

    /** Receiver: the signal this uplink emits on every side (0–15). */
    public int redstoneOutput() {
        return rsOutput;
    }

    /** Screen action: mode and channel (the channel is validated by the caller). */
    public void setRedstone(ai.ezfox.cccloud.redstone.RedstoneMode mode, String channel) {
        rsMode = mode;
        rsChannel = channel;
        readRedstoneInput();
        setChanged();
        ai.ezfox.cccloud.redstone.RedstoneService.markDirty();
    }

    /** Block neighbour update: transmitters re-read their input. */
    public void onNeighborChanged() {
        if (rsMode == ai.ezfox.cccloud.redstone.RedstoneMode.TRANSMIT && readRedstoneInput()) ai.ezfox.cccloud.redstone.RedstoneService.markDirty();
    }

    private boolean readRedstoneInput() {
        if (level == null) return false;
        int value = rsMode == ai.ezfox.cccloud.redstone.RedstoneMode.TRANSMIT ? level.getBestNeighborSignal(getBlockPos()) : 0;
        if (value == rsInput) return false;
        rsInput = value;
        return true;
    }

    /**
     * Receiver output; changes at most every 2 ticks (redstone clocks faster than that are smoothed).
     *
     * @return false if the change has to wait for the next tick
     */
    public boolean applyRedstoneOutput(int value, long gameTime) {
        value = Math.max(0, Math.min(15, value));
        if (value == rsOutput) return true;
        if (gameTime - rsLastChange < 2) return false;
        rsOutput = value;
        rsLastChange = gameTime;
        setChanged();
        if (level != null) level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
        return true;
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
            structureDirty = chartsDirty = true;
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
        if (!ai.ezfox.cccloud.access.Access.mayRelink(player, link == null ? null : link.id(), newLink.id())) {
            player.sendSystemMessage(Component.translatable("cccloud.link_card.linked_elsewhere").withStyle(ChatFormatting.RED));
            return;
        }
        var here = GlobalPos.of(serverLevel.dimension(), getBlockPos());
        // Range is checked against the whole network (mainframe and relays), not only the mainframe.
        var error = ai.ezfox.cccloud.network.NetworkService.checkLink(mainframe, serverLevel, getBlockPos(),
            ai.ezfox.cccloud.network.NetworkService.deviceType(this), uplinkId.toString(), wired);
        if (error != null) {
            player.sendSystemMessage(error.copy().withStyle(ChatFormatting.RED));
            return;
        }
        if (link != null && !link.id().equals(newLink.id())) unlinkFrom(link);
        link = newLink;
        linkedBy = player.getUUID();
        legacyLink = false;
        ai.ezfox.cccloud.network.NetworkService.markDirty();
        mainframe.linkUplink(uplinkId, here);
        synchronized (this) {
            structureDirty = chartsDirty = true;
        }
        setChanged();
        updateStatus(serverLevel);
        CCCloud.LOGGER.info("[cccloud] {} bound uplink {} to {}", player.getGameProfile().name(), uplinkId, mainframe.getDisplayName());
        player.sendSystemMessage(Component.translatable("cccloud.uplink.bound", newLink.describe()).withStyle(ChatFormatting.GREEN));
        var blocked = ai.ezfox.cccloud.claims.ClaimGuard.check(this);
        if (blocked != null) player.sendSystemMessage(ai.ezfox.cccloud.claims.ClaimGuard.linkMessage(blocked));
        ai.ezfox.cccloud.api.CloudEvents.deviceLinked(new ai.ezfox.cccloud.api.CloudEvents.DeviceLinked(newLink.id(), uplinkId, "uplink",
            serverLevel.dimension().identifier().toString(), getBlockPos(), player.getUUID(), player.getGameProfile().name()));
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
        linkedBy = null;
        legacyLink = false;
        ai.ezfox.cccloud.network.NetworkService.markDirty();
        synchronized (this) {
            structureDirty = chartsDirty = true;
        }
        setChanged();
        updateStatus(serverLevel);
        ai.ezfox.cccloud.claims.ClaimGuard.check(this);
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
        var block = claimBlock;
        json.addProperty("title", block != null && title.isEmpty() ? blockedTitle : title);
        json.addProperty("online", status == CloudStatus.ONLINE);
        var array = new JsonArray();
        // claim guard: a blocked card shows the claim instead of its widgets and values
        if (block == null) for (var widget : widgets.values()) array.add(widget.toJson());
        json.add("widgets", array);
        if (block != null) json.add("blocked", ai.ezfox.cccloud.claims.ClaimGuard.json(block));
        if (!pages.isEmpty()) json.add("pages", declarations(pages));
        if (!groups.isEmpty()) json.add("groups", declarations(groups));
        json.addProperty("mode", template || !deviceKind.isEmpty() ? "direct" : auto ? "auto" : "lua");
        if (template) {
            json.addProperty("template", "storage");
            json.addProperty("bridge", templateKind);
        } else if (!deviceKind.isEmpty()) {
            json.addProperty("template", deviceKind);
            var info = deviceCard == null ? null : ai.ezfox.cccloud.compat.card.DeviceCards.settingsInfo(deviceCard);
            if (info != null) json.add("cardSettings", info);
        }
        return json;
    }

    /** Block id of the block the uplink reads ("" if none), to tie saved card settings to it. */
    private String readBlockId() {
        var pos = readPos();
        if (pos == null || level == null || !level.isLoaded(pos)) return "";
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }

    /**
     * Wave 4: the dashboard's card settings for the device card (the Peripheral card's picked methods). The web layer
     * has checked {@code layout.edit}. Server thread. Null on success, else an error code.
     */
    @Override
    public @Nullable String handleCardSettings(com.google.gson.JsonObject settings, String user) {
        if (!isOnline()) return "uplink_offline";
        ai.ezfox.cccloud.compat.card.DeviceCard card;
        synchronized (this) {
            card = deviceCard;
        }
        if (card == null || ai.ezfox.cccloud.compat.card.DeviceCards.settingsInfo(card) == null) return "unsupported";
        String error = ai.ezfox.cccloud.compat.card.DeviceCards.applySettings(card, settings, false);
        if (error != null) return error;
        synchronized (this) {
            if (deviceCard != card) return "unavailable";
            cardSettings = ai.ezfox.cccloud.compat.card.DeviceCards.settings(card);
            cardSettingsFor = readBlockId();
            showDeviceCard(card);
            deviceRevision = ai.ezfox.cccloud.compat.card.DeviceCards.revision(card);
        }
        ai.ezfox.cccloud.compat.card.DeviceCards.refresh(card, deviceOutput);
        setChanged();
        CCCloud.LOGGER.info("[cccloud] {} changed the settings of the {} card on uplink {}: {}", user, card.kind(), uplinkId, Json.GSON.toJson(cardSettings));
        return null;
    }

    public synchronized void setTitle(String title) {
        if (this.title.equals(title)) return;
        this.title = title;
        structureDirty = chartsDirty = true;
    }

    @Override
    public synchronized String getTitle() {
        return title;
    }

    /** Adds or replaces a widget (a replaced widget keeps its position). Returns false if the widget limit is reached. */
    public synchronized boolean putWidget(Widget widget) {
        if (!widgets.containsKey(widget.id()) && widgets.size() >= maxWidgets()) return false;
        widgets.put(widget.id(), widget);
        structureDirty = chartsDirty = true;
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
        structureDirty = chartsDirty = true;
        return true;
    }

    public synchronized boolean putGroup(JsonObject group) {
        String id = group.get("id").getAsString();
        if (!groups.containsKey(id) && groups.size() >= Limits.MAX_GROUPS) return false;
        if (group.equals(groups.get(id))) return true;
        groups.put(id, group);
        structureDirty = chartsDirty = true;
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
        structureDirty = chartsDirty = true;
        return true;
    }

    public synchronized void clearWidgets() {
        if (widgets.isEmpty() && pages.isEmpty() && groups.isEmpty()) return;
        widgets.clear();
        pages.clear();
        groups.clear();
        structureDirty = chartsDirty = true;
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
        if (ai.ezfox.cccloud.claims.ClaimGuard.check(this) != null) return ActionResult.done(ai.ezfox.cccloud.claims.ClaimGuard.ERROR);
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
        // A probe whose target may not be read now (range, claim, moved) does nothing until the card is gone.
        if (card != null && isProbe() && readPos() == null) return ActionResult.done("unavailable");
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
            var ticket = core.craft(craft.name(), craft.count(), user, craft.alertWhenDone(), user.startsWith("rule:") ? "rule" : "dashboard");
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
        if (ai.ezfox.cccloud.claims.ClaimGuard.check(this) != null) return QueryResult.fail(ai.ezfox.cccloud.claims.ClaimGuard.ERROR);
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

    // ---- claim guard (docs/SECURITY.md "Claims") -----------------------------------------------------------------

    /** The block it sticks to (for a computer: the computer), whatever the claim guard says. */
    public BlockPos attachedPos() {
        return getBlockPos().relative(getBlockState().getValue(CloudUplinkBlock.FACING));
    }

    /** Who linked it with the Link Card (null for older links). */
    public @Nullable UUID linkedBy() {
        return linkedBy;
    }

    /** Placed by a player (the block's setPlacedBy): checked at once. */
    public void placedBy(Player player) {
        placedBy = player.getUUID();
        setChanged();
        ai.ezfox.cccloud.claims.ClaimGuard.check(this);
    }

    @Override
    public @Nullable ServerLevel guardLevel() {
        return level instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    /**
     * The block it reads: a probe's target, else the block it sticks to. Also while unlinked: its screen shows the card's
     * preview, and an uplink outside a claim can touch a machine inside it.
     */
    @Override
    public @Nullable BlockPos guardPos() {
        if (isRemoved()) return null;
        return isProbe() ? remoteTarget : attachedPos();
    }

    /** Who linked it (a probe: who set its target; unlinked: who placed it) and the mainframe's owner. */
    @Override
    public List<UUID> guardOwners() {
        var by = isProbe() && remoteBy != null ? remoteBy : linkedBy != null ? linkedBy : placedBy;
        return ai.ezfox.cccloud.claims.ClaimGuard.owners(by, link);
    }

    @Override
    public ai.ezfox.cccloud.claims.ClaimGuard.@Nullable Block claimBlock() {
        return claimBlock;
    }

    /** Blocked: the card, the Computer card and the Storage card go at once (the card keeps its title); unblocked: back. */
    @Override
    public void applyClaimBlock(ai.ezfox.cccloud.claims.ClaimGuard.@Nullable Block block) {
        synchronized (this) {
            if (block != null && claimBlock == null) {
                blockedTitle = title.isEmpty() ? blockedTitle : title;
                claimBlock = block;
                if (deviceCard != null || !deviceKind.isEmpty()) showDeviceCard(null);
                if (template) leaveTemplate();
                if (auto) leaveAuto();
                dirtyValues.clear();
                pendingAlerts.clear();
            }
            claimBlock = block;
            exposedElsewhere = null;
            structureDirty = chartsDirty = true;
        }
        if (block == null) claimRefresh = true;
    }

    @Override
    public String guardLabel() {
        return (isProbe() ? "Cloud Probe " : "uplink ") + uplinkId.toString().substring(0, 8);
    }

    @Override
    public @Nullable UUID guardMainframe() {
        return link == null ? null : link.id();
    }

    @Override
    public boolean guardRemoved() {
        return isRemoved();
    }

    /** Client-side animation state for the block entity renderer. */
    public SpinAnimation clientAnimation() {
        return clientAnimation;
    }
}
