package ai.ezfox.cccloud.compat.cc;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.GenericCards;
import ai.ezfox.cccloud.compat.card.PeripheralValues;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * The generic Peripheral card (docs/COMPAT.md "Peripheral card"): the fallback for every block CC: Tweaked exposes as a
 * peripheral (its own methods or CC's generic energy/item/fluid methods) that has no card of its own. The owner picks up
 * to {@link PeripheralValues#MAX_VALUES} getters in the dashboard's card settings ({@code card_settings}, needs
 * {@code layout.edit}); they are polled while the card is shown, rate limited and with a time budget. A setter that
 * takes one boolean can be the card's switch ({@code act.buttons}, confirm, PIN by default). Nothing is called unless
 * the owner picked it, and setters only run on a dashboard action.
 */
public final class PeripheralCard extends BlockCard<BlockEntity> {
    public static final String KIND = "peripheral";
    public static final String TYPE = "type";
    public static final String HINT = "hint";
    public static final String SWITCH = "switch";
    /** A poll slower than this (all methods together) doubles the interval, up to {@link #MAX_INTERVAL} seconds. */
    static final long SLOW_NANOS = 2_000_000;
    static final int MAX_INTERVAL = 30;
    /** All Peripheral cards together spend at most this much per server tick; the rest poll a second later. */
    static final long TICK_BUDGET_NANOS = 3_000_000;
    /** A method that failed is not called again for this many seconds. */
    static final int ERROR_PAUSE = 30;
    /** The peripheral is looked up again this often (seconds); if it changed, the card is rebuilt. */
    static final int RECHECK = 10;

    private static long budgetTick = -1;
    private static long budgetUsed;

    private IPeripheral peripheral;
    /** Replaced when the block hands out a new but equivalent peripheral object (read by snapshots on any thread). */
    private volatile PeripheralMethods.Discovery discovery;
    private volatile PeripheralValues.Settings settings = PeripheralValues.Settings.EMPTY;
    private int revision;
    private int seconds;
    private int interval;
    private int nextPoll;
    private boolean stale;
    private int rechecks;
    private final Map<String, Integer> pausedUntil = new HashMap<>();

    PeripheralCard(ServerLevel level, BlockPos pos, Direction side, BlockEntity be, IPeripheral peripheral, PeripheralMethods.Discovery discovery) {
        super(level, pos, side, be);
        this.peripheral = peripheral;
        this.discovery = discovery;
        this.interval = baseInterval();
    }

    /** Fallback provider: any block with a CC peripheral on the uplink's face, except CC: Tweaked's and ours. */
    public static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        if (!IntegrationConfig.get(IntegrationConfig.PERIPHERAL_CARD)) return null;
        var be = level.getBlockEntity(pos);
        if (be == null) return null;
        var id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock());
        if (CCCloud.MODID.equals(id.getNamespace()) || "computercraft".equals(id.getNamespace())) return null;
        if (GenericCards.excluded(be.getBlockState(), id)) return null;
        var peripheral = find(level, pos, side);
        if (peripheral == null) return null;
        return new PeripheralCard(level, pos, side, be, peripheral, PeripheralMethods.discover(level.getServer(), peripheral));
    }

    /** The peripheral a computer next to this face would see: the block's own, else CC's generic one. */
    static @Nullable IPeripheral find(ServerLevel level, BlockPos pos, Direction side) {
        var own = level.getCapability(PeripheralCapability.get(), pos, side);
        if (own != null) return own;
        var be = level.getBlockEntity(pos);
        return be == null ? null : dan200.computercraft.impl.Peripherals.getGenericPeripheral(level, pos, side, be);
    }

    private static int baseInterval() {
        return IntegrationConfig.get(IntegrationConfig.PERIPHERAL_POLL_SECONDS);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String title() {
        return blockTitle();
    }

    @Override
    public boolean isValid() {
        return !stale && super.isValid();
    }

    @Override
    public int revision() {
        return revision;
    }

    /** How often the peripheral was looked up again (tests). */
    public int rechecks() {
        return rechecks;
    }

    /** The getters the owner may pick (tests). */
    public Set<String> getters() {
        return discovery.getters().keySet();
    }

    public Set<String> setters() {
        return discovery.setters().keySet();
    }

    private Set<String> numeric() {
        return discovery.getters().values().stream().filter(PeripheralMethods.Callable::numeric).map(PeripheralMethods.Callable::name)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String valueId(int i) {
        return "v" + (i + 1);
    }

    private static String chartId(int i) {
        return "c" + (i + 1);
    }

    @Override
    public List<Widget> widgets() {
        var s = settings;
        var w = new CardWidgets().label(TYPE, "Peripheral");
        if (s.show().isEmpty()) w.label(HINT, "Values");
        for (int i = 0; i < s.show().size(); i++) {
            String name = s.show().get(i);
            var c = discovery.getters().get(name);
            String caption = PeripheralValues.caption(name);
            if (c != null && c.bool()) w.indicator(valueId(i), caption, "span", 2);
            else w.label(valueId(i), caption, "span", 2);
        }
        for (int i = 0; i < s.show().size(); i++) {
            String name = s.show().get(i);
            if (s.charts().contains(name)) w.history(chartId(i), PeripheralValues.caption(name), null, null, null);
        }
        if (s.toggle() != null) {
            w.toggle(SWITCH, PeripheralValues.caption(s.toggle()), "confirm", true, "pin", s.pin() ? Boolean.TRUE : null, "icon", "bolt");
        }
        return w.build();
    }

    @Override
    public void refresh(CardOutput out) {
        seconds++;
        out.text(TYPE, String.join(", ", discovery.types()));
        var s = settings;
        if (s.show().isEmpty()) {
            out.text(HINT, discovery.getters().isEmpty()
                ? discovery.unknown() > 0 ? "This block does not say which arguments its methods take – use a Lua program" : "This block has no values to show"
                : "Pick up to " + PeripheralValues.MAX_VALUES + ": Edit, then click this card");
        }
        if (seconds % RECHECK == 0) {
            rechecks++;
            var now = find(level, pos, side);
            if (now == null) {
                stale = true;
                return;
            }
            if (!now.equals(peripheral)) {
                // CC builds a new generic peripheral object on every lookup; only a different set of methods is a new card
                var fresh = PeripheralMethods.discover(level.getServer(), now);
                if (!sameShape(fresh, discovery)) {
                    stale = true;
                    return;
                }
                peripheral = now;
                discovery = fresh;
            }
        }
        if (seconds < nextPoll || (s.show().isEmpty() && s.toggle() == null)) return;
        long tick = level.getGameTime();
        if (tick != budgetTick) {
            budgetTick = tick;
            budgetUsed = 0;
        }
        if (budgetUsed >= TICK_BUDGET_NANOS) return; // over the shared budget: poll on the next refresh
        long start = System.nanoTime();
        for (int i = 0; i < s.show().size(); i++) {
            String name = s.show().get(i);
            var c = discovery.getters().get(name);
            if (c == null) continue;
            Integer paused = pausedUntil.get(name);
            if (paused != null && seconds < paused) continue;
            var outcome = PeripheralMethods.call(c, null);
            if (outcome.error() != null) {
                pausedUntil.put(name, seconds + ERROR_PAUSE);
                if (!c.bool()) out.text(valueId(i), PeripheralValues.format("error: " + outcome.error()));
                continue;
            }
            Boolean b = PeripheralValues.bool(outcome.value());
            if (c.bool()) out.bool(valueId(i), b != null && b);
            else out.text(valueId(i), PeripheralValues.format(outcome.value()));
            if (s.charts().contains(name)) {
                Double n = PeripheralValues.number(outcome.value());
                if (n != null) out.number(chartId(i), n);
            }
        }
        if (s.toggle() != null) {
            var state = pairedGetter(s.toggle());
            if (state != null) {
                var outcome = PeripheralMethods.call(state, null);
                Boolean b = outcome.error() == null ? PeripheralValues.bool(outcome.value()) : null;
                if (b != null) out.bool(SWITCH, b);
            }
        }
        long took = System.nanoTime() - start;
        budgetUsed += took;
        if (took > SLOW_NANOS) interval = Math.min(MAX_INTERVAL, interval * 2);
        else if (took < SLOW_NANOS / 4) interval = Math.max(baseInterval(), interval / 2);
        nextPoll = seconds + interval;
    }

    /** Same type and the same getters (with the same kind of result) and setters: the card's widgets still fit. */
    static boolean sameShape(PeripheralMethods.Discovery a, PeripheralMethods.Discovery b) {
        if (!a.type().equals(b.type()) || !a.getters().keySet().equals(b.getters().keySet()) || !a.setters().keySet().equals(b.setters().keySet())) return false;
        for (var e : a.getters().entrySet()) {
            var other = b.getters().get(e.getKey());
            if (other.bool() != e.getValue().bool() || other.numeric() != e.getValue().numeric()) return false;
        }
        return true;
    }

    /** The getter that reports a setter's state ({@code isEnabled} / {@code getEnabled} for {@code setEnabled}), if any. */
    private PeripheralMethods.@Nullable Callable pairedGetter(String setter) {
        for (String name : PeripheralValues.pairedGetters(setter)) {
            var c = discovery.getters().get(name);
            if (c != null && c.bool()) return c;
        }
        return null;
    }

    @Override
    public @Nullable String action(String widgetId, JsonElement value, String user) {
        var s = settings;
        if (!SWITCH.equals(widgetId) || s.toggle() == null) return "not_interactive";
        var setter = discovery.setters().get(s.toggle());
        if (setter == null) return "unavailable";
        boolean on = value.getAsBoolean();
        var outcome = PeripheralMethods.call(setter, on);
        if (outcome.error() != null) {
            CCCloud.LOGGER.info("[cccloud] {} could not call {}({}) on {} at {}: {}", user, s.toggle(), on, blockTitle(), pos.toShortString(), outcome.error());
            return "unavailable";
        }
        changed();
        nextPoll = 0; // show the new state with the next refresh
        CCCloud.LOGGER.info("[cccloud] {} called {}({}) on {} at {}", user, s.toggle(), on, blockTitle(), pos.toShortString());
        return null;
    }

    // ---- settings ----------------------------------------------------------------------------------------------------

    @Override
    public @Nullable JsonObject settings() {
        return settings.toJson();
    }

    @Override
    public JsonObject settingsInfo() {
        var json = new JsonObject();
        json.addProperty("kind", KIND);
        json.addProperty("type", discovery.type());
        var types = new JsonArray();
        discovery.types().forEach(types::add);
        json.add("types", types);
        var getters = new JsonArray();
        discovery.getters().values().stream().limit(64).forEach(c -> {
            var g = new JsonObject();
            g.addProperty("name", c.name());
            g.addProperty("caption", PeripheralValues.caption(c.name()));
            g.addProperty("value", c.bool() ? "bool" : c.numeric() ? "number" : "text");
            getters.add(g);
        });
        json.add("getters", getters);
        var setters = new JsonArray();
        discovery.setters().values().stream().limit(16).forEach(c -> {
            var g = new JsonObject();
            g.addProperty("name", c.name());
            g.addProperty("caption", PeripheralValues.caption(c.name()));
            setters.add(g);
        });
        json.add("setters", setters);
        json.addProperty("unknown", discovery.unknown());
        json.addProperty("maxValues", PeripheralValues.MAX_VALUES);
        json.addProperty("maxCharts", PeripheralValues.MAX_CHARTS);
        json.add("settings", settings.toJson());
        return json;
    }

    @Override
    public @Nullable String applySettings(JsonObject json, boolean saved) {
        var getters = discovery.getters().keySet();
        var setters = discovery.setters().keySet();
        if (saved) {
            settings = PeripheralValues.load(json).restrictTo(getters, numeric(), setters);
        } else {
            var parsed = PeripheralValues.parse(json, getters, numeric(), setters);
            if (parsed.error() != null) return parsed.error();
            settings = parsed.settings();
        }
        revision++;
        nextPoll = 0;
        pausedUntil.clear();
        return null;
    }
}
