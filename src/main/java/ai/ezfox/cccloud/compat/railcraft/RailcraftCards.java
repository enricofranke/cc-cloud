package ai.ezfox.cccloud.compat.railcraft;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import java.util.List;
import mods.railcraft.world.level.block.entity.steamboiler.SteamBoilerBlockEntity;
import mods.railcraft.world.module.SteamBoilerModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Railcraft Reborn steam boilers (docs/SPEC.md v0.8 item 5): an uplink on any block of a boiler (firebox or tank)
 * shows the formed boiler's temperature, water, steam, fuel and burning state. A crit alert fires once when the water
 * runs low while the boiler is hot (Railcraft boilers explode when water is added to a dry, hot boiler).
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
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof SteamBoilerBlockEntity b ? new Boiler(level, pos, side, b) : null;
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
