package ai.ezfox.cccloud.compat.farmingforblockheads;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.card.Slots;
import ai.ezfox.cccloud.uplink.Widget;
import java.util.List;
import net.blay09.mods.farmingforblockheads.block.entity.ShippingBinBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Farming for Blockheads (wave 4): the Shipping Bin – the value of the goods put in against the shipment size, the
 * goods waiting and the emeralds paid out; info alert when a shipment leaves. The Market only opens a trade screen and
 * holds no state, so it gets no card.
 */
public final class ShippingBinCards {
    public static final String SHIPMENT = "shipment";
    public static final String VALUE = "value";
    public static final String GOODS = "goods";
    public static final String PAYOUT = "payout";
    /** Shipping bin container: 0–5 goods, 6–11 payout. */
    static final int GOODS_FROM = 0, PAYOUT_FROM = 6, SLOTS = 6;

    private ShippingBinCards() {
    }

    public static void init() {
        DeviceCards.register("farmingforblockheads", DeviceCards.SPECIFIC, ShippingBinCards::detect);
        Integrations.test("farmingforblockheads_shipping_bin", () -> ShippingBinGameTests::bin);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof ShippingBinBlockEntity bin ? new Bin(level, pos, side, bin) : null;
    }

    static final class Bin extends BlockCard<ShippingBinBlockEntity> {
        private int lastValue = -1;

        Bin(ServerLevel level, BlockPos pos, Direction side, ShippingBinBlockEntity bin) {
            super(level, pos, side, bin);
        }

        @Override
        public String kind() {
            return "farmingforblockheads_shipping_bin";
        }

        @Override
        public String title() {
            return "Shipping Bin";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .percent(SHIPMENT, "Next shipment", "color", "yellow", "icon", "box")
                .label(VALUE, "Value")
                .label(GOODS, "Goods")
                .label(PAYOUT, "Paid out")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            int value = be.getShipmentValue(), capacity = Math.max(1, be.getShipmentCapacity());
            out.number(SHIPMENT, Fmt.percent(value, capacity));
            out.text(VALUE, value + " / " + capacity);
            var c = be.getContainer();
            out.text(GOODS, Slots.container(c, GOODS_FROM, SLOTS));
            out.text(PAYOUT, Slots.container(c, PAYOUT_FROM, SLOTS));
            if (lastValue >= capacity && value < lastValue) out.alert("Shipping Bin: a shipment left", "info");
            lastValue = value;
        }
    }
}
