package ai.ezfox.cccloud.compat.pipez;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonElement;
import de.maxhenkel.pipez.Upgrade;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.UpgradeTileEntity.RedstoneMode;
import de.maxhenkel.pipez.blocks.tileentity.types.EnergyPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.FluidPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.ItemPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.PipeType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Pipez: a pipe has no buffer, what matters is where it extracts, where that goes and whether it runs. The card shows
 * status, extracting sides, reachable targets, upgrade, rate and filters. An upgrade that may change the redstone mode
 * gets the mode as a select ({@code act.redstone}, confirm), set on every extracting side of the pipe; "Always off"
 * is a pipe's off switch. Without such an upgrade there is no select: a dashboard switch would skip the upgrade.
 * No coordinates: the targets are only counted. A pipe only has a block entity once it is configured, so an uplink holds
 * on a pipe with an extracting or disconnected side and drops off when the last one is cleared.
 */
public final class PipeCards {
    public static final String STATUS = "status";
    public static final String EXTRACTING = "extracting";
    public static final String TARGETS = "targets";
    public static final String UPGRADE = "upgrade";
    public static final String RATE = "rate";
    public static final String FILTERS = "filters";
    public static final String REDSTONE = "redstone";
    static final List<String> MODES = List.of("Ignored", "Off when powered", "On when powered", "Always off");
    static final List<RedstoneMode> REDSTONE_MODES = List.of(RedstoneMode.IGNORED, RedstoneMode.OFF_WHEN_POWERED, RedstoneMode.ON_WHEN_POWERED, RedstoneMode.ALWAYS_OFF);

    private PipeCards() {
    }

    public static void init() {
        DeviceCards.register("pipez", DeviceCards.SPECIFIC, PipeCards::detect);
        Integrations.test("pipez_pipe", () -> PipeGameTests::pipe);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof PipeLogicTileEntity pipe ? new Pipe(level, pos, side, pipe) : null;
    }

    static List<Direction> extracting(PipeLogicTileEntity pipe) {
        var sides = new ArrayList<Direction>();
        for (var dir : Direction.values()) if (pipe.isExtracting(dir)) sides.add(dir);
        return sides;
    }

    /** Extracting sides whose upgrade may change the redstone mode. */
    static List<Direction> controllable(PipeLogicTileEntity pipe) {
        var sides = extracting(pipe);
        sides.removeIf(dir -> !Upgrade.canChangeRedstoneMode(pipe.getUpgrade(dir)));
        return sides;
    }

    /** One side's rate per pipe type: "32 items / 5 ticks, 1.00 k mB/t". */
    static String rate(PipeLogicTileEntity pipe, Direction dir) {
        var parts = new ArrayList<String>();
        for (PipeType<?, ?> type : pipe.getPipeTypes()) {
            int rate = type.getRate(pipe, dir);
            if (type instanceof ItemPipeType items) parts.add(rate + " items / " + items.getSpeed(pipe, dir) + " ticks");
            else if (type instanceof FluidPipeType) parts.add(Fmt.si(rate) + " mB/t");
            else if (type instanceof EnergyPipeType) parts.add(Fmt.si(rate) + " FE/t");
            else parts.add(Fmt.si(rate) + "/t");
        }
        return String.join(", ", parts);
    }

    static final class Pipe extends BlockCard<PipeLogicTileEntity> {
        Pipe(ServerLevel level, BlockPos pos, Direction side, PipeLogicTileEntity pipe) {
            super(level, pos, side, pipe);
        }

        @Override
        public String kind() {
            return "pipez_pipe";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public int revision() {
            return controllable(be).isEmpty() ? 0 : 1;
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets()
                .label(STATUS, "Status")
                .label(EXTRACTING, "Extracting from", "span", 2)
                .label(TARGETS, "Targets", "span", 2)
                .label(UPGRADE, "Upgrade", "span", 2)
                .label(FILTERS, "Filters", "span", 2)
                .label(RATE, "Rate");
            if (!controllable(be).isEmpty()) w.select(REDSTONE, "Redstone mode", MODES, "confirm", true);
            return w.build();
        }

        private String status(List<Direction> sides) {
            if (sides.isEmpty()) return "Not extracting";
            if (be.getConnections().isEmpty()) return "No targets";
            boolean off = true;
            for (var dir : sides) {
                for (var type : be.getPipeTypes()) {
                    if (be.shouldWork(dir, type)) return "Running";
                    off &= be.getRedstoneMode(dir, type) == RedstoneMode.ALWAYS_OFF;
                }
            }
            return off ? "Switched off" : "Stopped by redstone";
        }

        @Override
        public void refresh(CardOutput out) {
            var sides = extracting(be);
            out.text(STATUS, status(sides));
            out.text(TARGETS, String.valueOf(be.getConnections().size()));
            var names = new ArrayList<String>();
            var upgrades = new ArrayList<String>();
            int filters = 0;
            for (var dir : sides) {
                names.add(Fmt.title(dir.getSerializedName()));
                var upgrade = be.getUpgrade(dir);
                String name = upgrade == null ? "None" : Fmt.title(upgrade.getName());
                if (!upgrades.contains(name)) upgrades.add(name);
                for (var type : be.getPipeTypes()) filters += be.getFilters(dir, type).size();
            }
            out.text(EXTRACTING, names.isEmpty() ? "–" : String.join(", ", names));
            out.text(UPGRADE, upgrades.isEmpty() ? "–" : String.join(", ", upgrades));
            out.text(FILTERS, String.valueOf(filters));
            out.text(RATE, sides.isEmpty() ? "–" : rate(be, sides.getFirst()));
            var control = controllable(be);
            if (!control.isEmpty()) {
                int i = REDSTONE_MODES.indexOf(be.getRedstoneMode(control.getFirst(), be.getPipeTypes()[0]));
                out.text(REDSTONE, MODES.get(Math.max(0, i)));
            }
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            if (!REDSTONE.equals(widgetId)) return "not_interactive";
            int i = MODES.indexOf(value.getAsString());
            if (i < 0) return "invalid_value";
            var control = controllable(be);
            if (control.isEmpty()) return "unavailable";
            for (var dir : control) for (var type : be.getPipeTypes()) be.setRedstoneMode(dir, type, REDSTONE_MODES.get(i));
            changed();
            CCCloud.LOGGER.info("[cccloud] {} set the redstone mode of the pipe at {} to {}", user, pos.toShortString(), MODES.get(i));
            return null;
        }
    }
}
