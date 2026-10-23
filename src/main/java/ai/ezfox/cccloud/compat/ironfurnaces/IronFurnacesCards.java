package ai.ezfox.cccloud.compat.ironfurnaces;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.bridge.ItemNames;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonElement;
import ironfurnaces.tileentity.furnaces.BlockIronFurnaceTileBase;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Iron Furnaces (docs/SPEC.md v0.8 item 5): every furnace tier. Status, smelting progress, fuel left, input/output,
 * stored FE (generator / energy augments), the furnace's own redstone mode as a select ({@code act.redstone}, confirm)
 * and an alert once when the output slot is full.
 */
public final class IronFurnacesCards {
    public static final String STATUS = "status";
    public static final String PROGRESS = "progress";
    public static final String FUEL = "fuel";
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";
    public static final String ENERGY = "energy";
    public static final String REDSTONE = "redstone";
    static final List<String> MODES = List.of("Ignore redstone", "Run without signal", "Run with signal");

    private IronFurnacesCards() {
    }

    public static void init() {
        DeviceCards.register("ironfurnaces", DeviceCards.SPECIFIC, IronFurnacesCards::detect);
        Integrations.test("ironfurnaces_furnace", () -> IronFurnacesGameTests::furnace);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof BlockIronFurnaceTileBase furnace ? new Furnace(level, pos, side, furnace) : null;
    }

    static String stack(ItemStack stack) {
        return stack.isEmpty() ? "Empty" : stack.getCount() + "× " + ItemNames.displayName(stack.getItem());
    }

    static final class Furnace extends BlockCard<BlockIronFurnaceTileBase> {
        private boolean wasFull;

        Furnace(ServerLevel level, BlockPos pos, Direction side, BlockIronFurnaceTileBase furnace) {
            super(level, pos, side, furnace);
        }

        @Override
        public String kind() {
            return "ironfurnaces_furnace";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .progress(PROGRESS, "Smelting", 0, 100, "%")
                .label(FUEL, "Fuel left")
                .label(INPUT, "Input")
                .label(OUTPUT, "Output")
                .percent(ENERGY, "Energy", "color", "green", "icon", "bolt")
                .select(REDSTONE, "Redstone mode", MODES, "confirm", true)
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var input = be.inventory.get(BlockIronFurnaceTileBase.INPUT);
            var output = be.inventory.get(BlockIronFurnaceTileBase.OUTPUT);
            boolean burning = be.furnaceBurnTime > 0;
            boolean cooking = be.cookTime > 0 && be.totalCookTime > 0;
            boolean full = !output.isEmpty() && output.getCount() >= output.getMaxStackSize();
            String status = full ? "Output full" : cooking ? "Smelting " + ItemNames.displayName(input.getItem()) : input.isEmpty() ? "Idle" : burning ? "Heating up" : "No fuel";
            out.text(STATUS, status);
            out.number(PROGRESS, cooking ? Math.round(be.cookTime * 100.0 / be.totalCookTime) : 0);
            out.text(FUEL, burning ? Fmt.duration(be.furnaceBurnTime / 20) : "–");
            out.text(INPUT, stack(input));
            out.text(OUTPUT, stack(output));
            out.number(ENERGY, Fmt.percent(be.getEnergy(), be.getCapacity()));
            int mode = be.furnaceSettings.getRedstoneSetting();
            out.text(REDSTONE, MODES.get(Math.max(0, Math.min(MODES.size() - 1, mode))));
            if (full && !wasFull) out.alert(title() + ": output full", "warn");
            wasFull = full;
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            if (!REDSTONE.equals(widgetId)) return "not_interactive";
            int mode = MODES.indexOf(value.getAsString());
            if (mode < 0) return "invalid_value";
            be.furnaceSettings.setRedstoneSetting(mode);
            changed();
            CCCloud.LOGGER.info("[cccloud] {} set the redstone mode of the furnace at {} to {}", user, pos.toShortString(), MODES.get(mode));
            return null;
        }
    }
}
