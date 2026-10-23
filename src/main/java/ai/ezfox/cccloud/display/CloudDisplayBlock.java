package ai.ezfox.cccloud.display;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Cloud Display (v0.7): a CC-monitor-like block that joins with its neighbours into rectangles of up to
 * {@link CloudDisplayBlockEntity#MAX_WIDTH}×{@link CloudDisplayBlockEntity#MAX_HEIGHT} and shows one dashboard section.
 * Placement, orientation and the edge states work like CC: Tweaked's monitors (the blockstate file is built for them).
 * Right-click with a linked Cloud Link Card binds it to a mainframe; sneak + right-click opens its screen; a plain
 * right-click on the screen presses buttons/toggles when operators enabled that.
 */
public class CloudDisplayBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CloudDisplayBlock> CODEC = simpleCodec(CloudDisplayBlock::new);
    public static final EnumProperty<Direction> ORIENTATION = EnumProperty.create("orientation", Direction.class, Direction.UP, Direction.DOWN, Direction.NORTH);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DisplayEdgeState> STATE = EnumProperty.create("state", DisplayEdgeState.class);

    public CloudDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(ORIENTATION, Direction.NORTH).setValue(FACING, Direction.NORTH)
            .setValue(STATE, DisplayEdgeState.NONE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION, FACING, STATE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        float pitch = context.getPlayer() == null ? 0 : context.getPlayer().getXRot();
        Direction orientation = pitch > 66.5f ? Direction.UP : pitch < -66.5f ? Direction.DOWN : Direction.NORTH;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(ORIENTATION, orientation);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CloudDisplayBlockEntity display) display.expand();
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof ai.ezfox.cccloud.item.CloudLinkCardItem)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CloudDisplayBlockEntity display) {
            DisplayService.bind(serverPlayer, display, stack);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CloudDisplayBlockEntity display)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive() || display.front() != hit.getDirection()) DisplayService.openScreen(serverPlayer, display);
            else DisplayService.click(serverPlayer, display, hit);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CloudDisplayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof CloudDisplayBlockEntity display) display.serverTick((ServerLevel) lvl);
        };
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof CloudDisplayBlockEntity display) display.expand();
    }
}
