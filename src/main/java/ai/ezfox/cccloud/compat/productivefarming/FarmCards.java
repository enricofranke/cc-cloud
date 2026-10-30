package ai.ezfox.cccloud.compat.productivefarming;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import cy.jdkdigital.productivefarming.common.block.entity.FarmControllerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Productive Farming farm controller (docs/SPEC.md v0.8 item 5): multiblock formed, water, output slots used and an
 * alert once when the output is full.
 */
public final class FarmCards {
    public static final String STATUS = "status";
    public static final String WATER = "water";
    public static final String OUTPUT = "output";
    public static final String OUTPUT_FULL = "output_full";

    private FarmCards() {
    }

    public static void init() {
        DeviceCards.register("productivefarming", DeviceCards.SPECIFIC, FarmCards::detect);
        Integrations.test("productivefarming_farm", () -> FarmGameTests::farm);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        if (level.getBlockEntity(pos) instanceof FarmControllerBlockEntity c) return new Farm(level, pos, side, c);
        // Auxiliary parts (hatches etc.) know their controller: an uplink on any of them shows the farm.
        if (level.getBlockEntity(pos) instanceof cy.jdkdigital.productivefarming.common.block.entity.AuxiliaryFarmBlockEntity part) {
            BlockPos controller = part.getControllerPos();
            if (controller != null && !controller.equals(pos) && level.isLoaded(controller)
                && level.getBlockEntity(controller) instanceof FarmControllerBlockEntity c) {
                return new Farm(level, controller, side, c);
            }
        }
        return null;
    }

    static final class Farm extends BlockCard<FarmControllerBlockEntity> {
        private boolean wasFull;

        Farm(ServerLevel level, BlockPos pos, Direction side, FarmControllerBlockEntity c) {
            super(level, pos, side, c);
        }

        @Override
        public String kind() {
            return "productivefarming_farm";
        }

        @Override
        public String title() {
            return "Farm";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(WATER, "Water")
                .percent(OUTPUT, "Output slots used", "warn", 80, "crit", 100)
                .indicator(OUTPUT_FULL, "Output full", "onColor", "red", "offColor", "gray")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            boolean formed = be.getMultiblockData() != null;
            var fluids = be.getFluidHandler();
            long water = 0, capacity = 0;
            if (fluids != null) {
                for (int i = 0; i < fluids.size(); i++) {
                    water += Math.max(0, fluids.getAmountAsLong(i));
                    capacity += Math.max(0, fluids.getCapacityAsLong(i, fluids.getResource(i)));
                }
            }
            var items = be.getItemHandler();
            int used = 0, slots = items == null ? 0 : items.size();
            boolean full = slots > 0;
            for (int i = 0; i < slots; i++) {
                var r = items.getResource(i);
                long amount = r.isEmpty() ? 0 : items.getAmountAsLong(i);
                if (amount > 0) used++;
                if (r.isEmpty() || amount < items.getCapacityAsLong(i, r)) full = false;
            }
            out.text(STATUS, !formed ? "Not formed" : full ? "Output full" : water <= 0 ? "No water" : "Farming");
            out.text(WATER, Fmt.si(water) + " / " + Fmt.si(capacity) + " mB");
            out.number(OUTPUT, Fmt.percent(used, slots));
            out.bool(OUTPUT_FULL, full);
            if (full && !wasFull) out.alert("Farm: output full", "warn");
            wasFull = full;
        }
    }
}
