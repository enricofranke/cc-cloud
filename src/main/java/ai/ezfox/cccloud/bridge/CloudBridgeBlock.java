package ai.ezfox.cccloud.bridge;

import ai.ezfox.cccloud.block.CloudStatus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The ME and RS Cloud Bridge block (full block, horizontal {@code facing}, {@code status}). Shared by both bridges; the
 * block entity type comes from {@link BridgeRegistry}, so this class never references AE2 or RS.
 */
public class CloudBridgeBlock extends Block implements EntityBlock {
    public static final MapCodec<CloudBridgeBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.STRING.fieldOf("kind").forGetter(CloudBridgeBlock::kind),
        propertiesCodec()
    ).apply(i, CloudBridgeBlock::new));

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<CloudStatus> STATUS = CloudStatus.PROPERTY;

    private final String kind;

    public CloudBridgeBlock(String kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, CloudStatus.OFFLINE));
    }

    public String kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends CloudBridgeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof BridgeHost host) {
            host.onPlacedBy(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof BridgeHost host) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) ai.ezfox.cccloud.gui.GuiNetwork.openBridge(serverPlayer, host);
            else BridgeStatus.show(player, host.bridgeCore(), state.getBlock().getName());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        var type = BridgeRegistry.BLOCK_ENTITIES.get(kind);
        return type == null ? null : type.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (l, p, s, be) -> {
            if (be instanceof BridgeHost host && l instanceof ServerLevel) host.bridgeTick();
        };
    }
}
