package ai.ezfox.cccloud.rsdevice;

import ai.ezfox.cccloud.block.CloudStatus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Cloud Switch, Cloud Button and Cloud Sensor: small plates that stick to any face (all six, like an uplink;
 * {@code facing} points from the plate towards the block it is attached to). Switch and button power like a lever /
 * button: strong on the block they sit on, weak on every side. {@code powered} = output on (switch, button) or input
 * received (sensor); {@code status} = network status LED.
 */
public class RsPlateBlock extends Block implements EntityBlock, RsDeviceBlock {
    public static final MapCodec<RsPlateBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.STRING.xmap(s -> {
            var kind = RsKind.byId(s);
            return kind == null ? RsKind.SWITCH : kind;
        }, k -> k.id).fieldOf("kind").forGetter(RsPlateBlock::kind),
        propertiesCodec()
    ).apply(i, RsPlateBlock::new));

    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<CloudStatus> STATUS = CloudStatus.PROPERTY;

    /** Shapes when attached to the block to the north (match the models in tools/gen_models.py). */
    private static final Map<Direction, VoxelShape> SWITCH = Shapes.rotateAll(Block.box(4, 3, 0, 12, 13, 4));
    private static final Map<Direction, VoxelShape> BUTTON = Shapes.rotateAll(Block.box(5, 5, 0, 11, 11, 2.5));
    private static final Map<Direction, VoxelShape> BUTTON_PRESSED = Shapes.rotateAll(Block.box(5, 5, 0, 11, 11, 1.75));
    private static final Map<Direction, VoxelShape> SENSOR = Shapes.rotateAll(Block.box(4, 4, 0, 12, 12, 2.25));
    /** Advanced Cloud Sensor: 10x10 plate with the gold-rimmed eye (bounds checked by tools/gen_models.py). */
    private static final Map<Direction, VoxelShape> ADVANCED_SENSOR = Shapes.rotateAll(Block.box(3, 3, 0, 13, 13, 3));

    private final RsKind kind;

    public RsPlateBlock(RsKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(STATUS, CloudStatus.OFFLINE));
    }

    @Override
    public RsKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends RsPlateBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, STATUS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var facing = state.getValue(FACING);
        return switch (kind) {
            case SWITCH -> SWITCH.get(facing);
            case BUTTON -> (state.getValue(POWERED) ? BUTTON_PRESSED : BUTTON).get(facing);
            case ADVANCED_SENSOR -> ADVANCED_SENSOR.get(facing);
            default -> SENSOR.get(facing);
        };
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // The clicked face first, then the other directions the player looks at (like a lever).
        var first = defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
        if (first.canSurvive(level, pos)) return first;
        for (var direction : context.getNearestLookingDirections()) {
            var state = defaultBlockState().setValue(FACING, direction);
            if (state.canSurvive(level, pos)) return state;
        }
        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos attached = pos.relative(facing);
        return level.getBlockState(attached).isFaceSturdy(level, attached, facing.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (direction == state.getValue(FACING) && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return state;
    }

    // ---- redstone ------------------------------------------------------------------------------------------------------

    @Override
    protected boolean isSignalSource(BlockState state) {
        return kind == RsKind.SWITCH || kind == RsKind.BUTTON || kind == RsKind.ADVANCED_SENSOR;
    }

    /**
     * What the plate emits (0–15): the switch's level, 15 while the button is pressed, 15 while something matching is
     * in the Advanced Cloud Sensor's zone (when its output is on), nothing for the basic sensor.
     */
    private int output(BlockState state, BlockGetter level, BlockPos pos) {
        return switch (kind) {
            case SWITCH -> level.getBlockEntity(pos) instanceof RsDeviceBlockEntity device ? device.output() : state.getValue(POWERED) ? 15 : 0;
            case BUTTON -> state.getValue(POWERED) ? 15 : 0;
            case ADVANCED_SENSOR -> state.getValue(POWERED) && level.getBlockEntity(pos) instanceof RsDeviceBlockEntity device ? device.output() : 0;
            default -> 0;
        };
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return output(state, level, pos);
    }

    /** Strong power only into the block the plate sits on (lever/button rule). */
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == state.getValue(FACING).getOpposite() ? output(state, level, pos) : 0;
    }

    /** Wakes up the plate's neighbours and the neighbours of the block it powers. */
    static void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        var block = state.getBlock();
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(state.getValue(FACING)), block);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston && state.getValue(POWERED) && kind != RsKind.SENSOR) updateNeighbours(state, level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, net.minecraft.world.level.redstone.@Nullable Orientation orientation,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RsDeviceBlockEntity device) device.onNeighborChanged();
    }

    /** The button's pulse ends. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (kind == RsKind.BUTTON && state.getValue(POWERED) && level.getBlockEntity(pos) instanceof RsDeviceBlockEntity device) device.release();
    }

    // ---- interaction -------------------------------------------------------------------------------------------------

    /** Advanced Cloud Sensor: the player who places it is its owner ("ignore my team", claims of the zone). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.@Nullable LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || kind != RsKind.ADVANCED_SENSOR || !(placer instanceof Player player)) return;
        if (level.getBlockEntity(pos) instanceof RsDeviceBlockEntity device && device.sensor() != null) {
            device.sensor().setOwner(player);
            device.setChanged();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return RsDeviceBlock.use(kind, level, pos, player);
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
        return RsDeviceBlock.newEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return RsDeviceBlock.ticker(level, type);
    }
}
