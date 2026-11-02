package ai.ezfox.cccloud.cable;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The CC wired networks a computer is on, as an uplink on that computer sees them (docs/SPEC.md "CC wired networks"),
 * without CC: Tweaked types. Created by {@link ai.ezfox.cccloud.compat.Cct}; without CC: Tweaked it is {@link #NONE}.
 * Server thread only.
 */
public interface CcReach {
    CcReach NONE = new CcReach() {
        @Override
        public void update(ServerLevel level, @Nullable BlockPos computer, BlockPos skip) {
        }

        @Override
        public @Nullable Object peripheral(String name) {
            return null;
        }

        @Override
        public Map<String, Object> targets() {
            return Map.of();
        }

        @Override
        public Set<BlockPos> modems() {
            return Set.of();
        }

        @Override
        public void close() {
        }
    };

    /**
     * Follows the computer's wired modems. Call about once a second.
     *
     * @param computer the computer block, or null when the uplink is not on a computer (everything is released)
     * @param skip     the uplink itself (it is next to the computer, but not the computer's modem)
     */
    void update(ServerLevel level, @Nullable BlockPos computer, BlockPos skip);

    /** The peripheral object the computer reaches under this network name (a CC peripheral), or null. */
    @Nullable Object peripheral(String name);

    /** Network name → what the peripheral is attached to ({@code IPeripheral#getTarget}), in name order. */
    Map<String, Object> targets();

    /** Positions of the computer's wired modems that are followed (tests, screen). */
    Set<BlockPos> modems();

    /** Leaves every network (the uplink unloads or is removed). */
    void close();
}
