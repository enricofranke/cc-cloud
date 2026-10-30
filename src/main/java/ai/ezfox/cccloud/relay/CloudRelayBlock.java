package ai.ezfox.cccloud.relay;

import ai.ezfox.cccloud.ModRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Cloud Relay, Advanced Cloud Relay and Ender Relay: network nodes that extend the range (no facing). */
public class CloudRelayBlock extends Block implements EntityBlock {
    public static final MapCodec<CloudRelayBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.STRING.xmap(RelayType::valueOf, RelayType::name).fieldOf("relay").forGetter(CloudRelayBlock::type),
        propertiesCodec()
    ).apply(i, CloudRelayBlock::new));

    private static final VoxelShape MAST = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape ENDER = Block.box(2, 0, 2, 14, 14, 14);

    private final RelayType type;

    public CloudRelayBlock(RelayType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(RelayStatus.PROPERTY, RelayStatus.OFFLINE));
    }

    public RelayType type() {
        return type;
    }

    @Override
    protected MapCodec<? extends CloudRelayBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RelayStatus.PROPERTY);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return type == RelayType.ENDER ? ENDER : MAST;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CloudRelayBlockEntity relay && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ai.ezfox.cccloud.gui.GuiNetwork.openRelay(serverPlayer, relay);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CloudRelayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModRegistry.RELAY_BE.get()) return null;
        return (l, p, s, be) -> ((CloudRelayBlockEntity) be).serverTick((ServerLevel) l);
    }
}
