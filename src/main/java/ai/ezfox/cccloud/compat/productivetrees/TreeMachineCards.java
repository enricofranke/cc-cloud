package ai.ezfox.cccloud.compat.productivetrees;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Slots;
import ai.ezfox.cccloud.uplink.Widget;
import cy.jdkdigital.productivetrees.common.block.entity.PollenSifterBlockEntity;
import cy.jdkdigital.productivetrees.common.block.entity.SawmillBlockEntity;
import cy.jdkdigital.productivetrees.common.block.entity.StripperBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Productive Trees (wave 4): its processing machines – Sawmill (logs into planks, sawdust and bark), Pollen Sifter
 * and Stripper (strips logs with an axe). Status, progress (sawmill and sifter), input and outputs, the stripper's
 * axe; warn alert once when the output slot is full. Tree farming itself happens in the world (no block to read), and
 * the machines have no switch.
 */
public final class TreeMachineCards {
    public static final String STATUS = "status";
    public static final String PROGRESS = "progress";
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";
    public static final String EXTRA = "extra";

    private TreeMachineCards() {
    }

    public static void init() {
        DeviceCards.register("productivetrees", DeviceCards.SPECIFIC, TreeMachineCards::detect);
        Integrations.test("productivetrees_sawmill", () -> TreeMachineGameTests::sawmill);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        if (be instanceof SawmillBlockEntity || be instanceof PollenSifterBlockEntity || be instanceof StripperBlockEntity) return new Machine(level, pos, side, be);
        return null;
    }

    static final class Machine extends BlockCard<BlockEntity> {
        private boolean wasFull;

        Machine(ServerLevel level, BlockPos pos, Direction side, BlockEntity be) {
            super(level, pos, side, be);
        }

        @Override
        public String kind() {
            return "productivetrees_machine";
        }

        @Override
        public String title() {
            return be instanceof SawmillBlockEntity ? "Sawmill" : be instanceof PollenSifterBlockEntity ? "Pollen Sifter" : "Stripper";
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets().label(STATUS, "Status");
            if (!(be instanceof StripperBlockEntity)) w.progress(PROGRESS, "Progress", 0, 100, "%");
            w.label(INPUT, "Input", "span", 2).label(OUTPUT, "Output", "span", 2);
            if (be instanceof SawmillBlockEntity) w.label(EXTRA, "By-products");
            else if (be instanceof StripperBlockEntity) w.label(EXTRA, "Axe");
            return w.build();
        }

        @Override
        public void refresh(CardOutput out) {
            ResourceHandler<ItemResource> inv;
            int in, outSlot, progress = 0, time = 0;
            if (be instanceof SawmillBlockEntity s) {
                inv = s.inventoryHandler;
                in = SawmillBlockEntity.SLOT_IN;
                outSlot = SawmillBlockEntity.SLOT_OUT;
                progress = s.progress;
                time = s.recipeTime;
                String second = Slots.item(inv, SawmillBlockEntity.SLOT_SECONDARY), third = Slots.item(inv, SawmillBlockEntity.SLOT_TERTIARY);
                out.text(EXTRA, second.equals("Empty") && third.equals("Empty") ? "Empty" : second.equals("Empty") ? third : third.equals("Empty") ? second : second + ", " + third);
            } else if (be instanceof PollenSifterBlockEntity p) {
                inv = p.inventoryHandler;
                in = PollenSifterBlockEntity.SLOT_IN;
                outSlot = PollenSifterBlockEntity.SLOT_OUT;
                progress = p.progress;
                time = p.recipeTime;
            } else {
                var st = (StripperBlockEntity) be;
                inv = st.inventoryHandler;
                in = StripperBlockEntity.SLOT_IN;
                outSlot = StripperBlockEntity.SLOT_OUT;
                out.text(EXTRA, Slots.stack(st.getAxe()));
            }
            boolean input = !Slots.empty(inv, in);
            boolean full = !Slots.empty(inv, outSlot) && inv.getAmountAsLong(outSlot) >= inv.getCapacityAsLong(outSlot, inv.getResource(outSlot));
            boolean working = time > 0 && progress > 0;
            if (!(be instanceof StripperBlockEntity)) out.number(PROGRESS, time > 0 ? Math.min(100, Math.round(progress * 100.0 / time)) : 0);
            out.text(STATUS, full ? "Output full" : working ? "Working" : input ? (be instanceof StripperBlockEntity && Slots.stack(((StripperBlockEntity) be).getAxe()).equals("Empty") ? "Needs an axe" : "Waiting") : "Idle");
            out.text(INPUT, Slots.item(inv, in));
            out.text(OUTPUT, Slots.item(inv, outSlot));
            if (full && !wasFull) out.alert(title() + ": output full", "warn");
            wasFull = full;
        }
    }
}
