package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.network.TickCost;
import ai.ezfox.cccloud.relay.CloudRelayBlockEntity;
import ai.ezfox.cccloud.rsdevice.RsCards;
import ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity;
import ai.ezfox.cccloud.rsdevice.RsDevices;
import ai.ezfox.cccloud.rsdevice.RsPlateBlock;
import ai.ezfox.cccloud.upgrade.MainframeUpgrades;
import ai.ezfox.cccloud.upgrade.UpgradeTier;
import ai.ezfox.cccloud.upgrade.UpgradeType;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.web.CloudWebServer;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Opt-in scale benchmark ({@code ./gradlew runGameTestServer -PscaleTest}, docs/SPEC.md "Scaling"): one mainframe with
 * {@code big} devices (machine cards, Lua cards with 25 widgets, redstone devices in groups, relays), {@code mainframes}
 * more mainframes with {@code devices} devices each, 100 rules (16 of them schedules), long-term statistics, and
 * {@code browsers} dashboard clients over real WebSockets ({@code bigBrowsers} of them on the big mainframe). The server
 * runs at a real 20 TPS while it measures. Reports: cccloud's server-thread time per tick by part (avg, p95, max), the
 * whole tick, snapshot size and time per browser, delta bandwidth per browser, every screen's view size, network
 * recompute and link-check time, history memory, heap. Writes {@code scaletest-report.json} next to the server.
 * <p>
 * Without {@code -Dcccloud.scaleTest=true} the test passes at once. It fails only on functional problems (a browser was
 * disconnected, a view or snapshot over a protocol limit, no deltas); the timing targets are checked with
 * {@code -Dcccloud.scaleTest.strict=true}.
 */
public final class ScaleTest {
    static final boolean ENABLED = Boolean.getBoolean("cccloud.scaleTest");
    static final int BIG = prop("big", 500);
    static final int MAINFRAMES = prop("mainframes", 10);
    static final int DEVICES = prop("devices", 100);
    static final int BROWSERS = prop("browsers", 50);
    static final int BIG_BROWSERS = prop("bigBrowsers", 10);
    static final int BASELINE_S = prop("baseline", 10);
    static final int LOAD_S = prop("seconds", 30);
    static final int HOLD_S = prop("hold", 0);
    static final int RULES = 100;
    static final long TICK_NANOS = 50_000_000L;

    private static int prop(String name, int def) {
        return Integer.getInteger("cccloud.scaleTest." + name, def);
    }

    private ScaleTest() {
    }

    // ---- the test machine card (same code path as every ready-made card) ---------------------------------------------

    /** Positions of the test machines (lodestones) and their numbers. */
    static final Map<Long, Integer> MACHINES = new ConcurrentHashMap<>();
    private static boolean providerRegistered;

    /** Like a powered machine with an energy buffer: 8 widgets incl. a 300-point history chart; 30 % never change. */
    static final class MachineCard extends DeviceCard {
        final ServerLevel level;
        final BlockPos pos;
        final int n;

        MachineCard(ServerLevel level, BlockPos pos, int n) {
            this.level = level;
            this.pos = pos;
            this.n = n;
        }

        @Override
        public String kind() {
            return "scale_machine";
        }

        @Override
        public String title() {
            return "Machine " + n;
        }

        @Override
        public List<Widget> widgets() {
            var widgets = new CardWidgets()
                .percent("stored", "Stored", "color", "green", "icon", "bolt")
                .label("energy", "Energy", "span", 2)
                .label("input", "Input")
                .label("output", "Output")
                .history("hist", "Stored (60 min)", 0.0, 100.0, "%")
                .progress("work", "Progress", 0, 100, "%")
                .indicator("running", "Running")
                .select("mode", "Mode", List.of("Auto", "On", "Off"))
                .build();
            // An hour of history, like a machine that has been running for a while.
            for (var w : widgets) if (w.type() == WidgetType.CHART) for (int i = 0; i < CardWidgets.HISTORY_POINTS; i++) w.push((i * 7 + n) % 100);
            return widgets;
        }

        @Override
        public boolean isValid() {
            return level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LODESTONE);
        }

        @Override
        public void refresh(CardOutput out) {
            boolean stable = n % 10 < 3;
            long t = stable ? 0 : level.getGameTime() / 20;
            double stored = (n * 7 + t) % 100;
            out.number("stored", stored);
            out.text("energy", String.format(Locale.ROOT, "%.1f / 100 kFE", stored));
            out.text("input", (n * 3 + t) % 50 + " FE/t");
            out.text("output", (n * 5 + t) % 40 + " FE/t");
            out.number("hist", stored);
            out.number("work", (t * 3 + n) % 100);
            out.bool("running", (t / 5 + n) % 2 == 0);
            out.text("mode", "Auto");
        }
    }

    private static void registerProvider() {
        if (providerRegistered) return;
        providerRegistered = true;
        DeviceCards.register("scale", DeviceCards.SPECIFIC, (level, pos, side) -> {
            var n = MACHINES.get(pos.asLong());
            return n == null || !level.getBlockState(pos).is(Blocks.LODESTONE) ? null : new MachineCard(level, pos, n);
        });
    }

    // ---- tick timing -------------------------------------------------------------------------------------------------

    private static boolean listening;
    private static volatile boolean throttle;
    private static long tickStart;
    private static boolean measuring;
    private static final List<Double> wholeTicks = new ArrayList<>();
    private static final List<Double> ownTicks = new ArrayList<>();
    /** Server-thread CPU time per tick, and cccloud's wall time scaled by the tick's CPU/wall ratio (a busy machine
     *  preempts the server thread, which inflates wall times; the CPU estimate is what a quiet server would see). */
    private static final List<Double> cpuTicks = new ArrayList<>();
    private static final List<Double> ownCpuTicks = new ArrayList<>();
    private static final java.lang.management.ThreadMXBean THREADS = java.lang.management.ManagementFactory.getThreadMXBean();
    private static long tickCpuStart;
    private static long[] partSums = new long[TickCost.NAMES.length];
    private static long[] partMax = new long[TickCost.NAMES.length];

    private static void onTickPre(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
        if (measuring) tickCpuStart = THREADS.getCurrentThreadCpuTime();
        if (measuring) TickCost.takeParts(); // nothing from before this tick
    }

    private static void onTickPost(ServerTickEvent.Post event) {
        if (!measuring) return;
        long wall = System.nanoTime() - tickStart;
        long cpu = THREADS.getCurrentThreadCpuTime() - tickCpuStart;
        wholeTicks.add(wall / 1e6);
        cpuTicks.add(cpu / 1e6);
        var parts = TickCost.takeParts();
        long sum = 0;
        for (int i = 0; i < parts.length; i++) {
            sum += parts[i];
            partSums[i] += parts[i];
            partMax[i] = Math.max(partMax[i], parts[i]);
        }
        ownTicks.add(sum / 1e6);
        ownCpuTicks.add(sum / 1e6 * Math.min(1.0, wall <= 0 ? 1.0 : (double) cpu / wall));
    }

    /** Real 20 TPS: the GameTest server would otherwise tick as fast as it can. Runs after everything else. */
    private static void onTickSleep(ServerTickEvent.Post event) {
        if (!throttle) return;
        long left = TICK_NANOS - (System.nanoTime() - tickStart);
        if (left > 0) {
            try {
                Thread.sleep(left / 1_000_000, (int) (left % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static final class State {
        volatile boolean finished;
        volatile @Nullable String failure;
    }

    private static volatile @Nullable Runnable ACTIVE;
    private static volatile @Nullable State STATE;
    private static volatile @Nullable Runnable CLEANUP;

    /** Runs the active scale test's step; a failure ends it (the GameTest sequence reports it). */
    private static void onTickRun(ServerTickEvent.Post event) {
        var step = ACTIVE;
        if (step == null) return;
        try {
            step.run();
        } catch (RuntimeException e) {
            CCCloud.LOGGER.error("[cccloud] SCALE TEST failed", e);
            ACTIVE = null;
            var cleanup = CLEANUP;
            if (cleanup != null) {
                try {
                    cleanup.run();
                } catch (RuntimeException ignored) {
                }
            }
            var state = STATE;
            if (state != null) {
                state.failure = String.valueOf(e.getMessage());
                state.finished = true;
            }
        }
        var state = STATE;
        if (state != null && state.finished) ACTIVE = null;
    }

    private static void startMeasuring() {
        wholeTicks.clear();
        ownTicks.clear();
        cpuTicks.clear();
        ownCpuTicks.clear();
        partSums = new long[TickCost.NAMES.length];
        partMax = new long[TickCost.NAMES.length];
        TickCost.enabled = true;
        TickCost.takeParts();
        measuring = true;
    }

    private static JsonObject stopMeasuring() {
        measuring = false;
        TickCost.enabled = false;
        var json = new JsonObject();
        json.addProperty("ticks", ownTicks.size());
        json.add("cccloudMs", stats(ownTicks));
        json.add("wholeTickMs", stats(wholeTicks));
        json.add("wholeTickCpuMs", stats(cpuTicks));
        json.add("cccloudCpuEstMs", stats(ownCpuTicks));
        var parts = new JsonObject();
        for (int i = 0; i < partSums.length; i++) {
            if (partSums[i] == 0) continue;
            var p = new JsonObject();
            p.addProperty("avgMs", round(partSums[i] / 1e6 / Math.max(1, ownTicks.size())));
            p.addProperty("maxMs", round(partMax[i] / 1e6));
            parts.add(TickCost.NAMES[i], p);
        }
        json.add("parts", parts);
        return json;
    }

    static JsonObject stats(List<Double> values) {
        var json = new JsonObject();
        if (values.isEmpty()) return json;
        var sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        json.addProperty("avg", round(Arrays.stream(sorted).average().orElse(0)));
        json.addProperty("p50", round(sorted[sorted.length / 2]));
        json.addProperty("p95", round(sorted[Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.95) - 1)]));
        json.addProperty("p99", round(sorted[Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.99) - 1)]));
        json.addProperty("max", round(sorted[sorted.length - 1]));
        return json;
    }

    static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    // ---- the bases -----------------------------------------------------------------------------------------------------

    enum Kind { MACHINE, LUA, REDSTONE }

    static final class Base {
        CloudMainframeBlockEntity mainframe;
        MainframeLink link;
        final List<BlockPos> blocks = new ArrayList<>();
        final List<CloudUplinkBlockEntity> lua = new ArrayList<>();
        final List<CloudUplinkBlockEntity> machines = new ArrayList<>();
        final List<RsDeviceBlockEntity> redstone = new ArrayList<>();
        final List<CloudRelayBlockEntity> relays = new ArrayList<>();
        int luaWidgets;
        String token;
        ServerPlayer owner;

        UUID id() {
            return mainframe.getMainframeId();
        }
    }

    /** Spots on a 2-block grid around (0, 0), nearest first, at least 3 blocks from the mainframe. */
    static List<int[]> spots(int count) {
        var list = new ArrayList<int[]>();
        int r = 4;
        while (list.size() < count * 2 + 16) {
            list.clear();
            for (int x = -r; x <= r; x += 2) for (int z = -r; z <= r; z += 2) if (Math.max(Math.abs(x), Math.abs(z)) >= 3) list.add(new int[]{x, z});
            r += 2;
        }
        list.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        return list.subList(0, count);
    }

    private static Kind kind(int i, boolean big) {
        int m = i % 20;
        if (big) return m < 7 ? Kind.MACHINE : m < 14 ? Kind.LUA : Kind.REDSTONE;
        return m < 8 ? Kind.MACHINE : m < 16 ? Kind.LUA : Kind.REDSTONE;
    }

    private static void set(ServerLevel level, Base base, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        base.blocks.add(pos);
    }

    /** Places a base with its mainframe on {@code floor} (relative) and {@code devices} devices around it. */
    static Base place(GameTestHelper helper, ServerPlayer owner, BlockPos floor, int devices, boolean big) {
        var level = helper.getLevel();
        var base = new Base();
        base.owner = owner;
        var absFloor = helper.absolutePos(floor);
        set(level, base, absFloor, Blocks.STONE.defaultBlockState());
        base.mainframe = CloudGameTests.placeMainframe(helper, owner, floor);
        base.blocks.add(absFloor.above());
        base.blocks.add(absFloor.above(2));
        base.mainframe.setOwner(owner);
        base.mainframe.setEnabled(true);
        var upgrades = base.mainframe.upgrades();
        upgrades.setItem(MainframeUpgrades.CORE_SLOT, new ItemStack(ModRegistry.QUANTUM_CORE.get()));
        upgrades.setItem(0, new ItemStack(ModRegistry.card(UpgradeType.MEMORY, UpgradeTier.UNOBTAINIUM).get()));
        base.link = new MainframeLink(GlobalPos.of(level.dimension(), base.mainframe.getBlockPos()), base.mainframe.getMainframeId());
        int n = 0;
        for (var spot : spots(devices)) {
            var at = absFloor.offset(spot[0], 0, spot[1]);
            switch (kind(n, big)) {
                case MACHINE -> {
                    set(level, base, at.above(), Blocks.LODESTONE.defaultBlockState());
                    MACHINES.put(at.above().asLong(), MACHINES.size());
                    set(level, base, at.above(2), ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.DOWN));
                }
                case LUA -> {
                    set(level, base, at, Blocks.STONE.defaultBlockState());
                    set(level, base, at.above(), ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.DOWN));
                }
                case REDSTONE -> {
                    int r = n % 10;
                    if (r < 4) {
                        set(level, base, at.above(), ModRegistry.CLOUD_LAMP.get().defaultBlockState());
                    } else {
                        set(level, base, at, Blocks.STONE.defaultBlockState());
                        Block plate = r < 7 ? ModRegistry.CLOUD_SWITCH.get() : r < 9 ? ModRegistry.CLOUD_SENSOR.get() : ModRegistry.CLOUD_BUTTON.get();
                        set(level, base, at.above(), plate.defaultBlockState().setValue(RsPlateBlock.FACING, Direction.DOWN));
                    }
                }
            }
            n++;
        }
        if (big) {
            for (int[] r : new int[][]{{16, 16}, {-16, 16}, {16, -16}, {-16, -16}}) {
                var at = absFloor.offset(r[0], 4, r[1]);
                set(level, base, at, ModRegistry.CLOUD_RELAY.get().defaultBlockState());
                if (level.getBlockEntity(at) instanceof CloudRelayBlockEntity relay) {
                    relay.fillForTest();
                    relay.restrictForTest(base.id());
                    base.relays.add(relay);
                }
            }
        }
        return base;
    }

    /** Links every device, fills the Lua cards and names/groups the redstone devices (one tick after placing). */
    static void link(GameTestHelper helper, Base base, boolean big) {
        var level = helper.getLevel();
        int luaN = 0, rsN = 0;
        for (var pos : base.blocks) {
            var be = level.getBlockEntity(pos);
            if (be instanceof CloudUplinkBlockEntity uplink) {
                uplink.forceLinkForTest(base.link);
                if (MACHINES.containsKey(pos.below().asLong())) {
                    base.machines.add(uplink);
                } else {
                    fillLua(helper, uplink, luaN++, big);
                    base.lua.add(uplink);
                }
            } else if (be instanceof RsDeviceBlockEntity device) {
                device.forceLinkForTest(base.link);
                int k = rsN++;
                device.setNameAndGroupForTest(device.kind().name().toLowerCase(Locale.ROOT) + " " + k, big ? "Group " + k / 10 : "");
                base.redstone.add(device);
            }
        }
        base.luaWidgets = big ? 25 : 12;
    }

    private static void put(CloudUplinkBlockEntity uplink, WidgetType type, String id, String label, Map<String, Object> opts) {
        try {
            uplink.putWidget(WidgetFactory.create(type, id, label, opts));
        } catch (WidgetFactory.WidgetException e) {
            throw new IllegalStateException(id + ": " + e.getMessage(), e);
        }
    }

    /** A Lua card: 25 widgets on the big base (12 on the others), some with long-term statistics, like a real script. */
    static void fillLua(GameTestHelper helper, CloudUplinkBlockEntity uplink, int n, boolean big) {
        uplink.beginLua();
        uplink.setTitle((big ? "Reactor " : "Farm ") + n);
        put(uplink, WidgetType.GAUGE, "g0", "Temperature", Map.of("min", 0.0, "max", 1000.0, "unit", "C"));
        put(uplink, WidgetType.GAUGE, "g1", "Pressure", Map.of("min", 0.0, "max", 1000.0));
        put(uplink, WidgetType.GAUGE, "g2", "Fuel", Map.of("min", 0.0, "max", 1000.0, "history", "1h"));
        put(uplink, WidgetType.GAUGE, "g3", "Output", Map.of("min", 0.0, "max", 1000.0, "history", "24h"));
        put(uplink, WidgetType.LABEL, "l0", "Status", Map.of());
        put(uplink, WidgetType.TOGGLE, "t0", "Enabled", Map.of());
        put(uplink, WidgetType.BUTTON, "b0", "Restart", Map.of());
        put(uplink, WidgetType.CHART, "c0", "Temperature", Map.of("points", 60.0, "interval", 1.0));
        put(uplink, WidgetType.PROGRESS, "p0", "Batch", Map.of("min", 0.0, "max", 100.0));
        put(uplink, WidgetType.INDICATOR, "i0", "Running", Map.of());
        var modes = new LinkedHashMap<Integer, String>();
        modes.put(1, "Eco");
        modes.put(2, "Normal");
        modes.put(3, "Turbo");
        put(uplink, WidgetType.SELECT, "s0", "Mode", Map.of("options", modes));
        put(uplink, WidgetType.NUMBER, "n0", "Target", Map.of("min", 0.0, "max", 1000.0, "step", 1.0));
        if (big) {
            put(uplink, WidgetType.GAUGE, "g4", "Coolant", n % 4 == 0 ? Map.of("min", 0.0, "max", 1000.0, "history", "7d") : Map.of("min", 0.0, "max", 1000.0));
            put(uplink, WidgetType.GAUGE, "g5", "Speed", Map.of("min", 0.0, "max", 1000.0));
            put(uplink, WidgetType.LABEL, "l1", "Fuel left", Map.of());
            put(uplink, WidgetType.LABEL, "l2", "Last error", Map.of());
            put(uplink, WidgetType.LABEL, "l3", "Uptime", Map.of());
            put(uplink, WidgetType.TOGGLE, "t1", "Auto refuel", Map.of());
            put(uplink, WidgetType.TOGGLE, "t2", "Alarm", Map.of());
            put(uplink, WidgetType.BUTTON, "b1", "Scram", Map.of("confirm", true));
            put(uplink, WidgetType.BUTTON, "b2", "Reset", Map.of());
            put(uplink, WidgetType.CHART, "c1", "Output", Map.of("points", 60.0, "interval", 1.0));
            put(uplink, WidgetType.PROGRESS, "p1", "Fuel rod", Map.of("min", 0.0, "max", 100.0));
            put(uplink, WidgetType.INDICATOR, "i1", "Overheat", Map.of());
            put(uplink, WidgetType.TEXT, "x0", "Note", Map.of());
        }
        // An hour-old script: the charts are full.
        for (var id : List.of("c0", "c1")) {
            var w = uplink.getWidget(id);
            if (w != null) for (int i = 0; i < 60; i++) w.push((i * 13 + n) % 1000);
        }
    }

    // ---- "Lua" updates: CC computers call set() from their own threads -------------------------------------------------

    static final class LuaLoad implements Runnable {
        final List<Base> bases;
        volatile boolean running = true;
        final AtomicLong sets = new AtomicLong();

        LuaLoad(List<Base> bases) {
            this.bases = bases;
        }

        @Override
        public void run() {
            long tick = 0;
            long next = System.nanoTime();
            while (running) {
                for (var base : bases) {
                    int u = 0;
                    for (var uplink : base.lua) {
                        u++;
                        // two gauges every tick, most values every second, text every 2 s, toggles every 10 s
                        set(uplink, "g0", (tick * 7 + u) % 1000);
                        set(uplink, "g1", (tick * 3 + u * 5) % 1000);
                        if ((tick + u) % 20 == 0) {
                            long s = (tick + u) / 20;
                            for (var id : List.of("g2", "g3", "g4", "g5", "c0", "c1")) set(uplink, id, Math.floorMod(s * 11 + u + id.hashCode(), 1000));
                            set(uplink, "p0", (s * 3 + u) % 100);
                            set(uplink, "p1", (s * 7 + u) % 100);
                        }
                        if ((tick + u) % 40 == 0) {
                            long s = (tick + u) / 40;
                            set(uplink, "l0", s % 3 == 0 ? "Running" : s % 3 == 1 ? "Heating up" : "Cooling");
                            set(uplink, "l1", (s * 17 + u) % 500 + " min");
                            set(uplink, "l3", s + " s");
                        }
                        if ((tick + u) % 100 == 0) {
                            set(uplink, "i0", (tick / 100 + u) % 2 == 0);
                            set(uplink, "i1", (tick / 100 + u) % 7 == 0);
                        }
                        if ((tick + u) % 200 == 0) set(uplink, "t0", (tick / 200 + u) % 2 == 0);
                    }
                }
                tick++;
                next += TICK_NANOS;
                long sleep = next - System.nanoTime();
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep / 1_000_000, (int) (sleep % 1_000_000));
                    } catch (InterruptedException e) {
                        return;
                    }
                } else if (sleep < -TICK_NANOS * 20) {
                    next = System.nanoTime();
                }
            }
        }

        private void set(CloudUplinkBlockEntity uplink, String id, Object value) {
            var widget = uplink.getWidget(id);
            if (widget == null) return;
            try {
                uplink.setValue(id, WidgetFactory.valueFor(widget, value instanceof Long l ? (Object) l.doubleValue() : value));
                sets.incrementAndGet();
            } catch (WidgetFactory.WidgetException e) {
                throw new IllegalStateException(id + ": " + e.getMessage(), e);
            }
        }
    }

    // ---- browsers ----------------------------------------------------------------------------------------------------

    static final class Client {
        final int mainframe;
        final long openedAt = System.nanoTime();
        volatile long snapshotMs = -1;
        volatile int snapshotChars;
        final AtomicLong chars = new AtomicLong();
        final AtomicLong messages = new AtomicLong();
        final AtomicLong valueDeltas = new AtomicLong();
        volatile @Nullable WebSocket ws;
        volatile @Nullable String closed;

        Client(int mainframe) {
            this.mainframe = mainframe;
        }
    }

    static void connect(List<Client> clients, Client client, String token, String mainframeId) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        URI uri = URI.create("ws://127.0.0.1:" + CloudWebServer.port() + "/ws/client");
        http.newWebSocketBuilder().buildAsync(uri, new WebSocket.Listener() {
            private final StringBuilder partial = new StringBuilder();

            @Override
            public void onOpen(WebSocket ws) {
                ws.sendText("{\"t\":\"auth\",\"token\":\"" + token + "\",\"mainframeId\":\"" + mainframeId + "\"}", true);
                ws.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                partial.append(data);
                if (!last) {
                    ws.request(1);
                    return null;
                }
                String text = partial.toString();
                partial.setLength(0);
                if (text.startsWith("{\"t\":\"snapshot\"") && client.snapshotMs < 0) {
                    client.snapshotChars = text.length();
                    client.snapshotMs = (System.nanoTime() - client.openedAt) / 1_000_000;
                } else {
                    client.chars.addAndGet(text.length());
                    client.messages.incrementAndGet();
                    if (text.startsWith("{\"t\":\"value")) client.valueDeltas.addAndGet(countValues(text));
                    if (text.startsWith("{\"t\":\"ping\"")) ws.sendText("{\"t\":\"pong\"}", true);
                }
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                client.closed = code + " " + reason;
                CCCloud.LOGGER.warn("[cccloud] SCALE TEST client of mainframe {} closed: {} {}", client.mainframe, code, reason);
                return null;
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                client.closed = "error " + error;
            }
        }).thenAccept(ws -> client.ws = ws).exceptionally(e -> {
            client.closed = "connect failed: " + e;
            return null;
        });
        clients.add(client);
    }

    /** Values in a value message: 1 for {@code value}, the number of entries for a batched {@code values} message. */
    static int countValues(String text) {
        if (text.startsWith("{\"t\":\"value\"")) return 1;
        int count = 0;
        try {
            var json = JsonParser.parseString(text).getAsJsonObject();
            if (json.get("s") instanceof JsonObject sections) {
                for (var e : sections.entrySet()) if (e.getValue() instanceof JsonObject o) count += o.size();
            }
        } catch (RuntimeException ignored) {
        }
        return count;
    }

    // ---- the test ----------------------------------------------------------------------------------------------------

    private enum Phase { LINK, READY, STATIC, CONNECT, BASELINE, LOAD, HOLD, DONE }

    public static void run(GameTestHelper helper) {
        if (!ENABLED) {
            helper.succeed();
            return;
        }
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ScaleTest::onTickPre);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, ScaleTest::onTickRun);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ScaleTest::onTickPost);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ScaleTest::onTickSleep);
        }
        if (!CloudWebServer.isRunning()) throw helper.assertionException("Dashboard web server is not running");
        registerProvider();
        throttle = true; // a real 20 TPS from the start: periodic work (cards every 20 ticks) happens at its real rate
        var level = helper.getLevel();
        var server = level.getServer();
        var report = new JsonObject();
        var config = new JsonObject();
        config.addProperty("bigDevices", BIG);
        config.addProperty("mainframes", MAINFRAMES);
        config.addProperty("devicesEach", DEVICES);
        config.addProperty("browsers", BROWSERS);
        config.addProperty("bigBrowsers", BIG_BROWSERS);
        config.addProperty("rules", RULES);
        report.add("config", config);

        // Everything lives above the test structure; its chunks stay loaded while the test runs.
        var origin = helper.absolutePos(BlockPos.ZERO);
        var forced = new ArrayList<ChunkPos>();
        for (int cx = (origin.getX() - 48) >> 4; cx <= (origin.getX() + 48) >> 4; cx++) {
            for (int cz = (origin.getZ() - 48) >> 4; cz <= (origin.getZ() + 48) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                forced.add(new ChunkPos(cx, cz));
            }
        }
        // one owner per base: players may only place a few mainframes each (mainframeLimit)
        var owner = ai.ezfox.cccloud.ingame.InGameTestSupport.player(helper, "scale_owner");
        long setupStart = System.nanoTime();
        var bases = new ArrayList<Base>();
        bases.add(place(helper, owner, new BlockPos(0, 20, 0), BIG, true));
        for (int i = 0; i < MAINFRAMES; i++) {
            bases.add(place(helper, ai.ezfox.cccloud.ingame.InGameTestSupport.player(helper, "scale_owner_" + i), new BlockPos(0, 32 + 6 * i, 0), DEVICES, false));
        }
        report.addProperty("placeMs", (System.nanoTime() - setupStart) / 1_000_000);
        var big = bases.getFirst();

        var phase = new Phase[]{Phase.LINK};
        var phaseStart = new long[]{System.nanoTime()};
        var ticks = new int[]{0};
        var clients = new ArrayList<Client>();
        var load = new LuaLoad(bases);
        var loadThread = new Thread[1];
        var counters = new long[4];
        var groupToggles = new int[]{0};

        var state = new State();
        // Not helper.onEachTick: that registers one task per tick up to max_ticks, which the GameTest framework walks
        // every tick (hundreds of thousands of entries, more than everything measured here). One tick listener instead.
        ACTIVE = () -> {
            for (var base : bases) {
                if (!base.mainframe.isRemoved()) base.mainframe.energy().set(base.mainframe.energy().capacity());
                for (var relay : base.relays) if (!relay.isRemoved()) relay.fillForTest();
            }
            ticks[0]++;
            long now = System.nanoTime();
            switch (phase[0]) {
                case LINK -> {
                    if (ticks[0] < 3) return;
                    long t0 = System.nanoTime();
                    for (int i = 0; i < bases.size(); i++) link(helper, bases.get(i), i == 0);
                    report.addProperty("linkMs", (System.nanoTime() - t0) / 1_000_000);
                    phase[0] = Phase.READY;
                    phaseStart[0] = now;
                }
                case READY -> {
                    // every device routed, every card on the dashboard
                    boolean ready = true;
                    int sections = 0;
                    for (var base : bases) {
                        var result = NetworkService.result(base.id());
                        int routed = 0;
                        if (result != null) for (var r : result.routes().values()) if (r.connected()) routed++;
                        int expected = base.lua.size() + base.machines.size() + base.redstone.size();
                        int published = WebHub.INSTANCE.sectionList(base.id()).size();
                        int wanted = base.lua.size() + base.machines.size() + RsDevices.sections(base.id()).size();
                        sections += published;
                        if (routed < expected || published < wanted || RsDevices.sections(base.id()).isEmpty()) ready = false;
                        for (var m : base.machines) if (m.deviceCard() == null) ready = false;
                    }
                    if (!ready) {
                        if (now - phaseStart[0] > 120_000_000_000L) throw new IllegalStateException("bases not ready after 120 s");
                        return;
                    }
                    report.addProperty("readyMs", (now - setupStart) / 1_000_000);
                    report.addProperty("sections", sections);
                    phase[0] = Phase.STATIC;
                }
                case STATIC -> {
                    addRules(helper, big, report);
                    measureStatic(helper, owner, bases, report);
                    for (var base : bases) base.token = CloudWebServer.issueTestToken(server, base.id(), base.owner.getUUID());
                    CloudWebServer.setMaxConnectionsPerIpForTest(BROWSERS + 8);
                    for (int i = 0; i < BROWSERS; i++) {
                        int mf = i < BIG_BROWSERS ? 0 : 1 + (i - BIG_BROWSERS) % Math.max(1, MAINFRAMES);
                        var base = bases.get(Math.min(mf, bases.size() - 1));
                        connect(clients, new Client(mf), base.token, base.id().toString());
                    }
                    WebHub.FLUSH_EVERY_TICK = false; // the web server's own 100 ms flush, like on a real server
                    phase[0] = Phase.CONNECT;
                    phaseStart[0] = now;
                }
                case CONNECT -> {
                    int done = 0;
                    for (var c : clients) if (c.snapshotMs >= 0 || c.closed != null) done++;
                    if (done < clients.size()) {
                        if (now - phaseStart[0] > 120_000_000_000L) throw new IllegalStateException("only " + done + " snapshots after 120 s");
                        return;
                    }
                    var snapshots = new JsonObject();
                    var bigMs = new ArrayList<Double>();
                    var otherMs = new ArrayList<Double>();
                    int bigChars = 0, otherChars = 0;
                    for (var c : clients) {
                        if (c.mainframe == 0) {
                            bigMs.add((double) c.snapshotMs);
                            bigChars = Math.max(bigChars, c.snapshotChars);
                        } else {
                            otherMs.add((double) c.snapshotMs);
                            otherChars = Math.max(otherChars, c.snapshotChars);
                        }
                    }
                    snapshots.add("bigMs", stats(bigMs));
                    snapshots.addProperty("bigChars", bigChars);
                    snapshots.add("otherMs", stats(otherMs));
                    snapshots.addProperty("otherChars", otherChars);
                    report.add("snapshots", snapshots);
                    phase[0] = Phase.BASELINE;
                    phaseStart[0] = now;
                    startMeasuring();
                }
                case BASELINE -> {
                    if (now - phaseStart[0] < BASELINE_S * 1_000_000_000L) return;
                    report.add("idle", stopMeasuring());
                    loadThread[0] = new Thread(load, "cccloud-scale-lua");
                    loadThread[0].setDaemon(true);
                    loadThread[0].start();
                    for (var c : clients) {
                        c.chars.set(0);
                        c.messages.set(0);
                        c.valueDeltas.set(0);
                    }
                    phase[0] = Phase.LOAD;
                    phaseStart[0] = now;
                    startMeasuring();
                }
                case LOAD -> {
                    toggleGroups(big, groupToggles);
                    if (now - phaseStart[0] < LOAD_S * 1_000_000_000L) return;
                    var result = stopMeasuring();
                    double seconds = (now - phaseStart[0]) / 1e9;
                    result.addProperty("seconds", round(seconds));
                    result.addProperty("luaSetsPerSecond", Math.round(load.sets.get() / seconds));
                    result.addProperty("systemLoadAverage", round(java.lang.management.ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage()));
                    result.addProperty("cpus", Runtime.getRuntime().availableProcessors());
                    result.add("browsers", bandwidth(clients, seconds));
                    report.add("load", result);
                    report.add("memory", memory(bases));
                    finish(helper, report, clients);
                    if (HOLD_S > 0) {
                        writeSession(big, report);
                        phase[0] = Phase.HOLD;
                        phaseStart[0] = now;
                    } else {
                        phase[0] = Phase.DONE;
                        cleanup(helper, bases, forced, clients, load);
                        state.finished = true;
                    }
                }
                case HOLD -> {
                    toggleGroups(big, groupToggles);
                    if (now - phaseStart[0] < HOLD_S * 1_000_000_000L) return;
                    phase[0] = Phase.DONE;
                    cleanup(helper, bases, forced, clients, load);
                    state.finished = true;
                }
                case DONE -> {
                }
            }
        };
        STATE = state;
        CLEANUP = () -> cleanup(helper, bases, forced, clients, load);
        helper.startSequence()
            .thenWaitUntil(() -> {
                if (!state.finished) throw helper.assertionException("scale test running");
            })
            .thenExecute(() -> {
                if (state.failure != null) helper.fail(state.failure);
                verdict(helper, report, clients);
            });
    }

    /** Every 5 s "All on"/"All off" on two redstone groups (a scene or a player). */
    private static void toggleGroups(Base big, int[] counter) {
        if (++counter[0] % 100 != 0) return;
        var sections = RsDevices.sections(big.id());
        if (sections.isEmpty()) return;
        int n = counter[0] / 100;
        for (int k = 0; k < 2; k++) {
            var section = sections.get((n * 2 + k) % sections.size());
            section.handleAction(n % 2 == 0 ? RsCards.ALL_ON : RsCards.ALL_OFF, new JsonPrimitive(true), null, "scale", false);
        }
    }

    /** 84 threshold rules on Lua gauges and 16 schedules on the big mainframe. */
    private static void addRules(GameTestHelper helper, Base big, JsonObject report) {
        var server = helper.getLevel().getServer();
        int ok = 0;
        var errors = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < RULES; i++) {
            String json;
            if (i < RULES - 16) {
                var uplink = big.lua.get(i % big.lua.size());
                json = "{\"name\":\"Hot " + i + "\",\"cooldown\":30,\"when\":{\"type\":\"widget\",\"uplinkId\":\"" + uplink.getUplinkId()
                    + "\",\"widgetId\":\"g0\",\"op\":\">\",\"value\":" + (500 + i) + "},\"then\":[{\"type\":\"alert\",\"text\":\"hot " + i + "\"}]}";
            } else {
                json = "{\"name\":\"Every minute " + i + "\",\"when\":{\"type\":\"interval\",\"minutes\":1},\"then\":[{\"type\":\"alert\",\"text\":\"tick\"}]}";
            }
            var edit = ai.ezfox.cccloud.rules.RuleService.set(server, big.id(), JsonParser.parseString(json));
            if (edit.error() == null) ok++;
            else errors.merge(edit.error(), 1, Integer::sum);
        }
        report.addProperty("rulesSaved", ok);
        if (!errors.isEmpty()) report.addProperty("ruleErrors", errors.toString());
    }

    /** Sizes and build times of everything that is built from the whole base at once. */
    private static void measureStatic(GameTestHelper helper, ServerPlayer owner, List<Base> bases, JsonObject report) {
        var big = bases.getFirst();
        var server = helper.getLevel().getServer();
        // Network recompute (all mainframes; it runs on every topology change and every 10 s)
        var network = new ArrayList<Double>();
        for (int i = 0; i < 10; i++) {
            NetworkService.markDirty();
            long t = System.nanoTime();
            NetworkService.tick(server);
            network.add((System.nanoTime() - t) / 1e6);
        }
        var net = new JsonObject();
        net.add("recomputeMs", stats(network));
        var full = WebHub.INSTANCE.networkForTest(big.id(), true);
        if (full != null) {
            net.addProperty("bigJsonChars", Json.GSON.toJson(full).length());
            net.addProperty("bigNodes", full.getAsJsonArray("nodes").size());
            net.addProperty("bigLinks", full.getAsJsonArray("links").size());
        }
        // Linking one more device on the big network (the Link Card's range check)
        var linkCheck = new ArrayList<Double>();
        for (int i = 0; i < 5; i++) {
            var uplink = big.lua.get(i);
            NetworkService.markDirty();
            long t = System.nanoTime();
            NetworkService.checkLink(big.mainframe, helper.getLevel(), uplink.getBlockPos(), NetworkService.deviceType(uplink), uplink.getUplinkId().toString(), uplink.wired());
            linkCheck.add((System.nanoTime() - t) / 1e6);
        }
        net.add("linkCheckMs", stats(linkCheck));
        report.add("network", net);

        // In-game screens: the view each one is opened with (open packet ≤ OPEN_VIEW, updates ≤ MAX_VIEW)
        var views = new JsonObject();
        view(views, "mainframe", () -> ai.ezfox.cccloud.gui.GuiViews.mainframe(big.mainframe, owner, null));
        view(views, "uplinkLua", () -> ai.ezfox.cccloud.gui.GuiViews.uplink(big.lua.getFirst(), owner, null));
        view(views, "uplinkMachine", () -> ai.ezfox.cccloud.gui.GuiViews.uplink(big.machines.getFirst(), owner, null));
        if (!big.relays.isEmpty()) view(views, "relay", () -> ai.ezfox.cccloud.gui.GuiViews.relay(big.relays.getFirst(), owner, null));
        view(views, "redstoneDevice", () -> ai.ezfox.cccloud.rsdevice.RsScreens.view(big.redstone.getFirst(), owner, null));
        views.addProperty("limitOpen", ai.ezfox.cccloud.gui.CloudMenu.OPEN_VIEW);
        views.addProperty("limitUpdate", ai.ezfox.cccloud.gui.CloudMenu.MAX_VIEW);
        report.add("views", views);

        // What a browser and a tablet (in-game payloads ≤ MAX_DATA) get on login
        var capture = new CaptureSession(big.id(), false);
        long t = System.nanoTime();
        WebHub.INSTANCE.addSession(capture);
        long snapshotNanos = System.nanoTime() - t;
        WebHub.INSTANCE.removeSession(capture);
        var snap = new JsonObject();
        int chars = capture.messages.stream().mapToInt(String::length).sum();
        snap.addProperty("chars", chars);
        snap.addProperty("messages", capture.messages.size());
        snap.addProperty("buildMs", round(snapshotNanos / 1e6));
        var tablet = new CaptureSession(big.id(), true);
        t = System.nanoTime();
        WebHub.INSTANCE.addSession(tablet);
        snap.addProperty("tabletCallerMs", round((System.nanoTime() - t) / 1e6));
        long until = System.currentTimeMillis() + 30_000;
        while (tablet.messages.isEmpty() && System.currentTimeMillis() < until) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                break;
            }
        }
        WebHub.INSTANCE.removeSession(tablet);
        snap.addProperty("tabletMessages", tablet.messages.size());
        snap.addProperty("largestMessage", tablet.messages.stream().mapToInt(String::length).max().orElse(0));
        snap.addProperty("tabletLimit", ai.ezfox.cccloud.ingame.InGamePayloads.MAX_DATA);
        report.add("bigSnapshot", snap);

        // Long-term statistics
        var history = new JsonObject();
        var store = ai.ezfox.cccloud.history.HistoryData.get(server).store();
        history.addProperty("bigBytes", store.bytes(big.id()));
        long total = 0;
        for (var base : bases) total += store.bytes(base.id());
        history.addProperty("allBytes", total);
        long s = System.nanoTime();
        var saved = store.save();
        history.addProperty("encodeMs", round((System.nanoTime() - s) / 1e6));
        long savedChars = 0;
        for (var m : saved.values()) for (var v : m.values()) savedChars += v.length();
        history.addProperty("encodedChars", savedChars);
        report.add("history", history);
    }

    private static void view(JsonObject views, String name, java.util.function.Supplier<String> build) {
        var times = new ArrayList<Double>();
        String view = "";
        for (int i = 0; i < 5; i++) {
            long t = System.nanoTime();
            view = build.get();
            times.add((System.nanoTime() - t) / 1e6);
        }
        var json = new JsonObject();
        json.addProperty("chars", view.length());
        json.addProperty("buildMs", stats(times).get("p50").getAsDouble());
        views.add(name, json);
    }

    /** A hub session that only records what it is sent (snapshot size without a socket). */
    static final class CaptureSession implements ai.ezfox.cccloud.web.HubSession {
        final UUID mainframeId;
        final boolean inGame;
        final List<String> messages = java.util.Collections.synchronizedList(new ArrayList<>());

        CaptureSession(UUID mainframeId, boolean inGame) {
            this.mainframeId = mainframeId;
            this.inGame = inGame;
        }

        @Override
        public boolean inGame() {
            return inGame;
        }

        @Override
        public UUID mainframeId() {
            return mainframeId;
        }

        @Override
        public ai.ezfox.cccloud.web.AuthStore.@Nullable Device device() {
            return null;
        }

        @Override
        public ai.ezfox.cccloud.access.Role role() {
            return ai.ezfox.cccloud.access.Role.OWNER;
        }

        @Override
        public void send(List<String> list) {
            messages.addAll(list);
        }

        @Override
        public void close(int code, String reason) {
        }

        @Override
        public boolean countsAsBrowser() {
            return false;
        }
    }

    private static JsonObject bandwidth(List<Client> clients, double seconds) {
        var json = new JsonObject();
        var bigKbps = new ArrayList<Double>();
        var otherKbps = new ArrayList<Double>();
        var bigMsgs = new ArrayList<Double>();
        var bigValues = new ArrayList<Double>();
        for (var c : clients) {
            double kbps = c.chars.get() / 1024.0 / seconds;
            if (c.mainframe == 0) {
                bigKbps.add(kbps);
                bigMsgs.add(c.messages.get() / seconds);
                bigValues.add(c.valueDeltas.get() / seconds);
            } else {
                otherKbps.add(kbps);
            }
        }
        json.add("bigKiBps", stats(bigKbps));
        json.add("bigMessagesPerSecond", stats(bigMsgs));
        json.add("bigValuesPerSecond", stats(bigValues));
        json.add("otherKiBps", stats(otherKbps));
        return json;
    }

    private static JsonObject memory(List<Base> bases) {
        var json = new JsonObject();
        var rt = Runtime.getRuntime();
        System.gc();
        json.addProperty("heapUsedMiB", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        json.addProperty("heapMaxMiB", rt.maxMemory() / (1024 * 1024));
        int devices = 0;
        for (var base : bases) devices += base.lua.size() + base.machines.size() + base.redstone.size();
        json.addProperty("devices", devices);
        return json;
    }

    private static void finish(GameTestHelper helper, JsonObject report, List<Client> clients) {
        int closed = 0;
        for (var c : clients) if (c.closed != null) closed++;
        report.addProperty("browsersDisconnected", closed);
        String text = Json.GSON.toJson(report);
        CCCloud.LOGGER.info("[cccloud] SCALE TEST REPORT {}", text);
        try {
            var path = Path.of(System.getProperty("cccloud.scaleTest.report", "scaletest-report.json"));
            Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
            CCCloud.LOGGER.info("[cccloud] SCALE TEST report written to {}", path.toAbsolutePath());
        } catch (java.io.IOException e) {
            CCCloud.LOGGER.warn("[cccloud] SCALE TEST could not write the report: {}", e.toString());
        }
    }

    /** For tools/e2e/scale-render.mjs: where the dashboard is and a token of the big mainframe. */
    private static void writeSession(Base big, JsonObject report) {
        var json = new JsonObject();
        json.addProperty("url", "http://127.0.0.1:" + CloudWebServer.port() + "/");
        json.addProperty("mainframeId", big.id().toString());
        json.addProperty("token", big.token);
        json.addProperty("name", big.mainframe.getDisplayName());
        json.addProperty("holdSeconds", HOLD_S);
        try {
            var path = Path.of(System.getProperty("cccloud.scaleTest.session", "scaletest-session.json"));
            Files.writeString(path, Json.GSON.toJson(json), StandardCharsets.UTF_8);
            CCCloud.LOGGER.info("[cccloud] SCALE TEST holding for {} s, session in {}", HOLD_S, path.toAbsolutePath());
        } catch (java.io.IOException e) {
            CCCloud.LOGGER.warn("[cccloud] SCALE TEST could not write the session: {}", e.toString());
        }
    }

    private static void cleanup(GameTestHelper helper, List<Base> bases, List<ChunkPos> forced, List<Client> clients, LuaLoad load) {
        load.running = false;
        throttle = false;
        WebHub.FLUSH_EVERY_TICK = Boolean.getBoolean("cccloud.gametest");
        for (var c : clients) {
            var ws = c.ws;
            if (ws != null) ws.abort();
        }
        CloudWebServer.setMaxConnectionsPerIpForTest(CloudWebServer.MAX_CONNECTIONS_PER_IP);
        var level = helper.getLevel();
        for (var base : bases) {
            ai.ezfox.cccloud.rules.RuleService.forget(level.getServer(), base.id());
            // devices first (plates pop off when their support goes), the mainframe last
            for (var pos : base.blocks.reversed()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        MACHINES.clear();
        for (var chunk : forced) level.setChunkForced(chunk.x(), chunk.z(), false);
    }

    private static void verdict(GameTestHelper helper, JsonObject report, List<Client> clients) {
        var problems = new ArrayList<String>();
        if (report.get("browsersDisconnected").getAsInt() > 0) {
            for (var c : clients) if (c.closed != null) problems.add("browser of mainframe " + c.mainframe + " closed: " + c.closed);
        }
        var views = report.getAsJsonObject("views");
        for (var e : views.entrySet()) {
            if (e.getValue() instanceof JsonObject v && v.get("chars").getAsInt() > ai.ezfox.cccloud.gui.CloudMenu.MAX_VIEW) {
                problems.add("screen view " + e.getKey() + " is over the update limit: " + v.get("chars"));
            }
        }
        var snap = report.getAsJsonObject("bigSnapshot");
        if (snap.get("largestMessage").getAsInt() > ai.ezfox.cccloud.ingame.InGamePayloads.MAX_DATA) {
            problems.add("the big snapshot does not fit an in-game payload: " + snap.get("largestMessage"));
        }
        var load = report.getAsJsonObject("load");
        if (load.getAsJsonObject("browsers").getAsJsonObject("bigValuesPerSecond").get("avg").getAsDouble() <= 0) problems.add("no value deltas reached the browsers");
        if (Boolean.getBoolean("cccloud.scaleTest.strict")) {
            double avg = load.getAsJsonObject("cccloudMs").get("avg").getAsDouble();
            double max = load.getAsJsonObject("cccloudMs").get("max").getAsDouble();
            if (avg > 1.0) problems.add("cccloud costs " + avg + " ms/tick on average (target < 1.0)");
            if (max > 5.0) problems.add("a tick spent " + max + " ms in cccloud (target ≤ 5)");
        }
        if (problems.isEmpty()) helper.succeed();
        else helper.fail(String.join("; ", problems));
    }
}
