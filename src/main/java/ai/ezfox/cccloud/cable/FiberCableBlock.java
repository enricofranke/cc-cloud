package ai.ezfox.cccloud.cable;

import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.bridge.CloudBridgeBlock;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.relay.CloudRelayBlock;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fiber Cable (docs/SPEC.md "Cables", part B): a thin cable that connects only to cloud devices, other fiber and the
 * Ender Fiber Link – never to CC cables or other blocks, so it is a clean network of its own (no CC peripheral
 * traffic). Six boolean side properties {@code north/south/east/west/up/down} drive the multipart model
 * (docs/MODELS.md). No block entity: fiber networks are flood-filled by the {@link FiberNetworks} pass on change.
 */
public class FiberCableBlock extends Block {
    public static final MapCodec<FiberCableBlock> CODEC = simpleCodec(FiberCableBlock::new);
    public static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;

    private final Map<BlockState, VoxelShape> shapes = new ConcurrentHashMap<>();
    private final VoxelShape core;
    private final Map<Direction, VoxelShape> arms = new EnumMap<>(Direction.class);

    public FiberCableBlock(Properties properties) {
        this(properties, 6, 10);
    }

    /** @param min/max the core box (pixels); arms are 6..10 thick and reach from the core to the block edge */
    protected FiberCableBlock(Properties properties, double min, double max) {
        super(properties);
        var state = stateDefinition.any();
        for (var p : SIDES.values()) state = state.setValue(p, false);
        registerDefaultState(state);
        core = Block.box(min, min, min, max, max, max);
        arms.put(Direction.DOWN, Block.box(6, 0, 6, 10, min, 10));
        arms.put(Direction.UP, Block.box(6, max, 6, 10, 16, 10));
        arms.put(Direction.NORTH, Block.box(6, 6, 0, 10, 10, min));
        arms.put(Direction.SOUTH, Block.box(6, 6, max, 10, 10, 16));
        arms.put(Direction.WEST, Block.box(0, 6, 6, min, 10, 10));
        arms.put(Direction.EAST, Block.box(max, 6, 6, 16, 10, 10));
    }

    @Override
    protected MapCodec<? extends FiberCableBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDES.values().toArray(BooleanProperty[]::new));
    }

    /** Fiber connects to fiber blocks (cable, junction, ender link) and cloud devices only. */
    public static boolean connectsTo(BlockState neighbour) {
        var block = neighbour.getBlock();
        return block instanceof FiberCableBlock || block instanceof EnderFiberLinkBlock || block instanceof CloudMainframeBlock
            || block instanceof CloudUplinkBlock || block instanceof CloudRelayBlock || block instanceof CloudBridgeBlock;
    }

    /** Blocks that carry a fiber network (cloud devices are members, but do not pass fiber through). */
    public static boolean isFiber(BlockState state) {
        return state.getBlock() instanceof FiberCableBlock || state.getBlock() instanceof EnderFiberLinkBlock;
    }

    public static BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        for (var e : SIDES.entrySet()) state = state.setValue(e.getValue(), connectsTo(level.getBlockState(pos.relative(e.getKey()))));
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(SIDES.get(direction), connectsTo(neighbourState));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.computeIfAbsent(state, s -> {
            var shape = core;
            for (var e : SIDES.entrySet()) if (s.getValue(e.getValue())) shape = Shapes.or(shape, arms.get(e.getKey()));
            return shape.optimize();
        });
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(state.getBlock())) NetworkService.markFiberDirty();
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        NetworkService.markFiberDirty();
    }
}
