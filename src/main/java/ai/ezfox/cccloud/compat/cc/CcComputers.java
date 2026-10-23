package ai.ezfox.cccloud.compat.cc;

import ai.ezfox.cccloud.CCCloud;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.util.DirectionUtil;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Server-side control of a CC: Tweaked computer for the automatic Computer card: label/id, power, and redstone per
 * side. Uses CC's (non-API) block entity and {@code ServerComputer}; redstone outputs need the core computer's
 * {@code RedstoneState}, which {@code ServerComputer} keeps private, so it is reached by reflection. Every failure
 * degrades gracefully (outputs become read-only, inputs fall back to the world's signal) instead of crashing.
 * <p>
 * Server thread only. Turtles are excluded (their sides move with the turtle).
 */
public final class CcComputers {
    /** Dashboard order of the sides. */
    public static final String[] SIDES = {"top", "bottom", "left", "right", "front", "back"};

    private static @Nullable Field computerField;
    private static boolean reflectionFailed;
    private static boolean linkageFailed;

    private CcComputers() {
    }

    /** A computer block next to the uplink, or null (no computer, a turtle, or CC internals changed). */
    public static @Nullable Handle find(Level level, BlockPos pos) {
        if (linkageFailed || level.isClientSide() || !level.isLoaded(pos)) return null;
        try {
            return level.getBlockEntity(pos) instanceof ComputerBlockEntity computer ? new Handle(computer) : null;
        } catch (LinkageError e) {
            linkageFailed = true;
            CCCloud.LOGGER.error("[cccloud] This CC: Tweaked version is not supported by the automatic Computer card; it is disabled", e);
            return null;
        }
    }

    private static @Nullable Computer core(ServerComputer computer) {
        if (reflectionFailed) return null;
        try {
            var field = computerField;
            if (field == null) {
                field = ServerComputer.class.getDeclaredField("computer");
                field.setAccessible(true);
                computerField = field;
            }
            return (Computer) field.get(computer);
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            CCCloud.LOGGER.warn("[cccloud] Cannot reach CC's redstone state; the Computer card's redstone outputs are read-only", e);
            return null;
        }
    }

    public static ComputerSide side(String name) {
        return switch (name) {
            case "top" -> ComputerSide.TOP;
            case "bottom" -> ComputerSide.BOTTOM;
            case "left" -> ComputerSide.LEFT;
            case "right" -> ComputerSide.RIGHT;
            case "front" -> ComputerSide.FRONT;
            default -> ComputerSide.BACK;
        };
    }

    public static final class Handle {
        private final ComputerBlockEntity be;

        Handle(ComputerBlockEntity be) {
            this.be = be;
        }

        public int id() {
            return be.getComputerID();
        }

        public String title() {
            String label = be.getLabel();
            return label == null || label.isBlank() ? "Computer #" + be.getComputerID() : label;
        }

        public boolean isOn() {
            var computer = be.getServerComputer();
            return computer != null && computer.isOn();
        }

        private ServerComputer computer() {
            var computer = be.getServerComputer();
            return computer != null ? computer : be.createServerComputer();
        }

        public void turnOn() {
            computer().turnOn();
        }

        public void shutdown() {
            var computer = be.getServerComputer();
            if (computer != null) computer.shutdown();
        }

        public void reboot() {
            computer().reboot();
        }

        /** Queues an event on the computer (it must be on). */
        public void queueEvent(String name, Object... args) {
            var computer = be.getServerComputer();
            if (computer != null) computer.queueEvent(name, args);
        }

        /** Advanced and command computers have colour and multishell. */
        public boolean advanced() {
            return be.getFamily() != dan200.computercraft.shared.computer.core.ComputerFamily.NORMAL;
        }

        /** The computer's folder in the world save ({@code computer/<id>}), or null before it got an id. */
        public @Nullable String saveDir() {
            int id = be.getComputerID();
            return id < 0 ? null : be.getFamily().getSaveFolder() + "/" + id;
        }

        public @Nullable String label() {
            return be.getLabel();
        }

        /** Redstone input on a computer-local side (works while the computer is off). */
        public int input(String side) {
            var computer = be.getServerComputer();
            var core = computer == null ? null : core(computer);
            if (core != null) return core.getRedstone().getInput(side(side));
            // Fallback: the signal of the neighbouring block.
            Direction dir = global(side);
            var level = be.getLevel();
            return level == null ? 0 : level.getSignal(be.getBlockPos().relative(dir), dir);
        }

        /** The output the computer currently emits on a side (0 while it is off). */
        public int output(String side) {
            var computer = be.getServerComputer();
            if (computer == null) return 0;
            var core = core(computer);
            return core != null ? core.getRedstone().getOutput(side(side)) : computer.getRedstoneOutput(side(side));
        }

        /** Sets an output like {@code redstone.setAnalogOutput}. Returns false if not possible. */
        public boolean setOutput(String side, int level) {
            var core = core(computer());
            if (core == null) return false;
            core.getRedstone().setOutput(side(side), Math.max(0, Math.min(15, level)));
            return true;
        }

        public boolean canSetOutputs() {
            return core(computer()) != null;
        }

        private Direction global(String side) {
            var front = be.getDirection();
            for (var dir : Direction.values()) {
                if (DirectionUtil.toLocal(front, dir) == side(side)) return dir;
            }
            return Direction.UP;
        }
    }
}
