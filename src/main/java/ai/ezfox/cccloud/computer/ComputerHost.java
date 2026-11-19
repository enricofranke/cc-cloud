package ai.ezfox.cccloud.computer;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Where a Cloud Computer lives: its block entity (or a test). Given to {@link ComputerRegistry#load}; the computer
 * calls it on the server thread only, except {@link #redstone()} whose object is thread-safe.
 */
public interface ComputerHost {
    /** The level, or null for a computer without a block (tests through the registry). */
    @Nullable ServerLevel level();

    BlockPos pos();

    /** The block's owner (the player who placed it), or null. Used for permission checks of world access. */
    @Nullable UUID owner();

    RedstoneIo redstone();

    /**
     * The world direction of a computer side (from the block's facing), or null without a world. Programs reach the
     * machines next to the computer through it ({@code peripheral}).
     */
    default net.minecraft.core.@Nullable Direction direction(ComputerSide side) {
        return null;
    }

    /** An advanced computer: colour screen and mouse input. Read once when the computer is created. */
    default boolean advanced() {
        return true;
    }

    /** Something the block shows changed (on/off, label, redstone outputs): sync and update neighbours. */
    default void changed() {
    }

    /** A host for tests and headless computers: no level, plain redstone. */
    static ComputerHost detached(RedstoneIo redstone) {
        return new ComputerHost() {
            @Override
            public @Nullable ServerLevel level() {
                return null;
            }

            @Override
            public BlockPos pos() {
                return BlockPos.ZERO;
            }

            @Override
            public @Nullable UUID owner() {
                return null;
            }

            @Override
            public RedstoneIo redstone() {
                return redstone;
            }
        };
    }
}
