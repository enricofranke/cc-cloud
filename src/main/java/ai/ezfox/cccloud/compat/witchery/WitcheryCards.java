package ai.ezfox.cccloud.compat.witchery;

import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import dev.sterner.witchery.content.block.altar.AltarBlockEntity;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Witchery altar (wave 3): current / maximum altar power, power gauge, range and the augment multiplier and boost; a
 * warn alert once when the power drops below 10 %.
 */
public final class WitcheryCards {
    public static final String POWER = "power";
    public static final String POWER_TEXT = "power_text";
    public static final String RANGE = "range";
    public static final String AUGMENTS = "augments";

    private WitcheryCards() {
    }

    public static void init() {
        DeviceCards.register("witchery", DeviceCards.SPECIFIC, WitcheryCards::detect);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof AltarBlockEntity altar ? new Altar(level, pos, side, altar) : null;
    }

    static final class Altar extends BlockCard<AltarBlockEntity> {
        private boolean wasLow;

        Altar(ServerLevel level, BlockPos pos, Direction side, AltarBlockEntity altar) {
            super(level, pos, side, altar);
        }

        @Override
        public String kind() {
            return "witchery_altar";
        }

        @Override
        public String title() {
            return "Altar";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .percent(POWER, "Altar power", "color", "green", "warn", 25, "crit", 10)
                .label(POWER_TEXT, "Power")
                .label(RANGE, "Range")
                .label(AUGMENTS, "Augments", "span", 2)
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            int current = be.getCurrentPower(), max = be.getMaxPower();
            double pct = Fmt.percent(current, max);
            out.number(POWER, pct);
            out.text(POWER_TEXT, Fmt.si(current) + " / " + Fmt.si(max));
            out.text(RANGE, be.getRange() + " blocks");
            out.text(AUGMENTS, String.format(Locale.ROOT, "×%.2f rate, +%.0f boost", be.getPowerMultiplier(), be.getPowerBoost()));
            boolean low = max > 0 && pct < 10;
            if (low && !wasLow) out.alert("Altar power below 10 %", "warn");
            wasLow = low;
        }
    }
}
