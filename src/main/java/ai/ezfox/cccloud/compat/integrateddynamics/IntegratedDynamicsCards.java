package ai.ezfox.cccloud.compat.integrateddynamics;

import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.blockentity.BlockEntityMultipartTicking;
import org.cyclops.integrateddynamics.core.part.panel.PartTypePanelVariableDriven;
import org.jspecify.annotations.Nullable;

/**
 * Integrated Dynamics (docs/SPEC.md v0.8 item 7): an uplink next to a logic cable that carries Display Panels shows the
 * value each panel displays right now (ID evaluates it on its own network; we only read the panel's display value), with
 * the value type. One label per side that has a panel. Variable Stores are not read: their variables are only evaluated
 * by a reader/panel on the network.
 */
public final class IntegratedDynamicsCards {
    public static final String PANEL = "panel_";

    private IntegratedDynamicsCards() {
    }

    public static void init() {
        DeviceCards.register("integrateddynamics", DeviceCards.SPECIFIC, IntegratedDynamicsCards::detect);
    }

    /** Sides of the cable that carry a variable-driven panel (display panel, light panel). */
    static List<Direction> panels(BlockEntityMultipartTicking cable) {
        var sides = new ArrayList<Direction>();
        var container = cable.getPartContainer();
        for (var dir : Direction.values()) {
            try {
                if (container.hasPart(dir) && container.getPartState(dir) instanceof PartTypePanelVariableDriven.State<?, ?>) sides.add(dir);
            } catch (Exception e) {
                // PartStateException: no state on that side
            }
        }
        return sides;
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        if (!(level.getBlockEntity(pos) instanceof BlockEntityMultipartTicking cable)) return null;
        var sides = panels(cable);
        return sides.isEmpty() ? null : new Panels(level, pos, side, cable, sides);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static String format(@Nullable IValue value) {
        if (value == null) return "No value";
        IValueType type = value.getType();
        String text;
        try {
            text = type.toCompactString(value).getString();
        } catch (RuntimeException e) {
            text = type.toString(value);
        }
        return text + " (" + Fmt.title(type.getTypeName().replaceAll("^.*[.:]", "")) + ")";
    }

    static final class Panels extends BlockCard<BlockEntityMultipartTicking> {
        Panels(ServerLevel level, BlockPos pos, Direction side, BlockEntityMultipartTicking cable, List<Direction> sides) {
            super(level, pos, side, cable);
        }

        @Override
        public String kind() {
            return "integrateddynamics_panels";
        }

        @Override
        public String title() {
            return "Display Panels";
        }

        @Override
        public int revision() {
            return panels(be).hashCode();
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets();
            for (var dir : panels(be)) w.label(PANEL + dir.getSerializedName(), "Panel (" + dir.getSerializedName() + ")", "span", 2);
            return w.build();
        }

        @Override
        public void refresh(CardOutput out) {
            var container = be.getPartContainer();
            for (var dir : panels(be)) {
                String text;
                try {
                    var state = container.getPartState(dir);
                    text = state instanceof PartTypePanelVariableDriven.State<?, ?> panel ? format(panel.getDisplayValue()) : "Removed";
                } catch (Exception e) {
                    text = "Removed";
                }
                out.text(PANEL + dir.getSerializedName(), text.length() > 120 ? text.substring(0, 120) + "…" : text);
            }
        }
    }
}
