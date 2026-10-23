package ai.ezfox.cccloud.compat.agritechevolved;

import ai.ezfox.cccloud.bridge.ItemNames;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import com.misterd.agritechevolved.blockentity.custom.AdvancedPlanterBlockEntity;
import com.misterd.agritechevolved.blockentity.custom.PlanterBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Agritech: Evolved planters (docs/SPEC.md v0.8 item 5): plant, soil, growth, energy (advanced planter) and an alert once
 * when the output is full. Slot 0 is the plant, slot 1 the soil (both planter kinds).
 */
public final class AgritechCards {
    public static final String STATUS = "status";
    public static final String PLANT = "plant";
    public static final String SOIL = "soil";
    public static final String GROWTH = "growth";
    public static final String ENERGY = "energy";
    public static final String OUTPUT_FULL = "output_full";

    private AgritechCards() {
    }

    public static void init() {
        DeviceCards.register("agritechevolved", DeviceCards.SPECIFIC, AgritechCards::detect);
        Integrations.test("agritechevolved_planter", () -> AgritechGameTests::planter);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        return be instanceof PlanterBlockEntity || be instanceof AdvancedPlanterBlockEntity ? new Planter(level, pos, side, be) : null;
    }

    static String item(ItemStack stack) {
        return stack.isEmpty() ? "None" : ItemNames.displayName(stack.getItem());
    }

    static final class Planter extends BlockCard<BlockEntity> {
        private boolean wasFull;

        Planter(ServerLevel level, BlockPos pos, Direction side, BlockEntity be) {
            super(level, pos, side, be);
        }

        private boolean advanced() {
            return be instanceof AdvancedPlanterBlockEntity;
        }

        @Override
        public String kind() {
            return advanced() ? "agritechevolved_advanced_planter" : "agritechevolved_planter";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(PLANT, "Plant")
                .label(SOIL, "Soil")
                .progress(GROWTH, "Growth", 0, 100, "%");
            if (advanced()) w.percent(ENERGY, "Energy", "color", "green", "icon", "bolt");
            return w.indicator(OUTPUT_FULL, "Output full", "onColor", "red", "offColor", "gray").build();
        }

        @Override
        public void refresh(CardOutput out) {
            ItemStack plant, soil;
            float growth;
            boolean ready, space;
            if (be instanceof AdvancedPlanterBlockEntity a) {
                plant = a.getStack(0);
                soil = a.getStack(1);
                growth = a.getGrowthProgress();
                ready = a.isReadyToHarvest();
                space = a.hasOutputSpace();
                out.number(ENERGY, Fmt.percent(a.getEnergyStored(), a.getMaxEnergyStored()));
            } else {
                var p = (PlanterBlockEntity) be;
                plant = p.getStack(0);
                soil = p.getStack(1);
                growth = p.getGrowthProgress();
                ready = p.isReadyToHarvest();
                space = p.hasOutputSpace();
            }
            boolean full = !plant.isEmpty() && !space;
            String status = plant.isEmpty() ? "No plant" : soil.isEmpty() ? "No soil" : full ? "Output full" : ready ? "Harvesting" : "Growing";
            out.text(STATUS, status);
            out.text(PLANT, item(plant));
            out.text(SOIL, item(soil));
            double pct = growth <= 1.0f ? growth * 100 : growth;
            out.number(GROWTH, Math.round(Math.max(0, Math.min(100, pct))));
            out.bool(OUTPUT_FULL, full);
            if (full && !wasFull) out.alert(title() + ": output full", "warn");
            wasFull = full;
        }
    }
}
