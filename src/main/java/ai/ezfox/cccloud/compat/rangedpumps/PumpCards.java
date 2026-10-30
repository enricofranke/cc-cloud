package ai.ezfox.cccloud.compat.rangedpumps;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.card.Slots;
import ai.ezfox.cccloud.uplink.Widget;
import com.refinedmods.rangedpumps.RangedPumps;
import com.refinedmods.rangedpumps.blockentity.PumpBlockEntity;
import com.refinedmods.rangedpumps.blockentity.PumpState;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Ranged Pumps (wave 4): the pump's state (working with its scan range, no energy, stopped by redstone, tank full,
 * done), the fluid in its tank and its energy; info alert when it is done, warn alert when the tank is full. No
 * control: any redstone signal stops a pump, it has no mode to switch. The scan position (coordinates) is not shown.
 */
public final class PumpCards {
    public static final String STATUS = "status";
    public static final String TANK = "tank";
    public static final String FLUID = "fluid";
    public static final String ENERGY = "energy";
    public static final String RANGE = "range";

    private static @Nullable Method getState;
    private static @Nullable Method getRange;
    private static boolean reflectionFailed;

    private PumpCards() {
    }

    public static void init() {
        DeviceCards.register("rangedpumps", DeviceCards.SPECIFIC, PumpCards::detect);
        Integrations.test("rangedpumps_pump", () -> PumpGameTests::pump);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof PumpBlockEntity pump ? new Pump(level, pos, side, pump) : null;
    }

    /** The pump's state ({@code getState()} is package-private); computed like the pump does if reflection fails. */
    static PumpState state(PumpBlockEntity pump, ServerLevel level, BlockPos pos) {
        if (!reflectionFailed) {
            try {
                if (getState == null) {
                    var m = PumpBlockEntity.class.getDeclaredMethod("getState");
                    m.setAccessible(true);
                    getState = m;
                }
                return (PumpState) getState.invoke(pump);
            } catch (ReflectiveOperationException | RuntimeException e) {
                failed(e);
            }
        }
        var tank = pump.getTank();
        if (level.hasNeighborSignal(pos)) return PumpState.REDSTONE;
        if (pump.getEnergy().getAmountAsLong() == 0) return PumpState.ENERGY;
        if (tank.getAmountAsLong(0) > tank.getCapacityAsLong(0, tank.getResource(0)) - 1000) return PumpState.FULL;
        return PumpState.WORKING;
    }

    static int range(PumpBlockEntity pump) {
        if (reflectionFailed) return -1;
        try {
            if (getRange == null) {
                var m = PumpBlockEntity.class.getDeclaredMethod("getRange");
                m.setAccessible(true);
                getRange = m;
            }
            return (int) getRange.invoke(pump);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed(e);
            return -1;
        }
    }

    private static void failed(Exception e) {
        if (!reflectionFailed) ai.ezfox.cccloud.CCCloud.LOGGER.warn("[cccloud] Ranged Pumps: cannot read the pump's state and range directly, the card computes them ({})", e.toString());
        reflectionFailed = true;
    }

    static final class Pump extends BlockCard<PumpBlockEntity> {
        private @Nullable PumpState last;

        Pump(ServerLevel level, BlockPos pos, Direction side, PumpBlockEntity pump) {
            super(level, pos, side, pump);
        }

        @Override
        public String kind() {
            return "rangedpumps_pump";
        }

        @Override
        public String title() {
            return "Pump";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status")
                .percent(TANK, "Tank", "color", "cyan", "icon", "drop")
                .percent(ENERGY, "Energy", "color", "green", "icon", "bolt")
                .label(FLUID, "Fluid")
                .label(RANGE, "Scan range")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var state = state(be, level, pos);
            int max = RangedPumps.SERVER_CONFIG.getRange();
            int range = range(be);
            out.text(STATUS, switch (state) {
                case WORKING -> "Pumping";
                case ENERGY -> "No energy";
                case REDSTONE -> "Stopped by redstone";
                case FULL -> "Tank full";
                case DONE -> "Done – nothing left in range";
            });
            out.text(RANGE, state == PumpState.DONE ? max + " (all)" : range < 0 ? "Not started" : range + " of " + max);
            var tank = be.getTank();
            var resource = tank.getResource(0);
            long amount = resource.isEmpty() ? 0 : tank.getAmountAsLong(0);
            long capacity = Math.max(1, RangedPumps.SERVER_CONFIG.getTankCapacity());
            out.number(TANK, Fmt.percent(amount, capacity));
            out.text(FLUID, Slots.fluid(resource.isEmpty() ? null : resource.getFluid(), amount, capacity));
            var energy = be.getEnergy();
            out.number(ENERGY, RangedPumps.SERVER_CONFIG.getUseEnergy() ? Fmt.percent(energy.getAmountAsLong(), energy.getCapacityAsLong()) : 100);
            if (state != last && last != null) {
                if (state == PumpState.DONE) out.alert("Pump: done, nothing left in range", "info");
                else if (state == PumpState.FULL) out.alert("Pump: tank full", "warn");
            }
            last = state;
        }
    }
}
