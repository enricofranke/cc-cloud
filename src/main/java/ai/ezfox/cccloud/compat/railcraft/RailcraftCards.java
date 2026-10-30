package ai.ezfox.cccloud.compat.railcraft;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import java.util.List;
import mods.railcraft.world.level.block.entity.BlastFurnaceBlockEntity;
import mods.railcraft.world.level.block.entity.CokeOvenBlockEntity;
import mods.railcraft.world.level.block.entity.steamboiler.SteamBoilerBlockEntity;
import mods.railcraft.world.level.block.entity.tank.TankBlockEntity;
import mods.railcraft.world.level.material.StandardTank;
import mods.railcraft.world.module.BlastFurnaceModule;
import mods.railcraft.world.module.CokeOvenModule;
import mods.railcraft.world.module.SteamBoilerModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Railcraft Reborn multiblocks (docs/SPEC.md v0.8 item 5, docs/COMPAT.md): an uplink or a Cloud Probe on any block of
 * a multiblock reads the formed structure through its master block entity ({@code getMasterBlockEntity()}), like the
 * other multiblock cards. Steam boilers (firebox or tank): temperature, water, steam, fuel and burning state; a crit
 * alert fires once when the water runs low while the boiler is hot (Railcraft boilers explode when water is added to a
 * dry, hot boiler). Iron and steel tanks (walls, gauges, valves): fill, fluid, amount. Coke ovens: what cooks, progress,
 * input, output, creosote (warn alert once when the creosote tank is full). Blast furnaces: what smelts, progress, fuel,
 * input, output, slag. Read-only.
 */
public final class RailcraftCards {
    public static final String STATUS = "status";
    public static final String TEMPERATURE = "temperature";
    public static final String WATER = "water";
    public static final String STEAM = "steam";
    public static final String BURNING = "burning";
    /** Water below this share of the tank counts as low. */
    static final double LOW_WATER = 0.2;

    private RailcraftCards() {
    }

    public static void init() {
        DeviceCards.register("railcraft", DeviceCards.SPECIFIC, RailcraftCards::detect);
        Integrations.test("railcraft_boiler", () -> RailcraftGameTests::boiler);
        Integrations.test("railcraft_multiblocks", () -> RailcraftGameTests::multiblocks);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        if (be instanceof SteamBoilerBlockEntity b) return new Boiler(level, pos, side, b);
        if (be instanceof TankBlockEntity t) return new Tank(level, pos, side, t);
        if (be instanceof CokeOvenBlockEntity c) return new CokeOven(level, pos, side, c);
        if (be instanceof BlastFurnaceBlockEntity f) return new BlastFurnace(level, pos, side, f);
        return null;
    }

    static final String FILL = "fill";
    static final String FLUID = "fluid";
    static final String AMOUNT = "amount";
    static final String PROGRESS = "progress";
    static final String INPUT = "input";
    static final String OUTPUT = "output";
    static final String CREOSOTE = "creosote";
    static final String FUEL = "fuel";
    static final String SLAG = "slag";
    static final String NOT_FORMED = "Not formed";

    /** "64× Coal" or "–" for an empty slot. */
    static String stack(Container container, int slot) {
        if (slot < 0 || slot >= container.getContainerSize()) return "–";
        var stack = container.getItem(slot);
        return stack.isEmpty() ? "–" : stack.getCount() + "× " + stack.getHoverName().getString();
    }

    static String fluidName(StandardTank tank) {
        return tank.isEmpty() ? "Empty" : Fmt.title(BuiltInRegistries.FLUID.getKey(tank.getFluidStack().getFluid()).getPath());
    }

    /** Iron or steel tank: any wall, gauge or valve reads the formed tank. */
    static final class Tank extends BlockCard<TankBlockEntity> {
        Tank(ServerLevel level, BlockPos pos, Direction side, TankBlockEntity be) {
            super(level, pos, side, be);
        }

        @Override
        public String kind() {
            return "railcraft_tank";
        }

        @Override
        public String title() {
            return be instanceof mods.railcraft.world.level.block.entity.tank.SteelTankBlockEntity ? "Steel Tank" : "Iron Tank";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .percent(FILL, "Filled", "color", "cyan")
                .label(FLUID, "Fluid")
                .label(AMOUNT, "Amount")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var master = be.isFormed() ? be.getMasterBlockEntity() : java.util.Optional.<TankBlockEntity>empty();
            if (master.isEmpty()) {
                out.text(STATUS, NOT_FORMED);
                out.number(FILL, 0);
                out.text(FLUID, "–");
                out.text(AMOUNT, "–");
                return;
            }
            var tank = master.get().getModule().getTank();
            out.text(STATUS, "Formed");
            out.number(FILL, Fmt.percent(tank.getFluidAmount(), tank.getCapacity()));
            out.text(FLUID, fluidName(tank));
            out.text(AMOUNT, Fmt.si(tank.getFluidAmount()) + " / " + Fmt.si(tank.getCapacity()) + " mB");
        }
    }

    /** Coke oven: any brick reads the formed oven. */
    static final class CokeOven extends BlockCard<CokeOvenBlockEntity> {
        private boolean wasFull;

        CokeOven(ServerLevel level, BlockPos pos, Direction side, CokeOvenBlockEntity be) {
            super(level, pos, side, be);
        }

        @Override
        public String kind() {
            return "railcraft_coke_oven";
        }

        @Override
        public String title() {
            return "Coke Oven";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .percent(PROGRESS, "Progress", "color", "yellow")
                .percent(CREOSOTE, "Creosote", "color", "gray", "warn", 90)
                .label(INPUT, "Input")
                .label(OUTPUT, "Output")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var master = be.isFormed() ? be.getMasterBlockEntity() : java.util.Optional.<CokeOvenBlockEntity>empty();
            if (master.isEmpty()) {
                out.text(STATUS, NOT_FORMED);
                out.number(PROGRESS, 0);
                out.number(CREOSOTE, 0);
                out.text(INPUT, "–");
                out.text(OUTPUT, "–");
                wasFull = false;
                return;
            }
            var module = master.get().getCokeOvenModule();
            var container = module.container();
            var tank = module.getTank();
            boolean full = tank.getCapacity() > 0 && tank.isFull();
            String input = stack(container, CokeOvenModule.SLOT_INPUT);
            out.text(STATUS, module.isProcessing() ? "Cooking " + input : full ? "Creosote tank full" : "Idle");
            out.number(PROGRESS, module.isProcessing() ? Math.round(module.getProgressPercent() * 1000) / 10.0 : 0);
            out.number(CREOSOTE, Fmt.percent(tank.getFluidAmount(), tank.getCapacity()));
            out.text(INPUT, input);
            out.text(OUTPUT, stack(container, CokeOvenModule.SLOT_OUTPUT));
            if (full && !wasFull) out.alert("Coke oven: the creosote tank is full", "warn");
            wasFull = full;
        }
    }

    /** Blast furnace: any brick reads the formed furnace. */
    static final class BlastFurnace extends BlockCard<BlastFurnaceBlockEntity> {
        BlastFurnace(ServerLevel level, BlockPos pos, Direction side, BlastFurnaceBlockEntity be) {
            super(level, pos, side, be);
        }

        @Override
        public String kind() {
            return "railcraft_blast_furnace";
        }

        @Override
        public String title() {
            return "Blast Furnace";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .percent(PROGRESS, "Progress", "color", "yellow")
                .indicator(BURNING, "Burning", "onColor", "yellow", "offColor", "gray")
                .label(INPUT, "Input")
                .label(FUEL, "Fuel")
                .label(OUTPUT, "Output")
                .label(SLAG, "Slag")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var master = be.isFormed() ? be.getMasterBlockEntity() : java.util.Optional.<BlastFurnaceBlockEntity>empty();
            if (master.isEmpty()) {
                out.text(STATUS, NOT_FORMED);
                out.number(PROGRESS, 0);
                out.bool(BURNING, false);
                for (var id : List.of(INPUT, FUEL, OUTPUT, SLAG)) out.text(id, "–");
                return;
            }
            var module = master.get().getBlastFurnaceModule();
            var container = module.container();
            String input = stack(container, BlastFurnaceModule.SLOT_INPUT);
            out.text(STATUS, module.isProcessing() ? "Smelting " + input : module.isBurning() ? "Burning, nothing to smelt" : "Idle");
            out.number(PROGRESS, module.isProcessing() ? Math.round(module.getProgressPercent() * 1000) / 10.0 : 0);
            out.bool(BURNING, module.isBurning());
            out.text(INPUT, input);
            String fuel = stack(container, BlastFurnaceModule.SLOT_FUEL);
            out.text(FUEL, module.isBurning() ? fuel + " · " + Fmt.duration(module.getBurnTime() / 20) + " left" : fuel);
            out.text(OUTPUT, stack(container, BlastFurnaceModule.SLOT_OUTPUT));
            out.text(SLAG, stack(container, BlastFurnaceModule.SLOT_SLAG));
        }
    }

    static final class Boiler extends BlockCard<SteamBoilerBlockEntity> {
        private boolean wasDanger;

        Boiler(ServerLevel level, BlockPos pos, Direction side, SteamBoilerBlockEntity b) {
            super(level, pos, side, b);
        }

        @Override
        public String kind() {
            return "railcraft_boiler";
        }

        @Override
        public String title() {
            return "Steam Boiler";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .gauge(TEMPERATURE, "Temperature", 0, 100, "%", "warn", 90, "color", "yellow")
                .percent(WATER, "Water", "color", "cyan", "warn", 30, "crit", 20)
                .percent(STEAM, "Steam", "color", "gray")
                .indicator(BURNING, "Burning", "onColor", "yellow", "offColor", "gray")
                .build();
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void refresh(CardOutput out) {
            var master = be.getMasterBlockEntity();
            var module = master.isPresent() && be.isFormed() ? master.get().getModule(SteamBoilerModule.class) : java.util.Optional.<SteamBoilerModule>empty();
            if (module.isEmpty()) {
                out.text(STATUS, "Not formed");
                out.number(TEMPERATURE, 0);
                out.number(WATER, 0);
                out.number(STEAM, 0);
                out.bool(BURNING, false);
                wasDanger = false;
                return;
            }
            var m = (SteamBoilerModule) module.get();
            var boiler = m.getBoiler();
            var water = m.getWaterTank();
            var steam = m.getSteamTank();
            double waterShare = water.getCapacity() > 0 ? (double) water.getFluidAmount() / water.getCapacity() : 0;
            boolean hot = !boiler.isCold();
            boolean danger = hot && waterShare < LOW_WATER;
            String status = danger ? "Low water while hot!" : boiler.isBurning() ? (boiler.isSuperHeated() ? "Making steam" : "Heating up") : boiler.hasFuel() ? "Idle" : "No fuel";
            out.text(STATUS, status + String.format(java.util.Locale.ROOT, " – %.0f °C", boiler.getTemperature()));
            out.number(TEMPERATURE, Math.round(boiler.getTemperaturePercent() * 1000) / 10.0);
            out.number(WATER, Fmt.percent(water.getFluidAmount(), water.getCapacity()));
            out.number(STEAM, Fmt.percent(steam.getFluidAmount(), steam.getCapacity()));
            out.bool(BURNING, boiler.isBurning());
            if (danger && !wasDanger) out.alert("Steam boiler: low water while hot – refilling a dry hot boiler makes it explode", "crit");
            wasDanger = danger;
        }
    }
}
