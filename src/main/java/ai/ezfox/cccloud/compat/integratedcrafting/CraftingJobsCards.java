package ai.ezfox.cccloud.compat.integratedcrafting;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.cyclops.commoncapabilities.IngredientComponents;
import org.cyclops.integratedcrafting.api.crafting.CraftingJob;
import org.cyclops.integratedcrafting.api.crafting.CraftingJobStatus;
import org.cyclops.integratedcrafting.api.network.ICraftingNetwork;
import org.cyclops.integratedcrafting.core.CraftingHelpers;
import org.cyclops.integratedcrafting.core.part.PartTypeInterfaceCraftingBase;
import org.cyclops.integrateddynamics.core.blockentity.BlockEntityMultipartTicking;
import org.cyclops.integrateddynamics.core.helper.NetworkHelpers;
import org.jspecify.annotations.Nullable;

/**
 * Integrated Crafting: an uplink on a logic cable that carries a Crafting Interface shows the network's crafting jobs:
 * the jobs by state, up to {@link #ROWS} rows ("64× Iron Ingot – Processing"), interfaces and recipes, a warn alert once
 * when a job's inputs turn out to be invalid, an info alert when the last job finishes, and a Cancel all button (what
 * the Crafting Jobs Terminal does for one job). Registered before Integrated Tunnels: this cable shows the jobs, any
 * other cable of the network the items.
 */
public final class CraftingJobsCards {
    public static final String STATUS = "status";
    public static final String INTERFACES = "interfaces";
    public static final String RECIPES = "recipes";
    public static final String JOB = "job_";
    public static final String CANCEL = "cancel";
    static final int ROWS = 6;

    private CraftingJobsCards() {
    }

    public static void init() {
        DeviceCards.register("integratedcrafting", DeviceCards.SPECIFIC, CraftingJobsCards::detect);
        Integrations.test("integratedcrafting_jobs", () -> CraftingJobsGameTests::jobs);
    }

    /** True if the cable carries a Crafting Interface on any side. */
    static boolean hasInterface(BlockEntityMultipartTicking cable) {
        for (var part : cable.getPartContainer().getParts().values()) {
            if (part instanceof PartTypeInterfaceCraftingBase<?, ?>) return true;
        }
        return false;
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        if (!(level.getBlockEntity(pos) instanceof BlockEntityMultipartTicking cable) || !hasInterface(cable)) return null;
        return new Jobs(level, pos, side, cable);
    }

    /** Words of a job's state; the first one is what the status line counts. */
    static String describe(@Nullable CraftingJobStatus status) {
        if (status == null) return "Waiting";
        return switch (status) {
            case PENDING_INTERFACE -> "Waiting for a free interface";
            case PENDING_DEPENDENCIES -> "Waiting for other jobs";
            case PENDING_INGREDIENTS -> "Missing ingredients";
            case INVALID_INPUTS -> "Invalid inputs";
            case PENDING_OUTPUT_STORAGE -> "No room for the output";
            case PROCESSING -> "Processing";
            case FINISHED -> "Finished";
            case UNKNOWN -> "Waiting";
        };
    }

    /** What the job makes: "64× Iron Ingot", "4000 mB Lava". */
    static String output(CraftingJob job) {
        var output = job.getRecipe().getOutput();
        var items = output.getInstances(IngredientComponents.ITEMSTACK);
        for (var stack : items) {
            if (!stack.isEmpty()) return (long) stack.getCount() * job.getAmount() + "× " + ItemNames.displayName(stack.getItem());
        }
        for (var fluid : output.getInstances(IngredientComponents.FLUIDSTACK)) {
            if (!fluid.isEmpty()) return (long) fluid.getAmount() * job.getAmount() + " mB " + Fmt.title(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getPath());
        }
        return job.getAmount() + "× recipe";
    }

    static final class Jobs extends BlockCard<BlockEntityMultipartTicking> {
        private int lastJobs;
        private boolean wasInvalid;

        Jobs(ServerLevel level, BlockPos pos, Direction side, BlockEntityMultipartTicking cable) {
            super(level, pos, side, cable);
        }

        @Override
        public String kind() {
            return "integratedcrafting_jobs";
        }

        @Override
        public String title() {
            return "Crafting Jobs";
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets()
                .label(STATUS, "Status")
                .label(INTERFACES, "Crafting interfaces", "span", 2)
                .label(RECIPES, "Recipes", "span", 2);
            for (int i = 0; i < ROWS; i++) w.label(JOB + i, "Job " + (i + 1));
            return w.button(CANCEL, "Cancel all jobs", "confirm", true, "color", "red").build();
        }

        private @Nullable ICraftingNetwork network() {
            var network = NetworkHelpers.getNetwork(level, pos, side).orElse(null);
            return network == null ? null : CraftingHelpers.getCraftingNetwork(network).orElse(null);
        }

        /** Every job of the network once, oldest first (an interface on the wildcard channel lists them on two channels). */
        private static List<CraftingJob> jobs(ICraftingNetwork network) {
            Map<Integer, CraftingJob> jobs = new LinkedHashMap<>();
            for (int channel : network.getChannels()) {
                for (var it = network.getCraftingJobs(channel); it.hasNext(); ) {
                    var job = it.next();
                    jobs.put(job.getId(), job);
                }
            }
            var sorted = new ArrayList<>(jobs.values());
            sorted.sort(java.util.Comparator.comparingInt(CraftingJob::getId));
            return sorted;
        }

        @Override
        public void refresh(CardOutput out) {
            var network = network();
            var jobs = network == null ? List.<CraftingJob>of() : jobs(network);
            int interfaces = 0;
            int recipes = 0;
            if (network != null) {
                for (int channel : network.getChannels()) {
                    interfaces += network.getCraftingInterfaces(channel).size();
                    recipes += network.getRecipeIndex(channel).getRecipes().size();
                }
            }
            out.text(INTERFACES, String.valueOf(interfaces));
            out.text(RECIPES, String.valueOf(recipes));
            var counts = new LinkedHashMap<String, Integer>();
            boolean invalid = false;
            for (int i = 0; i < jobs.size(); i++) {
                var job = jobs.get(i);
                var machine = network.getCraftingJobInterface(job.getChannel(), job.getId());
                var status = machine == null ? null : machine.getCraftingJobStatus(network, job.getChannel(), job.getId());
                String state = describe(status);
                counts.merge(state, 1, Integer::sum);
                invalid |= status == CraftingJobStatus.INVALID_INPUTS;
                if (i < ROWS) out.text(JOB + i, output(job) + " – " + state);
            }
            for (int i = jobs.size(); i < ROWS; i++) out.text(JOB + i, "–");
            if (network == null) out.text(STATUS, "No network");
            else if (jobs.isEmpty()) out.text(STATUS, "No jobs");
            else {
                var parts = new ArrayList<String>();
                for (var e : counts.entrySet()) parts.add(e.getValue() + " " + e.getKey().toLowerCase(java.util.Locale.ROOT));
                out.text(STATUS, String.join(", ", parts));
            }
            if (invalid && !wasInvalid) out.alert("Crafting Jobs: a job has invalid inputs", "warn");
            wasInvalid = invalid;
            if (lastJobs > 0 && jobs.isEmpty()) out.alert("Crafting Jobs: all jobs are done", "info");
            lastJobs = jobs.size();
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            if (!CANCEL.equals(widgetId)) return "not_interactive";
            var network = network();
            if (network == null) return "unavailable";
            var jobs = jobs(network);
            for (var job : jobs) network.cancelCraftingJob(job.getChannel(), job.getId());
            CCCloud.LOGGER.info("[cccloud] {} cancelled {} crafting jobs at {}", user, jobs.size(), pos.toShortString());
            return null;
        }
    }
}
