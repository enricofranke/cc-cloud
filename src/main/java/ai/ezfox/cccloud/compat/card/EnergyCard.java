package ai.ezfox.cccloud.compat.card;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonElement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * The Energy card (docs/SPEC.md v0.5 A) for any block with the FE capability ({@code Capabilities.Energy.BLOCK}):
 * stored/capacity gauge, energy label, in/out (or the net flow measured from the stored amount), a 60-minute history
 * chart and, when the mod exposes one, the block's own redstone mode. Mod integrations subclass it to add richer data
 * (Powah reactor, Ender IO capacitor banks, ...).
 */
public class EnergyCard extends BlockCard<BlockEntity> {
    public static final String KIND = "energy";
    public static final String STORED = "stored";
    public static final String ENERGY = "energy";
    public static final String FLOW = "flow";
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";
    public static final String HISTORY = "history";
    public static final String REDSTONE = "redstone";

    /** A block's own redstone control mode (e.g. "Ignore", "Active with signal", "Never"). */
    public interface RedstoneControl {
        List<String> options();

        String current();

        /** @return false if the option could not be applied */
        boolean set(String option);
    }

    private final String kind;
    private final String title;
    private long lastStored = Long.MIN_VALUE;
    private long lastTime;
    private @Nullable RedstoneControl redstone;
    private boolean redstoneResolved;

    public EnergyCard(String kind, @Nullable String title, ServerLevel level, BlockPos pos, Direction side, BlockEntity be) {
        super(level, pos, side, be);
        this.kind = kind;
        this.title = title != null ? title : blockTitle();
    }

    /** Generic provider: any block entity with an FE capability and a capacity, except this mod's own blocks. */
    public static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        if (be == null) return null;
        var id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock());
        if (CCCloud.MODID.equals(id.getNamespace())) return null;
        var handler = handler(level, pos, side);
        if (handler == null || handler.getCapacityAsLong() <= 0) return null;
        return new EnergyCard(KIND, null, level, pos, side, be);
    }

    /** The block's energy handler from the uplink's side, else the side-less one. */
    public static @Nullable EnergyHandler handler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        var handler = level.getCapability(Capabilities.Energy.BLOCK, pos, side);
        if (handler == null && side != null) handler = level.getCapability(Capabilities.Energy.BLOCK, pos, null);
        return handler;
    }

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public String title() {
        return title;
    }

    // ---- hooks for richer integrations -------------------------------------------------------------------------------

    /** Stored FE; default: the capability. */
    protected long stored() {
        var h = handler(level, pos, side);
        return h == null ? 0 : h.getAmountAsLong();
    }

    /** Capacity in FE; default: the capability. */
    protected long capacity() {
        var h = handler(level, pos, side);
        return h == null ? 0 : h.getCapacityAsLong();
    }

    /** FE/t going in, if the mod tracks it; null = unknown (the card then shows the measured net flow). */
    protected @Nullable Double inputRate() {
        return null;
    }

    /** FE/t going out (or generated, for generators), if the mod tracks it. */
    protected @Nullable Double outputRate() {
        return null;
    }

    /** Caption of the output label ("Output", "Generation"). */
    protected String outputLabel() {
        return "Output";
    }

    /** True if the card shows input and output labels instead of the measured net flow. */
    protected boolean hasRates() {
        return false;
    }

    /** The block's own redstone mode, if exposed. Resolved once. */
    protected @Nullable RedstoneControl redstoneControl() {
        return null;
    }

    protected void extraWidgets(CardWidgets w) {
    }

    protected void refreshExtra(CardOutput out) {
    }

    protected @Nullable String extraAction(String widgetId, JsonElement value, String user) {
        return "not_interactive";
    }

    // ---- card --------------------------------------------------------------------------------------------------------

    private @Nullable RedstoneControl redstone() {
        if (!redstoneResolved) {
            redstoneResolved = true;
            redstone = redstoneControl();
        }
        return redstone;
    }

    @Override
    public List<Widget> widgets() {
        var w = new CardWidgets()
            .percent(STORED, "Stored", "color", "green", "icon", "bolt")
            .label(ENERGY, "Energy", "span", 2);
        if (hasRates()) {
            w.label(INPUT, "Input");
            w.label(OUTPUT, outputLabel());
        } else {
            w.label(FLOW, "Net flow");
        }
        w.history(HISTORY, "Stored (60 min)", 0.0, 100.0, "%");
        extraWidgets(w);
        var rs = redstone();
        if (rs != null && !rs.options().isEmpty()) w.select(REDSTONE, "Redstone mode", rs.options(), "confirm", true);
        return w.build();
    }

    @Override
    public void refresh(CardOutput out) {
        long stored = stored();
        long capacity = capacity();
        out.number(STORED, Fmt.percent(stored, capacity));
        out.text(ENERGY, Fmt.energy(stored, capacity));
        out.number(HISTORY, Fmt.percent(stored, capacity));
        long now = level.getGameTime();
        if (hasRates()) {
            var in = inputRate();
            var outRate = outputRate();
            out.text(INPUT, in == null ? "–" : Fmt.flow(in));
            out.text(OUTPUT, outRate == null ? "–" : Fmt.flow(outRate));
        } else if (lastStored != Long.MIN_VALUE && now > lastTime) {
            out.text(FLOW, Fmt.rate((double) (stored - lastStored) / (now - lastTime)));
        }
        lastStored = stored;
        lastTime = now;
        var rs = redstone();
        if (rs != null) out.text(REDSTONE, rs.current());
        refreshExtra(out);
    }

    @Override
    public @Nullable String action(String widgetId, JsonElement value, String user) {
        var rs = redstone();
        if (REDSTONE.equals(widgetId) && rs != null) {
            if (!rs.set(value.getAsString())) return "unsupported";
            changed();
            CCCloud.LOGGER.info("[cccloud] {} set the redstone mode of {} at {} to {}", user, title, pos.toShortString(), value.getAsString());
            return null;
        }
        return extraAction(widgetId, value, user);
    }
}
