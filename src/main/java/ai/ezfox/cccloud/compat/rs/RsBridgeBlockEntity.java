package ai.ezfox.cccloud.compat.rs;

import ai.ezfox.cccloud.bridge.BridgeCore;
import ai.ezfox.cccloud.bridge.BridgeHost;
import ai.ezfox.cccloud.bridge.BridgePeripheral;
import ai.ezfox.cccloud.bridge.CloudBridgeBlock;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.SimpleNetworkNode;
import com.refinedmods.refinedstorage.common.api.support.network.AbstractNetworkNodeContainerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** RS Cloud Bridge: a Refined Storage 2 network node (2 FE/t). Only loaded when Refined Storage is present. */
public class RsBridgeBlockEntity extends AbstractNetworkNodeContainerBlockEntity<SimpleNetworkNode> implements BridgeHost {
    public static final long ENERGY_USAGE = 2;

    private final RsAdapter adapter;
    private final BridgeCore core;
    private final BridgePeripheral peripheral;
    private int ticks;

    public RsBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(RsCompat.BLOCK_ENTITY.get(), pos, state, new SimpleNetworkNode(ENERGY_USAGE));
        adapter = new RsAdapter(this);
        core = new BridgeCore(adapter, "rs", this::setChanged);
        peripheral = new BridgePeripheral("rs_cloud_bridge", this);
    }

    SimpleNetworkNode node() {
        return mainNetworkNode;
    }

    public BridgePeripheral peripheral() {
        return peripheral;
    }

    @Override
    public BridgeCore bridgeCore() {
        return core;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        core.loadWatchlist(input.getStringOr("watchlist", "[]"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("watchlist", core.saveWatchlist());
    }

    @Override
    public void bridgeTick() {
        // Active = part of a network that has energy (a controller); active nodes draw their energy usage.
        var network = mainNetworkNode.getNetwork();
        boolean powered = network != null && network.getComponent(EnergyNetworkComponent.class).getStored() >= ENERGY_USAGE;
        if (mainNetworkNode.isActive() != powered) mainNetworkNode.setActive(powered);
        mainNetworkNode.doWork();
        core.tick();
        if (ticks++ % 10 == 0 && level != null) {
            var status = adapter.status();
            var state = getBlockState();
            if (state.hasProperty(CloudBridgeBlock.STATUS) && state.getValue(CloudBridgeBlock.STATUS) != status) {
                level.setBlock(getBlockPos(), state.setValue(CloudBridgeBlock.STATUS, status), Block.UPDATE_CLIENTS);
            }
        }
    }
}
