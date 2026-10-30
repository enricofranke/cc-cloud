package ai.ezfox.cccloud.compat.sellingbin;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.card.Slots;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonElement;
import com.wdiscute.sellingbin.bin.Currency;
import com.wdiscute.sellingbin.bin.SellingBinBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.nikdo53.tinymultiblocklib.block.IMultiBlock;
import org.jspecify.annotations.Nullable;

/**
 * wd's Selling Bin (wave 4): the item waiting to be sold and what it is worth, the bin's credit (sold value not yet
 * paid out) and the currency waiting in the output, auto-sell as a toggle and "Sell now" as a button (both
 * {@code act.buttons}; selling is what the bin's own buttons do). The bin is two blocks; the card reads the main one.
 * The mod keeps no sales history, so the card shows the current credit, not past earnings.
 */
public final class SellingBinCards {
    public static final String SELLING = "selling";
    public static final String WORTH = "worth";
    public static final String CREDIT = "credit";
    public static final String PAYOUT = "payout";
    public static final String AUTO = "auto";
    public static final String SELL = "sell";

    private SellingBinCards() {
    }

    public static void init() {
        DeviceCards.register("selling_bin", DeviceCards.SPECIFIC, SellingBinCards::detect);
        Integrations.test("sellingbin_bin", () -> SellingBinGameTests::bin);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        if (!(level.getBlockEntity(pos) instanceof SellingBinBlockEntity)) return null;
        BlockPos center = IMultiBlock.getCenter(level, pos);
        if (center == null || !level.isLoaded(center)) center = pos;
        return level.getBlockEntity(center) instanceof SellingBinBlockEntity bin ? new Bin(level, center, side, bin) : null;
    }

    static final class Bin extends BlockCard<SellingBinBlockEntity> {
        Bin(ServerLevel level, BlockPos pos, Direction side, SellingBinBlockEntity bin) {
            super(level, pos, side, bin);
        }

        @Override
        public String kind() {
            return "sellingbin_bin";
        }

        @Override
        public String title() {
            return "Selling Bin";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(SELLING, "To sell", "span", 2)
                .label(WORTH, "Worth", "span", 2)
                .label(CREDIT, "Credit", "span", 2)
                .label(PAYOUT, "Paid out", "span", 2)
                .toggle(AUTO, "Auto-sell", "icon", "star")
                .button(SELL, "Sell now", "confirm", true, "color", "green")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var input = be.getItem(0);
            out.text(SELLING, Slots.stack(input));
            int each = input.isEmpty() ? 0 : Currency.calculateValueFromSingleStack(input, be);
            out.text(WORTH, input.isEmpty() ? "–" : each <= 0 ? "Not sellable" : Fmt.si((double) each * input.getCount()));
            out.text(CREDIT, Fmt.si(be.storedValue));
            out.text(PAYOUT, Slots.stack(be.getItem(1)));
            out.bool(AUTO, be.instaSell);
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            switch (widgetId) {
                case AUTO -> {
                    be.instaSell = value.getAsBoolean();
                    be.updateToClient();
                    CCCloud.LOGGER.info("[cccloud] {} switched auto-sell of the selling bin at {} {}", user, pos.toShortString(), be.instaSell ? "on" : "off");
                }
                case SELL -> {
                    if (be.getItem(0).isEmpty()) return "unavailable";
                    be.sell(true);
                    CCCloud.LOGGER.info("[cccloud] {} sold the contents of the selling bin at {}", user, pos.toShortString());
                }
                default -> {
                    return "not_interactive";
                }
            }
            changed();
            return null;
        }
    }
}
