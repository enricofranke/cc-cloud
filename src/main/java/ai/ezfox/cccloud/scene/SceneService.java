package ai.ezfox.cccloud.scene;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.bridge.BridgeCore;
import ai.ezfox.cccloud.compat.server.MainframeSection;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.rules.Rule;
import ai.ezfox.cccloud.rules.RuleService;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.InGameClients;
import ai.ezfox.cccloud.web.TokenBucket;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Game side of the v0.10 scenes: storage ({@link SceneData}), editing (dashboard), the Scenes section of every loaded
 * mainframe, starting runs (a press, a rule) and stepping them every tick with {@link SceneRunner}. Every step is
 * checked against the live rights of whoever started the run (the presser's device, or the scene's owner for rules),
 * so a scene never does more than that player could do by hand. Server thread only.
 */
public final class SceneService {
    /** Button ids in the Scenes section: {@code s_<sceneId>}. */
    public static final String WIDGET_PREFIX = "s_";
    /** Runs at the same time per mainframe. */
    public static final int MAX_RUNNING = 4;
    /** Runs started per minute per mainframe. */
    public static final int RUNS_PER_MINUTE = 30;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** mainframe → parsed scenes (cache of SceneData). */
    private static final Map<UUID, List<Scene>> SCENES = new HashMap<>();
    private static final Map<UUID, List<SceneRunner.Run>> RUNNING = new HashMap<>();
    /** mainframe → scene id → the last run (memory only). */
    private static final Map<UUID, Map<String, SceneRunner.Run>> LAST = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> RUN_COUNTS = new HashMap<>();
    private static final Map<UUID, TokenBucket> BUDGETS = new HashMap<>();
    private static final Map<UUID, ScenesSection> SECTIONS = new HashMap<>();
    /** Mainframes whose scenes document changed (published at the end of the tick). */
    private static final Set<UUID> DIRTY = new LinkedHashSet<>();
    private static final Map<SceneRunner.Run, RunContext> CONTEXTS = new HashMap<>();
    private static long tick;

    /**
     * Who started a run. Rights are resolved live for every step: from the device (dashboard, tablet, display; read-only
     * devices capped as always) or, without a device, from the player (scene owner for rules, the key's player for the
     * API).
     *
     * @param user   name in the action logs (device name, "rule:<name>", "api:<key>")
     * @param source dashboard, tablet, display, rule or api
     * @param cap    further limit of the rights (null = none)
     */
    public record Caller(UUID player, String playerName, String user, String source, AuthStore.@Nullable Device device, @Nullable Set<Perm> cap) {
        /** A dashboard, tablet or display press. */
        public static Caller of(AuthStore.Device device) {
            String source = "dashboard";
            if (device.tokenHash().startsWith(InGameClients.DEVICE_PREFIX)) source = device.name().endsWith("(display)") ? "display" : "tablet";
            return new Caller(device.player(), device.playerName(), device.name(), source, device, null);
        }
    }

    /** The result of an edit: {@code error} (+ {@code detail}), or the id of the saved scene. */
    public record Edit(@Nullable String error, @Nullable String detail, @Nullable String id) {
        static Edit fail(String error, @Nullable String detail) {
            return new Edit(error, detail, null);
        }
    }

    private record RunContext(UUID mainframeId, Caller caller, boolean pinVerified) {
    }

    private SceneService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> load(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> clear());
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
    }

    private static void clear() {
        SCENES.clear();
        RUNNING.clear();
        LAST.clear();
        RUN_COUNTS.clear();
        BUDGETS.clear();
        SECTIONS.clear();
        DIRTY.clear();
        CONTEXTS.clear();
    }

    public static boolean enabled() {
        return CloudConfig.get(CloudConfig.SCENES_ENABLED);
    }

    public static int limit() {
        return CloudConfig.get(CloudConfig.MAX_SCENES);
    }

    private static void load(MinecraftServer server) {
        clear();
        for (var e : SceneData.get(server).scenes().entrySet()) {
            var list = new ArrayList<Scene>();
            for (var stored : e.getValue()) {
                try {
                    var parsed = SceneCodec.parse(JsonParser.parseString(stored.json()), BridgeCore.maxCraftAmount());
                    if (parsed.scene() != null) list.add(parsed.scene().withOwner(stored.owner(), stored.ownerName()));
                    else CCCloud.LOGGER.warn("[cccloud] Dropping invalid scene of mainframe {}: {}", e.getKey(), parsed.error());
                } catch (RuntimeException ex) {
                    CCCloud.LOGGER.warn("[cccloud] Dropping unreadable scene of mainframe {}", e.getKey());
                }
            }
            SCENES.put(e.getKey(), list);
            DIRTY.add(e.getKey());
        }
    }

    public static List<Scene> scenes(UUID mainframeId) {
        return SCENES.getOrDefault(mainframeId, List.of());
    }

    public static @Nullable Scene scene(UUID mainframeId, String sceneId) {
        for (var scene : scenes(mainframeId)) if (scene.id().equals(sceneId)) return scene;
        return null;
    }

    /** Stable id of a mainframe's Scenes section (layouts survive restarts). */
    public static UUID sectionId(UUID mainframeId) {
        return MainframeSection.sectionId(mainframeId, ScenesSection.KIND);
    }

    public static boolean isScenesSection(UUID mainframeId, String uplinkId) {
        return sectionId(mainframeId).toString().equals(uplinkId);
    }

    // ---- permissions -------------------------------------------------------------------------------------------------

    /** The permission a step needs besides pressing the button (null: none, e.g. alerts). */
    public static @Nullable Perm stepPerm(UUID mainframeId, Rule.Action action) {
        return switch (action) {
            case Rule.WidgetAction w -> WebHub.INSTANCE.actionPerm(mainframeId,
                ai.ezfox.cccloud.rsdevice.RsDevices.sectionOf(mainframeId, w.uplinkId(), w.widgetId()), w.widgetId());
            case Rule.RedstoneAction r -> Perm.ACT_REDSTONE;
            case Rule.CraftAction c -> Perm.STORAGE_CRAFT;
            case Rule.DashboardAction d -> Perm.POWER_TOGGLE;
            case Rule.AlertAction a -> null;
            case Rule.SceneAction s -> Perm.RULES_EDIT; // never in a scene (the codec refuses it)
        };
    }

    /** Every permission the scene's steps need (shown in the dashboard). */
    public static Set<Perm> perms(UUID mainframeId, Scene scene) {
        var set = EnumSet.noneOf(Perm.class);
        for (var step : scene.steps()) {
            if (step.isDelay()) continue;
            var perm = stepPerm(mainframeId, step.action());
            if (perm != null) set.add(perm);
        }
        return set;
    }

    /** The scene asks for the mainframe PIN: its own flag, or a step on a PIN-protected widget. */
    public static boolean needsPin(UUID mainframeId, Scene scene) {
        if (scene.pin()) return true;
        for (var step : scene.steps()) {
            if (step.action() instanceof Rule.WidgetAction w && WebHub.INSTANCE.widgetNeedsPin(mainframeId, w.uplinkId(), w.widgetId())) return true;
        }
        return false;
    }

    /** The caller's live permissions on the mainframe (empty: none, e.g. banned or a revoked device). */
    static Set<Perm> rights(MinecraftServer server, UUID mainframeId, Caller caller) {
        var resolver = Access.resolver(server);
        var device = caller.device();
        Set<Perm> perms;
        if (device != null) {
            boolean inGame = device.tokenHash().startsWith(InGameClients.DEVICE_PREFIX);
            if (!inGame && !WebAuthData.get(server).isValid(device.tokenHash())) return Set.of();
            perms = resolver.resolveDevice(device, mainframeId).perms();
        } else {
            perms = resolver.resolve(caller.player(), mainframeId).perms();
        }
        if (caller.cap() == null) return perms;
        var capped = EnumSet.noneOf(Perm.class);
        capped.addAll(perms);
        capped.retainAll(caller.cap());
        return capped;
    }

    /** Null if the caller may run this step now, else the error. */
    static @Nullable String check(MinecraftServer server, UUID mainframeId, Caller caller, boolean pinVerified, Rule.Action action) {
        var perms = rights(server, mainframeId, caller);
        if (!perms.contains(Perm.VIEW)) return "not_allowed";
        var needed = stepPerm(mainframeId, action);
        if (needed != null && !perms.contains(needed)) return "not_allowed";
        if (action instanceof Rule.WidgetAction w && !pinVerified && WebHub.INSTANCE.widgetNeedsPin(mainframeId, w.uplinkId(), w.widgetId())) return "pin_required";
        return null;
    }

    // ---- editing -----------------------------------------------------------------------------------------------------

    /**
     * Creates or replaces a scene (validated JSON). The saver needs every permission the steps need and becomes the
     * scene's owner.
     */
    public static Edit set(MinecraftServer server, UUID mainframeId, @Nullable JsonElement json, UUID player, String playerName, Set<Perm> perms) {
        if (!enabled()) return Edit.fail("disabled", null);
        var parsed = SceneCodec.parse(json, BridgeCore.maxCraftAmount());
        if (parsed.scene() == null) return Edit.fail("invalid_scene", parsed.error());
        var scene = parsed.scene();
        for (int i = 0; i < scene.steps().size(); i++) {
            var action = scene.steps().get(i).action();
            if (action == null) continue;
            String target = switch (action) {
                case Rule.WidgetAction w -> w.uplinkId();
                case Rule.RedstoneAction r -> r.uplinkId();
                case Rule.CraftAction c -> c.uplinkId();
                default -> null;
            };
            if (target != null && isScenesSection(mainframeId, target)) return Edit.fail("invalid_scene", "step " + (i + 1) + ": a scene cannot press scene buttons");
            var needed = stepPerm(mainframeId, action);
            if (needed != null && !perms.contains(needed)) return Edit.fail("not_allowed", "step " + (i + 1) + ": " + needed.id);
        }
        var list = new ArrayList<>(scenes(mainframeId));
        int index = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(scene.id())) index = i;
        if (index < 0) {
            if (list.size() >= limit()) return Edit.fail("too_many", null);
            if (scene.id().isEmpty()) scene = scene.withId(newId(list));
        }
        scene = scene.withOwner(player, playerName);
        if (index < 0) list.add(scene);
        else list.set(index, scene);
        save(server, mainframeId, list);
        return new Edit(null, null, scene.id());
    }

    public static @Nullable String delete(MinecraftServer server, UUID mainframeId, String sceneId) {
        var list = new ArrayList<>(scenes(mainframeId));
        if (!list.removeIf(s -> s.id().equals(sceneId))) return "unknown_scene";
        var last = LAST.get(mainframeId);
        if (last != null) last.remove(sceneId);
        save(server, mainframeId, list);
        return null;
    }

    /** The mainframe was broken. */
    public static void forget(MinecraftServer server, UUID mainframeId) {
        SCENES.remove(mainframeId);
        LAST.remove(mainframeId);
        RUN_COUNTS.remove(mainframeId);
        BUDGETS.remove(mainframeId);
        var running = RUNNING.remove(mainframeId);
        if (running != null) running.forEach(CONTEXTS::remove);
        var section = SECTIONS.remove(mainframeId);
        if (section != null) CloudNetwork.unregisterSection(section);
        SceneData.get(server).remove(mainframeId);
    }

    private static void save(MinecraftServer server, UUID mainframeId, List<Scene> list) {
        SCENES.put(mainframeId, List.copyOf(list));
        var stored = new ArrayList<SceneData.Stored>();
        for (var scene : list) {
            if (scene.owner() != null) stored.add(new SceneData.Stored(SceneCodec.toJson(scene).toString(), scene.owner(), scene.ownerName()));
        }
        SceneData.get(server).setScenes(mainframeId, stored);
        updateSection(mainframeId);
        DIRTY.add(mainframeId);
    }

    private static String newId(List<Scene> existing) {
        while (true) {
            byte[] raw = new byte[4];
            RANDOM.nextBytes(raw);
            String id = HexFormat.of().formatHex(raw);
            if (existing.stream().noneMatch(s -> s.id().equals(id))) return id;
        }
    }

    // ---- running -----------------------------------------------------------------------------------------------------

    /**
     * Starts a scene for a caller whose press was already allowed ({@code act.buttons}, the button's PIN). Steps up to the
     * first delay run right away. Null if it started, else {@code unknown_scene|disabled|offline|busy|too_many|
     * rate_limited|pin_required}.
     */
    public static @Nullable String start(CloudMainframeBlockEntity mainframe, String sceneId, Caller caller, boolean pinVerified) {
        if (!enabled()) return "disabled";
        UUID mainframeId = mainframe.getMainframeId();
        var scene = scene(mainframeId, sceneId);
        if (scene == null) return "unknown_scene";
        if (!mainframe.isOnline() || mainframe.isRemoved()) return "offline";
        if (needsPin(mainframeId, scene) && !pinVerified) return "pin_required";
        var running = RUNNING.computeIfAbsent(mainframeId, k -> new ArrayList<>());
        for (var run : running) if (run.scene().id().equals(sceneId)) return "busy";
        if (running.size() >= MAX_RUNNING) return "too_many";
        if (!BUDGETS.computeIfAbsent(mainframeId, k -> new TokenBucket(RUNS_PER_MINUTE, RUNS_PER_MINUTE / 60.0)).tryAcquire()) return "rate_limited";
        var server = mainframe.getLevel() == null ? null : mainframe.getLevel().getServer();
        if (server == null) return "offline";
        var run = new SceneRunner.Run(scene, by(caller), caller.source(), System.currentTimeMillis());
        running.add(run);
        CONTEXTS.put(run, new RunContext(mainframeId, caller, pinVerified));
        LAST.computeIfAbsent(mainframeId, k -> new HashMap<>()).put(sceneId, run);
        RUN_COUNTS.computeIfAbsent(mainframeId, k -> new HashMap<>()).merge(sceneId, 1, Integer::sum);
        step(server, run);
        DIRTY.add(mainframeId);
        return null;
    }

    /** A rule's {@code scene} action: runs with the scene owner's live rights, never with a PIN. */
    public static @Nullable String startFromRule(CloudMainframeBlockEntity mainframe, String sceneId, String ruleName) {
        var scene = scene(mainframe.getMainframeId(), sceneId);
        if (scene == null) return "unknown_scene";
        if (scene.owner() == null) return "not_allowed";
        var server = mainframe.getLevel() == null ? null : mainframe.getLevel().getServer();
        if (server == null) return "offline";
        var caller = new Caller(scene.owner(), scene.ownerName(), "rule:" + ruleName, "rule", null, null);
        // A banned owner or one without any role: the whole run is refused (each step would be anyway).
        if (!rights(server, mainframe.getMainframeId(), caller).contains(Perm.VIEW)) return "not_allowed";
        return start(mainframe, sceneId, caller, false);
    }

    /** "Steve (Phone)", "Steve (rule:Night)", or the user alone when the player is unknown. */
    private static String by(Caller caller) {
        if (caller.playerName().isEmpty() || caller.user().startsWith(caller.playerName() + " (")) return caller.user();
        return caller.playerName() + " (" + caller.user() + ")";
    }

    /** Runs the due steps of one run; finished runs are logged and removed. */
    private static void step(MinecraftServer server, SceneRunner.Run run) {
        var context = CONTEXTS.get(run);
        if (context == null) return;
        var mainframe = CloudNetwork.mainframe(context.mainframeId());
        var env = new SceneRunner.Env() {
            @Override
            public boolean available() {
                // Online is enough: a press needs the dashboard switched on, but a step may switch it off for the rest.
                return mainframe != null && !mainframe.isRemoved() && mainframe.isOnline();
            }

            @Override
            public @Nullable String check(Rule.Action action) {
                return SceneService.check(server, context.mainframeId(), context.caller(), context.pinVerified(), action);
            }

            @Override
            public @Nullable String run(Rule.Action action) {
                return RuleService.runAction(mainframe, action, context.caller().user(), context.pinVerified());
            }
        };
        boolean changed = !env.available() ? SceneRunner.abort(run, System.currentTimeMillis(), "offline")
            : SceneRunner.advance(run, tick, System.currentTimeMillis(), env);
        if (!changed) return;
        DIRTY.add(context.mainframeId());
        if (!run.done()) return;
        var running = RUNNING.get(context.mainframeId());
        if (running != null) running.remove(run);
        CONTEXTS.remove(run);
        String error = run.firstError();
        String result = error == null ? "ok" : error;
        SceneData.get(server).addLog(context.mainframeId(), new SceneData.LogEntry(run.started(), run.scene().id(), run.scene().name(), run.by(), run.source(), result));
        CCCloud.LOGGER.info("[cccloud] {} ran scene '{}' on {}: {}", run.by(), run.scene().name(), mainframe != null ? mainframe.getDisplayName() : context.mainframeId(),
            result);
    }

    private static void tick(MinecraftServer server) {
        long tickCost = ai.ezfox.cccloud.network.TickCost.start();
        try {
            tick++;
            for (var running : new ArrayList<>(RUNNING.values())) {
                for (var run : new ArrayList<>(running)) step(server, run);
            }
            if (server.getTickCount() % 20 == 0) {
                var seen = new HashSet<UUID>();
                for (var mainframe : new ArrayList<>(CloudNetwork.mainframes())) {
                    if (mainframe.isRemoved() || mainframe.getLevel() == null) continue;
                    seen.add(mainframe.getMainframeId());
                    updateSection(mainframe.getMainframeId());
                }
                // Unloaded mainframes: stop routing to their Scenes section (the hub shows it offline).
                SECTIONS.entrySet().removeIf(e -> {
                    if (seen.contains(e.getKey())) return false;
                    CloudNetwork.unregisterSection(e.getValue());
                    return true;
                });
            }
            if (!DIRTY.isEmpty()) {
                for (var id : DIRTY) WebHub.INSTANCE.publishScenes(id, document(server, id));
                DIRTY.clear();
            }
        } finally {
            ai.ezfox.cccloud.network.TickCost.end(ai.ezfox.cccloud.network.TickCost.SCENES, tickCost);
        }
    }

    /** Creates, updates or removes the Scenes section of a loaded mainframe. */
    private static void updateSection(UUID mainframeId) {
        var mainframe = CloudNetwork.mainframe(mainframeId);
        var scenes = enabled() ? scenes(mainframeId) : List.<Scene>of();
        var section = SECTIONS.get(mainframeId);
        if (section != null && (scenes.isEmpty() || mainframe == null || section.mainframe() != mainframe)) {
            CloudNetwork.unregisterSection(section);
            SECTIONS.remove(mainframeId);
            if (scenes.isEmpty()) section.removeFromDashboard();
            section = null;
        }
        if (scenes.isEmpty()) {
            // deleted while the mainframe was unloaded: the hub may still show the old buttons
            if (WebHub.INSTANCE.hasUplink(mainframeId, sectionId(mainframeId).toString())) WebHub.INSTANCE.publishUplinkRemoved(mainframeId, sectionId(mainframeId).toString());
            return;
        }
        if (mainframe == null || mainframe.isRemoved() || mainframe.getLevel() == null) return;
        if (section == null) {
            section = new ScenesSection(mainframe);
            if (!CloudNetwork.registerSection(section)) return;
            SECTIONS.put(mainframeId, section);
        }
        section.update(scenes);
    }

    /** Test hook: the Scenes section of a loaded mainframe. */
    public static @Nullable ScenesSection section(UUID mainframeId) {
        return SECTIONS.get(mainframeId);
    }

    /** Test hook: the last run of a scene. */
    public static SceneRunner.@Nullable Run lastRun(UUID mainframeId, String sceneId) {
        var map = LAST.get(mainframeId);
        return map == null ? null : map.get(sceneId);
    }

    // ---- document ----------------------------------------------------------------------------------------------------

    /** {@code {scenes:[scene + owner/needsPin/perms/runs/lastRun], log:[…], limit, sectionId}}. */
    public static JsonObject document(MinecraftServer server, UUID mainframeId) {
        var json = new JsonObject();
        var array = new JsonArray();
        var last = LAST.getOrDefault(mainframeId, Map.of());
        var counts = RUN_COUNTS.getOrDefault(mainframeId, Map.of());
        for (var scene : scenes(mainframeId)) {
            var s = SceneCodec.toJson(scene);
            s.addProperty("owner", scene.ownerName());
            s.addProperty("needsPin", needsPin(mainframeId, scene));
            var perms = new JsonArray();
            for (var p : perms(mainframeId, scene)) perms.add(p.id);
            s.add("perms", perms);
            s.addProperty("runs", counts.getOrDefault(scene.id(), 0));
            var run = last.get(scene.id());
            s.add("lastRun", run == null ? JsonNull.INSTANCE : runJson(run));
            array.add(s);
        }
        json.add("scenes", array);
        var log = new JsonArray();
        for (var entry : SceneData.get(server).log(mainframeId)) {
            var e = new JsonObject();
            e.addProperty("time", entry.time());
            e.addProperty("scene", entry.sceneId());
            e.addProperty("name", entry.name());
            e.addProperty("by", entry.by());
            e.addProperty("source", entry.source());
            e.addProperty("result", entry.result());
            log.add(e);
        }
        json.add("log", log);
        json.addProperty("limit", limit());
        if (SECTIONS.containsKey(mainframeId) || (!scenes(mainframeId).isEmpty() && enabled())) json.addProperty("sectionId", sectionId(mainframeId).toString());
        else json.add("sectionId", JsonNull.INSTANCE);
        return json;
    }

    private static JsonObject runJson(SceneRunner.Run run) {
        var json = new JsonObject();
        json.addProperty("by", run.by());
        json.addProperty("source", run.source());
        json.addProperty("started", run.started());
        if (run.done()) json.addProperty("finished", run.finished());
        else json.add("finished", JsonNull.INSTANCE);
        var steps = new JsonArray();
        for (var result : run.results()) {
            if (result == null) {
                steps.add(JsonNull.INSTANCE);
                continue;
            }
            var step = new JsonObject();
            step.addProperty("ok", result.equals("ok"));
            if (!result.equals("ok")) step.addProperty("error", result);
            steps.add(step);
        }
        json.add("steps", steps);
        return json;
    }

    /** The scene list of the public API: {@code [{id, name, needsPin, running}]}. */
    public static JsonArray apiList(UUID mainframeId) {
        var array = new JsonArray();
        var running = RUNNING.getOrDefault(mainframeId, List.of());
        for (var scene : scenes(mainframeId)) {
            var s = new JsonObject();
            s.addProperty("id", scene.id());
            s.addProperty("name", scene.name());
            s.addProperty("needsPin", needsPin(mainframeId, scene));
            s.addProperty("running", running.stream().anyMatch(r -> r.scene().id().equals(scene.id())));
            array.add(s);
        }
        return array;
    }
}
