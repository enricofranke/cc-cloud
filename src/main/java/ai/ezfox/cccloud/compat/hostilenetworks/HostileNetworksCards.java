package ai.ezfox.cccloud.compat.hostilenetworks;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import dev.shadowsoffire.hostilenetworks.data.DataModelInstance;
import dev.shadowsoffire.hostilenetworks.tile.SimChamberTileEntity;
import dev.shadowsoffire.hostilenetworks.tile.SimChamberTileEntity.FailureState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Hostile Neural Networks simulation chamber: model, data, progress, energy and status. Raises an alert once when the
 * chamber gets blocked (output full, no power, no prediction matrix, ...) and once when it works again.
 */
public final class HostileNetworksCards {
    public static final String MODEL = "model";
    public static final String DATA = "data";
    public static final String PROGRESS = "progress";
    public static final String ENERGY = "energy";
    public static final String STATUS = "status";
    public static final String RUNNING = "running";
    public static final String OUTPUT_FULL = "output_full";

    private HostileNetworksCards() {
    }

    public static void init() {
        DeviceCards.register("hostilenetworks", DeviceCards.SPECIFIC, HostileNetworksCards::detect);
        Integrations.test("hostilenetworks_chamber", () -> HostileNetworksGameTests::chamber);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof SimChamberTileEntity chamber ? new Chamber(level, pos, side, chamber) : null;
    }

    /** Plain English for the chamber's failure states (the mod's own texts are client-side translations). */
    static String describe(FailureState state) {
        return switch (state.name()) {
            case "NONE" -> "Running";
            case "OUTPUT" -> "Output full";
            case "ENERGY", "ENERGY_MID_CYCLE" -> "Not enough power";
            case "INPUT" -> "No prediction matrix";
            case "MODEL" -> "No data model";
            case "FAULTY" -> "Model tier cannot simulate";
            case "REDSTONE" -> "Stopped by redstone";
            case "CANNOT_TRAIN" -> "Model cannot be trained further";
            default -> Fmt.title(state.name().toLowerCase(java.util.Locale.ROOT));
        };
    }

    /** States in which the chamber waits for the player (alert once per transition). */
    static boolean isBlocked(FailureState state) {
        return switch (state.name()) {
            case "OUTPUT", "ENERGY", "ENERGY_MID_CYCLE", "INPUT", "FAULTY" -> true;
            default -> false;
        };
    }

    static final class Chamber extends BlockCard<SimChamberTileEntity> {
        private @Nullable FailureState lastState;

        Chamber(ServerLevel level, BlockPos pos, Direction side, SimChamberTileEntity chamber) {
            super(level, pos, side, chamber);
        }

        @Override
        public String kind() {
            return "hostilenetworks_chamber";
        }

        @Override
        public String title() {
            return "Simulation Chamber";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .indicator(RUNNING, "Simulating", "onColor", "green", "offColor", "gray")
                .indicator(OUTPUT_FULL, "Output full", "onColor", "red", "offColor", "gray")
                .label(MODEL, "Data model")
                .label(DATA, "Data")
                .progress(PROGRESS, "Cycle", 0, 100, "%")
                .percent(ENERGY, "Energy", "color", "green", "icon", "bolt")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var items = be.getInventory().getItems();
            var model = new DataModelInstance(items.get(0), 0);
            var state = be.getFailState();
            boolean valid = model.isValid();
            int runtime = be.getRuntime();
            int total = be.getSimMode().getRuntime();
            out.text(STATUS, describe(state));
            out.bool(RUNNING, runtime > 0 && state == FailureState.NONE);
            boolean outputFull = valid && (!be.canStack(items.get(2), dev.shadowsoffire.hostilenetworks.util.Templates.create(model.getModel().baseDrop())) || !be.canStack(items.get(3), model.getPredictionDrop()));
            out.bool(OUTPUT_FULL, outputFull || state == FailureState.OUTPUT);
            out.text(MODEL, valid ? model.getModel().name().getString() : "–");
            out.text(DATA, valid ? (model.getTier().canSim() ? model.getData() + " / " + model.getNextTierData() + " (" + Math.round(model.getAccuracy() * 100) + " % accuracy)" : "Tier cannot simulate") : "–");
            out.number(PROGRESS, runtime > 0 && total > 0 ? Math.round((1 - (double) runtime / total) * 100) : 0);
            var energy = be.getEnergy();
            out.number(ENERGY, Fmt.percent(energy.getAmountAsLong(), energy.getCapacityAsLong()));
            if (lastState != null && state != lastState) {
                if (isBlocked(state)) out.alert("Simulation Chamber: " + describe(state), "warn");
                else if (isBlocked(lastState) && state == FailureState.NONE) out.alert("Simulation Chamber is running again", "info");
            }
            lastState = state;
        }
    }
}
