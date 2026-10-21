package ai.ezfox.cccloud.block.entity;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.peripheral.UplinkPeripheral;
import ai.ezfox.cccloud.uplink.ActionValidator;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Widget;
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
import java.util.concurrent.ConcurrentHashMap;
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
public class CloudUplinkBlockEntity extends BlockEntity {
    /** Max pending cloud_action events per computer (token bucket, refilled at 16/s). */
    public static final int MAX_PENDING_EVENTS = 64;
    private static final int STATUS_INTERVAL = 10;

    private UUID uplinkId = UUID.randomUUID();
    private @Nullable MainframeLink link;
    private String title = "";
    private final LinkedHashMap<String, Widget> widgets = new LinkedHashMap<>();

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
        synchronized (this) {
            title = input.getStringOr("title", "");
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
        synchronized (this) {
            output.putString("title", title);
            var array = new JsonArray();
            for (var widget : widgets.values()) array.add(widget.toJson());
            output.putString("widgets", Json.GSON.toJson(array));
        }
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
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!registered) return;
        registered = false;
        CloudNetwork.unregisterUplink(this);
        status = CloudStatus.OFFLINE;
        // Chunk unload: keep the section on the dashboard but show it as offline.
        if (!broken && link != null) WebHub.INSTANCE.publishUplink(link.id(), snapshot());
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
        if (tickCounter++ % STATUS_INTERVAL == 0) updateStatus(level);
        if (link == null) {
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

    private void updateStatus(ServerLevel level) {
        CloudStatus newStatus = CloudStatus.OFFLINE;
        if (link != null) {
            var mainframe = CloudNetwork.mainframe(link.id());
            if (mainframe != null && isInRange(level, mainframe)) {
                newStatus = switch (mainframe.status()) {
                    case ONLINE -> CloudStatus.ONLINE;
                    case CONNECTING -> CloudStatus.CONNECTING;
                    case OFFLINE -> CloudStatus.OFFLINE;
                };
                mainframe.linkUplink(uplinkId, GlobalPos.of(level.dimension(), getBlockPos()));
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

    public boolean isInRange(ServerLevel level, CloudMainframeBlockEntity mainframe) {
        if (mainframe.getLevel() == null) return false;
        return isInRange(isAdvanced(), GlobalPos.of(level.dimension(), getBlockPos()),
            GlobalPos.of(mainframe.getLevel().dimension(), mainframe.getBlockPos()));
    }

    public static boolean isInRange(boolean advanced, GlobalPos uplink, GlobalPos mainframe) {
        if (advanced) return true;
        if (!uplink.dimension().equals(mainframe.dimension())) return false;
        double range = CloudConfig.get(CloudConfig.UPLINK_RANGE);
        return uplink.pos().distSqr(mainframe.pos()) <= range * range;
    }

    // ---- binding (link card) -----------------------------------------------------------------------------------------

    public void bind(MainframeLink newLink, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var mainframe = CloudNetwork.mainframe(newLink.id());
        if (mainframe == null || mainframe.getLevel() == null || !mainframe.getBlockPos().equals(newLink.pos().pos())) {
            player.sendSystemMessage(Component.translatable("cccloud.uplink.mainframe_missing").withStyle(ChatFormatting.RED));
            return;
        }
        if (!mainframe.canManage(player)) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.not_owner").withStyle(ChatFormatting.RED));
            return;
        }
        var here = GlobalPos.of(serverLevel.dimension(), getBlockPos());
        var there = GlobalPos.of(mainframe.getLevel().dimension(), mainframe.getBlockPos());
        if (!isInRange(isAdvanced(), here, there)) {
            if (!here.dimension().equals(there.dimension())) {
                player.sendSystemMessage(Component.translatable("cccloud.uplink.other_dimension").withStyle(ChatFormatting.RED));
            } else {
                int distance = (int) Math.sqrt(here.pos().distSqr(there.pos()));
                player.sendSystemMessage(Component.translatable("cccloud.uplink.out_of_range", distance, CloudConfig.get(CloudConfig.UPLINK_RANGE)).withStyle(ChatFormatting.RED));
            }
            return;
        }
        if (link != null && !link.id().equals(newLink.id())) unlinkFrom(link);
        link = newLink;
        mainframe.linkUplink(uplinkId, here);
        synchronized (this) {
            structureDirty = true;
        }
        setChanged();
        updateStatus(serverLevel);
        CCCloud.LOGGER.info("[cccloud] {} bound uplink {} to {}", player.getGameProfile().name(), uplinkId, mainframe.getDisplayName());
        player.sendSystemMessage(Component.translatable("cccloud.uplink.bound", newLink.describe()).withStyle(ChatFormatting.GREEN));
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
        return json;
    }

    public synchronized void setTitle(String title) {
        if (this.title.equals(title)) return;
        this.title = title;
        structureDirty = true;
    }

    public synchronized String getTitle() {
        return title;
    }

    /** Adds or replaces a widget (a replaced widget keeps its position). Returns false if the widget limit is reached. */
    public synchronized boolean putWidget(Widget widget) {
        if (!widgets.containsKey(widget.id()) && widgets.size() >= CloudConfig.get(CloudConfig.MAX_WIDGETS_PER_UPLINK)) return false;
        widgets.put(widget.id(), widget);
        structureDirty = true;
        return true;
    }

    public synchronized @Nullable Widget getWidget(String id) {
        return widgets.get(id);
    }

    public synchronized void setValue(String id, JsonElement value) {
        var widget = widgets.get(id);
        if (widget == null || widget.value().equals(value)) return;
        widget.setValue(value);
        dirtyValues.add(id);
    }

    public synchronized boolean removeWidget(String id) {
        if (widgets.remove(id) == null) return false;
        structureDirty = true;
        return true;
    }

    public synchronized void clearWidgets() {
        if (widgets.isEmpty()) return;
        widgets.clear();
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

    /** A few glowing "data packets" fly from the uplink to its mainframe when a dashboard command arrives. */
    private void spawnDataPackets() {
        if (!(level instanceof ServerLevel serverLevel) || link == null || !link.pos().dimension().equals(serverLevel.dimension())) return;
        var from = getBlockPos().getCenter();
        var to = link.pos().pos().getCenter().add(0, 0.5, 0);
        var delta = to.subtract(from);
        if (delta.lengthSqr() > 64 * 64) return;
        var velocity = delta.scale(1.0 / 20); // reaches the mainframe within the particle lifetime (20 ticks)
        for (int i = 0; i < 3; i++) {
            serverLevel.sendParticles(ai.ezfox.cccloud.ModRegistry.DATA_PACKET.get(), from.x, from.y + i * 0.08, from.z, 0,
                velocity.x, velocity.y, velocity.z, 1.0);
        }
    }

    // ---- computers & actions -----------------------------------------------------------------------------------------

    public void attach(IComputerAccess computer) {
        computers.put(computer, new TokenBucket(MAX_PENDING_EVENTS, 16));
    }

    public void detach(IComputerAccess computer) {
        computers.remove(computer);
    }

    public int computerCount() {
        return computers.size();
    }

    /**
     * Handles an action from the dashboard (server thread). Validates it against the widget definition, updates
     * toggle/slider values and queues {@code cloud_action} on every attached computer.
     *
     * @return null on success, otherwise the error code.
     */
    public @Nullable String handleAction(String widgetId, JsonElement rawValue, String user, boolean pinVerified) {
        if (!isOnline()) return "uplink_offline";
        ActionValidator.Result result;
        String type;
        synchronized (this) {
            var widget = widgets.get(widgetId);
            result = ActionValidator.validate(widget, rawValue, pinVerified);
            if (!result.ok()) return result.error();
            type = widget.type().id();
            if (!type.equals("button")) {
                widget.setValue(result.value());
                dirtyValues.add(widgetId);
            }
        }
        spawnDataPackets();
        Object luaValue = Json.toLua(result.value());
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
        return null;
    }

    /** Client-side animation state for the block entity renderer. */
    public SpinAnimation clientAnimation() {
        return clientAnimation;
    }
}
