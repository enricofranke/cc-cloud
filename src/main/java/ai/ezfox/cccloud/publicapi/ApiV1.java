package ai.ezfox.cccloud.publicapi;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.AccessResolver;
import ai.ezfox.cccloud.access.CloudPermissions;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.access.ServerPermissions;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.production.ProductionService;
import ai.ezfox.cccloud.scene.SceneService;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.web.AccessApi;
import ai.ezfox.cccloud.web.AccessGate;
import ai.ezfox.cccloud.web.ClientMessages;
import ai.ezfox.cccloud.web.Router;
import ai.ezfox.cccloud.web.SectionChecks;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * The routes of the public API v1 after the key was authenticated (docs/SPEC.md v0.10 "Public API v1"). Runs on the
 * server thread with the key owner's <b>live</b> rights: every request needs the route's scope and the matching
 * permission of the owner (scope ∩ rights). Writes are refused on PIN-protected widgets and scenes and are written to
 * the mainframe's audit log.
 */
public final class ApiV1 {
    private ApiV1() {
    }

    /**
     * The live rights of a key's owner on the key's mainframe, capped by the key's scopes. NONE if the owner is banned,
     * lacks {@code cccloud.use} or {@code cccloud.api.keys}, or has no role there.
     */
    public static AccessResolver.Resolution access(MinecraftServer server, ApiKeys.Key key) {
        var permissions = CloudPermissions.of(server);
        if (!permissions.has(key.player(), ServerPermissions.Node.API_KEYS)) return AccessResolver.Resolution.NONE;
        var resolution = Access.resolver(server).resolve(key.player(), key.mainframeId());
        if (resolution.role() == Role.NONE) return AccessResolver.Resolution.NONE;
        return resolution;
    }

    /** {@code perms ∩ cap(scopes)}. */
    static Set<Perm> capped(Set<Perm> perms, Set<ApiScope> scopes) {
        var set = EnumSet.noneOf(Perm.class);
        set.addAll(perms);
        set.retainAll(ApiScope.cap(scopes));
        return set;
    }

    public static AccessApi.Response handle(MinecraftServer server, ApiKeys.Key key, Router.Id route, String path, Map<String, String> query, String body, String ip) {
        var access = access(server, key);
        if (access.role() == Role.NONE) return AccessApi.error(403, "no_access");
        ApiKeyData.get(server).keys().touch(key.hash(), ip);
        String[] parts = path.split("/");
        // /api/v1/mainframes/<id>/…: only the key's own mainframe exists for it
        if (parts.length > 4 && parts[3].equals("mainframes")) {
            UUID id = ClientMessages.parseUuid(parts[4]);
            if (id == null || !id.equals(key.mainframeId())) return AccessApi.error(404, "not_found");
        }
        var perms = capped(access.perms(), key.scopes());
        try {
            return switch (route) {
                case API_ME -> me(key);
                case API_SECTIONS -> {
                    var denied = need(key, ApiScope.READ_SECTIONS, perms, Perm.VIEW);
                    yield denied != null ? denied : new AccessApi.Response(200, sections(key.mainframeId(), perms));
                }
                case API_SECTION -> {
                    var denied = need(key, ApiScope.READ_SECTIONS, perms, Perm.VIEW);
                    yield denied != null ? denied : section(key.mainframeId(), parts[6], perms);
                }
                case API_WIDGET -> widget(server, key, perms, parts[6], parts[8], body);
                case API_ITEMS -> items(key, perms, parts[6], query);
                case API_PRODUCTION -> {
                    var denied = need(key, ApiScope.READ_STATS, perms, Perm.VIEW);
                    if (denied != null) yield denied;
                    if (!ProductionService.enabled()) yield AccessApi.error(409, "disabled");
                    yield new AccessApi.Response(200, ProductionService.overview(server, key.mainframeId(), perms, false));
                }
                case API_SCENES -> {
                    var denied = need(key, ApiScope.RUN_SCENES, perms, Perm.VIEW);
                    if (denied != null) yield denied;
                    var json = new JsonObject();
                    json.add("scenes", SceneService.apiList(key.mainframeId()));
                    yield new AccessApi.Response(200, json);
                }
                case API_SCENE_RUN -> runScene(server, key, access.perms(), parts[6]);
                default -> AccessApi.error(404, "not_found");
            };
        } catch (RuntimeException e) {
            CloudLog.LOGGER.error("[cccloud] Error in API request {} of key '{}'", route, key.name(), e);
            return AccessApi.error(500, "internal_error");
        }
    }

    /** The scope on the key and the permission of the owner; null if both are there. */
    private static AccessApi.@Nullable Response need(ApiKeys.Key key, ApiScope scope, Set<Perm> perms, Perm perm) {
        if (!key.scopes().contains(scope)) return AccessApi.error(403, "missing_scope");
        return perms.contains(perm) ? null : AccessApi.error(403, "not_allowed");
    }

    private static AccessApi.Response me(ApiKeys.Key key) {
        var json = new JsonObject();
        var k = new JsonObject();
        k.addProperty("id", key.id());
        k.addProperty("name", key.name());
        var scopes = new JsonArray();
        for (var s : key.scopes()) scopes.add(s.id);
        k.add("scopes", scopes);
        if (key.expires() == 0) k.add("expires", com.google.gson.JsonNull.INSTANCE);
        else k.addProperty("expires", key.expires());
        json.add("key", k);
        json.addProperty("player", key.playerName());
        var mainframe = new JsonObject();
        var info = WebHub.INSTANCE.info(key.mainframeId());
        mainframe.addProperty("id", key.mainframeId().toString());
        mainframe.addProperty("name", info == null ? "" : info.name());
        mainframe.addProperty("online", info != null && info.online());
        mainframe.addProperty("enabled", info != null && info.enabled());
        json.add("mainframe", mainframe);
        return new AccessApi.Response(200, json);
    }

    /** A section the permissions may see (restricted ones need view.positions). */
    private static boolean visible(UUID mainframeId, String sectionId, Set<Perm> perms) {
        return perms.contains(Perm.VIEW) && (!WebHub.INSTANCE.isRestricted(mainframeId, sectionId) || perms.contains(Perm.VIEW_POSITIONS));
    }

    private static JsonObject sections(UUID mainframeId, Set<Perm> perms) {
        var array = new JsonArray();
        for (var snapshot : WebHub.INSTANCE.sectionSnapshots(mainframeId)) {
            var api = apiSection(mainframeId, snapshot, perms);
            if (api != null) array.add(api);
        }
        var json = new JsonObject();
        json.add("sections", array);
        return json;
    }

    private static AccessApi.Response section(UUID mainframeId, String sectionId, Set<Perm> perms) {
        var snapshot = WebHub.INSTANCE.sectionSnapshot(mainframeId, sectionId);
        var api = snapshot == null ? null : apiSection(mainframeId, snapshot, perms);
        return api == null ? AccessApi.error(404, "not_found") : new AccessApi.Response(200, api);
    }

    /** The API form of a published section (null if the permissions may not see it). */
    public static @Nullable JsonObject apiSection(UUID mainframeId, JsonObject snapshot, Set<Perm> perms) {
        if (!(snapshot.get("uplinkId") instanceof JsonPrimitive id) || !visible(mainframeId, id.getAsString(), perms)) return null;
        var json = new JsonObject();
        json.addProperty("id", id.getAsString());
        json.add("title", snapshot.get("title"));
        json.addProperty("online", snapshot.has("online") && snapshot.get("online").getAsBoolean());
        json.addProperty("loaded", !snapshot.has("loaded") || snapshot.get("loaded").getAsBoolean());
        if (snapshot.has("template")) json.add("template", snapshot.get("template"));
        var widgets = new JsonArray();
        if (snapshot.get("widgets") instanceof JsonArray array) {
            for (var e : array) {
                if (!(e instanceof JsonObject w)) continue;
                var out = new JsonObject();
                out.add("id", w.get("id"));
                out.add("type", w.get("type"));
                if (w.has("label")) out.add("label", w.get("label"));
                if (w.has("value")) out.add("value", w.get("value"));
                if (w.get("opts") instanceof JsonObject opts) {
                    for (String key : new String[]{"unit", "min", "max", "options"}) if (opts.has(key)) out.add(key, opts.get(key));
                    if (opts.has("pin")) out.add("pin", opts.get("pin"));
                }
                widgets.add(out);
            }
        }
        json.add("widgets", widgets);
        return json;
    }

    /** {@code POST …/sections/<sectionId>/widgets/<widgetId>} with {@code {"value": number|string|boolean}}. */
    private static AccessApi.Response widget(MinecraftServer server, ApiKeys.Key key, Set<Perm> perms, String sectionId, String widgetId, String body) {
        if (!key.scopes().contains(ApiScope.ACTION_WIDGETS)) return AccessApi.error(403, "missing_scope");
        UUID mainframeId = key.mainframeId();
        if (SceneService.isScenesSection(mainframeId, sectionId)) return AccessApi.error(400, "use_scene_route");
        var value = scalar(body);
        if (value == null) return AccessApi.error(400, "invalid");
        String type = WebHub.INSTANCE.widgetType(mainframeId, sectionId, widgetId);
        if (type == null || !visible(mainframeId, sectionId, perms)) return AccessApi.error(404, "not_found");
        var widgetType = WidgetType.byId(type);
        if (widgetType != null && widgetType.takesOps()) return AccessApi.error(400, "not_supported");
        String denied = AccessGate.check(Role.OPERATOR, perms, WebHub.INSTANCE.actionPerm(mainframeId, sectionId, widgetId));
        if (denied != null) return AccessApi.error(403, "not_allowed");
        // No PIN in the API on purpose: a leaked key must not reach the second factor.
        if (WebHub.INSTANCE.widgetNeedsPin(mainframeId, sectionId, widgetId)) return AccessApi.error(403, "pin_required");
        UUID section = ClientMessages.parseUuid(sectionId);
        if (section == null) return AccessApi.error(404, "not_found");
        String error = SectionChecks.check(mainframeId, section);
        if (error != null) return actionError(error);
        var result = CloudNetwork.section(section).handleAction(widgetId, value, null, "api:" + key.name(), false).getNow(null);
        if (result != null && !result.ok()) return actionError(result.error());
        String title = CloudNetwork.section(section).getTitle();
        audit(server, key, "set " + (title.isEmpty() ? sectionId : title) + "/" + widgetId + " = " + value);
        return ok();
    }

    /** {@code GET …/storage/<sectionId>/items?q=&limit=}. */
    private static AccessApi.Response items(ApiKeys.Key key, Set<Perm> perms, String sectionId, Map<String, String> query) {
        if (!key.scopes().contains(ApiScope.READ_STORAGE)) return AccessApi.error(403, "missing_scope");
        if (!perms.contains(Perm.STORAGE_SEARCH)) return AccessApi.error(403, "not_allowed");
        UUID mainframeId = key.mainframeId();
        String q = query.getOrDefault("q", "");
        int limit;
        try {
            limit = Integer.parseInt(query.getOrDefault("limit", "20"));
        } catch (NumberFormatException e) {
            return AccessApi.error(400, "invalid");
        }
        if (q.length() > Limits.QUERY || limit < 1 || limit > Limits.QUERY_ROWS) return AccessApi.error(400, "invalid");
        String search = WebHub.INSTANCE.firstWidgetOfType(mainframeId, sectionId, "item_search");
        if (search == null || !visible(mainframeId, sectionId, perms)) return AccessApi.error(404, "not_found");
        UUID section = ClientMessages.parseUuid(sectionId);
        if (section == null) return AccessApi.error(404, "not_found");
        String error = SectionChecks.check(mainframeId, section);
        if (error != null) return actionError(error);
        var result = CloudNetwork.section(section).handleQuery(search, q.strip());
        if (result.error() != null) return actionError(result.error());
        var rows = new JsonArray();
        for (var row : result.rows()) if (rows.size() < limit) rows.add(row);
        var json = new JsonObject();
        json.add("rows", rows);
        json.addProperty("truncated", result.truncated() || result.rows().size() > limit);
        return new AccessApi.Response(200, json);
    }

    /** {@code POST …/scenes/<sceneId>/run}: like a press of the scene's button by the key's player, never with a PIN. */
    private static AccessApi.Response runScene(MinecraftServer server, ApiKeys.Key key, Set<Perm> ownerPerms, String sceneId) {
        if (!key.scopes().contains(ApiScope.RUN_SCENES)) return AccessApi.error(403, "missing_scope");
        if (!ownerPerms.contains(Perm.ACT_BUTTONS)) return AccessApi.error(403, "not_allowed");
        UUID mainframeId = key.mainframeId();
        var scene = SceneService.scene(mainframeId, sceneId);
        if (scene == null) return AccessApi.error(404, "not_found");
        if (SceneService.needsPin(mainframeId, scene)) return AccessApi.error(403, "pin_required");
        String error = SectionChecks.check(mainframeId, SceneService.sectionId(mainframeId));
        if (error != null) return actionError(error);
        var caller = new SceneService.Caller(key.player(), key.playerName(), "api:" + key.name(), "api", null, null);
        error = SceneService.start(CloudNetwork.mainframe(mainframeId), sceneId, caller, false);
        if (error != null) return actionError(error);
        audit(server, key, "ran scene '" + scene.name() + "'");
        return ok();
    }

    private static @Nullable JsonElement scalar(String body) {
        try {
            if (!(JsonParser.parseString(body) instanceof JsonObject obj) || obj.size() != 1 || !(obj.get("value") instanceof JsonPrimitive p)) return null;
            if (p.isString() && p.getAsString().length() > Limits.STRING_VALUE) return null;
            if (p.isNumber() && !Double.isFinite(p.getAsDouble())) return null;
            return p;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Maps a game error to an HTTP status. */
    static AccessApi.Response actionError(String error) {
        int status = switch (error) {
            case "unknown_uplink", "unknown_widget", "unknown_scene", "not_found" -> 404;
            case "not_allowed", "read_only", "forbidden", "pin_required" -> 403;
            case "rate_limited" -> 429;
            case "offline", "no_power", "disabled", "unloaded", "uplink_offline", "busy", "too_many" -> 409;
            default -> 400;
        };
        return AccessApi.error(status, switch (error) {
            case "unknown_uplink", "unknown_widget", "unknown_scene" -> "not_found";
            default -> error;
        });
    }

    private static void audit(MinecraftServer server, ApiKeys.Key key, String what) {
        String text = "API key '" + key.name() + "' (" + key.playerName() + ") " + what;
        WebAuthData.access(server).audit(key.mainframeId(), text);
        CloudLog.LOGGER.info("[cccloud] {}", text);
    }

    private static AccessApi.Response ok() {
        var json = new JsonObject();
        json.addProperty("ok", true);
        return new AccessApi.Response(200, json);
    }
}
