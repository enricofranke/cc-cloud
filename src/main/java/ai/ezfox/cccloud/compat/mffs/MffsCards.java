package ai.ezfox.cccloud.compat.mffs;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonElement;
import dev.su5ed.mffs.blockentity.ProjectorBlockEntity;
import dev.su5ed.mffs.setup.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * MFFS force field projector: field on/off from the dashboard (canControl, and the mainframe PIN unless
 * {@code mffsRequirePin=false}; always with a confirmation), Fortron level, and what the projector is doing.
 */
public final class MffsCards {
    public static final String FIELD = "field";
    public static final String STATUS = "status";
    public static final String FORTRON = "fortron";
    public static final String FORTRON_TEXT = "fortron_text";
    public static final String BLOCKS = "blocks";
    public static final String REDSTONE = "redstone_held";
    /** Field blocks are counted on the calculated shape, at most this many positions per refresh. */
    static final int MAX_COUNT = 8192;

    private MffsCards() {
    }

    public static void init() {
        DeviceCards.register("mffs", DeviceCards.SPECIFIC, MffsCards::detect);
        Integrations.test("mffs_projector", () -> MffsGameTests::projector);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof ProjectorBlockEntity projector ? new Projector(level, pos, side, projector) : null;
    }

    static final class Projector extends BlockCard<ProjectorBlockEntity> {
        private boolean wasUp;

        Projector(ServerLevel level, BlockPos pos, Direction side, ProjectorBlockEntity projector) {
            super(level, pos, side, projector);
        }

        @Override
        public String kind() {
            return "mffs_projector";
        }

        @Override
        public String title() {
            return "Force Field Projector";
        }

        @Override
        public List<Widget> widgets() {
            boolean pin = IntegrationConfig.get(IntegrationConfig.MFFS_REQUIRE_PIN);
            return new CardWidgets()
                .toggle(FIELD, "Force field", "confirm", true, "pin", pin ? Boolean.TRUE : null, "icon", "shield", "color", "cyan")
                .label(STATUS, "Status", "span", 2)
                .percent(FORTRON, "Fortron", "color", "cyan")
                .label(FORTRON_TEXT, "Fortron stored")
                .label(BLOCKS, "Field blocks")
                .indicator(REDSTONE, "Held on by redstone", "onColor", "yellow", "offColor", "gray")
                .build();
        }

        private int fieldBlocks() {
            int count = 0, seen = 0;
            var field = ModBlocks.FORCE_FIELD.get();
            for (var target : be.getCalculatedFieldPositions()) {
                if (++seen > MAX_COUNT) break;
                var p = target.pos();
                if (level.isLoaded(p) && level.getBlockState(p).is(field)) count++;
            }
            return count;
        }

        @Override
        public void refresh(CardOutput out) {
            boolean active = be.isActive();
            boolean redstone = level.hasNeighborSignal(pos);
            int stored = be.fortronStorage.getStoredFortron();
            int capacity = be.fortronStorage.getFortronCapacity();
            int blocks = active ? fieldBlocks() : 0;
            String status;
            if (!active) status = "Off";
            else if (be.getMode().isEmpty()) status = "No projector mode installed";
            else if (stored < be.getFortronCost()) status = "Not enough Fortron";
            else if (blocks == 0) status = "Starting up";
            else status = "Projecting";
            out.bool(FIELD, active);
            out.text(STATUS, status);
            out.number(FORTRON, Fmt.percent(stored, capacity));
            out.text(FORTRON_TEXT, Fmt.si(stored) + " / " + Fmt.si(capacity) + " mB");
            out.text(BLOCKS, blocks + (blocks >= MAX_COUNT ? "+" : ""));
            out.bool(REDSTONE, redstone);
            boolean up = blocks > 0;
            if (wasUp && !up) out.alert("Force field went down (" + status + ")", "crit");
            wasUp = up;
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            if (!FIELD.equals(widgetId)) return "not_interactive";
            boolean on = value.getAsBoolean();
            be.setActive(on);
            changed();
            CCCloud.LOGGER.info("[cccloud] {} switched the force field projector at {} {}", user, pos.toShortString(), on ? "on" : "off");
            // A redstone signal keeps the projector on regardless; the card's "Held on by redstone" indicator shows it.
            return null;
        }
    }
}
