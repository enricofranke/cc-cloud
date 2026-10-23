package ai.ezfox.cccloud.compat.enderio;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.EnergyCard;
import ai.ezfox.cccloud.compat.card.Fmt;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelBlockEntity;
import com.enderio.enderio.content.machines.stirling_generator.StirlingGeneratorBlockEntity;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Ender IO (enderio 9.x): every powered machine gets the Energy card with Ender IO's redstone control; the Stirling
 * generator and photovoltaic modules add their generation. Ender IO 9.0.6-alpha has no capacitor banks yet.
 */
public final class EnderIoCards {
    static final List<String> REDSTONE_OPTIONS = List.of("Always active", "Active with signal", "Active without signal", "Never active");

    private EnderIoCards() {
    }

    public static void init() {
        DeviceCards.register("enderio", DeviceCards.SPECIFIC, EnderIoCards::detect);
        Integrations.test("enderio_machine", () -> EnderIoGameTests::stirling);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        if (be instanceof PoweredMachineBlockEntity machine) return new Machine(level, pos, side, machine);
        if (be instanceof SolarPanelBlockEntity solar) return new Solar(level, pos, side, solar);
        return null;
    }

    static final class Machine extends EnergyCard {
        private final PoweredMachineBlockEntity machine;

        Machine(ServerLevel level, BlockPos pos, Direction side, PoweredMachineBlockEntity machine) {
            super("enderio_machine", null, level, pos, side, machine);
            this.machine = machine;
        }

        @Override
        protected long stored() {
            return machine.getEnergyStorage().getAmountAsLong();
        }

        @Override
        protected long capacity() {
            return machine.getEnergyStorage().getCapacityAsLong();
        }

        @Override
        protected @Nullable RedstoneControl redstoneControl() {
            if (!machine.supportsRedstoneControl()) return null;
            return new RedstoneControl() {
                @Override
                public List<String> options() {
                    return REDSTONE_OPTIONS;
                }

                @Override
                public String current() {
                    return REDSTONE_OPTIONS.get(machine.getRedstoneControl().ordinal());
                }

                @Override
                public boolean set(String option) {
                    int i = REDSTONE_OPTIONS.indexOf(option);
                    if (i < 0) return false;
                    machine.setRedstoneControl(com.enderio.enderio.api.io.RedstoneControl.values()[i]);
                    return true;
                }
            };
        }

        @Override
        protected void extraWidgets(CardWidgets w) {
            w.indicator("active", "Active", "onColor", "green", "offColor", "gray");
            if (machine instanceof StirlingGeneratorBlockEntity) w.label("generation", "Generation");
        }

        @Override
        protected void refreshExtra(CardOutput out) {
            out.bool("active", machine.isActive());
            if (machine instanceof StirlingGeneratorBlockEntity stirling) {
                out.text("generation", stirling.isActive() ? Fmt.flow(stirling.getGenerationRate()) : "0 FE/t");
            }
        }
    }

    /** Photovoltaic modules: Ender IO merges adjacent panels, so the energy values are the whole panel network's. */
    static final class Solar extends EnergyCard {
        private final SolarPanelBlockEntity solar;

        Solar(ServerLevel level, BlockPos pos, Direction side, SolarPanelBlockEntity solar) {
            super("enderio_solar", null, level, pos, side, solar);
            this.solar = solar;
        }

        @Override
        protected void extraWidgets(CardWidgets w) {
            w.label("generation", "Generation (this module)");
        }

        @Override
        protected void refreshExtra(CardOutput out) {
            out.text("generation", Fmt.flow(solar.getGenerationRate()));
        }
    }
}
