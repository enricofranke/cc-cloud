package ai.ezfox.cccloud.compat.trashcans;

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
import com.supermartijn642.trashcans.TrashCanBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Trash Cans: which filter decides what a can takes (allow or deny list, set from the dashboard, {@code act.buttons}),
 * the energy limit, and the most useful thing: an item can keeps its last deleted stacks ({@link
 * TrashCanBlockEntity#MAX_DELETED_ITEMS}), so the card shows what went in. The card never restores them.
 */
public final class TrashCanCards {
    public static final String HANDLES = "handles";
    public static final String DELETED = "deleted_";
    public static final String ITEM_ALLOW = "item_allow";
    public static final String FLUID_ALLOW = "fluid_allow";
    public static final String ENERGY_LIMIT = "energy_limit";

    private TrashCanCards() {
    }

    public static void init() {
        DeviceCards.register("trashcans", DeviceCards.SPECIFIC, TrashCanCards::detect);
        Integrations.test("trashcans_item", () -> TrashCanGameTests::item);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof TrashCanBlockEntity can ? new Can(level, pos, side, can) : null;
    }

    static final class Can extends BlockCard<TrashCanBlockEntity> {
        Can(ServerLevel level, BlockPos pos, Direction side, TrashCanBlockEntity can) {
            super(level, pos, side, can);
        }

        @Override
        public String kind() {
            return "trashcans_can";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets().label(HANDLES, "Takes");
            if (be.handlesItems()) {
                w.toggle(ITEM_ALLOW, "Items: only what the filter lists");
                for (int i = 0; i < TrashCanBlockEntity.MAX_DELETED_ITEMS; i++) w.label(DELETED + i, i == 0 ? "Deleted last" : "Before that", "span", 2);
            }
            if (be.handlesFluids()) w.toggle(FLUID_ALLOW, "Fluids: only what the filter lists");
            if (be.handlesEnergy()) w.label(ENERGY_LIMIT, "Energy limit");
            return w.build();
        }

        @Override
        public void refresh(CardOutput out) {
            var kinds = new java.util.ArrayList<String>();
            if (be.handlesItems()) kinds.add("Items");
            if (be.handlesFluids()) kinds.add("Fluids");
            if (be.handlesEnergy()) kinds.add("Energy");
            out.text(HANDLES, String.join(", ", kinds));
            if (be.handlesItems()) {
                out.bool(ITEM_ALLOW, be.isItemFilterWhitelist());
                var deleted = be.getDeletedItems();
                for (int i = 0; i < TrashCanBlockEntity.MAX_DELETED_ITEMS; i++) {
                    out.text(DELETED + i, i < deleted.size() ? Slots.stack(deleted.get(i)) : "–");
                }
            }
            if (be.handlesFluids()) out.bool(FLUID_ALLOW, be.isFluidFilterWhitelist());
            if (be.handlesEnergy()) out.text(ENERGY_LIMIT, be.isEnergyLimited() ? Fmt.si(be.getEnergyLimit()) + " FE/t" : "Off");
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            boolean allow = value.getAsBoolean();
            if (ITEM_ALLOW.equals(widgetId) && be.handlesItems()) {
                if (be.isItemFilterWhitelist() != allow) be.toggleItemFilterWhitelist();
            } else if (FLUID_ALLOW.equals(widgetId) && be.handlesFluids()) {
                if (be.isFluidFilterWhitelist() != allow) be.toggleFluidFilterWhitelist();
            } else return "not_interactive";
            changed();
            CCCloud.LOGGER.info("[cccloud] {} set {} of the trash can at {} to {}", user, widgetId, pos.toShortString(), allow);
            return null;
        }
    }
}
