package ai.ezfox.cccloud.code;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.api.ScriptHost;
import ai.ezfox.cccloud.code.engine.Budget;
import ai.ezfox.cccloud.code.engine.Diagnostic;
import ai.ezfox.cccloud.code.engine.EngineInstance;
import ai.ezfox.cccloud.code.engine.ScriptEngine;
import ai.ezfox.cccloud.code.engine.ScriptError;
import ai.ezfox.cccloud.code.runtime.Limits;
import ai.ezfox.cccloud.code.runtime.ScriptRuntime;
import ai.ezfox.cccloud.code.world.WorldAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The runtime: CPU and memory limits, the cloud API against a fake world, events, timers, storage, quotas, stopping. */
class ScriptRuntimeTest {
    private final TestScripts scripts = new TestScripts();

    @AfterEach
    void close() {
        scripts.close();
    }

    // ---- limits ------------------------------------------------------------------------------------------------------

    @Test
    void infiniteLoopIsStoppedWithAClearError() throws InterruptedException {
        var run = scripts.runFast("local n = 0\nwhile true do n = n + 1 end");
        assertTrue(run.await(10_000), "the loop was not stopped");
        assertNotNull(run.error());
        assertTrue(run.error().getMessage().contains("ran for more than 120 ms without pausing"), run.error().getMessage());
        assertEquals(2, run.error().line());
        assertEquals(ScriptRuntime.Status.ERROR, run.runtime().status());
        // 120 ms at 50 ms per second: the share pauses it in between (twice when the watchdog is on time). Counted, not timed:
        // on a loaded machine a late check can see 50 ms and more at once, so fewer pauses and less wall time are fine.
        assertTrue(run.runtime().sharePauses() >= 1, "the per-second share never paused it");
        // about 120 ms of CPU; the watchdog notices a little late on a loaded machine (CI runners: up to a few hundred ms),
        // so the bound only proves it was stopped within a few slices instead of running on
        assertTrue(run.runtime().cpuTotalNanos() < 600_000_000L, "used " + run.runtime().cpuTotalNanos() / 1e6 + " ms");
    }

    @Test
    void pcallCannotCatchTheAbort() throws InterruptedException {
        var run = scripts.runFast("""
            local caught = 0
            while true do
              pcall(function() while true do end end)
              caught = caught + 1
              if caught > 3 then cloud.log("escaped") end
            end
            """);
        assertTrue(run.await(10_000));
        assertNotNull(run.error());
        assertTrue(run.error().getMessage().contains("without pausing"), run.error().getMessage());
        assertFalse(run.logText().contains("escaped"), run.logText());
        var x = scripts.runFast("xpcall(function() while true do end end, function(e) while true do end end)");
        assertTrue(x.await(10_000));
        assertTrue(x.error().getMessage().contains("without pausing") || x.error().getMessage().contains("error"), x.error().getMessage());
    }

    @Test
    void busyScriptGetsItsShareOnly() throws InterruptedException {
        var limits = new Limits(16, 8, 32 * 1024, 30, 100_000, 20, 32, 64, 1024, 20, 200, 1L << 40, 1L << 30, 20, 32, 6, 5, 0.2);
        var run = scripts.run("while true do end", limits, TestScripts.snapshot(1, 20, true));
        Thread.sleep(2500);
        long used = run.runtime().cpuTotalNanos();
        // ~30 ms per started second (3 windows); the time is wall-clock while running, so a loaded machine (other test
        // JVMs) adds to it: the bound only has to show the throttle (unthrottled it would be ~2500 ms)
        assertTrue(used < 250_000_000L, "about 30 ms per started second, used " + used / 1e6 + " ms");
        assertTrue(used > 30_000_000L, "but it does run, used " + used / 1e6 + " ms");
        assertTrue(run.runtime().cpuAverage() <= 80, "average " + run.runtime().cpuAverage());
        run.runtime().stop("stopped");
        assertTrue(run.await(2000), "stop ends a busy script promptly");
        assertNull(run.error(), "a person stopping it is not an error");
        assertEquals("stopped", run.runtime().endReason());
    }

    @Test
    void stringBombIsStopped() throws InterruptedException {
        // '..' builds lazy ropes in Cobalt (no bytes copied); upper() makes the bytes, so this really allocates
        var run = scripts.run("local s = 'x' .. string.rep('y', 1000)\nfor i = 1, 40 do s = (s .. s):upper() end");
        assertTrue(run.await(10_000));
        assertNotNull(run.error());
        String m = run.error().getMessage();
        assertTrue(m.contains("allocated more than") || m.contains("memory"), m);
    }

    @Test
    void tableBombIsStoppedEvenWhenItPauses() throws InterruptedException {
        var run = scripts.run("""
            local t = {}
            for i = 1, 1e8 do
              t[i] = {i}
              if i % 20000 == 0 then cloud.sleep(0) end
            end
            """);
        assertTrue(run.await(30_000), run.logText());
        assertNotNull(run.error());
        String m = run.error().getMessage();
        assertTrue(m.contains("MB of memory") || m.contains("allocated more than"), m);
    }

    private ScriptError.Kind kind(String source) throws InterruptedException {
        return kindOf(scripts.run(source));
    }

    private ScriptError.Kind kindFast(String source) throws InterruptedException {
        return kindOf(scripts.runFast(source));
    }

    private ScriptError.Kind kindOf(TestScripts.Run run) throws InterruptedException {
        assertTrue(run.await(30_000), run.logText());
        assertNotNull(run.error(), run.logText());
        assertTrue(run.error().getMessage().startsWith("test:"), "chunk:line: detail, got " + run.error().getMessage());
        return run.error().kind();
    }

    @Test
    void errorsSayWhatKindTheyAre() throws InterruptedException {
        assertEquals(ScriptError.Kind.SYNTAX, kind("if then"));
        assertEquals(ScriptError.Kind.RUNTIME, kind("error('boom')"));
        assertEquals(ScriptError.Kind.RUNTIME, kind("local t = nil\nprint(t.x)"));
        assertEquals(ScriptError.Kind.CPU_LIMIT, kindFast("while true do end"));
        assertEquals(ScriptError.Kind.MEMORY_LIMIT, kind("local t = {} for i = 1, 1e8 do t[i] = {i} end"));
        assertEquals(ScriptError.Kind.STACK_OVERFLOW, kind("local function f() return 1 + f() end f()"));
        assertEquals("cpu_limit", ScriptError.Kind.CPU_LIMIT.id());
    }

    /** An engine that must never be asked for an instance: the runtime refuses it first. */
    private static ScriptEngine refusedEngine(ScriptEngine.Preemption preemption, long minimumMemory) {
        return new ScriptEngine() {
            @Override
            public String language() {
                return "wasm";
            }

            @Override
            public Preemption preemption() {
                return preemption;
            }

            @Override
            public long minimumMemory() {
                return minimumMemory;
            }

            @Override
            public java.util.List<Diagnostic> check(String name, String source) {
                return java.util.List.of();
            }

            @Override
            public EngineInstance create(String name, ScriptHost host, Budget budget) {
                throw new AssertionError("the runtime must refuse this engine before creating an instance");
            }
        };
    }

    @Test
    void enginesItCannotHoldAreRefused() throws InterruptedException {
        var abort = scripts.run("x", TestScripts.FAST, TestScripts.snapshot(1, 20, true), refusedEngine(ScriptEngine.Preemption.ABORT, 0));
        assertTrue(abort.await(5000));
        assertEquals(ScriptError.Kind.ENGINE, abort.error().kind());
        assertTrue(abort.error().getMessage().contains("cannot run here yet"), abort.error().getMessage());
        var small = scripts.run("x", TestScripts.FAST, TestScripts.snapshot(1, 20, true), refusedEngine(ScriptEngine.Preemption.SUSPEND, 64L << 20));
        assertTrue(small.await(5000));
        assertEquals(ScriptError.Kind.ENGINE, small.error().kind());
        assertTrue(small.error().getMessage().contains("needs at least 64 MB"), small.error().getMessage());
        var lua = scripts.run("cloud.sleep(5)");
        assertEquals(TestScripts.FAST.memoryBytes(), lua.runtime().memoryBytes(), "the budget hands the per-instance memory cap to the engine");
    }

    @Test
    void sleepingScriptsUseNoCpu() throws InterruptedException {
        var run = scripts.run("for i = 1, 3 do cloud.sleep(0.2) end cloud.log('done')");
        assertTrue(run.await(3000));
        assertNull(run.error());
        assertEquals("finished", run.runtime().endReason());
        assertTrue(run.logText().contains("done"));
    }

    // ---- the API -----------------------------------------------------------------------------------------------------

    @Test
    void readsTheSnapshot() throws InterruptedException {
        var run = scripts.run("""
            local c = cloud.getCard("reactor")
            print(c.title, c.online, #c.widgets, c.widgets[1].id, c.widgets[1].unit)
            print(cloud.getValue("Reactor", "heat"), cloud.getValue("Reactor", "nope"), cloud.getCard("nope"))
            print(cloud.getEnergy(), cloud.getEnergyCapacity(), cloud.getMainframe().percent, cloud.getValue("mainframe", "percent"))
            print(#cloud.getCards(), cloud.getCards()[1].widgets, cloud.getRedstone("door"), cloud.getRedstone("x"))
            print(cloud.getRules()[1].name, cloud.getScenes()[1].confirm, cloud.isOnline(), cloud.isOnline("Reactor"), cloud.getDevices()[1].type)
            print(cloud.script.name, cloud.script.author, cloud.script.version, cloud.gameTime())
            """);
        assertTrue(run.await(5000), run.logText());
        assertNull(run.error(), () -> run.error().getMessage());
        String log = run.logText();
        assertTrue(log.contains("info: Reactor true 4 heat °C"), log);
        assertTrue(log.contains("info: 20 nil nil"), log);
        assertTrue(log.contains("info: 5000 10000 50 50"), log);
        assertTrue(log.contains("info: 1 4 0 nil"), log);
        assertTrue(log.contains("info: Night true true true uplink"), log);
        assertTrue(log.contains("info: test Steve 1 1004"), log);
    }

    @Test
    void actionsGoToTheWorldWithTheDashboardChecks() throws InterruptedException {
        var run = scripts.run("""
            print(cloud.set("Reactor", "active", false))
            print(cloud.press("Reactor", "scram"))
            print(cloud.press("Reactor", "purge"))
            print(cloud.press("Reactor", "purge", {confirm = true}))
            print(cloud.set("Nope", "x", 1))
            print(cloud.getItemCount("Reactor", "minecraft:iron_ingot"))
            print(cloud.runScene("Lockdown"))
            print("scene", cloud.runScene("lockdown", {confirm = true}))
            print(cloud.setRuleEnabled("Night", false), cloud.setEnabled(true))
            local co = coroutine.wrap(function() return cloud.set("Reactor", "active", true) end)
            print("in a coroutine", co())
            """);
        assertTrue(run.await(5000), run.logText());
        assertNull(run.error(), () -> run.error().getMessage());
        String log = run.logText();
        assertTrue(log.contains("info: true\n"), log);
        assertTrue(log.contains("info: false pin_required"), log);
        assertTrue(log.contains("info: false confirm_required"), log);
        assertTrue(log.contains("info: false unknown_card"), log);
        assertTrue(log.contains("info: 42"), log);
        assertEquals(2, log.split("false confirm_required", -1).length - 1, log);
        assertTrue(log.contains("info: scene true"), log);
        assertTrue(log.contains("info: in a coroutine true"), log);
        var actions = scripts.world.actions;
        assertInstanceOf(WorldAction.Set.class, actions.getFirst());
        assertEquals(false, ((WorldAction.Set) actions.getFirst()).value());
        assertEquals(TestScripts.REACTOR, ((WorldAction.Set) actions.getFirst()).cardId());
        assertTrue(actions.stream().noneMatch(a -> a instanceof WorldAction.Press p && p.widgetId().equals("scram")), "PIN widgets never reach the world");
        assertTrue(scripts.world.audits.getFirst().startsWith("script test (by Steve): set Reactor/active = false"), scripts.world.audits.toString());
    }

    @Test
    void scriptsSetACardsDesignWithTheEditorsChecks() throws Exception {
        // load the CSS checks first: class loading is not what the script's short test time budget is about
        ai.ezfox.cccloud.uplink.WidgetFactory.style(java.util.Map.of("css", "border-radius: 4px", "parts", java.util.Map.of("title", "color: red")));
        var run = scripts.run("""
            print(cloud.setStyle("reactor", {bg = "custom", bgColor = "#0b1a2a", parts = {gauge_fill = "color: #4ff0ff"}}))
            print(cloud.setStyle("Reactor"))
            print(cloud.setStyle("Reactor", {}))
            print(cloud.setStyle("Nope", {css = "border-radius: 4px"}))
            print(cloud.setStyle("mainframe", {css = "border-radius: 4px"}))
            for _, bad in ipairs({{parts = {title = "background: url(x)"}}, {parts = {evil = "color: red"}}, {color = "red"}, {css = 1}, {parts = "color: red"}}) do
              local ok, err = pcall(cloud.setStyle, "Reactor", bad)
              print("refused", ok, err)
            end
            """);
        assertTrue(run.await(5000), run.logText());
        assertNull(run.error(), () -> run.error().getMessage());
        String log = run.logText();
        assertTrue(log.contains("info: false unknown_card"), log);
        assertTrue(log.contains("info: false not_supported"), log);
        assertEquals(5, log.split("refused false", -1).length - 1, log);
        assertTrue(log.contains("bad style (parts.title: network)"), log);
        assertTrue(log.contains("bad style (parts.evil: part)"), log);
        assertTrue(log.contains("unknown style key 'color'"), log);
        var styles = scripts.world.actions.stream().filter(a -> a instanceof WorldAction.SetStyle).map(a -> (WorldAction.SetStyle) a).toList();
        assertEquals(3, styles.size(), "nothing refused reaches the world: " + styles);
        assertEquals(TestScripts.REACTOR, styles.getFirst().cardId());
        assertEquals("color: #4ff0ff", styles.getFirst().design().getAsJsonObject("parts").get("gauge_fill").getAsString());
        assertNull(styles.get(1).design(), "no design: the card's own design is removed");
        assertNull(styles.get(2).design(), "an empty table is the same");
        assertTrue(scripts.world.audits.getFirst().contains("set the design of Reactor"), scripts.world.audits.toString());
    }

    @Test
    void refusedActionsAreLoggedAndAuditBursts() throws InterruptedException {
        scripts.world.answer = a -> new Object[]{false, "not_allowed"};
        var run = scripts.run("for i = 1, 10 do cloud.set('Reactor', 'active', i % 2 == 0) end");
        assertTrue(run.await(5000));
        assertTrue(run.logText().contains("refused: not_allowed"), run.logText());
        assertEquals(7, scripts.world.audits.size(), "6 single entries and one summary: " + scripts.world.audits);
        assertTrue(scripts.world.audits.getLast().contains("4 more actions in the last minute"), scripts.world.audits.getLast());
    }

    @Test
    void badCallsAreErrorsAtTheirLine() throws InterruptedException {
        var run = scripts.run("local ok, err = pcall(cloud.on, 'explode', function() end)\nprint(err)\ncloud.every(0.1, print)");
        assertTrue(run.await(5000));
        assertTrue(run.logText().contains("unknown event 'explode'"), run.logText());
        assertNotNull(run.error());
        assertTrue(run.error().getMessage().contains("at least 0.25 seconds"), run.error().getMessage());
        assertEquals(3, run.error().line());
    }

    // ---- events and timers -------------------------------------------------------------------------------------------

    @Test
    void handlersSeeChangesAndCrossings() throws InterruptedException {
        var run = scripts.run("""
            cloud.on("change", {card = "Reactor", widget = "heat"}, function(e) print("change", e.old, e.value, e.title) end)
            cloud.on("above", {card = "Reactor", widget = "heat", value = 80}, function(e)
              print("hot", e.value)
              cloud.set("Reactor", "active", false)
            end)
            cloud.on("change", function(e) if e.widget == "active" then print("active", e.value) end end)
            print("ready")
            """);
        TestScripts.until(() -> run.logText().contains("ready"), 3000, "script ready");
        run.runtime().offer(TestScripts.snapshot(2, 50, true));
        run.runtime().offer(TestScripts.snapshot(3, 85, true));
        TestScripts.until(() -> run.logText().contains("hot 85"), 3000, "above handler");
        run.runtime().offer(TestScripts.snapshot(4, 90, false));
        TestScripts.until(() -> run.logText().contains("active false"), 3000, "change handler without a filter");
        String log = run.logText();
        assertTrue(log.contains("change 20 85 Reactor") || log.contains("change 20 50 Reactor"), log);
        assertEquals(1, log.split("hot ", -1).length - 1, "above fires once per crossing:\n" + log);
        assertTrue(scripts.world.actions.stream().anyMatch(a -> a instanceof WorldAction.Set s && s.value().equals(false)));
        assertFalse(run.runtime().isEnded(), "handlers keep a script running");
        assertEquals(3, run.runtime().handlerCount());
    }

    @Test
    void waitWithTimeout() throws InterruptedException {
        var run = scripts.run("""
            print("timeout", cloud.wait("redstone", {channel = "door"}, 0.2))
            local e = cloud.wait("redstone", {channel = "door"})
            print("level", e.value, e.old, e.channel)
            """);
        TestScripts.until(() -> run.logText().contains("timeout nil"), 3000, "wait timeout");
        var s = TestScripts.snapshot(5, 20, true);
        var changed = new ai.ezfox.cccloud.code.world.WorldSnapshot(s.seq(), s.time(), s.gameTime(), s.mainframe(), s.cards(), s.devices(),
            java.util.Map.of("door", 15), s.rules(), s.scenes());
        Thread.sleep(50);
        run.runtime().offer(TestScripts.snapshot(4, 20, true));
        run.runtime().offer(changed);
        assertTrue(run.await(3000), run.logText());
        assertTrue(run.logText().contains("level 15 0 door"), run.logText());
    }

    @Test
    void timers() throws InterruptedException {
        var run = scripts.run("""
            local n = 0
            local id
            id = cloud.every(0.25, function()
              n = n + 1
              if n == 3 then cloud.off(id) print("every done") end
            end)
            cloud.after(0.3, function() print("after") end)
            local busy = 0
            local slow = cloud.every(0.25, function() busy = busy + 1 cloud.sleep(1) end)
            cloud.after(1.2, function() print("slow ran", busy) cloud.off(slow) end)
            """);
        assertTrue(run.await(5000), run.logText());
        assertNull(run.error(), () -> run.error().getMessage());
        String log = run.logText();
        assertTrue(log.contains("every done"), log);
        assertTrue(log.contains("after"), log);
        assertTrue(log.contains("slow ran 1") || log.contains("slow ran 2"), "a busy every-timer is not started twice: " + log);
        assertEquals("finished", run.runtime().endReason(), "nothing left to do: finished");
    }

    @Test
    void gameTimeTimers() throws InterruptedException {
        var run = scripts.run("cloud.at({gameTime = 1010}, function() print('dusk') end) print('ready')");
        TestScripts.until(() -> run.logText().contains("ready"), 3000, "ready");
        run.runtime().offer(TestScripts.snapshot(2, 20, true)); // 1008
        run.runtime().offer(TestScripts.snapshot(3, 20, true)); // 1012: crossed 1010
        TestScripts.until(() -> run.logText().contains("dusk"), 3000, "game time timer");
        assertTrue(ScriptRuntime.class.getName().length() > 0);
    }

    // ---- storage, quotas, log -----------------------------------------------------------------------------------------

    @Test
    void storage() throws InterruptedException {
        var run = scripts.run("""
            print(cloud.store.set("count", 3), cloud.store.set("cfg", {a = 1, list = {1, 2, 3}}))
            local cfg = cloud.store.get("cfg")
            print(cloud.store.get("count"), cfg.a, #cfg.list, table.concat(cloud.store.keys(), ","))
            print(cloud.store.set("big", string.rep("x", 2000)))
            print(pcall(cloud.store.set, "f", print))
            cloud.store.delete("count")
            print(cloud.store.get("count"))
            """);
        assertTrue(run.await(5000), run.logText());
        String log = run.logText();
        assertTrue(log.contains("info: true true"), log);
        assertTrue(log.contains("info: 3 1 3 cfg,count"), log);
        assertTrue(log.contains("info: false\n"), "over the 1 KB test limit: " + log);
        assertTrue(log.contains("cannot store a function"), log);
        assertTrue(log.contains("info: nil"), log);
        assertNull(run.store().get("count"));
    }

    @Test
    void quotas() throws InterruptedException {
        var run = scripts.run("""
            for i = 1, 64 do cloud.on("change", function() end) end
            print(pcall(cloud.on, "change", function() end))
            for i = 1, 32 do cloud.every(10, function() end) end
            print(pcall(cloud.every, 10, function() end))
            """);
        TestScripts.until(() -> run.logText().contains("too many timers"), 3000, "quota errors");
        assertTrue(run.logText().contains("too many event handlers (at most 64)"), run.logText());
        assertEquals(64, run.runtime().handlerCount());
        assertEquals(32, run.runtime().timerCount());
    }

    @Test
    void logIsRateLimited() throws InterruptedException {
        var run = scripts.run("for i = 1, 100 do print('line', i) end cloud.sleep(1.1) print('after')");
        assertTrue(run.await(5000));
        long lines = run.log().since(0).stream().filter(l -> l.text().startsWith("line")).count();
        assertEquals(20, lines);
        assertTrue(run.logText().contains("80 log line(s) dropped"), run.logText());
    }

    @Test
    void notificationsAreRateLimited() throws InterruptedException {
        var run = scripts.run("for i = 1, 8 do cloud.notify('n' .. i, 'warn') end cloud.toast('t') cloud.push('p', 'crit', 'Title')");
        assertTrue(run.await(5000));
        assertEquals(5, scripts.world.notes.size(), scripts.world.notes.toString());
        assertEquals("BOTH:warn:n1", scripts.world.notes.getFirst());
        assertTrue(run.logText().contains("notification dropped"), run.logText());
    }
}
