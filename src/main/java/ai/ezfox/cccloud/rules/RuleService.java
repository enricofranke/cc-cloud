package ai.ezfox.cccloud.rules;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.bridge.BridgeCore;
import ai.ezfox.cccloud.bridge.BridgeSection;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.DashboardSection;
import ai.ezfox.cccloud.uplink.Op;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Game side of the v0.5 rules: storage ({@link RuleData}), editing (dashboard, Lua), the evaluation step every
 * {@link #INTERVAL} ticks on the server thread and the actions. Rules only run while their mainframe is loaded and
 * online; v0.10 schedules are also evaluated for unloaded mainframes, so an occurrence due then is skipped and logged.
 * Every run is logged (server log + per-mainframe run log shown in the dashboard).
 */
public final class RuleService {
    public static final int INTERVAL = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** mainframe → parsed rules (cache of RuleData). */
    private static final Map<UUID, List<Rule>> RULES = new HashMap<>();
    private static final Map<UUID, RuleEngine> ENGINES = new HashMap<>();
    /** GameTests: a manual clock per mainframe (schedules), so tests never move the clock of other mainframes. */
    private static final Map<UUID, java.util.function.LongSupplier> TEST_CLOCKS = new java.util.concurrent.ConcurrentHashMap<>();
    /** GameTests: an in-game time of day per mainframe (the world's clock is shared by every test). */
    private static final Map<UUID, java.util.function.LongSupplier> TEST_DAY_TIMES = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile java.time.@Nullable ZoneId testZone;
    private static @Nullable String badZone;
    /** Push hook (v0.5 D): rule runs with {@code notify} and alert actions. */
    private static volatile Consumer<Notification> notifier = n -> {
    };

    public record Notification(UUID mainframeId, String title, String text, String level) {
    }

    public record Edit(@Nullable String error, @Nullable String id) {
    }

    private RuleService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> load(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            RULES.clear();
            ENGINES.clear();
            TEST_CLOCKS.clear();
            TEST_DAY_TIMES.clear();
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
    }

    public static void setNotifier(Consumer<Notification> consumer) {
        notifier = consumer;
    }

    private static void load(MinecraftServer server) {
        RULES.clear();
        ENGINES.clear();
        var data = RuleData.get(server);
        for (var e : data.rules().entrySet()) {
            var list = new ArrayList<Rule>();
            for (String json : e.getValue()) {
                try {
                    var result = RuleCodec.parse(JsonParser.parseString(json), BridgeCore.maxCraftAmount());
                    if (result.rule() != null) list.add(result.rule());
                    else CCCloud.LOGGER.warn("[cccloud] Dropping invalid rule of mainframe {}: {}", e.getKey(), result.error());
                } catch (RuntimeException ex) {
                    CCCloud.LOGGER.warn("[cccloud] Dropping unreadable rule of mainframe {}", e.getKey());
                }
            }
            RULES.put(e.getKey(), list);
            // A fresh engine with the stored last occurrences: its first evaluation catches up one missed run (v0.10).
            ENGINES.put(e.getKey(), new RuleEngine(data.fired(e.getKey())));
            publish(server, e.getKey());
        }
    }

    /** GameTests: drops a mainframe's run state as if the server had restarted (the stored occurrences stay). */
    public static void restartForTest(MinecraftServer server, UUID mainframeId) {
        ENGINES.put(mainframeId, new RuleEngine(RuleData.get(server).fired(mainframeId)));
    }

    /** GameTests: the clock schedules of one mainframe use (null = the real clock again). */
    public static void setClockForTest(UUID mainframeId, java.util.function.@Nullable LongSupplier clock) {
        if (clock == null) TEST_CLOCKS.remove(mainframeId);
        else TEST_CLOCKS.put(mainframeId, clock);
    }

    /** GameTests: the in-game time of day one mainframe's rules see (null = the overworld clock again). */
    public static void setDayTimeForTest(UUID mainframeId, java.util.function.@Nullable LongSupplier dayTime) {
        if (dayTime == null) TEST_DAY_TIMES.remove(mainframeId);
        else TEST_DAY_TIMES.put(mainframeId, dayTime);
    }

    /** In-game time of day (0–23999) from the overworld clock (or a test's own). */
    static long dayTime(MinecraftServer server, UUID mainframeId) {
        var test = TEST_DAY_TIMES.get(mainframeId);
        if (test != null) return Math.floorMod(test.getAsLong(), 24000L);
        return Math.floorMod(server.overworld().getOverworldClockTime(), 24000L);
    }

    /** GameTests: a fixed time zone for weekday schedules (null = the config). */
    public static void setZoneForTest(java.time.@Nullable ZoneId zone) {
        testZone = zone;
    }

    static long now(UUID mainframeId) {
        var clock = TEST_CLOCKS.get(mainframeId);
        return clock != null ? clock.getAsLong() : System.currentTimeMillis();
    }

    /** The time zone of weekday schedules: config {@code schedules.timezone}, else the server's system zone. */
    public static java.time.ZoneId zone() {
        var test = testZone;
        if (test != null) return test;
        String id = CloudConfig.get(CloudConfig.TIMEZONE).trim();
        if (id.isEmpty()) return java.time.ZoneId.systemDefault();
        try {
            return java.time.ZoneId.of(id);
        } catch (java.time.DateTimeException e) {
            if (!id.equals(badZone)) {
                badZone = id;
                CCCloud.LOGGER.warn("[cccloud] Unknown time zone '{}' in schedules.timezone, using the server's zone {}", id, java.time.ZoneId.systemDefault());
            }
            return java.time.ZoneId.systemDefault();
        }
    }

    static RuleEngine.Settings settings() {
        return new RuleEngine.Settings(zone(), CloudConfig.get(CloudConfig.RUN_MISSED), CloudConfig.get(CloudConfig.MISSED_WINDOW_HOURS) * 3_600_000L,
            CloudConfig.get(CloudConfig.MIN_INTERVAL_MINUTES));
    }

    /** Schedules (clock-triggered rules) a mainframe may have: config {@code maxSchedulesPerMainframe}. */
    public static int scheduleLimit() {
        return CloudConfig.get(CloudConfig.MAX_SCHEDULES);
    }

    /** Rules a mainframe may have: its Memory upgrade (or the base limit), capped by {@code maxRulesPerMainframe}. */
    public static int limit(UUID mainframeId) {
        return Math.min(CloudConfig.get(CloudConfig.MAX_RULES), ai.ezfox.cccloud.upgrade.Upgrades.effects(mainframeId).rules());
    }

    public static List<Rule> rules(UUID mainframeId) {
        return RULES.getOrDefault(mainframeId, List.of());
    }

    // ---- evaluation --------------------------------------------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        long tickCost = ai.ezfox.cccloud.network.TickCost.start();
        try {
            if (server.getTickCount() % INTERVAL != 0 || RULES.isEmpty() || !CloudConfig.get(CloudConfig.RULES_ENABLED)) return;
            var settings = settings();
            for (var e : RULES.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                var mainframe = CloudNetwork.mainframe(e.getKey());
                boolean loaded = mainframe != null && !mainframe.isRemoved();
                boolean available = loaded && mainframe.isOnline();
                // Unloaded or offline: only schedules matter (their due occurrences are skipped and logged).
                if (!available && e.getValue().stream().noneMatch(Rule::isSchedule)) continue;
                var engine = ENGINES.computeIfAbsent(e.getKey(), k -> new RuleEngine(RuleData.get(server).fired(k)));
                List<RuleEngine.Run> runs;
                try {
                    runs = engine.tick(e.getValue(), available ? world(mainframe) : unavailableWorld(server, e.getKey(), loaded ? "offline" : "unloaded"), settings);
                } catch (RuntimeException ex) {
                    CCCloud.LOGGER.error("[cccloud] Error while evaluating the rules of mainframe {}", e.getKey(), ex);
                    continue;
                }
                if (engine.takeFiredDirty()) saveFired(server, e.getKey(), engine);
                if (!runs.isEmpty()) {
                    String name = loaded ? mainframe.getDisplayName() : displayName(e.getKey());
                    for (var run : runs) record(server, e.getKey(), name, run);
                    publish(server, e.getKey());
                }
            }
        } finally {
            ai.ezfox.cccloud.network.TickCost.end(ai.ezfox.cccloud.network.TickCost.GLOBAL, tickCost);
        }
    }

    /** Name of a mainframe that is not loaded (the hub remembers it), else its id. */
    private static String displayName(UUID mainframeId) {
        var info = WebHub.INSTANCE.info(mainframeId);
        return info != null && !info.name().isEmpty() ? info.name() : mainframeId.toString();
    }

    private static void saveFired(MinecraftServer server, UUID mainframeId, RuleEngine engine) {
        var ids = new java.util.HashSet<String>();
        for (var rule : rules(mainframeId)) ids.add(rule.id());
        var fired = new HashMap<>(engine.fired());
        fired.keySet().retainAll(ids);
        RuleData.get(server).setFired(mainframeId, fired);
    }

    /** The world of a mainframe that is not loaded or not online: only the clocks are known. */
    private static RuleEngine.World unavailableWorld(MinecraftServer server, UUID mainframeId, String reason) {
        return new RuleEngine.World() {
            @Override
            public @Nullable Double widget(String uplinkId, String widgetId) {
                return null;
            }

            @Override
            public @Nullable Long items(String uplinkId, String item) {
                return null;
            }

            @Override
            public @Nullable Double powerPercent() {
                return null;
            }

            @Override
            public boolean online(String uplinkId) {
                return false;
            }

            @Override
            public long dayTime() {
                return RuleService.dayTime(server, mainframeId);
            }

            @Override
            public long now() {
                return RuleService.now(mainframeId);
            }

            @Override
            public @Nullable String run(Rule rule, Rule.Action action) {
                return "offline";
            }

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public String unavailable() {
                return reason;
            }
        };
    }

    private static void record(MinecraftServer server, UUID mainframeId, String mainframeName, RuleEngine.Run run) {
        String result = run.ok() ? "ok" : run.error();
        RuleData.get(server).addLog(mainframeId, new RuleData.LogEntry(run.time(), run.ruleId(), run.name(), result, run.manual(), run.skipped()));
        if (run.skipped()) {
            CCCloud.LOGGER.info("[cccloud] Schedule '{}' of {} skipped: the mainframe is {}", run.name(), mainframeName, result.substring("skipped_".length()));
            return;
        }
        CCCloud.LOGGER.info("[cccloud] Rule '{}' of {} ran{} ({} action(s)): {}", run.name(), mainframeName, run.manual() ? " manually" : "",
            run.actions(), result);
        if (run.push()) notifier.accept(new Notification(mainframeId, mainframeName, "Rule '" + run.name() + "': " + result,
            run.ok() ? "info" : "warn"));
    }

    /** The {@link RuleEngine.World} of a loaded mainframe. */
    static RuleEngine.World world(CloudMainframeBlockEntity mainframe) {
        UUID id = mainframe.getMainframeId();
        return new RuleEngine.World() {
            @Override
            public @Nullable Double widget(String uplinkId, String widgetId) {
                var value = WebHub.INSTANCE.widgetValue(id, uplinkId, widgetId);
                if (value == null || !value.isJsonPrimitive()) return null;
                var p = value.getAsJsonPrimitive();
                if (p.isNumber()) return p.getAsDouble();
                if (p.isBoolean()) return p.getAsBoolean() ? 1.0 : 0.0;
                return null;
            }

            @Override
            public @Nullable Long items(String uplinkId, String item) {
                var core = storage(id, uplinkId);
                if (core == null || !core.isConnected()) return null;
                var row = core.getItem(item);
                return row == null ? 0L : row.count();
            }

            @Override
            public @Nullable Double powerPercent() {
                long capacity = mainframe.energy().capacity();
                return capacity <= 0 ? null : 100.0 * mainframe.energy().energy() / capacity;
            }

            @Override
            public boolean online(String uplinkId) {
                return WebHub.INSTANCE.uplinkOnline(id, uplinkId);
            }

            @Override
            public long dayTime() {
                var level = mainframe.getLevel();
                return level == null || level.getServer() == null ? -1 : RuleService.dayTime(level.getServer(), id);
            }

            @Override
            public long now() {
                return RuleService.now(id);
            }

            @Override
            public @Nullable String run(Rule rule, Rule.Action action) {
                return RuleService.run(mainframe, rule, action);
            }
        };
    }

    private static @Nullable DashboardSection section(UUID mainframeId, String uplinkId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uplinkId);
        } catch (IllegalArgumentException e) {
            return null;
        }
        var section = CloudNetwork.section(uuid);
        if (section == null || section.getLink() == null || !section.getLink().id().equals(mainframeId)) return null;
        return section;
    }

    private static @Nullable BridgeCore storage(UUID mainframeId, String uplinkId) {
        var section = section(mainframeId, uplinkId);
        if (section instanceof BridgeSection bridge) return bridge.host().bridgeCore();
        if (section instanceof CloudUplinkBlockEntity uplink && uplink.storageBridge() != null) return uplink.storageBridge().bridgeCore();
        return null;
    }

    /** Runs one action with the same validation as a human action; user = "rule:<name>", never with a PIN. */
    static @Nullable String run(CloudMainframeBlockEntity mainframe, Rule rule, Rule.Action action) {
        if (action instanceof Rule.SceneAction scene) {
            return ai.ezfox.cccloud.scene.SceneService.startFromRule(mainframe, scene.sceneId(), rule.name());
        }
        return runAction(mainframe, action, "rule:" + rule.name(), false);
    }

    /**
     * Runs one action (rules and scene steps) with the same validation as a human action. The caller has checked the
     * rights; {@code pinVerified} = the mainframe PIN was entered for this run (PIN widgets refuse otherwise).
     */
    public static @Nullable String runAction(CloudMainframeBlockEntity mainframe, Rule.Action action, String user, boolean pinVerified) {
        UUID id = mainframe.getMainframeId();
        switch (action) {
            case Rule.AlertAction alert -> {
                WebHub.INSTANCE.publishAlert(id, null, alert.text(), alert.level()); // also pushed (WebHub.onAlert)
                return null;
            }
            case Rule.DashboardAction dashboard -> {
                mainframe.setEnabled(dashboard.enabled());
                return null;
            }
            case Rule.RedstoneAction redstone -> {
                var section = section(id, redstone.uplinkId());
                if (section == null) return "unknown_uplink";
                var value = new JsonObject();
                value.addProperty("op", "set_output");
                value.addProperty("side", redstone.side());
                value.addProperty("value", redstone.value());
                return result(section.handleAction("redstone", value, new Op.SetOutput(redstone.side(), redstone.value()), user, pinVerified));
            }
            case Rule.WidgetAction widget -> {
                var section = section(id, widget.uplinkId());
                if (section == null) return "unknown_uplink";
                if (section instanceof ai.ezfox.cccloud.scene.ScenesSection) return "not_interactive"; // scenes run through the scene action
                // PIN widgets stay human-only (a scene press carries the PIN check of its presser)
                if (!pinVerified && WebHub.INSTANCE.widgetNeedsPin(id, widget.uplinkId(), widget.widgetId())) return "pin_required";
                return result(section.handleAction(widget.widgetId(), widget.value(), null, user, pinVerified));
            }
            case Rule.CraftAction craft -> {
                var section = section(id, craft.uplinkId());
                if (section == null) return "unknown_uplink";
                String search = WebHub.INSTANCE.firstWidgetOfType(id, craft.uplinkId(), "item_search");
                if (search == null) return "unknown_widget";
                var value = new JsonObject();
                value.addProperty("op", "craft");
                value.addProperty("name", craft.item());
                value.addProperty("count", craft.amount());
                long amount = Math.min(craft.amount(), BridgeCore.maxCraftAmount());
                return result(section.handleAction(search, value, new Op.Craft(craft.item(), amount), user, pinVerified));
            }
            case Rule.SceneAction scene -> {
                return "invalid_action"; // scene steps never run scenes (SceneCodec refuses them)
            }
        }
    }

    /** Done → its error; still running (craft planning) → counted as started. */
    private static @Nullable String result(java.util.concurrent.CompletableFuture<ai.ezfox.cccloud.web.ActionResult> future) {
        var r = future.getNow(null);
        return r == null ? null : r.error();
    }

    // ---- editing (dashboard, Lua) ------------------------------------------------------------------------------------

    /** Creates or replaces a rule (validated JSON). */
    public static Edit set(MinecraftServer server, UUID mainframeId, com.google.gson.@Nullable JsonElement json) {
        var parsed = RuleCodec.parse(json, BridgeCore.maxCraftAmount());
        if (parsed.rule() == null) return new Edit("invalid_rule:" + parsed.error(), null);
        var rule = parsed.rule();
        if (rule.when() instanceof Rule.IntervalTrigger t && t.minutes() < CloudConfig.get(CloudConfig.MIN_INTERVAL_MINUTES)) {
            return new Edit("invalid_rule:the server allows intervals of " + CloudConfig.get(CloudConfig.MIN_INTERVAL_MINUTES) + " minutes or more", null);
        }
        for (var action : rule.actions()) {
            if (action instanceof Rule.WidgetAction w && ai.ezfox.cccloud.scene.SceneService.isScenesSection(mainframeId, w.uplinkId())) {
                return new Edit("invalid_rule:use the scene action to run a scene", null);
            }
            if (action instanceof Rule.SceneAction sc) {
                var scene = ai.ezfox.cccloud.scene.SceneService.scene(mainframeId, sc.sceneId());
                if (scene == null) return new Edit("invalid_rule:unknown scene " + sc.sceneId(), null);
                if (ai.ezfox.cccloud.scene.SceneService.needsPin(mainframeId, scene)) return new Edit("pin_widget", null); // rules never enter a PIN
            }
        }
        var list = new ArrayList<>(rules(mainframeId));
        int index = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(rule.id())) index = i;
        if (rule.isSchedule()) {
            long schedules = list.stream().filter(r -> r.isSchedule() && !r.id().equals(parsed.rule().id())).count();
            if (schedules >= scheduleLimit()) return new Edit("too_many_schedules", null);
        }
        if (index < 0) {
            if (list.size() >= limit(mainframeId)) return new Edit("too_many", null);
            if (rule.id().isEmpty()) rule = rule.withId(newId(list));
            list.add(rule);
        } else {
            list.set(index, rule);
            var engine = ENGINES.get(mainframeId);
            if (engine != null) engine.retain(list.stream().filter(r -> !r.id().equals(parsed.rule().id())).map(Rule::id).toList()); // new state
        }
        // PIN widgets are never rule targets.
        for (var action : rule.actions()) {
            if (action instanceof Rule.WidgetAction w && WebHub.INSTANCE.widgetNeedsPin(mainframeId, w.uplinkId(), w.widgetId())) return new Edit("pin_widget", null);
        }
        save(server, mainframeId, list);
        return new Edit(null, rule.id());
    }

    public static @Nullable String delete(MinecraftServer server, UUID mainframeId, String ruleId) {
        var list = new ArrayList<>(rules(mainframeId));
        if (!list.removeIf(r -> r.id().equals(ruleId))) return "unknown_rule";
        save(server, mainframeId, list);
        return null;
    }

    public static @Nullable String setEnabled(MinecraftServer server, UUID mainframeId, String ruleId, boolean enabled) {
        var list = new ArrayList<>(rules(mainframeId));
        for (int i = 0; i < list.size(); i++) {
            if (!list.get(i).id().equals(ruleId)) continue;
            if (list.get(i).enabled() == enabled) return null;
            list.set(i, list.get(i).withEnabled(enabled));
            save(server, mainframeId, list);
            return null;
        }
        return "unknown_rule";
    }

    /** "Run now": runs the actions once (needs the mainframe loaded and online). */
    public static @Nullable String runNow(MinecraftServer server, UUID mainframeId, String ruleId) {
        var rule = rules(mainframeId).stream().filter(r -> r.id().equals(ruleId)).findFirst().orElse(null);
        if (rule == null) return "unknown_rule";
        var mainframe = CloudNetwork.mainframe(mainframeId);
        if (mainframe == null || !mainframe.isOnline()) return "offline";
        var run = ENGINES.computeIfAbsent(mainframeId, k -> new RuleEngine(RuleData.get(server).fired(k))).runNow(rule, world(mainframe));
        if (run == null) return "rate_limited";
        record(server, mainframeId, mainframe.getDisplayName(), run);
        publish(server, mainframeId);
        return run.error();
    }

    /** The mainframe was broken. */
    public static void forget(MinecraftServer server, UUID mainframeId) {
        RULES.remove(mainframeId);
        ENGINES.remove(mainframeId);
        TEST_CLOCKS.remove(mainframeId);
        TEST_DAY_TIMES.remove(mainframeId);
        RuleData.get(server).remove(mainframeId);
    }

    private static void save(MinecraftServer server, UUID mainframeId, List<Rule> list) {
        RULES.put(mainframeId, List.copyOf(list));
        var json = new ArrayList<String>();
        for (var rule : list) json.add(RuleCodec.toJson(rule).toString());
        RuleData.get(server).setRules(mainframeId, json);
        var engine = ENGINES.get(mainframeId);
        if (engine != null) {
            engine.retain(list.stream().map(Rule::id).toList());
            saveFired(server, mainframeId, engine);
        }
        publish(server, mainframeId);
    }

    private static String newId(List<Rule> existing) {
        while (true) {
            byte[] raw = new byte[4];
            RANDOM.nextBytes(raw);
            String id = HexFormat.of().formatHex(raw);
            if (existing.stream().noneMatch(r -> r.id().equals(id))) return id;
        }
    }

    /** {@code {rules:[rule + lastRun/lastResult/runs], log:[{time,rule,name,result,manual}], limit}}. */
    public static JsonObject document(MinecraftServer server, UUID mainframeId) {
        var json = new JsonObject();
        var array = new JsonArray();
        var engine = ENGINES.get(mainframeId);
        for (var rule : rules(mainframeId)) {
            var r = RuleCodec.toJson(rule);
            var state = engine == null ? null : engine.state(rule.id());
            if (state != null && state.lastRun() != 0) r.addProperty("lastRun", state.lastRun());
            else r.add("lastRun", com.google.gson.JsonNull.INSTANCE);
            if (state != null && state.lastResult() != null) r.addProperty("lastResult", state.lastResult());
            else r.add("lastResult", com.google.gson.JsonNull.INSTANCE);
            r.addProperty("runs", state == null ? 0 : state.runs());
            if (state != null && state.nextAt() != 0 && (rule.when() instanceof Rule.IntervalTrigger || rule.when() instanceof Rule.ScheduleTrigger)) {
                r.addProperty("nextRun", state.nextAt());
            } else if (rule.when() instanceof Rule.ScheduleTrigger t) {
                r.addProperty("nextRun", ScheduleMath.next(t, now(mainframeId), zone()));
            } else {
                r.add("nextRun", com.google.gson.JsonNull.INSTANCE);
            }
            long fired = state != null && state.lastFire() != 0 ? state.lastFire() : RuleData.get(server).fired(mainframeId).getOrDefault(rule.id(), 0L);
            if (fired != 0) r.addProperty("lastFire", fired);
            else r.add("lastFire", com.google.gson.JsonNull.INSTANCE);
            array.add(r);
        }
        json.add("rules", array);
        var log = new JsonArray();
        for (var entry : RuleData.get(server).log(mainframeId)) {
            var e = new JsonObject();
            e.addProperty("time", entry.time());
            e.addProperty("rule", entry.ruleId());
            e.addProperty("name", entry.name());
            e.addProperty("result", entry.result());
            e.addProperty("manual", entry.manual());
            if (entry.skipped()) e.addProperty("skipped", true);
            log.add(e);
        }
        json.add("log", log);
        json.addProperty("limit", limit(mainframeId));
        var schedules = new JsonObject();
        schedules.addProperty("count", rules(mainframeId).stream().filter(Rule::isSchedule).count());
        schedules.addProperty("limit", scheduleLimit());
        schedules.addProperty("minInterval", CloudConfig.get(CloudConfig.MIN_INTERVAL_MINUTES));
        schedules.addProperty("timezone", zone().getId());
        json.add("schedules", schedules);
        return json;
    }

    private static void publish(MinecraftServer server, UUID mainframeId) {
        WebHub.INSTANCE.publishRules(mainframeId, document(server, mainframeId));
    }
}
