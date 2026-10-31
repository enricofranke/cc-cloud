package ai.ezfox.cccloud.compat.refinedtypes;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import com.ultramega.refinedtypes.networkenergizer.NetworkEnergizerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Refined Types: the Network Energizer feeds a Refined Storage network from the FE on its Energy Disks. The card shows
 * that reserve (the values of the block's own screen) and warns once below {@link #LOW} percent, before the network
 * loses power. No control: its redstone mode is a screen setting without a public setter.
 */
public final class EnergizerCards {
    public static final String RESERVE = "reserve";
    public static final String ENERGY = "energy";
    /** Percent below which the card warns (the reserve is what keeps the network alive). */
    static final double LOW = 10;

    private EnergizerCards() {
    }

    public static void init() {
        DeviceCards.register("refinedtypes", DeviceCards.SPECIFIC, EnergizerCards::detect);
        Integrations.test("refinedtypes_energizer", () -> EnergizerGameTests::energizer);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof NetworkEnergizerBlockEntity e ? new Energizer(level, pos, side, e) : null;
    }

    static final class Energizer extends BlockCard<NetworkEnergizerBlockEntity> {
        private boolean wasLow;

        Energizer(ServerLevel level, BlockPos pos, Direction side, NetworkEnergizerBlockEntity e) {
            super(level, pos, side, e);
        }

        @Override
        public String kind() {
            return "refinedtypes_energizer";
        }

        @Override
        public String title() {
            return "Network Energizer";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .percent(RESERVE, "Reserve on Energy Disks", "color", "green", "icon", "bolt", "warn", 10, "crit", 3)
                .label(ENERGY, "Stored")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var data = be.getMenuData();
            double percent = Fmt.percent(data.stored(), data.capacity());
            out.number(RESERVE, percent);
            out.text(ENERGY, data.capacity() > 0 ? Fmt.energy(data.stored(), data.capacity()) : "No Energy Disks");
            boolean low = data.capacity() > 0 && percent < LOW;
            if (low && !wasLow) out.alert("Network Energizer: energy reserve below " + (int) LOW + " %", "warn");
            wasLow = low;
        }
    }
}
