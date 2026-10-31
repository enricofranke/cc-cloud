package ai.ezfox.cccloud.compat.strainers;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.card.Slots;
import ai.ezfox.cccloud.uplink.Widget;
import com.benbenlaw.strainers.block.entity.StrainerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * BBL Strainers: the Strainer's status (straining, what is missing, output full), progress, input, mesh, fluid and
 * output slots; a warn alert once when the output is full. Progress is only in the block entity's saved data, the card
 * reads it from there. No control: the strainer has no switch.
 */
public final class StrainerCards {
    public static final String STATUS = "status";
    public static final String PROGRESS = "progress";
    public static final String INPUT = "input";
    public static final String MESH = "mesh";
    public static final String FLUID = "fluid";
    public static final String OUTPUT = "output";
    public static final String OUTPUT_FULL = "output_full";
    /** Saved progress or fluid need that says a cycle runs; both are 0 while the strainer waits. */
    static final String KEY_PROGRESS = "progress";
    static final String KEY_MAX = "maxProgress";

    private StrainerCards() {
    }

    public static void init() {
        DeviceCards.register("strainers", DeviceCards.SPECIFIC, StrainerCards::detect);
        Integrations.test("strainers_strainer", () -> StrainerGameTests::strainer);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof StrainerBlockEntity s ? new Strainer(level, pos, side, s) : null;
    }

    static final class Strainer extends BlockCard<StrainerBlockEntity> {
        private boolean wasFull;

        Strainer(ServerLevel level, BlockPos pos, Direction side, StrainerBlockEntity s) {
            super(level, pos, side, s);
        }

        @Override
        public String kind() {
            return "strainers_strainer";
        }

        @Override
        public String title() {
            return "Strainer";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status")
                .progress(PROGRESS, "Progress", 0, 100, "%")
                .label(INPUT, "Input", "span", 2)
                .label(MESH, "Mesh", "span", 2)
                .label(FLUID, "Fluid", "span", 2)
                .label(OUTPUT, "Output slots", "span", 2)
                .indicator(OUTPUT_FULL, "Output full", "onColor", "red", "offColor", "gray")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var items = be.getItemHandler();
            var saved = be.saveCustomOnly(level.registryAccess());
            int progress = saved.getIntOr(KEY_PROGRESS, 0);
            int max = saved.getIntOr(KEY_MAX, 0);
            int used = 0;
            int slots = 0;
            for (int i = StrainerBlockEntity.FIRST_OUTPUT_SLOT; i <= StrainerBlockEntity.LAST_OUTPUT_SLOT && i < items.size(); i++) {
                slots++;
                if (!Slots.empty(items, i)) used++;
            }
            boolean full = slots > 0 && used == slots;
            var tank = be.getFluidHandler();
            var fluid = tank.getResource(0);
            long amount = fluid.isEmpty() ? 0 : tank.getAmountAsLong(0);
            boolean noInput = Slots.empty(items, StrainerBlockEntity.INPUT_SLOT);
            boolean noMesh = Slots.empty(items, StrainerBlockEntity.MESH_SLOT);
            out.text(STATUS, noInput ? "No input" : noMesh ? "No mesh" : full ? "Output full" : progress > 0 ? "Straining" : amount == 0 ? "No fluid" : "Waiting");
            out.number(PROGRESS, Math.round(Fmt.percent(progress, max)));
            out.text(INPUT, Slots.item(items, StrainerBlockEntity.INPUT_SLOT));
            out.text(MESH, Slots.item(items, StrainerBlockEntity.MESH_SLOT));
            out.text(FLUID, Slots.fluid(fluid.isEmpty() ? null : fluid.getFluid(), amount, tank.getCapacityAsLong(0, fluid)));
            out.text(OUTPUT, used + " / " + slots);
            out.bool(OUTPUT_FULL, full);
            if (full && !wasFull) out.alert("Strainer: output full", "warn");
            wasFull = full;
        }
    }
}
