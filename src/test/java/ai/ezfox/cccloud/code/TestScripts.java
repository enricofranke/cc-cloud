package ai.ezfox.cccloud.code;

import ai.ezfox.cccloud.code.engine.ScriptError;
import ai.ezfox.cccloud.code.lua.LuaEngine;
import ai.ezfox.cccloud.code.runtime.Limits;
import ai.ezfox.cccloud.code.runtime.ScriptLog;
import ai.ezfox.cccloud.code.runtime.ScriptRuntime;
import ai.ezfox.cccloud.code.runtime.ScriptScheduler;
import ai.ezfox.cccloud.code.runtime.ScriptStore;
import ai.ezfox.cccloud.code.world.ScriptIdentity;
import ai.ezfox.cccloud.code.world.ScriptWorld;
import ai.ezfox.cccloud.code.world.WorldAction;
import ai.ezfox.cccloud.code.world.WorldSnapshot;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** A script runtime without Minecraft: a fake world that records actions, a fresh scheduler, short limits. */
final class TestScripts implements AutoCloseable {
    static final UUID MAINFRAME = UUID.fromString("00000000-0000-4000-8000-00000000aaaa");
    static final UUID STEVE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    static final String REACTOR = "0a1b2c3d-0000-4000-8000-000000000001";

    /** Short limits so tests are quick: 50 ms/s, 120 ms per resume, 4 MB heap. */
    static final Limits FAST = new Limits(16, 8, 32 * 1024, 50, 120, 20, 32, 64, 1024, 20, 128 * 1024, 32 * 1024, 200, 1L << 30, 4L << 20, 20, 32, 6, 5, 0.2, 2000, 64 * 1024);
    /** FAST with a CPU share no test script reaches (1 s per second, 5 s per resume): the default of {@link #run(String)}. */
    static final Limits ROOMY_CPU = new Limits(16, 8, 32 * 1024, 1000, 5000, 20, 32, 64, 1024, 20, 128 * 1024, 32 * 1024, 200, 1L << 30, 4L << 20, 20, 32, 6, 5, 0.2, 2000, 64 * 1024);

    final ScriptScheduler scheduler = new ScriptScheduler(2);
    final FakeWorld world = new FakeWorld();
    final List<ScriptRuntime> started = new ArrayList<>();

    static final class FakeWorld implements ScriptWorld {
        final List<WorldAction> actions = Collections.synchronizedList(new ArrayList<>());
        final List<String> notes = Collections.synchronizedList(new ArrayList<>());
        final List<String> audits = Collections.synchronizedList(new ArrayList<>());
        volatile Function<WorldAction, Object[]> answer = a -> a instanceof WorldAction.ItemCount ? new Object[]{42L} : new Object[]{true};

        @Override
        public CompletableFuture<@Nullable Object[]> act(ScriptIdentity who, WorldAction action) {
            actions.add(action);
            // answered from another thread, like the server thread would
            return CompletableFuture.supplyAsync(() -> answer.apply(action), CompletableFuture.delayedExecutor(5, TimeUnit.MILLISECONDS));
        }

        @Override
        public boolean notify(ScriptIdentity who, Channel channel, String text, String level, @Nullable String title) {
            notes.add(channel + ":" + level + ":" + text);
            return true;
        }

        @Override
        public void audit(ScriptIdentity who, String text) {
            audits.add(text);
        }
    }

    record Run(ScriptRuntime runtime, ScriptLog log, ScriptStore store, CountDownLatch ended) {
        boolean await(long ms) throws InterruptedException {
            return ended.await(ms, TimeUnit.MILLISECONDS);
        }

        String logText() {
            var sb = new StringBuilder();
            for (var line : log.since(0)) sb.append(line.level()).append(": ").append(line.text()).append('\n');
            return sb.toString();
        }

        @Nullable ScriptError error() {
            return runtime.endError();
        }
    }

    /**
     * A script with {@link #ROOMY_CPU}: tests of everything but the CPU limit, so a loaded machine (the runtime counts
     * wall-clock time while a script runs) cannot stop them with "ran for more than 120 ms".
     */
    Run run(String source) {
        return run(source, ROOMY_CPU, snapshot(1, 20.0, true));
    }

    /** A script with {@link #FAST}: for the tests of the CPU limit itself (50 ms per second, 120 ms per resume). */
    Run runFast(String source) {
        return run(source, FAST, snapshot(1, 20.0, true));
    }

    Run run(String source, Limits limits, WorldSnapshot snapshot) {
        return run(source, limits, snapshot, new LuaEngine(ZoneId::systemDefault));
    }

    Run run(String source, Limits limits, WorldSnapshot snapshot, ai.ezfox.cccloud.code.engine.ScriptEngine engine) {
        var log = new ScriptLog(limits.logKept(), limits.logLinesPerSecond(), System::currentTimeMillis);
        var store = new ScriptStore(limits.storageBytes());
        var ended = new CountDownLatch(1);
        var who = new ScriptIdentity(MAINFRAME, "s1", "test", STEVE, "Steve", STEVE, "Steve");
        var runtime = new ScriptRuntime(who, 1, source, engine, scheduler, world, limits, log, store,
            (r, reason, error) -> ended.countDown(), ZoneId::systemDefault, snapshot);
        started.add(runtime);
        runtime.start();
        return new Run(runtime, log, store, ended);
    }

    /** A mainframe with one card "Reactor": heat (gauge), active (toggle), scram (PIN button), purge (confirm button). */
    static WorldSnapshot snapshot(long seq, double heat, boolean active) {
        var widgets = List.of(
            new WorldSnapshot.Widget("heat", "gauge", "Heat", heat, "°C", 0.0, 100.0, false, false),
            new WorldSnapshot.Widget("active", "toggle", "Active", active, null, null, null, false, false),
            new WorldSnapshot.Widget("scram", "button", "SCRAM", null, null, null, null, true, false),
            new WorldSnapshot.Widget("purge", "button", "Purge", null, null, null, null, false, true));
        var card = new WorldSnapshot.Card(REACTOR, "Reactor", true, "lua", widgets, false);
        var mainframe = new WorldSnapshot.Mainframe(MAINFRAME.toString(), "Base", true, true, 5000, 10000, 12);
        return new WorldSnapshot(seq, System.currentTimeMillis(), 1000 + seq * 4, mainframe, List.of(card),
            List.of(new WorldSnapshot.Device(REACTOR, "uplink", "Reactor", "online")), Map.of("door", 0),
            List.of(new WorldSnapshot.RuleInfo("aa11bb22", "Night", true, "time", 3, "ok")),
            List.of(new WorldSnapshot.SceneInfo("cc33dd44", "Lockdown", false, true, false)));
    }

    static void until(BooleanSupplier condition, long ms, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(5);
        }
    }

    @Override
    public void close() {
        for (var r : started) r.stop("test");
        scheduler.close();
    }
}
