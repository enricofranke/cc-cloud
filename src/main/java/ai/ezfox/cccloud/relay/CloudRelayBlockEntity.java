package ai.ezfox.cccloud.relay;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.entity.SpinAnimation;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.network.graph.RelaySharing;
import ai.ezfox.cccloud.power.MainframeEnergy;
import ai.ezfox.cccloud.power.PowerState;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
 * A relay: a node of the cloud network with its own FE buffer. A relay belongs to the player who placed it and carries
 * only the networks of that player, their team and its allies, unless its owner shares it with everyone
 * ({@link RelaySharing}, docs/SPEC.md "Relays belong to a team"); relays without an owner (placed before relays had
 * owners, or by a machine) carry every network. Ender Relays additionally belong to the ender group of the mainframe
 * they were linked to with the Link Card. Topology changes (load, unload, break, power flips, ender links, the share
 * setting) mark the network dirty; the {@link NetworkService} recomputes routes, never this block entity per tick.
 */
public class CloudRelayBlockEntity extends BlockEntity {
    private UUID relayId = UUID.randomUUID();
    /** Ender relays: the mainframe whose ender group this relay joined. */
    private @Nullable UUID enderGroup;
    /** The player who placed it; null = no owner (placed before relays had owners, or by a machine): carries every network. */
    private @Nullable UUID owner;
    private String ownerName = "";
    /** Its owner's choice; null = the server default ({@code relayShareDefault}). */
    private RelaySharing.@Nullable Share share;
    private final MainframeEnergy energy;
    private final PowerState power = new PowerState();
    private boolean hasPower;
    private boolean registered;
    private int ticks;
    private final SpinAnimation clientAnimation = new SpinAnimation();
    private final ai.ezfox.cccloud.peripheral.RelayPeripheral peripheral = new ai.ezfox.cccloud.peripheral.RelayPeripheral(this);
    /** CC wired network membership (cables, docs/SPEC.md "Cables"). */
    private final ai.ezfox.cccloud.cable.CloudWired wired = ai.ezfox.cccloud.cable.CloudWired.single(this, () -> relayId.toString(), true, () -> peripheral);

    public CloudRelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RELAY_BE.get(), pos, state);
        var type = state.getBlock() instanceof CloudRelayBlock b ? b.type() : RelayType.RELAY;
        energy = new MainframeEnergy(type.buffer(), Math.max(1, type.buffer() / 50));
    }

    public RelayType type() {
        return getBlockState().getBlock() instanceof CloudRelayBlock b ? b.type() : RelayType.RELAY;
    }

    public UUID relayId() {
        return relayId;
    }

    public @Nullable UUID enderGroup() {
        return enderGroup;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    /** No owner: placed before relays had owners (or by a machine); carries every network, the setting does not apply. */
    public boolean legacy() {
        return owner == null;
    }

    /** The owner's choice, or null when it follows the server default. */
    public RelaySharing.@Nullable Share share() {
        return share;
    }

    /** What applies now: the owner's choice, else the server default. */
    public RelaySharing.Share effectiveShare() {
        return share != null ? share : RelayShares.serverDefault();
    }

    /** Whether this relay carries the network of a mainframe owned by {@code mainframeOwner} (cached team check). */
    public boolean carries(@Nullable UUID mainframeOwner) {
        return RelayShares.carries(owner, effectiveShare(), mainframeOwner);
    }

    /** Set once when a player places it ({@link CloudRelayBlock#setPlacedBy}). */
    public void setOwner(UUID player, String name) {
        owner = player;
        ownerName = name;
        setChanged();
        NetworkService.markDirty();
    }

    /** Only the owner and server admins ({@code cccloud.admin.manage}, op level 2 by default) change who the relay carries. */
    public boolean mayChangeShare(Player player) {
        if (player.getUUID().equals(owner)) return true;
        var server = player.level().getServer();
        return server != null && ai.ezfox.cccloud.access.CloudPermissions.of(server).has(player.getUUID(), ai.ezfox.cccloud.access.ServerPermissions.Node.ADMIN_MANAGE);
    }

    /** The relay screen's "Share with" (rights checked by the caller). Every network is computed again. */
    public void setShare(RelaySharing.Share value, String by) {
        if (value == share) return;
        share = value;
        setChanged();
        NetworkService.markDirty();
        CCCloud.LOGGER.info("[cccloud] {} set the {} at {} (owner {}) to share with {}", by, type().nodeType.id, getBlockPos().toShortString(),
            ownerName.isEmpty() ? owner : ownerName, value.id());
    }

    public MainframeEnergy energy() {
        return energy;
    }

    public boolean hasPower() {
        return hasPower;
    }

    public ai.ezfox.cccloud.peripheral.RelayPeripheral peripheral() {
        return peripheral;
    }

    public ai.ezfox.cccloud.cable.CloudWired wired() {
        return wired;
    }

    public SpinAnimation clientAnimation() {
        return clientAnimation;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.read("relay_id", UUIDUtil.STRING_CODEC).ifPresent(id -> relayId = id);
        enderGroup = input.read("ender_group", UUIDUtil.STRING_CODEC).orElse(null);
        owner = input.read("owner", UUIDUtil.STRING_CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "");
        share = RelaySharing.Share.byId(input.getStringOr("share", ""));
        energy.set(Math.min(energy.capacity(), Math.max(0, input.getIntOr("energy", 0))));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("relay_id", UUIDUtil.STRING_CODEC, relayId);
        output.storeNullable("ender_group", UUIDUtil.STRING_CODEC, enderGroup);
        output.storeNullable("owner", UUIDUtil.STRING_CODEC, owner);
        if (!ownerName.isEmpty()) output.putString("owner_name", ownerName);
        if (share != null) output.putString("share", share.id());
        output.putInt("energy", energy.energy());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide()) return;
        if (!CloudNetwork.registerRelay(this)) {
            relayId = UUID.randomUUID();
            setChanged();
            CloudNetwork.registerRelay(this);
        }
        registered = true;
        wired.onLoad();
        NetworkService.markDirty();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        wired.onRemoved();
        if (!registered) return;
        registered = false;
        CloudNetwork.unregisterRelay(this);
        NetworkService.markDirty();
    }

    public void serverTick(ServerLevel level) {
        var type = type();
        if (energy.capacity() != type.buffer()) energy.setCapacity(type.buffer());
        int maxInput = Math.max(1, type.buffer() / 50);
        if (energy.maxPerTick() != maxInput) energy.setMaxPerTick(maxInput);
        boolean before = hasPower;
        hasPower = power.tick(energy.energy(), type.usage(), true, energy::consume);
        energy.startTick();
        if (before != hasPower) {
            NetworkService.markDirty();
            setChanged();
        }
        // after setPlacedBy (same tick as the placement), so a freshly placed relay is not reported
        if (ticks == 0 && owner == null) RelayShares.legacyLoaded(getBlockPos());
        if (ticks++ % 20 == 0) updateStatus(level);
    }

    private void updateStatus(ServerLevel level) {
        RelayStatus status = !hasPower ? RelayStatus.NO_POWER : NetworkService.isServing(relayId) ? RelayStatus.ONLINE : RelayStatus.OFFLINE;
        var state = getBlockState();
        if (state.getValue(RelayStatus.PROPERTY) != status) level.setBlock(getBlockPos(), state.setValue(RelayStatus.PROPERTY, status), Block.UPDATE_CLIENTS);
    }

    /** Link Card on an Ender Relay: join the ender group of the card's mainframe (owner/trusted only). */
    public void bindEnder(ai.ezfox.cccloud.item.MainframeLink link, Player player) {
        if (type() != RelayType.ENDER) {
            player.sendSystemMessage(Component.translatable("cccloud.relay.not_ender").withStyle(ChatFormatting.GRAY));
            return;
        }
        var mainframe = CloudNetwork.mainframe(link.id());
        if (mainframe == null) {
            player.sendSystemMessage(Component.translatable("cccloud.uplink.mainframe_missing").withStyle(ChatFormatting.RED));
            return;
        }
        if (!mainframe.has(player, ai.ezfox.cccloud.access.Perm.NETWORK_LINK)) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.not_owner").withStyle(ChatFormatting.RED));
            return;
        }
        if (!ai.ezfox.cccloud.access.Access.mayRelink(player, enderGroup, link.id())) {
            player.sendSystemMessage(Component.translatable("cccloud.link_card.linked_elsewhere").withStyle(ChatFormatting.RED));
            return;
        }
        // Another team's relay: it would not carry that network, and joining its group is the owner's (or their team's) call.
        if (!carries(mainframe.getOwner())) {
            player.sendSystemMessage(Component.translatable("cccloud.relay.not_shared", ownerName.isEmpty() ? "?" : ownerName).withStyle(ChatFormatting.RED));
            return;
        }
        enderGroup = link.id();
        setChanged();
        NetworkService.markDirty();
        CCCloud.LOGGER.info("[cccloud] {} linked the Ender Relay at {} to {}", player.getGameProfile().name(), getBlockPos(), mainframe.getDisplayName());
        player.sendSystemMessage(Component.translatable("cccloud.relay.ender_linked", link.describe()).withStyle(ChatFormatting.GREEN));
    }

    public void showStatus(Player player) {
        player.sendSystemMessage(getBlockState().getBlock().getName().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        var status = getBlockState().getValue(RelayStatus.PROPERTY);
        player.sendSystemMessage(Component.translatable("cccloud.relay.status", status.displayName(),
            String.format("%,d", energy.energy()), String.format("%,d", energy.capacity()), type().usage()));
        player.sendSystemMessage(Component.translatable("cccloud.relay.ranges", type().nodeRange(), type().deviceRange()).withStyle(ChatFormatting.GRAY));
        if (type() == RelayType.ENDER) {
            player.sendSystemMessage(Component.translatable(enderGroup == null ? "cccloud.relay.ender_unlinked" : "cccloud.relay.ender_group")
                .withStyle(ChatFormatting.GRAY));
        }
    }

    /** GameTests only: this relay serves the networks of these mainframes (tests run side by side in one world). */
    private java.util.@Nullable Set<UUID> onlyForTest;

    public void restrictForTest(UUID... mainframeIds) {
        onlyForTest = java.util.Set.of(mainframeIds);
        ai.ezfox.cccloud.network.NetworkService.markDirty();
    }

    /** False if a GameTest restricted this relay to other mainframes' networks. */
    public boolean servesForTest(UUID mainframeId) {
        return onlyForTest == null || onlyForTest.contains(mainframeId);
    }

    public void fillForTest() {
        energy.set(energy.capacity());
    }
}
