package ai.ezfox.cccloud.compat.card;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/** A card bound to one block entity: valid while that exact block entity is loaded at its position. */
public abstract class BlockCard<T extends BlockEntity> extends DeviceCard {
    protected final ServerLevel level;
    protected final BlockPos pos;
    protected final Direction side;
    protected final T be;

    protected BlockCard(ServerLevel level, BlockPos pos, Direction side, T be) {
        this.level = level;
        this.pos = pos.immutable();
        this.side = side;
        this.be = be;
    }

    @Override
    public boolean isValid() {
        return !be.isRemoved() && level.isLoaded(pos) && level.getBlockEntity(pos) == be;
    }

    /** Title from the block id ("powah:energy_cell_hardened" → "Energy Cell Hardened"). */
    protected String blockTitle() {
        return Fmt.title(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath());
    }

    /** Marks the block entity changed and sends a block update so clients and comparators see an action's effect. */
    protected void changed() {
        be.setChanged();
        var state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    /** The block entity at pos if it has the given class, else null. */
    public static <E> @Nullable E at(ServerLevel level, BlockPos pos, Class<E> type) {
        var be = level.getBlockEntity(pos);
        return type.isInstance(be) ? type.cast(be) : null;
    }
}
