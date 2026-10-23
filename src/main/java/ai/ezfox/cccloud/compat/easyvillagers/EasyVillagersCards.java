package ai.ezfox.cccloud.compat.easyvillagers;

import ai.ezfox.cccloud.bridge.ItemNames;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import de.maxhenkel.easyvillagers.blocks.tileentity.BreederTileentity;
import de.maxhenkel.easyvillagers.blocks.tileentity.FarmerTileentity;
import de.maxhenkel.easyvillagers.blocks.tileentity.IronFarmTileentity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Easy Villagers (docs/SPEC.md v0.8 item 4, "only if trivial"): Breeder, Iron Farm and Farmer – villagers inside,
 * what the block is doing (iron farm cycle, crop), output fill and an alert once when the output is full.
 */
public final class EasyVillagersCards {
    public static final String STATUS = "status";
    public static final String VILLAGERS = "villagers";
    public static final String CYCLE = "cycle";
    public static final String OUTPUT = "output";
    public static final String OUTPUT_FULL = "output_full";

    private EasyVillagersCards() {
    }

    public static void init() {
        DeviceCards.register("easyvillagers", DeviceCards.SPECIFIC, EasyVillagersCards::detect);
        Integrations.test("easyvillagers_iron_farm", () -> EasyVillagersGameTests::ironFarm);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        if (be instanceof BreederTileentity || be instanceof IronFarmTileentity || be instanceof FarmerTileentity) return new Machine(level, pos, side, be);
        return null;
    }

    /** Used slots of a container in percent, and whether no slot can take more. */
    static double fill(Container container) {
        int size = container.getContainerSize();
        if (size == 0) return 0;
        int used = 0;
        for (int i = 0; i < size; i++) if (!container.getItem(i).isEmpty()) used++;
        return Fmt.percent(used, size);
    }

    static boolean full(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            var stack = container.getItem(i);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) return false;
        }
        return container.getContainerSize() > 0;
    }

    static final class Machine extends BlockCard<BlockEntity> {
        private boolean wasFull;

        Machine(ServerLevel level, BlockPos pos, Direction side, BlockEntity be) {
            super(level, pos, side, be);
        }

        @Override
        public String kind() {
            return be instanceof BreederTileentity ? "easyvillagers_breeder" : be instanceof IronFarmTileentity ? "easyvillagers_iron_farm" : "easyvillagers_farmer";
        }

        @Override
        public String title() {
            return be instanceof BreederTileentity ? "Villager Breeder" : be instanceof IronFarmTileentity ? "Iron Farm" : "Villager Farmer";
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(VILLAGERS, "Villagers");
            if (be instanceof IronFarmTileentity) w.progress(CYCLE, "Golem cycle", 0, 100, "%");
            return w.percent(OUTPUT, "Output slots used", "warn", 80, "crit", 100)
                .indicator(OUTPUT_FULL, "Output full", "onColor", "red", "offColor", "gray")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            Container output;
            String status;
            int villagers;
            if (be instanceof BreederTileentity breeder) {
                output = breeder.getOutputInventory();
                villagers = (breeder.hasVillager1() ? 1 : 0) + (breeder.hasVillager2() ? 1 : 0);
                status = villagers < 2 ? "Needs two villagers" : breeder.canBreed() ? "Breeding" : "Needs food";
            } else if (be instanceof IronFarmTileentity farm) {
                output = farm.getOutputInventory();
                villagers = farm.hasVillager() ? 1 : 0;
                long cycle = (long) IronFarmTileentity.getGolemSpawnTime() + IronFarmTileentity.getGolemKillTime();
                long timer = farm.getTimer();
                out.number(CYCLE, villagers > 0 && cycle > 0 ? Math.round(Math.min(100, (timer % cycle) * 100.0 / cycle)) : 0);
                status = villagers == 0 ? "No villager" : timer < IronFarmTileentity.getGolemSpawnTime() ? "Waiting for a golem" : "Golem spawned";
            } else {
                var farmer = (FarmerTileentity) be;
                output = farmer.getOutputInventory();
                villagers = farmer.hasVillager() ? 1 : 0;
                var crop = farmer.getCrop();
                status = villagers == 0 ? "No villager" : crop == null ? "No seed" : "Farming " + ItemNames.displayName(crop.getBlock().asItem());
            }
            boolean full = full(output);
            out.text(STATUS, full ? status + " (output full)" : status);
            out.text(VILLAGERS, String.valueOf(villagers));
            out.number(OUTPUT, fill(output));
            out.bool(OUTPUT_FULL, full);
            if (full && !wasFull) out.alert(title() + ": output full", "warn");
            wasFull = full;
        }
    }
}
