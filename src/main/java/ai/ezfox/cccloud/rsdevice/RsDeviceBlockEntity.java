package ai.ezfox.cccloud.rsdevice;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.network.graph.NetworkGraph;
import ai.ezfox.cccloud.web.TokenBucket;
import com.google.gson.JsonElement;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Block entity of all four Cloud Redstone devices (docs/SPEC.md "Cloud Redstone devices"). A network device like an
 * uplink (Link Card, range, route, capacity, FE), but without a section of its own: the mainframe shows it on a group
 * card ({@link RsDevices}). Holds the device's settings and state; everything that changes the state goes through
 * {@link #applyState} (block state, neighbours, the dashboard copy). Server thread only.
 */
public class RsDeviceBlockEntity extends BlockEntity {
    private static final int STATUS_INTERVAL = 10;

    private UUID deviceId = UUID.randomUUID();
    private @Nullable MainframeLink link;
    private String name = "";
    private String group = "";
    /** Switch: analog mode (0–15 from a slider) instead of on/off (0/15). */
    private boolean analog;
    /** Switch: the output level 0–15. */
    private int output;
    /** Analog switch: level when switched on · lamp: brightness. */
    private int preset = 15;
    /** Lamp: its own switch (dashboard / in-world), without redstone. */
    private boolean lampOn;
    /** Lamp: a redstone signal lights it too. */
    private boolean follow;
    /** Button: pulse length in ticks. */
    private int pulse = RsDevice.PULSE_DEFAULT;
    private boolean pin;
    private boolean confirm;
    /** Lamp and sensor: the strongest neighbour signal. Not saved: sampled on the first tick after loading. */
    private int input;
    private boolean inputPending;
    private volatile CloudStatus status = CloudStatus.OFFLINE;
    /** "no_route", "over_capacity" or null (screen). */
    private volatile @Nullable String routeState;
    private boolean registered;
    /** The dashboard copy is on the mainframe's cards (see {@link #updateStatus}). */
    private boolean listed;
    private boolean broken;
    private boolean chunkUnloading;
    private int tickCounter;
    private boolean statusPending = true;
    /** Changes from the dashboard, rules, scenes and the API (in-world clicks are not limited, like a lever). */
    private @Nullable TokenBucket changes;

    public RsDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RS_DEVICE_BE.get(), pos, state);
    }

    public RsKind kind() {
        return getBlockState().getBlock() instanceof RsDeviceBlock block ? block.kind() : RsKind.SWITCH;
    }

    public UUID deviceId() {
        return deviceId;
    }

    public @Nullable MainframeLink getLink() {
        return link;
    }

    public CloudStatus status() {
        return status;
    }

    public @Nullable String routeState() {
        return routeState;
    }

    public String name() {
        return name;
    }

    public String group() {
        return group;
    }

    public boolean analog() {
        return analog;
    }

    public int preset() {
        return preset;
    }

    public int pulse() {
        return pulse;
    }

    public boolean follow() {
        return follow;
    }

    public boolean pin() {
        return pin;
    }

    public boolean confirm() {
        return confirm;
    }

    public int input() {
        return input;
    }

    /** Switch: the redstone output (0–15). */
    public int output() {
        return output;
    }

    /** The device's level: switch output, lamp light, sensor input, 15 while the button is pressed. */
    public int signalLevel() {
        return switch (kind()) {
            case SWITCH -> output;
            case BUTTON -> getBlockState().hasProperty(RsPlateBlock.POWERED) && getBlockState().getValue(RsPlateBlock.POWERED) ? 15 : 0;
            case LAMP -> Math.max(lampOn ? preset : 0, follow ? input : 0);
            case SENSOR -> input;
        };
    }

    /** The dashboard's copy of this device. */
    public RsDevice record() {
        var p = getBlockPos();
        String dim = level == null ? "" : level.dimension().identifier().toString();
        return new RsDevice(deviceId, kind(), name, group, dim, p.getX(), p.getY(), p.getZ(), analog, signalLevel(), lampOn, preset, pulse, follow, pin,
            confirm);
    }

    // ---- persistence -------------------------------------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.read("device_id", UUIDUtil.STRING_CODEC).ifPresent(id -> deviceId = id);
        link = in.read("link", MainframeLink.CODEC).orElse(null);
        name = clip(in.getStringOr("name", ""));
        group = clip(in.getStringOr("group", ""));
        analog = in.getBooleanOr("analog", false);
        output = RsDevice.clampLevel(in.getIntOr("output", 0));
        preset = Math.max(1, RsDevice.clampLevel(in.getIntOr("preset", 15)));
        lampOn = in.getBooleanOr("lamp_on", false);
        follow = in.getBooleanOr("follow", false);
        pulse = RsDevice.clampPulse(in.getIntOr("pulse", RsDevice.PULSE_DEFAULT));
        pin = in.getBooleanOr("pin", false);
        confirm = in.getBooleanOr("confirm", false);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("device_id", UUIDUtil.STRING_CODEC, deviceId);
        out.storeNullable("link", MainframeLink.CODEC, link);
        out.putInt("net_v", 1);
        out.putString("name", name);
        out.putString("group", group);
        out.putBoolean("analog", analog);
        out.putInt("output", output);
        out.putInt("preset", preset);
        out.putBoolean("lamp_on", lampOn);
        out.putBoolean("follow", follow);
        out.putInt("pulse", pulse);
        out.putBoolean("pin", pin);
        out.putBoolean("confirm", confirm);
    }

    private static String clip(String text) {
        String s = text.strip();
        return RsDevice.validText(s) ? s : "";
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        chunkUnloading = false;
        if (level == null || level.isClientSide()) return;
        if (!RsDevices.register(this)) {
            // A copy of this block (structure placement, creative pick with data): become a new device.
            deviceId = UUID.randomUUID();
            link = null;
            setChanged();
            RsDevices.register(this);
        }
        registered = true;
        listed = false;
        tickCounter = Math.floorMod(Long.hashCode(getBlockPos().asLong()), STATUS_INTERVAL);
        statusPending = true;
        inputPending = true;
        NetworkService.markDirty(link == null ? null : link.id());
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        chunkUnloading = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!registered) return;
        registered = false;
        // Removed without the break path (/setblock, explosions without side effects, block movers): the mainframe
        // drops the device once its block stays missing (OrphanSections).
        if (!chunkUnloading && !broken && link != null && level instanceof ServerLevel serverLevel) {
            ai.ezfox.cccloud.network.OrphanSections.removedInPlace(GlobalPos.of(serverLevel.dimension(), getBlockPos()), deviceId, link.id(),
                serverLevel.getServer().getTickCount());
        }
        RsDevices.unregister(this);
        NetworkService.markDirty(link == null ? null : link.id());
        status = CloudStatus.OFFLINE;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        broken = true;
        if (link != null) unlinkFrom(link);
    }

    private void unlinkFrom(MainframeLink old) {
        RsDevices.forget(old.id(), deviceId);
        var mainframe = CloudNetwork.mainframe(old.id());
        if (mainframe != null) mainframe.unlinkUplink(deviceId);
    }

    // ---- server tick -------------------------------------------------------------------------------------------------

    public void serverTick(ServerLevel level) {
        long cost = ai.ezfox.cccloud.network.TickCost.start();
        try {
            if (inputPending) {
                // A lever that was already on when the chunk loaded (or before the device was placed) sends no
                // neighbour update; sample once like the wireless-redstone transmitter, neighborChanged does the rest.
                inputPending = false;
                readInput();
            }
            if (statusPending || tickCounter % STATUS_INTERVAL == 0) {
                statusPending = false;
                updateStatus(level);
            }
            tickCounter++;
        } finally {
            ai.ezfox.cccloud.network.TickCost.end(ai.ezfox.cccloud.network.TickCost.RSDEVICE, cost);
        }
    }

    private void updateStatus(ServerLevel level) {
        CloudStatus newStatus = CloudStatus.OFFLINE;
        String newRoute = null;
        if (link != null) {
            var mainframe = CloudNetwork.mainframe(link.id());
            if (mainframe != null) {
                mainframe.linkUplink(deviceId, GlobalPos.of(level.dimension(), getBlockPos()));
                var at = GlobalPos.of(mainframe.getLevel().dimension(), mainframe.getBlockPos());
                if (!at.equals(link.pos())) {
                    // The mainframe was restored from its item somewhere else: follow it (the link is by id).
                    link = new MainframeLink(at, link.id());
                    setChanged();
                }
                // A device that arrived with its link in the block entity data (/setblock, a structure, a schematic
                // paste) has no copy on the cards yet; without this it only showed up after its first state change.
                if (!listed) listed = RsDevices.list(this);
                var route = NetworkService.route(deviceId);
                if (route == null) {
                    newStatus = CloudStatus.CONNECTING; // network not computed yet
                } else if (!route.connected()) {
                    newRoute = NetworkService.overCapacity(deviceId) ? "over_capacity" : "no_route";
                } else {
                    newStatus = switch (mainframe.status()) {
                        case ONLINE -> CloudStatus.ONLINE;
                        case CONNECTING -> CloudStatus.CONNECTING;
                        case OFFLINE -> CloudStatus.OFFLINE;
                    };
                }
            }
        }
        routeState = newRoute;
        status = newStatus;
        var state = getBlockState();
        if (state.hasProperty(RsPlateBlock.STATUS) && state.getValue(RsPlateBlock.STATUS) != newStatus) {
            level.setBlock(getBlockPos(), state.setValue(RsPlateBlock.STATUS, newStatus), Block.UPDATE_CLIENTS);
        }
    }

    /** Connected to its mainframe over the network. */
    public boolean hasRoute() {
        var route = NetworkService.route(deviceId);
        return route != null && route.connected();
    }

    // ---- state -------------------------------------------------------------------------------------------------------

    /** Block neighbour update: lamps and sensors re-read their input. */
    public void onNeighborChanged() {
        var kind = kind();
        if (kind == RsKind.SENSOR || kind == RsKind.LAMP) readInput();
    }

    private void readInput() {
        if (level == null) return;
        var kind = kind();
        if (kind != RsKind.SENSOR && kind != RsKind.LAMP) return;
        int value = level.getBestNeighborSignal(getBlockPos());
        if (value == input) return;
        input = value;
        applyState();
    }

    /**
     * Brings the block in line with the device's level (switch/sensor {@code powered}, lamp {@code level}), wakes up the
     * neighbours the switch powers and updates the dashboard copy.
     */
    private void applyState() {
        if (level == null || level.isClientSide()) return;
        var state = getBlockState();
        int lvl = signalLevel();
        var next = switch (kind()) {
            case SWITCH, SENSOR -> state.setValue(RsPlateBlock.POWERED, lvl > 0);
            case LAMP -> state.setValue(RsLampBlock.LEVEL, lvl);
            case BUTTON -> state;
        };
        if (next != state) level.setBlock(getBlockPos(), next, Block.UPDATE_ALL);
        // an analog level change keeps the block state: tell the neighbours anyway
        if (kind() == RsKind.SWITCH) RsPlateBlock.updateNeighbours(next, level, getBlockPos());
        setChanged();
        RsDevices.changed(this);
    }

    /** Switch: output level 0–15 (analog: also remembered as the level for "on"). */
    void setOutput(int value) {
        value = RsDevice.clampLevel(value);
        if (!analog && value > 0) value = 15;
        if (value == output) return;
        if (analog && value > 0) preset = value;
        output = value;
        applyState();
    }

    /** Switch: on (15, or the remembered level in analog mode) or off. */
    void setSwitch(boolean on) {
        setOutput(on ? (analog ? preset : 15) : 0);
    }

    /** Lamp: its own switch. */
    void setLamp(boolean on) {
        if (lampOn == on) return;
        lampOn = on;
        applyState();
    }

    /** Lamp: the dimmer (0 = off, 1–15 = on at that brightness). */
    void setDimmer(int value) {
        value = RsDevice.clampLevel(value);
        if (value == 0) {
            setLamp(false);
            return;
        }
        if (lampOn && preset == value) return;
        lampOn = true;
        preset = value;
        applyState();
    }

    /** Button: a pulse of {@link #pulse} ticks; ignored while pressed (like a vanilla button). */
    void press(@Nullable Player player) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var state = getBlockState();
        if (!state.hasProperty(RsPlateBlock.POWERED) || state.getValue(RsPlateBlock.POWERED)) return;
        var pressed = state.setValue(RsPlateBlock.POWERED, true);
        serverLevel.setBlock(getBlockPos(), pressed, Block.UPDATE_ALL);
        RsPlateBlock.updateNeighbours(pressed, serverLevel, getBlockPos());
        serverLevel.scheduleTick(getBlockPos(), pressed.getBlock(), pulse);
        // null: everybody hears it (the click is handled on the server only, the client plays nothing itself)
        serverLevel.playSound(null, getBlockPos(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS);
        setChanged();
        RsDevices.changed(this);
    }

    /** Button: the scheduled end of the pulse. */
    void release() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var state = getBlockState();
        if (!state.getValue(RsPlateBlock.POWERED)) return;
        var released = state.setValue(RsPlateBlock.POWERED, false);
        serverLevel.setBlock(getBlockPos(), released, Block.UPDATE_ALL);
        RsPlateBlock.updateNeighbours(released, serverLevel, getBlockPos());
        serverLevel.playSound(null, getBlockPos(), SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS);
        RsDevices.changed(this);
    }

    /** In-world right-click (not sneaking): the switch and the lamp toggle, the button presses. */
    public void useInWorld(Player player) {
        switch (kind()) {
            case SWITCH -> {
                setSwitch(output == 0);
                click(output > 0);
            }
            case LAMP -> {
                setLamp(!lampOn);
                click(lampOn);
            }
            case BUTTON -> press(player);
            case SENSOR -> {
            }
        }
    }

    private void click(boolean on) {
        if (level != null) level.playSound(null, getBlockPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, on ? 0.6f : 0.5f);
    }

    // ---- dashboard, rules, scenes, API ---------------------------------------------------------------------------------

    private TokenBucket changes() {
        // the limit follows the config (reload): a bucket in use keeps what was used, capped at the new burst
        double perSecond = CloudConfig.get(CloudConfig.RS_DEVICE_CHANGES_PER_SECOND);
        changes = TokenBucket.ensure(changes, Math.max(1, perSecond * 2), perSecond);
        return changes;
    }

    /**
     * An action from a card (validated against the widget already). Null if it was applied, else the error:
     * {@code device_offline} (no route to the mainframe), {@code rate_limited}, {@code not_interactive}.
     */
    @Nullable String apply(RsCards.Control control, JsonElement value, String user) {
        if (status != CloudStatus.ONLINE) return "device_offline";
        var kind = kind();
        if (kind == RsKind.SENSOR) return "not_interactive";
        if ((control == RsCards.Control.ALL_ON || control == RsCards.Control.ALL_OFF) && !kind.switchable()) return null;
        if (!changes().tryAcquire()) return "rate_limited";
        switch (control) {
            case ALL_ON, ALL_OFF -> {
                boolean on = control == RsCards.Control.ALL_ON;
                if (kind == RsKind.SWITCH) setSwitch(on);
                else setLamp(on);
            }
            case MAIN -> {
                switch (kind) {
                    case SWITCH -> {
                        if (analog) setOutput((int) Math.round(value.getAsDouble()));
                        else setSwitch(value.getAsBoolean());
                    }
                    case LAMP -> setLamp(value.getAsBoolean());
                    case BUTTON -> press(null);
                    default -> {
                        return "not_interactive";
                    }
                }
            }
            case BRIGHTNESS -> {
                if (kind != RsKind.LAMP) return "not_interactive";
                setDimmer((int) Math.round(value.getAsDouble()));
            }
            case LEVEL -> {
                return "not_interactive";
            }
        }
        return null;
    }

    // ---- settings (screen) -------------------------------------------------------------------------------------------

    /** Name and group (validated by {@link RsScreens}). */
    void setNameAndGroup(String newName, String newGroup) {
        if (newName.equals(name) && newGroup.equals(group)) return;
        name = newName;
        group = newGroup;
        setChanged();
        RsDevices.changed(this);
    }

    /** Test hook (scale test): name and group like the screen sets them. */
    public void setNameAndGroupForTest(String newName, String newGroup) {
        setNameAndGroup(newName, newGroup);
    }

    void setAnalog(boolean value) {
        if (kind() != RsKind.SWITCH || analog == value) return;
        analog = value;
        if (!analog && output > 0) output = 15;
        applyState();
    }

    void setPulse(int ticks) {
        pulse = RsDevice.clampPulse(ticks);
        setChanged();
        RsDevices.changed(this);
    }

    void setFollow(boolean value) {
        if (follow == value) return;
        follow = value;
        applyState();
    }

    void setProtection(boolean newPin, boolean newConfirm) {
        if (pin == newPin && confirm == newConfirm) return;
        pin = newPin;
        confirm = newConfirm;
        setChanged();
        RsDevices.changed(this);
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
        // Range (and capacity) are checked against the whole network, like an uplink; redstone devices use radio only.
        var error = NetworkService.checkLink(mainframe, serverLevel, getBlockPos(), NetworkGraph.DeviceType.REDSTONE, deviceId.toString(), null);
        if (error != null) {
            player.sendSystemMessage(error.copy().withStyle(ChatFormatting.RED));
            return;
        }
        if (link != null && !link.id().equals(newLink.id())) unlinkFrom(link);
        link = newLink;
        listed = false;
        NetworkService.markDirty();
        mainframe.linkUplink(deviceId, GlobalPos.of(serverLevel.dimension(), getBlockPos()));
        setChanged();
        RsDevices.changed(this);
        updateStatus(serverLevel);
        CCCloud.LOGGER.info("[cccloud] {} bound the {} at {} to {}", player.getGameProfile().name(), kind().block, getBlockPos(), mainframe.getDisplayName());
        player.sendSystemMessage(Component.translatable("cccloud.rsdevice.bound", record().displayName(), newLink.describe()).withStyle(ChatFormatting.GREEN));
        ai.ezfox.cccloud.api.CloudEvents.deviceLinked(new ai.ezfox.cccloud.api.CloudEvents.DeviceLinked(newLink.id(), deviceId, "redstone:" + kind().id,
            serverLevel.dimension().identifier().toString(), getBlockPos(), player.getUUID(), player.getGameProfile().name()));
    }

    /** Unlinks the device from its mainframe (screen; rights are checked by the caller). */
    public void unlink(String by) {
        if (link == null || !(level instanceof ServerLevel serverLevel)) return;
        var old = link;
        unlinkFrom(old);
        link = null;
        listed = false;
        NetworkService.markDirty();
        setChanged();
        updateStatus(serverLevel);
        CCCloud.LOGGER.info("[cccloud] {} unlinked the {} at {} from {}", by, kind().block, getBlockPos(), old.describe());
    }

    /** Test hook: link without the range check. */
    public void forceLinkForTest(MainframeLink newLink) {
        link = newLink;
        listed = false;
        var mainframe = CloudNetwork.mainframe(newLink.id());
        if (mainframe != null && level != null) mainframe.linkUplink(deviceId, GlobalPos.of(level.dimension(), getBlockPos()));
        NetworkService.markDirty();
        RsDevices.changed(this);
    }
}
