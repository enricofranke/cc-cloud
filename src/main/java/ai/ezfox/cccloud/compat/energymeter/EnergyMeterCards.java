package ai.ezfox.cccloud.compat.energymeter;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.google.gson.JsonElement;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Energy Meter (energymeter): live flow in FE/t with a 60-minute chart, connection status, transferred total and the
 * transfer mode (Split / Transfer / Consume, changeable from the dashboard).
 */
public final class EnergyMeterCards {
    public static final String FLOW = "flow";
    public static final String HISTORY = "history";
    public static final String STATUS = "status";
    public static final String TOTAL = "total";
    public static final String MODE = "mode";
    public static final String LIMIT = "limit";
    static final List<String> MODES = List.of("Split", "Transfer", "Consume");

    private EnergyMeterCards() {
    }

    public static void init() {
        DeviceCards.register("energymeter", DeviceCards.SPECIFIC, EnergyMeterCards::detect);
        Integrations.test("energymeter_meter", () -> EnergyMeterGameTests::meter);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof MeterBlockEntity meter ? new Meter(level, pos, side, meter) : null;
    }

    static final class Meter extends BlockCard<MeterBlockEntity> {
        Meter(ServerLevel level, BlockPos pos, Direction side, MeterBlockEntity meter) {
            super(level, pos, side, meter);
        }

        @Override
        public String kind() {
            return "energymeter";
        }

        @Override
        public String title() {
            return "Energy Meter";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(FLOW, "Flow", "icon", "bolt")
                .label(STATUS, "Status")
                .history(HISTORY, "Flow (60 min)", null, null, "FE/t")
                .label(TOTAL, "Transferred")
                .label(LIMIT, "Transfer limit")
                .select(MODE, "Transfer mode", MODES, "confirm", true)
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            double rate = be.getEnergyRate();
            out.text(FLOW, Fmt.flow(rate));
            out.number(HISTORY, Math.round(rate * 10) / 10.0);
            out.text(STATUS, Fmt.title(be.getConnectionStatus().name().toLowerCase(Locale.ROOT)));
            out.text(TOTAL, Fmt.si(be.getTotalEnergy()) + " FE");
            long limit = be.getTransferLimit();
            out.text(LIMIT, limit <= 0 ? "Unlimited" : Fmt.flow(limit));
            out.text(MODE, MODES.get(be.getTransferMode().ordinal()));
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            if (!MODE.equals(widgetId)) return "not_interactive";
            int i = MODES.indexOf(value.getAsString());
            if (i < 0) return "invalid_value";
            be.setTransferMode(MeterBlockEntity.TransferMode.values()[i]);
            changed();
            CCCloud.LOGGER.info("[cccloud] {} set the energy meter at {} to {}", user, pos.toShortString(), value.getAsString());
            return null;
        }
    }
}
