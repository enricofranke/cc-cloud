package ai.ezfox.cccloud.block;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A small module stuck to the face of another block. {@code facing} points from the uplink towards the block it is
 * attached to (like a CC modem).
 */
public class CloudUplinkBlock extends Block implements EntityBlock {
    public static final MapCodec<CloudUplinkBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        com.mojang.serialization.Codec.BOOL.fieldOf("advanced").forGetter(CloudUplinkBlock::isAdvanced),
        propertiesCodec()
    ).apply(i, CloudUplinkBlock::new));

    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    public static final EnumProperty<CloudStatus> STATUS = CloudStatus.PROPERTY;
    public static final BooleanProperty DIRECT = BooleanProperty.create("direct");

    /** Shape when attached to the block to the north: a flat 14x14 plate, 2.25 px deep (matches the models). */
    private static final VoxelShape NORTH_SHAPE = Block.box(1, 1, 0, 15, 15, 2.25);
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(NORTH_SHAPE);

    private final boolean advanced;

    public CloudUplinkBlock(boolean advanced, Properties properties) {
        super(properties);
        this.advanced = advanced;
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(STATUS, CloudStatus.OFFLINE)
            .setValue(DIRECT, false));
    }

    public boolean isAdvanced() {
        return advanced;
    }

    @Override
    protected MapCodec<? extends CloudUplinkBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS, DIRECT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!state.canSurvive(level, pos)) return null;
        return state.setValue(DIRECT, isDirect(level.getBlockState(pos.relative(facing))));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos attached = pos.relative(facing);
        // v0.5: machines with a block entity may have non-full models (e.g. the MFFS projector); uplinks stick to them too.
        return level.getBlockState(attached).isFaceSturdy(level, attached, facing.getOpposite()) || level.getBlockEntity(attached) != null;
    }

    @Override
    protected BlockState updateShape(
        BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
        BlockPos neighbourPos, BlockState neighbourState, RandomSource random
    ) {
        if (direction != state.getValue(FACING)) return state;
        if (!state.canSurvive(level, pos)) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        return state.setValue(DIRECT, isDirect(neighbourState));
    }

    /**
     * Whether the uplink is attached directly to a machine rather than to a computer or a wired network block.
     */
    public static boolean isDirect(BlockState attached) {
        var id = BuiltInRegistries.BLOCK.getKey(attached.getBlock());
        if (!id.getNamespace().equals("computercraft")) return true;
        String path = id.getPath();
        return !(path.contains("computer") || path.contains("turtle") || path.contains("modem") || path.equals("cable"));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CloudUplinkBlockEntity uplink) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) ai.ezfox.cccloud.gui.GuiNetwork.openUplink(serverPlayer, uplink);
            else uplink.showStatus(player);
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CloudUplinkBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModRegistry.UPLINK_BE.get()) return null;
        return (l, p, s, be) -> ((CloudUplinkBlockEntity) be).serverTick((ServerLevel) l);
    }
}
