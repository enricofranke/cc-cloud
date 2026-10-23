package ai.ezfox.cccloud.compat.productivebees;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import cy.jdkdigital.productivebees.common.block.Centrifuge;
import cy.jdkdigital.productivebees.common.block.entity.AdvancedBeehiveBlockEntity;
import cy.jdkdigital.productivebees.common.block.entity.AdvancedBeehiveBlockEntityAbstract;
import cy.jdkdigital.productivebees.common.block.entity.CentrifugeBlockEntity;
import cy.jdkdigital.productivebees.common.block.entity.PoweredCentrifugeBlockEntity;
import cy.jdkdigital.productivelib.common.block.entity.InventoryHandlerHelper;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Productive Bees: advanced hives (honey level, bees inside, output fullness) and centrifuges (running, output
 * fullness, energy for the powered/heated ones, honey tank). Alerts once when an output becomes full.
 */
public final class ProductiveBeesCards {
    public static final String HONEY = "honey";
    public static final String BEES = "bees";
    public static final String OUTPUT = "output";
    public static final String OUTPUT_FULL = "output_full";
    public static final String RUNNING = "running";
    public static final String ENERGY = "energy";
    public static final String FLUID = "fluid";
    /** Output slots of hives and centrifuges (productivelib's InventoryHandlerHelper.OUTPUT_SLOTS). */
    static final int FIRST_OUTPUT = 2;
    static final int LAST_OUTPUT = 10;

    private ProductiveBeesCards() {
    }

    public static void init() {
        DeviceCards.register("productivebees", DeviceCards.SPECIFIC, ProductiveBeesCards::detect);
        Integrations.test("productivebees_hive", () -> ProductiveBeesGameTests::hive);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        if (be instanceof AdvancedBeehiveBlockEntity hive) return new Hive(level, pos, side, hive);
        if (be instanceof CentrifugeBlockEntity centrifuge) return new CentrifugeCard(level, pos, side, centrifuge);
        return null;
    }

    /** Percentage of the output slots' capacity in use, and whether no output can take another item. */
    record Fullness(double percent, boolean full) {
        static Fullness of(InventoryHandlerHelper.BlockEntityItemStackHandler inventory) {
            long used = 0, capacity = 0;
            boolean full = true;
            for (int slot = FIRST_OUTPUT; slot <= LAST_OUTPUT && slot < inventory.size(); slot++) {
                var stack = inventory.getStackInSlot(slot);
                int max = stack.isEmpty() ? 64 : stack.getMaxStackSize();
                used += stack.getCount();
                capacity += max;
                if (stack.isEmpty() || stack.getCount() < max) full = false;
            }
            return new Fullness(Fmt.percent(used, capacity), full);
        }
    }

    abstract static class OutputCard<T extends BlockEntity> extends BlockCard<T> {
        private boolean wasFull;

        OutputCard(ServerLevel level, BlockPos pos, Direction side, T be) {
            super(level, pos, side, be);
        }

        void output(CardOutput out, InventoryHandlerHelper.BlockEntityItemStackHandler inventory) {
            var fullness = Fullness.of(inventory);
            out.number(OUTPUT, fullness.percent());
            out.bool(OUTPUT_FULL, fullness.full());
            if (fullness.full() && !wasFull) out.alert(title() + ": output is full", "warn");
            wasFull = fullness.full();
        }
    }

    static final class Hive extends OutputCard<AdvancedBeehiveBlockEntity> {
        Hive(ServerLevel level, BlockPos pos, Direction side, AdvancedBeehiveBlockEntity hive) {
            super(level, pos, side, hive);
        }

        @Override
        public String kind() {
            return "productivebees_hive";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .progress(HONEY, "Honey level", 0, 5, null, "icon", "drop")
                .label(BEES, "Bees inside")
                .percent(OUTPUT, "Output", "warn", 80, "crit", 95)
                .indicator(OUTPUT_FULL, "Output full", "onColor", "red", "offColor", "gray")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var state = be.getBlockState();
            out.number(HONEY, state.hasProperty(BeehiveBlock.HONEY_LEVEL) ? state.getValue(BeehiveBlock.HONEY_LEVEL) : 0);
            out.text(BEES, be.getOccupantCount() + " / " + ((AdvancedBeehiveBlockEntityAbstract) be).MAX_BEES);
            output(out, be.inventoryHandler);
        }
    }

    static final class CentrifugeCard extends OutputCard<CentrifugeBlockEntity> {
        CentrifugeCard(ServerLevel level, BlockPos pos, Direction side, CentrifugeBlockEntity centrifuge) {
            super(level, pos, side, centrifuge);
        }

        @Override
        public String kind() {
            return "productivebees_centrifuge";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets()
                .indicator(RUNNING, "Running", "onColor", "green", "offColor", "gray")
                .percent(OUTPUT, "Output", "warn", 80, "crit", 95)
                .indicator(OUTPUT_FULL, "Output full", "onColor", "red", "offColor", "gray")
                .label(FLUID, "Honey tank");
            if (be instanceof PoweredCentrifugeBlockEntity) w.percent(ENERGY, "Energy", "color", "green", "icon", "bolt");
            return w.build();
        }

        @Override
        public void refresh(CardOutput out) {
            var state = be.getBlockState();
            out.bool(RUNNING, state.hasProperty(Centrifuge.RUNNING) && state.getValue(Centrifuge.RUNNING));
            output(out, be.inventoryHandler);
            out.text(FLUID, be.fluidHandler.getAmountAsInt(0) + " / " + be.fluidHandler.getCapacityAsInt(0, net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY) + " mB");
            if (be instanceof PoweredCentrifugeBlockEntity powered) {
                out.number(ENERGY, Fmt.percent(powered.energyHandler.getAmountAsLong(), powered.energyHandler.getCapacityAsLong()));
            }
        }
    }
}
