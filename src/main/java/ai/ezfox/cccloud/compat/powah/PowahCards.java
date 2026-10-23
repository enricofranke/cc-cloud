package ai.ezfox.cccloud.compat.powah;

import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.EnergyCard;
import ai.ezfox.cccloud.compat.card.Fmt;
import com.google.gson.JsonElement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import owmii.powah.block.energycell.EnergyCellBlockEntity;
import owmii.powah.block.furnator.FurnatorBlockEntity;
import owmii.powah.block.magmator.MagmatorBlockEntity;
import owmii.powah.block.reactor.ReactorBlockEntity;
import owmii.powah.block.reactor.ReactorPartBlockEntity;
import owmii.powah.block.solar.SolarBlockEntity;
import owmii.powah.block.thermo.ThermoBlockEntity;
import owmii.powah.lib.block.PowahBaseBlockEntity;
import owmii.powah.lib.block.PowahBaseEnergyStorageBlockEntity;
import owmii.powah.lib.block.PowahBaseGeneratorBlockEntity;
import owmii.powah.lib.logistics.Redstone;

/**
 * Powah (powah 7.0.x): energy cells, generators and the reactor. Uses Powah's block entity classes (Powah has no
 * block-entity API); only loaded when Powah is present.
 */
public final class PowahCards {
    static final List<String> REDSTONE_OPTIONS = List.of("Ignore redstone", "Active with signal", "Active without signal");

    private PowahCards() {
    }

    public static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        if (be instanceof ReactorPartBlockEntity part) {
            var core = part.core();
            return core.isPresent() ? new Reactor(level, core.get().getBlockPos(), side, core.get()) : null;
        }
        if (be instanceof ReactorBlockEntity reactor) return new Reactor(level, pos, side, reactor);
        if (be instanceof PowahBaseEnergyStorageBlockEntity<?> storage) return new Storage(level, pos, side, storage);
        return null;
    }

    /** Powah's own redstone mode (IGNORE / ON = active with signal / OFF = active without signal). */
    static EnergyCard.RedstoneControl redstone(PowahBaseBlockEntity<?> be) {
        return new EnergyCard.RedstoneControl() {
            @Override
            public List<String> options() {
                return REDSTONE_OPTIONS;
            }

            @Override
            public String current() {
                return REDSTONE_OPTIONS.get(be.getRedstoneMode().ordinal());
            }

            @Override
            public boolean set(String option) {
                int i = REDSTONE_OPTIONS.indexOf(option);
                if (i < 0) return false;
                be.setRedstoneMode(Redstone.values()[i]);
                be.sync();
                return true;
            }
        };
    }

    /** Energy cells, ender cells and the simple generators. */
    static final class Storage extends EnergyCard {
        private final PowahBaseEnergyStorageBlockEntity<?> storage;

        Storage(ServerLevel level, BlockPos pos, Direction side, PowahBaseEnergyStorageBlockEntity<?> storage) {
            super(storage instanceof PowahBaseGeneratorBlockEntity<?> ? "powah_generator" : "powah_cell", null, level, pos, side, storage);
            this.storage = storage;
        }

        private boolean isGenerator() {
            return storage instanceof PowahBaseGeneratorBlockEntity<?>;
        }

        @Override
        protected long stored() {
            return storage.getEnergy().getStored();
        }

        @Override
        protected long capacity() {
            return storage.getEnergy().getCapacity();
        }

        @Override
        protected @Nullable RedstoneControl redstoneControl() {
            return redstone(storage);
        }

        @Override
        protected void extraWidgets(CardWidgets w) {
            if (isGenerator()) {
                w.indicator("running", "Generating", "onColor", "green", "offColor", "gray");
                w.label("generation", "Generation");
            } else {
                w.label("tier", "Tier");
            }
        }

        @Override
        protected void refreshExtra(CardOutput out) {
            if (!isGenerator()) {
                boolean creative = storage instanceof EnergyCellBlockEntity cell && cell.isCreative();
                out.text("tier", creative ? "Creative (infinite)" : Fmt.title(storage.getTier().name().toLowerCase(java.util.Locale.ROOT)));
            }
            if (storage instanceof PowahBaseGeneratorBlockEntity<?> generator) {
                long rate = 0;
                boolean running;
                if (generator instanceof ThermoBlockEntity thermo) {
                    rate = thermo.generating;
                    running = rate > 0;
                } else if (generator instanceof SolarBlockEntity solar) {
                    running = solar.canSeeSky() && level.dimensionType().hasSkyLight() && level.getSkyDarken() < 4 && !solar.getEnergy().isFull();
                } else if (generator instanceof FurnatorBlockEntity furnator) {
                    running = furnator.isBurning();
                } else if (generator instanceof MagmatorBlockEntity magmator) {
                    running = magmator.isBurning();
                } else {
                    running = false;
                }
                if (running && rate == 0) rate = generator.getEnergyGeneration();
                out.bool("running", running);
                out.text("generation", running ? Fmt.flow(rate) : "0 FE/t");
            }
        }
    }

    /** The Powah reactor (multiblock): the card always reads the core, also when the uplink sits on another part. */
    static final class Reactor extends EnergyCard {
        private final ReactorBlockEntity reactor;
        private boolean wasRunning;
        private boolean fuelAlerted;

        Reactor(ServerLevel level, BlockPos pos, Direction side, ReactorBlockEntity reactor) {
            super("powah_reactor", "Powah Reactor (" + Fmt.title(reactor.getTier().name().toLowerCase(java.util.Locale.ROOT)) + ")",
                level, pos, side, reactor);
            this.reactor = reactor;
        }

        @Override
        protected long stored() {
            return reactor.getEnergy().getStored();
        }

        @Override
        protected long capacity() {
            return reactor.getEnergy().getCapacity();
        }

        @Override
        protected boolean hasRates() {
            return true;
        }

        @Override
        protected @Nullable Double inputRate() {
            return null;
        }

        @Override
        protected @Nullable Double outputRate() {
            return reactor.isRunning() ? reactor.calcProduction() : 0.0;
        }

        @Override
        protected String outputLabel() {
            return "Generation";
        }

        @Override
        protected @Nullable RedstoneControl redstoneControl() {
            return redstone(reactor);
        }

        @Override
        protected void extraWidgets(CardWidgets w) {
            w.indicator("running", "Running", "onColor", "green", "offColor", "gray")
                .indicator("built", "Multiblock complete", "onColor", "green", "offColor", "red")
                .gauge("temperature", "Temperature", 0, 1000, "°C", "warn", 750, "crit", 900, "icon", "flame")
                .progress("fuel", "Fuel", 0, 1000, "mB")
                .progress("carbon", "Carbon", 0, 100, "%")
                .progress("redstone_fuel", "Redstone", 0, 100, "%")
                .label("consumption", "Fuel use")
                .toggle("gen_mode", "Generate only on demand", "confirm", true);
        }

        @Override
        protected void refreshExtra(CardOutput out) {
            boolean running = reactor.isRunning();
            out.bool("running", running);
            out.bool("built", reactor.isBuilt());
            out.number("temperature", Math.round(reactor.temp.getTicks()));
            out.number("fuel", Math.round(reactor.fuel.getTicks()));
            out.number("carbon", Math.round(reactor.carbon.perCent()));
            out.number("redstone_fuel", Math.round(reactor.redstone.perCent()));
            out.text("consumption", String.format(java.util.Locale.ROOT, "%.2f mB/t", reactor.calcConsumption()));
            out.bool("gen_mode", reactor.isGenModeOn());
            if (wasRunning && !running && reactor.isBuilt()) out.alert(title() + " stopped", "warn");
            wasRunning = running;
            boolean empty = reactor.fuel.getTicks() <= 0;
            if (empty && !fuelAlerted && reactor.isBuilt()) out.alert(title() + " is out of fuel", "warn");
            fuelAlerted = empty;
        }

        @Override
        protected @Nullable String extraAction(String widgetId, JsonElement value, String user) {
            if (!"gen_mode".equals(widgetId)) return "not_interactive";
            reactor.setGenModeOn(value.getAsBoolean());
            return null;
        }
    }
}
