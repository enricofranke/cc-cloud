package ai.ezfox.cccloud.compat.charginggadgets;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.card.Slots;
import ai.ezfox.cccloud.uplink.Widget;
import com.direwolf20.charginggadgets.blocks.chargingstation.ChargingStationTile;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import org.jspecify.annotations.Nullable;

/**
 * Charging Gadgets (wave 4): the Charging Station – status, its energy, burn left, the fuel and the item being charged
 * with that item's own charge; info alert when the item is full. No control (the station has none).
 */
public final class ChargingStationCards {
    public static final String STATUS = "status";
    public static final String ENERGY = "energy";
    public static final String BURN = "burn";
    public static final String FUEL = "fuel";
    public static final String ITEM = "item";
    public static final String CHARGE = "charge";

    private ChargingStationCards() {
    }

    public static void init() {
        DeviceCards.register("charginggadgets", DeviceCards.SPECIFIC, ChargingStationCards::detect);
        Integrations.test("charginggadgets_station", () -> ChargingStationGameTests::station);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof ChargingStationTile station ? new Station(level, pos, side, station) : null;
    }

    static final class Station extends BlockCard<ChargingStationTile> {
        private boolean wasFull;

        Station(ServerLevel level, BlockPos pos, Direction side, ChargingStationTile station) {
            super(level, pos, side, station);
        }

        @Override
        public String kind() {
            return "charginggadgets_station";
        }

        @Override
        public String title() {
            return "Charging Station";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status")
                .percent(ENERGY, "Station energy", "color", "green", "icon", "bolt")
                .percent(CHARGE, "Item charge", "color", "cyan", "icon", "bolt")
                .label(ITEM, "Charging", "span", 2)
                .label(FUEL, "Fuel", "span", 2)
                .progress(BURN, "Burn left", 0, 100, "%")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var data = be.chargingStationData;
            int burn = data.getCount() > 3 ? data.get(2) : 0, maxBurn = data.getCount() > 3 ? data.get(3) : 0;
            out.number(ENERGY, Fmt.percent(be.energyStorage.getAmountAsLong(), be.energyStorage.getCapacityAsLong()));
            out.number(BURN, maxBurn > 0 ? Math.round(burn * 100.0 / maxBurn) : 0);
            int fuelSlot = ChargingStationTile.Slots.FUEL.getId(), chargeSlot = ChargingStationTile.Slots.CHARGE.getId();
            out.text(FUEL, Slots.item(be.inventory, fuelSlot));
            out.text(ITEM, Slots.item(be.inventory, chargeSlot));
            boolean item = !Slots.empty(be.inventory, chargeSlot);
            var energy = item ? ItemAccess.forHandlerIndex(be.inventory, chargeSlot).getCapability(Capabilities.Energy.ITEM) : null;
            double charge = energy == null ? 0 : Fmt.percent(energy.getAmountAsLong(), energy.getCapacityAsLong());
            out.number(CHARGE, charge);
            boolean full = energy != null && energy.getCapacityAsLong() > 0 && energy.getAmountAsLong() >= energy.getCapacityAsLong();
            out.text(STATUS, !item ? (burn > 0 ? "Burning fuel" : "Idle") : energy == null ? "Item takes no energy" : full ? "Item charged" : "Charging");
            if (full && !wasFull) out.alert("Charging Station: item charged", "info");
            wasFull = full;
        }
    }
}
