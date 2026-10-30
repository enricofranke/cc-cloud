package ai.ezfox.cccloud.publicapi;

import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.web.AccessApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Key management for the dashboard (docs/SPEC.md v0.10): {@code GET/POST /api/mainframes/<id>/apikeys} with a device
 * token. The caller resolved the device's permissions ({@code api.keys}); this lists own keys (every key of the
 * mainframe with {@code access.manage}), creates keys (shown once) and revokes them. Strict bodies; pure Java, unit
 * tested.
 */
public final class ApiKeysApi {
    private static final Set<String> CREATE_FIELDS = Set.of("op", "name", "scopes", "expiresIn", "ips");

    /**
     * Who asks: the device's player, their permissions on the mainframe and the device's token hash (a key ends when the
     * browser that created it is revoked).
     */
    public record Caller(UUID player, String playerName, Set<Perm> perms, String device) {
        public Caller(UUID player, String playerName, Set<Perm> perms) {
            this(player, playerName, perms, "");
        }

        boolean moderates() {
            return perms.contains(Perm.ACCESS_MANAGE);
        }
    }

    private ApiKeysApi() {
    }

    public static AccessApi.Response get(ApiKeys keys, ApiSettings settings, UUID mainframeId, Caller caller, long now) {
        var json = new JsonObject();
        json.addProperty("enabled", settings.enabled());
        var scopes = new JsonArray();
        for (var s : ApiScope.values()) scopes.add(s.id);
        json.add("scopes", scopes);
        json.addProperty("limit", settings.keysPerPlayer());
        var array = new JsonArray();
        for (var key : keys.forMainframe(mainframeId)) {
            boolean own = key.player().equals(caller.player());
            if (!own && !caller.moderates()) continue;
            array.add(keyJson(key, own, now));
        }
        json.add("keys", array);
        return new AccessApi.Response(200, json);
    }

    /**
     * One key as the list shows it. Keys of other players (seen by moderators to revoke them) leave out the last IP
     * address and the allow-list: those are the other player's network addresses.
     */
    static JsonObject keyJson(ApiKeys.Key key, boolean own, long now) {
        var k = new JsonObject();
        k.addProperty("id", key.id());
        k.addProperty("name", key.name());
        k.addProperty("player", key.playerName());
        var scopes = new JsonArray();
        for (var s : ApiScope.values()) if (key.scopes().contains(s)) scopes.add(s.id);
        k.add("scopes", scopes);
        k.addProperty("created", key.created());
        if (key.expires() == 0) k.add("expires", JsonNull.INSTANCE);
        else k.addProperty("expires", key.expires());
        k.addProperty("expired", key.expired(now));
        if (key.lastUsed() == 0) k.add("lastUsed", JsonNull.INSTANCE);
        else k.addProperty("lastUsed", key.lastUsed());
        if (key.lastIp().isEmpty() || !own) k.add("lastIp", JsonNull.INSTANCE);
        else k.addProperty("lastIp", key.lastIp());
        var ips = new JsonArray();
        if (own) for (var ip : key.ips()) ips.add(ip);
        k.add("ips", ips);
        k.addProperty("own", own);
        return k;
    }

    /**
     * {@code {"op":"create",…}} or {@code {"op":"revoke","id"}}.
     *
     * @param audit gets one line per change (mainframe audit log)
     */
    public static AccessApi.Response post(ApiKeys keys, ApiSettings settings, UUID mainframeId, Caller caller, String body, long now, Consumer<String> audit) {
        JsonObject obj;
        try {
            if (!(JsonParser.parseString(body) instanceof JsonObject o)) return AccessApi.error(400, "invalid");
            obj = o;
        } catch (RuntimeException e) {
            return AccessApi.error(400, "invalid");
        }
        String op = string(obj, "op");
        if ("revoke".equals(op)) {
            if (obj.size() != 2) return AccessApi.error(400, "invalid");
            String id = string(obj, "id");
            if (id == null || !id.matches("[0-9a-f]{16}")) return AccessApi.error(400, "invalid");
            var removed = keys.revoke(id, k -> k.mainframeId().equals(mainframeId) && (k.player().equals(caller.player()) || caller.moderates()));
            if (removed == null) return AccessApi.error(404, "not_found");
            audit.accept(caller.playerName() + " revoked the API key '" + removed.name() + "' of " + removed.playerName());
            var ok = new JsonObject();
            ok.addProperty("ok", true);
            return new AccessApi.Response(200, ok);
        }
        if (!"create".equals(op)) return AccessApi.error(400, "invalid");
        if (!settings.enabled()) return AccessApi.error(409, "disabled");
        for (String key : obj.keySet()) if (!CREATE_FIELDS.contains(key)) return AccessApi.error(400, "invalid");
        String rawName = string(obj, "name");
        String name = rawName == null ? "" : plain(rawName);
        if (name.isEmpty() || name.length() > ApiKeys.MAX_NAME) return AccessApi.error(400, "invalid");
        if (!(obj.get("scopes") instanceof JsonArray scopeArray) || scopeArray.isEmpty() || scopeArray.size() > ApiScope.values().length) {
            return AccessApi.error(400, "invalid");
        }
        var scopes = EnumSet.noneOf(ApiScope.class);
        for (var e : scopeArray) {
            var scope = e instanceof JsonPrimitive p && p.isString() ? ApiScope.byId(p.getAsString()) : null;
            if (scope == null || !scopes.add(scope)) return AccessApi.error(400, "invalid");
            // A key never reaches past its creator: each scope's base permission is needed to create it.
            if (!caller.perms().contains(basePerm(scope))) return AccessApi.error(403, "not_allowed");
        }
        long expiresIn = 0;
        var e = obj.get("expiresIn");
        if (e != null && !e.isJsonNull()) {
            if (!(e instanceof JsonPrimitive p) || !p.isNumber() || !ApiKeys.EXPIRES_IN.contains(p.getAsLong()) || p.getAsDouble() != p.getAsLong()) {
                return AccessApi.error(400, "invalid");
            }
            expiresIn = p.getAsLong();
        }
        var ips = new ArrayList<String>();
        if (obj.has("ips")) {
            if (!(obj.get("ips") instanceof JsonArray ipArray) || ipArray.size() > ApiKeys.MAX_IPS) return AccessApi.error(400, "invalid");
            for (var ip : ipArray) {
                if (!(ip instanceof JsonPrimitive p) || !p.isString() || !ApiKeys.validIp(p.getAsString())) return AccessApi.error(400, "invalid");
                ips.add(p.getAsString().trim());
            }
        }
        if (keys.count(caller.player()) >= settings.keysPerPlayer()) return AccessApi.error(409, "too_many");
        var created = keys.create(caller.player(), caller.playerName(), mainframeId, name, scopes, expiresIn, List.copyOf(ips), caller.device());
        audit.accept(caller.playerName() + " created the API key '" + name + "' (" + String.join(", ", scopes.stream().map(s -> s.id).toList()) + ")");
        var json = keyJson(created.key(), true, now);
        json.addProperty("key", created.secret());
        return new AccessApi.Response(200, json);
    }

    /** The permission without which a scope is useless (and may not be put on a key). */
    static Perm basePerm(ApiScope scope) {
        return switch (scope) {
            case READ_SECTIONS, READ_STATS -> Perm.VIEW;
            case READ_STORAGE -> Perm.STORAGE_SEARCH;
            case ACTION_WIDGETS, RUN_SCENES -> Perm.ACT_BUTTONS;
        };
    }

    private static @Nullable String string(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    private static String plain(String raw) {
        var sb = new StringBuilder();
        raw.codePoints().filter(c -> !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT).forEach(sb::appendCodePoint);
        return sb.toString().trim();
    }
}
