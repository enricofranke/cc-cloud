package ai.ezfox.cccloud.compat.stepcrafter;

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
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.ultramega.stepcrafter.common.stepcrafter.stepcraftingmonitor.StepCraftingMonitorBlockEntity;
import com.ultramega.stepcrafter.common.stepcrafter.task.StepTaskState;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Stepcrafter (Refined Storage add-on): the Step Crafting Monitor as a card: tasks, up to {@link #ROWS} rows ("64× Iron
 * Ingot – Running 42 %"), a warn alert once when a task runs out of ingredients or the network is full, an info alert
 * when the last task finishes, and Cancel all (the monitor's own button).
 */
public final class StepCraftingCards {
    public static final String STATUS = "status";
    public static final String TASK = "task_";
    public static final String CANCEL = "cancel";
    static final int ROWS = 6;

    private StepCraftingCards() {
    }

    public static void init() {
        DeviceCards.register("stepcrafter", DeviceCards.SPECIFIC, StepCraftingCards::detect);
        Integrations.test("stepcrafter_monitor", () -> StepCraftingGameTests::monitor);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof StepCraftingMonitorBlockEntity m ? new Monitor(level, pos, side, m) : null;
    }

    static String describe(StepTaskState state) {
        return switch (state) {
            case READY -> "Ready";
            case RUNNING -> "Running";
            case NOT_ENOUGH_INGREDIENTS -> "Missing ingredients";
            case NETWORK_FULL -> "Network full";
            case COMPLETED -> "Done";
        };
    }

    static String resource(ResourceKey key, long amount) {
        if (key instanceof ItemResource item) return amount + "× " + ItemNames.displayName(item.item());
        if (key instanceof FluidResource fluid) return Fmt.si(amount) + " mB " + Fmt.title(BuiltInRegistries.FLUID.getKey(fluid.fluid()).getPath());
        return String.valueOf(amount);
    }

    static final class Monitor extends BlockCard<StepCraftingMonitorBlockEntity> {
        private int lastTasks;
        private boolean wasStuck;

        Monitor(ServerLevel level, BlockPos pos, Direction side, StepCraftingMonitorBlockEntity m) {
            super(level, pos, side, m);
        }

        @Override
        public String kind() {
            return "stepcrafter_monitor";
        }

        @Override
        public String title() {
            return "Step Crafting Monitor";
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets().label(STATUS, "Status");
            for (int i = 0; i < ROWS; i++) w.label(TASK + i, "Task " + (i + 1));
            return w.button(CANCEL, "Cancel all tasks", "confirm", true, "color", "red").build();
        }

        @Override
        public void refresh(CardOutput out) {
            var tasks = be.getStatuses();
            boolean stuck = false;
            for (int i = 0; i < tasks.size(); i++) {
                var task = tasks.get(i);
                stuck |= task.state() == StepTaskState.NOT_ENOUGH_INGREDIENTS || task.state() == StepTaskState.NETWORK_FULL;
                if (i < ROWS) {
                    String done = task.state() == StepTaskState.RUNNING ? String.format(Locale.ROOT, " %.0f %%", task.percentageCompleted() * 100) : "";
                    out.text(TASK + i, resource(task.info().resource(), task.info().amount()) + " – " + describe(task.state()) + done);
                }
            }
            for (int i = tasks.size(); i < ROWS; i++) out.text(TASK + i, "–");
            out.text(STATUS, !be.isStepCraftingMonitorActive() ? "Not active" : tasks.isEmpty() ? "No tasks" : tasks.size() + (tasks.size() == 1 ? " task" : " tasks"));
            if (stuck && !wasStuck) out.alert("Step Crafting: a task is stuck (ingredients missing or network full)", "warn");
            wasStuck = stuck;
            if (lastTasks > 0 && tasks.isEmpty()) out.alert("Step Crafting: all tasks are done", "info");
            lastTasks = tasks.size();
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            if (!CANCEL.equals(widgetId)) return "not_interactive";
            if (!be.isStepCraftingMonitorActive()) return "unavailable";
            be.cancelAll();
            CCCloud.LOGGER.info("[cccloud] {} cancelled the step crafting tasks at {}", user, pos.toShortString());
            return null;
        }
    }
}
