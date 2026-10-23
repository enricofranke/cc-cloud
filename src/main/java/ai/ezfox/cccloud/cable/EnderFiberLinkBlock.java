package ai.ezfox.cccloud.cable;

import ai.ezfox.cccloud.network.NetworkService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ender Fiber Link (endgame): links bound to the same mainframe with the Link Card join their fiber networks across any
 * distance and dimension. Part of the fiber network it touches; no block state properties (docs/MODELS.md).
 */
public class EnderFiberLinkBlock extends Block implements EntityBlock {
    public static final MapCodec<EnderFiberLinkBlock> CODEC = simpleCodec(EnderFiberLinkBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);

    public EnderFiberLinkBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends EnderFiberLinkBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof EnderFiberLinkBlockEntity link) link.showStatus(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) NetworkService.markFiberDirty();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnderFiberLinkBlockEntity(pos, state);
    }
}
