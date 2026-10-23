package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.web.CloudWebServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Load test: 50 dashboard clients, 4 uplinks x 32 widgets, every widget updated every tick (through the same code path
 * as Lua's {@code set()}). Reports the average server tick time with idle clients vs. under load.
 */
public final class LoadTest {
    static final int CLIENTS = 50;
    static final int UPLINKS = 4;
    static final int WIDGETS = 32;
    /** Each phase runs this long (wall clock). The GameTest server ticks as fast as it can, so we simulate 20 TPS. */
    static final long PHASE_NANOS = 10_000_000_000L;
    static final long TICK_NANOS = 50_000_000L;

    // Tick timing (server thread only).
    private static boolean listening;
    private static long tickStart;
    private static boolean recording;
    private static long recordedNanos;
    private static int recordedTicks;

    private LoadTest() {
    }

    private static void onTickPre(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
    }

    /** Only ticks on the simulated 20 TPS grid are measured. */
    private static boolean measureThisTick;

    private static void onTickPost(ServerTickEvent.Post event) {
        if (!recording || !measureThisTick) return;
        measureThisTick = false;
        recordedNanos += System.nanoTime() - tickStart;
        recordedTicks++;
    }

    private static double takeAverageMs() {
        double avg = recordedTicks == 0 ? 0 : recordedNanos / 1e6 / recordedTicks;
        recordedNanos = 0;
        recordedTicks = 0;
        return avg;
    }

    private enum Phase { CONNECTING, BASELINE, LOAD, DONE }

    public static void run(GameTestHelper helper) {
        // ./gradlew runGameTestServer -PskipLoadTest: the functional tests stay green regardless of machine load.
        if (Boolean.getBoolean("cccloud.skipLoadTest")) {
            ai.ezfox.cccloud.CCCloud.LOGGER.info("[cccloud] LOAD TEST skipped (-PskipLoadTest)");
            helper.succeed();
            return;
        }
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(LoadTest::onTickPre);
            NeoForge.EVENT_BUS.addListener(LoadTest::onTickPost);
        }
        if (!CloudWebServer.isRunning()) throw helper.assertionException("Dashboard web server is not running (port in use?)");

        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        // 50 browsers: beyond every Bandwidth card, so the mainframe gets a Quantum Core (unlimited browsers).
        mainframe.upgrades().setItem(ai.ezfox.cccloud.upgrade.MainframeUpgrades.CORE_SLOT, new net.minecraft.world.item.ItemStack(ai.ezfox.cccloud.ModRegistry.QUANTUM_CORE.get()));
        CloudGameTests.keepPowered(helper, mainframe);
        var link = new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId());

        var uplinks = new ArrayList<CloudUplinkBlockEntity>();
        var snapshots = new AtomicInteger();
        var messages = new AtomicLong();
        var closed = new AtomicInteger();
        var sockets = new ArrayList<WebSocket>();
        // The mainframe registers itself in BlockEntity#onLoad, which NeoForge runs on the next tick.
        helper.runAfterDelay(2, () -> setup(helper, owner, mainframe, link, uplinks, snapshots, messages, closed, sockets));

        var phase = new Phase[]{Phase.CONNECTING};
        var counter = new int[]{0};
        var phaseStart = new long[]{0};
        var lastSimTick = new long[]{0};
        var results = new double[3];
        var setNanos = new long[]{0};
        var messagesAtLoadStart = new long[]{0};
        helper.onEachTick(() -> {
            // The server ticks faster than 20 TPS here: only the one server tick after a simulated tick (its block entity
            // ticks and global tick handlers, which publish the new values) is measured, like a real 20 TPS tick.
            ai.ezfox.cccloud.network.TickCost.enabled = false;
            switch (phase[0]) {
                case CONNECTING -> {
                    if (snapshots.get() >= CLIENTS) {
                        phase[0] = Phase.BASELINE;
                        counter[0] = 0;
                        takeAverageMs();
                        recording = true;
                        phaseStart[0] = System.nanoTime();
                        ai.ezfox.cccloud.network.TickCost.take();
                    }
                }
                case BASELINE -> {
                    long now = System.nanoTime();
                    if (now - lastSimTick[0] >= TICK_NANOS) {
                        lastSimTick[0] = now;
                        measureThisTick = true;
                    }
                    if (now - phaseStart[0] >= PHASE_NANOS) {
                        results[0] = takeAverageMs();
                        ai.ezfox.cccloud.network.TickCost.take();
                        phase[0] = Phase.LOAD;
                        counter[0] = 0;
                        phaseStart[0] = now;
                        messagesAtLoadStart[0] = messages.get();
                    }
                }
                case LOAD -> {
                    long start = System.nanoTime();
                    if (start - lastSimTick[0] < TICK_NANOS) return; // not a simulated game tick
                    lastSimTick[0] = start;
                    measureThisTick = true;
                    int tick = counter[0];
                    for (var uplink : uplinks) {
                        for (int w = 0; w < WIDGETS; w++) {
                            var widget = uplink.getWidget("g" + w);
                            try {
                                uplink.setValue("g" + w, WidgetFactory.valueFor(widget, (double) ((tick * 7 + w) % 1000)));
                            } catch (WidgetFactory.WidgetException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                    }
                    setNanos[0] += System.nanoTime() - start;
                    counter[0]++;
                    ai.ezfox.cccloud.network.TickCost.enabled = true; // measure the next server tick
                    if (start - phaseStart[0] >= PHASE_NANOS) {
                        recording = false;
                        results[1] = takeAverageMs();
                        results[2] = setNanos[0] / 1e6 / counter[0];
                        // Everything cccloud does on the server thread per simulated tick: set() calls + block entity ticks.
                        var parts = ai.ezfox.cccloud.network.TickCost.takeParts();
                        long partsSum = 0;
                        var breakdown = new StringBuilder();
                        for (int p = 0; p < parts.length; p++) {
                            partsSum += parts[p];
                            breakdown.append(p == 0 ? "" : ", ").append(ai.ezfox.cccloud.network.TickCost.NAMES[p]).append(' ').append(fmt(parts[p] / 1e6 / counter[0])).append(" ms");
                        }
                        double ownCost = results[2] + partsSum / 1e6 / counter[0];
                        ai.ezfox.cccloud.network.TickCost.enabled = false;
                        CCCloud.LOGGER.info("[cccloud] LOAD TEST: per tick: set() {} ms, {}", fmt(results[2]), breakdown);
                        phase[0] = Phase.DONE;
                        long delivered = messages.get() - messagesAtLoadStart[0];
                        CCCloud.LOGGER.info("[cccloud] LOAD TEST: {} clients, {} uplinks x {} widgets updated every tick for {} ticks (simulated 20 TPS, {} s)",
                            CLIENTS, UPLINKS, WIDGETS, counter[0], PHASE_NANOS / 1_000_000_000L);
                        CCCloud.LOGGER.info("[cccloud] LOAD TEST: cccloud server-thread cost {} ms/tick (set() calls {} ms + ticks {} ms); "
                                + "{} messages delivered to clients, {} clients disconnected",
                            fmt(ownCost), fmt(results[2]), fmt(ownCost - results[2]), delivered, closed.get());
                        CCCloud.LOGGER.info("[cccloud] LOAD TEST: whole tick {} ms with idle clients vs {} ms under load (delta {} ms; "
                                + "includes CPU contention from the 50 test clients running in the same JVM)",
                            fmt(results[0]), fmt(results[1]), fmt(results[1] - results[0]));
                        synchronized (sockets) {
                            for (var ws : sockets) ws.abort();
                        }
                        CloudWebServer.setMaxConnectionsPerIpForTest(CloudWebServer.MAX_CONNECTIONS_PER_IP);
                        if (closed.get() > 0) {
                            helper.fail("Clients were disconnected during the load test: " + closed.get());
                        } else if (delivered == 0) {
                            helper.fail("No updates reached the clients");
                        } else if (ownCost > 0.5) {
                            helper.fail("Tick impact too high: " + fmt(ownCost) + " ms/tick (target < 0.5)");
                        } else {
                            helper.succeed();
                        }
                    }
                }
                case DONE -> {
                }
            }
        });
    }

    private static void setup(GameTestHelper helper, net.minecraft.world.entity.player.Player owner,
                              ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity mainframe, MainframeLink link,
                              List<CloudUplinkBlockEntity> uplinks, AtomicInteger snapshots, AtomicLong messages,
                              AtomicInteger closed, List<WebSocket> sockets) {
        BlockPos[] positions = {new BlockPos(0, 1, 0), new BlockPos(4, 1, 0), new BlockPos(0, 1, 4), new BlockPos(4, 1, 4)};
        for (int u = 0; u < UPLINKS; u++) {
            helper.setBlock(positions[u], ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.DOWN));
            var uplink = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(positions[u]));
            uplink.bind(link, owner);
            uplink.setTitle("Load " + u);
            for (int w = 0; w < WIDGETS; w++) {
                try {
                    uplink.putWidget(WidgetFactory.create(WidgetType.GAUGE, "g" + w, "Gauge " + w, Map.of("min", 0.0, "max", 1000.0)));
                } catch (WidgetFactory.WidgetException e) {
                    throw helper.assertionException(e.getMessage());
                }
            }
            uplinks.add(uplink);
        }

        String token = CloudWebServer.issueTestToken(helper.getLevel().getServer(), mainframe.getMainframeId(), owner.getUUID());
        if (token == null) throw helper.assertionException("Could not issue a test token");
        CloudWebServer.setMaxConnectionsPerIpForTest(CLIENTS + 4);

        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        URI uri = URI.create("ws://127.0.0.1:" + CloudWebServer.port() + "/ws/client");
        for (int i = 0; i < CLIENTS; i++) {
            http.newWebSocketBuilder().buildAsync(uri, new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket ws) {
                    ws.sendText("{\"t\":\"auth\",\"token\":\"" + token + "\"}", true);
                    ws.request(1);
                }

                private final StringBuilder partial = new StringBuilder();

                @Override
                public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                    // Large messages (the snapshot with the network map) arrive in several fragments.
                    partial.append(data);
                    if (!last) {
                        ws.request(1);
                        return null;
                    }
                    String text = partial.toString();
                    partial.setLength(0);
                    if (text.startsWith("{\"t\":\"snapshot\"")) {
                        // Protocol check over a real socket: the snapshot's mainframe object carries canControl and power.
                        var snapshot = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
                        var mf = snapshot.getAsJsonObject("mainframe");
                        if (mf.has("canControl") && mf.get("canControl").getAsBoolean() && snapshot.has("layout")) {
                            if (snapshots.incrementAndGet() == 1) ai.ezfox.cccloud.CCCloud.LOGGER.info("[cccloud] LOAD TEST: first snapshot mainframe = {}", mf);
                        } else {
                            ai.ezfox.cccloud.CCCloud.LOGGER.error("[cccloud] LOAD TEST: snapshot without mainframe.canControl/layout: {}", text);
                        }
                    } else if (text.startsWith("{\"t\":\"ping\"")) ws.sendText("{\"t\":\"pong\"}", true);
                    messages.incrementAndGet();
                    ws.request(1);
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                    closed.incrementAndGet();
                    CCCloud.LOGGER.warn("[cccloud] Load test client closed: {} {}", code, reason);
                    return null;
                }
            }).thenAccept(ws -> {
                synchronized (sockets) {
                    sockets.add(ws);
                }
            });
        }

    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    static List<String> describe() {
        return List.of();
    }
}
