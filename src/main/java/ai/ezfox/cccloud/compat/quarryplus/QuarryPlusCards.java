package ai.ezfox.cccloud.compat.quarryplus;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.MachineStorage;
import com.yogpc.qp.machine.MachineStorageHolder;
import com.yogpc.qp.machine.PowerEntity;
import com.yogpc.qp.machine.QpBlockProperty;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * QuarryPlus / "Additional Enchanted Miner" (docs/SPEC.md v0.8 item 3): Quarry and Chunk Destroyer (adv. quarry).
 * Status (the machine's own state, read through its public client-sync tag), working indicator, energy, area size,
 * dig depth progress (quarry: from the drill head), buffered output, and an alert once when the output piles up
 * (nothing pulls the mined items out) or the machine finishes. No coordinates are shown (device cards are visible to
 * viewers). QuarryPlus has no redstone or API pause, so the card has no controls.
 */
public final class QuarryPlusCards {
    public static final String STATUS = "status";
    public static final String WORKING = "working";
    public static final String ENERGY = "energy";
    public static final String AREA = "area";
    public static final String DEPTH = "depth";
    public static final String BUFFER = "buffer";
    public static final String OUTPUT_FULL = "output_full";
    /** Items waiting in the internal buffer above which the output counts as blocked. */
    static final long BLOCKED_ITEMS = 10_000;

    private QuarryPlusCards() {
    }

    public static void init() {
        DeviceCards.register("quarryplus", DeviceCards.SPECIFIC, QuarryPlusCards::detect);
        Integrations.test("quarryplus_quarry", () -> QuarryPlusGameTests::quarry);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        if (be instanceof QuarryEntity || be instanceof AdvQuarryEntity) return new Quarry(level, pos, side, (PowerEntity) be);
        return null;
    }

    /** Plain English for the state names of both machines. */
    static String describe(String state) {
        return switch (state) {
            case "FINISHED" -> "Finished";
            case "WAITING" -> "Waiting (no area or no energy)";
            case "BREAK_INSIDE_FRAME" -> "Clearing the frame area";
            case "MAKE_FRAME" -> "Building the frame";
            case "MOVE_HEAD", "BREAK_BLOCK" -> "Mining";
            case "REMOVE_FLUID" -> "Removing fluids";
            case "FILLER" -> "Filling";
            case "CLEAN_UP" -> "Cleaning up";
            case "" -> "–";
            default -> Fmt.title(state.toLowerCase(Locale.ROOT));
        };
    }

    static final class Quarry extends BlockCard<PowerEntity> {
        private boolean wasBlocked;
        private String lastState = "";

        Quarry(ServerLevel level, BlockPos pos, Direction side, PowerEntity be) {
            super(level, pos, side, be);
        }

        @Override
        public String kind() {
            return be instanceof AdvQuarryEntity ? "quarryplus_adv_quarry" : "quarryplus_quarry";
        }

        @Override
        public String title() {
            return be instanceof AdvQuarryEntity ? "Chunk Destroyer" : "Quarry";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .indicator(WORKING, "Working", "onColor", "green", "offColor", "gray")
                .indicator(OUTPUT_FULL, "Output blocked", "onColor", "red", "offColor", "gray")
                .percent(ENERGY, "Energy", "color", "green", "icon", "bolt")
                .label(AREA, "Area")
                .progress(DEPTH, "Depth", 0, 100, "%")
                .label(BUFFER, "Buffered items")
                .build();
        }

        /** The machine's state name from its public client-sync tag ("" if unavailable). */
        private String state() {
            try (var reporter = new ProblemReporter.ScopedCollector(be.problemPath(), CloudLog.LOGGER)) {
                var out = TagValueOutput.createWithContext(reporter, level.registryAccess());
                if (be instanceof QuarryEntity q) q.toClientTag(out);
                else if (be instanceof AdvQuarryEntity a) a.toClientTag(out);
                return out.buildResult().getStringOr("state", "");
            }
        }

        private @Nullable Area area() {
            return be instanceof QuarryEntity q ? q.getArea() : be instanceof AdvQuarryEntity a ? a.getArea() : null;
        }

        private int digMinY() {
            var dig = be instanceof QuarryEntity q ? q.digMinY : be instanceof AdvQuarryEntity a ? a.digMinY : null;
            return dig == null ? level.getMinY() : dig.getMinY(level);
        }

        private long buffered() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            var holder = (java.util.Optional) MachineStorageHolder.getHolder(be);
            if (holder.isEmpty()) return 0;
            @SuppressWarnings("unchecked")
            MachineStorage storage = ((MachineStorageHolder<Object>) holder.get()).getMachineStorage(be);
            long total = 0;
            for (var entry : storage.itemKeyCounts()) total += Math.max(0, entry.count());
            return total;
        }

        @Override
        public void refresh(CardOutput out) {
            String state = state();
            var blockState = level.getBlockState(pos);
            boolean working = blockState.hasProperty(QpBlockProperty.WORKING) && blockState.getValue(QpBlockProperty.WORKING);
            var area = area();
            out.text(STATUS, area == null ? "No area set" : describe(state));
            out.bool(WORKING, working);
            out.number(ENERGY, Fmt.percent(be.getEnergy() / (double) PowerEntity.ONE_FE, be.getMaxEnergy() / (double) PowerEntity.ONE_FE));
            int bottom = digMinY();
            if (area == null) {
                out.text(AREA, "Not set");
                out.number(DEPTH, 0);
            } else {
                int sx = area.maxX() - area.minX() - 1, sz = area.maxZ() - area.minZ() - 1;
                out.text(AREA, Math.max(0, sx) + " × " + Math.max(0, sz) + " down to Y " + bottom);
                double top = area.minY();
                double depth;
                if ("FINISHED".equals(state)) depth = 100;
                else if (be instanceof QuarryEntity q && q.head != null && top > bottom && ("BREAK_BLOCK".equals(state) || "MOVE_HEAD".equals(state) || "REMOVE_FLUID".equals(state))) {
                    depth = Math.max(0, Math.min(100, (top - q.head.y) * 100 / (top - bottom)));
                } else depth = 0;
                out.number(DEPTH, Math.round(depth));
            }
            long buffered = buffered();
            out.text(BUFFER, Fmt.si(buffered));
            boolean blocked = buffered >= BLOCKED_ITEMS;
            out.bool(OUTPUT_FULL, blocked);
            if (blocked && !wasBlocked) out.alert(title() + ": output blocked, " + Fmt.si(buffered) + " items waiting", "warn");
            wasBlocked = blocked;
            if (!lastState.isEmpty() && !"FINISHED".equals(lastState) && "FINISHED".equals(state)) out.alert(title() + " finished", "info");
            lastState = state;
        }
    }
}
