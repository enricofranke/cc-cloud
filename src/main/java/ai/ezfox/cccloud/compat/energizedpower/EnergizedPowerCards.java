package ai.ezfox.cccloud.compat.energizedpower;

import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.EnergyCard;
import java.util.ArrayList;
import java.util.List;
import me.jddev0.ep.block.EPBlockStateProperties;
import me.jddev0.ep.block.entity.base.EnergyStorageBlockEntity;
import me.jddev0.ep.machine.configuration.IRedstoneModeHandler;
import me.jddev0.ep.machine.configuration.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Energized Power (energizedpower 3.x): generators and machines with EP's redstone mode and/or a "working" state get
 * the Energy card with those controls. Plain storage (battery boxes) stays with the generic FE card, since EP exposes
 * no extra data for it.
 */
public final class EnergizedPowerCards {
    private EnergizedPowerCards() {
    }

    public static void init() {
        DeviceCards.register("energizedpower", DeviceCards.SPECIFIC, EnergizedPowerCards::detect);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        if (!(level.getBlockEntity(pos) instanceof EnergyStorageBlockEntity<?> be)) return null;
        boolean working = be.getBlockState().hasProperty(EPBlockStateProperties.WORKING);
        if (!(be instanceof IRedstoneModeHandler) && !working) return null;
        return new Machine(level, pos, side, be, working);
    }

    static String label(RedstoneMode mode) {
        return switch (mode.name()) {
            case "IGNORE" -> "Ignore redstone";
            case "HIGH" -> "Active with signal";
            case "LOW" -> "Active without signal";
            default -> mode.name();
        };
    }

    static final class Machine extends EnergyCard {
        private final EnergyStorageBlockEntity<?> machine;
        private final boolean working;

        Machine(ServerLevel level, BlockPos pos, Direction side, EnergyStorageBlockEntity<?> machine, boolean working) {
            super("energizedpower_machine", null, level, pos, side, machine);
            this.machine = machine;
            this.working = working;
        }

        @Override
        protected long stored() {
            return machine.getEnergy();
        }

        @Override
        protected long capacity() {
            return machine.getCapacity();
        }

        @Override
        protected @Nullable RedstoneControl redstoneControl() {
            if (!(machine instanceof IRedstoneModeHandler handler)) return null;
            var modes = handler.getAvailableRedstoneModes();
            List<String> options = new ArrayList<>();
            for (var mode : modes) options.add(label(mode));
            return new RedstoneControl() {
                @Override
                public List<String> options() {
                    return options;
                }

                @Override
                public String current() {
                    return label(handler.getRedstoneMode());
                }

                @Override
                public boolean set(String option) {
                    int i = options.indexOf(option);
                    return i >= 0 && handler.setRedstoneMode(modes[i]);
                }
            };
        }

        @Override
        protected void extraWidgets(CardWidgets w) {
            if (working) w.indicator("running", "Working", "onColor", "green", "offColor", "gray");
        }

        @Override
        protected void refreshExtra(CardOutput out) {
            if (working) out.bool("running", machine.getBlockState().getValue(EPBlockStateProperties.WORKING));
        }
    }
}
