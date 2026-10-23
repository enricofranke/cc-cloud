package ai.ezfox.cccloud.compat.advancedae;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.bridge.ItemNames;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonElement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.entities.AdvCraftingBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Advanced AE quantum computer (docs/SPEC.md v0.8 item 2): an uplink on any block of a formed quantum computer shows
 * the cluster – storage, co-processor threads, parallel jobs with their progress – and can cancel all its jobs
 * ({@code act.buttons}, confirm). Quantum CPU jobs also appear in the ME Cloud Bridge's job list without this card,
 * because Advanced AE adds its CPUs to AE2's {@code ICraftingService#getCpus()}.
 */
public final class AdvancedAeCards {
    public static final String STATUS = "status";
    public static final String STORAGE = "storage";
    public static final String THREADS = "threads";
    public static final String ACTIVE = "active";
    public static final String JOB = "job";
    public static final String CANCEL = "cancel_all";
    /** Job rows on the card (more jobs are summarised in the last row). */
    static final int JOB_ROWS = 6;

    private AdvancedAeCards() {
    }

    public static void init() {
        DeviceCards.register("advancedae", DeviceCards.SPECIFIC, AdvancedAeCards::detect);
        ai.ezfox.cccloud.compat.Integrations.test("advancedae_quantum_unit", () -> AdvancedAeGameTests::quantumUnit);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof AdvCraftingBlockEntity be ? new QuantumComputer(level, pos, side, be) : null;
    }

    static String describe(GenericStack stack) {
        String name = stack.what() instanceof AEItemKey item ? ItemNames.displayName(item.getItem()) : stack.what().getId().toString();
        return Fmt.si(stack.amount()) + "× " + name;
    }

    static final class QuantumComputer extends BlockCard<AdvCraftingBlockEntity> {
        private int lastActive = -1;

        QuantumComputer(ServerLevel level, BlockPos pos, Direction side, AdvCraftingBlockEntity be) {
            super(level, pos, side, be);
        }

        @Override
        public String kind() {
            return "advancedae_quantum";
        }

        @Override
        public String title() {
            return "Quantum Computer";
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(STORAGE, "Storage")
                .label(THREADS, "Co-processors")
                .label(ACTIVE, "Jobs running");
            for (int i = 1; i <= JOB_ROWS; i++) w.label(JOB + i, "Job " + i, "span", 2);
            w.button(CANCEL, "Cancel all jobs", "confirm", true, "color", "red");
            return w.build();
        }

        @Override
        public void refresh(CardOutput out) {
            var cluster = be.getCluster();
            if (cluster == null || cluster.isDestroyed()) {
                out.text(STATUS, "Not formed");
                out.text(STORAGE, "–");
                out.text(THREADS, "–");
                out.text(ACTIVE, "0");
                for (int i = 1; i <= JOB_ROWS; i++) out.text(JOB + i, "–");
                lastActive = -1;
                return;
            }
            out.text(STATUS, cluster.isActive() ? "Online – " + cluster.getName().getString() : "No power / no channel");
            out.text(STORAGE, Fmt.si(cluster.getAvailableStorage()) + " bytes free");
            out.text(THREADS, String.valueOf(cluster.getCoProcessors()));
            List<AdvCraftingCPU> cpus = cluster.getActiveCPUs();
            int active = 0;
            int row = 1;
            for (var cpu : cpus) {
                if (!cpu.isBusy()) continue;
                active++;
                var status = cpu.getJobStatus();
                if (status == null || row > JOB_ROWS) continue;
                if (row == JOB_ROWS && cpus.size() > JOB_ROWS) {
                    out.text(JOB + row++, "+" + (cpus.size() - JOB_ROWS + 1) + " more jobs");
                    continue;
                }
                long pct = status.totalItems() > 0 ? Math.round(100.0 * status.progress() / status.totalItems()) : 0;
                out.text(JOB + row++, describe(status.crafting()) + " – " + pct + " %, " + Fmt.duration(status.elapsedTimeNanos() / 1_000_000_000L));
            }
            for (; row <= JOB_ROWS; row++) out.text(JOB + row, "–");
            out.text(ACTIVE, String.valueOf(active));
            if (lastActive > 0 && active == 0) out.alert("Quantum Computer finished all jobs", "info");
            lastActive = active;
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            if (!CANCEL.equals(widgetId)) return "not_interactive";
            var cluster = be.getCluster();
            if (cluster == null || cluster.isDestroyed()) return "unavailable";
            cluster.cancelJobs();
            CCCloud.LOGGER.info("[cccloud] {} cancelled all jobs of the quantum computer at {}", user, pos.toShortString());
            return null;
        }
    }
}
