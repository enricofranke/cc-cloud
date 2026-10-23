package ai.ezfox.cccloud.compat.pylons;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonElement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.permutated.pylons.machines.base.AbstractPylonTile;
import org.jspecify.annotations.Nullable;

/**
 * Pylons (wave 3): Expulsion, Harvester, Infusion, Interdiction and Protection pylons – owner, selected range and the
 * pylon's own on/off switch as a toggle ({@code act.buttons}, confirm).
 */
public final class PylonCards {
    public static final String WORKING = "working";
    public static final String OWNER = "owner";
    public static final String RANGE = "range";

    private PylonCards() {
    }

    public static void init() {
        DeviceCards.register("pylons", DeviceCards.SPECIFIC, PylonCards::detect);
        Integrations.test("pylons_pylon", () -> PylonGameTests::pylon);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof AbstractPylonTile p ? new Pylon(level, pos, side, p) : null;
    }

    static final class Pylon extends BlockCard<AbstractPylonTile> {
        Pylon(ServerLevel level, BlockPos pos, Direction side, AbstractPylonTile p) {
            super(level, pos, side, p);
        }

        @Override
        public String kind() {
            return "pylons_pylon";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .toggle(WORKING, "Pylon on", "confirm", true, "icon", "shield")
                .label(OWNER, "Owner")
                .label(RANGE, "Range")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            out.bool(WORKING, be.shouldWork());
            String owner = be.getOwnerName();
            out.text(OWNER, owner == null || owner.isBlank() ? "–" : owner);
            int r = be.getSelectedRange();
            out.text(RANGE, r > 0 ? (r * 2 + 1) + " × " + (r * 2 + 1) + " chunks" : "This chunk");
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            if (!WORKING.equals(widgetId)) return "not_interactive";
            if (value.getAsBoolean() != be.shouldWork()) be.handleWorkPacket();
            changed();
            CCCloud.LOGGER.info("[cccloud] {} switched the pylon at {} {}", user, pos.toShortString(), be.shouldWork() ? "on" : "off");
            return null;
        }
    }
}
