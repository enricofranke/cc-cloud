package ai.ezfox.cccloud.code.game;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.code.engine.Diagnostic;
import ai.ezfox.cccloud.code.engine.ScriptEngine;
import ai.ezfox.cccloud.code.engine.ScriptError;
import ai.ezfox.cccloud.code.lua.CobaltCheck;
import ai.ezfox.cccloud.code.lua.LuaEngine;
import ai.ezfox.cccloud.code.runtime.Limits;
import ai.ezfox.cccloud.code.runtime.ScriptLog;
import ai.ezfox.cccloud.code.runtime.ScriptRuntime;
import ai.ezfox.cccloud.code.runtime.ScriptScheduler;
import ai.ezfox.cccloud.code.runtime.ScriptStore;
import ai.ezfox.cccloud.code.store.CodeData;
import ai.ezfox.cccloud.code.store.ScriptBook;
import ai.ezfox.cccloud.code.store.ScriptRecord;
import ai.ezfox.cccloud.code.web.CodeViewer;
import ai.ezfox.cccloud.code.world.ScriptIdentity;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.rules.RuleService;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * Game side of Cloud Scripts: the saved scripts of every mainframe ({@link CodeData}), their runs, and what connects
 * the runs to the world. Server thread unless a method says otherwise.
 *
 * <ul>
 * <li>Rights: a run acts with its author's live rights (whoever saved the running version), capped by the live rights
 * of whoever started it (for autostart: who switched autostart on). Checked again for every action
 * ({@link GameWorld}) and every second: an author or starter without {@code code.edit} stops the run.</li>
 * <li>Snapshots: every {@link #SNAPSHOT_TICKS} ticks the server part of a snapshot is taken here and the cards are
 * built on a script worker, once per rights form (with or without {@code view.positions}).</li>
 * <li>Stops: the redstone kill switch ({@code kill_switch}), the mainframe going offline or being unloaded stop every
 * script of it within a tick; the dashboard switch does not. Autostart scripts start again once the mainframe is back,
 * and after a server restart.</li>
 * </ul>
 */
public final class CodeService {
    public static final int SNAPSHOT_TICKS = 4;
    static final int CHECK_TICKS = 20;
    static final int STATUS_TICKS = 10;
    static final int LOG_TICKS = 5;
    /** Stop reasons after which an autostart script starts again by itself. */
    static final Set<String> AUTO_REASONS = Set.of("kill_switch", "offline", "unloaded", "server_stopping");
    private static final SecureRandom RANDOM = new SecureRandom();

    record Key(UUID mainframeId, String scriptId) {
    }

    /** What the server knows about one saved script besides the record: its log, storage, the current run. */
    static final class Slot {
        final Key key;
        final ScriptLog log;
        final ScriptStore store;
        @Nullable ScriptRuntime runtime;
        String status = "stopped";
        @Nullable String reason;
        @Nullable ScriptError lastError;
        long lastErrorTime;
        long startedAt;
        @Nullable String startedBy;
        int runningVersion;
        /** Start again when the mainframe can run it (autostart scripts after a server start, the kill switch, …). */
        boolean autoPending = true;
        /** Rights form of the run's snapshots. */
        volatile boolean positions;
        long pushedSeq;
        final Set<CodeViewer> watchers = new LinkedHashSet<>();
        /** Compile diagnostics of a version (from a save), so a start of a broken version is refused at once. */
        int checkedVersion;
        List<Diagnostic> diagnostics = List.of();

        Slot(Key key, Limits limits, Map<String, String> stored) {
            this.key = key;
            this.log = new ScriptLog(limits.logKept(), limits.logLinesPerSecond(), System::currentTimeMillis);
            this.store = new ScriptStore(limits.storageBytes());
            this.store.load(stored);
        }

        boolean running() {
            var r = runtime;
            return r != null && !r.isEnded();
        }
    }

    private static @Nullable MinecraftServer server;
    private static @Nullable ScriptScheduler scheduler;
    private static @Nullable GameWorld world;
    private static @Nullable String vmProblem;
    private static final ScriptEngine ENGINE = new LuaEngine(RuleService::zone);
    private static final ScriptEngine WASM = new ai.ezfox.cccloud.code.wasm.WasmEngine(CodeService::limits);
    private static final Map<UUID, ScriptBook> BOOKS = new HashMap<>();
    private static final Map<Key, Slot> SLOTS = new LinkedHashMap<>();
    private static final Map<UUID, Set<CodeViewer>> LISTENERS = new HashMap<>();
    private static final Map<UUID, Long> SEQ = new HashMap<>();
    private static final Map<UUID, AtomicBoolean> BUILDING = new ConcurrentHashMap<>();
    /** Mainframes whose script list changed (a code_status push is due). */
    private static final Set<UUID> DIRTY = new LinkedHashSet<>();
    /** Mainframes whose saved scripts changed (written to CodeData at the end of the tick). */
    private static final Set<UUID> SAVE = new LinkedHashSet<>();
    private static long ticks;

    private static final ScriptRuntime.Listener LISTENER = new ScriptRuntime.Listener() {
        @Override
        public void ended(ScriptRuntime runtime, String reason, @Nullable ScriptError error) {
            var w = world;
            if (w != null) w.later(() -> CodeService.ended(runtime, reason, error));
        }

        @Override
        public void changed(ScriptRuntime runtime) {
            var w = world;
            if (w != null) w.later(() -> DIRTY.add(runtime.identity().mainframeId()));
        }
    };

    private CodeService() {
    }

    // ---- life cycle --------------------------------------------------------------------------------------------------

    static void started(MinecraftServer srv) {
        clear();
        server = srv;
        vmProblem = CobaltCheck.problem();
        if (vmProblem != null) {
            CCCloud.LOGGER.warn("[cccloud] Cloud Scripts are off: {} (Cobalt from {})", vmProblem, CobaltCheck.origin());
        } else {
            // with CC: Tweaked installed this names the one Cobalt both mods share
            CCCloud.LOGGER.info("[cccloud] Cloud Scripts run Lua on Cobalt from {}", CobaltCheck.origin());
        }
        scheduler = new ScriptScheduler(CloudConfig.get(CloudConfig.CLOUD_SCRIPT_THREADS));
        world = new GameWorld(srv);
        var limits = limits();
        for (var e : CodeData.get(srv).scripts().entrySet()) {
            BOOKS.put(e.getKey(), new ScriptBook(e.getValue()));
            for (var record : e.getValue()) slot(e.getKey(), record, limits);
        }
    }

    static void stopping() {
        for (var slot : new ArrayList<>(SLOTS.values())) {
            if (slot.running()) stop(slot, "server_stopping");
        }
        flushStores(true);
        save();
        var w = world;
        if (w != null) w.drain();
        var s = scheduler;
        if (s != null) s.close();
    }

    static void stopped() {
        clear();
    }

    private static void clear() {
        var s = scheduler;
        if (s != null) s.close();
        scheduler = null;
        world = null;
        server = null;
        BOOKS.clear();
        SLOTS.clear();
        LISTENERS.clear();
        SEQ.clear();
        BUILDING.clear();
        DIRTY.clear();
        SAVE.clear();
        ticks = 0;
    }

    /** Cloud Scripts may run: switched on in the config and a working Lua VM. Any thread. */
    public static boolean enabled() {
        return CloudConfig.get(CloudConfig.CLOUD_SCRIPTS_ENABLED) && vmProblem == null;
    }

    public static Limits limits() {
        return Limits.DEFAULT.withConfig(CloudConfig.get(CloudConfig.CLOUD_SCRIPTS_PER_MAINFRAME), CloudConfig.get(CloudConfig.CLOUD_SCRIPTS_RUNNING),
            CloudConfig.get(CloudConfig.CLOUD_SCRIPT_CPU_MS), CloudConfig.get(CloudConfig.CLOUD_SCRIPT_CPU_MS_PER_RESUME), CloudConfig.get(CloudConfig.CLOUD_SCRIPT_VERSIONS),
            CloudConfig.get(CloudConfig.CLOUD_SCRIPT_TIMERS), CloudConfig.get(CloudConfig.CLOUD_SCRIPT_HANDLERS), CloudConfig.get(CloudConfig.CLOUD_SCRIPT_STORAGE),
            CloudConfig.get(CloudConfig.CLOUD_SCRIPT_LOG_LINES), CloudConfig.get(CloudConfig.CLOUD_SCRIPT_WASM_BYTES), CloudConfig.get(CloudConfig.CLOUD_SCRIPT_WASM_SOURCE));
    }

    static ScriptEngine engine() {
        return ENGINE;
    }

    /** The engine of a script language, or null for one this server cannot run. */
    static @Nullable ScriptEngine engine(String language) {
        return switch (language) {
            case "lua" -> ENGINE;
            case ai.ezfox.cccloud.code.wasm.WasmEngine.LANGUAGE -> WASM;
            default -> null;
        };
    }

    /** The WebAssembly engine, shared with Cloud Computers (programs started from the shell). */
    public static ScriptEngine wasm() {
        return WASM;
    }

    /** The Lua engine, shared with Cloud Computers. */
    public static ScriptEngine lua() {
        return ENGINE;
    }

    /** The script threads while the server runs (shared with Cloud Computers), else null. */
    public static @Nullable ScriptScheduler scheduler() {
        return scheduler;
    }

    /** Why Lua cannot run on this server (an incompatible Cobalt), or null. */
    public static @Nullable String vmProblem() {
        return vmProblem;
    }

    /** The mainframe was broken for good: its scripts stop and are deleted. */
    public static void forget(MinecraftServer srv, UUID mainframeId) {
        for (var slot : slots(mainframeId)) {
            if (slot.running()) stop(slot, "deleted");
            SLOTS.remove(slot.key);
        }
        BOOKS.remove(mainframeId);
        LISTENERS.remove(mainframeId);
        SEQ.remove(mainframeId);
        BUILDING.remove(mainframeId);
        DIRTY.remove(mainframeId);
        SAVE.remove(mainframeId);
        CodeData.get(srv).remove(mainframeId);
    }

    // ---- books and slots ---------------------------------------------------------------------------------------------

    static ScriptBook book(UUID mainframeId) {
        return BOOKS.computeIfAbsent(mainframeId, k -> new ScriptBook(List.of()));
    }

    static Slot slot(UUID mainframeId, ScriptRecord record, Limits limits) {
        return SLOTS.computeIfAbsent(new Key(mainframeId, record.id()), k -> new Slot(k, limits, record.store()));
    }

    static @Nullable Slot slot(UUID mainframeId, String scriptId) {
        return SLOTS.get(new Key(mainframeId, scriptId));
    }

    static void removeSlot(Slot slot) {
        SLOTS.remove(slot.key);
    }

    static List<Slot> slots(UUID mainframeId) {
        var list = new ArrayList<Slot>();
        for (var slot : SLOTS.values()) if (slot.key.mainframeId().equals(mainframeId)) list.add(slot);
        return list;
    }

    static int running(UUID mainframeId) {
        int n = 0;
        for (var slot : SLOTS.values()) if (slot.key.mainframeId().equals(mainframeId) && slot.running()) n++;
        return n;
    }

    static void changed(UUID mainframeId) {
        DIRTY.add(mainframeId);
        SAVE.add(mainframeId);
    }

    static String newId() {
        byte[] raw = new byte[4];
        RANDOM.nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }

    /** Every saved script of the server: mainframe → records (commands, tests). */
    public static Map<UUID, List<ScriptRecord>> all() {
        var map = new LinkedHashMap<UUID, List<ScriptRecord>>();
        for (var e : BOOKS.entrySet()) if (e.getValue().size() > 0) map.put(e.getKey(), e.getValue().scripts());
        return map;
    }

    // ---- rights ------------------------------------------------------------------------------------------------------

    /** The live rights of a run: its author's, capped by its starter's. */
    public static Set<Perm> rights(MinecraftServer srv, ScriptIdentity who) {
        var resolver = Access.resolver(srv);
        var set = EnumSet.noneOf(Perm.class);
        set.addAll(resolver.resolve(who.author(), who.mainframeId()).perms());
        if (!who.starter().equals(who.author())) set.retainAll(resolver.resolve(who.starter(), who.mainframeId()).perms());
        return set;
    }

    static boolean mayCode(MinecraftServer srv, UUID player, UUID mainframeId) {
        return Access.resolver(srv).resolve(player, mainframeId).perms().contains(Perm.CODE_EDIT);
    }

    /** Whether this identity's run is the current, running one. */
    static boolean isRunning(ScriptIdentity who) {
        var slot = SLOTS.get(new Key(who.mainframeId(), who.scriptId()));
        return slot != null && slot.running() && slot.runtime.identity() == who;
    }

    // ---- running -----------------------------------------------------------------------------------------------------

    /** A refusal with an error code ({@code has_errors}: {@code detail} = the first compile error). */
    record Refusal(String error, @Nullable String detail) {
    }

    /**
     * Starts a script for {@code starter} (who must have {@code code.edit}; checked here again, as for the author). Null if
     * it started.
     */
    static @Nullable Refusal start(MinecraftServer srv, UUID mainframeId, String scriptId, UUID starter, String starterName) {
        if (!enabled()) return new Refusal("disabled", vmProblem);
        var record = book(mainframeId).get(scriptId);
        if (record == null) return new Refusal("unknown_script", null);
        var limits = limits();
        var slot = slot(mainframeId, record, limits);
        if (slot.running()) return new Refusal("already_running", null);
        var mainframe = CloudNetwork.mainframe(mainframeId);
        var sched = scheduler;
        var w = world;
        if (mainframe == null || mainframe.isRemoved() || mainframe.getLevel() == null || !mainframe.isOnline() || sched == null || w == null) {
            return new Refusal("offline", null);
        }
        if (running(mainframeId) >= limits.maxRunning()) return new Refusal("quota", String.valueOf(limits.maxRunning()));
        if (slot.checkedVersion == record.version() && !slot.diagnostics.isEmpty()) {
            var first = slot.diagnostics.getFirst();
            return new Refusal("has_errors", record.name() + ":" + first.line() + ": " + first.message());
        }
        if (!mayCode(srv, record.author(), mainframeId)) return new Refusal("not_allowed", "author");
        if (!mayCode(srv, starter, mainframeId)) return new Refusal("not_allowed", null);
        var who = new ScriptIdentity(mainframeId, record.id(), record.name(), record.author(), record.authorName(), starter, starterName);
        boolean positions = WebHub.seesRestricted(rights(srv, who));
        slot.positions = positions;
        slot.log.limits(limits.logKept(), limits.logLinesPerSecond());
        slot.store.limit(limits.storageBytes());
        var snapshot = GameSnapshots.snapshot(GameSnapshots.base(srv, mainframe, nextSeq(mainframeId)), positions);
        var engine = engine(record.language());
        if (engine == null) return new Refusal("disabled", "language " + record.language());
        var runtime = new ScriptRuntime(who, record.version(), record.source(), engine, sched, w, limits, slot.log, slot.store, LISTENER, RuleService::zone, snapshot);
        slot.runtime = runtime;
        slot.status = "running";
        slot.reason = null;
        slot.lastError = null;
        slot.startedAt = System.currentTimeMillis();
        slot.startedBy = starterName;
        slot.runningVersion = record.version();
        slot.autoPending = false;
        runtime.start();
        DIRTY.add(mainframeId);
        return null;
    }

    /** Stops a run right away ({@code reason}: stopped, command, kill_switch, …). */
    static void stop(Slot slot, String reason) {
        var runtime = slot.runtime;
        if (runtime == null) return;
        runtime.stop(reason);
        finish(slot, runtime, reason, runtime.endError());
    }

    /** A run ended (any thread called the listener; this runs on the server thread). */
    private static void ended(ScriptRuntime runtime, String reason, @Nullable ScriptError error) {
        var slot = SLOTS.get(new Key(runtime.identity().mainframeId(), runtime.identity().scriptId()));
        if (slot != null && slot.runtime == runtime) finish(slot, runtime, reason, error);
    }

    private static void finish(Slot slot, ScriptRuntime runtime, String reason, @Nullable ScriptError error) {
        if (slot.runtime != runtime) return;
        slot.runtime = null;
        slot.status = error != null ? "error" : "stopped";
        slot.reason = error != null ? "error" : reason;
        if (error != null) {
            slot.lastError = error;
            slot.lastErrorTime = System.currentTimeMillis();
        }
        slot.autoPending = AUTO_REASONS.contains(reason);
        flushStore(slot);
        DIRTY.add(slot.key.mainframeId());
    }

    /** Stops every running script (all mainframes, or one name); returns how many. {@code /cccloud scripts stop}. */
    public static int stopMatching(@Nullable String name, String reason) {
        int n = 0;
        for (var slot : new ArrayList<>(SLOTS.values())) {
            if (!slot.running()) continue;
            if (name != null && !slot.runtime.identity().scriptName().equalsIgnoreCase(name)) continue;
            stop(slot, reason);
            n++;
        }
        return n;
    }

    /** {@code /cccloud scripts list}: running scripts with their mainframe and CPU use. */
    public record RunningInfo(UUID mainframeId, String mainframe, String name, String author, double cpuMs, int handlers, int timers) {
    }

    public static List<RunningInfo> runningInfo() {
        var list = new ArrayList<RunningInfo>();
        for (var slot : SLOTS.values()) {
            var r = slot.runtime;
            if (r == null || r.isEnded()) continue;
            var mainframe = CloudNetwork.mainframe(slot.key.mainframeId());
            list.add(new RunningInfo(slot.key.mainframeId(), mainframe != null ? mainframe.getDisplayName() : slot.key.mainframeId().toString(),
                r.identity().scriptName(), r.identity().authorName(), r.cpuLastSecond(), r.handlerCount(), r.timerCount()));
        }
        return list;
    }

    private static long nextSeq(UUID mainframeId) {
        return SEQ.merge(mainframeId, 1L, Long::sum);
    }

    // ---- tick --------------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer srv) {
        if (server != srv) return;
        ticks++;
        var w = world;
        if (w != null) w.tick();
        var active = new LinkedHashMap<UUID, List<Slot>>();
        for (var slot : SLOTS.values()) if (slot.running()) active.computeIfAbsent(slot.key.mainframeId(), k -> new ArrayList<>()).add(slot);
        for (var e : active.entrySet()) {
            String reason = offline(e.getKey());
            if (reason == null && !enabled()) reason = "disabled";
            if (reason != null) for (var slot : e.getValue()) stop(slot, reason);
        }
        if (ticks % SNAPSHOT_TICKS == 0) {
            for (var e : active.entrySet()) {
                var running = e.getValue().stream().filter(Slot::running).toList();
                if (!running.isEmpty()) offerSnapshots(srv, e.getKey(), running);
            }
        }
        if (ticks % CHECK_TICKS == 0) {
            recheck(srv);
            autostart(srv);
            flushStores(false);
        }
        if (ticks % LOG_TICKS == 0) pushLogs();
        if (ticks % STATUS_TICKS == 0) {
            if (ticks % CHECK_TICKS == 0) DIRTY.addAll(active.keySet()); // CPU figures change all the time: once a second
            pushStatus();
        }
        save();
    }

    /** Null if the mainframe can run scripts, else the stop reason. */
    static @Nullable String offline(UUID mainframeId) {
        var mainframe = CloudNetwork.mainframe(mainframeId);
        if (mainframe == null || mainframe.isRemoved() || mainframe.getLevel() == null) return "unloaded";
        if (mainframe.isOnline()) return null;
        return "redstone".equals(mainframe.reason()) ? "kill_switch" : "offline";
    }

    private static void offerSnapshots(MinecraftServer srv, UUID mainframeId, List<Slot> running) {
        var mainframe = CloudNetwork.mainframe(mainframeId);
        var sched = scheduler;
        if (mainframe == null || sched == null) return;
        var flag = BUILDING.computeIfAbsent(mainframeId, k -> new AtomicBoolean());
        if (!flag.compareAndSet(false, true)) return; // the last one is still being built
        var base = GameSnapshots.base(srv, mainframe, nextSeq(mainframeId));
        var targets = new ArrayList<Map.Entry<ScriptRuntime, Boolean>>();
        for (var slot : running) targets.add(Map.entry(slot.runtime, slot.positions));
        boolean submitted = sched.submit(() -> {
            try {
                var forms = new HashMap<Boolean, ai.ezfox.cccloud.code.world.WorldSnapshot>();
                for (var t : targets) t.getKey().offer(forms.computeIfAbsent(t.getValue(), p -> GameSnapshots.snapshot(base, p)));
            } catch (RuntimeException e) {
                CCCloud.LOGGER.warn("[cccloud] Could not build a Cloud Scripts snapshot of {}", mainframeId, e);
            } finally {
                flag.set(false);
            }
        });
        if (!submitted) flag.set(false);
    }

    /** Every second: authors and starters still have code.edit; the rights form of the snapshots. */
    private static void recheck(MinecraftServer srv) {
        for (var slot : new ArrayList<>(SLOTS.values())) {
            if (!slot.running()) continue;
            var who = slot.runtime.identity();
            if (!mayCode(srv, who.author(), who.mainframeId())) {
                slot.log.system("warn", who.authorName() + " may no longer write Cloud Scripts here");
                stop(slot, "author_lost_access");
                continue;
            }
            if (!who.starter().equals(who.author()) && !mayCode(srv, who.starter(), who.mainframeId())) {
                slot.log.system("warn", who.starterName() + " may no longer run Cloud Scripts here");
                stop(slot, "starter_lost_access");
                continue;
            }
            slot.positions = WebHub.seesRestricted(rights(srv, who));
        }
    }

    /** Autostart scripts whose mainframe is loaded and online (after a server start, the kill switch, an unload). */
    private static void autostart(MinecraftServer srv) {
        if (!enabled()) return;
        for (var e : BOOKS.entrySet()) {
            UUID id = e.getKey();
            if (offline(id) != null) continue;
            for (var record : e.getValue().scripts()) {
                if (!record.autostart() || record.autostartBy() == null) continue;
                var slot = slot(id, record, limits());
                if (!slot.autoPending || slot.running()) continue;
                var refusal = start(srv, id, record.id(), record.autostartBy(), record.autostartByName().isEmpty() ? "autostart" : record.autostartByName());
                if (refusal == null || refusal.error().equals("quota")) continue;
                slot.autoPending = false;
                slot.log.system("warn", "autostart refused: " + refusal.error() + (refusal.detail() == null ? "" : " (" + refusal.detail() + ")"));
                DIRTY.add(id);
            }
        }
    }

    private static void flushStore(Slot slot) {
        if (!slot.store.dirty()) return;
        var book = BOOKS.get(slot.key.mainframeId());
        if (book == null || book.get(slot.key.scriptId()) == null) return;
        book.setStore(slot.key.scriptId(), slot.store.snapshot());
        SAVE.add(slot.key.mainframeId());
    }

    private static void flushStores(boolean all) {
        for (var slot : SLOTS.values()) if (all || slot.running()) flushStore(slot);
    }

    private static void save() {
        var srv = server;
        if (srv == null || SAVE.isEmpty()) return;
        var data = CodeData.get(srv);
        for (var id : SAVE) data.set(id, book(id).scripts());
        SAVE.clear();
    }

    // ---- dashboard pushes --------------------------------------------------------------------------------------------

    static void listen(UUID mainframeId, CodeViewer viewer) {
        LISTENERS.computeIfAbsent(mainframeId, k -> new LinkedHashSet<>()).add(viewer);
    }

    static void watch(Slot slot, CodeViewer viewer) {
        if (slot.watchers.isEmpty()) slot.pushedSeq = slot.log.seq();
        slot.watchers.add(viewer);
    }

    static void unwatch(Slot slot, CodeViewer viewer) {
        slot.watchers.remove(viewer);
    }

    /** A session closed: no more pushes to it. */
    public static void close(UUID mainframeId, CodeViewer viewer) {
        var set = LISTENERS.get(mainframeId);
        if (set != null) set.remove(viewer);
        for (var slot : SLOTS.values()) slot.watchers.remove(viewer);
    }

    private static void pushStatus() {
        if (DIRTY.isEmpty()) return;
        for (var id : DIRTY) {
            var viewers = LISTENERS.get(id);
            if (viewers == null || viewers.isEmpty()) continue;
            var json = new JsonObject();
            json.addProperty("t", "code_status");
            json.add("scripts", list(id));
            json.add("limits", limits().toJson());
            String text = json.toString();
            viewers.removeIf(v -> {
                if (!v.allowed()) return true;
                v.send(List.of(text));
                return false;
            });
        }
        DIRTY.clear();
    }

    private static void pushLogs() {
        for (var slot : SLOTS.values()) {
            if (slot.watchers.isEmpty()) continue;
            long seq = slot.log.seq();
            if (seq == slot.pushedSeq) continue;
            var lines = slot.log.since(slot.pushedSeq);
            slot.pushedSeq = seq;
            if (lines.isEmpty()) continue;
            var json = new JsonObject();
            json.addProperty("t", "code_log");
            json.addProperty("id", slot.key.scriptId());
            json.add("lines", ScriptLog.toJson(lines));
            String text = json.toString();
            slot.watchers.removeIf(v -> {
                if (!v.allowed()) return true;
                v.send(List.of(text));
                return false;
            });
        }
    }

    // ---- JSON --------------------------------------------------------------------------------------------------------

    /** Summaries of every script of a mainframe (dashboard protocol). */
    static JsonArray list(UUID mainframeId) {
        var array = new JsonArray();
        var limits = limits();
        for (var record : book(mainframeId).scripts()) array.add(summary(mainframeId, record, limits));
        return array;
    }

    static JsonObject summary(UUID mainframeId, ScriptRecord record, Limits limits) {
        var slot = slot(mainframeId, record, limits);
        var json = new JsonObject();
        json.addProperty("id", record.id());
        json.addProperty("name", record.name());
        json.addProperty("language", record.language());
        var r = slot.runtime;
        boolean running = r != null && !r.isEnded();
        json.addProperty("status", running ? "running" : slot.status);
        if (running || slot.reason == null) json.add("reason", JsonNull.INSTANCE);
        else json.addProperty("reason", slot.reason);
        json.addProperty("autostart", record.autostart());
        json.addProperty("version", record.version());
        if (running) json.addProperty("runningVersion", slot.runningVersion);
        json.addProperty("size", record.current().size());
        json.addProperty("updated", record.updated());
        json.addProperty("author", record.authorName());
        if (slot.startedBy != null) {
            json.addProperty("startedAt", slot.startedAt);
            json.addProperty("startedBy", slot.startedBy);
        } else {
            json.add("startedAt", JsonNull.INSTANCE);
            json.add("startedBy", JsonNull.INSTANCE);
        }
        var cpu = new JsonObject();
        cpu.addProperty("ms", running ? Math.round(r.cpuLastSecond() * 10) / 10.0 : 0);
        cpu.addProperty("avg", running ? Math.round(r.cpuAverage() * 10) / 10.0 : 0);
        cpu.addProperty("limit", limits.cpuMsPerSecond());
        json.add("cpu", cpu);
        json.addProperty("handlers", running ? r.handlerCount() : 0);
        json.addProperty("timers", running ? r.timerCount() : 0);
        json.addProperty("memory", running ? r.heapEstimate() : 0);
        var error = slot.lastError;
        if (error == null) {
            json.add("lastError", JsonNull.INSTANCE);
        } else {
            var e = new JsonObject();
            e.addProperty("message", error.getMessage());
            if (error.line() != null) e.addProperty("line", error.line());
            else e.add("line", JsonNull.INSTANCE);
            e.addProperty("kind", error.kind().id());
            e.addProperty("time", slot.lastErrorTime);
            json.add("lastError", e);
        }
        json.addProperty("logLines", slot.log.size());
        return json;
    }

    /** Compiles off the server thread (a script worker); completes with the diagnostics. */
    static CompletableFuture<List<Diagnostic>> compile(String name, String source) {
        return compile(name, source, "lua");
    }

    /** {@link #compile(String, String)} for a language ({@code lua}, {@code wasm}). */
    static CompletableFuture<List<Diagnostic>> compile(String name, String source, String language) {
        var future = new CompletableFuture<List<Diagnostic>>();
        var engine = engine(language);
        if (engine == null) return CompletableFuture.completedFuture(List.of(Diagnostic.error(1, "unknown language " + language)));
        var sched = scheduler;
        boolean submitted = sched != null && sched.submit(() -> {
            try {
                future.complete(engine.check(name, source));
            } catch (StackOverflowError e) {
                future.complete(List.of(Diagnostic.error(1, "the code is nested too deeply")));
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        if (!submitted) future.completeExceptionally(new java.util.concurrent.RejectedExecutionException("offline"));
        return future;
    }

    static @Nullable MinecraftServer server() {
        return server;
    }

    static @Nullable GameWorld world() {
        return world;
    }

    /** Test hook: the running script of that name on a mainframe, or null. */
    public static @Nullable ScriptRuntime runtimeForTest(UUID mainframeId, String name) {
        for (var slot : slots(mainframeId)) {
            var r = slot.runtime;
            if (r != null && !r.isEnded() && r.identity().scriptName().equalsIgnoreCase(name)) return r;
        }
        return null;
    }

    /** Test hook: the log of a script by name. */
    public static @Nullable ScriptLog logForTest(UUID mainframeId, String name) {
        var record = book(mainframeId).find(name);
        if (record == null) return null;
        var slot = slot(mainframeId, record.id());
        return slot == null ? null : slot.log;
    }

    /** Test hook: why a script stopped last (null while running or never run). */
    public static @Nullable String reasonForTest(UUID mainframeId, String name) {
        var record = book(mainframeId).find(name);
        var slot = record == null ? null : slot(mainframeId, record.id());
        return slot == null || slot.running() ? null : slot.reason;
    }
}
