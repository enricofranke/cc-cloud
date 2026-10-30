package ai.ezfox.cccloud.block;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A small module stuck to the face of another block. {@code facing} points from the uplink towards the block it is
 * attached to (like a CC modem). {@code mount} picks the 14x14 plate or, on side faces that cannot carry it (slab sides,
 * solar panels), the compact puck; it follows the attached block's shape (see {@link UplinkMount}).
 * New uplinks are refused on top / bottom faces that do not carry the plate (action bar hint).
 */
public class CloudUplinkBlock extends Block implements EntityBlock {
    public static final MapCodec<CloudUplinkBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        com.mojang.serialization.Codec.BOOL.fieldOf("advanced").forGetter(CloudUplinkBlock::isAdvanced),
        propertiesCodec()
    ).apply(i, CloudUplinkBlock::new));

    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    public static final EnumProperty<CloudStatus> STATUS = CloudStatus.PROPERTY;
    public static final BooleanProperty DIRECT = BooleanProperty.create("direct");
    /** Plate or compact puck, from the attached block's shape (see {@link UplinkMount}). */
    public static final EnumProperty<UplinkMount> MOUNT = UplinkMount.PROPERTY;

    private final boolean advanced;

    public CloudUplinkBlock(boolean advanced, Properties properties) {
        super(properties);
        this.advanced = advanced;
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(STATUS, CloudStatus.OFFLINE)
            .setValue(DIRECT, false)
            .setValue(MOUNT, UplinkMount.FULL));
    }

    public boolean isAdvanced() {
        return advanced;
    }

    /** The Cloud Probe ({@link CloudProbeBlock}): reads one machine at a distance instead of the block it sticks to. */
    public boolean isProbe() {
        return false;
    }

    @Override
    protected MapCodec<? extends CloudUplinkBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS, DIRECT, MOUNT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(MOUNT).shape(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!state.canSurvive(level, pos)) return null;
        BlockPos attached = pos.relative(facing);
        if (facing.getAxis() == Direction.Axis.Y && !UplinkMount.platesFit(level.getBlockState(attached), level, attached, facing)) {
            // Top / bottom faces only take the plate (no posts or antennas): e.g. the top of a slab or solar panel.
            if (!level.isClientSide() && context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(net.minecraft.network.chat.Component.translatable("cccloud.uplink.side_only")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
            return null;
        }
        return withMount(state.setValue(DIRECT, isDirect(level.getBlockState(attached))), level, pos);
    }

    /** {@code state} with the mount (plate / compact puck) that fits the face it is attached to. */
    public static BlockState withMount(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getBlock() instanceof CloudUplinkBlock b && b.isProbe()) return state.setValue(MOUNT, UplinkMount.FULL);
        return state.setValue(MOUNT, UplinkMount.compute(level, pos, state.getValue(FACING)));
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
        return state.setValue(DIRECT, isDirect(neighbourState))
            .setValue(MOUNT, UplinkMount.compute(neighbourState, level, neighbourPos, direction));
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

    // v0.7 wireless redstone: receivers emit their channel's level on every side, transmitters read their neighbours.
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity uplink ? uplink.redstoneOutput() : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, net.minecraft.world.level.redstone.@Nullable Orientation orientation,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity uplink) uplink.onNeighborChanged();
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.@Nullable LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // claim guard: the placer's team may use claimed land for it until somebody links it
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof CloudUplinkBlockEntity uplink) {
            uplink.placedBy(player);
        }
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
