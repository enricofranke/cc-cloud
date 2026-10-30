package ai.ezfox.cccloud.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Cloud Probe (docs/SPEC.md "Cloud Probe"): an advanced uplink that reads exactly one machine up to {@code probeRange}
 * blocks away instead of the block it sticks to – for machines built into walls, or when every face is needed for
 * pipes and cables. The machine is picked with the Link Card (sneak + right-click it, then right-click the probe);
 * see {@code CloudUplinkBlockEntity#setRemoteTarget}. It always shows the full plate (with a lens) and needs a face
 * that carries it; {@code direct} is on while it has a target. Shares the uplink block entity.
 */
public class CloudProbeBlock extends CloudUplinkBlock {
    public static final MapCodec<CloudProbeBlock> PROBE_CODEC = simpleCodec(CloudProbeBlock::new);
    /** Attached to the north: the plate (like the uplink) plus the lens standing 3.5 px out (matches the model). */
    public static final net.minecraft.world.phys.shapes.VoxelShape NORTH_SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
        net.minecraft.world.level.block.Block.box(1, 1, 0, 15, 15, 2.25), net.minecraft.world.level.block.Block.box(5, 5, 0, 11, 11, 3.5));
    private static final java.util.Map<Direction, net.minecraft.world.phys.shapes.VoxelShape> SHAPES =
        net.minecraft.world.phys.shapes.Shapes.rotateAll(NORTH_SHAPE);

    public CloudProbeBlock(Properties properties) {
        super(true, properties);
    }

    @Override
    protected MapCodec<? extends CloudUplinkBlock> codec() {
        return PROBE_CODEC;
    }

    @Override
    public boolean isProbe() {
        return true;
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                                                 net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        var level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!state.canSurvive(level, pos)) return null;
        BlockPos attached = pos.relative(facing);
        if (!UplinkMount.platesFit(level.getBlockState(attached), level, attached, facing)) {
            if (!level.isClientSide() && context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(Component.translatable("cccloud.probe.full_face").withStyle(ChatFormatting.YELLOW));
            }
            return null;
        }
        return state.setValue(MOUNT, UplinkMount.FULL).setValue(DIRECT, false);
    }

    @Override
    protected BlockState updateShape(
        BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
        BlockPos neighbourPos, BlockState neighbourState, RandomSource random
    ) {
        // The block it sticks to only holds it; what it reads is its target (DIRECT is set by the block entity).
        if (direction == state.getValue(FACING) && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return state.getValue(MOUNT) == UplinkMount.FULL ? state : state.setValue(MOUNT, UplinkMount.FULL);
    }
}
