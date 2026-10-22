package ai.ezfox.cccloud.compat.ae2;

import ai.ezfox.cccloud.bridge.BridgeCore;
import ai.ezfox.cccloud.bridge.BridgeHost;
import ai.ezfox.cccloud.bridge.BridgePeripheral;
import ai.ezfox.cccloud.bridge.CloudBridgeBlock;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * ME Cloud Bridge: an in-world AE2 grid node (connects to cables and adjacent machines, needs 1 channel, idles at
 * 1 AE/t). Only loaded when AE2 is present.
 */
public class MeBridgeBlockEntity extends BlockEntity implements BridgeHost, IInWorldGridNodeHost, IActionHost {
    /** Idle power draw in AE/t. */
    public static final double IDLE_POWER = 1.0;

    private final IManagedGridNode node;
    private final Ae2Adapter adapter;
    private final BridgeCore core;
    private final BridgePeripheral peripheral;
    private final ai.ezfox.cccloud.bridge.BridgeSection section;
    private int ticks;

    public MeBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(Ae2Compat.BLOCK_ENTITY.get(), pos, state);
        node = GridHelper.createManagedNode(this, NodeListener.INSTANCE)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(IDLE_POWER)
            .setInWorldNode(true)
            .setTagName("node")
            .setVisualRepresentation(Ae2Compat.ITEM.get());
        adapter = new Ae2Adapter(this);
        core = new BridgeCore(adapter, "me", this::setChanged);
        section = new ai.ezfox.cccloud.bridge.BridgeSection(this);
        peripheral = new BridgePeripheral("me_cloud_bridge", this);
    }

    IManagedGridNode managedNode() {
        return node;
    }

    public BridgePeripheral peripheral() {
        return peripheral;
    }

    @Override
    public BridgeCore bridgeCore() {
        return core;
    }

    @Override
    public ai.ezfox.cccloud.bridge.BridgeSection bridgeSection() {
        return section;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) section.broken();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) GridHelper.onFirstTick(this, be -> be.node.create(be.getLevel(), be.getBlockPos()));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        node.destroy();
        if (level != null && !level.isClientSide()) section.removed();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        node.destroy();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        node.deserialize(input);
        core.loadWatchlist(input.getStringOr("watchlist", "[]"));
        section.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        node.serialize(output);
        output.putString("watchlist", core.saveWatchlist());
        section.save(output);
    }

    @Override
    public void bridgeTick() {
        core.tick();
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) section.tick(serverLevel);
        if (ticks++ % 10 == 0 && level != null) {
            var status = adapter.status();
            var state = getBlockState();
            if (state.hasProperty(CloudBridgeBlock.STATUS) && state.getValue(CloudBridgeBlock.STATUS) != status) {
                level.setBlock(getBlockPos(), state.setValue(CloudBridgeBlock.STATUS, status), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void onPlacedBy(Player player) {
        node.setOwningPlayer(player);
    }

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        return node.getNode();
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return node.getNode();
    }

    private enum NodeListener implements IGridNodeListener<MeBridgeBlockEntity> {
        INSTANCE;

        @Override
        public void onSaveChanges(MeBridgeBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }
    }
}
