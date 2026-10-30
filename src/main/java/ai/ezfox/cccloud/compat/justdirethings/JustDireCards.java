package ai.ezfox.cccloud.compat.justdirethings;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.card.Slots;
import ai.ezfox.cccloud.uplink.Widget;
import com.direwolf20.justdirethings.common.blockentities.EnergyTransmitterBE;
import com.direwolf20.justdirethings.common.blockentities.ExperienceHolderBE;
import com.direwolf20.justdirethings.common.blockentities.GeneratorFluidT1BE;
import com.direwolf20.justdirethings.common.blockentities.GeneratorT1BE;
import com.direwolf20.justdirethings.common.blockentities.SensorT1BE;
import com.direwolf20.justdirethings.common.blockentities.basebe.BaseMachineBE;
import com.direwolf20.justdirethings.common.blockentities.basebe.FluidMachineBE;
import com.direwolf20.justdirethings.common.blockentities.basebe.PoweredMachineBE;
import com.direwolf20.justdirethings.common.blockentities.basebe.RedstoneControlledBE;
import com.direwolf20.justdirethings.util.MiscHelpers;
import com.google.gson.JsonElement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Just Dire Things (wave 4). Three cards over its machine base classes:
 * <ul>
 * <li><b>Generator</b> (Simple Coal / Fuel Generator): status, energy, output FE/t, burn left or fuel tank;</li>
 * <li><b>Machine</b> for every other redstone-controlled machine (breakers, placers, clickers, droppers, swappers, fluid
 * collectors/placers, item collector, experience holder, paradox machine, energy transmitter): active, speed, energy
 * and fluid where the machine has them, plus the transmitter's network energy and the holder's experience;</li>
 * <li><b>Sensor</b>: what it senses and whether it emits a signal (read-only).</li>
 * </ul>
 * Generators and machines get the machine's own redstone mode (Always / without signal / with signal / per pulse) as a
 * select ({@code act.redstone}, confirm), set exactly like the machine's screen does.
 */
public final class JustDireCards {
    public static final String STATUS = "status";
    public static final String ENERGY = "energy";
    public static final String OUTPUT = "output";
    public static final String FUEL = "fuel";
    public static final String FLUID = "fluid";
    public static final String SPEED = "speed";
    public static final String EXTRA = "extra";
    public static final String REDSTONE = "redstone";
    public static final String TARGET = "target";
    public static final String SIGNAL = "signal";
    /** In the order of JDT's {@code RedstoneMode} enum (IGNORED, LOW, HIGH, PULSE). */
    static final List<String> MODES = List.of("Always on", "On without signal", "On with signal", "One step per pulse");

    /** Block names (a dedicated server has no translations of other mods). */
    static final Map<String, String> NAMES = Map.ofEntries(
        Map.entry("generatort1", "Simple Coal Generator"), Map.entry("generatorfluidt1", "Simple Fuel Generator"),
        Map.entry("blockbreakert1", "Simple Block Breaker"), Map.entry("blockbreakert2", "Advanced Block Breaker"),
        Map.entry("blockplacert1", "Simple Block Placer"), Map.entry("blockplacert2", "Advanced Block Placer"),
        Map.entry("blockswappert1", "Simple Swapper"), Map.entry("blockswappert2", "Advanced Swapper"),
        Map.entry("clickert1", "Simple Clicker"), Map.entry("clickert2", "Advanced Clicker"),
        Map.entry("droppert1", "Simple Dropper"), Map.entry("droppert2", "Advanced Dropper"),
        Map.entry("fluidcollectort1", "Simple Fluid Collector"), Map.entry("fluidcollectort2", "Advanced Fluid Collector"),
        Map.entry("fluidplacert1", "Simple Fluid Placer"), Map.entry("fluidplacert2", "Advanced Fluid Placer"),
        Map.entry("sensort1", "Simple Sensor"), Map.entry("sensort2", "Advanced Sensor"),
        Map.entry("energytransmitter", "Energy Transmitter"), Map.entry("experienceholder", "Experience Holder"),
        Map.entry("itemcollector", "Item Collector"), Map.entry("paradoxmachine", "Paradox Machine"));

    private JustDireCards() {
    }

    public static void init() {
        DeviceCards.register("justdirethings", DeviceCards.SPECIFIC, JustDireCards::detect);
        Integrations.test("justdirethings_generator", () -> JustDireGameTests::generator);
        Integrations.test("justdirethings_machine", () -> JustDireGameTests::machine);
        Integrations.test("justdirethings_sensor", () -> JustDireGameTests::sensor);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GeneratorT1BE || be instanceof GeneratorFluidT1BE) return new Generator(level, pos, side, (BaseMachineBE) be);
        if (be instanceof SensorT1BE sensor) return new Sensor(level, pos, side, sensor);
        if (be instanceof BaseMachineBE machine && be instanceof RedstoneControlledBE) return new Machine(level, pos, side, machine);
        return null;
    }

    static String title(BlockEntity be) {
        String path = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath();
        return NAMES.getOrDefault(path, Fmt.title(path));
    }

    /** Whether a machine runs now under its redstone mode (read-only: JDT's own check also consumes pulses). */
    static String redstoneState(RedstoneControlledBE be) {
        var data = be.getRedstoneControlData();
        var mode = data.redstoneMode;
        if (mode == null || mode == MiscHelpers.RedstoneMode.IGNORED) return "Active";
        return switch (mode) {
            case LOW -> data.receivingRedstone ? "Off (redstone signal)" : "Active";
            case HIGH -> data.receivingRedstone ? "Active" : "Off (no redstone signal)";
            case PULSE -> "Waits for a redstone pulse";
            default -> "Active";
        };
    }

    static String mode(RedstoneControlledBE be) {
        var mode = be.getRedstoneControlData().redstoneMode;
        int i = mode == null ? 0 : mode.ordinal();
        return MODES.get(Math.max(0, Math.min(MODES.size() - 1, i)));
    }

    /** Sets the redstone mode like the machine's screen ({@code RedstoneSettingPacket} → {@code setRedstoneSettings}). */
    static @Nullable String setMode(BlockCard<?> card, RedstoneControlledBE be, JsonElement value, String user, BlockPos pos) {
        int i = MODES.indexOf(value.getAsString());
        if (i < 0 || i >= MiscHelpers.RedstoneMode.values().length) return "invalid_value";
        be.setRedstoneSettings(i);
        CCCloud.LOGGER.info("[cccloud] {} set the redstone mode of the {} at {} to {}", user, title(be.getBlockEntity()), pos.toShortString(), MODES.get(i));
        return null;
    }

    static String fluid(FluidMachineBE be) {
        var stack = be.getFluidStack();
        return stack == null || stack.isEmpty() ? "Empty" : Slots.fluid(stack.getFluid(), be.getAmountStored(), be.getMaxMB());
    }

    // ---- generator ---------------------------------------------------------------------------------------------------

    static final class Generator extends BlockCard<BaseMachineBE> {
        Generator(ServerLevel level, BlockPos pos, Direction side, BaseMachineBE be) {
            super(level, pos, side, be);
        }

        @Override
        public String kind() {
            return "justdirethings_generator";
        }

        @Override
        public String title() {
            return JustDireCards.title(be);
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(OUTPUT, "Output", "span", 2)
                .percent(ENERGY, "Energy", "color", "green", "icon", "bolt");
            if (be instanceof GeneratorFluidT1BE) w.label(FLUID, "Fuel tank");
            else w.progress(FUEL, "Burn left", 0, 100, "%").label(EXTRA, "Fuel");
            return w.select(REDSTONE, "Redstone mode", MODES, "confirm", true).build();
        }

        @Override
        public void refresh(CardOutput out) {
            var powered = (PoweredMachineBE) be;
            var redstone = (RedstoneControlledBE) be;
            int stored = powered.getEnergyStored(), max = powered.getMaxEnergy();
            out.number(ENERGY, Fmt.percent(stored, max));
            String active = redstoneState(redstone);
            if (be instanceof GeneratorT1BE g) {
                boolean burning = g.feRemaining > 0 || g.burnRemaining > 0;
                out.number(FUEL, g.maxBurn > 0 ? Math.round(g.burnRemaining * 100.0 / g.maxBurn) : 0);
                out.text(EXTRA, Slots.item(g.getMachineHandler(), 0));
                out.text(OUTPUT, burning ? Fmt.flow(g.fePerTick()) : "0 FE/t");
                out.text(STATUS, burning ? (stored >= max ? "Full" : "Generating") : !active.equals("Active") ? active : "No fuel");
            } else if (be instanceof GeneratorFluidT1BE g) {
                boolean fuel = g.getAmountStored() > 0;
                out.text(FLUID, fluid(g));
                out.text(OUTPUT, fuel && active.equals("Active") ? Fmt.flow(g.getFEOutputPerTick()) : "0 FE/t");
                out.text(STATUS, !active.equals("Active") ? active : !fuel ? "No fuel" : stored >= max ? "Full" : "Generating");
            }
            out.text(REDSTONE, mode(redstone));
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            if (!REDSTONE.equals(widgetId)) return "not_interactive";
            String error = setMode(this, (RedstoneControlledBE) be, value, user, pos);
            if (error == null) changed();
            return error;
        }
    }

    // ---- machines ----------------------------------------------------------------------------------------------------

    static final class Machine extends BlockCard<BaseMachineBE> {
        Machine(ServerLevel level, BlockPos pos, Direction side, BaseMachineBE be) {
            super(level, pos, side, be);
        }

        @Override
        public String kind() {
            return "justdirethings_machine";
        }

        @Override
        public String title() {
            return JustDireCards.title(be);
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(SPEED, "Speed", "span", 2);
            if (be instanceof PoweredMachineBE) w.percent(ENERGY, "Energy", "color", "green", "icon", "bolt");
            if (be instanceof FluidMachineBE) w.label(FLUID, "Tank");
            if (be instanceof EnergyTransmitterBE) w.label(EXTRA, "Network energy");
            else if (be instanceof ExperienceHolderBE) w.label(EXTRA, "Experience");
            return w.select(REDSTONE, "Redstone mode", MODES, "confirm", true).build();
        }

        @Override
        public void refresh(CardOutput out) {
            var redstone = (RedstoneControlledBE) be;
            out.text(STATUS, redstoneState(redstone));
            int ticks = be.getTickSpeed();
            out.text(SPEED, ticks <= 1 ? "Every tick" : String.format(Locale.ROOT, "Every %d ticks", ticks));
            if (be instanceof PoweredMachineBE p) {
                // the transmitter's own capacity: getEnergyStored() of its storage is the whole network
                if (be instanceof EnergyTransmitterBE t) out.number(ENERGY, Fmt.percent(t.getEnergyStored(), t.getMaxEnergy()));
                else out.number(ENERGY, Fmt.percent(p.getEnergyStored(), p.getMaxEnergy()));
            }
            if (be instanceof FluidMachineBE f) out.text(FLUID, fluid(f));
            if (be instanceof EnergyTransmitterBE t) out.text(EXTRA, Fmt.energy(t.getTotalEnergyStored(), t.getTotalMaxEnergyStored()));
            else if (be instanceof ExperienceHolderBE x) out.text(EXTRA, Fmt.si(x.exp) + " XP");
            out.text(REDSTONE, mode(redstone));
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            if (!REDSTONE.equals(widgetId)) return "not_interactive";
            String error = setMode(this, (RedstoneControlledBE) be, value, user, pos);
            if (error == null) changed();
            return error;
        }
    }

    // ---- sensors -----------------------------------------------------------------------------------------------------

    static final class Sensor extends BlockCard<SensorT1BE> {
        Sensor(ServerLevel level, BlockPos pos, Direction side, SensorT1BE be) {
            super(level, pos, side, be);
        }

        @Override
        public String kind() {
            return "justdirethings_sensor";
        }

        @Override
        public String title() {
            return JustDireCards.title(be);
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .indicator(SIGNAL, "Signal out", "onColor", "red")
                .label(TARGET, "Senses", "span", 2)
                .label(STATUS, "Signal", "span", 2)
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            out.bool(SIGNAL, be.emitRedstone);
            out.text(TARGET, be.sense_target == null ? "–" : Fmt.title(be.sense_target.name().toLowerCase(Locale.ROOT)));
            out.text(STATUS, be.strongSignal ? "Strong" : "Weak");
        }
    }
}
