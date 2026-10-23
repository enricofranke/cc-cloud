package ai.ezfox.cccloud.compat.sfm;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.uplink.Widget;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Super Factory Manager (docs/SPEC.md v0.8 item 6): the manager's state (running / no disk / no program / invalid
 * program), program name, average tick time, compile errors and warnings (count + the first error) and queued redstone
 * pulses; a warn alert once when the program becomes invalid. No on/off control: SFM has none – taking the disk out is
 * its off switch.
 */
public final class SfmCards {
    public static final String STATE = "state";
    public static final String PROGRAM = "program";
    public static final String TICK = "tick_time";
    public static final String ERRORS = "errors";
    public static final String WARNINGS = "warnings";
    public static final String LAST_ERROR = "last_error";
    public static final String PULSES = "pulses";

    private SfmCards() {
    }

    public static void init() {
        DeviceCards.register("sfm", DeviceCards.SPECIFIC, SfmCards::detect);
        Integrations.test("sfm_manager", () -> SfmGameTests::manager);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof ManagerBlockEntity m ? new Manager(level, pos, side, m) : null;
    }

    static String describe(ManagerBlockEntity.State state) {
        return switch (state.name()) {
            case "RUNNING" -> "Running";
            case "NO_DISK" -> "No disk";
            case "NO_PROGRAM" -> "No program on the disk";
            case "INVALID_PROGRAM" -> "Program has errors";
            default -> state.name().toLowerCase(Locale.ROOT);
        };
    }

    static final class Manager extends BlockCard<ManagerBlockEntity> {
        private @Nullable String lastState;

        Manager(ServerLevel level, BlockPos pos, Direction side, ManagerBlockEntity m) {
            super(level, pos, side, m);
        }

        @Override
        public String kind() {
            return "sfm_manager";
        }

        @Override
        public String title() {
            return "Factory Manager";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATE, "State", "span", 2)
                .label(PROGRAM, "Program")
                .label(TICK, "Tick time")
                .label(ERRORS, "Errors")
                .label(WARNINGS, "Warnings")
                .label(LAST_ERROR, "First error", "span", 2)
                .label(PULSES, "Queued redstone pulses")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var state = be.getState();
            var disk = be.getDisk();
            out.text(STATE, describe(state));
            String name = disk.isEmpty() ? "" : DiskItem.getProgramName(disk);
            out.text(PROGRAM, disk.isEmpty() ? "–" : name == null || name.isBlank() ? "Unnamed" : name);
            var times = be.getTickTimes();
            long sum = 0;
            int n = 0;
            if (times != null) for (var t : times) if (t != null) { sum += t.toNanos(); n++; }
            out.text(TICK, n == 0 ? "–" : String.format(Locale.ROOT, "%.2f ms", sum / 1e6 / n));
            var errors = disk.isEmpty() ? List.<net.minecraft.network.chat.Component>of() : DiskItem.getErrors(disk);
            var warnings = disk.isEmpty() ? List.<net.minecraft.network.chat.Component>of() : DiskItem.getWarnings(disk);
            out.text(ERRORS, String.valueOf(errors.size()));
            out.text(WARNINGS, String.valueOf(warnings.size()));
            out.text(LAST_ERROR, errors.isEmpty() ? "–" : errors.getFirst().getString());
            out.text(PULSES, String.valueOf(be.getUnprocessedRedstonePulseCount()));
            String now = state.name();
            if (lastState != null && !lastState.equals(now) && "INVALID_PROGRAM".equals(now)) out.alert("Factory Manager: the program has errors", "warn");
            lastState = now;
        }
    }
}
