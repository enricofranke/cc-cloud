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
import net.minecraft.server.level.ServerPlayer;
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
    /** v0.3 trust list, only read from old saves and moved into the v0.6 AccessStore (as admins) on load. */
    private final Map<UUID, String> legacyTrusted = new LinkedHashMap<>();
    private final Map<UUID, GlobalPos> linkedUplinks = new LinkedHashMap<>();

    private boolean registered;
    /** From the placed item: the removed mainframe it restores (consumed by {@link #takePlacedIdentity}). */
    private @Nullable UUID placedIdentity;
    /** Set when a player breaks the mainframe: whether the block drops its item (null = removed some other way). */
    private @Nullable Boolean breakerDrops;
    private final SpinAnimation clientAnimation = new SpinAnimation();
    private boolean redstoneChecked;
    private boolean powered;
    private volatile CloudStatus status = CloudStatus.OFFLINE;
    private volatile boolean online;
    private final MainframePeripheral peripheral = new MainframePeripheral(this);
    /** CC wired network membership on both halves (cables, docs/SPEC.md "Cables"). */
    private final ai.ezfox.cccloud.cable.CloudWired wired = new ai.ezfox.cccloud.cable.CloudWired(this, () -> mainframeId.toString(), true,
        () -> peripheral, () -> java.util.List.of(getBlockPos(), getBlockPos().above()));

    /** FE buffer (Capabilities.Energy.BLOCK on both halves) and the power logic. */
    private final ai.ezfox.cccloud.power.MainframeEnergy energy = new ai.ezfox.cccloud.power.MainframeEnergy(
        CloudConfig.get(CloudConfig.MAINFRAME_BUFFER_FE), CloudConfig.get(CloudConfig.MAINFRAME_MAX_INPUT_FE));
    private final ai.ezfox.cccloud.power.PowerState power = new ai.ezfox.cccloud.power.PowerState();
    private long energyUsage;
    private boolean hasPower;
    private int powerTicks;

    /** v0.7 upgrade slots (4 cards + Quantum Core); effects are recomputed when they change. */
    private final ai.ezfox.cccloud.upgrade.MainframeUpgrades upgrades = new ai.ezfox.cccloud.upgrade.MainframeUpgrades(this::upgradesChanged);
    private ai.ezfox.cccloud.upgrade.UpgradeEffects effects = ai.ezfox.cccloud.upgrade.UpgradeEffects.none(ai.ezfox.cccloud.upgrade.UpgradeEffects.Base.DEFAULT);
    /** Chunks the Chunk Anchor card keeps loaded (saved, so stale tickets are removed after a restart). */
    private java.util.Set<ai.ezfox.cccloud.upgrade.ChunkAnchor.Key> anchorChunks = java.util.Set.of();
    private boolean anchorDirty = true;

    public CloudMainframeBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MAINFRAME_BE.get(), pos, state);
    }

    // ---- identity & persistence ----------------------------------------------------------------------------------

    public UUID getMainframeId() {
        return mainframeId;
    }

    private @Nullable String displayName;

    public String getDisplayName() {
        // The position never changes for a block entity: build the name once (it is read every tick)
        if (displayName == null) {
            var p = getBlockPos();
            displayName = "Mainframe @ " + p.getX() + " " + p.getY() + " " + p.getZ();
        }
        return displayName;
    }

    public ai.ezfox.cccloud.upgrade.MainframeUpgrades upgrades() {
        return upgrades;
    }

    /** Effective limits from the installed upgrades. */
    public ai.ezfox.cccloud.upgrade.UpgradeEffects effects() {
        return effects;
    }

    /** The base limits ({@code [upgrades]} base*) changed in the config: recompute and publish the effects. */
    public void refreshEffects() {
        effects = upgrades.effects(ai.ezfox.cccloud.upgrade.Upgrades.base());
        ai.ezfox.cccloud.upgrade.Upgrades.publish(mainframeId, effects);
        anchorDirty = true;
        ai.ezfox.cccloud.network.NetworkService.markDirty();
    }

    private void upgradesChanged() {
        effects = upgrades.effects(ai.ezfox.cccloud.upgrade.Upgrades.base());
        ai.ezfox.cccloud.upgrade.Upgrades.publish(mainframeId, effects);
        anchorDirty = true;
        setChanged();
        ai.ezfox.cccloud.network.NetworkService.markDirty();
    }

    public ai.ezfox.cccloud.cable.CloudWired wired() {
        return wired;
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
        legacyTrusted.clear();
        input.read("trusted", NameAndId.CODEC.listOf()).ifPresent(list -> list.forEach(t -> legacyTrusted.put(t.id(), t.name())));
        linkedUplinks.clear();
        input.read("uplinks", LinkedUplink.CODEC.listOf()).ifPresent(list -> list.forEach(u -> linkedUplinks.put(u.id(), u.pos())));
        energy.set(Math.min(energy.capacity(), Math.max(0, input.getIntOr("energy", 0))));
        upgrades.clearContent();
        net.minecraft.world.ContainerHelper.loadAllItems(input.childOrEmpty("upgrades"), upgrades.getItems());
        var anchored = new java.util.LinkedHashSet<ai.ezfox.cccloud.upgrade.ChunkAnchor.Key>();
        input.read("anchor_chunks", com.mojang.serialization.Codec.STRING.listOf()).ifPresent(list -> list.forEach(entry -> {
            var parts = entry.split("\\|");
            if (parts.length == 3) {
                try {
                    anchored.add(new ai.ezfox.cccloud.upgrade.ChunkAnchor.Key(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                } catch (NumberFormatException ignored) {
                    // skip a broken entry
                }
            }
        }));
        anchorChunks = anchored;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("mainframe_id", UUIDUtil.STRING_CODEC, mainframeId);
        output.putBoolean("enabled", enabled);
        output.storeNullable("owner", UUIDUtil.STRING_CODEC, owner);
        output.putString("owner_name", ownerName);
        net.minecraft.world.ContainerHelper.saveAllItems(output.child("upgrades"), upgrades.getItems());
        if (!anchorChunks.isEmpty()) {
            output.store("anchor_chunks", com.mojang.serialization.Codec.STRING.listOf(),
                anchorChunks.stream().map(k -> k.dim() + "|" + k.x() + "|" + k.z()).toList());
        }
        if (!legacyTrusted.isEmpty()) {
            output.store("trusted", NameAndId.CODEC.listOf(), legacyTrusted.entrySet().stream().map(e -> new NameAndId(e.getKey(), e.getValue())).toList());
        }
        output.store("uplinks", LinkedUplink.CODEC.listOf(), linkedUplinks.entrySet().stream().map(e -> new LinkedUplink(e.getKey(), e.getValue())).toList());
        output.putInt("energy", energy.energy());
    }

    // ---- identity on the item (docs/SPEC.md "Mainframe removal") -------------------------------------------------------

    /** The dropped item (loot table {@code copy_components}) and pick-block carry the mainframe's identity. */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(ModRegistry.MAINFRAME_IDENTITY.get(), new ai.ezfox.cccloud.item.MainframeIdentity(mainframeId, ownerName));
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) {
        super.applyImplicitComponents(components);
        var identity = components.get(ModRegistry.MAINFRAME_IDENTITY.get());
        placedIdentity = identity == null ? null : identity.id();
    }

    /** The removed mainframe the item placed here stands for, once (null for a plain item). */
    public @Nullable UUID takePlacedIdentity() {
        var id = placedIdentity;
        placedIdentity = null;
        return id;
    }

    /**
     * Placed from the item of a removed mainframe: takes over its identity and what only its block knew (owner,
     * dashboard switch, linked devices), so devices, accounts, layout, rules and scenes are back and the devices
     * reconnect. Ownership comes from the server's record, never from the item or the placer. False (nothing changed)
     * when that mainframe's data is gone or it is placed somewhere else already.
     */
    public boolean restore(net.minecraft.server.MinecraftServer server, UUID id) {
        if (CloudNetwork.mainframe(id) != null) return false;
        var entry = ai.ezfox.cccloud.block.MainframeRetention.get(server).restore(id);
        if (entry == null) return false;
        mainframeId = id;
        owner = entry.owner().orElse(null);
        ownerName = entry.ownerName();
        enabled = entry.enabled();
        linkedUplinks.clear();
        linkedUplinks.putAll(entry.uplinks());
        setChanged();
        CCCloud.LOGGER.info("[cccloud] Mainframe {} of {} was placed again at {}; its data is restored", id, ownerName, getBlockPos());
        return true;
    }

    /** A player is breaking the mainframe: remember whether the block drops its item (for /cccloud mainframe recover). */
    public void noteBreaker(Player player, BlockState state) {
        breakerDrops = level != null && !player.preventsBlockDrops() && state.canHarvestBlock(level, getBlockPos(), player);
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
        wired.onLoad();
        effects = upgrades.effects(ai.ezfox.cccloud.upgrade.Upgrades.base());
        ai.ezfox.cccloud.upgrade.Upgrades.publish(mainframeId, effects);
        ai.ezfox.cccloud.upgrade.ChunkAnchor.restore(mainframeId, anchorChunks);
        anchorDirty = true;
        syncAcl();
        ai.ezfox.cccloud.network.NetworkService.markDirty();
    }

    /**
     * Makes sure the AccessStore knows this mainframe and its owner (rights are checked there, also while this chunk is
     * unloaded). Migrates a v0.3 trust list once: trusted players become admins.
     */
    private void syncAcl() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var store = WebAuthData.access(serverLevel.getServer());
        if (store.migrateLegacy(mainframeId, owner, ownerName, legacyTrusted) && !legacyTrusted.isEmpty()) {
            CCCloud.LOGGER.info("[cccloud] {}: {} trusted player(s) are admins now (v0.6 roles)", getDisplayName(), legacyTrusted.size());
        }
        var access = store.access(mainframeId);
        if (owner != null && access != null && !owner.equals(access.owner())) store.setOwner(mainframeId, owner, ownerName);
        if (!legacyTrusted.isEmpty()) {
            legacyTrusted.clear();
            setChanged();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        wired.onRemoved();
        if (registered) {
            registered = false;
            CloudNetwork.unregisterMainframe(this);
            online = false;
            ai.ezfox.cccloud.network.NetworkService.markDirty();
            WebHub.INSTANCE.publishMainframe(mainframeId, getDisplayName(), false, enabled);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            var server = serverLevel.getServer();
            // Upgrade cards drop as items (they are not kept with the mainframe); the chunk anchor lets go.
            net.minecraft.world.Containers.dropContents(serverLevel, pos, upgrades);
            ai.ezfox.cccloud.upgrade.ChunkAnchor.release(server, mainframeId);
            anchorChunks = java.util.Set.of();
            ai.ezfox.cccloud.upgrade.Upgrades.forget(mainframeId);
            if (ai.ezfox.cccloud.block.MainframeRetention.retentionMs() <= 0) {
                CCCloud.LOGGER.info("[cccloud] {} was removed (mainframeRetentionDays=0)", getDisplayName());
                ai.ezfox.cccloud.block.MainframeRetention.purge(server, mainframeId);
                return;
            }
            // Keep everything for the retention time: placing the item (or a recovered one) brings it back.
            var entry = new ai.ezfox.cccloud.block.MainframeRetention.Entry(java.util.Optional.ofNullable(owner), ownerName, System.currentTimeMillis(),
                GlobalPos.of(serverLevel.dimension(), pos), breakerDrops == null || breakerDrops, false, enabled, Map.copyOf(linkedUplinks));
            ai.ezfox.cccloud.block.MainframeRetention.get(server).removed(mainframeId, entry);
            CCCloud.LOGGER.info("[cccloud] {} ({}, owner {}) was removed; its data is kept for {} day(s) – place its item again or use "
                + "/cccloud mainframe recover to restore it", getDisplayName(), mainframeId, ownerName, CloudConfig.get(CloudConfig.MAINFRAME_RETENTION_DAYS));
        }
    }

    public void serverTick(ServerLevel level) {
        long cost = ai.ezfox.cccloud.network.TickCost.start();
        try {
            tick(level);
        } finally {
            ai.ezfox.cccloud.network.TickCost.end(ai.ezfox.cccloud.network.TickCost.MAINFRAME, cost);
        }
    }

    private @org.jspecify.annotations.Nullable Boolean pushedPower;

    private void tick(ServerLevel level) {
        if (!redstoneChecked) updateRedstone();
        tickPower();
        // v0.5 D: push "no power" / "power restored" on a change (not on the first tick after a load)
        if (pushedPower != null && pushedPower != hasPower) ai.ezfox.cccloud.push.PushService.power(mainframeId, getDisplayName(), hasPower);
        pushedPower = hasPower;

        boolean nowOnline = !powered && hasPower && CloudConfig.get(CloudConfig.ALLOW_CLOUD);
        if (nowOnline != online) ai.ezfox.cccloud.network.NetworkService.markDirty(); // the root node changed
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

        // Chunk Anchor card: reconcile tickets every 5 s or when something changed.
        if (anchorDirty || powerTicks % 100 == 0) {
            anchorDirty = false;
            boolean active = effects.chunkAnchor() && CloudConfig.get(CloudConfig.CHUNK_ANCHOR) && hasPower && nowOnline;
            int limit = owner == null ? 0 : ai.ezfox.cccloud.access.CloudPermissions.of(level.getServer()).anchorChunkLimit(owner);
            if (ai.ezfox.cccloud.upgrade.ChunkAnchor.update(this, active, limit)) {
                anchorChunks = ai.ezfox.cccloud.upgrade.ChunkAnchor.active(mainframeId);
                setChanged();
            }
        }

        // Cheap: the hub ignores unchanged state.
        WebHub.INSTANCE.publishMainframe(mainframeId, getDisplayName(), nowOnline, enabled);
        // Power numbers change every tick: publish once per second, or right away when the power state flips.
        if (powerTicks++ % 20 == 0 || WebHub.INSTANCE.powerOkChanged(mainframeId, hasPower)) {
            WebHub.INSTANCE.publishPower(mainframeId, energy.energy(), energy.capacity(), energyUsage, hasPower, reason(),
                ai.ezfox.cccloud.network.NetworkService.breakdownJson(mainframeId, WebHub.INSTANCE.clientCount(mainframeId)));
        }
    }

    // ---- power -------------------------------------------------------------------------------------------------------

    private ai.ezfox.cccloud.network.graph.NetworkPower.@org.jspecify.annotations.Nullable Counts usageCounts;
    private int usageClients = -1;

    private void tickPower() {
        int capacity = CloudConfig.get(CloudConfig.MAINFRAME_BUFFER_FE);
        if (capacity != energy.capacity()) energy.setCapacity(capacity);
        int maxInput = CloudConfig.get(CloudConfig.MAINFRAME_MAX_INPUT_FE);
        if (maxInput != energy.maxPerTick()) energy.setMaxPerTick(maxInput);
        // v0.4: base + per connected device type + per client + per extra hop (counts come from the network service).
        // Recomputed when the device counts or the browsers change, else once a second (upgrades, config).
        var counts = ai.ezfox.cccloud.network.NetworkService.counts(mainframeId);
        int clients = WebHub.INSTANCE.clientCount(mainframeId);
        if (counts != usageCounts || clients != usageClients || powerTicks % 20 == 0) {
            usageCounts = counts;
            usageClients = clients;
            energyUsage = ai.ezfox.cccloud.network.NetworkService.power(mainframeId, clients).get("total");
        }
        boolean before = hasPower;
        hasPower = power.tick(energy.energy(), energyUsage, CloudConfig.get(CloudConfig.REQUIRE_POWER), energy::consume);
        energy.startTick();
        if (before != hasPower) {
            CCCloud.LOGGER.info("[cccloud] {} {}", getDisplayName(), hasPower ? "has power again" : "ran out of power (no_power)");
            setChanged();
        }
    }

    /** null, "redstone" (kill switch), "no_power" or "cloud_disabled". */
    public @Nullable String reason() {
        if (!CloudConfig.get(CloudConfig.ALLOW_CLOUD)) return "cloud_disabled";
        if (powered) return "redstone";
        if (!hasPower) return "no_power";
        return null;
    }

    public boolean hasPower() {
        return hasPower;
    }

    public ai.ezfox.cccloud.power.MainframeEnergy energy() {
        return energy;
    }

    public long energyUsage() {
        return energyUsage;
    }

    /** What costs how much (FE/t), for the screen, the dashboard and Lua. */
    public java.util.Map<String, Long> powerBreakdown() {
        return ai.ezfox.cccloud.network.NetworkService.power(mainframeId, WebHub.INSTANCE.clientCount(mainframeId));
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

    /** Players with a direct role (v0.6; "trusted" players of v0.3 are admins). Server side only. */
    public Map<UUID, ai.ezfox.cccloud.access.MainframeAccess.Grant> getGrants() {
        if (!(level instanceof ServerLevel serverLevel)) return Map.of();
        var access = WebAuthData.access(serverLevel.getServer()).access(mainframeId);
        return access == null ? Map.of() : access.grants();
    }

    /** Manages the mainframe's access ({@code access.manage}; owner and admins by default). */
    public boolean canManage(Player player) {
        return has(player, ai.ezfox.cccloud.access.Perm.ACCESS_MANAGE);
    }

    /** One permission of the player here (AccessResolver: owner, roles + custom permissions, teams, admin nodes). */
    public boolean has(Player player, ai.ezfox.cccloud.access.Perm perm) {
        if (player instanceof ServerPlayer sp) return ai.ezfox.cccloud.access.Access.has(sp, mainframeId, perm);
        // Server-side non-ServerPlayer players (fake/mock players): resolve by UUID.
        return level instanceof ServerLevel serverLevel && !level.isClientSide()
            && ai.ezfox.cccloud.access.Access.resolver(serverLevel.getServer()).resolve(player.getUUID(), mainframeId).has(perm);
    }

    /** Gives a player a direct role (NONE removes it). Returns false if nothing changed or the player is the owner. */
    public boolean setRole(NameAndId player, ai.ezfox.cccloud.access.Role role) {
        if (!(level instanceof ServerLevel serverLevel) || player.id().equals(owner)) return false;
        var store = WebAuthData.access(serverLevel.getServer());
        var before = store.access(mainframeId);
        var grant = before == null ? null : before.grants().get(player.id());
        if (role == ai.ezfox.cccloud.access.Role.NONE ? grant == null : grant != null && grant.role() == role) return false;
        if (store.setRole(mainframeId, player.id(), player.name(), role) != null) return false;
        ai.ezfox.cccloud.web.CloudWebServer.recheck(serverLevel.getServer(), mainframeId);
        return true;
    }

    /** v0.3 "trust" = admin. */
    /** Adds a player with the given role (the caller's rank was checked with {@link ai.ezfox.cccloud.access.AccessRules}); false if unchanged. */
    public boolean trust(NameAndId player, ai.ezfox.cccloud.access.Role role) {
        var grant = getGrants().get(player.id());
        if (grant != null) return false; // already has a role: change it with /cccloud role
        return setRole(player, role);
    }

    public boolean untrust(UUID player) {
        var grant = getGrants().get(player);
        return grant != null && setRole(new NameAndId(player, grant.name()), ai.ezfox.cccloud.access.Role.NONE);
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
        if (!has(player, ai.ezfox.cccloud.access.Perm.POWER_TOGGLE)) {
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
        if (!has(player, ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE)) {
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
            // CC: Cloud Connect: the HTTPS address that works from anywhere
            var connect = ai.ezfox.cccloud.connect.ConnectService.status();
            if (connect.online()) player.sendSystemMessage(Component.translatable("cccloud.mainframe.connect_url", link(connect.url())));
        }
        if (level instanceof ServerLevel serverLevel && enabled) {
            var code = WebAuthData.get(serverLevel.getServer()).currentCode(mainframeId, player.getUUID());
            long minutes = Math.max(1, (code.expiresAt() - System.currentTimeMillis() + 59_999) / 60_000);
            String formatted = AuthStore.formatCode(code.code());
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.pairing_code", ai.ezfox.cccloud.web.PairingText.code(formatted), minutes)
                .append(ai.ezfox.cccloud.web.PairingText.pairLink(CloudWebServer.isRunning()
                    ? ai.ezfox.cccloud.connect.ConnectService.preferredUrl(CloudWebServer.dashboardUrl()) : null, formatted)));
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.pairing_hint").withStyle(ChatFormatting.GRAY));
            int devices = WebAuthData.get(serverLevel.getServer()).devices(mainframeId).size();
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.counts", devices, linkedUplinks.size()).withStyle(ChatFormatting.GRAY));
        }
    }

    public void revokeTokens(Player player) {
        if (!has(player, ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE)) {
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

    private net.minecraft.world.phys.@Nullable AABB renderBox;

    /** Render bounds for the block entity renderer (both halves plus the hologram), cached. */
    public net.minecraft.world.phys.AABB renderBox() {
        var box = renderBox;
        if (box == null) {
            var pos = getBlockPos();
            renderBox = box = new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2.5, pos.getZ() + 1);
        }
        return box;
    }

    /** Client-side animation state for the block entity renderer. */
    public SpinAnimation clientAnimation() {
        return clientAnimation;
    }
}
