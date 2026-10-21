package ai.ezfox.cccloud.block.entity;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.peripheral.MainframePeripheral;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.CloudWebServer;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Block entity of the lower mainframe half. Owns the identity, the dashboard switch, the owner/trust list and the
 * list of linked uplinks, and publishes its state into the {@link WebHub}.
 */
public class CloudMainframeBlockEntity extends BlockEntity {
    private record LinkedUplink(UUID id, GlobalPos pos) {
        static final Codec<LinkedUplink> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(LinkedUplink::id),
            GlobalPos.CODEC.fieldOf("pos").forGetter(LinkedUplink::pos)
        ).apply(i, LinkedUplink::new));
    }

    private UUID mainframeId = UUID.randomUUID();
    private boolean enabled = true;
    private @Nullable UUID owner;
    private String ownerName = "";
    private final Map<UUID, String> trusted = new LinkedHashMap<>();
    private final Map<UUID, GlobalPos> linkedUplinks = new LinkedHashMap<>();

    private boolean registered;
    private final SpinAnimation clientAnimation = new SpinAnimation();
    private boolean redstoneChecked;
    private boolean powered;
    private volatile CloudStatus status = CloudStatus.OFFLINE;
    private volatile boolean online;
    private final MainframePeripheral peripheral = new MainframePeripheral(this);

    public CloudMainframeBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MAINFRAME_BE.get(), pos, state);
    }

    // ---- identity & persistence ----------------------------------------------------------------------------------

    public UUID getMainframeId() {
        return mainframeId;
    }

    public String getDisplayName() {
        var p = getBlockPos();
        return "Mainframe @ " + p.getX() + " " + p.getY() + " " + p.getZ();
    }

    public MainframePeripheral peripheral() {
        return peripheral;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.read("mainframe_id", UUIDUtil.STRING_CODEC).ifPresent(id -> mainframeId = id);
        enabled = input.getBooleanOr("enabled", true);
        owner = input.read("owner", UUIDUtil.STRING_CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "");
        trusted.clear();
        input.read("trusted", NameAndId.CODEC.listOf()).ifPresent(list -> list.forEach(t -> trusted.put(t.id(), t.name())));
        linkedUplinks.clear();
        input.read("uplinks", LinkedUplink.CODEC.listOf()).ifPresent(list -> list.forEach(u -> linkedUplinks.put(u.id(), u.pos())));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("mainframe_id", UUIDUtil.STRING_CODEC, mainframeId);
        output.putBoolean("enabled", enabled);
        output.storeNullable("owner", UUIDUtil.STRING_CODEC, owner);
        output.putString("owner_name", ownerName);
        output.store("trusted", NameAndId.CODEC.listOf(), trusted.entrySet().stream().map(e -> new NameAndId(e.getKey(), e.getValue())).toList());
        output.store("uplinks", LinkedUplink.CODEC.listOf(), linkedUplinks.entrySet().stream().map(e -> new LinkedUplink(e.getKey(), e.getValue())).toList());
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide()) return;
        if (!CloudNetwork.registerMainframe(this)) {
            // Another loaded mainframe uses the same id (e.g. copied with pick-block + NBT): give this one a new identity.
            mainframeId = UUID.randomUUID();
            linkedUplinks.clear();
            setChanged();
            CloudNetwork.registerMainframe(this);
        }
        registered = true;
        syncAcl();
    }

    /** Mirrors owner + trust list into the auth store so device rights can be checked while this chunk is unloaded. */
    private void syncAcl() {
        if (level instanceof ServerLevel serverLevel) {
            WebAuthData.get(serverLevel.getServer()).setAcl(mainframeId, owner, trusted.keySet());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (registered) {
            registered = false;
            CloudNetwork.unregisterMainframe(this);
            online = false;
            WebHub.INSTANCE.publishMainframe(mainframeId, getDisplayName(), false, enabled);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            // The mainframe is gone for good: drop its pairing code and devices.
            var store = WebAuthData.get(serverLevel.getServer());
            int devices = store.devices(mainframeId).size();
            store.forgetMainframe(mainframeId);
            CCCloud.LOGGER.info("[cccloud] {} was removed; its pairing code, PIN and {} device(s) were deleted", getDisplayName(), devices);
            WebHub.INSTANCE.removeMainframe(mainframeId);
        }
    }

    public void serverTick(ServerLevel level) {
        long cost = ai.ezfox.cccloud.network.TickCost.start();
        try {
            tick(level);
        } finally {
            ai.ezfox.cccloud.network.TickCost.end(cost);
        }
    }

    private void tick(ServerLevel level) {
        if (!redstoneChecked) updateRedstone();

        boolean nowOnline = !powered && CloudConfig.get(CloudConfig.ALLOW_CLOUD);
        online = nowOnline;
        CloudStatus newStatus;
        if (!nowOnline || !enabled || !CloudConfig.get(CloudConfig.WEB_ENABLED)) {
            newStatus = CloudStatus.OFFLINE;
        } else if (!CloudWebServer.isRunning()) {
            newStatus = CloudStatus.CONNECTING;
        } else {
            newStatus = CloudStatus.ONLINE;
        }
        status = newStatus;

        BlockState state = getBlockState();
        if (state.getValue(CloudMainframeBlock.STATUS) != newStatus) {
            level.setBlock(getBlockPos(), state.setValue(CloudMainframeBlock.STATUS, newStatus), Block.UPDATE_ALL);
            BlockState upper = level.getBlockState(getBlockPos().above());
            if (upper.is(state.getBlock()) && upper.getValue(CloudMainframeBlock.HALF) == DoubleBlockHalf.UPPER
                && upper.getValue(CloudMainframeBlock.STATUS) != newStatus) {
                level.setBlock(getBlockPos().above(), upper.setValue(CloudMainframeBlock.STATUS, newStatus), Block.UPDATE_ALL);
            }
        }

        // Cheap: the hub ignores unchanged state.
        WebHub.INSTANCE.publishMainframe(mainframeId, getDisplayName(), nowOnline, enabled);
    }

    public void updateRedstone() {
        if (level == null) return;
        redstoneChecked = true;
        powered = CloudMainframeBlock.isPowered(level, getBlockPos());
    }

    // ---- state -------------------------------------------------------------------------------------------------------

    /** Online = loaded, cloud allowed and not redstone-killed (independent of the dashboard switch). */
    public boolean isOnline() {
        return online && !isRemoved();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPowered() {
        return powered;
    }

    public CloudStatus status() {
        return status;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        setChanged();
        CCCloud.LOGGER.info("[cccloud] Dashboard of {} {}", getDisplayName(), enabled ? "enabled" : "disabled");
    }

    // ---- ownership ---------------------------------------------------------------------------------------------------

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().name();
        setChanged();
        syncAcl();
    }

    public @Nullable UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Map<UUID, String> getTrusted() {
        return trusted;
    }

    /** Owner, trusted players and operators (permission level 2+) may manage the mainframe. */
    public boolean canManage(Player player) {
        if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return true;
        UUID id = player.getUUID();
        return id.equals(owner) || trusted.containsKey(id);
    }

    public boolean trust(NameAndId player) {
        if (player.id().equals(owner)) return false;
        boolean added = trusted.put(player.id(), player.name()) == null;
        setChanged();
        syncAcl();
        return added;
    }

    public boolean untrust(UUID player) {
        boolean removed = trusted.remove(player) != null;
        if (removed) {
            setChanged();
            syncAcl();
        }
        return removed;
    }

    // ---- uplinks -----------------------------------------------------------------------------------------------------

    public void linkUplink(UUID uplinkId, GlobalPos pos) {
        if (!pos.equals(linkedUplinks.put(uplinkId, pos))) setChanged();
    }

    public void unlinkUplink(UUID uplinkId) {
        if (linkedUplinks.remove(uplinkId) != null) setChanged();
        WebHub.INSTANCE.publishUplinkRemoved(mainframeId, uplinkId.toString());
    }

    public Map<UUID, GlobalPos> getLinkedUplinks() {
        return linkedUplinks;
    }

    // ---- interaction -------------------------------------------------------------------------------------------------

    public void toggleEnabled(Player player) {
        if (!canManage(player)) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.not_owner").withStyle(ChatFormatting.RED));
            return;
        }
        setEnabled(!enabled);
        player.sendSystemMessage(Component.translatable(enabled ? "cccloud.mainframe.enabled" : "cccloud.mainframe.disabled")
            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
    }

    public void showStatus(Player player) {
        player.sendSystemMessage(Component.translatable("cccloud.mainframe.header", getDisplayName()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("cccloud.mainframe.status_line", status.displayName(),
            Component.translatable(enabled ? "cccloud.mainframe.dashboard_on" : "cccloud.mainframe.dashboard_off")));
        if (!CloudConfig.get(CloudConfig.ALLOW_CLOUD)) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.cloud_disabled").withStyle(ChatFormatting.RED));
        } else if (powered) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.killed").withStyle(ChatFormatting.RED));
        }
        if (!canManage(player)) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.not_owner").withStyle(ChatFormatting.RED));
            return;
        }
        if (!CloudConfig.get(CloudConfig.WEB_ENABLED)) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.web_disabled").withStyle(ChatFormatting.RED));
        } else if (!CloudWebServer.isRunning()) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.web_down").withStyle(ChatFormatting.RED));
        } else {
            String url = CloudWebServer.dashboardUrl();
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.url", link(url)));
        }
        if (level instanceof ServerLevel serverLevel && enabled) {
            var code = WebAuthData.get(serverLevel.getServer()).currentCode(mainframeId, player.getUUID());
            long minutes = Math.max(1, (code.expiresAt() - System.currentTimeMillis() + 59_999) / 60_000);
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.pairing_code",
                Component.literal(AuthStore.formatCode(code.code())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), minutes));
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.pairing_hint").withStyle(ChatFormatting.GRAY));
            int devices = WebAuthData.get(serverLevel.getServer()).devices(mainframeId).size();
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.counts", devices, linkedUplinks.size()).withStyle(ChatFormatting.GRAY));
        }
    }

    public void revokeTokens(Player player) {
        if (!canManage(player)) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.not_owner").withStyle(ChatFormatting.RED));
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) return;
        var removed = revoke(serverLevel, "all", player.getGameProfile().name());
        player.sendSystemMessage(Component.translatable("cccloud.mainframe.revoked_all", removed.size()).withStyle(ChatFormatting.YELLOW));
    }

    /** Revokes devices by name or "all", disconnects them and writes an audit line. */
    public List<AuthStore.Device> revoke(ServerLevel level, String nameOrAll, String by) {
        var removed = WebAuthData.get(level.getServer()).revoke(mainframeId, nameOrAll);
        for (var device : removed) {
            WebHub.INSTANCE.closeSessions(mainframeId, device.tokenHash());
            CCCloud.LOGGER.info("[cccloud] {} revoked device '{}' of {}", by, device.name(), getDisplayName());
        }
        return new ArrayList<>(removed);
    }

    private static MutableComponent link(String url) {
        return Component.literal(url).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
            .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(url))));
    }

    /** Client-side animation state for the block entity renderer. */
    public SpinAnimation clientAnimation() {
        return clientAnimation;
    }
}
