package ai.ezfox.cccloud.block;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The two block tall Cloud Mainframe. Works like a door: placing it places both halves, breaking either half breaks
 * both, and only the lower half drops the item (see the loot table) and carries the block entity.
 */
public class CloudMainframeBlock extends Block implements EntityBlock {
    public static final MapCodec<CloudMainframeBlock> CODEC = simpleCodec(CloudMainframeBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<CloudStatus> STATUS = CloudStatus.PROPERTY;

    public CloudMainframeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(HALF, DoubleBlockHalf.LOWER)
            .setValue(STATUS, CloudStatus.OFFLINE));
    }

    @Override
    protected MapCodec<? extends CloudMainframeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, STATUS);
    }

    /** Position of the lower half (the one with the block entity). */
    public static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    public static @Nullable CloudMainframeBlockEntity getBlockEntity(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CloudMainframeBlock)) return null;
        return level.getBlockEntity(lowerPos(state, pos)) instanceof CloudMainframeBlockEntity be ? be : null;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (!level.isClientSide() && by instanceof Player player && level.getBlockEntity(pos) instanceof CloudMainframeBlockEntity mainframe) {
            mainframe.setOwner(player);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected BlockState updateShape(
        BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
        BlockPos neighbourPos, BlockState neighbourState, RandomSource random
    ) {
        DoubleBlockHalf half = state.getValue(HALF);
        Direction towardsOther = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        if (direction == towardsOther) {
            // The other half vanished (or was replaced): break this half too.
            if (!neighbourState.is(this) || neighbourState.getValue(HALF) == half) return Blocks.AIR.defaultBlockState();
            // Keep the status of both halves in sync.
            return state.setValue(FACING, neighbourState.getValue(FACING)).setValue(STATUS, neighbourState.getValue(STATUS));
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            // Breaking the upper half in creative must not make the lower half drop its item: remove it silently.
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var mainframe = getBlockEntity(level, pos, state);
        if (mainframe == null) return InteractionResult.PASS;
        if (player.isSecondaryUseActive() && player.getMainHandItem().isEmpty()) {
            mainframe.toggleEnabled(player);
        } else {
            mainframe.showStatus(player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;
        var mainframe = getBlockEntity(level, pos, state);
        if (mainframe != null) mainframe.updateRedstone();
    }

    /** True if either half receives a redstone signal. */
    public static boolean isPowered(Level level, BlockPos lowerPos) {
        return level.hasNeighborSignal(lowerPos) || level.hasNeighborSignal(lowerPos.above());
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
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new CloudMainframeBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModRegistry.MAINFRAME_BE.get() || state.getValue(HALF) != DoubleBlockHalf.LOWER) return null;
        return (l, p, s, be) -> ((CloudMainframeBlockEntity) be).serverTick((ServerLevel) l);
    }
}
