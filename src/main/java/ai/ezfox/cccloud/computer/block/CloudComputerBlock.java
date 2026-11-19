package ai.ezfox.cccloud.computer.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Cloud Computer block (docs/SPEC.md "Cloud Computers"): faces the player who places it ({@code facing} = the side
 * the screen looks out of), {@code on} while its computer runs. Right-click opens the terminal screen. Emits the
 * redstone its program sets on each side and reads the redstone around it.
 */
public class CloudComputerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CloudComputerBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.BOOL.fieldOf("advanced").forGetter(CloudComputerBlock::advanced),
        propertiesCodec()
    ).apply(i, CloudComputerBlock::new));

    public static final BooleanProperty ON = BooleanProperty.create("on");

    private final boolean advanced;

    public CloudComputerBlock(boolean advanced, Properties properties) {
        super(properties);
        this.advanced = advanced;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false));
    }

    /** Advanced: colour terminal and mouse input. */
    public boolean advanced() {
        return advanced;
    }

    @Override
    protected MapCodec<? extends CloudComputerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CloudComputerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ComputerBlocks.COMPUTER_BE.get()) return null;
        return (l, p, s, be) -> ((CloudComputerBlockEntity) be).serverTick();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CloudComputerBlockEntity computer) {
            computer.placed(placer instanceof Player player ? player : null, stack.get(ComputerBlocks.COMPUTER_DATA.get()));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CloudComputerBlockEntity computer && player instanceof ServerPlayer serverPlayer) {
            ComputerScreens.open(serverPlayer, computer);
        }
        return InteractionResult.SUCCESS;
    }

    // ---- redstone ------------------------------------------------------------------------------------------------------

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** {@code direction} points from the block that asks to this one: the computer's side facing it is the opposite. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CloudComputerBlockEntity computer ? computer.outputToward(direction.getOpposite()) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, net.minecraft.world.level.redstone.@Nullable Orientation orientation,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CloudComputerBlockEntity computer) computer.readInputs();
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) CloudComputerBlockEntity.updateNeighbours(level, pos, this);
    }
}
